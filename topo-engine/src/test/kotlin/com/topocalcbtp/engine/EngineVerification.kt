package com.topocalcbtp.engine

import com.topocalcbtp.engine.angles.Angles
import com.topocalcbtp.engine.coordinates.*
import com.topocalcbtp.engine.intersection.*
import com.topocalcbtp.engine.leveling.*
import com.topocalcbtp.engine.stakeout.Stakeout
import com.topocalcbtp.engine.surface.PolygonSurface
import com.topocalcbtp.engine.traverse.*
import com.topocalcbtp.engine.terrain.*
import com.topocalcbtp.engine.profiles.Profiles
import com.topocalcbtp.engine.earthworks.*
import com.topocalcbtp.engine.profiles.CrossProfiles
import com.topocalcbtp.engine.profiles.DesignLevel
import com.topocalcbtp.engine.profiles.TemplatePoint
import com.topocalcbtp.engine.terrain.Breakline
import com.topocalcbtp.engine.terrain.Contours
import com.topocalcbtp.engine.cad.DxfWriter
import com.topocalcbtp.engine.projection.*
import com.topocalcbtp.engine.angles.AngleText
import com.topocalcbtp.engine.angles.TwoFace
import com.topocalcbtp.engine.io.*
import com.topocalcbtp.engine.worksite.*
import kotlin.math.abs
import kotlin.math.sqrt

private fun near(actual: Double, expected: Double, tolerance: Double = 1e-6, label: String) {
    check(abs(actual - expected) <= tolerance) { "$label: expected $expected, got $actual (tol=$tolerance)" }
}

fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) {
        block()
        println("PASS: $name")
        passed++
    }

    test("angle normalization and gon conversion") {
        near(Angles.normalize360(-10.0), 350.0, label = "normalize360")
        near(Angles.normalize180(270.0), -90.0, label = "normalize180")
        near(Angles.gonsToDegrees(100.0), 90.0, label = "gon to degrees")
        near(Angles.dmsToDecimal(12, 30, 0.0), 12.5, label = "DMS to decimal")
    }

    test("polar forward and inverse coordinates") {
        val a = Point2D("A", 1000.0, 2000.0)
        val b = Coordinates.forward(a, PolarObservation(100.0, 90.0), "B")
        near(b.x, 1100.0, label = "B.X")
        near(b.y, 2000.0, label = "B.Y")
        val inverse = Coordinates.inverse(a, b)
        near(inverse.distanceMetres, 100.0, label = "distance")
        near(inverse.gisementDegrees, 90.0, label = "gisement")
    }

    test("slope distance reduction with vertical angle") {
        val reduced = DistanceReduction.reduce(100.0, VerticalObservation.VerticalAngle(30.0), 1.5, 2.0)
        near(reduced.horizontalDistanceMetres, 86.6025403784, 1e-8, "horizontal distance")
        near(reduced.verticalComponentMetres, 50.0, label = "vertical component")
        near(reduced.elevationDifferenceMetres, 49.5, label = "ground elevation difference")
    }

    test("slope distance reduction with zenith angle") {
        val reduced = DistanceReduction.reduce(50.0, VerticalObservation.ZenithAngle(90.0))
        near(reduced.horizontalDistanceMetres, 50.0, label = "horizontal distance")
        near(reduced.verticalComponentMetres, 0.0, label = "vertical component")
    }

    test("two-ray intersection") {
        val result = Intersections.twoRays(
            Ray(Point2D("A", 0.0, 0.0), 45.0),
            Ray(Point2D("B", 100.0, 0.0), 315.0),
            "P"
        )
        near(result.point.x, 50.0, 1e-6, "P.X")
        near(result.point.y, 50.0, 1e-6, "P.Y")
        near(result.distanceFromFirstStationMetres, sqrt(5000.0), 1e-6, "distance A-P")
    }

    test("distance resection") {
        val expected = Point2D("S", 30.0, 40.0)
        val known = listOf(
            Point2D("A", 0.0, 0.0),
            Point2D("B", 100.0, 0.0),
            Point2D("C", 0.0, 100.0)
        )
        val observations = known.map { DistanceObservation(it, Coordinates.distance2D(expected, it)) }
        val result = Intersections.distanceResection(observations, Point2D("initial", 32.0, 38.0), "S")
        near(result.estimatedPoint.x, 30.0, 1e-6, "station X")
        near(result.estimatedPoint.y, 40.0, 1e-6, "station Y")
        near(result.rootMeanSquareMetres, 0.0, 1e-6, "RMS")
    }

    test("two-setup HI levelling and arithmetic check") {
        val setups = listOf(
            LevelSetup(
                StaffReading("BM", 1.5),
                listOf(StaffReading("P1", 2.0, 25.0)),
                StaffReading("CP1", 1.2), 50.0
            ),
            LevelSetup(
                StaffReading("CP1", 1.4),
                emptyList(),
                StaffReading("END", 1.1), 50.0
            )
        )
        val result = Leveling.compute("BM", 100.0, setups, knownEndElevationMetres = 100.5, adjustment = LevelAdjustmentMethod.PROPORTIONAL_TO_DISTANCE)
        near(result.calculatedEndElevationMetres, 100.6, label = "calculated end")
        near(result.closureErrorMetres!!, 0.1, label = "closure")
        near(result.arithmeticCheckMetres, 0.6, label = "sum BS - sum FS")
        near(result.totalLengthMetres, 100.0, label = "length")
        near(result.observations.first { it.pointId == "P1" }.rawElevationMetres, 99.5, label = "P1 RL")
        val compensatedEnd = result.observations.last().adjustedElevationMetres
        near(compensatedEnd, 100.5, label = "compensated end")
        val p1 = result.observations.first { it.pointId == "P1" }
        near(p1.adjustedElevationMetres, 99.475, label = "compensated P1")
    }

    test("closed traverse Bowditch adjustment") {
        val start = Point2D("A", 0.0, 0.0)
        val segments = listOf(
            TraverseSegment("A", "B", 10.0, 0.0),
            TraverseSegment("B", "C", 10.0, 90.0),
            TraverseSegment("C", "D", 10.0, 180.0),
            TraverseSegment("D", "A", 9.9, 270.0)
        )
        val result = Traverses.compute(start, segments, Point2D("A", 0.0, 0.0), TraverseAdjustment.BOWDITCH)
        near(result.misclosureXMetres!!, 0.1, 1e-8, "misclosure X")
        near(result.misclosureYMetres!!, 0.0, 1e-8, "misclosure Y")
        near(result.points.last().point.x, 0.0, 1e-8, "adjusted end X")
        near(result.points.last().point.y, 0.0, 1e-8, "adjusted end Y")
        near(result.points.last().correctionXMetres, -0.1, 1e-8, "final correction X")
    }

    test("stakeout polar elements and orientation") {
        val station = Point2D("ST", 0.0, 0.0)
        val target = Point2D("P", 10.0, 10.0)
        val v0 = Stakeout.orientationConstant(90.0, 10.0)
        val elements = Stakeout.calculate(station, target, v0)
        near(elements.horizontalDistanceMetres, sqrt(200.0), label = "stakeout distance")
        near(elements.gisementDegrees, 45.0, label = "stakeout gisement")
        near(elements.horizontalCircleReadingDegrees!!, 325.0, label = "circle reading")
    }


    test("stadia distance and middle-wire check") {
        val result = Stadia.calculate(1.850, 1.500, 1.150)
        near(result.estimatedDistanceMetres, 70.0, label = "stadia distance")
        near(result.expectedMiddleReadingMetres, 1.5, label = "expected middle wire")
        check(result.middleReadingWithinTolerance)
    }

    test("polygon area and perimeter") {
        val polygon = listOf(
            Point2D("A", 0.0, 0.0), Point2D("B", 10.0, 0.0),
            Point2D("C", 10.0, 10.0), Point2D("D", 0.0, 10.0)
        )
        val result = PolygonSurface.calculate(polygon)
        near(result.areaSquareMetres, 100.0, label = "area")
        near(result.perimeterMetres, 40.0, label = "perimeter")
    }

    test("TIN triangulation and linear elevation interpolation") {
        val tin = TinBuilder.build(listOf(
            Point3D("A",0.0,0.0,100.0), Point3D("B",10.0,0.0,110.0),
            Point3D("C",10.0,10.0,120.0), Point3D("D",0.0,10.0,110.0)
        ))
        check(tin.triangles.size == 2)
        near(tin.interpolate(5.0,5.0)!!,110.0,1e-8,"TIN centre elevation")
        check(tin.interpolate(20.0,20.0) == null)
    }

    test("TIN contour segment generation") {
        val tin = TinBuilder.build(listOf(
            Point3D("A",0.0,0.0,0.0), Point3D("B",10.0,0.0,10.0),
            Point3D("C",0.0,10.0,10.0)
        ))
        val contours = tin.contourSegments(5.0)
        check(contours.any { abs(it.elevationMetres-5.0)<1e-9 })
    }

    test("longitudinal profile sampled from TIN") {
        val tin = TinBuilder.build(listOf(
            Point3D("A",0.0,0.0,100.0), Point3D("B",10.0,0.0,110.0),
            Point3D("C",10.0,10.0,120.0), Point3D("D",0.0,10.0,110.0)
        ))
        val profile = Profiles.longitudinal(listOf(Point2D("S",1.0,1.0),Point2D("E",9.0,1.0)),tin,2.0)
        near(profile.totalLengthMetres,8.0,label="profile length")
        near(profile.points.first().groundElevationMetres,102.0,1e-8,"profile start Z")
        near(profile.points.last().groundElevationMetres,110.0,1e-8,"profile end Z")
    }

    test("earthwork average end area volumes") {
        val result = Earthworks.averageEndArea(listOf(
            SectionArea(0.0,10.0,2.0), SectionArea(20.0,20.0,4.0), SectionArea(40.0,10.0,6.0)
        ))
        near(result.totalCutCubicMetres,600.0,label="cut volume")
        near(result.totalFillCubicMetres,160.0,label="fill volume")
        near(result.netFillMinusCutCubicMetres,-440.0,label="net volume")
    }

    test("breakline-constrained TIN includes every requested edge") {
        val pts=listOf(
            Point3D("A",0.0,0.0,100.0), Point3D("B",10.0,0.0,101.0),
            Point3D("C",10.0,10.0,102.0), Point3D("D",0.0,10.0,103.0),
            Point3D("E",5.0,5.0,105.0)
        )
        val tin=TinBuilder.build(pts,listOf(Breakline(listOf("A","C"))))
        check(("A" to "E") in tin.constrainedEdges || ("E" to "A") in tin.constrainedEdges)
        check(("C" to "E") in tin.constrainedEdges || ("E" to "C") in tin.constrainedEdges)
        check(tin.triangles.isNotEmpty())
    }

    test("contour segments stitch into continuous polyline") {
        val tin=TinBuilder.build(listOf(
            Point3D("A",0.0,0.0,0.0), Point3D("B",10.0,0.0,0.0),
            Point3D("C",10.0,10.0,10.0), Point3D("D",0.0,10.0,10.0)
        ))
        val lines=tin.contourPolylines(5.0,5.0)
        check(lines.size==1) { "Expected one stitched contour, got ${lines.size}" }
        check(lines.first().points.size>=2)
        check(!lines.first().closed)
        check(lines.first().label().text=="5.00")
        check(lines.first().smoothed(1).points.size>lines.first().points.size)
    }

    test("cross sections intersect design template and compute cut area") {
        val tin=TinBuilder.build(listOf(
            Point3D("A",0.0,-5.0,10.0), Point3D("B",10.0,-5.0,10.0),
            Point3D("C",10.0,5.0,10.0), Point3D("D",0.0,5.0,10.0)
        ))
        val sections=CrossProfiles.generate(
            listOf(Point2D("S",0.0,0.0),Point2D("E",10.0,0.0)),tin,
            listOf(2.0,8.0),listOf(DesignLevel(0.0,9.0),DesignLevel(10.0,9.0)),
            listOf(TemplatePoint(-5.0,0.0),TemplatePoint(5.0,0.0))
        )
        near(sections.sections.first().cutAreaSquareMetres,10.0,label="cross-section cut area")
        near(sections.earthworkSections().first().cutAreaSquareMetres,10.0,label="section export area")
        val volume=Earthworks.averageEndArea(sections.earthworkSections())
        near(volume.totalCutCubicMetres,60.0,label="cross-section volume")
    }

    test("surface-to-surface comparison computes cut fill and zero boundary") {
        val existing=TinBuilder.build(listOf(
            Point3D("A",0.0,0.0,-5.0), Point3D("B",10.0,0.0,5.0),
            Point3D("C",10.0,10.0,5.0), Point3D("D",0.0,10.0,-5.0)
        ))
        val design=TinBuilder.build(listOf(
            Point3D("A2",0.0,0.0,0.0), Point3D("B2",10.0,0.0,0.0),
            Point3D("C2",10.0,10.0,0.0), Point3D("D2",0.0,10.0,0.0)
        ))
        val result=SurfaceComparison.compare(existing,design)
        near(result.overlapAreaSquareMetres,100.0,1e-6,"overlap area")
        near(result.cutCubicMetres,125.0,1e-5,"cut volume")
        near(result.fillCubicMetres,125.0,1e-5,"fill volume")
        check(result.cutPolygons.isNotEmpty() && result.fillPolygons.isNotEmpty())
    }

    test("DXF export contains points TIN faces contours and profile entities") {
        val pts=listOf(Point3D("A",0.0,0.0,10.0),Point3D("B",10.0,0.0,10.0),Point3D("C",0.0,10.0,10.0))
        val tin=TinBuilder.build(pts)
        val contourTin=TinBuilder.build(listOf(Point3D("P1",0.0,0.0,0.0),Point3D("P2",10.0,0.0,10.0),Point3D("P3",0.0,10.0,10.0)))
        val cross=com.topocalcbtp.engine.profiles.CrossSectionResult(5.0,5.0,5.0,10.0,listOf(
            com.topocalcbtp.engine.profiles.CrossSectionPoint(-1.0,5.0,4.0,10.0,9.0),
            com.topocalcbtp.engine.profiles.CrossSectionPoint(1.0,5.0,6.0,10.0,9.0)
        ),emptyList(),2.0,0.0)
        val polygon=CutFillPolygon(listOf(DifferenceVertex(0.0,0.0,1.0),DifferenceVertex(1.0,0.0,1.0),DifferenceVertex(0.0,1.0,1.0)),0.5,0.5)
        val comparison=SurfaceComparisonResult(0.5,0.25,-0.25,1.0,listOf(polygon),listOf(polygon))
        val dxf=DxfWriter.toAscii(points=pts,tin=tin,contours=contourTin.contourPolylines(2.0),crossSections=listOf(cross),surfaceComparison=comparison)
        check(dxf.contains("AC1015"))
        check(dxf.contains("POINT"))
        check(dxf.contains("3DFACE"))
        check(dxf.contains("LWPOLYLINE"))
        check(dxf.contains("TEXT"))
        check(dxf.contains("GROUND_SECTION"))
        check(dxf.contains("DESIGN_SECTION"))
        check(dxf.contains("CUT")); check(dxf.contains("FILL"))
        check(dxf.trimEnd().endsWith("EOF"))
    }

    test("TIN handles a larger deterministic grid and preserves surface interpolation") {
        val pts=buildList {
            for(y in 0..49) for(x in 0..49) add(Point3D("P${x}_$y",x.toDouble(),y.toDouble(),100.0+0.5*x+0.25*y))
        }
        val tin=TinBuilder.build(pts)
        check(tin.triangles.size>4_000)
        near(tin.interpolate(8.5,7.5)!!,106.125,1e-6,"large TIN interpolation")
    }

    test("normalize360 folds tiny negatives and full turns to zero") {
        near(Angles.normalize360(-1e-15), 0.0, label = "tiny negative")
        near(Angles.normalize360(720.0), 0.0, label = "full turns")
        check(Angles.normalize360(-1e-15) < 360.0)
        near(Angles.normalize400Gons(-50.0), 350.0, label = "gons")
    }

    test("curvature and refraction correction is optional and sized correctly") {
        val off = DistanceReduction.reduce(500.0, VerticalObservation.ZenithAngle(90.0))
        near(off.verticalComponentMetres, 0.0, 1e-9, "no correction by default")
        val on = DistanceReduction.reduce(500.0, VerticalObservation.ZenithAngle(90.0), applyCurvatureRefraction = true)
        near(on.curvatureRefractionMetres, 0.87 * 500.0 * 500.0 / (2 * 6_371_000.0), 1e-12, "c+r")
        check(on.verticalComponentMetres in 0.016..0.018) { "about 17 mm at 500 m" }
    }

    test("inclined stadia uses cos^2 and reduces to the level formula at alpha = 0") {
        val flat = Stadia.inclined(1.600, 1.500, 1.400, VerticalObservation.ZenithAngle(90.0), 1.5)
        near(flat.horizontalDistanceMetres, 20.0, 1e-9, "flat Dh")
        near(flat.elevationDifferenceMetres, 0.0, 1e-9, "flat dZ")
        val inclined = Stadia.inclined(1.600, 1.500, 1.400, VerticalObservation.VerticalAngle(10.0), 1.5)
        val a = Math.toRadians(10.0)
        near(inclined.horizontalDistanceMetres, 20.0 * kotlin.math.cos(a) * kotlin.math.cos(a), 1e-9, "inclined Dh")
        near(inclined.elevationDifferenceMetres, 1.5 + 10.0 * kotlin.math.sin(2 * a) - 1.5, 1e-9, "inclined dZ")
    }

    test("levelling closure tolerance k*sqrt(L km)") {
        near(Leveling.closureToleranceMetres(20.0, 4000.0), 0.040, 1e-12, "20 mm * sqrt(4)")
    }

    test("angular traverse closure: square loop with a 20-second error") {
        val err = 20.0 / 3600.0
        val perfect = AngularTraverse.carryGisements(0.0, listOf(90.0, 270.0, 270.0, 270.0, 180.0), 0.0)
        near(perfect.angularMisclosureDegrees!!, 0.0, 1e-12, "perfect misclosure")
        near(perfect.legGisementsDegrees[1], 180.0, 1e-12, "leg 2 gisement")
        val noisy = AngularTraverse.carryGisements(0.0, listOf(90.0, 270.0 + err, 270.0, 270.0, 180.0), 0.0)
        near(noisy.angularMisclosureDegrees!!, err, 1e-12, "misclosure")
        near(noisy.correctionPerAngleDegrees, -err / 5, 1e-12, "per-angle correction")
        check(noisy.legGisementsDegrees.size == 4)
        // After correction, the closing direction is exactly the known one.
        val closing = noisy.legGisementsDegrees.last() + 180.0 + 180.0 + noisy.correctionPerAngleDegrees
        near(Angles.normalize180(closing), 0.0, 1e-9, "corrected closing direction")
        val open = AngularTraverse.carryGisements(45.0, listOf(90.0, 180.0))
        near(open.legGisementsDegrees[0], 135.0, 1e-12, "open leg 1")
        near(open.legGisementsDegrees[1], 135.0, 1e-12, "open leg 2 (straight line)")
        check(open.angularMisclosureDegrees == null)
        // Corrected gisements feed the Bowditch traverse.
        val segs = noisy.legGisementsDegrees.mapIndexed { i, g -> TraverseSegment("S$i", if (i == 3) "S0" else "S${i + 1}", 100.0, g) }
        val t = Traverses.compute(Point2D("S0", 0.0, 0.0), segs, Point2D("S0", 0.0, 0.0), TraverseAdjustment.BOWDITCH)
        check(t.linearMisclosureMetres!! < 0.02) { "residual linear misclosure is only a few mm" }
        near(t.points.last().point.x, 0.0, 1e-9, "Bowditch closes X")
        near(t.points.last().point.y, 0.0, 1e-9, "Bowditch closes Y")
    }

    test("EPSG 9801 worked example: LCC 1SP Jamaica National Grid") {
        val lcc = LambertConicConformal1SP(Ellipsoid.CLARKE_1866, 18.0, -77.0, 1.0, 250_000.0, 150_000.0)
        val lat = 17.0 + 55.0 / 60 + 55.80 / 3600
        val lon = -(76.0 + 56.0 / 60 + 37.26 / 3600)
        val g = lcc.project(lat, lon)
        near(g.x, 255_966.58, 0.01, "E")
        near(g.y, 142_493.51, 0.01, "N")
        val (rlat, rlon) = lcc.unproject(g)
        near(rlat, lat, 1e-9, "inverse lat")
        near(rlon, lon, 1e-9, "inverse lon")
        near(lcc.scaleFactorAt(18.0), 1.0, 1e-12, "k at origin")
    }

    test("EPSG 9602 worked example: geographic <-> geocentric (WGS 84)") {
        val p = GeographicPoint(53.0 + 48.0 / 60 + 33.820 / 3600, 2.0 + 7.0 / 60 + 46.380 / 3600, 73.0)
        val c = Geocentric.fromGeographic(p, Ellipsoid.WGS84)
        near(c.x, 3_771_793.968, 0.001, "X")
        near(c.y, 140_253.342, 0.001, "Y")
        near(c.z, 5_124_304.349, 0.001, "Z")
        val back = Geocentric.toGeographic(c, Ellipsoid.WGS84)
        near(back.latitudeDegrees, p.latitudeDegrees, 1e-10, "lat")
        near(back.longitudeDegrees, p.longitudeDegrees, 1e-10, "lon")
        near(back.ellipsoidalHeightMetres, 73.0, 1e-4, "h")
    }

    test("EPSG 9606/9607 worked example: 7-parameter Helmert (WGS 72 -> WGS 84)") {
        val src = GeocentricPoint(3_657_660.66, 255_768.55, 5_201_382.11)
        val pv = Helmert7("pv", 0.0, 0.0, 4.5, 0.0, 0.0, 0.554, 0.219, RotationConvention.POSITION_VECTOR)
        val t = pv.forward(src)
        near(t.x, 3_657_660.78, 0.01, "X")
        near(t.y, 255_778.43, 0.01, "Y")
        near(t.z, 5_201_387.75, 0.01, "Z")
        val cf = Helmert7("cf", 0.0, 0.0, 4.5, 0.0, 0.0, -0.554, 0.219, RotationConvention.COORDINATE_FRAME)
        val t2 = cf.forward(src)
        near(t2.x, t.x, 1e-9, "CF X"); near(t2.y, t.y, 1e-9, "CF Y"); near(t2.z, t.z, 1e-9, "CF Z")
        val back = pv.inverse(t)
        near(back.x, src.x, 1e-6, "inverse X"); near(back.y, src.y, 1e-6, "inverse Y"); near(back.z, src.z, 1e-6, "inverse Z")
    }

    test("Morocco Lambert zones: EPSG parameters, origins, round trips, zone choice") {
        for (zone in MoroccoLambertZone.entries) {
            val origin = zone.projection.project(zone.latitudeOfOriginDegrees, -5.4)
            near(origin.x, zone.falseEastingMetres, 1e-6, "${zone.name} FE")
            near(origin.y, zone.falseNorthingMetres, 1e-6, "${zone.name} FN")
            near(zone.projection.scaleFactorAt(zone.latitudeOfOriginDegrees), zone.scaleFactor, 1e-12, "${zone.name} k0")
        }
        // Grad definitions: 37, 33, 29, 25 gr and -6 gr.
        near(MoroccoLambertZone.NORD_MAROC.latitudeOfOriginDegrees, Angles.gonsToDegrees(37.0), 1e-12, "Nord 37 gr")
        near(MoroccoLambertZone.SAHARA_SUD.latitudeOfOriginDegrees, Angles.gonsToDegrees(25.0), 1e-12, "Sahara Sud 25 gr")
        check(MoroccoLambertZone.forLatitude(34.0) == MoroccoLambertZone.NORD_MAROC)
        check(MoroccoLambertZone.forLatitude(31.0) == MoroccoLambertZone.SUD_MAROC)
        check(MoroccoLambertZone.forLatitude(26.0) == MoroccoLambertZone.SAHARA_NORD)
        check(MoroccoLambertZone.forLatitude(22.0) == MoroccoLambertZone.SAHARA_SUD)
        // Grid -> WGS 84 -> grid closes to well below a millimetre (Rabat area, Nord Maroc).
        val grid = GridPoint(375_000.0, 380_000.0)
        val wgs = MoroccoCoordinates.lambertToWgs84(grid, MoroccoLambertZone.NORD_MAROC)
        check(wgs.latitudeDegrees in 33.5..34.5 && wgs.longitudeDegrees in -7.5..-6.0) { "Rabat area: $wgs" }
        val back = MoroccoCoordinates.wgs84ToLambert(wgs, MoroccoLambertZone.NORD_MAROC)
        near(back.x, grid.x, 1e-4, "round trip X"); near(back.y, grid.y, 1e-4, "round trip Y")
        // The datum shift Merchich -> WGS 84 is real: the same lat/lon differ by tens to hundreds of metres.
        val (mlat, mlon) = MoroccoLambertZone.NORD_MAROC.projection.unproject(grid)
        val shiftM = kotlin.math.hypot((wgs.latitudeDegrees - mlat) * 111_000.0, (wgs.longitudeDegrees - mlon) * 92_000.0)
        check(shiftM in 20.0..500.0) { "datum shift magnitude $shiftM m" }
    }

    test("Angle text parsing (decimal, DMS, hemispheres, comma)") {
        near(AngleText.parseDegrees("33.5")!!, 33.5, 1e-12, "decimal")
        near(AngleText.parseDegrees("-5,42")!!, -5.42, 1e-12, "comma")
        near(AngleText.parseDegrees("33 30 36")!!, 33.51, 1e-12, "dms spaces")
        near(AngleText.parseDegrees("33°30'36\"N")!!, 33.51, 1e-12, "dms symbols")
        near(AngleText.parseDegrees("6°50'30\"W")!!, -(6 + 50.0 / 60 + 30.0 / 3600), 1e-12, "west")
        near(AngleText.parseDegrees("O 6 50 30")!!, -(6 + 50.0 / 60 + 30.0 / 3600), 1e-12, "ouest prefix")
        check(AngleText.parseDegrees("33 61 00") == null) { "minutes >= 60 rejected" }
        check(AngleText.parseDegrees("abc") == null)
        check(AngleText.parseDegrees("") == null)
        check(AngleText.formatDms(33.51, 'N', 'S', 2) == "33°30'36.00\"N") { AngleText.formatDms(33.51, 'N', 'S', 2) }
        check(AngleText.formatDms(-6.0, 'E', 'W', 1) == "6°00'00.0\"W") { AngleText.formatDms(-6.0, 'E', 'W', 1) }
    }

    test("Traverse workflow: encadré with synthetic exact data, then with angle and distance errors") {
        val a = Point2D("A", 1000.0, 1000.0); val r1 = Point2D("R1", 1000.0, 2000.0)
        val s1 = Point2D("S1", 1100.0, 1050.0); val s2 = Point2D("S2", 1250.0, 1000.0)
        val b = Point2D("B", 1400.0, 1080.0); val r2 = Point2D("R2", 1500.0, 1180.0)
        fun g(p: Point2D, q: Point2D) = Coordinates.inverse(p, q).gisementDegrees
        fun d(p: Point2D, q: Point2D) = Coordinates.inverse(p, q).distanceMetres
        fun ang(back: Double, fwd: Double) = Angles.normalize360(fwd - back)
        val route = listOf(a, s1, s2, b)
        val angles = listOf(ang(g(a, r1), g(a, s1)), ang(g(s1, a), g(s1, s2)), ang(g(s2, s1), g(s2, b)))
        val closing = ang(g(b, s2), g(b, r2))
        val legs = route.zipWithNext().mapIndexed { i, (p, q) -> TraverseLegObservation(p.id, angles[i], d(p, q), q.id) }
        val exact = TraverseWorkflow.solve(TraverseKind.CLOSED_ON_KNOWN_POINT, a, g(a, r1), legs, closing, b, g(b, r2))
        near(exact.angular.angularMisclosureDegrees!!, 0.0, 1e-9, "exact angular")
        near(exact.traverse.linearMisclosureMetres!!, 0.0, 1e-6, "exact linear")
        near(exact.traverse.points[1].point.x, s1.x, 1e-6, "S1 x"); near(exact.traverse.points[2].point.y, s2.y, 1e-6, "S2 y")

        val err = 30.0 / 3600.0
        val noisyLegs = legs.mapIndexed { i, l ->
            when (i) { 1 -> l.copy(angleDegrees = l.angleDegrees + err); 2 -> l.copy(horizontalDistanceMetres = l.horizontalDistanceMetres + 0.05); else -> l }
        }
        val noisy = TraverseWorkflow.solve(TraverseKind.CLOSED_ON_KNOWN_POINT, a, g(a, r1), noisyLegs, closing, b, g(b, r2))
        near(noisy.angular.angularMisclosureDegrees!!, err, 1e-9, "angular misclosure = injected error")
        check(noisy.traverse.linearMisclosureMetres!! in 0.01..0.2) { "linear ${noisy.traverse.linearMisclosureMetres}" }
        near(noisy.traverse.points.last().point.x, b.x, 1e-9, "closes on B x")
        near(noisy.traverse.points.last().point.y, b.y, 1e-9, "closes on B y")
        check(abs(noisy.traverse.points[2].point.x - s2.x) < 0.05) { "S2 recovered within 5 cm" }

        // Loop: A -> S1 -> S2 -> A, closing angle back to R1.
        val loopRoute = listOf(a, s1, s2, a)
        val loopAngles = listOf(ang(g(a, r1), g(a, s1)), ang(g(s1, a), g(s1, s2)), ang(g(s2, s1), g(s2, a)))
        val loopLegs = loopRoute.zipWithNext().mapIndexed { i, (p, q) -> TraverseLegObservation(p.id, loopAngles[i], d(p, q), q.id) }
        val loop = TraverseWorkflow.solve(TraverseKind.LOOP, a, g(a, r1), loopLegs, ang(g(a, s2), g(a, r1)))
        near(loop.angular.angularMisclosureDegrees!!, 0.0, 1e-9, "loop angular")
        near(loop.traverse.linearMisclosureMetres!!, 0.0, 1e-6, "loop linear")

        // Open: same first two legs, no closure.
        val open = TraverseWorkflow.solve(TraverseKind.OPEN, a, g(a, r1), legs.take(2))
        check(open.angular.angularMisclosureDegrees == null && open.traverse.linearMisclosureMetres == null)
        near(open.traverse.points.last().point.x, s2.x, 1e-6, "open S2")
        var failed = false
        try { TraverseWorkflow.solve(TraverseKind.LOOP, a, g(a, r1), legs, 0.0) } catch (e: IllegalArgumentException) { failed = true }
        check(failed) { "loop must end on start point" }
    }

    test("Point CSV import: separators, decimal comma, header, order, duplicates, codes") {
        val semi = "Nom;X;Y;Z;Code\nP1;1234,567;4567,891;100,25;BORNE\nP2;1300,0;4600,0;;\nP1;1;2;3;dup\nP3;abc;1;2;x\n"
        val r1 = PointCsv.parse(semi)
        check(r1.delimiter == ';' && r1.decimalComma)
        check(r1.points.size == 2) { r1.points.toString() }
        near(r1.points[0].x, 1234.567, 1e-9, "x comma"); near(r1.points[0].z!!, 100.25, 1e-9, "z")
        check(r1.points[0].code == "BORNE" && r1.points[1].z == null)
        check(r1.skipped.map { it.reason } == listOf(PointCsv.HEADER_REASON, PointCsv.DUPLICATE_REASON, PointCsv.NUMBER_REASON)) { r1.skipped.toString() }

        val comma = "1,4567.891,1234.567,100.0,TN\n2,4600.0,1300.0,101.5\n"
        val r2 = PointCsv.parse(comma, CsvColumnOrder.NAME_Y_X_Z)
        check(r2.delimiter == ',' && !r2.decimalComma && r2.points.size == 2)
        near(r2.points[0].x, 1234.567, 1e-9, "PNEZ x"); near(r2.points[0].y, 4567.891, 1e-9, "PNEZ y")
        check(r2.points[0].code == "TN")

        val spaces = "S1  1234,50  4567,25  12,0\nS2 1235,50 4568,25\n"
        val r3 = PointCsv.parse(spaces)
        check(r3.delimiter == ' ' && r3.decimalComma && r3.points.size == 2) { r3.toString() }
        near(r3.points[1].y, 4568.25, 1e-9, "space y")

        val tab = "\uFEFFA\t10.5\t20.5\tCODE ONLY\n"
        val r4 = PointCsv.parse(tab)
        check(r4.delimiter == '\t' && r4.points.size == 1 && r4.points[0].z == null && r4.points[0].code == "CODE ONLY") { r4.toString() }

        val written = PointCsv.write(r1.points)
        val again = PointCsv.parse(written)
        check(again.points == r1.points) { "round trip: $again" }
    }

    test("Two faces CG/CD: mean, collimation, wrap-around, zenith index error") {
        var f = TwoFace.horizontal(10.0, 190.001)
        near(f.meanDegrees, 10.0005, 1e-9, "mean"); near(f.deltaDegrees, 0.001, 1e-9, "delta"); near(f.collimationDegrees, 0.0005, 1e-9, "c")
        f = TwoFace.horizontal(359.999, 180.0)
        near(f.meanDegrees, 359.9995, 1e-9, "wrap mean"); near(f.deltaDegrees, 0.001, 1e-9, "wrap delta")
        near(TwoFace.horizontal(0.0005, 179.9995).meanDegrees, 0.0, 1e-9, "mean 0")
        val z = TwoFace.zenith(95.0, 265.01)
        near(z.indexErrorDegrees, 0.005, 1e-9, "index"); near(z.correctedZenithDegrees, 94.995, 1e-9, "Zc")
        near(TwoFace.zenith(95.0, 265.0).correctedZenithDegrees, 95.0, 1e-12, "perfect")
    }

    test("Levelling compensation: by distance, equally per setup (N), by |ΔH|; tolerance 4.6√N") {
        fun setups(withIntermediate: Boolean) = listOf(
            LevelSetup(StaffReading("A", 1.5), emptyList(), StaffReading("1", 1.0), 100.0),
            LevelSetup(StaffReading("1", 1.2), if (withIntermediate) listOf(StaffReading("I", 1.7, 25.0)) else emptyList(), StaffReading("2", 2.2), 50.0),
            LevelSetup(StaffReading("2", 1.0), emptyList(), StaffReading("B", 0.5), 50.0)
        )
        fun fore(r: LevelingResult, id: String) = r.observations.first { it.pointId == id && it.sightType == SightType.FORESIGHT }.adjustedElevationMetres
        val eq = Leveling.compute("A", 100.0, setups(true), 99.994, LevelAdjustmentMethod.EQUAL_PER_SETUP)
        near(eq.closureErrorMetres!!, 0.006, 1e-12, "closure")
        near(fore(eq, "1"), 100.498, 1e-9, "N p1"); near(fore(eq, "2"), 99.496, 1e-9, "N p2"); near(fore(eq, "B"), 99.994, 1e-9, "N B")
        near(eq.observations.first { it.pointId == "I" }.adjustedElevationMetres, 99.997, 1e-9, "N intermediate")
        val ab = Leveling.compute("A", 100.0, setups(false), 99.994, LevelAdjustmentMethod.PROPORTIONAL_TO_ABS_DELTA)
        near(fore(ab, "1"), 100.4985, 1e-9, "|dH| p1"); near(fore(ab, "2"), 99.4955, 1e-9, "|dH| p2"); near(fore(ab, "B"), 99.994, 1e-9, "|dH| B")
        val di = Leveling.compute("A", 100.0, setups(true), 99.994, LevelAdjustmentMethod.PROPORTIONAL_TO_DISTANCE)
        near(fore(di, "1"), 100.497, 1e-9, "dist p1"); near(fore(di, "2"), 99.4955, 1e-9, "dist p2")
        near(di.observations.first { it.pointId == "I" }.adjustedElevationMetres, 100.0 - 0.006 * 125.0 / 200.0, 1e-9, "dist intermediate")
        near(Leveling.closureToleranceBySetupsMetres(4.6, 4), 0.0092, 1e-12, "4.6√4")
    }

    test("Cross-section areas (OFPPT M07 profil P1 = 64.40 m²), mixed cut/fill, average end areas") {
        val partial = listOf(0.0, 3.50, 4.90, 5.40, 4.40, 4.80, 3.00, 3.00, 0.70, 2.30)
        val x = partial.runningReduce { a, b -> a + b }
        val h = listOf(3.70, 3.20, 2.20, 1.20, 0.20, 1.20, 2.20, 3.20, 3.20, 3.79)
        val a = CrossSections.areas(x, h)
        near(a.cut, 64.4035, 1e-9, "S1"); near(a.fill, 0.0, 1e-12, "no fill")
        val m = CrossSections.areas(listOf(0.0, 10.0), listOf(1.0, -1.0))
        near(m.cut, 2.5, 1e-12, "mixed cut"); near(m.fill, 2.5, 1e-12, "mixed fill")
        near(CutFillIntegration.zeroCrossings(listOf(0.0, 10.0), listOf(1.0, -3.0)).single(), 2.5, 1e-12, "crossing")
        val v = CrossSections.averageEndArea(listOf(SectionAreas("A", 0.0, 10.0, 0.0), SectionAreas("B", 20.0, 30.0, 4.0), SectionAreas("C", 50.0, 0.0, 10.0)))
        near(v.intervals[0].volume.cut, 400.0, 1e-9, "AB cut"); near(v.intervals[0].volume.fill, 40.0, 1e-9, "AB fill")
        near(v.total.cut, 850.0, 1e-9, "total cut"); near(v.total.fill, 250.0, 1e-9, "total fill")
    }

    test("Long profile: design grade, heights, points de passage, slopes, areas") {
        val st = listOf(ProfileStation("P0", 0.0, 101.0), ProfileStation("P1", 20.0, 100.0), ProfileStation("P2", 40.0, 99.0))
        val r = LongProfile.compute(st, LongProfile.singleGrade(0.0, 100.5, -1.0, 40.0))
        near(r.rows[1].designZ, 100.3, 1e-12, "design P1"); near(r.rows[2].height, -1.1, 1e-12, "h P2")
        near(r.rows[1].groundSlopePercent!!, -5.0, 1e-12, "TN slope"); near(r.rows[2].designSlopePercent!!, -1.0, 1e-12, "design slope")
        near(r.crossings.single().first, 12.5, 1e-12, "crossing PK"); near(r.crossings.single().second, 100.375, 1e-12, "crossing Z")
        near(r.area.cut, 3.125, 1e-12, "cut area"); near(r.area.fill, 15.125, 1e-12, "fill area")
        val breaks = listOf(GradeBreak(0.0, 100.5), GradeBreak(20.0, 100.5), GradeBreak(40.0, 99.5))
        near(LongProfile.designAt(breaks, 60.0), 98.5, 1e-12, "extrapolated design")
        near(LongProfile.designAt(breaks, 30.0), 100.0, 1e-12, "design in 2nd grade")
        val r2 = LongProfile.compute(st, breaks) // break at 20 between stations is exact
        near(r2.area.cut, 0.5 / 2 * (20 * 0.5 / 1.0), 1e-12, "cut with break")
    }

    test("Grid volume (plans cotés): full cut, transition cell, holes, balance level, parsing") {
        val full = GridVolume.compute(List(3) { List<Double?>(3) { 101.0 } }, 10.0, 10.0, 100.0)
        near(full.volume.cut, 400.0, 1e-9, "full cut"); near(full.area, 400.0, 1e-9, "area")
        val mixed = GridVolume.compute(listOf(listOf(101.0, 99.0), listOf(101.0, 99.0)), 10.0, 10.0, 100.0)
        near(mixed.volume.cut, 25.0, 1e-9, "transition cut"); near(mixed.volume.fill, 25.0, 1e-9, "transition fill")
        near(mixed.balanceLevel, 100.0, 1e-12, "balance")
        near(GridVolume.compute(listOf(listOf(102.0, 100.0), listOf(100.0, 100.0)), 10.0, 10.0, 100.0).volume.cut, 50.0, 1e-9, "one corner")
        val holed = GridVolume.compute(listOf(listOf(1.0, 1.0, 1.0), listOf(1.0, 1.0, null)), 1.0, 1.0, 0.0)
        check(holed.cells.size == 1); near(holed.volume.cut, 1.0, 1e-12, "holed")
        val g = GridVolume.parse("101 99,5 -\n101;99;100\n")
        check(g == listOf(listOf(101.0, 99.5, null), listOf(101.0, 99.0, 100.0))) { g.toString() }
        val b = GridVolume.compute(listOf(listOf(10.0, 12.0, 11.0), listOf(9.0, 13.0, 10.0)), 5.0, 4.0, 0.0).balanceLevel
        val atBalance = GridVolume.compute(listOf(listOf(10.0, 12.0, 11.0), listOf(9.0, 13.0, 10.0)), 5.0, 4.0, b)
        near(atBalance.volume.net, 0.0, 1e-9, "net zero at balance level")
    }

    test("Spot levels and height staking (OFPPT M07 p.92, ex. 4.1, exam Q5)") {
        near(SpotLevels.compute(SpotLevels.lineOfSight(1120.0, 1.60), listOf(SpotLevelInput("B", null, 1121.0)))[0].readingForDesign!!, 0.60, 1e-9, "p92")
        val ex = SpotLevels.compute(120.0 + 1.50, listOf(SpotLevelInput("1", null, 119.50), SpotLevelInput("2", null, 118.80), SpotLevelInput("3", null, 120.50)))
        near(ex[0].readingForDesign!!, 2.00, 1e-9, "ex1"); near(ex[1].readingForDesign!!, 2.70, 1e-9, "ex2"); near(ex[2].readingForDesign!!, 1.00, 1e-9, "ex3")
        val q5 = SpotLevels.compute(SpotLevels.lineOfSight(20.153, 1.845), listOf(SpotLevelInput("P1", 2.409, 19.0), SpotLevelInput("P2", 3.614, null), SpotLevelInput("P3", 0.175, null)))
        near(q5[0].groundZ!!, 19.589, 1e-9, "P1"); near(q5[1].groundZ!!, 18.384, 1e-9, "P2"); near(q5[2].groundZ!!, 21.823, 1e-9, "P3")
        near(q5[0].height!!, 0.589, 1e-9, "P1 cut")
        near(SpotLevels.gradeLevels(100.0, 1.25, listOf(0.0, 25.0))[1], 100.3125, 1e-12, "grade 1.25%")
    }

    test("Field tools: TP slope, scale, repeated measurements (exam Q2), contour interpolation, baseline offsets") {
        val s = FieldTools.slope(29.0 - 33.0, kotlin.math.abs(19.0 - 12.0))
        near(s.ratio, -0.5714285714, 1e-9, "slope"); near(s.percent, -57.14285714, 1e-7, "%")
        near(FieldTools.scaleFrom(2.5, 25.0), 1000.0, 1e-9, "scale"); near(FieldTools.groundFromPlan(7.4, 500.0), 37.0, 1e-9, "7.4 cm @1/500")
        val q2 = FieldTools.stats(listOf(128.567, 128.572, 128.563, 128.568, 128.562, 128.575, 128.571, 128.565, 128.569, 128.574))
        near(q2.mean, 128.5686, 1e-9, "mean"); check(q2.outliers.isEmpty())
        val withBad = FieldTools.stats(listOf(10.0, 10.01, 9.99, 10.0, 10.01, 9.99, 10.0, 10.0, 10.01, 9.99, 10.30))
        check(withBad.outliers == listOf(10)) { "outliers ${withBad.outliers}" }
        near(FieldTools.interpolateZ(150.0, 155.0, 20.0, 8.0), 152.0, 1e-12, "interp")
        near(FieldTools.distanceForZ(150.0, 155.0, 20.0, 152.0)!!, 8.0, 1e-12, "inverse interp")
        check(FieldTools.distanceForZ(150.0, 155.0, 20.0, 160.0) == null)
        val (px, py) = FieldTools.fromBaseline(0.0, 0.0, 10.0, 0.0, 5.0, 2.0)
        near(px, 5.0, 1e-12, "x right"); near(py, -2.0, 1e-12, "y right")
        val back = FieldTools.toBaseline(100.0, 200.0, 130.0, 240.0, 112.0, 206.0)
        val (qx, qy) = FieldTools.fromBaseline(100.0, 200.0, 130.0, 240.0, back.abscissa, back.offset)
        near(qx, 112.0, 1e-9, "round x"); near(qy, 206.0, 1e-9, "round y")
    }

    test("OFPPT M07 exam Q4 (CG/CD in grads): means and angles A1B, C1D; typo 8,38 detected") {
        fun g(v: Double) = v * 0.9
        fun mean(cg: Double, cd: Double) = TwoFace.horizontal(g(cg), g(cd)).meanDegrees / 0.9
        val a = mean(104.85, 304.80); val b = mean(228.43, 28.38); val c = mean(313.64, 113.60); val d = mean(66.20, 266.24)
        near(a, 104.825, 1e-9, "A"); near(b, 228.405, 1e-9, "B"); near(c, 313.620, 1e-9, "C"); near(d, 66.220, 1e-9, "D")
        near(Angles.normalize360(g(b) - g(a)) / 0.9, 123.580, 1e-9, "A1B")
        near(Angles.normalize360(g(d) - g(c)) / 0.9, 152.600, 1e-9, "C1D")
        check(kotlin.math.abs(TwoFace.horizontal(g(228.43), g(8.38)).deltaDegrees / 0.9) > 19.0) { "typo must be detected" }
    }

    println("\nVERIFICATION RESULT: $passed test groups passed.")
}
