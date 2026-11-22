package com.topocalcbtp.engine.stakeout

import com.topocalcbtp.engine.coordinates.Coordinates
import com.topocalcbtp.engine.coordinates.Point2D

/** Polar elements required to stake a target from a known station. */
data class StakeoutElements(
    val targetId: String,
    val horizontalDistanceMetres: Double,
    val gisementDegrees: Double,
    /** Target direction relative to current instrument orientation, when V0 is known. */
    val horizontalCircleReadingDegrees: Double?
)

object Stakeout {
    fun calculate(
        station: Point2D,
        target: Point2D,
        orientationConstantDegrees: Double? = null
    ): StakeoutElements {
        val inverse = Coordinates.inverse(station, target)
        val circle = orientationConstantDegrees?.let { (inverse.gisementDegrees - it + 3600.0) % 360.0 }
        return StakeoutElements(target.id, inverse.distanceMetres, inverse.gisementDegrees, circle)
    }

    /** Orientation constant V0 = known gisement - observed horizontal circle reading. */
    fun orientationConstant(knownGisementDegrees: Double, observedCircleReadingDegrees: Double): Double {
        return ((knownGisementDegrees - observedCircleReadingDegrees) % 360.0 + 360.0) % 360.0
    }
}
