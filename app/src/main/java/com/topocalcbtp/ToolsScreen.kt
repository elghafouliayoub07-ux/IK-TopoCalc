@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.surface.PolygonSurface
import com.topocalcbtp.engine.worksite.FieldTools
import kotlin.math.abs

/** Quick site tools: slope, scale, repeated measurements, interpolation, offsets from a line, area. */
@Composable
fun ToolsScreen(modifier: Modifier, env: AppEnv) {
    val t = env::t
    var tool by rememberSaveable { mutableIntStateOf(0) }
    ScreenColumn(modifier) {
        ScreenTitle(t("Outils de chantier", "أدوات الورش"))
        ChoiceRow(
            listOf(t("Pente", "Pente"), t("Échelle", "السلم"), t("Moyenne", "المتوسط"), t("Interpolation", "Interpolation"),
                t("Abscisses / ordonnées", "Abscisses / ordonnées"), t("Surface", "المساحة")),
            tool
        ) { tool = it }
        HorizontalDivider()
        when (tool) {
            0 -> SlopeTool(env)
            1 -> ScaleTool(env)
            2 -> MeanTool(env)
            3 -> InterpolationTool(env)
            4 -> BaselineTool(env)
            else -> AreaTool(env)
        }
    }
}

@Composable
private fun SlopeTool(env: AppEnv) {
    val t = env::t
    var z1 by rememberSaveable { mutableStateOf("") }
    var z2 by rememberSaveable { mutableStateOf("") }
    var d by rememberSaveable { mutableStateOf("") }
    var p by rememberSaveable { mutableStateOf("") }
    var d2 by rememberSaveable { mutableStateOf("") }
    SectionTitle(t("Pente entre deux points", "Pente بين جوج نقط"))
    Hint(t("Pente TP = (Z arrivée − Z départ) / distance horizontale : + rampe, − pente.", "Pente TP = (Z الوصول − Z البداية) / المسافة الأفقية: + طالعة، − نازلة."))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(t("Z départ", "Z البداية"), z1, { z1 = it }, Modifier.weight(1f), signed = true)
        Field(t("Z arrivée", "Z الوصول"), z2, { z2 = it }, Modifier.weight(1f), signed = true)
        Field(t("Dist. horiz.", "مسافة أفقية"), d, { d = it }, Modifier.weight(1f))
    }
    val a = parseNum(z1); val b = parseNum(z2); val dist = parseNum(d)
    if (a != null && b != null && dist != null && dist > 0) {
        val s = FieldTools.slope(b - a, dist)
        ResultCard {
            KeyValue("ΔZ", "${fmt(s.deltaZ)} m")
            KeyValue(t("Pente", "Pente"), "${fmt(s.percent, 3)} %   (${fmt(s.perMille, 2)} ‰)", bold = true)
            KeyValue(t("Angle", "الزاوية"), "${fmt(s.angleDegrees, 4)}°  =  ${fmt(s.angleDegrees / 0.9, 4)} gr")
            s.oneIn?.let { KeyValue(t("Rapport", "النسبة"), "1 / ${fmt(it, 2)}") }
            KeyValue(t("Distance suivant la pente", "المسافة على الميل"), "${fmt(s.slopeDistance)} m")
            Text(if (s.ratio > 0) t("Montante (rampe)", "طالعة (rampe)") else if (s.ratio < 0) t("Descendante (pente)", "نازلة (pente)") else t("Palier", "مستوية"),
                fontWeight = FontWeight.SemiBold)
        }
    }
    HorizontalDivider()
    SectionTitle(t("Dénivelée à partir d'une pente", "الفرق فالعلو من pente"))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(t("Pente (%)", "Pente (%)"), p, { p = it }, Modifier.weight(1f), signed = true)
        Field(t("Distance (m)", "المسافة (m)"), d2, { d2 = it }, Modifier.weight(1f))
    }
    val pp = parseNum(p); val dd = parseNum(d2)
    if (pp != null && dd != null) ResultCard {
        KeyValue("ΔZ", "${fmt(pp / 100 * dd)} m", bold = true)
        a?.let { KeyValue(t("Z arrivée (depuis Z départ)", "Z الوصول (من Z البداية)"), "${fmt(it + pp / 100 * dd)} m") }
    }
}

