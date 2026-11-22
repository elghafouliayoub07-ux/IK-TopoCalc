package com.topocalcbtp.engine.angles

import com.topocalcbtp.engine.validation.requireFinite

/** Angle utilities. Public API uses decimal degrees unless a name says otherwise. */
object Angles {
    fun normalize360(degrees: Double): Double {
        requireFinite("degrees", degrees)
        var normalized = degrees % 360.0
        if (normalized < 0.0) normalized += 360.0
        // A tiny negative input (e.g. -1e-15) becomes exactly 360.0 after the addition; fold it to 0.
        return if (normalized >= 360.0 || normalized == 0.0) 0.0 else normalized
    }

    fun normalize400Gons(gons: Double): Double {
        requireFinite("gons", gons)
        var normalized = gons % 400.0
        if (normalized < 0.0) normalized += 400.0
        return if (normalized >= 400.0 || normalized == 0.0) 0.0 else normalized
    }

    fun normalize180(degrees: Double): Double {
        val value = normalize360(degrees)
        return if (value > 180.0) value - 360.0 else value
    }

    fun degreesToRadians(degrees: Double): Double {
        requireFinite("degrees", degrees)
        return Math.toRadians(degrees)
    }

    fun radiansToDegrees(radians: Double): Double {
        requireFinite("radians", radians)
        return Math.toDegrees(radians)
    }

    fun degreesToGons(degrees: Double): Double {
        requireFinite("degrees", degrees)
        return degrees * (400.0 / 360.0)
    }

    fun gonsToDegrees(gons: Double): Double {
        requireFinite("gons", gons)
        return gons * (360.0 / 400.0)
    }

    /** Converts DMS components to decimal degrees. Minutes and seconds must be in [0, 60). */
    fun dmsToDecimal(degrees: Int, minutes: Int, seconds: Double, negative: Boolean = degrees < 0): Double {
        require(minutes in 0..59) { "minutes must be between 0 and 59" }
        requireFinite("seconds", seconds)
        require(seconds >= 0.0 && seconds < 60.0) { "seconds must be in [0, 60)" }
        val sign = if (negative) -1.0 else 1.0
        return sign * (kotlin.math.abs(degrees).toDouble() + minutes / 60.0 + seconds / 3600.0)
    }

    /** Converts decimal degrees to signed DMS components. */
    fun decimalToDms(decimalDegrees: Double): Dms {
        requireFinite("decimalDegrees", decimalDegrees)
        val sign = if (decimalDegrees < 0.0) -1 else 1
        val absValue = kotlin.math.abs(decimalDegrees)
        val deg = kotlin.math.floor(absValue).toInt()
        val minuteValue = (absValue - deg) * 60.0
        val min = kotlin.math.floor(minuteValue).toInt()
        val sec = (minuteValue - min) * 60.0
        return Dms(sign * deg, min, sec)
    }
}

data class Dms(val degrees: Int, val minutes: Int, val seconds: Double)
