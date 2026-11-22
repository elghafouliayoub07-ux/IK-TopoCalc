package com.topocalcbtp.engine.coordinates

import com.topocalcbtp.engine.validation.requireFinite
import com.topocalcbtp.engine.validation.requireNonNegative
import com.topocalcbtp.engine.validation.requirePositive

data class StadiaResult(
    val intervalMetres: Double,
    val estimatedDistanceMetres: Double,
    val expectedMiddleReadingMetres: Double,
    val middleReadingResidualMetres: Double,
    val middleReadingWithinTolerance: Boolean
)

object Stadia {
    /**
     * Stadia distance: D = K × (upper - lower) + C.
     * The default K=100, C=0 is a common instrument approximation, not a universal calibration.
     */
    fun calculate(
        upperReadingMetres: Double,
        middleReadingMetres: Double,
        lowerReadingMetres: Double,
        stadiaConstantK: Double = 100.0,
        additiveConstantCMetres: Double = 0.0,
        middleWireToleranceMetres: Double = 0.005
    ): StadiaResult {
        requireFinite("upperReadingMetres", upperReadingMetres)
        requireFinite("middleReadingMetres", middleReadingMetres)
        requireFinite("lowerReadingMetres", lowerReadingMetres)
        requirePositive("stadiaConstantK", stadiaConstantK)
        requireFinite("additiveConstantCMetres", additiveConstantCMetres)
        requireNonNegative("middleWireToleranceMetres", middleWireToleranceMetres)
        require(upperReadingMetres >= middleReadingMetres && middleReadingMetres >= lowerReadingMetres) {
            "Staff readings must satisfy upper >= middle >= lower"
        }
        val interval = upperReadingMetres - lowerReadingMetres
        val expectedMiddle = (upperReadingMetres + lowerReadingMetres) / 2.0
        val residual = middleReadingMetres - expectedMiddle
        return StadiaResult(
            interval,
            stadiaConstantK * interval + additiveConstantCMetres,
            expectedMiddle,
            residual,
            kotlin.math.abs(residual) <= middleWireToleranceMetres
        )
    }

    /**
     * Inclined-sight stadia (theodolite / tachéomètre optique), with alpha measured from horizontal:
     *   Dh = K * l * cos^2(alpha) + C * cos(alpha)
     *   V  = K * l * sin(2 alpha) / 2 + C * sin(alpha)
     *   dZ(ground) = HI + V - middle reading
     * With alpha = 0 this reduces to the level formula D = K * l + C.
     */
    fun inclined(
        upperReadingMetres: Double,
        middleReadingMetres: Double,
        lowerReadingMetres: Double,
        vertical: VerticalObservation,
        instrumentHeightMetres: Double = 0.0,
        stadiaConstantK: Double = 100.0,
        additiveConstantCMetres: Double = 0.0,
        middleWireToleranceMetres: Double = 0.005
    ): InclinedStadiaResult {
        val base = calculate(
            upperReadingMetres, middleReadingMetres, lowerReadingMetres,
            stadiaConstantK, additiveConstantCMetres, middleWireToleranceMetres
        )
        requireFinite("instrumentHeightMetres", instrumentHeightMetres)
        val alphaDeg = when (vertical) {
            is VerticalObservation.VerticalAngle -> vertical.alphaDegrees
            is VerticalObservation.ZenithAngle -> {
                require(vertical.zenithDegrees in 0.0..180.0) { "zenith angle must be in [0, 180] degrees" }
                90.0 - vertical.zenithDegrees
            }
        }
        requireFinite("alphaDegrees", alphaDeg)
        val a = Math.toRadians(alphaDeg)
        val kl = stadiaConstantK * base.intervalMetres
        val hd = kl * kotlin.math.cos(a) * kotlin.math.cos(a) + additiveConstantCMetres * kotlin.math.cos(a)
        val v = kl * kotlin.math.sin(2.0 * a) / 2.0 + additiveConstantCMetres * kotlin.math.sin(a)
        return InclinedStadiaResult(base, kotlin.math.abs(hd), v, instrumentHeightMetres + v - middleReadingMetres)
    }
}

data class InclinedStadiaResult(
    val wires: StadiaResult,
    val horizontalDistanceMetres: Double,
    val verticalComponentMetres: Double,
    /** Ground-to-ground height difference: HI + V - middle wire reading. */
    val elevationDifferenceMetres: Double
)
