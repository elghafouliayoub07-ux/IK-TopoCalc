package com.topocalcbtp.engine.terrain

import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.coordinates.Point3D
import kotlin.math.abs
import kotlin.math.hypot

/** Delaunay TIN in a local projected metric CRS. */
data class TinTriangle(val a: Point3D, val b: Point3D, val c: Point3D) {
    val planAreaSquareMetres: Double get() = abs((b.x-a.x)*(c.y-a.y)-(c.x-a.x)*(b.y-a.y))/2.0
    val minX: Double get() = minOf(a.x,b.x,c.x)
    val maxX: Double get() = maxOf(a.x,b.x,c.x)
    val minY: Double get() = minOf(a.y,b.y,c.y)
    val maxY: Double get() = maxOf(a.y,b.y,c.y)
}

data class Breakline(val pointIds: List<String>) {
    init { require(pointIds.size >= 2) { "A breakline needs at least two point ids" } }
}

data class TinBuildOptions(
    val duplicateToleranceMetres: Double = 1e-8,
    val geometryToleranceMetres: Double = 1e-10,
    val maximumPoints: Int = 10_000
) {
    init {
        require(duplicateToleranceMetres > 0.0 && duplicateToleranceMetres.isFinite())
        require(geometryToleranceMetres > 0.0 && geometryToleranceMetres.isFinite())
        require(maximumPoints >= 3)
    }
}

data class TinSurface(
    val triangles: List<TinTriangle>,
    /** Edges forced into the triangulation by the supplied breaklines. */
    val constrainedEdges: Set<Pair<String,String>> = emptySet()
) {
    init { require(triangles.isNotEmpty()) { "TIN has no triangles" } }

    /** Returns null outside the convex hull or on a degenerate triangle. */
    fun interpolate(x: Double, y: Double): Double? {
        // A bounding-box prefilter avoids barycentric work for most triangles on larger surfaces.
        for (t in triangles) {
            if (x < t.minX-1e-9 || x > t.maxX+1e-9 || y < t.minY-1e-9 || y > t.maxY+1e-9) continue
            val den = (t.b.y-t.c.y)*(t.a.x-t.c.x)+(t.c.x-t.b.x)*(t.a.y-t.c.y)
            if (abs(den) < 1e-12) continue
            val u = ((t.b.y-t.c.y)*(x-t.c.x)+(t.c.x-t.b.x)*(y-t.c.y))/den
            val v = ((t.c.y-t.a.y)*(x-t.c.x)+(t.a.x-t.c.x)*(y-t.c.y))/den
            val w = 1.0-u-v
            if (u >= -1e-10 && v >= -1e-10 && w >= -1e-10)
                return u*t.a.z + v*t.b.z + w*t.c.z
        }
        return null
    }

    fun contourSegments(intervalMetres: Double, baseElevationMetres: Double? = null): List<ContourSegment> =
        Contours.segments(this, intervalMetres, baseElevationMetres)

    fun contourPolylines(
        intervalMetres: Double,
        baseElevationMetres: Double? = null,
        stitchToleranceMetres: Double = 1e-7
    ): List<ContourPolyline> = Contours.stitch(contourSegments(intervalMetres, baseElevationMetres), stitchToleranceMetres)
}

data class ContourSegment(val elevationMetres: Double, val start: Point2D, val end: Point2D)

object TinBuilder {
    private data class P(val x:Double,val y:Double,val z:Double,val id:String)
    private data class Tri(val a:Int,val b:Int,val c:Int,val circleX:Double,val circleY:Double,val circleRadiusSquared:Double)
    private data class Edge(val a:Int,val b:Int) {
        fun key()=if(a<b) "$a:$b" else "$b:$a"
        fun pair()=if(a<b) a to b else b to a
    }

