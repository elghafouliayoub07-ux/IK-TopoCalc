package com.topocalcbtp.engine.worksite

/**
 * Levelling from one station (rayonnement) and height staking (implantation altimétrique).
 * HPV = altitude of the line of sight (plan de visée) = benchmark level + backsight.
 */
data class SpotLevelInput(val name: String, val reading: Double?, val designZ: Double?)

data class SpotLevelRow(
    val name: String,
    val reading: Double?,
    val groundZ: Double?,
    val designZ: Double?,
    /** Reading to see on the staff when its foot is at the design level: HPV − design. */
    val readingForDesign: Double?,
    /** ground − design: + = cut (à déblayer), − = fill (à remblayer). */
    val height: Double?
)

object SpotLevels {
    fun lineOfSight(benchmarkZ: Double, backsight: Double): Double = benchmarkZ + backsight

    fun compute(lineOfSight: Double, points: List<SpotLevelInput>): List<SpotLevelRow> = points.map { p ->
        val g = p.reading?.let { lineOfSight - it }
        SpotLevelRow(p.name, p.reading, g, p.designZ, p.designZ?.let { lineOfSight - it },
            if (g != null && p.designZ != null) g - p.designZ else null)
    }

    /** Design levels along a line: start level + slope (%) × cumulative distance. */
    fun gradeLevels(startZ: Double, slopePercent: Double, cumulativeDistances: List<Double>): List<Double> =
        cumulativeDistances.map { startZ + slopePercent / 100.0 * it }
}
