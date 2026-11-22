package com.topocalcbtp.engine.intersection

import com.topocalcbtp.engine.angles.Angles
import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.validation.DEFAULT_TOLERANCE_METRES
import com.topocalcbtp.engine.validation.requireFinite
import com.topocalcbtp.engine.validation.requireNonNegative
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class NoIntersectionException(message: String) : IllegalArgumentException(message)

data class Ray(val station: Point2D, val gisementDegrees: Double) {
    init { requireFinite("gisementDegrees", gisementDegrees) }
}

data class RayIntersectionResult(
    val point: Point2D,
    val distanceFromFirstStationMetres: Double,
    val distanceFromSecondStationMetres: Double
)

data class DistanceObservation(val knownPoint: Point2D, val measuredDistanceMetres: Double) {
    init { requireNonNegative("measuredDistanceMetres", measuredDistanceMetres) }
}

data class ResectionResult(
    val estimatedPoint: Point2D,
    val iterations: Int,
    val residualsMetres: List<Double>,
    val rootMeanSquareMetres: Double
)

object Intersections {
    /** Intersection of two forward rays. Gisements are clockwise from grid North. */
    fun twoRays(first: Ray, second: Ray, pointId: String = "I", tolerance: Double = DEFAULT_TOLERANCE_METRES): RayIntersectionResult {
        requireNonNegative("tolerance", tolerance)
        val a = direction(first.gisementDegrees)
        val b = direction(second.gisementDegrees)
        val qpx = second.station.x - first.station.x
        val qpy = second.station.y - first.station.y
        val determinant = cross(a.first, a.second, b.first, b.second)
        if (abs(determinant) <= 1e-14) throw NoIntersectionException("The two sight lines are parallel or nearly parallel")
        val t = cross(qpx, qpy, b.first, b.second) / determinant
        val u = cross(qpx, qpy, a.first, a.second) / determinant
        if (t < -tolerance || u < -tolerance) {
            throw NoIntersectionException("The infinite lines intersect, but not on both forward rays")
        }
        val x = first.station.x + t * a.first
        val y = first.station.y + t * a.second
        return RayIntersectionResult(Point2D(pointId, x, y), t, u)
    }

    /**
     * Distance resection by iterative least squares (Gauss-Newton).
     * Requires at least three known points and an initial coordinate near the expected solution.
     */
    fun distanceResection(
        observations: List<DistanceObservation>,
        initialApproximation: Point2D,
        pointId: String = "STATION",
        maxIterations: Int = 100,
        coordinateToleranceMetres: Double = 1e-8
    ): ResectionResult {
        require(observations.size >= 3) { "At least three distance observations are required" }
        require(maxIterations > 0) { "maxIterations must be positive" }
        requireFinite("coordinateToleranceMetres", coordinateToleranceMetres)
        require(coordinateToleranceMetres > 0.0) { "coordinateToleranceMetres must be positive" }
        require(observations.map { it.knownPoint.id }.distinct().size == observations.size) {
            "Known point ids must be unique"
        }

        var x = initialApproximation.x
        var y = initialApproximation.y
        var iterations = 0
        for (iteration in 1..maxIterations) {
            var nxx = 0.0; var nxy = 0.0; var nyy = 0.0
            var bx = 0.0; var by = 0.0
            observations.forEach { obs ->
                val dx = x - obs.knownPoint.x
                val dy = y - obs.knownPoint.y
                val distance = kotlin.math.hypot(dx, dy)
                if (distance <= 1e-12) throw NoIntersectionException("Approximation coincides with known point '${obs.knownPoint.id}'")
                val residual = distance - obs.measuredDistanceMetres
                val jx = dx / distance
                val jy = dy / distance
                nxx += jx * jx
                nxy += jx * jy
                nyy += jy * jy
                bx += jx * residual
                by += jy * residual
            }
            val determinant = nxx * nyy - nxy * nxy
            if (abs(determinant) <= 1e-14) throw NoIntersectionException("Resection geometry is weak or singular")
            val deltaX = (-bx * nyy + by * nxy) / determinant
            val deltaY = (-by * nxx + bx * nxy) / determinant
            x += deltaX
            y += deltaY
            iterations = iteration
            if (kotlin.math.hypot(deltaX, deltaY) <= coordinateToleranceMetres) break
            if (iteration == maxIterations) throw NoIntersectionException("Resection did not converge within $maxIterations iterations")
        }

        val residuals = observations.map { obs ->
            kotlin.math.hypot(x - obs.knownPoint.x, y - obs.knownPoint.y) - obs.measuredDistanceMetres
        }
        val rms = kotlin.math.sqrt(residuals.sumOf { it * it } / residuals.size)
        return ResectionResult(Point2D(pointId, x, y), iterations, residuals, rms)
    }

    private fun direction(gisementDegrees: Double): Pair<Double, Double> {
        val radians = Math.toRadians(Angles.normalize360(gisementDegrees))
        return sin(radians) to cos(radians)
    }

    private fun cross(ax: Double, ay: Double, bx: Double, by: Double): Double = ax * by - ay * bx
}