    fun build(points: List<Point3D>, breaklines: List<Breakline> = emptyList(), options: TinBuildOptions = TinBuildOptions()): TinSurface {
        require(points.size >= 3) { "At least three points are required" }
        require(points.size <= options.maximumPoints) { "TIN point limit (${options.maximumPoints}) exceeded" }
        require(points.map { it.id }.distinct().size == points.size) { "Point ids must be unique" }
        val tol2 = options.duplicateToleranceMetres * options.duplicateToleranceMetres
        // Spatial buckets keep duplicate checking near-linear instead of comparing every point pair.
        val buckets=mutableMapOf<Pair<Long,Long>,MutableList<Int>>()
        for(i in points.indices) {
            val p=points[i]
            val gx=kotlin.math.floor(p.x/options.duplicateToleranceMetres).toLong()
            val gy=kotlin.math.floor(p.y/options.duplicateToleranceMetres).toLong()
            for(dx in -1L..1L) for(dy in -1L..1L) {
                for(j in buckets[gx+dx to gy+dy].orEmpty()) {
                    val ddx=p.x-points[j].x; val ddy=p.y-points[j].y
                    require(ddx*ddx+ddy*ddy > tol2) { "Duplicate or near-duplicate plan coordinates: '${p.id}' and '${points[j].id}'" }
                }
            }
            buckets.getOrPut(gx to gy){mutableListOf()}.add(i)
        }
        val minX=points.minOf{it.x}; val maxX=points.maxOf{it.x}
        val minY=points.minOf{it.y}; val maxY=points.maxOf{it.y}
        val span=maxOf(maxX-minX,maxY-minY)
        require(span > options.geometryToleranceMetres) { "Points are coincident or have insufficient extent" }
        val cx=(minX+maxX)/2; val cy=(minY+maxY)/2; val r=span*1000+1
        val all=points.map { P(it.x,it.y,it.z,it.id) }.toMutableList()
        val s0=all.size
        all.add(P(cx-2*r,cy-r,0.0,"__s0")); all.add(P(cx+2*r,cy-r,0.0,"__s1")); all.add(P(cx,cy+2*r,0.0,"__s2"))
        var tris= mutableListOf(makeTriangle(s0,s0+1,s0+2,all))

        // Deterministic Morton ordering improves insertion locality for a spatial point cloud.
        val insertionOrder = points.indices.sortedBy { mortonKey(points[it].x,points[it].y,minX,minY,span) }
        for (i in insertionOrder) {
            val bad=tris.filter { insideCircumcircle(it,all[i]) }
            if (bad.isEmpty()) continue
            val edgeMap=linkedMapOf<String,Pair<Edge,Int>>()
            for(t in bad) for(e in listOf(Edge(t.a,t.b),Edge(t.b,t.c),Edge(t.c,t.a))) {
                val k=e.key(); val old=edgeMap[k]; edgeMap[k]=e to ((old?.second ?: 0)+1)
            }
            tris.removeAll(bad.toSet())
            for((edge,count) in edgeMap.values) if(count==1) {
                var a=edge.a; var b=edge.b
                if(orientation(all[a],all[b],all[i])<0) { val tmp=a;a=b;b=tmp }
                if(abs(orientation(all[a],all[b],all[i]))>options.geometryToleranceMetres*options.geometryToleranceMetres) tris.add(makeTriangle(a,b,i,all))
            }
        }
        tris = tris.filter { it.a<points.size && it.b<points.size && it.c<points.size }.toMutableList()
        require(tris.isNotEmpty()) { "Could not triangulate points; check for collinearity or poor geometry" }

        val idToIndex = points.mapIndexed { index, p -> p.id to index }.toMap()
        val constrained = linkedSetOf<Pair<Int,Int>>()
        for (line in breaklines) {
            val ids=line.pointIds
            require(ids.distinct().size == ids.size) { "A breakline cannot repeat a point id" }
            for (id in ids) require(idToIndex.containsKey(id)) { "Breakline point '$id' is not in TIN points" }
            for (k in 0 until ids.lastIndex) {
                val ia=idToIndex.getValue(ids[k]); val ib=idToIndex.getValue(ids[k+1])
                require(ia!=ib) { "Breakline contains a zero-length segment" }
                val a=all[ia]; val b=all[ib]
                val dx=b.x-a.x; val dy=b.y-a.y; val len2=dx*dx+dy*dy
                val split=points.indices.filter { idx ->
                    if(idx==ia || idx==ib) false else {
                        val p=all[idx]; val cross=abs(dx*(p.y-a.y)-dy*(p.x-a.x))
                        val dot=(p.x-a.x)*dx+(p.y-a.y)*dy
                        cross <= options.geometryToleranceMetres*maxOf(1.0, kotlin.math.sqrt(len2)) && dot>0.0 && dot<len2
                    }
                }.sortedBy { idx -> ((all[idx].x-a.x)*dx+(all[idx].y-a.y)*dy)/len2 }
                val chain=listOf(ia)+split+listOf(ib)
                for (m in 0 until chain.lastIndex) {
                    val edge=Edge(chain[m],chain[m+1])
                    for((ca,cb) in constrained) {
                        require(!properIntersection(all[edge.a],all[edge.b],all[ca],all[cb],options.geometryToleranceMetres)) {
                            "Breaklines cross without a shared surveyed junction point; node the crossing before triangulation"
                        }
                    }
                    insertConstraint(tris, all, edge, constrained, options.geometryToleranceMetres)
                    constrained.add(edge.pair())
                }
            }
        }
        val output=tris.map { TinTriangle(points[it.a],points[it.b],points[it.c]) }
        val constrainedIds=constrained.map { (a,b) ->
            val x=points[a].id; val y=points[b].id
            if(x<y) x to y else y to x
        }.toSet()
        return TinSurface(output, constrainedIds)
    }

