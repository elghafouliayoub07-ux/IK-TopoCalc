package com.topocalcbtp.engine.earthworks

import com.topocalcbtp.engine.terrain.TinSurface
import kotlin.math.abs

/** A plan polygon fragment and signed existing-minus-design elevation difference at each vertex. */
data class DifferenceVertex(val x:Double,val y:Double,val differenceMetres:Double)
data class CutFillPolygon(val vertices:List<DifferenceVertex>,val planAreaSquareMetres:Double,val volumeCubicMetres:Double)
data class SurfaceComparisonResult(
    val cutCubicMetres:Double,
    val fillCubicMetres:Double,
    val netFillMinusCutCubicMetres:Double,
    val overlapAreaSquareMetres:Double,
    val cutPolygons:List<CutFillPolygon>,
    val fillPolygons:List<CutFillPolygon>
)

/** Computes volumes between two piecewise-linear TIN surfaces over their common planimetric area. */
object SurfaceComparison {
    private data class V(val x:Double,val y:Double,val d:Double)
    private data class BBox(val minX:Double,val maxX:Double,val minY:Double,val maxY:Double)

    /** Positive difference (existing > design) is cut; negative difference is fill. */
    fun compare(existing:TinSurface,design:TinSurface,areaToleranceSquareMetres:Double=1e-10):SurfaceComparisonResult {
        require(areaToleranceSquareMetres>=0.0 && areaToleranceSquareMetres.isFinite())
        val existingSorted=existing.triangles.map { it to BBox(it.minX,it.maxX,it.minY,it.maxY) }
        val designSorted=design.triangles.map { it to BBox(it.minX,it.maxX,it.minY,it.maxY) }.sortedBy { it.second.minX }
        val cutPolys=mutableListOf<CutFillPolygon>(); val fillPolys=mutableListOf<CutFillPolygon>()
        var overlap=0.0
        for((te,be) in existingSorted) {
            for((td,bd) in designSorted) {
                if(bd.minX>be.maxX+1e-9) break
                if(bd.maxX<be.minX-1e-9 || bd.minY>be.maxY+1e-9 || bd.maxY<be.minY-1e-9) continue
                val plan=intersectionPolygon(te,td)
                if(plan.size<3) continue
                val area=polygonArea(plan)
                if(area<=areaToleranceSquareMetres) continue
                val withDifference=plan.map { p -> V(p.x,p.y,interpolateTriangle(te,p.x,p.y)-interpolateTriangle(td,p.x,p.y)) }
                overlap+=area
                val cut=clipByDifference(withDifference,positive=true)
                if(cut.size>=3) {
                    val (a,v)=integrate(cut)
                    if(a>areaToleranceSquareMetres && v>1e-12) cutPolys.add(CutFillPolygon(cut.map { DifferenceVertex(it.x,it.y,it.d) },a,v))
                }
                val fill=clipByDifference(withDifference,positive=false)
                if(fill.size>=3) {
                    val (a,v)=integrate(fill)
                    if(a>areaToleranceSquareMetres && v>1e-12) fillPolys.add(CutFillPolygon(fill.map { DifferenceVertex(it.x,it.y,it.d) },a,v))
                }
            }
        }
        val cut=cutPolys.sumOf { it.volumeCubicMetres }; val fill=fillPolys.sumOf { it.volumeCubicMetres }
        return SurfaceComparisonResult(cut,fill,fill-cut,overlap,cutPolys,fillPolys)
    }

    private fun intersectionPolygon(a:com.topocalcbtp.engine.terrain.TinTriangle,b:com.topocalcbtp.engine.terrain.TinTriangle):List<V> {
        var poly=listOf(V(a.a.x,a.a.y,0.0),V(a.b.x,a.b.y,0.0),V(a.c.x,a.c.y,0.0))
        val clip=listOf(b.a,b.b,b.c)
        val orientation=signArea(clip.map { V(it.x,it.y,0.0) })
        for(i in 0..2) {
            val c=clip[i]; val d=clip[(i+1)%3]
            poly=clipPolygon(poly) { p -> orientation*((d.x-c.x)*(p.y-c.y)-(d.y-c.y)*(p.x-c.x)) >= -1e-9 }
            if(poly.isEmpty()) return emptyList()
        }
        return poly
    }

    private fun clipPolygon(input:List<V>,inside:(V)->Boolean):List<V> {
        if(input.isEmpty()) return emptyList()
        val out=mutableListOf<V>(); var prev=input.last(); var prevIn=inside(prev)
        for(cur in input) {
            val curIn=inside(cur)
            if(curIn!=prevIn) {
                // Intersection parameters are calculated in XY using a binary line interpolation against the clipping half-plane.
                val t=intersectionParameter(prev,cur,inside)
                out.add(V(prev.x+t*(cur.x-prev.x),prev.y+t*(cur.y-prev.y),prev.d+t*(cur.d-prev.d)))
            }
            if(curIn) out.add(cur)
            prev=cur; prevIn=curIn
        }
        return dedupe(out)
    }

    private fun intersectionParameter(a:V,b:V,inside:(V)->Boolean):Double {
        var lo=0.0; var hi=1.0; val aIn=inside(a)
        repeat(60) {
            val mid=(lo+hi)/2
            val p=V(a.x+mid*(b.x-a.x),a.y+mid*(b.y-a.y),a.d+mid*(b.d-a.d))
            if(inside(p)==aIn) lo=mid else hi=mid
        }
        return (lo+hi)/2
    }

    private fun clipByDifference(input:List<V>,positive:Boolean):List<V> = clipPolygon(input) { if(positive) it.d>=-1e-12 else it.d<=1e-12 }

    private fun integrate(poly:List<V>):Pair<Double,Double> {
        if(poly.size<3) return 0.0 to 0.0
        var area=0.0; var volume=0.0
        for(i in 1 until poly.lastIndex) {
            val a=poly[0]; val b=poly[i]; val c=poly[i+1]
            val triangleArea=abs((b.x-a.x)*(c.y-a.y)-(c.x-a.x)*(b.y-a.y))/2.0
            area+=triangleArea
            volume+=triangleArea*(a.d+b.d+c.d)/3.0
        }
        return area to abs(volume)
    }

    private fun interpolateTriangle(t:com.topocalcbtp.engine.terrain.TinTriangle,x:Double,y:Double):Double {
        val den=(t.b.y-t.c.y)*(t.a.x-t.c.x)+(t.c.x-t.b.x)*(t.a.y-t.c.y)
        val u=((t.b.y-t.c.y)*(x-t.c.x)+(t.c.x-t.b.x)*(y-t.c.y))/den
        val v=((t.c.y-t.a.y)*(x-t.c.x)+(t.a.x-t.c.x)*(y-t.c.y))/den
        val w=1.0-u-v
        return u*t.a.z+v*t.b.z+w*t.c.z
    }
    private fun polygonArea(p:List<V>)=abs(signArea(p))
    private fun signArea(p:List<V>):Double { var a=0.0; for(i in p.indices) { val n=p[(i+1)%p.size]; a+=p[i].x*n.y-n.x*p[i].y }; return a/2.0 }
    private fun dedupe(p:List<V>):List<V> {
        val out=mutableListOf<V>(); for(v in p) if(out.none { abs(it.x-v.x)<1e-9 && abs(it.y-v.y)<1e-9 }) out.add(v)
        if(out.size>1 && abs(out.first().x-out.last().x)<1e-9 && abs(out.first().y-out.last().y)<1e-9) out.removeAt(out.lastIndex)
        return out
    }
}
