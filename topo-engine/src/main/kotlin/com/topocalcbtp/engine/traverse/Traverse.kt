package com.topocalcbtp.engine.traverse

import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.angles.Angles
import com.topocalcbtp.engine.validation.requirePositive
import com.topocalcbtp.engine.validation.requireFinite
import kotlin.math.hypot

/** Each segment is defined by a gisement clockwise from North and a horizontal distance. */
data class TraverseSegment(
    val fromPointId: String,
    val toPointId: String,
    val distanceMetres: Double,
    val gisementDegrees: Double
) {
    init {
        require(fromPointId.isNotBlank() && toPointId.isNotBlank()) { "Segment point ids must not be blank" }
        require(fromPointId != toPointId) { "A traverse segment cannot start and end at the same point id" }
        requirePositive("distanceMetres", distanceMetres)
        requireFinite("gisementDegrees", gisementDegrees)
    }
}

enum class TraverseAdjustment { NONE, BOWDITCH }

data class TraversePointResult(
    val point: Point2D,
    val cumulativeDistanceMetres: Double,
    val correctionXMetres: Double,
    val correctionYMetres: Double
)

data class TraverseResult(
    val points: List<TraversePointResult>,
    val misclosureXMetres: Double?,
    val misclosureYMetres: Double?,
    val linearMisclosureMetres: Double?,
    val totalLengthMetres: Double,
    val relativePrecision: Double?
)

object Traverses {
    /**
     * Computes an open or closed traverse. When a known end point is supplied, coordinate misclosure
     * is calculated. Bowditch corrections are proportional to each segment length.
     * A closed traverse is represented by knownEnd having the same coordinates as the start point.
     */
    fun compute(
        start: Point2D,
        segments: List<TraverseSegment>,
        knownEnd: Point2D? = null,
        adjustment: TraverseAdjustment = TraverseAdjustment.NONE
    ): TraverseResult {
        require(segments.isNotEmpty()) { "At least one traverse segment is required" }
        require(segments.first().fromPointId == start.id) { "First segment must start at '${start.id}'" }
        segments.zipWithNext().forEach { (a, b) ->
            require(a.toPointId == b.fromPointId) { "Traverse is disconnected between '${a.toPointId}' and '${b.fromPointId}'" }
        }
        knownEnd?.let { require(it.id == segments.last().toPointId) { "Known end point id must match last segment end id" } }

        val totalLength = segments.sumOf { it.distanceMetres }
        val rawCoordinates = mutableListOf(start)
        var x = start.x
        var y = start.y
        var chainage = 0.0
        segments.forEach { segment ->
            val radians = Math.toRadians(Angles.normalize360(segment.gisementDegrees))
            x += segment.distanceMetres * kotlin.math.sin(radians)
            y += segment.distanceMetres * kotlin.math.cos(radians)
            chainage += segment.distanceMetres
            rawCoordinates += Point2D(segment.toPointId, x, y)
        }

        val fx = knownEnd?.let { x - it.x }
        val fy = knownEnd?.let { y - it.y }
        val linear = if (fx != null && fy != null) hypot(fx, fy) else null
        val relative = if (linear != null && linear > 0.0 && totalLength > 0.0) totalLength / linear else null

        val results = mutableListOf<TraversePointResult>()
        results += TraversePointResult(start, 0.0, 0.0, 0.0)
        var segmentChainage = 0.0
        segments.forEachIndexed { index, segment ->
            segmentChainage += segment.distanceMetres
            val raw = rawCoordinates[index + 1]
            val fraction = if (totalLength == 0.0) 0.0 else segmentChainage / totalLength
            val correctionX = if (adjustment == TraverseAdjustment.BOWDITCH && fx != null) -fx * fraction else 0.0
            val correctionY = if (adjustment == TraverseAdjustment.BOWDITCH && fy != null) -fy * fraction else 0.0
            results += TraversePointResult(
                Point2D(raw.id, raw.x + correctionX, raw.y + correctionY),
                segmentChainage, correctionX, correctionY
            )
        }

        return TraverseResult(results, fx, fy, linear, totalLength, relative)
    }
}

