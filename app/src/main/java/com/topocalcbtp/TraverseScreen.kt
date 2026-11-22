@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.coordinates.Coordinates
import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.io.CsvPoint
import com.topocalcbtp.engine.io.PointCsv
import com.topocalcbtp.engine.traverse.AngularTraverse
import com.topocalcbtp.engine.traverse.AngularTraverseSolution
import com.topocalcbtp.engine.traverse.TraverseKind
import com.topocalcbtp.engine.traverse.TraverseLegObservation
import com.topocalcbtp.engine.traverse.TraverseWorkflow

/** A leg as typed by the user. The angle is kept in DEGREES whatever the display unit. */
data class TraverseLegInput(val station: String, val angleDegrees: Double, val distance: Double, val next: String)

internal val TraverseLegListSaver = listSaver<List<TraverseLegInput>, Any>(
    save = { list -> list.flatMap { listOf(it.station, it.angleDegrees, it.distance, it.next) } },
    restore = { flat -> flat.chunked(4).map { TraverseLegInput(it[0] as String, it[1] as Double, it[2] as Double, it[3] as String) } }
)

@Composable
fun TraverseScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val context = LocalContext.current
    val unit = env.unit
    val u = unit.symbol
    val small = smallAngleUnitLabel(unit)
    var kind by rememberSaveable { mutableIntStateOf(0) } // 0 encadré, 1 fermé, 2 ouvert
    var startName by rememberSaveable { mutableStateOf("A") }
    var startX by rememberSaveable { mutableStateOf("") }
    var startY by rememberSaveable { mutableStateOf("") }
    var startRefMode by rememberSaveable { mutableIntStateOf(0) } // 0 point, 1 gisement
    var startRefName by rememberSaveable { mutableStateOf("R1") }
    var startRefX by rememberSaveable { mutableStateOf("") }
    var startRefY by rememberSaveable { mutableStateOf("") }
    var startGis by rememberSaveable { mutableStateOf("") }
    var legs by rememberSaveable(stateSaver = TraverseLegListSaver) { mutableStateOf(listOf<TraverseLegInput>()) }
    var angle by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("S1") }
    var closingAngle by rememberSaveable { mutableStateOf("") }
    var endName by rememberSaveable { mutableStateOf("B") }
    var endX by rememberSaveable { mutableStateOf("") }
    var endY by rememberSaveable { mutableStateOf("") }
    var endRefMode by rememberSaveable { mutableIntStateOf(0) }
    var endRefName by rememberSaveable { mutableStateOf("R2") }
    var endRefX by rememberSaveable { mutableStateOf("") }
    var endRefY by rememberSaveable { mutableStateOf("") }
    var endGis by rememberSaveable { mutableStateOf("") }
    var kAngle by rememberSaveable { mutableStateOf("") }
    var solution by remember { mutableStateOf<AngularTraverseSolution?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentStation = legs.lastOrNull()?.next ?: startName.trim()

    OnAngleUnitChange(unit) { from, to ->
        angle = convertAngleText(angle, from, to)
        closingAngle = convertAngleText(closingAngle, from, to)
        startGis = convertAngleText(startGis, from, to)
        endGis = convertAngleText(endGis, from, to)
        // kAngle is in arc-seconds or milligons: convert too.
        parseNum(kAngle)?.let { k ->
            val deg = if (from == AngleUnit.DEGREES) k / 3600.0 else from.toDegrees(k / 1000.0)
            kAngle = fmtTrim(degreesToSmallAngle(deg, to), 2)
        }
    }

    fun record(): Record {
        fun deg(t: String) = parseNum(t)?.let { fmtTrim(unit.toDegrees(it), 8) } ?: ""
        val rec = Record(CalcType.TRAVERSE)
        rec["kind"] = kind.toString()
        rec["startName"] = startName; rec["startX"] = startX; rec["startY"] = startY
        rec["startRefMode"] = startRefMode.toString()
        rec["startRefName"] = startRefName; rec["startRefX"] = startRefX; rec["startRefY"] = startRefY
        rec["startGisDeg"] = deg(startGis)
        rec["closingDeg"] = deg(closingAngle)
        rec["endName"] = endName; rec["endX"] = endX; rec["endY"] = endY
        rec["endRefMode"] = endRefMode.toString()
        rec["endRefName"] = endRefName; rec["endRefX"] = endRefX; rec["endRefY"] = endRefY
        rec["endGisDeg"] = deg(endGis)
        rec["kAngleDeg"] = parseNum(kAngle)?.let {
            fmtTrim(if (unit == AngleUnit.DEGREES) it / 3600.0 else unit.toDegrees(it / 1000.0), 10)
        } ?: ""
        legs.forEach { rec.rows += listOf(it.station, fmtTrim(it.angleDegrees, 10), fmtTrim(it.distance, 4), it.next) }
        return rec
    }

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.TRAVERSE) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            fun ang(key: String) = parseNum(rec.text(key))?.let { fmtTrim(unit.fromDegrees(it), 6) } ?: ""
            kind = rec.text("kind").toIntOrNull()?.coerceIn(0, 2) ?: 0
            startName = rec.text("startName"); startX = rec.text("startX"); startY = rec.text("startY")
            startRefMode = rec.text("startRefMode").toIntOrNull()?.coerceIn(0, 1) ?: 0
            startRefName = rec.text("startRefName"); startRefX = rec.text("startRefX"); startRefY = rec.text("startRefY")
            startGis = ang("startGisDeg"); closingAngle = ang("closingDeg")
            endName = rec.text("endName"); endX = rec.text("endX"); endY = rec.text("endY")
            endRefMode = rec.text("endRefMode").toIntOrNull()?.coerceIn(0, 1) ?: 0
            endRefName = rec.text("endRefName"); endRefX = rec.text("endRefX"); endRefY = rec.text("endRefY")
            endGis = ang("endGisDeg")
            kAngle = parseNum(rec.text("kAngleDeg"))?.let { fmtTrim(degreesToSmallAngle(it, unit), 2) } ?: ""
            legs = rec.rows.mapNotNull { r ->
                if (r.size < 4) null else {
                    val a = parseNum(r[1]); val d = parseNum(r[2])
                    if (a == null || d == null) null else TraverseLegInput(r[0], a, d, r[3])
                }
            }
            next = "S${legs.size + 1}"; angle = ""; distance = ""
            solution = null; error = null
            env.toast(env.t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val s = solution ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val csv = PointCsv.write(s.traverse.points.map { CsvPoint(it.point.id, it.point.x, it.point.y) })
            context.contentResolver.openOutputStream(uri)?.use { it.write(TextFiles.encodeForExcel(csv)) }
                ?: throw java.io.IOException("Cannot open destination")
        }.onSuccess { env.toast(env.t("Fichier enregistré", "تم حفظ الملف")) }
            .onFailure { error = errorMessage(env.french, it) }
    }

    fun compute() {
        error = null; solution = null
        val sx = parseNum(startX); val sy = parseNum(startY)
        if (startName.isBlank() || sx == null || sy == null) { error = env.t("Point de départ incomplet.", "نقطة البداية ناقصة."); return }
        val start = Point2D(startName.trim(), sx, sy)
        fun refGisement(mode: Int, from: Point2D, rx: String, ry: String, gis: String, what: String): Double? {
            return if (mode == 0) {
                val x2 = parseNum(rx); val y2 = parseNum(ry)
                if (x2 == null || y2 == null) { error = what; null }
                else {
                    val inv = Coordinates.inverse(from, Point2D("R", x2, y2))
                    if (inv.distanceMetres == 0.0) { error = what; null } else inv.gisementDegrees
                }
            } else parseNum(gis)?.let { unit.toDegrees(it) } ?: run { error = what; null }
        }
        val g0 = refGisement(startRefMode, start, startRefX, startRefY, startGis,
            env.t("Référence de départ invalide.", "مرجع البداية غير صالح.")) ?: return
        if (legs.isEmpty()) { error = env.t("Ajouter au moins une station.", "أضف محطة واحدة على الأقل."); return }
        val obs = legs.map { TraverseLegObservation(it.station, it.angleDegrees, it.distance, it.next) }
        val traverseKind = when (kind) { 0 -> TraverseKind.CLOSED_ON_KNOWN_POINT; 1 -> TraverseKind.LOOP; else -> TraverseKind.OPEN }
        var closingDeg: Double? = null
        var end: Point2D? = null
        var gEnd: Double? = null
        if (traverseKind != TraverseKind.OPEN) {
            closingDeg = parseNum(closingAngle)?.let { unit.toDegrees(it) }
                ?: run { error = env.t("Angle de fermeture manquant.", "زاوية الإغلاق ناقصة."); return }
        }
        if (traverseKind == TraverseKind.CLOSED_ON_KNOWN_POINT) {
            val ex = parseNum(endX); val ey = parseNum(endY)
            if (endName.isBlank() || ex == null || ey == null) { error = env.t("Point d'arrivée incomplet.", "نقطة الوصول ناقصة."); return }
            end = Point2D(endName.trim(), ex, ey)
            gEnd = refGisement(endRefMode, end, endRefX, endRefY, endGis,
                env.t("Référence d'arrivée invalide.", "مرجع الوصول غير صالح.")) ?: return
        }
        runCatching { TraverseWorkflow.solve(traverseKind, start, g0, obs, closingDeg, end, gEnd) }
            .onSuccess { solution = it }
            .onFailure { error = errorMessage(env.french, it) }
    }

    ScreenColumn(modifier) {
        ScreenTitle(env.t("Cheminement (polygonale)", "Cheminement (المضلع)"))
        ChoiceRow(listOf(env.t("Encadré", "مؤطر"), env.t("Fermé", "مغلق"), env.t("Ouvert", "مفتوح")), kind) { kind = it; solution = null }
        Hint(when (kind) {
            0 -> env.t("Départ sur un point connu orienté, arrivée sur un autre point connu orienté. Contrôle angulaire et linéaire.",
                "انطلاق من نقطة معروفة موجّهة ووصول إلى نقطة معروفة أخرى موجّهة. تحقق زاوي وخطي.")
            1 -> env.t("Retour sur le point de départ : la dernière station doit être « $startName », angle de fermeture vers la référence de départ.",
                "العودة لنقطة البداية: آخر محطة يجب أن تكون «$startName»، وزاوية الإغلاق نحو مرجع البداية.")
            else -> env.t("Cheminement ouvert : AUCUN contrôle. À éviter pour des points importants.",
                "مسار مفتوح: بدون أي تحقق. تجنّبه للنقط المهمة.")
        })

        PointInput(env, env.t("Point de départ (connu)", "نقطة البداية (معروفة)"),
            startName, { startName = it; solution = null }, startX, { startX = it; solution = null }, startY, { startY = it; solution = null })
        SectionTitle(env.t("Orientation au départ", "التوجيه عند البداية"))
        ChoiceRow(listOf(env.t("Point de référence", "نقطة مرجعية"), env.t("Gisement connu", "Gisement معروف")), startRefMode) { startRefMode = it; solution = null }
        if (startRefMode == 0) {
            PointInput(env, env.t("Référence de départ", "مرجع البداية"),
                startRefName, { startRefName = it }, startRefX, { startRefX = it; solution = null }, startRefY, { startRefY = it; solution = null })
        } else {
            Field(env.t("Gisement départ → référence ($u)", "Gisement البداية ← المرجع ($u)"), startGis, { startGis = it; solution = null }, Modifier.fillMaxWidth())
        }

        HorizontalDivider()
        SectionTitle(env.t("Stations", "المحطات"))
        Hint(env.t("Angle lu à la station, dans le sens horaire, de la visée arrière vers la visée avant (au départ : de la référence vers la 1re station).",
            "الزاوية المقروءة في المحطة مع عقارب الساعة، من الشوفة الخلفية إلى الأمامية (في البداية: من المرجع إلى أول محطة)."))
        Text(env.t("Station actuelle : ", "المحطة الحالية: ") + currentStation, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(env.t("Angle ($u)", "الزاوية ($u)"), angle, { angle = it }, Modifier.weight(1f))
            Field(env.t("Distance (m)", "المسافة (m)"), distance, { distance = it }, Modifier.weight(1f))
            Field(env.t("Vers", "إلى"), next, { next = it }, Modifier.weight(0.8f), numeric = false)
        }
        Button(onClick = {
            error = null
            val a = parseNum(angle); val d = parseNum(distance); val n = next.trim()
            when {
                currentStation.isBlank() -> error = env.t("Nom du point de départ manquant.", "اسم نقطة البداية ناقص.")
                a == null -> error = env.t("Angle invalide.", "زاوية غير صالحة.")
                d == null || d <= 0 -> error = env.t("La distance doit être > 0.", "المسافة يجب أن تكون أكبر من 0.")
                n.isEmpty() || n == currentStation -> error = env.t("Nom de la station suivante invalide.", "اسم المحطة التالية غير صالح.")
                else -> {
                    legs = legs + TraverseLegInput(currentStation, unit.toDegrees(a), d, n)
                    angle = ""; distance = ""
                    val m = Regex("^(.*?)(\\d+)$").find(n)
                    next = if (m != null) m.groupValues[1] + (m.groupValues[2].toLong() + 1) else n + "1"
                    solution = null
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Ajouter la station", "إضافة المحطة")) }
        legs.forEachIndexed { i, leg ->
            Text("${i + 1}. ${leg.station}: β = ${fmt(unit.normalizeFull(unit.fromDegrees(leg.angleDegrees)), 4)} $u · D = ${fmt(leg.distance)} m → ${leg.next}")
        }
        if (legs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val last = legs.last(); legs = legs.dropLast(1); next = last.next; solution = null
                }) { Text(env.t("Annuler la dernière", "حذف الأخيرة")) }
                TextButton(onClick = { legs = emptyList(); next = "S1"; solution = null }) { Text(env.t("Tout effacer", "مسح الكل")) }
            }
        }

        if (kind != 2) {
            HorizontalDivider()
            SectionTitle(env.t("Fermeture", "الإغلاق"))
            Field(env.t("Angle de fermeture à la dernière station ($u)", "زاوية الإغلاق في آخر محطة ($u)"),
                closingAngle, { closingAngle = it; solution = null }, Modifier.fillMaxWidth())
            if (kind == 0) {
                Hint(env.t("La dernière station ajoutée doit être le point d'arrivée.", "آخر محطة مضافة يجب أن تكون نقطة الوصول."))
                PointInput(env, env.t("Point d'arrivée (connu)", "نقطة الوصول (معروفة)"),
                    endName, { endName = it; solution = null }, endX, { endX = it; solution = null }, endY, { endY = it; solution = null })
                ChoiceRow(listOf(env.t("Point de référence", "نقطة مرجعية"), env.t("Gisement connu", "Gisement معروف")), endRefMode) { endRefMode = it; solution = null }
                if (endRefMode == 0) {
                    PointInput(env, env.t("Référence d'arrivée", "مرجع الوصول"),
                        endRefName, { endRefName = it }, endRefX, { endRefX = it; solution = null }, endRefY, { endRefY = it; solution = null })
                } else {
                    Field(env.t("Gisement arrivée → référence ($u)", "Gisement الوصول ← المرجع ($u)"), endGis, { endGis = it; solution = null }, Modifier.fillMaxWidth())
                }
            }
            Field(env.t("Tolérance angulaire k ($small/√n) — selon CPS, optionnel", "التسامح الزاوي k ($small/√n) — حسب CPS، اختياري"),
                kAngle, { kAngle = it }, Modifier.fillMaxWidth())
        }

        Button(onClick = { compute() }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Calculer le cheminement", "حساب المضلع")) }
        ErrorText(error)

        solution?.let { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val fa = s.angular.angularMisclosureDegrees
                    if (fa != null) {
                        val nAngles = legs.size + 1
                        Text(env.t("Fermeture angulaire fα", "الخطأ الزاوي fα") + " = ${fmt(degreesToSmallAngle(fa, unit), 1)} $small  (n = $nAngles)",
                            fontWeight = FontWeight.SemiBold)
                        Text(env.t("Correction par angle", "التصحيح لكل زاوية") + " = ${fmt(degreesToSmallAngle(s.angular.correctionPerAngleDegrees, unit), 2)} $small")
                        parseNum(kAngle)?.takeIf { it > 0 }?.let { k ->
                            val t = AngularTraverse.angularTolerance(k, nAngles)
                            val ok = kotlin.math.abs(degreesToSmallAngle(fa, unit)) <= t
                            Text(env.t("Tolérance", "التسامح") + " = ${fmt(t, 1)} $small → " +
                                if (ok) env.t("Acceptable", "مقبول") else env.t("HORS TOLÉRANCE — vérifier les angles", "خارج التسامح — راجع الزوايا"),
                                color = if (ok) LocalAppColors.current.ok else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(env.t("Cheminement ouvert : aucun contrôle.", "مسار مفتوح: بدون تحقق."), color = MaterialTheme.colorScheme.error)
                    }
                    val tv = s.traverse
                    val linear = tv.linearMisclosureMetres
                    Text(env.t("Longueur totale", "الطول الكلي") + " = ${fmt(tv.totalLengthMetres, 2)} m")
                    if (linear != null) {
                        Text("fx = ${fmt((tv.misclosureXMetres ?: 0.0) * 1000, 1)} mm   fy = ${fmt((tv.misclosureYMetres ?: 0.0) * 1000, 1)} mm")
                        Text(env.t("Fermeture linéaire", "الخطأ الخطي") + " f = ${fmt(linear * 1000, 1)} mm" +
                            (tv.relativePrecision?.let { "  →  1/${fmt(it, 0)}" } ?: ""), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                    tv.points.drop(1).forEach { p ->
                        Text("${p.point.id}: X ${fmt(p.point.x)}  Y ${fmt(p.point.y)}" +
                            if (linear != null) "  (cx ${fmt(p.correctionXMetres * 1000, 1)}, cy ${fmt(p.correctionYMetres * 1000, 1)} mm)" else "")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveToProjectButton(env, env.t("Enregistrer les points", "حفظ النقط")) {
                            // Known points (start, and end of an encadré) are not overwritten.
                            val known = setOfNotNull(startName.trim(), if (kind == 0) endName.trim() else null)
                            env.savePoints(tv.points.filter { it.point.id !in known }
                                .map { NewPoint(it.point.id, it.point.x, it.point.y, null, "", "CHEMINEMENT") })
                        }
                        OutlinedButton(onClick = { csvLauncher.launch("cheminement.csv") }) { Text("CSV") }
                    }
                }
            }
        }
        if (legs.isNotEmpty()) {
            SaveToProjectButton(env, env.t("Enregistrer le calcul", "حفظ الحساب")) {
                val s = solution
                val summary = buildString {
                    append("${legs.size} ").append(env.t("côtés", "أضلاع"))
                    s?.angular?.angularMisclosureDegrees?.let { append(" · fα = ${fmt(degreesToSmallAngle(it, unit), 1)} $small") }
                    s?.traverse?.linearMisclosureMetres?.let { append(" · f = ${fmt(it * 1000, 1)} mm") }
                }
                env.saveCalculation(CalcType.TRAVERSE, "Cheminement ${startName.trim()} → ${legs.last().next}", summary, record())
            }
        }
    }
}
