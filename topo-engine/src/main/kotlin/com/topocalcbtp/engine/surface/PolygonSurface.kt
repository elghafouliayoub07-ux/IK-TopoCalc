package com.topocalcbtp.engine.surface

import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.validation.requireFinite
import kotlin.math.abs
import kotlin.math.hypot

data class PolygonMetrics(
    /** Positive area independent of vertex order, in square metres. */
    val areaSquareMetres: Double,
    val perimeterMetres: Double,
    /** Signed area: positive for counter-clockwise order in X-East/Y-North coordinates. */
    val signedAreaSquareMetres: Double
)

object PolygonSurface {
    /** Shoelace area and boundary perimeter for a simple polygon. Vertices must not repeat the first point at the end. */
    fun calculate(vertices: List<Point2D>): PolygonMetrics {
        require(vertices.size >= 3) { "A polygon needs at least three vertices" }
        require(vertices.map { it.id }.distinct().size == vertices.size) { "Polygon vertex ids must be unique" }
        var twiceSignedArea = 0.0
        var perimeter = 0.0
        for (i in vertices.indices) {
            val current = vertices[i]
            val next = vertices[(i + 1) % vertices.size]
            twiceSignedArea += current.x * next.y - next.x * current.y
            perimeter += hypot(next.x - current.x, next.y - current.y)
        }
        val signedArea = twiceSignedArea / 2.0
        requireFinite("signedArea", signedArea)
        require(abs(signedArea) > 1e-12) { "Polygon area is zero; vertices may be collinear" }
        return PolygonMetrics(abs(signedArea), perimeter, signedArea)
    }
}
