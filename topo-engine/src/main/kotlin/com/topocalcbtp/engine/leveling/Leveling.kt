package com.topocalcbtp.engine.leveling

import com.topocalcbtp.engine.validation.requireFinite
import com.topocalcbtp.engine.validation.requireNonNegative
import com.topocalcbtp.engine.validation.requirePositive

/** Staff reading in metres. */
data class StaffReading(
    val pointId: String,
    val readingMetres: Double,
    /** Position from the start of this setup, used for intermediate sights only. */
    val chainageInSetupMetres: Double? = null
) {
    init {
        require(pointId.isNotBlank()) { "pointId must not be blank" }
        requireNonNegative("readingMetres", readingMetres)
        chainageInSetupMetres?.let { requireNonNegative("chainageInSetupMetres", it) }
    }
}

data class LevelSetup(
    val backSight: StaffReading,
    val intermediateSights: List<StaffReading>,
    val foreSight: StaffReading,
    /** Levelling route length covered by this setup, in metres. */
    val sectionLengthMetres: Double
) {
    init {
        requirePositive("sectionLengthMetres", sectionLengthMetres)
        intermediateSights.forEach {
            val chainage = requireNotNull(it.chainageInSetupMetres) {
                "Intermediate sight ${it.pointId} must have chainageInSetupMetres"
            }
            require(chainage <= sectionLengthMetres) {
                "Intermediate sight ${it.pointId} lies beyond this setup's section length"
            }
        }
    }
}

enum class SightType { BACKSIGHT, INTERMEDIATE, FORESIGHT }

/**
 * How the closure error is distributed over the setups (stations):
 * by route length, equally per setup (N), or proportionally to each setup's |ΔH|.
 */
enum class LevelAdjustmentMethod { NONE, PROPORTIONAL_TO_DISTANCE, EQUAL_PER_SETUP, PROPORTIONAL_TO_ABS_DELTA }

data class LevelObservationResult(
    val setupNumber: Int,
    val pointId: String,
    val sightType: SightType,
    val staffReadingMetres: Double,
    val heightOfInstrumentMetres: Double,
    val rawElevationMetres: Double,
    val chainageMetres: Double,
    val adjustedElevationMetres: Double
)

data class LevelingResult(
    val observations: List<LevelObservationResult>,
    val sumBacksightsMetres: Double,
    val sumForesightsMetres: Double,
    val arithmeticCheckMetres: Double,
    val calculatedEndElevationMetres: Double,
    val knownEndElevationMetres: Double?,
    /** Calculated end elevation minus known end elevation. */
    val closureErrorMetres: Double?,
    val totalLengthMetres: Double
)

