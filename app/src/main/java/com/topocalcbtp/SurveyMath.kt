package com.topocalcbtp

import com.topocalcbtp.engine.leveling.LevelAdjustmentMethod
import com.topocalcbtp.engine.leveling.LevelSetup
import com.topocalcbtp.engine.leveling.Leveling
import com.topocalcbtp.engine.leveling.StaffReading
import com.topocalcbtp.engine.leveling.SightType

/** UI-facing adapter. All survey mathematics is delegated to topo-engine. */
data class LevelShot(
    val from: String,
    val to: String,
    val backsight: Double,
    val foresight: Double,
    val distanceMeters: Double = 1.0,
    /** Intermediate sights (LI) taken from this station: point name + staff reading. */
    val intermediates: List<Pair<String, Double>> = emptyList()
) {
    val observedDelta: Double get() = backsight - foresight
}

data class CompensatedShot(
    val shot: LevelShot,
    val correction: Double,
    val compensatedDelta: Double,
    val altitudeTo: Double
)

data class IntermediateResult(val shotIndex: Int, val name: String, val reading: Double, val altitude: Double)

/** Tolerance formula: k·√L (L in km) or c·√N (N = number of stations). */
enum class ToleranceMode { PER_SQRT_KM, PER_SQRT_SETUPS }

/** Text form of a station's intermediate sights (stored in saved calculations). */
object IntermediateCodec {
    fun encode(list: List<Pair<String, Double>>): String = list.joinToString("\u001E") { it.first + "\u001F" + fmtTrim(it.second) }
    fun decode(text: String): List<Pair<String, Double>> = if (text.isBlank()) emptyList() else text.split('\u001E').mapNotNull { item ->
        val parts = item.split('\u001F')
        val v = parts.getOrNull(1)?.let { parseNum(it) }
        if (parts[0].isBlank() || v == null) null else parts[0] to v
    }
}

data class ClosureResult(
    val startAltitude: Double,
    /** Calculated minus known end altitude; null for an open line (no known end altitude). */
    val closureError: Double?,
    val totalDistance: Double,
    val rows: List<CompensatedShot>,
    val sumBacksights: Double,
    val sumForesights: Double,
    val calculatedEndAltitude: Double,
    /** Tolerance in metres, when a k value (mm/sqrt(km)) was supplied. */
    val tolerance: Double?,
    val intermediates: List<IntermediateResult> = emptyList(),
    val method: LevelAdjustmentMethod = LevelAdjustmentMethod.PROPORTIONAL_TO_DISTANCE
) {
    /** ΣLA − ΣLV must equal (calculated end − start): classic field-book arithmetic check. */
    val arithmeticCheckOk: Boolean
        get() = kotlin.math.abs((sumBacksights - sumForesights) - (calculatedEndAltitude - startAltitude)) < 1e-6
    val withinTolerance: Boolean?
        get() = if (closureError != null && tolerance != null) kotlin.math.abs(closureError) <= tolerance else null
}

object SurveyMath {
    fun observedDelta(backsight: Double, foresight: Double): Double = backsight - foresight

    /**
     * Maps the app's one-segment-per-setup entry to the engine's HI field-book model.
     * [knownEndAltitude] null = open line: altitudes are computed, nothing is compensated.
     * [toleranceMmPerSqrtKm] optional k for T = k·√L(km), taken from the project specification.
     */
    fun compensate(
        startAltitude: Double,
        shots: List<LevelShot>,
        knownEndAltitude: Double?,
        toleranceMmPerSqrtKm: Double? = null,
        toleranceMode: ToleranceMode = ToleranceMode.PER_SQRT_KM,
        method: LevelAdjustmentMethod = LevelAdjustmentMethod.PROPORTIONAL_TO_DISTANCE
    ): ClosureResult {
        require(shots.isNotEmpty()) { "Add at least one leveling segment." }
        require(shots.all { it.distanceMeters > 0.0 }) { "Distances must be greater than zero." }
        shots.zipWithNext().forEach { (a, b) ->
            require(a.to == b.from) { "Discontinuous leveling route: ${a.to} does not match ${b.from}." }
        }
        val setups = shots.map { shot ->
            LevelSetup(
                backSight = StaffReading(shot.from, shot.backsight),
                // LI points take the correction of the station's back point (position 0 in the setup).
                intermediateSights = shot.intermediates.map { (name, reading) -> StaffReading(name, reading, 0.0) },
                foreSight = StaffReading(shot.to, shot.foresight),
                sectionLengthMetres = shot.distanceMeters
            )
        }
        val result = Leveling.compute(
            startPointId = shots.first().from,
            startElevationMetres = startAltitude,
            setups = setups,
            knownEndElevationMetres = knownEndAltitude,
            adjustment = if (knownEndAltitude != null) method else LevelAdjustmentMethod.NONE
        )
        val foresights = result.observations.filter { it.sightType == SightType.FORESIGHT }
        check(foresights.size == shots.size) { "Internal error: foresight count mismatch." }
        var previousCorrection = 0.0
        val rows = shots.zip(foresights).map { (shot, row) ->
            val cumulative = row.adjustedElevationMetres - row.rawElevationMetres
            // Per-segment correction (the engine gives the cumulative correction at each point).
            val segmentCorrection = cumulative - previousCorrection
            previousCorrection = cumulative
            CompensatedShot(shot, segmentCorrection, shot.observedDelta + segmentCorrection, row.adjustedElevationMetres)
        }
        val tolerance = toleranceMmPerSqrtKm?.let {
            if (toleranceMode == ToleranceMode.PER_SQRT_SETUPS) Leveling.closureToleranceBySetupsMetres(it, shots.size)
            else Leveling.closureToleranceMetres(it, result.totalLengthMetres)
        }
        val intermediates = result.observations.filter { it.sightType == SightType.INTERMEDIATE }.map {
            IntermediateResult(it.setupNumber - 1, it.pointId, it.staffReadingMetres, it.adjustedElevationMetres)
        }
        return ClosureResult(
            startAltitude, result.closureErrorMetres, result.totalLengthMetres, rows,
            result.sumBacksightsMetres, result.sumForesightsMetres, result.calculatedEndElevationMetres, tolerance,
            intermediates, if (knownEndAltitude != null) method else LevelAdjustmentMethod.NONE
        )
    }

    /** H = h - N. Requires compatible vertical datums and units. */
    fun orthometricAltitude(ellipsoidalHeight: Double, geoidUndulation: Double): Double =
        ellipsoidalHeight - geoidUndulation

    fun formatMeters(value: Double): String = "%+.3f m".format(java.util.Locale.US, value)
}
