package com.topocalcbtp.engine.angles

import com.topocalcbtp.engine.validation.requireFinite

/** Mean of a horizontal reading taken in both faces (cercle gauche / cercle droit). */
data class TwoFaceHorizontal(
    /** Mean reading referred to face left (CG), in [0, 360). */
    val meanDegrees: Double,
    /** CD − CG − 180°, in (−180, 180]; ideally 0. */
    val deltaDegrees: Double
) {
    /** Collimation error c = delta / 2. */
    val collimationDegrees: Double get() = deltaDegrees / 2.0
}

data class TwoFaceZenith(
    /** Vertical index error i = (Z_CG + Z_CD − 360°) / 2. */
    val indexErrorDegrees: Double,
    /** Corrected zenith angle Z = Z_CG − i = (Z_CG − Z_CD + 360°) / 2. */
    val correctedZenithDegrees: Double
)

/** Double retournement: averaging face left and face right removes collimation and vertical index errors. */
object TwoFace {
    fun horizontal(faceLeftDegrees: Double, faceRightDegrees: Double): TwoFaceHorizontal {
        requireFinite("faceLeftDegrees", faceLeftDegrees)
        requireFinite("faceRightDegrees", faceRightDegrees)
        var delta = Angles.normalize180(faceRightDegrees - faceLeftDegrees - 180.0)
        if (delta == -180.0) delta = 180.0
        return TwoFaceHorizontal(Angles.normalize360(faceLeftDegrees + delta / 2.0), delta)
    }

    fun zenith(faceLeftZenithDegrees: Double, faceRightZenithDegrees: Double): TwoFaceZenith {
        requireFinite("faceLeftZenithDegrees", faceLeftZenithDegrees)
        requireFinite("faceRightZenithDegrees", faceRightZenithDegrees)
        val index = Angles.normalize180(faceLeftZenithDegrees + faceRightZenithDegrees - 360.0) / 2.0
        val corrected = faceLeftZenithDegrees - index
        require(corrected in 0.0..180.0) { "Corrected zenith angle out of [0, 180] degrees" }
        return TwoFaceZenith(index, corrected)
    }
}
