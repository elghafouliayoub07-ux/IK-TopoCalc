package com.topocalcbtp.engine.projection

import com.topocalcbtp.engine.validation.requireFinite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Projected (grid) coordinates: X = Easting, Y = Northing, metres. */
data class GridPoint(val x: Double, val y: Double) {
    init { requireFinite("x", x); requireFinite("y", y) }
}

/**
 * Lambert Conic Conformal (1SP), EPSG method 9801, following IOGP Guidance Note 7-2.
 * Angles in decimal degrees.
 */
class LambertConicConformal1SP(
    val ellipsoid: Ellipsoid,
    val latitudeOfOriginDegrees: Double,
    val longitudeOfOriginDegrees: Double,
    val scaleFactor: Double,
    val falseEastingMetres: Double,
    val falseNorthingMetres: Double
) {
    private val e = ellipsoid.e
    private val a = ellipsoid.semiMajorAxisMetres
    private val phi0 = Math.toRadians(latitudeOfOriginDegrees)
    private val lambda0 = Math.toRadians(longitudeOfOriginDegrees)
    private val n = sin(phi0)
    private val bigF: Double
    private val r0: Double

    init {
        require(latitudeOfOriginDegrees > 0.0 && latitudeOfOriginDegrees < 90.0) {
            "Only northern-hemisphere 1SP Lambert projections are supported"
        }
        requireFinite("scaleFactor", scaleFactor)
        val m0 = m(phi0)
        val t0 = t(phi0)
        bigF = m0 / (n * t0.pow(n))
        r0 = a * bigF * t0.pow(n) * scaleFactor
    }

    private fun m(phi: Double) = cos(phi) / sqrt(1.0 - e * e * sin(phi) * sin(phi))
    private fun t(phi: Double) = tan(PI / 4.0 - phi / 2.0) / ((1.0 - e * sin(phi)) / (1.0 + e * sin(phi))).pow(e / 2.0)

    fun project(latitudeDegrees: Double, longitudeDegrees: Double): GridPoint {
        val phi = Math.toRadians(latitudeDegrees)
        val r = a * bigF * t(phi).pow(n) * scaleFactor
        val theta = n * (Math.toRadians(longitudeDegrees) - lambda0)
        return GridPoint(falseEastingMetres + r * sin(theta), falseNorthingMetres + r0 - r * cos(theta))
    }

    /** Returns (latitude, longitude) in decimal degrees on [ellipsoid]. */
    fun unproject(grid: GridPoint): Pair<Double, Double> {
        val dx = grid.x - falseEastingMetres
        val dy = r0 - (grid.y - falseNorthingMetres)
        val rPrime = sqrt(dx * dx + dy * dy) // n > 0 here
        val tPrime = (rPrime / (a * scaleFactor * bigF)).pow(1.0 / n)
        val thetaPrime = atan2(dx, dy)
        var phi = PI / 2.0 - 2.0 * atan(tPrime)
        for (i in 0 until 20) {
            val next = PI / 2.0 - 2.0 * atan(tPrime * ((1.0 - e * sin(phi)) / (1.0 + e * sin(phi))).pow(e / 2.0))
            val done = abs(next - phi) < 1e-14
            phi = next
            if (done) break
        }
        val lambda = thetaPrime / n + lambda0
        return Math.toDegrees(phi) to Math.toDegrees(lambda)
    }

    /** Point scale factor at a latitude (useful to reduce field distances to the grid). */
    fun scaleFactorAt(latitudeDegrees: Double): Double {
        val phi = Math.toRadians(latitudeDegrees)
        val r = a * bigF * t(phi).pow(n) * scaleFactor
        return r * n / (a * m(phi))
    }
}

/** The four Lambert zones of the Merchich datum (EPSG parameters, angles converted from grads). */
enum class MoroccoLambertZone(
    val label: String,
    val epsgCode: Int,
    val latitudeOfOriginDegrees: Double,
    val scaleFactor: Double,
    val falseEastingMetres: Double,
    val falseNorthingMetres: Double,
    /** Latitude band (WGS 84 degrees, approximate) where the zone is used: [southLimit, northLimit). */
    val southLimitDegrees: Double,
    val northLimitDegrees: Double
) {
    NORD_MAROC("Lambert Nord Maroc", 26191, 33.3, 0.999625769, 500_000.0, 300_000.0, 31.5, 90.0),
    SUD_MAROC("Lambert Sud Maroc", 26192, 29.7, 0.999615596, 500_000.0, 300_000.0, 27.9, 31.5),
    SAHARA_NORD("Lambert Sahara Nord", 26194, 26.1, 0.999616304, 1_200_000.0, 400_000.0, 24.3, 27.9),
    SAHARA_SUD("Lambert Sahara Sud", 26195, 22.5, 0.999616437, 1_500_000.0, 400_000.0, -90.0, 24.3);

    /** All four zones share the central meridian -6 grads = -5.4 degrees. */
    val longitudeOfOriginDegrees: Double get() = -5.4

    val projection: LambertConicConformal1SP by lazy {
        LambertConicConformal1SP(
            Ellipsoid.CLARKE_1880_IGN, latitudeOfOriginDegrees, longitudeOfOriginDegrees,
            scaleFactor, falseEastingMetres, falseNorthingMetres
        )
    }

    companion object {
        /** Zone normally used at this latitude (boundaries at 35, 31 and 27 grads). */
        fun forLatitude(latitudeDegrees: Double): MoroccoLambertZone =
            entries.first { latitudeDegrees >= it.southLimitDegrees && latitudeDegrees < it.northLimitDegrees }
    }
}

/** Lambert (Merchich) <-> WGS 84 geographic, through geocentric coordinates. */
object MoroccoCoordinates {
    /**
     * Lambert grid -> WGS 84 latitude/longitude.
     * The Merchich ellipsoidal height is unknown from 2D grid coordinates and is taken as 0:
     * its effect on the horizontal result is far below the datum transformation accuracy.
     */
    fun lambertToWgs84(
        grid: GridPoint,
        zone: MoroccoLambertZone,
        transformation: Helmert7 = Helmert7.MERCHICH_TO_WGS84_EPSG_1166
    ): GeographicPoint {
        val (lat, lon) = zone.projection.unproject(grid)
        val merchich = Geocentric.fromGeographic(GeographicPoint(lat, lon, 0.0), Ellipsoid.CLARKE_1880_IGN)
        val wgs = transformation.forward(merchich)
        return Geocentric.toGeographic(wgs, Ellipsoid.WGS84)
    }

    fun wgs84ToLambert(
        wgs84: GeographicPoint,
        zone: MoroccoLambertZone,
        transformation: Helmert7 = Helmert7.MERCHICH_TO_WGS84_EPSG_1166
    ): GridPoint {
        val wgsGeocentric = Geocentric.fromGeographic(wgs84, Ellipsoid.WGS84)
        val merchich = Geocentric.toGeographic(transformation.inverse(wgsGeocentric), Ellipsoid.CLARKE_1880_IGN)
        return zone.projection.project(merchich.latitudeDegrees, merchich.longitudeDegrees)
    }
}
