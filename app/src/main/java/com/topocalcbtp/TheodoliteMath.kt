package com.topocalcbtp

import com.topocalcbtp.engine.angles.Angles
import com.topocalcbtp.engine.coordinates.DistanceReduction
import com.topocalcbtp.engine.coordinates.InclinedStadiaResult
import com.topocalcbtp.engine.coordinates.ReducedDistance
import com.topocalcbtp.engine.coordinates.Stadia
import com.topocalcbtp.engine.coordinates.StadiaResult
import com.topocalcbtp.engine.coordinates.VerticalObservation

enum class VerticalReadingType { ZENITH_ANGLE, VERTICAL_ANGLE }

/** Angle unit chosen by the user. All engine calls receive decimal degrees. */
enum class AngleUnit(val symbol: String) {
    DEGREES("°"), GONS("gr");

    fun toDegrees(value: Double): Double = if (this == GONS) Angles.gonsToDegrees(value) else value
    fun fromDegrees(value: Double): Double = if (this == GONS) Angles.degreesToGons(value) else value
    /** Full circle normalisation in this unit. */
    fun normalizeFull(valueInUnit: Double): Double =
        if (this == GONS) Angles.normalize400Gons(valueInUnit) else Angles.normalize360(valueInUnit)
}

/** UI-facing adapter to the independent calculation engine. Inputs/outputs use [AngleUnit]. */
object TheodoliteMath {
    /** Clockwise angle from the first (back) reading to the second (fore) reading. */
    fun horizontalAngle(backReading: Double, foreReading: Double, unit: AngleUnit = AngleUnit.DEGREES): Double {
        val deg = Angles.normalize360(unit.toDegrees(foreReading) - unit.toDegrees(backReading))
        return unit.normalizeFull(unit.fromDegrees(deg))
    }

    private fun observation(reading: Double, type: VerticalReadingType, unit: AngleUnit): VerticalObservation {
        val deg = unit.toDegrees(reading)
        return when (type) {
            VerticalReadingType.ZENITH_ANGLE -> VerticalObservation.ZenithAngle(deg)
            VerticalReadingType.VERTICAL_ANGLE -> VerticalObservation.VerticalAngle(deg)
        }
    }

    fun reduceSlopeDistance(
        slopeDistanceMeters: Double,
        verticalReading: Double,
        type: VerticalReadingType,
        unit: AngleUnit,
        instrumentHeightMeters: Double,
        targetHeightMeters: Double,
        applyCurvatureRefraction: Boolean
    ): ReducedDistance = DistanceReduction.reduce(
        slopeDistanceMeters, observation(verticalReading, type, unit),
        instrumentHeightMeters, targetHeightMeters, applyCurvatureRefraction
    )

    /** Theodolite stadia: uses the vertical reading (Dh = K·l·cos²α + C·cosα). */
    fun inclinedStadia(
        upper: Double, middle: Double, lower: Double,
        verticalReading: Double, type: VerticalReadingType, unit: AngleUnit,
        instrumentHeightMeters: Double, k: Double, c: Double
    ): InclinedStadiaResult = Stadia.inclined(
        upper, middle, lower, observation(verticalReading, type, unit), instrumentHeightMeters, k, c
    )

    /** Level (horizontal sight) stadia with middle-wire check. */
    fun levelStadia(upper: Double, middle: Double, lower: Double, k: Double = 100.0, c: Double = 0.0): StadiaResult =
        Stadia.calculate(upper, middle, lower, k, c)
}