@Composable
private fun ScaleTool(env: AppEnv) {
    val t = env::t
    var plan by rememberSaveable { mutableStateOf("") }
    var ground by rememberSaveable { mutableStateOf("") }
    var scale by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    SectionTitle(t("Échelle 1/N", "السلم 1/N"))
    Hint(t("Remplissez deux valeurs : la troisième est calculée.", "عمّر جوج قيم: الثالثة كتتحسب."))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(t("Plan (cm)", "البلان (cm)"), plan, { plan = it; msg = null }, Modifier.weight(1f))
        Field(t("Terrain (m)", "الأرض (m)"), ground, { ground = it; msg = null }, Modifier.weight(1f))
        Field("N (1/N)", scale, { scale = it; msg = null }, Modifier.weight(1f))
    }
    Button(onClick = {
        val pl = parseNum(plan); val gr = parseNum(ground); val n = parseNum(scale)
        msg = null
        when {
            pl != null && gr != null && (n == null || scale.isBlank()) -> scale = fmtTrim(FieldTools.scaleFrom(pl, gr), 2)
            pl != null && n != null && ground.isBlank() -> ground = fmtTrim(FieldTools.groundFromPlan(pl, n), 3)
            gr != null && n != null && plan.isBlank() -> plan = fmtTrim(FieldTools.planFromGround(gr, n), 2)
            else -> msg = t("Laissez vide la valeur à calculer.", "خلي القيمة اللي بغيتي تحسب خاوية.")
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(t("Calculer", "احسب")) }
    ErrorText(msg)
    parseNum(scale)?.takeIf { it > 0 }?.let { n ->
        ResultCard {
            KeyValue(t("1 cm sur le plan", "1 cm فالبلان"), "${fmtTrim(n / 100, 3)} m")
            KeyValue(t("Précision graphique (0,1 mm)", "الدقة الخطية (0.1 mm)"), "${fmtTrim(n * 0.0001 * 100, 2)} cm " + t("sur le terrain", "فالأرض"))
        }
    }
}

@Composable
private fun MeanTool(env: AppEnv) {
    val t = env::t
    var text by rememberSaveable { mutableStateOf("") }
    SectionTitle(t("Moyenne de mesures répétées", "متوسط قياسات متكررة"))
    Hint(t("Une valeur par ligne (ou séparées par des espaces). Tolérance = 2,7 σ.", "قيمة فكل سطر (ولا مفرقين بفراغ). التسامح = 2.7 σ."))
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
        placeholder = { Text("128.567\n128.572\n128.563", fontFamily = FontFamily.Monospace) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = LocalAppColors.current.fieldBackground, unfocusedContainerColor = LocalAppColors.current.fieldBackground,
            focusedTextColor = LocalAppColors.current.fieldText, unfocusedTextColor = LocalAppColors.current.fieldText,
            focusedBorderColor = LocalAppColors.current.fieldFocus, unfocusedBorderColor = LocalAppColors.current.fieldBorder))
    val values = text.split(Regex("[\\s;]+")).mapNotNull { parseNum(it) }
    if (values.size >= 2) {
        val s = FieldTools.stats(values)
        ResultCard {
            KeyValue("n", "${values.size}")
            KeyValue(t("Moyenne (valeur la plus probable)", "المتوسط (القيمة الأكثر احتمالا)"), fmt(s.mean, 4), bold = true)
            KeyValue("σ " + t("(une mesure)", "(قياس واحد)"), fmt(s.sigma, 4))
            KeyValue("σ " + t("de la moyenne", "ديال المتوسط"), fmt(s.sigmaMean, 4))
            KeyValue(t("Tolérance 2,7 σ", "التسامح 2.7 σ"), fmt(s.tolerance, 4))
            KeyValue(t("Écart max", "أكبر فرق"), fmt(values.maxOf { it } - values.minOf { it }, 4))
            if (s.outliers.isEmpty()) Text(t("Aucune mesure hors tolérance.", "حتى قياس ما خارج التسامح."), color = LocalAppColors.current.ok)
            else {
                Text(t("Hors tolérance : ", "خارج التسامح: ") + s.outliers.joinToString { "n°${it + 1} (${fmt(values[it], 4)})" },
                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                val kept = values.filterIndexed { i, _ -> i !in s.outliers }
                if (kept.size >= 2) KeyValue(t("Moyenne sans ces mesures", "المتوسط بلا هاد القياسات"), fmt(kept.average(), 4), bold = true)
            }
        }
    }
}

@Composable
private fun InterpolationTool(env: AppEnv) {
    val t = env::t
    var za by rememberSaveable { mutableStateOf("") }
    var zb by rememberSaveable { mutableStateOf("") }
    var dab by rememberSaveable { mutableStateOf("") }
    var x by rememberSaveable { mutableStateOf("") }
    var z by rememberSaveable { mutableStateOf("") }
    SectionTitle(t("Interpolation entre deux points / courbes", "Interpolation بين جوج نقط / courbes"))
    Hint(t("Altitude d'un point entre deux courbes de niveau (Δh = ΔH × X1 / X), ou position d'une altitude (tracé des courbes).",
        "منسوب نقطة بين جوج courbes de niveau (Δh = ΔH × X1 / X)، ولا بلاصة منسوب معين (رسم الـcourbes)."))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field("Z A", za, { za = it }, Modifier.weight(1f), signed = true)
        Field("Z B", zb, { zb = it }, Modifier.weight(1f), signed = true)
        Field(t("Distance AB", "المسافة AB"), dab, { dab = it }, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(t("Distance depuis A", "المسافة من A"), x, { x = it }, Modifier.weight(1f))
        Field(t("ou altitude cherchée", "ولا المنسوب المطلوب"), z, { z = it }, Modifier.weight(1f), signed = true)
    }
    val a = parseNum(za); val b = parseNum(zb); val d = parseNum(dab)
    if (a != null && b != null && d != null && d > 0) ResultCard {
        parseNum(x)?.let { KeyValue(t("Altitude au point", "المنسوب فالنقطة"), "${fmt(FieldTools.interpolateZ(a, b, d, it))} m", bold = true) }
        parseNum(z)?.let { target ->
            val pos = FieldTools.distanceForZ(a, b, d, target)
            if (pos == null) Text(t("Cette altitude n'est pas entre Z A et Z B.", "هاد المنسوب ماشي بين Z A و Z B."), color = MaterialTheme.colorScheme.error)
            else KeyValue(t("Altitude ${fmt(target)} à", "المنسوب ${fmt(target)} على بعد"), "${fmt(pos)} m " + t("de A", "من A"), bold = true)
        }
        KeyValue(t("Pente AB", "Pente AB"), "${fmt((b - a) / d * 100, 3)} %")
    }
}

@Composable
private fun BaselineTool(env: AppEnv) {
    val t = env::t
    var an by rememberSaveable { mutableStateOf("A") }
    var ax by rememberSaveable { mutableStateOf("") }
    var ay by rememberSaveable { mutableStateOf("") }
    var bn by rememberSaveable { mutableStateOf("B") }
    var bx by rememberSaveable { mutableStateOf("") }
    var bYv by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var abscissa by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableStateOf("") }
    var pn by rememberSaveable { mutableStateOf("P1") }
    var px by rememberSaveable { mutableStateOf("") }
    var py by rememberSaveable { mutableStateOf("") }
    SectionTitle(t("Levé / implantation par abscisses et ordonnées", "Levé / implantation بالـabscisses و ordonnées"))
    Hint(t("Ligne d'opération A → B. Abscisse le long de A → B depuis A ; ordonnée perpendiculaire : + à droite, − à gauche.",
        "خط العمل A → B. Abscisse على طول A → B من A؛ ordonnée عمودية: + على اليمين، − على اليسار."))
    PointInput(env, "A", an, { an = it }, ax, { ax = it }, ay, { ay = it })
    PointInput(env, "B", bn, { bn = it }, bx, { bx = it }, bYv, { bYv = it })
    ChoiceRow(listOf(t("Abscisse/ordonnée → X, Y", "Abscisse/ordonnée ← X, Y"), t("X, Y → abscisse/ordonnée", "X, Y ← abscisse/ordonnée")), mode) { mode = it }
    val a1 = parseNum(ax); val a2 = parseNum(ay); val b1 = parseNum(bx); val b2 = parseNum(bYv)
    val lineOk = a1 != null && a2 != null && b1 != null && b2 != null && (a1 != b1 || a2 != b2)
    if (mode == 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(t("Nom", "الاسم"), pn, { pn = it }, Modifier.weight(0.8f), numeric = false)
            Field(t("Abscisse (m)", "Abscisse (m)"), abscissa, { abscissa = it }, Modifier.weight(1f), signed = true)
            Field(t("Ordonnée (m)", "Ordonnée (m)"), offset, { offset = it }, Modifier.weight(1f), signed = true)
        }
        val s = parseNum(abscissa); val o = parseNum(offset)
        if (lineOk && s != null && o != null) {
            val (x, y) = FieldTools.fromBaseline(a1!!, a2!!, b1!!, b2!!, s, o)
            ResultCard {
                KeyValue("X", "${fmt(x)} m", bold = true); KeyValue("Y", "${fmt(y)} m", bold = true)
                KeyValue(t("Contrôle : distance A–${pn}", "التحقق: المسافة A–${pn}"), "${fmt(kotlin.math.hypot(s, o))} m")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveToProjectButton(env, t("Enregistrer le point", "حفظ النقطة")) {
                        env.savePoints(listOf(NewPoint(pn.trim().ifBlank { "P" }, x, y, null, "", "ABSCISSES")))
                    }
                    TextButton(onClick = { pn = nextName(pn).ifBlank { pn }; abscissa = ""; offset = "" }) { Text(t("Point suivant", "النقطة التالية")) }
                }
            }
        }
    } else {
        PointInput(env, t("Point", "النقطة"), pn, { pn = it }, px, { px = it }, py, { py = it })
        val x = parseNum(px); val y = parseNum(py)
        if (lineOk && x != null && y != null) {
            val r = FieldTools.toBaseline(a1!!, a2!!, b1!!, b2!!, x, y)
            ResultCard {
                KeyValue(t("Abscisse depuis A", "Abscisse من A"), "${fmt(r.abscissa)} m", bold = true)
                KeyValue(t("Ordonnée", "Ordonnée"), "${fmt(abs(r.offset))} m " + when {
                    abs(r.offset) < 0.0005 -> t("(sur la ligne)", "(على الخط)")
                    r.offset > 0 -> t("à droite", "على اليمين")
                    else -> t("à gauche", "على اليسار")
                }, bold = true)
                KeyValue(t("Contrôle : distance A–${pn}", "التحقق: المسافة A–${pn}"), "${fmt(kotlin.math.hypot(r.abscissa, r.offset))} m")
            }
        }
    }
    if (!lineOk && (ax.isNotBlank() || bx.isNotBlank())) Hint(t("Saisir A et B (différents).", "دخل A و B (مختلفين)."))
}