object Leveling {
    /**
     * Computes a multi-setup HI (Height of Instrument) levelling field book.
     * X/Y are not used here. All staff readings, elevations and chainages are metres.
     * The fore-sight point of a setup must be the back-sight point of the next setup.
     */
    fun compute(
        startPointId: String,
        startElevationMetres: Double,
        setups: List<LevelSetup>,
        knownEndElevationMetres: Double? = null,
        adjustment: LevelAdjustmentMethod = LevelAdjustmentMethod.NONE
    ): LevelingResult {
        require(startPointId.isNotBlank()) { "startPointId must not be blank" }
        requireFinite("startElevationMetres", startElevationMetres)
        require(setups.isNotEmpty()) { "At least one setup is required" }
        knownEndElevationMetres?.let { requireFinite("knownEndElevationMetres", it) }

        var currentKnownPointId = startPointId
        var currentElevation = startElevationMetres
        var chainageStart = 0.0
        var sumBs = 0.0
        var sumFs = 0.0
        val rawRows = mutableListOf<LevelObservationResult>()

        setups.forEachIndexed { index, setup ->
            require(setup.backSight.pointId == currentKnownPointId) {
                "Setup ${index + 1}: backsight point '${setup.backSight.pointId}' must match previous known point '$currentKnownPointId'"
            }
            val hi = currentElevation + setup.backSight.readingMetres
            sumBs += setup.backSight.readingMetres
            rawRows += LevelObservationResult(
                index + 1, setup.backSight.pointId, SightType.BACKSIGHT,
                setup.backSight.readingMetres, hi, currentElevation, chainageStart, currentElevation
            )

            setup.intermediateSights.forEach { sight ->
                val elevation = hi - sight.readingMetres
                rawRows += LevelObservationResult(
                    index + 1, sight.pointId, SightType.INTERMEDIATE,
                    sight.readingMetres, hi, elevation,
                    chainageStart + requireNotNull(sight.chainageInSetupMetres), elevation
                )
            }

            val endElevation = hi - setup.foreSight.readingMetres
            sumFs += setup.foreSight.readingMetres
            val chainageEnd = chainageStart + setup.sectionLengthMetres
            rawRows += LevelObservationResult(
                index + 1, setup.foreSight.pointId, SightType.FORESIGHT,
                setup.foreSight.readingMetres, hi, endElevation, chainageEnd, endElevation
            )
            currentKnownPointId = setup.foreSight.pointId
            currentElevation = endElevation
            chainageStart = chainageEnd
        }

        val arithmeticCheck = sumBs - sumFs
        val calculatedEnd = currentElevation
        val closure = knownEndElevationMetres?.let { calculatedEnd - it }
        val totalLength = chainageStart

        val correctedRows = if (adjustment != LevelAdjustmentMethod.NONE && closure != null) {
            // Weight of each setup, then cumulative correction at the end of each setup.
            val deltas = setups.map { it.backSight.readingMetres - it.foreSight.readingMetres }
            var weights = when (adjustment) {
                LevelAdjustmentMethod.PROPORTIONAL_TO_DISTANCE -> setups.map { it.sectionLengthMetres }
                LevelAdjustmentMethod.EQUAL_PER_SETUP -> setups.map { 1.0 }
                LevelAdjustmentMethod.PROPORTIONAL_TO_ABS_DELTA -> deltas.map { kotlin.math.abs(it) }
                LevelAdjustmentMethod.NONE -> error("unreachable")
            }
            if (weights.sum() <= 0.0) weights = setups.map { 1.0 } // all ΔH = 0: equal shares
            val total = weights.sum()
            val endCorrection = DoubleArray(setups.size)
            var acc = 0.0
            weights.forEachIndexed { i, w -> acc += w; endCorrection[i] = -closure * acc / total }
            rawRows.map { row ->
                val i = row.setupNumber - 1
                val before = if (i == 0) 0.0 else endCorrection[i - 1]
                val correction = when (row.sightType) {
                    SightType.BACKSIGHT -> before
                    SightType.FORESIGHT -> endCorrection[i]
                    // Intermediate sights: share of the setup correction by position inside the setup.
                    SightType.INTERMEDIATE -> {
                        val setupStart = rawRows.first { it.setupNumber == row.setupNumber && it.sightType == SightType.BACKSIGHT }.chainageMetres
                        val f = ((row.chainageMetres - setupStart) / setups[i].sectionLengthMetres).coerceIn(0.0, 1.0)
                        before + f * (endCorrection[i] - before)
                    }
                }
                row.copy(adjustedElevationMetres = row.rawElevationMetres + correction)
            }
        } else rawRows

        return LevelingResult(
            correctedRows, sumBs, sumFs, arithmeticCheck,
            calculatedEnd, knownEndElevationMetres, closure, totalLength
        )
    }

    /**
     * Closure tolerance T = k * sqrt(L[km]), returned in metres. k is in millimetres per sqrt(km)
     * and must come from the project specification (CPS) or the applicable standard; the engine does
     * not impose a default class.
     */
    fun closureToleranceMetres(millimetresPerSqrtKm: Double, routeLengthMetres: Double): Double {
        requirePositive("millimetresPerSqrtKm", millimetresPerSqrtKm)
        requirePositive("routeLengthMetres", routeLengthMetres)
        return millimetresPerSqrtKm * kotlin.math.sqrt(routeLengthMetres / 1000.0) / 1000.0
    }

    /**
     * Site-levelling tolerance T = c * sqrt(N), N = number of setups (dénivelées), c in millimetres
     * (OFPPT course: c = 4.6 mm for an ordinary site level). Returned in metres.
     */
    fun closureToleranceBySetupsMetres(millimetresPerSqrtSetup: Double, setupCount: Int): Double {
        requirePositive("millimetresPerSqrtSetup", millimetresPerSqrtSetup)
        require(setupCount > 0) { "setupCount must be > 0" }
        return millimetresPerSqrtSetup * kotlin.math.sqrt(setupCount.toDouble()) / 1000.0
    }
}
