package com.topocalcbtp.engine.coordinates

import com.topocalcbtp.engine.validation.requireFinite
import com.topocalcbtp.engine.validation.requireNonNegative
import kotlin.math.cos
import kotlin.math.sin

sealed class VerticalObservation {
    /** Alpha: vertical angle measured from horizontal; positive upward. */
    data class VerticalAngle(val alphaDegrees: Double) : VerticalObservation()
    /** Zenith angle Z: 0° at zenith, 90° horizontal, 180° at nadir. */
    data class ZenithAngle(val zenithDegrees: Double) : VerticalObservation()
}

data class ReducedDistance(
    val horizontalDistanceMetres: Double,
    val verticalComponentMetres: Double,
    val elevationDifferenceMetres: Double,
    /** Earth curvature + refraction term already included in the two values above (0 when not applied). */
    val curvatureRefractionMetres: Double = 0.0
)

/** Mean Earth radius used for the curvature/refraction correction, in metres. */
const val EARTH_RADIUS_METRES: Double = 6_371_000.0

object DistanceReduction {
    fun reduce(
        slopeDistanceMetres: Double,
        verticalObservation: VerticalObservation,
        instrumentHeightMetres: Double = 0.0,
        targetHeightMetres: Double = 0.0,
        /**
         * Adds (1 - k) * Dh^2 / (2R) to the vertical component. Leave false when the total station
         * already applies it internally; it matters for long sights (about 7 mm at 300 m, 2 cm at 500 m).
         */
        applyCurvatureRefraction: Boolean = false,
        refractionCoefficient: Double = 0.13
    ): ReducedDistance {
        require(refractionCoefficient in 0.0..1.0) { "refractionCoefficient must be between 0 and 1" }
        requireNonNegative("slopeDistanceMetres", slopeDistanceMetres)
        requireFinite("instrumentHeightMetres", instrumentHeightMetres)
        requireFinite("targetHeightMetres", targetHeightMetres)
        val alpha = when (verticalObservation) {
            is VerticalObservation.VerticalAngle -> {
                requireFinite("alphaDegrees", verticalObservation.alphaDegrees)
                verticalObservation.alphaDegrees
            }
            is VerticalObservation.ZenithAngle -> {
                requireFinite("zenithDegrees", verticalObservation.zenithDegrees)
                require(verticalObservation.zenithDegrees in 0.0..180.0) { "zenith angle must be in [0, 180] degrees" }
                90.0 - verticalObservation.zenithDegrees
            }
        }
        val radians = Math.toRadians(alpha)
        val horizontal = slopeDistanceMetres * cos(radians)
        val hd = kotlin.math.abs(horizontal)
        val cr = if (applyCurvatureRefraction) (1.0 - refractionCoefficient) * hd * hd / (2.0 * EARTH_RADIUS_METRES) else 0.0
        val vertical = slopeDistanceMetres * sin(radians) + cr
        return ReducedDistance(
            hd,
            vertical,
            instrumentHeightMetres + vertical - targetHeightMetres,
            cr
        )
    }
}