/** Result of carrying gisements through observed horizontal angles. */
data class AngularClosureResult(
    /** Gisement of each traverse leg, after angular correction when a closing gisement was given. */
    val legGisementsDegrees: List<Double>,
    /** Raw (uncorrected) leg gisements. */
    val rawLegGisementsDegrees: List<Double>,
    /** Computed closing gisement minus known closing gisement, in (-180, 180]; null for an open traverse. */
    val angularMisclosureDegrees: Double?,
    /** Correction added to each observed angle (equal distribution); 0 for an open traverse. */
    val correctionPerAngleDegrees: Double
)

object AngularTraverse {
    /**
     * Carries gisements along a traverse from observed horizontal angles.
     *
     * Each angle is read CLOCKWISE at a station, from the back direction to the forward direction
     * (the usual reading on a theodolite graduated clockwise).
     *  - angles[0] is read at the start station, from its known reference direction
     *    (gisement [startReferenceGisementDegrees], start station -> reference point) to the first leg.
     *  - angles[i] for the following stations: G(i -> i+1) = G(i-1 -> i) + 180 + angle[i].
     *  - If [knownClosingGisementDegrees] is given (end station -> closing reference point),
     *    the LAST angle is the closing angle read at the end station from the last leg's back
     *    direction to that reference; the angular misclosure is distributed equally over all angles.
     *    For a closed loop returning to the start station, use the start reference direction as closing
     *    reference and its known gisement (start station -> reference point).
     */
    fun carryGisements(
        startReferenceGisementDegrees: Double,
        anglesClockwiseDegrees: List<Double>,
        knownClosingGisementDegrees: Double? = null
    ): AngularClosureResult {
        requireFinite("startReferenceGisementDegrees", startReferenceGisementDegrees)
        anglesClockwiseDegrees.forEach { requireFinite("angle", it) }
        knownClosingGisementDegrees?.let { requireFinite("knownClosingGisementDegrees", it) }
        val minAngles = if (knownClosingGisementDegrees != null) 2 else 1
        require(anglesClockwiseDegrees.size >= minAngles) { "At least $minAngles observed angles are required" }

        // Raw directions: dir[0] = first leg, dir[k] = leg k (or closing direction for the last one).
        val raw = ArrayList<Double>(anglesClockwiseDegrees.size)
        var g = Angles.normalize360(startReferenceGisementDegrees + anglesClockwiseDegrees[0])
        raw += g
        for (i in 1 until anglesClockwiseDegrees.size) {
            g = Angles.normalize360(g + 180.0 + anglesClockwiseDegrees[i])
            raw += g
        }

        if (knownClosingGisementDegrees == null) {
            return AngularClosureResult(raw, raw, null, 0.0)
        }
        val n = anglesClockwiseDegrees.size
        var misclosure = Angles.normalize180(raw.last() - knownClosingGisementDegrees)
        if (misclosure == -180.0) misclosure = 180.0
        val perAngle = -misclosure / n
        // Direction k depends on angles 0..k, so it receives (k + 1) corrections.
        val corrected = raw.mapIndexed { k, d -> Angles.normalize360(d + perAngle * (k + 1)) }
        val legsRaw = raw.dropLast(1)
        val legs = corrected.dropLast(1)
        return AngularClosureResult(legs, legsRaw, misclosure, perAngle)
    }

    /**
     * Angular closure tolerance T = k * sqrt(n), with k in the same unit as returned
     * (e.g. seconds or centigrades); k comes from the project specification.
     */
    fun angularTolerance(kPerSqrtStation: Double, numberOfAngles: Int): Double {
        requirePositive("kPerSqrtStation", kPerSqrtStation)
        require(numberOfAngles > 0) { "numberOfAngles must be positive" }
        return kPerSqrtStation * kotlin.math.sqrt(numberOfAngles.toDouble())
    }
}

