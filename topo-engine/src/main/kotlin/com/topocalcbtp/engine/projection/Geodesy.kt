package com.topocalcbtp.engine.projection

import com.topocalcbtp.engine.validation.requireFinite
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Reference ellipsoid defined by semi-major axis and inverse flattening. */
data class Ellipsoid(val name: String, val semiMajorAxisMetres: Double, val inverseFlattening: Double) {
    val flattening: Double get() = 1.0 / inverseFlattening
    /** First eccentricity squared. */
    val e2: Double get() = flattening * (2.0 - flattening)
    val e: Double get() = sqrt(e2)

    companion object {
        val WGS84 = Ellipsoid("WGS 84", 6_378_137.0, 298.257223563)
        /** Clarke 1880 (IGN), EPSG:7011 — ellipsoid of the Merchich datum (Morocco). */
        val CLARKE_1880_IGN = Ellipsoid("Clarke 1880 (IGN)", 6_378_249.2, 293.466021293627)
        /** Clarke 1866, EPSG:7008 — used only for the EPSG worked example in the tests. */
        val CLARKE_1866 = Ellipsoid("Clarke 1866", 6_378_206.4, 294.978698213898)
    }
}

/** Geographic coordinates in decimal degrees; height is ellipsoidal (metres). */
data class GeographicPoint(val latitudeDegrees: Double, val longitudeDegrees: Double, val ellipsoidalHeightMetres: Double = 0.0) {
    init {
        requireFinite("latitudeDegrees", latitudeDegrees)
        requireFinite("longitudeDegrees", longitudeDegrees)
        requireFinite("ellipsoidalHeightMetres", ellipsoidalHeightMetres)
        require(latitudeDegrees in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitudeDegrees in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
    }
}

data class GeocentricPoint(val x: Double, val y: Double, val z: Double)

object Geocentric {
    /** EPSG method 9602, geographic -> geocentric. */
    fun fromGeographic(p: GeographicPoint, ellipsoid: Ellipsoid): GeocentricPoint {
        val phi = Math.toRadians(p.latitudeDegrees)
        val lambda = Math.toRadians(p.longitudeDegrees)
        val nu = ellipsoid.semiMajorAxisMetres / sqrt(1.0 - ellipsoid.e2 * sin(phi) * sin(phi))
        val h = p.ellipsoidalHeightMetres
        return GeocentricPoint(
            (nu + h) * cos(phi) * cos(lambda),
            (nu + h) * cos(phi) * sin(lambda),
            ((1.0 - ellipsoid.e2) * nu + h) * sin(phi)
        )
    }

    /** EPSG method 9602, geocentric -> geographic (iterative; converges to < 1e-12 rad off the poles). */
    fun toGeographic(c: GeocentricPoint, ellipsoid: Ellipsoid): GeographicPoint {
        val a = ellipsoid.semiMajorAxisMetres
        val e2 = ellipsoid.e2
        val p = sqrt(c.x * c.x + c.y * c.y)
        require(p > 1.0) { "Point too close to the polar axis" }
        val lambda = atan2(c.y, c.x)
        var phi = atan2(c.z, p * (1.0 - e2))
        var h = 0.0
        repeat(12) {
            val nu = a / sqrt(1.0 - e2 * sin(phi) * sin(phi))
            h = p / cos(phi) - nu
            phi = atan2(c.z, p * (1.0 - e2 * nu / (nu + h)))
        }
        return GeographicPoint(Math.toDegrees(phi), Math.toDegrees(lambda), h)
    }
}

enum class RotationConvention {
    /** EPSG 9606 / 1033 — "Position Vector" (IERS, most of Europe/Africa publications). */
    POSITION_VECTOR,
    /** EPSG 9607 / 1032 — "Coordinate Frame" (rotations with opposite sign). */
    COORDINATE_FRAME
}

/**
 * Seven-parameter similarity (Helmert) transformation between two geocentric frames.
 * Translations in metres, rotations in arc-seconds, scale in ppm.
 * With zero rotations and scale it is the 3-parameter "geocentric translations" (EPSG 9603).
 */
data class Helmert7(
    val name: String,
    val txMetres: Double, val tyMetres: Double, val tzMetres: Double,
    val rxArcSeconds: Double = 0.0, val ryArcSeconds: Double = 0.0, val rzArcSeconds: Double = 0.0,
    val scalePpm: Double = 0.0,
    val convention: RotationConvention = RotationConvention.POSITION_VECTOR,
    /** Published accuracy in metres, when known. */
    val accuracyMetres: Double? = null
) {
    init {
        listOf(txMetres, tyMetres, tzMetres, rxArcSeconds, ryArcSeconds, rzArcSeconds, scalePpm).forEach {
            requireFinite("Helmert parameter", it)
        }
    }

    private fun rotationsRadians(): Triple<Double, Double, Double> {
        val k = Math.PI / (180.0 * 3600.0)
        val sign = if (convention == RotationConvention.POSITION_VECTOR) 1.0 else -1.0
        return Triple(sign * rxArcSeconds * k, sign * ryArcSeconds * k, sign * rzArcSeconds * k)
    }

    /** Source frame -> target frame. */
    fun forward(s: GeocentricPoint): GeocentricPoint {
        val (rx, ry, rz) = rotationsRadians()
        val m = 1.0 + scalePpm * 1e-6
        return GeocentricPoint(
            m * (s.x - rz * s.y + ry * s.z) + txMetres,
            m * (rz * s.x + s.y - rx * s.z) + tyMetres,
            m * (-ry * s.x + rx * s.y + s.z) + tzMetres
        )
    }

    /** Target frame -> source frame: exact inverse of [forward] (solves the 3x3 system). */
    fun inverse(t: GeocentricPoint): GeocentricPoint {
        val (rx, ry, rz) = rotationsRadians()
        val m = 1.0 + scalePpm * 1e-6
        val bx = (t.x - txMetres) / m
        val by = (t.y - tyMetres) / m
        val bz = (t.z - tzMetres) / m
        // R = [[1, -rz, ry], [rz, 1, -rx], [-ry, rx, 1]]; solve R * s = b (Cramer's rule).
        val r = arrayOf(doubleArrayOf(1.0, -rz, ry), doubleArrayOf(rz, 1.0, -rx), doubleArrayOf(-ry, rx, 1.0))
        val b = doubleArrayOf(bx, by, bz)
        fun det(mm: Array<DoubleArray>) =
            mm[0][0] * (mm[1][1] * mm[2][2] - mm[1][2] * mm[2][1]) -
            mm[0][1] * (mm[1][0] * mm[2][2] - mm[1][2] * mm[2][0]) +
            mm[0][2] * (mm[1][0] * mm[2][1] - mm[1][1] * mm[2][0])
        val d = det(r)
        fun replaced(col: Int) = Array(3) { i -> DoubleArray(3) { j -> if (j == col) b[i] else r[i][j] } }
        return GeocentricPoint(det(replaced(0)) / d, det(replaced(1)) / d, det(replaced(2)) / d)
    }

    companion object {
        /** EPSG:1166 "Merchich to WGS 84 (1)": geocentric translations, derived at 9 stations, accuracy 7 m. */
        val MERCHICH_TO_WGS84_EPSG_1166 = Helmert7("EPSG:1166 Merchich → WGS 84", 31.0, 146.0, 47.0, accuracyMetres = 7.0)
    }
}
