package com.topocalcbtp.engine.worksite

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.sqrt

data class SlopeResult(
    val deltaZ: Double, val horizontalDistance: Double,
    /** (Z end − Z start) / |ΔX|: + rising (rampe), − falling (pente). */
    val ratio: Double
) {
    val percent get() = ratio * 100.0
    val perMille get() = ratio * 1000.0
    val angleDegrees get() = Math.toDegrees(atan(ratio))
    val slopeDistance get() = hypot(horizontalDistance, deltaZ)
    /** 1 / n (one metre of height for n metres of distance); null on a flat line. */
    val oneIn get() = if (ratio == 0.0) null else 1.0 / abs(ratio)
}

data class MeasurementStats(
    val values: List<Double>,
    val mean: Double,
    val residuals: List<Double>,
    /** Standard deviation of one measurement, σ = √(Σv² / (n − 1)). */
    val sigma: Double,
    /** σ of the mean = σ / √n. */
    val sigmaMean: Double,
    /** Tolerance 2.7 σ (OFPPT / French practice); indexes of values beyond it. */
    val tolerance: Double,
    val outliers: List<Int>
)

data class BaselinePosition(val abscissa: Double, val offset: Double)

object FieldTools {
    fun slope(deltaZ: Double, horizontalDistance: Double): SlopeResult {
        require(horizontalDistance > 0) { "Horizontal distance must be > 0" }
        return SlopeResult(deltaZ, horizontalDistance, deltaZ / horizontalDistance)
    }

    /** Ground distance (m) from a plan distance (cm) at scale 1/[scale]. */
    fun groundFromPlan(planCm: Double, scale: Double): Double = planCm / 100.0 * scale
    fun planFromGround(groundM: Double, scale: Double): Double = groundM * 100.0 / scale
    fun scaleFrom(planCm: Double, groundM: Double): Double {
        require(planCm > 0 && groundM > 0) { "Distances must be > 0" }
        return groundM * 100.0 / planCm
    }

    fun stats(values: List<Double>, toleranceFactor: Double = 2.7): MeasurementStats {
        require(values.size >= 2) { "At least two measurements are required" }
        val mean = values.average()
        val v = values.map { it - mean }
        val sigma = sqrt(v.sumOf { it * it } / (values.size - 1))
        val t = toleranceFactor * sigma
        return MeasurementStats(values, mean, v, sigma, sigma / sqrt(values.size.toDouble()), t,
            v.indices.filter { abs(v[it]) > t + 1e-12 })
    }

    /** Level at distance [x] from A on the line A → B (contour interpolation). */
    fun interpolateZ(za: Double, zb: Double, distanceAB: Double, x: Double): Double {
        require(distanceAB > 0) { "Distance AB must be > 0" }
        return za + (zb - za) * x / distanceAB
    }

    /** Distance from A where the level [z] is reached on A → B; null when z is not between za and zb. */
    fun distanceForZ(za: Double, zb: Double, distanceAB: Double, z: Double): Double? {
        require(distanceAB > 0) { "Distance AB must be > 0" }
        if (za == zb) return null
        val f = (z - za) / (zb - za)
        return if (f < 0 || f > 1) null else f * distanceAB
    }

    /**
     * Levé / implantation par abscisses et ordonnées on the operation line A → B.
     * Abscissa along A → B from A; offset (ordonnée) positive to the RIGHT of A → B.
     */
    fun fromBaseline(ax: Double, ay: Double, bx: Double, by: Double, abscissa: Double, offset: Double): Pair<Double, Double> {
        val l = hypot(bx - ax, by - ay)
        require(l > 0) { "A and B must be different" }
        val ux = (bx - ax) / l; val uy = (by - ay) / l
        // right-hand normal in X-East / Y-North: (uy, −ux)
        return Pair(ax + abscissa * ux + offset * uy, ay + abscissa * uy - offset * ux)
    }

    fun toBaseline(ax: Double, ay: Double, bx: Double, by: Double, x: Double, y: Double): BaselinePosition {
        val l = hypot(bx - ax, by - ay)
        require(l > 0) { "A and B must be different" }
        val ux = (bx - ax) / l; val uy = (by - ay) / l
        val dx = x - ax; val dy = y - ay
        return BaselinePosition(dx * ux + dy * uy, dx * uy - dy * ux)
    }
}
