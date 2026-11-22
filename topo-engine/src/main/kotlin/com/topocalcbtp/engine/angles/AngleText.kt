package com.topocalcbtp.engine.angles

import kotlin.math.abs
import kotlin.math.floor

/** Parsing and formatting of geographic angles typed by a user. */
object AngleText {
    /**
     * Accepts decimal degrees ("33.5", "-5,42") or degrees-minutes-seconds separated by spaces or
     * symbols ("33 30 15.2", "33°30'15.2\"", "5d24m0s"). A trailing or leading N/S/E/W
     * (also O for Ouest) sets the sign: S and W/O are negative. Returns null when the text is invalid.
     */
    fun parseDegrees(text: String): Double? {
        var s = text.trim().uppercase().replace(',', '.')
        if (s.isEmpty()) return null
        var sign = 1.0
        val hemi = Regex("^[NSEWO]|[NSEWO]$").find(s)?.value
        if (hemi != null) {
            if (hemi == "S" || hemi == "W" || hemi == "O") sign = -1.0
            s = s.removePrefix(hemi).removeSuffix(hemi).trim()
        }
        if (s.startsWith("-")) { sign = -sign; s = s.removePrefix("-").trim() }
        else if (s.startsWith("+")) s = s.removePrefix("+").trim()
        if (s.contains('-') || s.contains('+')) return null
        val parts = s.split(Regex("[^0-9.]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 3) return null
        val nums = parts.map { it.toDoubleOrNull() ?: return null }
        if (nums.size > 1) {
            // Only the last component may carry decimals; minutes and seconds must be < 60.
            if (nums.dropLast(1).any { it != floor(it) }) return null
            if (nums.drop(1).any { it >= 60.0 }) return null
        }
        val value = nums[0] + (nums.getOrNull(1) ?: 0.0) / 60.0 + (nums.getOrNull(2) ?: 0.0) / 3600.0
        return if (value.isFinite()) sign * value else null
    }

    /** 33°30'15.20"N style text. [positive]/[negative] are the hemisphere letters. */
    fun formatDms(decimalDegrees: Double, positive: Char, negative: Char, secondDecimals: Int = 3): String {
        val hemi = if (decimalDegrees < 0) negative else positive
        var totalSeconds = abs(decimalDegrees) * 3600.0
        val factor = Math.pow(10.0, secondDecimals.toDouble())
        totalSeconds = Math.round(totalSeconds * factor) / factor
        val d = floor(totalSeconds / 3600.0).toInt()
        val m = floor((totalSeconds - d * 3600.0) / 60.0).toInt()
        val sec = totalSeconds - d * 3600.0 - m * 60.0
        return "%d°%02d'%0${secondDecimals + 3}.${secondDecimals}f\"%c".format(java.util.Locale.US, d, m, sec, hemi)
    }
}
