package com.topocalcbtp.engine.coordinates

import com.topocalcbtp.engine.angles.Angles
import com.topocalcbtp.engine.validation.requireFinite
import com.topocalcbtp.engine.validation.requireNonNegative
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.cos

data class Point2D(val id: String, val x: Double, val y: Double) {
    init {
        require(id.isNotBlank()) { "Point id must not be blank" }
        requireFinite("x", x)
        requireFinite("y", y)
    }
}

data class Point3D(val id: String, val x: Double, val y: Double, val z: Double) {
    init {
        require(id.isNotBlank()) { "Point id must not be blank" }
        requireFinite("x", x)
        requireFinite("y", y)
        requireFinite("z", z)
    }
}

data class PolarObservation(val distanceMetres: Double, val gisementDegrees: Double) {
    init { requireNonNegative("distanceMetres", distanceMetres); requireFinite("gisementDegrees", gisementDegrees) }
}

data class InverseResult(val distanceMetres: Double, val gisementDegrees: Double)

/**
 * Coordinate convention used throughout this engine:
 * X = Easting, Y = Northing; gisement is clockwise from grid North (positive Y).
 * Therefore dX = D*sin(G), dY = D*cos(G).
 */
object Coordinates {
    fun forward(origin: Point2D, observation: PolarObservation, pointId: String): Point2D {
        val g = Math.toRadians(Angles.normalize360(observation.gisementDegrees))
        return Point2D(
            pointId,
            origin.x + observation.distanceMetres * sin(g),
            origin.y + observation.distanceMetres * cos(g)
        )
    }

    fun inverse(from: Point2D, to: Point2D): InverseResult {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val distance = hypot(dx, dy)
        val gisement = if (distance == 0.0) 0.0 else Angles.normalize360(Math.toDegrees(atan2(dx, dy)))
        return InverseResult(distance, gisement)
    }

    fun distance2D(a: Point2D, b: Point2D): Double = hypot(b.x - a.x, b.y - a.y)
}
