package com.topocalcbtp.engine.cad

import com.topocalcbtp.engine.coordinates.Point3D
import com.topocalcbtp.engine.profiles.LongitudinalProfile
import com.topocalcbtp.engine.profiles.CrossSectionResult
import com.topocalcbtp.engine.earthworks.SurfaceComparisonResult
import com.topocalcbtp.engine.terrain.ContourPolyline
import com.topocalcbtp.engine.terrain.TinSurface
import java.io.File
import java.util.Locale

/** Minimal ASCII DXF (AC1015) export: survey points, TIN 3DFACE entities, contours and profiles. */
object DxfWriter {
    fun toAscii(
        points:List<Point3D> = emptyList(),
        tin:TinSurface? = null,
        contours:List<ContourPolyline> = emptyList(),
        longitudinalProfiles:List<LongitudinalProfile> = emptyList(),
        crossSections:List<CrossSectionResult> = emptyList(),
        surfaceComparison:SurfaceComparisonResult? = null,
        contourLabelHeightMetres:Double = 1.5
    ):String {
        require(contourLabelHeightMetres>0.0 && contourLabelHeightMetres.isFinite()) { "Contour label height must be positive" }
        val s=StringBuilder()
        fun pair(code:Int,value:String) { s.append(code).append('\n').append(value).append('\n') }
        pair(0,"SECTION"); pair(2,"HEADER")
        pair(9,"\$ACADVER"); pair(1,"AC1015")
        pair(9,"\$INSUNITS"); pair(70,"6") // metres
        pair(9,"\$MEASUREMENT"); pair(70,"1") // metric
        pair(0,"ENDSEC")
        pair(0,"SECTION"); pair(2,"TABLES")
        pair(0,"TABLE"); pair(2,"LTYPE"); pair(70,"1")
        pair(0,"LTYPE"); pair(2,"CONTINUOUS"); pair(70,"0"); pair(3,"Solid line"); pair(72,"65"); pair(73,"0"); pair(40,"0.0")
        pair(0,"ENDTAB")
        pair(0,"TABLE"); pair(2,"LAYER"); pair(70,"9")
        for((name,color) in listOf("POINTS" to 7,"TIN" to 8,"CONTOURS" to 3,"PROFILE" to 4,"CUT" to 1,"FILL" to 5,"BREAKLINES" to 6,"GROUND_SECTION" to 2,"DESIGN_SECTION" to 30)) {
            pair(0,"LAYER"); pair(2,name); pair(70,"0"); pair(62,color.toString()); pair(6,"CONTINUOUS")
        }
        pair(0,"ENDTAB"); pair(0,"ENDSEC")
        pair(0,"SECTION"); pair(2,"ENTITIES")
        for(p in points) {
            pair(0,"POINT"); pair(8,"POINTS"); pair(10,fmt(p.x)); pair(20,fmt(p.y)); pair(30,fmt(p.z))
        }
        tin?.triangles?.forEach { t ->
            pair(0,"3DFACE"); pair(8,"TIN")
            pair(10,fmt(t.a.x)); pair(20,fmt(t.a.y)); pair(30,fmt(t.a.z))
            pair(11,fmt(t.b.x)); pair(21,fmt(t.b.y)); pair(31,fmt(t.b.z))
            pair(12,fmt(t.c.x)); pair(22,fmt(t.c.y)); pair(32,fmt(t.c.z))
            pair(13,fmt(t.c.x)); pair(23,fmt(t.c.y)); pair(33,fmt(t.c.z))
        }
        if(tin!=null) {
            val byId=points.associateBy { it.id }
            for((aId,bId) in tin.constrainedEdges) {
                val a=byId[aId] ?: continue; val b=byId[bId] ?: continue
                pair(0,"LINE"); pair(8,"BREAKLINES")
                pair(10,fmt(a.x)); pair(20,fmt(a.y)); pair(30,fmt(a.z))
                pair(11,fmt(b.x)); pair(21,fmt(b.y)); pair(31,fmt(b.z))
            }
        }
        for(c in contours) {
            val contourPoints=if(c.closed && c.points.size>2 && c.points.first().x==c.points.last().x && c.points.first().y==c.points.last().y) c.points.dropLast(1) else c.points
            pair(0,"LWPOLYLINE"); pair(8,"CONTOURS"); pair(90,contourPoints.size.toString()); pair(70,if(c.closed) "1" else "0"); pair(38,fmt(c.elevationMetres))
            for(p in contourPoints) { pair(10,fmt(p.x)); pair(20,fmt(p.y)) }
            val label=c.label()
            pair(0,"TEXT"); pair(8,"CONTOURS"); pair(10,fmt(label.position.x)); pair(20,fmt(label.position.y)); pair(30,fmt(c.elevationMetres))
            pair(40,fmt(contourLabelHeightMetres)); pair(1,label.text)
        }
        for(profile in longitudinalProfiles) {
            pair(0,"LWPOLYLINE"); pair(8,"PROFILE"); pair(90,profile.points.size.toString()); pair(70,"0"); pair(38,"0.0")
            for(p in profile.points) { pair(10,fmt(p.chainageMetres)); pair(20,fmt(p.groundElevationMetres)) }
        }
        for(section in crossSections) {
            // 3D polylines preserve real-world XYZ for both terrain and design templates.
            for((layer,useDesign) in listOf("GROUND_SECTION" to false,"DESIGN_SECTION" to true)) {
                pair(0,"POLYLINE"); pair(8,layer); pair(66,"1"); pair(70,"8")
                for(p in section.points) {
                    pair(0,"VERTEX"); pair(8,layer); pair(10,fmt(p.x)); pair(20,fmt(p.y))
                    pair(30,fmt(if(useDesign) p.designElevationMetres else p.groundElevationMetres))
                }
                pair(0,"SEQEND"); pair(8,layer)
            }
        }
        surfaceComparison?.let { comparison ->
            for(poly in comparison.cutPolygons) {
                pair(0,"LWPOLYLINE"); pair(8,"CUT"); pair(90,poly.vertices.size.toString()); pair(70,"1"); pair(38,"0.0")
                for(v in poly.vertices) { pair(10,fmt(v.x)); pair(20,fmt(v.y)) }
            }
            for(poly in comparison.fillPolygons) {
                pair(0,"LWPOLYLINE"); pair(8,"FILL"); pair(90,poly.vertices.size.toString()); pair(70,"1"); pair(38,"0.0")
                for(v in poly.vertices) { pair(10,fmt(v.x)); pair(20,fmt(v.y)) }
            }
        }
        pair(0,"ENDSEC"); pair(0,"EOF")
        return s.toString()
    }

    fun write(
        file:File,points:List<Point3D> = emptyList(),tin:TinSurface? = null,
        contours:List<ContourPolyline> = emptyList(),longitudinalProfiles:List<LongitudinalProfile> = emptyList(),
        crossSections:List<CrossSectionResult> = emptyList(),surfaceComparison:SurfaceComparisonResult? = null,
        contourLabelHeightMetres:Double = 1.5
    ) {
        require(file.parentFile?.exists()!=false) { "DXF parent directory does not exist" }
        file.writeText(toAscii(points,tin,contours,longitudinalProfiles,crossSections,surfaceComparison,contourLabelHeightMetres),Charsets.US_ASCII)
    }

    private fun fmt(value:Double)=String.format(Locale.US,"%.6f",value)
}
