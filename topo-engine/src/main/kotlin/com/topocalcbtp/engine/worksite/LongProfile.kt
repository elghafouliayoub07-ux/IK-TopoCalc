package com.topocalcbtp.engine.worksite

/** A measured point of the ground along the axis: cumulative distance (abscisse) and ground level (cote TN). */
data class ProfileStation(val name: String, val chainage: Double, val groundZ: Double)

/** A point of the design line (cote projet); the design is linear between consecutive breaks. */
data class GradeBreak(val chainage: Double, val z: Double)

data class LongProfileRow(
    val name: String,
    val chainage: Double,
    val partialDistance: Double,
    val groundZ: Double,
    val designZ: Double,
    /** ground − design: positive = cut (déblai), negative = fill (remblai). */
    val height: Double,
    /** Slopes from the previous point, in % (null for the first point). */
    val groundSlopePercent: Double?,
    val designSlopePercent: Double?
)

data class LongProfileResult(
    val rows: List<LongProfileRow>,
    /** Points de passage: abscisse and level where ground = design. */
    val crossings: List<Pair<Double, Double>>,
    /** Cut / fill areas of the long profile (m²). Multiply by a width for a trench or a strip. */
    val area: CutFill,
    val length: Double
)

object LongProfile {
    /** Design level at [chainage]; outside the breaks, the first / last grade is extended. */
    fun designAt(breaks: List<GradeBreak>, chainage: Double): Double {
        require(breaks.isNotEmpty()) { "The design line needs at least one point" }
        if (breaks.size == 1) return breaks[0].z
        val i = when {
            chainage <= breaks.first().chainage -> 0
            chainage >= breaks.last().chainage -> breaks.size - 2
            else -> breaks.indexOfLast { it.chainage <= chainage }.coerceAtMost(breaks.size - 2)
        }
        val a = breaks[i]; val b = breaks[i + 1]
        return a.z + (b.z - a.z) * (chainage - a.chainage) / (b.chainage - a.chainage)
    }

    /** Design line from a start level and a single slope (%, positive = rising). */
    fun singleGrade(startChainage: Double, startZ: Double, slopePercent: Double, endChainage: Double): List<GradeBreak> =
        listOf(GradeBreak(startChainage, startZ), GradeBreak(endChainage, startZ + slopePercent / 100.0 * (endChainage - startChainage)))

    fun compute(stations: List<ProfileStation>, breaks: List<GradeBreak>): LongProfileResult {
        require(stations.size >= 2) { "At least two points are required" }
        require(stations.zipWithNext().all { it.second.chainage > it.first.chainage }) { "Distances must increase from one point to the next" }
        require(breaks.isNotEmpty()) { "The design line needs at least one point" }
        require(breaks.zipWithNext().all { it.second.chainage > it.first.chainage }) { "Design points must be in increasing distance order" }
        val rows = stations.mapIndexed { i, s ->
            val dz = designAt(breaks, s.chainage)
            val prev = stations.getOrNull(i - 1)
            val d = if (prev == null) 0.0 else s.chainage - prev.chainage
            LongProfileRow(
                s.name, s.chainage, d, s.groundZ, dz, s.groundZ - dz,
                prev?.let { (s.groundZ - it.groundZ) / d * 100.0 },
                prev?.let { (dz - designAt(breaks, it.chainage)) / d * 100.0 }
            )
        }
        // Integrate on stations ∪ design breaks: ground and design are both linear in between.
        val xs = (stations.map { it.chainage } + breaks.map { it.chainage }
            .filter { it > stations.first().chainage && it < stations.last().chainage }).distinct().sorted()
        fun groundAt(c: Double): Double {
            val j = stations.indexOfLast { it.chainage <= c }.coerceIn(0, stations.size - 2)
            val a = stations[j]; val b = stations[j + 1]
            return a.groundZ + (b.groundZ - a.groundZ) * (c - a.chainage) / (b.chainage - a.chainage)
        }
        val hs = xs.map { groundAt(it) - designAt(breaks, it) }
        val crossings = CutFillIntegration.zeroCrossings(xs, hs).map { it to designAt(breaks, it) }
        return LongProfileResult(rows, crossings, CutFillIntegration.integrate(xs, hs), stations.last().chainage - stations.first().chainage)
    }
}
