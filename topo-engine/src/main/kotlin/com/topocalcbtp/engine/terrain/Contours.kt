package com.topocalcbtp.engine.terrain

import com.topocalcbtp.engine.coordinates.Point2D
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/** Continuous contour polyline. The first and last coordinates coincide when closed=true. */
data class ContourPolyline(
    val elevationMetres: Double,
    val points: List<Point2D>,
    val closed: Boolean
) {
    init { require(points.size >= 2) }

    /** Optional Chaikin smoothing; open-line endpoints are preserved. This is cartographic only. */
    fun smoothed(iterations: Int = 1, cutRatio: Double = 0.25): ContourPolyline {
        require(iterations >= 0)
        require(cutRatio in 0.0..0.5)
        var current=points
        repeat(iterations) {
            if(current.size<3) return@repeat
            val next=mutableListOf<Point2D>()
            if(!closed) next.add(current.first())
            val edgeCount=if(closed) current.size-1 else current.size-1
            for(i in 0 until edgeCount) {
                val a=current[i]; val b=current[i+1]
                next.add(Point2D("C",a.x*(1-cutRatio)+b.x*cutRatio,a.y*(1-cutRatio)+b.y*cutRatio))
                next.add(Point2D("C",a.x*cutRatio+b.x*(1-cutRatio),a.y*cutRatio+b.y*(1-cutRatio)))
            }
            if(!closed) next.add(current.last()) else next.add(next.first())
            current=next
        }
        return copy(points=current)
    }

    /** Places one elevation label at the half-length position of the contour. */
    fun label(precision: Int = 2): ContourLabel {
        require(precision in 0..6)
        val lengths=points.zipWithNext { a,b -> hypot(b.x-a.x,b.y-a.y) }
        val total=lengths.sum()
        var remaining=total/2.0
        var position=points.first()
        for(i in lengths.indices) {
            val length=lengths[i]
            if(length>0 && remaining<=length) {
                val f=remaining/length; val a=points[i]; val b=points[i+1]
                position=Point2D("LABEL",a.x+f*(b.x-a.x),a.y+f*(b.y-a.y)); break
            }
            remaining-=length
        }
        val format="%.${precision}f".format(java.util.Locale.US,elevationMetres)
        return ContourLabel(elevationMetres,position,format)
    }
}

data class ContourLabel(val elevationMetres:Double,val position:Point2D,val text:String)

object Contours {
    fun segments(tin:TinSurface,intervalMetres:Double,baseElevationMetres:Double?=null):List<ContourSegment> {
        require(intervalMetres>0.0 && intervalMetres.isFinite()) { "Contour interval must be positive" }
        val minZ=tin.triangles.minOf { minOf(it.a.z,it.b.z,it.c.z) }
        val maxZ=tin.triangles.maxOf { maxOf(it.a.z,it.b.z,it.c.z) }
        val base=baseElevationMetres ?: floor(minZ/intervalMetres)*intervalMetres
        require(base.isFinite())
        val count=floor((maxZ-base)/intervalMetres).toInt()+1
        require(count in 0..100_000) { "Contour request creates too many elevation levels" }
        val result=mutableListOf<ContourSegment>()
        for(levelIndex in 0 until count) {
            val level=base+levelIndex*intervalMetres
            for(t in tin.triangles) {
                val hits=mutableListOf<Point2D>()
                for((p,q) in listOf(t.a to t.b,t.b to t.c,t.c to t.a)) {
                    // Half-open rule avoids generating the same vertex from both adjacent triangles.
                    if((p.z <= level + 1e-10 && q.z > level + 1e-10) ||
                        (q.z <= level + 1e-10 && p.z > level + 1e-10)) {
                        val f=if(abs(q.z-p.z)<1e-15) 0.0 else ((level-p.z)/(q.z-p.z)).coerceIn(0.0,1.0)
                        val hit=Point2D("C",p.x+f*(q.x-p.x),p.y+f*(q.y-p.y))
                        if(hits.none { hypot(it.x-hit.x,it.y-hit.y)<1e-8 }) hits.add(hit)
                    }
                }
                if(hits.size==2 && hypot(hits[0].x-hits[1].x,hits[0].y-hits[1].y)>1e-9)
                    result.add(ContourSegment(level,hits[0],hits[1]))
            }
        }
        return result
    }

    /** Stitches triangle contour segments into continuous lines by endpoint tolerance. */
    fun stitch(segments:List<ContourSegment>,toleranceMetres:Double=1e-7):List<ContourPolyline> {
        require(toleranceMetres>0.0 && toleranceMetres.isFinite())
        if(segments.isEmpty()) return emptyList()
        val output=mutableListOf<ContourPolyline>()
        for((level,group) in segments.groupBy { it.elevationMetres }) {
            data class Edge(val a:Point2D,val b:Point2D)
            fun key(p:Point2D)="${kotlin.math.round(p.x/toleranceMetres).toLong()}:${kotlin.math.round(p.y/toleranceMetres).toLong()}"
            val edges=group.map { Edge(it.start,it.end) }
            val adjacency=mutableMapOf<String,MutableList<Int>>()
            edges.forEachIndexed { i,e -> adjacency.getOrPut(key(e.a)){mutableListOf()}.add(i); adjacency.getOrPut(key(e.b)){mutableListOf()}.add(i) }
            val used=BooleanArray(edges.size)
            fun walk(startKey:String,firstEdge:Int):ContourPolyline {
                val pts=mutableListOf<Point2D>()
                var edgeIndex=firstEdge; var currentKey=startKey; var closed=false
                var point=if(key(edges[edgeIndex].a)==currentKey) edges[edgeIndex].a else edges[edgeIndex].b
                pts.add(point)
                while(true) {
                    if(used[edgeIndex]) break
                    used[edgeIndex]=true
                    val e=edges[edgeIndex]
                    val next=if(key(e.a)==currentKey) e.b else e.a
                    pts.add(next)
                    currentKey=key(next)
                    if(currentKey==key(pts.first())) { closed=true; break }
                    val nextEdge=adjacency[currentKey].orEmpty().firstOrNull { !used[it] } ?: break
                    edgeIndex=nextEdge
                }
                if(closed && pts.size>2) pts[pts.lastIndex]=pts.first()
                val unique=pts.distinctBy { key(it) }
                return ContourPolyline(level,if(closed) unique+unique.first() else unique,closed)
            }
            // Start at boundary/branch nodes first; remaining components are closed loops.
            for((node,incident) in adjacency) if(incident.size!=2) for(e in incident) if(!used[e]) output.add(walk(node,e))
            for(i in edges.indices) if(!used[i]) output.add(walk(key(edges[i].a),i))
        }
        return output
    }
}
