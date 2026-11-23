@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topocalcbtp.engine.worksite.GridVolume
import com.topocalcbtp.engine.worksite.GridVolumeResult
import java.util.Locale

/** Cubature par plans cotés: a grid of levels (or staff readings), mesh size and design level. */
@Composable
fun GridScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val t = env::t
    var dx by rememberSaveable { mutableStateOf("20") }
    var dy by rememberSaveable { mutableStateOf("20") }
    var designZ by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 levels, 1 staff readings
    var refZ by rememberSaveable { mutableStateOf("") }
    var refReading by rememberSaveable { mutableStateOf("") }
    var gridText by rememberSaveable { mutableStateOf("") }
    var swell by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<List<List<Double?>>, GridVolumeResult>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.GRID) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            dx = rec.text("dx"); dy = rec.text("dy"); designZ = rec.text("designZ"); mode = rec.text("mode").toIntOrNull() ?: 0
            refZ = rec.text("refZ"); refReading = rec.text("refReading"); gridText = rec.text("grid"); swell = rec.text("swell")
            result = null; error = null
            env.toast(t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }

    fun gridOf(values: List<List<Double?>>, digits: Int, signed: Boolean = false): String {
        val f = if (signed) "%+9.${digits}f" else "%9.${digits}f"
        return values.joinToString("\n") { row -> row.joinToString("") { v -> v?.let { String.format(Locale.US, f, it) } ?: "        -" } }
    }

    ScreenColumn(modifier) {
        ScreenTitle(t("Cubature par quadrillage (plans cotés)", "Cubature بالـquadrillage (plans cotés)"))
        Hint(t("Chaque maille est un prisme de hauteur (ha + hb + hc + hd) / 4, avec h = TN − cote projet.",
            "كل maille هي prisme علوها (ha + hb + hc + hd) / 4، مع h = الأرض − cote projet."))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(t("Maille X (m)", "Maille X (m)"), dx, { dx = it; result = null }, Modifier.weight(1f))
            Field(t("Maille Y (m)", "Maille Y (m)"), dy, { dy = it; result = null }, Modifier.weight(1f))
            Field(t("Cote projet", "Cote projet"), designZ, { designZ = it; result = null }, Modifier.weight(1f))
        }
        ChoiceRow(listOf(t("Altitudes", "المناسيب"), t("Lectures sur mire", "قراءات المير")), mode) { mode = it; result = null }
        if (mode == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(t("Altitude du repère", "منسوب المعلم"), refZ, { refZ = it; result = null }, Modifier.weight(1f))
                Field(t("Lecture sur repère", "القراءة على المعلم"), refReading, { refReading = it; result = null }, Modifier.weight(1f))
            }
            parseNum(refZ)?.let { z -> parseNum(refReading)?.let { r -> Hint(t("Plan de visée", "Plan de visée") + " = ${fmt(z + r)} m") } }
        }
        Text(t("Grille : une ligne par rangée, valeurs séparées par des espaces ; « - » = pas de point.",
            "الشبكة: سطر لكل صف، القيم مفرقة بفراغات؛ « - » = ما كايناش نقطة."))
        OutlinedTextField(
            gridText, { gridText = it; result = null }, Modifier.fillMaxWidth().heightIn(min = 150.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
            placeholder = { Text(if (mode == 0) "251.20 251.05 250.90\n251.35 251.10 250.95" else "1.480 1.605 1.575\n1.450 1.382 1.475",
                fontFamily = FontFamily.Monospace) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LocalAppColors.current.fieldBackground, unfocusedContainerColor = LocalAppColors.current.fieldBackground,
                focusedTextColor = LocalAppColors.current.fieldText, unfocusedTextColor = LocalAppColors.current.fieldText,
                focusedBorderColor = LocalAppColors.current.fieldFocus, unfocusedBorderColor = LocalAppColors.current.fieldBorder)
        )
        Field(t("Coefficient de foisonnement — optionnel", "معامل الانتفاش — اختياري"), swell, { swell = it }, Modifier.fillMaxWidth())

        Button(onClick = {
            error = null; result = null
            runCatching {
                val a = requireNotNull(parseNum(dx)) { t("Maille X invalide.", "Maille X غير صالحة.") }
                val b = requireNotNull(parseNum(dy)) { t("Maille Y invalide.", "Maille Y غير صالحة.") }
                val design = requireNotNull(parseNum(designZ)) { t("Cote projet manquante.", "Cote projet ناقصة.") }
                val raw = GridVolume.parse(gridText)
                val levels = if (mode == 0) raw else {
                    val hpv = requireNotNull(parseNum(refZ)?.let { z -> parseNum(refReading)?.let { z + it } }) {
                        t("Saisir l'altitude du repère et sa lecture.", "دخل منسوب المعلم والقراءة ديالو.")
                    }
                    raw.map { row -> row.map { r -> r?.let { hpv - it } } }
                }
                levels to GridVolume.compute(levels, a, b, design)
            }.onSuccess { result = it }.onFailure { error = errorMessage(env.french, it) }
        }, modifier = Modifier.fillMaxWidth()) { Text(t("Calculer les volumes", "احسب الأحجام")) }
        ErrorText(error)

        result?.let { (levels, r) ->
            val design = parseNum(designZ) ?: 0.0
            val k = parseNum(swell)?.takeIf { it > 0 }
            ResultCard {
                KeyValue(t("Mailles complètes", "Mailles كاملة"), "${r.cells.size}")
                KeyValue(t("Surface", "المساحة"), "${fmt(r.area, 2)} m²")
                KeyValue(t("TN min / max", "الأرض min / max"), "${fmt(r.minGround)} / ${fmt(r.maxGround)} m")
                HorizontalDivider()
                KeyValue(t("Déblai", "الحفر"), "${fmt(r.volume.cut, 2)} m³", bold = true, color = LocalAppColors.current.heading)
                KeyValue(t("Remblai", "الردم"), "${fmt(r.volume.fill, 2)} m³", bold = true, color = MaterialTheme.colorScheme.error)
                KeyValue(t("Différence (D − R)", "الفرق (حفر − ردم)"), "${fmt(r.volume.net, 2)} m³")
                k?.let { KeyValue(t("Déblai foisonné", "الحفر بعد الانتفاش") + " (× $it)", "${fmt(r.volume.cut * it, 2)} m³", bold = true) }
                KeyValue(t("Cote d'équilibre (D = R)", "Cote d'équilibre (حفر = ردم)"), "${fmt(r.balanceLevel)} m", bold = true)
                Hint(t("Mailles mixtes (déblai et remblai) : formule de transition.", "Mailles فيها حفر وردم: كتحسب بالـformule de transition."))
                if (mode == 1) {
                    HorizontalDivider()
                    SectionTitle(t("Altitudes calculées", "المناسيب المحسوبة"))
                    MonoText(gridOf(levels, 3))
                }
                HorizontalDivider()
                SectionTitle(t("Hauteurs à terrasser h (+ déblai, − remblai)", "العلو h (+ حفر، − ردم)"))
                MonoText(gridOf(levels.map { row -> row.map { z -> z?.let { it - design } } }, 2, signed = true))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareButton(env, t("Cubature par quadrillage", "Cubature par quadrillage")) {
                        buildString {
                            appendLine("IK TopoCalc — " + t("Cubature par quadrillage", "Cubature par quadrillage"))
                            appendLine(t("Maille", "Maille") + " $dx × $dy m ; " + t("cote projet", "cote projet") + " $designZ m")
                            appendLine(t("Surface", "المساحة") + " ${fmt(r.area, 2)} m² (${r.cells.size} " + t("mailles", "mailles") + ")")
                            appendLine(t("Déblai", "الحفر") + " ${fmt(r.volume.cut, 2)} m³ ; " + t("Remblai", "الردم") + " ${fmt(r.volume.fill, 2)} m³")
                            k?.let { appendLine(t("Déblai foisonné", "الحفر بعد الانتفاش") + " ${fmt(r.volume.cut * it, 2)} m³") }
                            appendLine(t("Cote d'équilibre", "Cote d'équilibre") + " ${fmt(r.balanceLevel)} m")
                            appendLine(t("Hauteurs h :", "العلو h:"))
                            append(gridOf(levels.map { row -> row.map { z -> z?.let { it - design } } }, 2, signed = true))
                        }
                    }
                    SaveToProjectButton(env) {
                        val rec = Record(CalcType.GRID)
                        rec["dx"] = dx; rec["dy"] = dy; rec["designZ"] = designZ; rec["mode"] = mode.toString()
                        rec["refZ"] = refZ; rec["refReading"] = refReading; rec["grid"] = gridText; rec["swell"] = swell
                        env.saveCalculation(CalcType.GRID, t("Quadrillage", "Quadrillage") + " $dx×$dy m",
                            "D ${fmt(r.volume.cut, 1)} m³ · R ${fmt(r.volume.fill, 1)} m³", rec)
                    }
                }
            }
        }
    }
}