enum class TraverseKind {
    /** Encadré: starts on a known point and closes on another known point with a known closing direction. */
    CLOSED_ON_KNOWN_POINT,
    /** Fermé: returns to the start point and re-observes the start reference direction. */
    LOOP,
    /** Ouvert (antenne): no check at all. */
    OPEN
}

/** One traverse leg as observed in the field. */
data class TraverseLegObservation(
    val stationId: String,
    /** Clockwise angle at [stationId] from the back direction to the forward direction (degrees). */
    val angleDegrees: Double,
    val horizontalDistanceMetres: Double,
    val nextStationId: String
)

data class AngularTraverseSolution(
    val kind: TraverseKind,
    val angular: AngularClosureResult,
    val traverse: TraverseResult
)

object TraverseWorkflow {
    /**
     * Full traverse computation: carries gisements from the observed angles, distributes the angular
     * misclosure equally, then computes coordinates with Bowditch adjustment of the linear misclosure.
     *
     * @param startReferenceGisementDegrees gisement start station -> its reference (orientation) point.
     * @param closingAngleDegrees angle read at the last station from the last leg's back direction to the
     *        closing reference (required for [TraverseKind.CLOSED_ON_KNOWN_POINT] and [TraverseKind.LOOP]).
     * @param knownEnd known end point ([TraverseKind.CLOSED_ON_KNOWN_POINT] only).
     * @param closingReferenceGisementDegrees gisement end point -> closing reference
     *        ([TraverseKind.CLOSED_ON_KNOWN_POINT] only; for a loop the start reference is reused).
     */
    fun solve(
        kind: TraverseKind,
        start: Point2D,
        startReferenceGisementDegrees: Double,
        legs: List<TraverseLegObservation>,
        closingAngleDegrees: Double? = null,
        knownEnd: Point2D? = null,
        closingReferenceGisementDegrees: Double? = null
    ): AngularTraverseSolution {
        require(legs.isNotEmpty()) { "At least one traverse leg is required" }
        require(legs.first().stationId == start.id) { "The first leg must start at '${start.id}'" }
        legs.zipWithNext().forEach { (a, b) ->
            require(a.nextStationId == b.stationId) { "Traverse is disconnected between '${a.nextStationId}' and '${b.stationId}'" }
        }
        val angles = legs.map { it.angleDegrees }
        val (closingGisement, endPoint) = when (kind) {
            TraverseKind.OPEN -> null to null
            TraverseKind.LOOP -> {
                require(legs.last().nextStationId == start.id) { "A loop traverse must end on the start point '${start.id}'" }
                require(legs.size >= 2) { "A loop traverse needs at least two legs" }
                startReferenceGisementDegrees to start
            }
            TraverseKind.CLOSED_ON_KNOWN_POINT -> {
                requireNotNull(knownEnd) { "The known end point is required" }
                requireNotNull(closingReferenceGisementDegrees) { "The closing reference direction is required" }
                require(legs.last().nextStationId == knownEnd.id) { "The last leg must end on the known point '${knownEnd.id}'" }
                closingReferenceGisementDegrees to knownEnd
            }
        }
        if (kind != TraverseKind.OPEN) requireNotNull(closingAngleDegrees) { "The closing angle is required" }
        val allAngles = if (kind == TraverseKind.OPEN) angles else angles + closingAngleDegrees!!
        val angular = AngularTraverse.carryGisements(startReferenceGisementDegrees, allAngles, closingGisement)
        val segments = legs.mapIndexed { i, leg ->
            TraverseSegment(leg.stationId, leg.nextStationId, leg.horizontalDistanceMetres, angular.legGisementsDegrees[i])
        }
        val traverse = Traverses.compute(
            start, segments, endPoint,
            if (kind == TraverseKind.OPEN) TraverseAdjustment.NONE else TraverseAdjustment.BOWDITCH
        )
        return AngularTraverseSolution(kind, angular, traverse)
    }
}
