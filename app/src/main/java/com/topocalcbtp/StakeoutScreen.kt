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
import com.topocalcbtp.engine.stakeout.Stakeout

/** Point to set out. z = NaN when unknown (keeps the saver simple). */
data class StakeTarget(val name: String, val x: Double, val y: Double, val z: Double = Double.NaN)

internal val StakeTargetListSaver = listSaver<List<StakeTarget>, Any>(
    save = { list -> list.flatMap { listOf(it.name, it.x, it.y, it.z) } },
    restore = { flat -> flat.chunked(4).map { StakeTarget(it[0] as String, it[1] as Double, it[2] as Double, it[3] as Double) } }
)

data class StakeRow(val name: String, val hzInUnit: Double, val distance: Double, val deltaZ: Double?)

data class StakeResult(val v0InUnit: Double, val referenceDistance: Double, val rows: List<StakeRow>)

@Composable
fun StakeoutScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val context = LocalContext.current
    val unit = env.unit
    val u = unit.symbol
    var stationName by rememberSaveable { mutableStateOf("ST") }
    var stationX by rememberSaveable { mutableStateOf("") }
    var stationY by rememberSaveable { mutableStateOf("") }
    var stationZ by rememberSaveable { mutableStateOf("") }
    var refName by rememberSaveable { mutableStateOf("R") }
    var refX by rememberSaveable { mutableStateOf("") }
    var refY by rememberSaveable { mutableStateOf("") }
    var hzRef by rememberSaveable { mutableStateOf("") }
    var targets by rememberSaveable(stateSaver = StakeTargetListSaver) { mutableStateOf(listOf<StakeTarget>()) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newX by rememberSaveable { mutableStateOf("") }
    var newY by rememberSaveable { mutableStateOf("") }
    var newZ by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<StakeResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    OnAngleUnitChange(unit) { from, to -> hzRef = convertAngleText(hzRef, from, to); result = null }

    fun record(): Record {
        val rec = Record(CalcType.STAKEOUT)
        rec["stationName"] = stationName; rec["stationX"] = stationX; rec["stationY"] = stationY; rec["stationZ"] = stationZ
        rec["refName"] = refName; rec["refX"] = refX; rec["refY"] = refY
        rec["hzRefDeg"] = parseNum(hzRef)?.let { fmtTrim(unit.toDegrees(it), 8) } ?: ""
        targets.forEach { rec.rows += listOf(it.name, fmtTrim(it.x, 4), fmtTrim(it.y, 4), if (it.z.isNaN()) "" else fmtTrim(it.z, 4)) }
        return rec
    }

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.STAKEOUT) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            stationName = rec.text("stationName"); stationX = rec.text("stationX"); stationY = rec.text("stationY")
            stationZ = rec.text("stationZ")
            refName = rec.text("refName"); refX = rec.text("refX"); refY = rec.text("refY")
            hzRef = parseNum(rec.text("hzRefDeg"))?.let { fmtTrim(unit.fromDegrees(it), 6) } ?: ""
            targets = rec.rows.mapNotNull { r ->
                if (r.size < 3) null else {
                    val x = parseNum(r[1]); val y = parseNum(r[2])
                    if (x == null || y == null) null else StakeTarget(r[0], x, y, r.getOrNull(3)?.let { parseNum(it) } ?: Double.NaN)
                }
            }
            result = null; error = null
            env.toast(env.t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val r = result ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString {
                append("Station;${stationName.trim()};V0 ($u);${fmtPlain(r.v0InUnit, 4)}\n")
                append("Point;Hz ($u);Dh (m);dZ (m)\n")
                r.rows.forEach { append("${it.name};${fmtPlain(it.hzInUnit, 4)};${fmtPlain(it.distance)};${it.deltaZ?.let { d -> fmtPlain(d) } ?: ""}\n") }
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(TextFiles.encodeForExcel(csv)) }
                ?: throw java.io.IOException("Cannot open destination")
        }.onSuccess { env.toast(env.t("Fichier enregistré", "تم حفظ الملف")) }
            .onFailure { error = errorMessage(env.french, it) }
    }

    ScreenColumn(modifier) {
        ScreenTitle(env.t("Implantation", "التوقيع (Implantation)"))
        PointInput(env, env.t("Station (connue)", "المحطة (معروفة)"),
            stationName, { stationName = it; result = null }, stationX, { stationX = it; result = null }, stationY, { stationY = it; result = null })
        Field(env.t("Altitude station (optionnel, pour ΔZ)", "منسوب المحطة (اختياري، لـ ΔZ)"), stationZ, { stationZ = it; result = null },
            Modifier.fillMaxWidth(), signed = true)
        PointInput(env, env.t("Référence d'orientation", "نقطة التوجيه"),
            refName, { refName = it; result = null }, refX, { refX = it; result = null }, refY, { refY = it; result = null })
        Field(env.t("Lecture Hz sur la référence ($u)", "قراءة Hz على نقطة التوجيه ($u)"), hzRef, { hzRef = it; result = null }, Modifier.fillMaxWidth())
        Hint(env.t("Mettez 0 si vous avez fait le zéro sur la référence.", "ضع 0 إذا صفّرت الجهاز على نقطة التوجيه."))

        HorizontalDivider()
        SectionTitle(env.t("Points à implanter (${targets.size})", "النقط المراد توقيعها (${targets.size})"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PointPickerButton(env, env.t("+ Point du projet", "+ نقطة من المشروع")) { p ->
                targets = targets.filterNot { it.name == p.name } + StakeTarget(p.name, p.x, p.y, p.z ?: Double.NaN); result = null
            }
            TextButton(onClick = {
                if (env.points.isEmpty()) env.toast(env.t("Aucun point dans le projet.", "لا توجد نقط في المشروع."))
                else {
                    val skip = setOf(stationName.trim(), refName.trim())
                    val existing = targets.map { it.name }.toSet()
                    targets = targets + env.points.filter { it.name !in skip && it.name !in existing }
                        .map { StakeTarget(it.name, it.x, it.y, it.z ?: Double.NaN) }
                    result = null
                }
            }) { Text(env.t("Tous les points", "كل النقط")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(env.t("Nom", "الاسم"), newName, { newName = it }, Modifier.weight(0.8f), numeric = false)
            Field("X", newX, { newX = it }, Modifier.weight(1f), signed = true)
            Field("Y", newY, { newY = it }, Modifier.weight(1f), signed = true)
            Field("Z", newZ, { newZ = it }, Modifier.weight(0.7f), signed = true)
        }
        OutlinedButton(onClick = {
            error = null
            val x = parseNum(newX); val y = parseNum(newY); val z = if (newZ.isBlank()) Double.NaN else parseNum(newZ)
            when {
                newName.isBlank() -> error = env.t("Nom du point manquant.", "اسم النقطة ناقص.")
                x == null || y == null -> error = env.t("X et Y sont obligatoires.", "X و Y إجباريان.")
                z == null -> error = env.t("Z invalide.", "Z غير صالح.")
                else -> {
                    targets = targets.filterNot { it.name == newName.trim() } + StakeTarget(newName.trim(), x, y, z)
                    newName = ""; newX = ""; newY = ""; newZ = ""; result = null
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Ajouter ce point", "إضافة هذه النقطة")) }
        targets.forEach { t ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${t.name}  X ${fmt(t.x)}  Y ${fmt(t.y)}" + if (t.z.isNaN()) "" else "  Z ${fmt(t.z)}", Modifier.weight(1f))
                TextButton(onClick = { targets = targets.filterNot { it.name == t.name }; result = null }) { Text("✕") }
            }
        }

        Button(onClick = {
            error = null; result = null
            val sx = parseNum(stationX); val sy = parseNum(stationY)
            val rx = parseNum(refX); val ry = parseNum(refY); val hz = parseNum(hzRef)
            val sz = if (stationZ.isBlank()) null else parseNum(stationZ)
            when {
                sx == null || sy == null -> error = env.t("Coordonnées de la station manquantes.", "إحداثيات المحطة ناقصة.")
                rx == null || ry == null -> error = env.t("Coordonnées de la référence manquantes.", "إحداثيات نقطة التوجيه ناقصة.")
                hz == null -> error = env.t("Lecture Hz sur la référence manquante.", "قراءة Hz على نقطة التوجيه ناقصة.")
                stationZ.isNotBlank() && sz == null -> error = env.t("Altitude station invalide.", "منسوب المحطة غير صالح.")
                targets.isEmpty() -> error = env.t("Ajouter au moins un point à implanter.", "أضف نقطة واحدة على الأقل.")
                else -> runCatching {
                    val station = Point2D(stationName.ifBlank { "ST" }.trim(), sx, sy)
                    val ref = Coordinates.inverse(station, Point2D("R", rx, ry))
                    require(ref.distanceMetres > 0.0) { env.t("La référence est confondue avec la station.", "نقطة التوجيه هي نفسها المحطة.") }
                    val v0 = Stakeout.orientationConstant(ref.gisementDegrees, unit.toDegrees(hz))
                    val rows = targets.map { t ->
                        val e = Stakeout.calculate(station, Point2D(t.name, t.x, t.y), v0)
                        StakeRow(t.name, unit.normalizeFull(unit.fromDegrees(e.horizontalCircleReadingDegrees ?: 0.0)),
                            e.horizontalDistanceMetres, if (sz != null && !t.z.isNaN()) t.z - sz else null)
                    }
                    StakeResult(unit.normalizeFull(unit.fromDegrees(v0)), ref.distanceMetres, rows)
                }.onSuccess { result = it }.onFailure { error = errorMessage(env.french, it) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Calculer l'implantation", "حساب التوقيع")) }
        ErrorText(error)

        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("V0 = ${fmt(r.v0InUnit, 4)} $u", fontWeight = FontWeight.SemiBold)
                    Text(env.t("Contrôle : distance station → référence", "تحقق: المسافة المحطة ← المرجع") + " = ${fmt(r.referenceDistance)} m")
                    Hint(env.t("Mesurez cette distance sur le terrain avant d'implanter.", "قِس هذه المسافة في الميدان قبل التوقيع."))
                    HorizontalDivider()
                    r.rows.forEach { row ->
                        Text("${row.name}:  Hz ${fmt(row.hzInUnit, 4)} $u   Dh ${fmt(row.distance)} m" +
                            (row.deltaZ?.let { "   ΔZ ${SurveyMath.formatMeters(it)}" } ?: ""))
                    }
                    Hint(env.t("Hz = lecture à afficher sur le cercle horizontal (sens horaire). ΔZ = Z point − Z station.",
                        "Hz = القراءة التي يجب ضبطها على الدائرة الأفقية (مع عقارب الساعة). ΔZ = Z النقطة − Z المحطة."))
                    OutlinedButton(onClick = { csvLauncher.launch("implantation.csv") }) { Text("CSV") }
                }
            }
        }
        if (targets.isNotEmpty()) {
            SaveToProjectButton(env, env.t("Enregistrer le calcul", "حفظ الحساب")) {
                env.saveCalculation(CalcType.STAKEOUT, "Implantation depuis ${stationName.trim()}",
                    "${targets.size} " + env.t("points", "نقط") + (result?.let { " · V0 = ${fmt(it.v0InUnit, 4)} $u" } ?: ""), record())
            }
        }
    }
}