@Composable
private fun AreaTool(env: AppEnv) {
    val t = env::t
    var rowsText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val rows = decodeRows(rowsText)
    SectionTitle(t("Surface d'un polygone (sommets dans l'ordre)", "مساحة مضلع (الرؤوس بالترتيب)"))
    PointPickerButton(env, t("+ Ajouter un point du projet", "+ زيد نقطة من المشروع")) { p ->
        rowsText = encodeRows(rows + listOf(listOf(p.name, fmtTrim(p.x, 4), fmtTrim(p.y, 4))))
    }
    RowsEditor(env, listOf(Col(t("Point", "النقطة"), numeric = false), Col("X (E)", signed = true), Col("Y (N)", signed = true)),
        rowsText, { rowsText = it; error = null },
        defaults = { rs -> listOf("S${rs.size + 1}", "", "") })
    if (rows.size >= 3) {
        val r = runCatching {
            PolygonSurface.calculate(rows.mapIndexed { i, it -> Point2D(it[0].ifBlank { "S${i + 1}" } + "#$i", parseNum(it[1])!!, parseNum(it[2])!!) })
        }
        r.onSuccess { m ->
            ResultCard {
                KeyValue(t("Surface", "المساحة"), "${fmt(m.areaSquareMetres, 2)} m²", bold = true)
                KeyValue(t("Soit", "يعني"), "${fmt(m.areaSquareMetres / 100, 4)} a  =  ${fmt(m.areaSquareMetres / 10000, 4)} ha")
                KeyValue(t("Périmètre", "المحيط"), "${fmt(m.perimeterMetres, 2)} m")
                Hint(t("Les côtés ne doivent pas se croiser.", "الأضلاع ما خاصهومش يتقاطعو."))
            }
        }.onFailure { ErrorText(errorMessage(env.french, it)) }
    }
}