    private fun insertConstraint(tris:MutableList<Tri>, pts:List<P>, target:Edge, constrained:Set<Pair<Int,Int>>, eps:Double) {
        var iterations=0
        val maxIterations=maxOf(200, tris.size*10)
        while (!tris.any { hasEdge(it,target) }) {
            if(iterations++ > maxIterations) throw IllegalArgumentException("Could not enforce breakline edge ${pts[target.a].id}-${pts[target.b].id}; check crossing or degenerate breaklines")
            val candidates=linkedMapOf<String,Pair<Edge,MutableList<Int>>>()
            tris.forEachIndexed { ti,t -> listOf(Edge(t.a,t.b),Edge(t.b,t.c),Edge(t.c,t.a)).forEach { e ->
                val old=candidates[e.key()]
                if(old==null) candidates[e.key()]=e to mutableListOf(ti) else old.second.add(ti)
            }}
            val crossing=candidates.values.firstOrNull { (e, adjacent) ->
                adjacent.size==2 && e.pair() !in constrained && e.a!=target.a && e.a!=target.b && e.b!=target.a && e.b!=target.b &&
                    properIntersection(pts[target.a],pts[target.b],pts[e.a],pts[e.b],eps)
            } ?: throw IllegalArgumentException("Breakline could not be inserted: ${pts[target.a].id}-${pts[target.b].id}")
            val e=crossing.first; val adjacent=crossing.second
            val t1=tris[adjacent[0]]; val t2=tris[adjacent[1]]
            val c=listOf(t1.a,t1.b,t1.c).first { it!=e.a && it!=e.b }
            val d=listOf(t2.a,t2.b,t2.c).first { it!=e.a && it!=e.b }
            // A legal diagonal flip requires a convex quadrilateral.
            if(!properIntersection(pts[e.a],pts[e.b],pts[c],pts[d],eps)) {
                throw IllegalArgumentException("Breakline crosses a non-flippable edge; split or revise the breakline")
            }
            val replacement1=orientedTriangle(c,d,e.a,pts)
            val replacement2=orientedTriangle(d,c,e.b,pts)
            val hi=maxOf(adjacent[0],adjacent[1]); val lo=minOf(adjacent[0],adjacent[1])
            tris.removeAt(hi); tris.removeAt(lo)
            tris.add(replacement1); tris.add(replacement2)
        }
    }

    private fun orientedTriangle(a:Int,b:Int,c:Int,pts:List<P>):Tri =
        if(orientation(pts[a],pts[b],pts[c])>=0) makeTriangle(a,b,c,pts) else makeTriangle(b,a,c,pts)
    private fun hasEdge(t:Tri,e:Edge)=listOf(Edge(t.a,t.b),Edge(t.b,t.c),Edge(t.c,t.a)).any { it.key()==e.key() }
    private fun orientation(a:P,b:P,c:P)=(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
    private fun properIntersection(a:P,b:P,c:P,d:P,eps:Double):Boolean {
        val o1=orientation(a,b,c); val o2=orientation(a,b,d); val o3=orientation(c,d,a); val o4=orientation(c,d,b)
        return ((o1>eps && o2 < -eps)||(o1 < -eps && o2>eps)) && ((o3>eps && o4 < -eps)||(o3 < -eps && o4>eps))
    }
    private fun makeTriangle(a:Int,b:Int,c:Int,pts:List<P>):Tri {
        val p=pts[a]; val q=pts[b]; val r=pts[c]
        val bx=q.x-p.x; val by=q.y-p.y; val cx=r.x-p.x; val cy=r.y-p.y
        val d=2.0*(bx*cy-by*cx)
        require(abs(d)>1e-18) { "Degenerate triangle in TIN triangulation" }
        val b2=bx*bx+by*by; val c2=cx*cx+cy*cy
        val ux=(cy*b2-by*c2)/d; val uy=(bx*c2-cx*b2)/d
        val centerX=p.x+ux; val centerY=p.y+uy
        val radiusSquared=ux*ux+uy*uy
        return Tri(a,b,c,centerX,centerY,radiusSquared)
    }
    private fun insideCircumcircle(t:Tri,p:P):Boolean {
        val dx=p.x-t.circleX; val dy=p.y-t.circleY
        val d2=dx*dx+dy*dy
        return d2 <= t.circleRadiusSquared + maxOf(1.0,t.circleRadiusSquared)*1e-12
    }
    private fun mortonKey(x:Double,y:Double,minX:Double,minY:Double,span:Double):Long {
        val scale=2_097_151.0/span
        val qx=(((x-minX)*scale).toLong()).coerceIn(0L,2_097_151L)
        val qy=(((y-minY)*scale).toLong()).coerceIn(0L,2_097_151L)
        fun spread(v0:Long):Long {
            var v=v0 and 0x1fffffL
            v=(v or (v shl 32)) and 0x1f00000000ffffL
            v=(v or (v shl 16)) and 0x1f0000ff0000ffL
            v=(v or (v shl 8)) and 0x100f00f00f00f00fL
            v=(v or (v shl 4)) and 0x10c30c30c30c30c3L
            v=(v or (v shl 2)) and 0x1249249249249249L
            return v
        }
        return spread(qx) or (spread(qy) shl 1)
    }
}
