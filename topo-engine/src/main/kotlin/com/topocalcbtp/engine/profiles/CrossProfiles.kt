package com.topocalcbtp.engine.profiles

import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.terrain.TinSurface
import kotlin.math.hypot
import kotlin.math.abs

/** Design cross-section template: signed offset from axis and relative elevation from design axis level. */
data class TemplatePoint(val offsetMetres:Double,val relativeElevationMetres:Double) {
    init { require(offsetMetres.isFinite() && relativeElevationMetres.isFinite()) }
}

data class DesignLevel(val chainageMetres:Double,val elevationMetres:Double) {
    init { require(chainageMetres.isFinite() && elevationMetres.isFinite()) }
}

data class CrossSectionPoint(
    val offsetMetres:Double,val x:Double,val y:Double,
    val groundElevationMetres:Double,val designElevationMetres:Double
) {
    val differenceGroundMinusDesignMetres:Double get()=groundElevationMetres-designElevationMetres
}

data class DaylightPoint(val offsetMetres:Double,val x:Double,val y:Double,val elevationMetres:Double)
data class CrossSectionResult(
    val chainageMetres:Double,
    val axisX:Double,val axisY:Double,
    val designAxisElevationMetres:Double,
    val points:List<CrossSectionPoint>,
    val daylightPoints:List<DaylightPoint>,
    val cutAreaSquareMetres:Double,
    val fillAreaSquareMetres:Double
)

data class CrossProfileSet(val sections:List<CrossSectionResult>) {
    fun earthworkSections()=sections.map { com.topocalcbtp.engine.earthworks.SectionArea(it.chainageMetres,it.cutAreaSquareMetres,it.fillAreaSquareMetres) }
}

object CrossProfiles {
    /** Builds ground/design sections perpendicular to an alignment and computes automatic daylight intersections and areas. */
    fun generate(
        alignment:List<Point2D>,
        tin:TinSurface,
        chainagesMetres:List<Double>,
        designLevels:List<DesignLevel>,
        template:List<TemplatePoint>
    ):CrossProfileSet {
        require(alignment.size>=2) { "Alignment needs at least two vertices" }
        require(chainagesMetres.isNotEmpty() && chainagesMetres.all { it.isFinite() })
        require(chainagesMetres.zipWithNext().all { it.first<it.second }) { "Requested chainages must be strictly increasing" }
        require(designLevels.size>=2 && designLevels.zipWithNext().all { it.first.chainageMetres<it.second.chainageMetres }) { "Design levels must be strictly increasing and contain at least two stations" }
        require(template.size>=2 && template.zipWithNext().all { it.first.offsetMetres<it.second.offsetMetres }) { "Template offsets must be strictly increasing" }
        val alignmentSegments=alignment.zipWithNext().map { (a,b) ->
            val length=hypot(b.x-a.x,b.y-a.y)
            require(length>1e-9) { "Alignment contains a zero-length segment" }
            length
        }
        val cumulative=MutableList(alignment.size) { 0.0 }
        for(i in alignmentSegments.indices) cumulative[i+1]=cumulative[i]+alignmentSegments[i]
        val total=cumulative.last()
        return CrossProfileSet(chainagesMetres.map { chain ->
            require(chain>=0.0 && chain<=total+1e-8) { "Chainage $chain lies outside alignment length $total" }
            val segmentIndex=(0 until alignmentSegments.size).firstOrNull { chain<=cumulative[it+1]+1e-9 } ?: alignmentSegments.lastIndex
            val a=alignment[segmentIndex]; val b=alignment[segmentIndex+1]; val len=alignmentSegments[segmentIndex]
            val f=((chain-cumulative[segmentIndex])/len).coerceIn(0.0,1.0)
            val cx=a.x+f*(b.x-a.x); val cy=a.y+f*(b.y-a.y)
            val tx=(b.x-a.x)/len; val ty=(b.y-a.y)/len
            // Positive offset points to the left of chainage direction.
            val nx=-ty; val ny=tx
            val designAxis=interpolateDesignLevel(designLevels,chain)
            val sampled=template.map { tp ->
                val x=cx+nx*tp.offsetMetres; val y=cy+ny*tp.offsetMetres
                val ground=tin.interpolate(x,y) ?: throw IllegalArgumentException("Cross-section at chainage $chain is outside TIN (offset ${tp.offsetMetres})")
                CrossSectionPoint(tp.offsetMetres,x,y,ground,designAxis+tp.relativeElevationMetres)
            }
            val daylight=mutableListOf<DaylightPoint>()
            var cutArea=0.0; var fillArea=0.0
            for((p,q) in sampled.zipWithNext()) {
                val width=q.offsetMetres-p.offsetMetres
                val dp=p.differenceGroundMinusDesignMetres; val dq=q.differenceGroundMinusDesignMetres
                if(dp==0.0) daylight.add(DaylightPoint(p.offsetMetres,p.x,p.y,p.groundElevationMetres))
                if(dp*dq<0.0) {
                    val crossingRatio=dp/(dp-dq)
                    val offset=p.offsetMetres+crossingRatio*width
                    val x=p.x+crossingRatio*(q.x-p.x); val y=p.y+crossingRatio*(q.y-p.y)
                    val z=p.groundElevationMetres+crossingRatio*(q.groundElevationMetres-p.groundElevationMetres)
                    daylight.add(DaylightPoint(offset,x,y,z))
                }
                if(dp>=0.0 && dq>=0.0) cutArea+=(dp+dq)*0.5*width
                else if(dp<=0.0 && dq<=0.0) fillArea+=(-dp-dq)*0.5*width
                else {
                    val areaRatio=abs(dp)/(abs(dp)+abs(dq))
                    if(dp>0) cutArea+=0.5*abs(dp)*(width*areaRatio) else fillArea+=0.5*abs(dp)*(width*areaRatio)
                    if(dq>0) cutArea+=0.5*abs(dq)*(width*(1-areaRatio)) else fillArea+=0.5*abs(dq)*(width*(1-areaRatio))
                }
            }
            if(sampled.last().differenceGroundMinusDesignMetres==0.0) {
                val p=sampled.last(); daylight.add(DaylightPoint(p.offsetMetres,p.x,p.y,p.groundElevationMetres))
            }
            CrossSectionResult(chain,cx,cy,designAxis,sampled,daylight,cutArea,fillArea)
        })
    }

    private fun interpolateDesignLevel(levels:List<DesignLevel>,chain:Double):Double {
        require(chain>=levels.first().chainageMetres-1e-8 && chain<=levels.last().chainageMetres+1e-8) { "Design levels do not cover chainage $chain" }
        val i=(0 until levels.lastIndex).firstOrNull { chain<=levels[it+1].chainageMetres } ?: levels.lastIndex-1
        val a=levels[i]; val b=levels[i+1]
        val f=((chain-a.chainageMetres)/(b.chainageMetres-a.chainageMetres)).coerceIn(0.0,1.0)
        return a.elevationMetres+f*(b.elevationMetres-a.elevationMetres)
    }
}
