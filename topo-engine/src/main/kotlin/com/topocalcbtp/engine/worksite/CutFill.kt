package com.topocalcbtp.engine.worksite

import kotlin.math.abs

/** Areas (m²) or volumes (m³) split into cut (déblai) and fill (remblai), both positive. */
data class CutFill(val cut: Double, val fill: Double) {
    val net: Double get() = cut - fill
    operator fun plus(o: CutFill) = CutFill(cut + o.cut, fill + o.fill)
    companion object { val ZERO = CutFill(0.0, 0.0) }
}

/**
 * Piecewise-linear height h(x) = ground − design (positive = cut / déblai), integrated by trapezoids.
 * Where h changes sign inside an interval, the interval is split at the zero point, so cut and fill
 * are not compensated against each other.
 */
object CutFillIntegration {
    fun integrate(x: List<Double>, h: List<Double>): CutFill {
        require(x.size == h.size) { "x and h must have the same size" }
        require(x.size >= 2) { "At least two points are required" }
        require(x.all { it.isFinite() } && h.all { it.isFinite() }) { "Values must be finite" }
        var cut = 0.0
        var fill = 0.0
        for (i in 0 until x.lastIndex) {
            val d = x[i + 1] - x[i]
            require(d >= 0.0) { "Distances must increase (point ${i + 2})" }
            val a = h[i]; val b = h[i + 1]
            if (a >= 0 && b >= 0) cut += (a + b) / 2 * d
            else if (a <= 0 && b <= 0) fill += -(a + b) / 2 * d
            else {
                val d0 = d * abs(a) / (abs(a) + abs(b)) // distance to the zero point
                if (a > 0) { cut += a / 2 * d0; fill += -b / 2 * (d - d0) }
                else { fill += -a / 2 * d0; cut += b / 2 * (d - d0) }
            }
        }
        return CutFill(cut, fill)
    }

    /** x positions where h changes sign (points de passage). */
    fun zeroCrossings(x: List<Double>, h: List<Double>): List<Double> {
        val out = mutableListOf<Double>()
        for (i in 0 until x.lastIndex) {
            val a = h[i]; val b = h[i + 1]
            if ((a > 0 && b < 0) || (a < 0 && b > 0)) out += x[i] + (x[i + 1] - x[i]) * abs(a) / (abs(a) + abs(b))
        }
        return out
    }
}
