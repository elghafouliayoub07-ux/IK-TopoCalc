@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.worksite.CrossSections
import com.topocalcbtp.engine.worksite.CutFill
import com.topocalcbtp.engine.worksite.SectionAreas
import com.topocalcbtp.engine.worksite.SectionsVolumeResult
import java.util.Locale

/**
 * Cubature par profils: (1) areas of one cross-section from its points, (2) volumes between
 * sections by the average of end areas, V = (S1 + S2) / 2 × d.
 */
@Composable
fun SectionsScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val t = env::t
    // part 1: one section
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 heights h, 1 ground levels + design level
    var pointsText by rememberSaveable { mutableStateOf("") }
    var designZ by rememberSaveable { mutableStateOf("") }
    var sectionName by rememberSaveable { mutableStateOf("P1") }
    var sectionPk by rememberSaveable { mutableStateOf("0") }
    var areas by remember { mutableStateOf<Triple<List<Double>, List<Double>, CutFill>?>(null) }
    // part 2: list of sections
    var sectionsText by rememberSaveable { mutableStateOf("") }
    var swell by rememberSaveable { mutableStateOf("") }
    var volumes by remember { mutableStateOf<SectionsVolumeResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var error2 by remember { mutableStateOf<String?>(null) }
    val points = decodeRows(pointsText)
    val sections = decodeRows(sectionsText)

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.SECTIONS) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            mode = rec.text("mode").toIntOrNull() ?: 0
            pointsText = rec.text("points"); designZ = rec.text("designZ"); swell = rec.text("swell")
            sectionName = rec.text("name").ifBlank { "P1" }; sectionPk = rec.text("pk").ifBlank { "0" }
            sectionsText = encodeRows(rec.rows.map { r -> List(4) { r.getOrElse(it) { "" } } })
            areas = null; volumes = null; error = null; error2 = null
            env.toast(t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }

    ScreenColumn(modifier) {
        ScreenTitle(t("Cubature par profils", "Cubature par profils"))
        SectionTitle(t("1. Surface d'un profil en travers", "1. مساحة profil en travers"))
        ChoiceRow(listOf(t("Hauteurs à terrasser h", "العلو h"), t("Cotes TN + cote projet", "Cotes TN + cote projet")), mode) { mode = it; areas = null }
        Hint(if (mode == 0) t("h = TN − projet : + déblai, − remblai. Distances partielles entre points (premier point : 0).",
                "h = الأرض − المشروع: + حفر، − ردم. مسافات جزئية بين النقط (أول نقطة: 0).")
             else t("Cote projet constante (plateforme, fond de fouille). Distances partielles (premier point : 0).",
                "Cote projet ثابتة (plateforme، fond de fouille). مسافات جزئية (أول نقطة: 0)."))
        if (mode == 1) Field(t("Cote projet (m)", "Cote projet (m)"), designZ, { designZ = it; areas = null }, Modifier.fillMaxWidth())
        RowsEditor(
            env,
            listOf(Col(t("Dist. (m)", "المسافة (m)")), Col(if (mode == 0) "h (m)" else t("Cote TN", "Cote TN"), signed = mode == 0)),
            pointsText, { pointsText = it; areas = null },
            defaults = { rs -> listOf(if (rs.isEmpty()) "0" else "", "") }
        )
        Button(onClick = {
            error = null; areas = null
            runCatching {
                require(points.size >= 2) { t("Au moins 2 points.", "على الأقل جوج نقط.") }
                val x = points.map { parseNum(it[0])!! }.runningReduce { a, b -> a + b }
                val dz = if (mode == 1) requireNotNull(parseNum(designZ)) { t("Cote projet manquante.", "Cote projet ناقصة.") } else 0.0
                val v = points.map { parseNum(it[1])!! }
                val h = if (mode == 1) v.map { it - dz } else v
                Triple(x, v, CrossSections.areas(x, h))
            }.onSuccess { areas = it }.onFailure { error = errorMessage(env.french, it) }
        }, modifier = Modifier.fillMaxWidth()) { Text(t("Calculer la surface", "احسب المساحة")) }
        ErrorText(error)
        areas?.let { (x, v, a) ->
            if (mode == 1) ProfileChart(env, x, v, List(x.size) { parseNum(designZ) ?: 0.0 }, heightDp = 170)
            else ProfileChart(env, x, v, List(x.size) { 0.0 }, heightDp = 170)
            ResultCard {
                KeyValue(t("Surface déblai", "مساحة الحفر"), "${fmt(a.cut, 2)} m²", bold = true, color = LocalAppColors.current.heading)
                KeyValue(t("Surface remblai", "مساحة الردم"), "${fmt(a.fill, 2)} m²", bold = true, color = MaterialTheme.colorScheme.error)
                KeyValue(t("Largeur", "العرض"), "${fmt(x.last() - x.first(), 2)} m")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(t("Nom du profil", "اسم البروفيل"), sectionName, { sectionName = it }, Modifier.weight(1f), numeric = false)
                    Field("PK (m)", sectionPk, { sectionPk = it }, Modifier.weight(1f))
                }
                OutlinedButton(onClick = {
                    if (parseNum(sectionPk) == null) { env.toast(t("PK invalide.", "PK غير صالح.")); return@OutlinedButton }
                    sectionsText = encodeRows(sections + listOf(listOf(sectionName.trim().ifBlank { "P${sections.size + 1}" }, sectionPk.trim(), fmt(a.cut, 3), fmt(a.fill, 3))))
                    volumes = null
                    sectionName = nextName(sectionName).ifBlank { "P${sections.size + 2}" }
                    env.toast(t("Profil ajouté à la liste", "تزاد البروفيل للائحة"))
                }) { Text(t("Ajouter à la liste des profils ↓", "زيدو للائحة البروفيلات ↓")) }
            }
        }

        HorizontalDivider()
        SectionTitle(t("2. Volume entre profils (moyenne des aires)", "2. الحجم بين البروفيلات (moyenne des aires)"))
        Hint(t("V = (S1 + S2) / 2 × d. PK = distance cumulée depuis l'origine du projet.", "V = (S1 + S2) / 2 × d. PK = المسافة التراكمية من بداية المشروع."))
        RowsEditor(
            env,
            listOf(Col(t("Profil", "البروفيل"), numeric = false), Col("PK (m)"), Col(t("S déblai", "S حفر")), Col(t("S remblai", "S ردم"), required = false)),
            sectionsText, { sectionsText = it; volumes = null },
            defaults = { rs -> listOf("P${rs.size + 1}", "", "", "0") }
        )
        Field(t("Coefficient de foisonnement — optionnel (ex. 1,25)", "معامل الانتفاش — اختياري (مثلا 1.25)"), swell, { swell = it }, Modifier.fillMaxWidth())
        Button(onClick = {
            error2 = null; volumes = null
            runCatching {
                require(sections.size >= 2) { t("Au moins 2 profils.", "على الأقل جوج بروفيلات.") }
                CrossSections.averageEndArea(sections.map { SectionAreas(it[0], parseNum(it[1])!!, parseNum(it[2])!!, parseNum(it[3]) ?: 0.0) }.sortedBy { it.chainage })
            }.onSuccess { volumes = it }.onFailure { error2 = errorMessage(env.french, it) }
        }, modifier = Modifier.fillMaxWidth()) { Text(t("Calculer les volumes", "احسب الأحجام")) }
        ErrorText(error2)
        volumes?.let { v ->
            val k = parseNum(swell)?.takeIf { it > 0 }
            ResultCard {
                MonoText(buildString {
                    appendLine(String.format(Locale.US, "%-11s %7s %10s %10s", t("Entre", "بين"), "d", t("Déblai", "حفر"), t("Remblai", "ردم")))
                    v.intervals.forEach {
                        appendLine(String.format(Locale.US, "%-11s %7.2f %10.2f %10.2f", "${it.from}-${it.to}".take(11), it.distance, it.volume.cut, it.volume.fill))
                    }
                }.trimEnd())
                HorizontalDivider()
                KeyValue(t("Total déblai", "مجموع الحفر"), "${fmt(v.total.cut, 2)} m³", bold = true, color = LocalAppColors.current.heading)
                KeyValue(t("Total remblai", "مجموع الردم"), "${fmt(v.total.fill, 2)} m³", bold = true, color = MaterialTheme.colorScheme.error)
                KeyValue(t("Différence (D − R)", "الفرق (حفر − ردم)"), "${fmt(v.total.net, 2)} m³")
                k?.let { KeyValue(t("Déblai foisonné", "الحفر بعد الانتفاش") + " (× $it)", "${fmt(v.total.cut * it, 2)} m³", bold = true) }
                Hint(t("Pensez à ajouter le volume des talus si besoin.", "ما تنساش تزيد حجم الـtalus إلا خاص."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareButton(env, t("Cubature par profils", "Cubature par profils")) {
                        buildString {
                            appendLine("IK TopoCalc — " + t("Cubature par profils", "Cubature par profils"))
                            sections.forEach { appendLine("${it[0]} PK ${it[1]} : Sd ${it[2]} m², Sr ${it[3].ifBlank { "0" }} m²") }
                            v.intervals.forEach { appendLine("${it.from}-${it.to} (d ${fmt(it.distance, 2)}) : D ${fmt(it.volume.cut, 2)} m³, R ${fmt(it.volume.fill, 2)} m³") }
                            appendLine(t("Total déblai", "مجموع الحفر") + " ${fmt(v.total.cut, 2)} m³ ; " + t("remblai", "الردم") + " ${fmt(v.total.fill, 2)} m³")
                            k?.let { appendLine(t("Déblai foisonné", "الحفر بعد الانتفاش") + " ${fmt(v.total.cut * it, 2)} m³") }
                        }
                    }
                    SaveToProjectButton(env) {
                        val rec = Record(CalcType.SECTIONS)
                        rec["mode"] = mode.toString(); rec["points"] = pointsText; rec["designZ"] = designZ; rec["swell"] = swell
                        rec["name"] = sectionName; rec["pk"] = sectionPk
                        sections.forEach { rec.rows += it }
                        env.saveCalculation(CalcType.SECTIONS, t("Cubature", "Cubature") + " ${sections.first()[0]} → ${sections.last()[0]}",
                            "D ${fmt(v.total.cut, 1)} m³ · R ${fmt(v.total.fill, 1)} m³", rec)
                    }
                }
            }
        }
    }
}
