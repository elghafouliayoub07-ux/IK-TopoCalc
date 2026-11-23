@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.worksite.GradeBreak
import com.topocalcbtp.engine.worksite.LongProfile
import com.topocalcbtp.engine.worksite.LongProfileResult
import com.topocalcbtp.engine.worksite.ProfileStation
import java.util.Locale
import kotlin.math.abs

/** Profil en long: ground points along an axis, design line (one grade or grade breaks), cut / fill. */
@Composable
fun LongProfileScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val t = env::t
    var cumulative by rememberSaveable { mutableIntStateOf(0) } // 0 partial distances, 1 cumulative (PK)
    var rowsText by rememberSaveable { mutableStateOf("") }
    var designMode by rememberSaveable { mutableIntStateOf(0) } // 0 single grade, 1 grade breaks
    var startZ by rememberSaveable { mutableStateOf("") }
    var slope by rememberSaveable { mutableStateOf("") }
    var breaksText by rememberSaveable { mutableStateOf("") }
    var width by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<LongProfileResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val rows = decodeRows(rowsText)

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.LONG_PROFILE) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            cumulative = rec.text("cumulative").toIntOrNull() ?: 0
            designMode = rec.text("designMode").toIntOrNull() ?: 0
            startZ = rec.text("startZ"); slope = rec.text("slope"); width = rec.text("width")
            breaksText = rec.text("breaks")
            rowsText = encodeRows(rec.rows.map { r -> List(3) { r.getOrElse(it) { "" } } })
            result = null; error = null
            env.toast(t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }

    fun stations(): List<ProfileStation> {
        var acc = 0.0
        return rows.mapIndexed { i, r ->
            val d = requireNotNull(parseNum(r[1])) { "${r[0]}: distance" }
            val z = requireNotNull(parseNum(r[2])) { "${r[0]}: cote TN" }
            acc = if (cumulative == 1) d else if (i == 0) d else acc + d
            ProfileStation(r[0], acc, z)
        }
    }

    fun report(r: LongProfileResult, w: Double?): String = buildString {
        appendLine("IK TopoCalc — " + t("Profil en long", "Profil en long"))
        appendLine(t("Point | PK | TN | Projet | h", "النقطة | PK | TN | مشروع | h"))
        r.rows.forEach { appendLine("${it.name} | ${fmt(it.chainage, 2)} | ${fmt(it.groundZ)} | ${fmt(it.designZ)} | ${fmt(abs(it.height))} ${cutFillWord(env.french, it.height)}") }
        if (r.crossings.isNotEmpty()) appendLine(t("Points de passage : ", "نقط المرور: ") + r.crossings.joinToString { "PK ${fmt(it.first, 2)} (Z ${fmt(it.second)})" })
        appendLine(t("Surface déblai", "مساحة الحفر") + " = ${fmt(r.area.cut, 2)} m² ; " + t("remblai", "الردم") + " = ${fmt(r.area.fill, 2)} m²")
        w?.let { appendLine(t("Largeur", "العرض") + " $it m → " + t("déblai", "حفر") + " ${fmt(r.area.cut * it, 2)} m³ ; " + t("remblai", "ردم") + " ${fmt(r.area.fill * it, 2)} m³") }
    }

    ScreenColumn(modifier) {
        ScreenTitle(t("Profil en long", "Profil en long"))
        Hint(t("Points du terrain naturel le long de l'axe (altitudes issues du nivellement), puis la ligne projet.",
            "نقط الأرض الطبيعية على طول المحور (المناسيب من النيفو)، ومن بعد خط المشروع."))
        ChoiceRow(listOf(t("Distances partielles", "مسافات جزئية"), t("Distances cumulées (PK)", "مسافات تراكمية (PK)")), cumulative) { cumulative = it; result = null }
        RowsEditor(
            env,
            listOf(Col(t("Point", "النقطة"), numeric = false), Col(if (cumulative == 1) "PK (m)" else t("Dist. (m)", "المسافة (m)")), Col(t("Cote TN", "Cote TN"))),
            rowsText, { rowsText = it; result = null },
            defaults = { rs -> listOf(rs.lastOrNull()?.get(0)?.let { nextName(it) }?.ifBlank { null } ?: "P${rs.size}", if (rs.isEmpty()) "0" else "", "") }
        )
        if (cumulative == 0) Hint(t("Premier point : distance 0. Ensuite, la distance depuis le point précédent.", "أول نقطة: المسافة 0. من بعد، المسافة من النقطة اللي قبل."))

        SectionTitle(t("Ligne projet", "خط المشروع"))
        ChoiceRow(listOf(t("Pente unique", "Pente وحدة"), t("Points de changement de pente", "نقط تبدال الـpente")), designMode) { designMode = it; result = null }
        if (designMode == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(t("Cote projet au départ", "Cote projet فالبداية"), startZ, { startZ = it; result = null }, Modifier.weight(1f))
                Field(t("Pente (%)", "Pente (%)"), slope, { slope = it; result = null }, Modifier.weight(1f), signed = true)
            }
            Hint(t("+ = rampe (monte), − = pente (descend), dans le sens des distances.", "+ = طالعة، − = نازلة، فاتجاه المسافات."))
        } else {
            RowsEditor(env, listOf(Col("PK (m)"), Col(t("Cote projet", "Cote projet"))), breaksText, { breaksText = it; result = null })
            Hint(t("Au moins 2 points ; la ligne est droite entre eux et prolongée aux extrémités.", "على الأقل 2 نقط؛ الخط مستقيم بيناتهم وكيتمد فالأطراف."))
        }
        Field(t("Largeur (m) — optionnel, pour un volume (tranchée, plateforme)", "العرض (m) — اختياري، باش يحسب الحجم (tranchée، plateforme)"),
            width, { width = it; result = null }, Modifier.fillMaxWidth())

        Button(onClick = {
            error = null; result = null
            runCatching {
                require(rows.size >= 2) { t("Au moins 2 points du terrain.", "على الأقل جوج نقط ديال الأرض.") }
                val st = stations()
                val breaks = if (designMode == 0) {
                    val z0 = requireNotNull(parseNum(startZ)) { t("Cote projet au départ manquante.", "Cote projet ديال البداية ناقصة.") }
                    val p = requireNotNull(parseNum(slope)) { t("Pente manquante.", "الـpente ناقصة.") }
                    LongProfile.singleGrade(st.first().chainage, z0, p, st.last().chainage)
                } else decodeRows(breaksText).map { GradeBreak(parseNum(it[0])!!, parseNum(it[1])!!) }.sortedBy { it.chainage }
                    .also { require(it.size >= 2) { t("Au moins 2 points de la ligne projet.", "على الأقل جوج نقط فخط المشروع.") } }
                LongProfile.compute(st, breaks)
            }.onSuccess { result = it }.onFailure { error = errorMessage(env.french, it) }
        }, modifier = Modifier.fillMaxWidth()) { Text(t("Calculer le profil", "احسب الـprofil")) }
        ErrorText(error)

        result?.let { r ->
            val w = parseNum(width)?.takeIf { it > 0 }
            ProfileChart(env, r.rows.map { it.chainage }, r.rows.map { it.groundZ }, r.rows.map { it.designZ })
            ResultCard {
                MonoText(buildString {
                    appendLine(String.format(Locale.US, "%-6s %8s %8s %8s %8s", t("Pt", "Pt"), "PK", "TN", t("Proj", "Proj"), "h"))
                    r.rows.forEach {
                        appendLine(String.format(Locale.US, "%-6s %8.2f %8.3f %8.3f %+8.3f", it.name.take(6), it.chainage, it.groundZ, it.designZ, it.height))
                    }
                }.trimEnd())
                Hint(t("h > 0 : déblai (à creuser) ; h < 0 : remblai (à remplir).", "h > 0: حفر؛ h < 0: ردم."))
                HorizontalDivider()
                SectionTitle(t("Pentes par tronçon", "الـpentes حسب المقطع"))
                MonoText(r.rows.drop(1).mapIndexed { i, it ->
                    val prev = r.rows[i]
                    String.format(Locale.US, "%s→%s  TN %+.2f%%  " + t("proj", "proj") + " %+.2f%%", prev.name, it.name, it.groundSlopePercent ?: 0.0, it.designSlopePercent ?: 0.0)
                }.joinToString("\n"))
                if (r.crossings.isNotEmpty()) {
                    HorizontalDivider()
                    SectionTitle(t("Points de passage (TN = projet)", "نقط المرور (الأرض = المشروع)"))
                    r.crossings.forEach { Text("PK ${fmt(it.first, 2)} m — Z ${fmt(it.second)} m") }
                }
                HorizontalDivider()
                KeyValue(t("Longueur", "الطول"), "${fmt(r.length, 2)} m")
                KeyValue(t("Surface déblai", "مساحة الحفر"), "${fmt(r.area.cut, 2)} m²", bold = true)
                KeyValue(t("Surface remblai", "مساحة الردم"), "${fmt(r.area.fill, 2)} m²", bold = true)
                if (w != null) {
                    KeyValue(t("Volume déblai", "حجم الحفر") + " (× $w m)", "${fmt(r.area.cut * w, 2)} m³", bold = true, color = LocalAppColors.current.heading)
                    KeyValue(t("Volume remblai", "حجم الردم") + " (× $w m)", "${fmt(r.area.fill * w, 2)} m³", bold = true, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareButton(env, t("Profil en long", "Profil en long")) { report(r, w) }
                    SaveToProjectButton(env) {
                        val rec = Record(CalcType.LONG_PROFILE)
                        rec["cumulative"] = cumulative.toString(); rec["designMode"] = designMode.toString()
                        rec["startZ"] = startZ; rec["slope"] = slope; rec["width"] = width; rec["breaks"] = breaksText
                        rows.forEach { rec.rows += it }
                        env.saveCalculation(CalcType.LONG_PROFILE, t("Profil en long", "Profil en long") + " ${rows.first()[0]} → ${rows.last()[0]}",
                            "${fmt(r.length, 1)} m · D ${fmt(r.area.cut, 1)} m² · R ${fmt(r.area.fill, 1)} m²", rec)
                    }
                }
            }
        }
    }
}
