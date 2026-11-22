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


internal val LevelShotListSaver = listSaver<List<LevelShot>, Any>(
    save = { list -> list.flatMap { listOf(it.from, it.to, it.backsight, it.foresight, it.distanceMeters) } },
    restore = { flat ->
        flat.chunked(5).map {
            LevelShot(it[0] as String, it[1] as String, it[2] as Double, it[3] as Double, it[4] as Double)
        }
    }
)

@Composable
fun LevelingScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val french = env.french
    val context = LocalContext.current
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var toleranceK by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("A") }
    var to by rememberSaveable { mutableStateOf("P1") }
    var bs by rememberSaveable { mutableStateOf("") }
    var fs by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var upper by rememberSaveable { mutableStateOf("") }
    var middle by rememberSaveable { mutableStateOf("") }
    var lower by rememberSaveable { mutableStateOf("") }
    var stadiaK by rememberSaveable { mutableStateOf("100") }
    var stadiaC by rememberSaveable { mutableStateOf("0") }
    var rows by rememberSaveable(stateSaver = LevelShotListSaver) { mutableStateOf(listOf<LevelShot>()) }
    var stadia by remember { mutableStateOf<com.topocalcbtp.engine.coordinates.StadiaResult?>(null) }
    var result by remember { mutableStateOf<ClosureResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.LEVELING) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            start = rec.text("start"); end = rec.text("end"); toleranceK = rec.text("k")
            rows = rec.rows.mapNotNull { r ->
                if (r.size < 5) null else {
                    val b = parseNum(r[2]); val f = parseNum(r[3]); val d = parseNum(r[4])
                    if (b == null || f == null || d == null) null else LevelShot(r[0], r[1], b, f, d)
                }
            }
            from = rows.lastOrNull()?.to ?: "A"; to = "P${rows.size + 1}"
            result = null; error = null
            env.toast(env.t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }
    fun saveToProject() {
        if (rows.isEmpty()) { error = tr(french, "Rien à enregistrer.", "لا شيء للحفظ."); return }
        val rec = Record(CalcType.LEVELING)
        rec["start"] = start; rec["end"] = end; rec["k"] = toleranceK
        rows.forEach { rec.rows += listOf(it.from, it.to, fmtTrim(it.backsight), fmtTrim(it.foresight), fmtTrim(it.distanceMeters)) }
        val r = result
        val summary = buildString {
            append("${rows.size} ").append(tr(french, "stations", "محطات"))
            if (r?.closureError != null) append(" · f = ${fmt(r.closureError * 1000, 1)} mm")
            if (r != null) append(" · ${r.rows.last().shot.to} = ${fmt(r.rows.last().altitudeTo)} m")
        }
        env.saveCalculation(CalcType.LEVELING, "Nivellement ${rows.first().from} → ${rows.last().to}", summary, rec)
    }

    val reportTitle = tr(french, "Nivellement", "Nivellement") + " ${rows.firstOrNull()?.from ?: ""} → ${rows.lastOrNull()?.to ?: ""}"
    fun export(uri: android.net.Uri?, pdf: Boolean) {
        val r = result ?: return
        if (uri == null) return
        runCatching {
            if (pdf) Exporters.exportPdf(context, uri, reportTitle, r) else Exporters.exportExcel(context, uri, r)
        }.onSuccess {
            env.toast(tr(french, "Fichier enregistré", "تم حفظ الملف"))
        }.onFailure { error = errorMessage(french, it) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { export(it, true) }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { export(it, false) }

    ScreenColumn(modifier) {
        Text(tr(french, "Niveau optique — Nivellement", "Niveau optique — التسوية"),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(tr(french, "Lectures sur mire : lecture arrière LA et lecture avant LV.",
            "أدخل قراءات القامة: القراءة الخلفية LA والقراءة الأمامية LV."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(tr(french, "Altitude départ", "منسوب البداية"), start, { start = it; result = null }, Modifier.weight(1f))
            Field(tr(french, "Altitude arrivée (vide = ouvert)", "منسوب النهاية (فارغ = مفتوح)"), end, { end = it; result = null }, Modifier.weight(1f))
        }
        Field(tr(french, "Tolérance k (mm/√km) — selon CPS, optionnel", "التسامح k (mm/√km) — حسب CPS، اختياري"),
            toleranceK, { toleranceK = it; result = null }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(tr(french, "De", "من"), from, { from = it }, Modifier.weight(1f), numeric = false)
            Field(tr(french, "Vers", "إلى"), to, { to = it }, Modifier.weight(1f), numeric = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("LA (m)", bs, { bs = it }, Modifier.weight(1f))
            Field("LV (m)", fs, { fs = it }, Modifier.weight(1f))
            Field(tr(french, "Portée (m)", "المسافة (m)"), distance, { distance = it }, Modifier.weight(1f))
        }
        Text(tr(french, "La portée = distance arrière + distance avant de la station.",
            "المسافة = المسافة الخلفية + الأمامية لهذه المحطة."), style = MaterialTheme.typography.bodySmall)

        Text(tr(french, "Fils stadimétriques (optionnel)", "الشعيرات الثلاث (اختياري)"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(tr(french, "Fil sup.", "العليا"), upper, { upper = it }, Modifier.weight(1f))
            Field(tr(french, "Fil moy.", "الوسطى"), middle, { middle = it }, Modifier.weight(1f))
            Field(tr(french, "Fil inf.", "السفلى"), lower, { lower = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("K", stadiaK, { stadiaK = it }, Modifier.weight(1f))
            Field("C (m)", stadiaC, { stadiaC = it }, Modifier.weight(1f), signed = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                error = null
                val u = parseNum(upper); val mid = parseNum(middle); val l = parseNum(lower)
                val k = parseNum(stadiaK); val c = parseNum(stadiaC)
                if (u == null || mid == null || l == null || k == null || c == null) {
                    stadia = null
                    error = tr(french, "Saisir les trois fils, K et C.", "أدخل الشعيرات الثلاث و K و C.")
                } else {
                    runCatching { TheodoliteMath.levelStadia(u, mid, l, k, c) }
                        .onSuccess { stadia = it }
                        .onFailure { stadia = null; error = errorMessage(french, it) }
                }
            }, modifier = Modifier.weight(1f)) { Text(tr(french, "Calculer stadia", "حساب المسافة")) }
            TextButton(onClick = { upper = ""; middle = ""; lower = ""; stadia = null }) { Text(tr(french, "Effacer", "مسح")) }
        }
        stadia?.let { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(tr(french, "Distance stadimétrique", "المسافة بالشعيرات") + ": ${fmt(s.estimatedDistanceMetres)} m")
                    Text(tr(french, "Moyenne (sup.+inf.)/2", "متوسط العليا والسفلى") + ": ${fmt(s.expectedMiddleReadingMetres)} m")
                    Text(
                        if (s.middleReadingWithinTolerance) tr(french, "Contrôle du fil moyen : OK", "التحقق من الشعيرة الوسطى: سليم")
                        else tr(french, "Attention : écart fil moyen ", "تنبيه: فرق الشعيرة الوسطى ") + fmt(s.middleReadingResidualMetres) + " m",
                        color = if (s.middleReadingWithinTolerance) LocalAppColors.current.ok else MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { distance = fmt(s.estimatedDistanceMetres, 2) }) {
                            Text(tr(french, "Utiliser comme portée", "استعملها كمسافة"))
                        }
                        TextButton(onClick = { distance = fmt((parseNum(distance) ?: 0.0) + s.estimatedDistanceMetres, 2) }) {
                            Text(tr(french, "+ Ajouter à la portée", "+ أضفها للمسافة"))
                        }
                    }
                }
            }
        }

        Button(onClick = {
            error = null
            val b = parseNum(bs); val f = parseNum(fs); val d = parseNum(distance)
            val fromId = from.trim(); val toId = to.trim()
            when {
                fromId.isEmpty() || toId.isEmpty() -> error = tr(french, "Nom de point manquant.", "اسم النقطة ناقص.")
                fromId == toId -> error = tr(french, "« De » et « Vers » doivent être différents.", "نقطة البداية والنهاية يجب أن تختلفا.")
                rows.isNotEmpty() && rows.last().to != fromId ->
                    error = tr(french, "Le point de départ doit être ${rows.last().to}.", "نقطة البداية يجب أن تكون ${rows.last().to}.")
                b == null || f == null -> error = tr(french, "LA et LV sont obligatoires.", "LA و LV إجباريان.")
                b < 0 || f < 0 -> error = tr(french, "Les lectures ne peuvent pas être négatives.", "القراءات لا يمكن أن تكون سالبة.")
                d == null || d <= 0 -> error = tr(french, "La portée doit être > 0.", "المسافة يجب أن تكون أكبر من 0.")
                else -> {
                    rows = rows + LevelShot(fromId, toId, b, f, d)
                    from = toId
                    to = "P${rows.size + 1}"
                    bs = ""; fs = ""; distance = ""
                    result = null
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr(french, "Ajouter la lecture", "إضافة القراءة")) }

        rows.forEachIndexed { i, shot ->
            ListItem(
                headlineContent = { Text("${i + 1}. ${shot.from} → ${shot.to}") },
                supportingContent = {
                    Text("LA ${fmt(shot.backsight)} | LV ${fmt(shot.foresight)} | ΔH ${SurveyMath.formatMeters(shot.observedDelta)} | ${fmt(shot.distanceMeters, 1)} m")
                }
            )
        }
        if (rows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val last = rows.last()
                    rows = rows.dropLast(1)
                    from = last.from; to = last.to; result = null
                }) { Text(tr(french, "Annuler la dernière", "حذف الأخيرة")) }
                TextButton(onClick = { rows = emptyList(); from = "A"; to = "P1"; result = null }) {
                    Text(tr(french, "Tout effacer", "مسح الكل"))
                }
            }
        }

        Button(onClick = {
            error = null
            val s = parseNum(start)
            val e = if (end.isBlank()) null else parseNum(end)
            val k = if (toleranceK.isBlank()) null else parseNum(toleranceK)
            when {
                s == null -> error = tr(french, "Altitude de départ invalide.", "منسوب البداية غير صالح.")
                end.isNotBlank() && e == null -> error = tr(french, "Altitude d'arrivée invalide.", "منسوب النهاية غير صالح.")
                toleranceK.isNotBlank() && (k == null || k <= 0) -> error = tr(french, "k invalide.", "قيمة k غير صالحة.")
                rows.isEmpty() -> error = tr(french, "Ajouter au moins une lecture.", "أضف قراءة واحدة على الأقل.")
                else -> runCatching { SurveyMath.compensate(s, rows, e, k) }
                    .onSuccess { result = it }
                    .onFailure { result = null; error = errorMessage(french, it) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr(french, "Calculer et compenser", "حساب التعويض والمناسيب")) }

        ErrorText(error)
        if (rows.isNotEmpty()) SaveToProjectButton(env) { saveToProject() }

        result?.let { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ΣLA = ${fmt(r.sumBacksights)}   ΣLV = ${fmt(r.sumForesights)}")
                    Text(tr(french, "Contrôle arithmétique : ", "التحقق الحسابي: ") +
                        if (r.arithmeticCheckOk) "OK" else tr(french, "ERREUR", "خطأ"),
                        color = if (r.arithmeticCheckOk) LocalAppColors.current.ok else MaterialTheme.colorScheme.error)
                    Text(tr(french, "Longueur totale", "الطول الكلي") + ": ${fmt(r.totalDistance, 1)} m")
                    if (r.closureError != null) {
                        Text(tr(french, "Erreur de fermeture", "خطأ الإغلاق") + ": ${fmt(r.closureError * 1000, 1)} mm",
                            fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(tr(french, "Cheminement ouvert : altitudes non compensées (aucun contrôle).",
                            "مسار مفتوح: المناسيب بدون تعويض (بدون تحقق)."), color = MaterialTheme.colorScheme.error)
                    }
                    r.tolerance?.let { t ->
                        val ok = r.withinTolerance == true
                        Text(tr(french, "Tolérance", "التسامح") + ": ${fmt(t * 1000, 1)} mm → " +
                            if (ok) tr(french, "Acceptable", "مقبول") else tr(french, "HORS TOLÉRANCE — refaire", "خارج التسامح — أعد القياس"),
                            color = if (ok) LocalAppColors.current.ok else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                    r.rows.forEach {
                        Text("${it.shot.to}: ${fmt(it.altitudeTo)} m  |  C ${fmt(it.correction * 1000, 1)} mm")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pdfLauncher.launch("nivellement.pdf") }) { Text("PDF") }
                        OutlinedButton(onClick = { xlsxLauncher.launch("nivellement.xlsx") }) { Text("Excel") }
                    }
                }
            }
        }
    }
}
