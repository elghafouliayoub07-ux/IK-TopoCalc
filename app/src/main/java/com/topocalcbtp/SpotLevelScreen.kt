@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.worksite.SpotLevelInput
import com.topocalcbtp.engine.worksite.SpotLevelRow
import com.topocalcbtp.engine.worksite.SpotLevels

/**
 * One level station: altitudes of points from their readings (nivellement par rayonnement) and
 * readings to show on the staff for design levels (implantation altimétrique), with cut / fill.
 */
@Composable
fun SpotLevelScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val t = env::t
    var refName by rememberSaveable { mutableStateOf("R") }
    var refZ by rememberSaveable { mutableStateOf("") }
    var backsight by rememberSaveable { mutableStateOf("") }
    var rowsText by rememberSaveable { mutableStateOf("") }
    var gradeStart by rememberSaveable { mutableStateOf("") }
    var gradePercent by rememberSaveable { mutableStateOf("") }
    var staffLength by rememberSaveable { mutableStateOf("4") }
    var result by remember { mutableStateOf<Pair<Double, List<SpotLevelRow>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val rows = decodeRows(rowsText)

    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.SPOT_LEVELS) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            refName = rec.text("refName"); refZ = rec.text("refZ"); backsight = rec.text("bs")
            gradeStart = rec.text("gradeStart"); gradePercent = rec.text("gradePercent"); staffLength = rec.text("staff").ifBlank { "4" }
            rowsText = encodeRows(rec.rows.map { r -> List(4) { r.getOrElse(it) { "" } } })
            result = null; error = null
            env.toast(t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }

    fun report(hpv: Double, r: List<SpotLevelRow>): String = buildString {
        appendLine("IK TopoCalc — " + t("Nivellement par rayonnement / implantation altimétrique", "تسوية بالإشعاع / implantation altimétrique"))
        appendLine("${refName.ifBlank { "R" }} = $refZ m ; LA = $backsight m ; " + t("plan de visée", "plan de visée") + " = ${fmt(hpv)} m")
        r.forEach { p ->
            append(p.name)
            p.reading?.let { append(" | L ${fmt(it)}") }
            p.groundZ?.let { append(" | TN ${fmt(it)}") }
            p.designZ?.let { append(" | " + t("projet", "مشروع") + " ${fmt(it)} | " + t("lecture à afficher", "القراءة المطلوبة") + " ${fmt(hpv - it)}") }
            p.height?.let { append(" | ${fmt(kotlin.math.abs(it))} ${cutFillWord(env.french, it)}") }
            appendLine()
        }
    }

    ScreenColumn(modifier) {
        ScreenTitle(t("Rayonnement & implantation altimétrique", "Rayonnement و implantation altimétrique"))
        Hint(t("Une seule station de niveau : la lecture sur un repère connu donne le plan de visée. Ensuite, chaque lecture donne une altitude, et chaque cote projet donne la lecture à obtenir sur la mire.",
            "محطة نيفو وحدة: القراءة على نقطة معروفة كتعطي plan de visée. من بعد كل قراءة كتعطي منسوب، وكل cote projet كتعطي القراءة اللي خاصها تبان فالمير."))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(t("Repère", "المعلم"), refName, { refName = it }, Modifier.weight(0.8f), numeric = false)
            Field(t("Altitude (m)", "المنسوب (m)"), refZ, { refZ = it; result = null }, Modifier.weight(1f))
            Field("LA (m)", backsight, { backsight = it; result = null }, Modifier.weight(1f))
        }
        parseNum(refZ)?.let { z -> parseNum(backsight)?.let { b -> Hint(t("Plan de visée", "Plan de visée") + " = ${fmt(z + b)} m") } }

        SectionTitle(t("Points", "النقط"))
        Hint(t("Lecture : pour calculer l'altitude du terrain. Cote projet : pour l'implantation (lecture à afficher, déblai/remblai). Distance : seulement pour remplir les cotes avec une pente.",
            "القراءة: لحساب منسوب الأرض. Cote projet: للـimplantation (القراءة المطلوبة، حفر/ردم). المسافة: غير باش تعمر الكوطات بالـpente."))
        RowsEditor(
            env,
            listOf(Col(t("Point", "النقطة"), numeric = false), Col(t("Lecture", "القراءة"), required = false),
                Col(t("Cote projet", "Cote projet"), required = false), Col(t("Dist. cumulée", "مسافة تراكمية"), required = false)),
            rowsText, { rowsText = it; result = null },
            defaults = { rs -> listOf(rs.lastOrNull()?.get(0)?.let { nextName(it) }?.ifBlank { null } ?: "P${rs.size + 1}", "", "", "") },
            display = { _, r ->
                r[0] + (r[1].takeIf { it.isNotBlank() }?.let { "  L $it" } ?: "") + (r[2].takeIf { it.isNotBlank() }?.let { "  proj $it" } ?: "") +
                    (r[3].takeIf { it.isNotBlank() }?.let { "  d $it" } ?: "")
            }
        )

        SectionTitle(t("Cotes projet par pente (optionnel)", "Cotes projet بالـpente (اختياري)"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(t("Cote à d = 0", "الكوطة عند d = 0"), gradeStart, { gradeStart = it }, Modifier.weight(1f))
            Field(t("Pente (%)", "Pente (%)"), gradePercent, { gradePercent = it }, Modifier.weight(1f), signed = true)
        }
        OutlinedButton(onClick = {
            error = null
            val z0 = parseNum(gradeStart); val p = parseNum(gradePercent)
            if (z0 == null || p == null) { error = t("Saisir la cote de départ et la pente.", "دخل الكوطة ديال البداية والـpente."); return@OutlinedButton }
            var filled = 0
            rowsText = encodeRows(rows.map { r ->
                val d = parseNum(r.getOrElse(3) { "" })
                if (d == null) r else { filled++; listOf(r[0], r[1], fmtPlain(SpotLevels.gradeLevels(z0, p, listOf(d))[0]), r[3]) }
            })
            result = null
            env.toast(if (filled == 0) t("Aucun point n'a de distance.", "حتى نقطة ما فيها مسافة.") else t("$filled cote(s) projet remplie(s)", "تعمرات $filled كوطة"))
        }) { Text(t("Remplir les cotes projet", "عمّر الكوطات")) }
        Hint(t("Pente + : montante dans le sens des distances ; − : descendante.", "Pente +: طالعة فاتجاه المسافات؛ −: نازلة."))

        Field(t("Longueur de la mire (m)", "طول المير (m)"), staffLength, { staffLength = it }, Modifier.fillMaxWidth())

        Button(onClick = {
            error = null; result = null
            val z = parseNum(refZ); val b = parseNum(backsight)
            when {
                z == null || b == null -> error = t("Saisir l'altitude du repère et la lecture LA.", "دخل منسوب المعلم والقراءة LA.")
                rows.isEmpty() -> error = t("Ajouter au moins un point.", "زيد نقطة وحدة على الأقل.")
                else -> runCatching {
                    val hpv = SpotLevels.lineOfSight(z, b)
                    hpv to SpotLevels.compute(hpv, rows.map { r -> SpotLevelInput(r[0], parseNum(r[1]), parseNum(r[2])) })
                }.onSuccess { result = it }.onFailure { error = errorMessage(env.french, it) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(t("Calculer", "احسب")) }
        ErrorText(error)

        result?.let { (hpv, r) ->
            val staff = parseNum(staffLength) ?: 4.0
            ResultCard {
                KeyValue(t("Plan de visée", "Plan de visée"), "${fmt(hpv)} m", bold = true)
                HorizontalDivider()
                r.forEach { p ->
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    p.groundZ?.let { Text("   " + t("Altitude TN", "منسوب الأرض") + " = ${fmt(it)} m") }
                    p.readingForDesign?.let { lv ->
                        Text("   " + t("Lecture à afficher", "القراءة المطلوبة") + " = ${fmt(lv)} m  (" + t("projet", "مشروع") + " ${fmt(p.designZ!!)})",
                            fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.heading)
                        if (lv < 0) Text("   " + t("Projet au-dessus du plan de visée : impossible avec cette station.", "المشروع فوق plan de visée: ما يمكنش من هاد المحطة."),
                            color = MaterialTheme.colorScheme.error)
                        else if (lv > staff) Text("   " + t("Lecture plus longue que la mire.", "القراءة أطول من المير."), color = MaterialTheme.colorScheme.error)
                    }
                    p.height?.let { h ->
                        Text("   ${fmt(kotlin.math.abs(h))} m " + cutFillWord(env.french, h),
                            color = if (h > 0) LocalAppColors.current.heading else MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareButton(env, t("Implantation altimétrique", "Implantation altimétrique")) { report(hpv, r) }
                    SaveToProjectButton(env) {
                        val rec = Record(CalcType.SPOT_LEVELS)
                        rec["refName"] = refName; rec["refZ"] = refZ; rec["bs"] = backsight
                        rec["gradeStart"] = gradeStart; rec["gradePercent"] = gradePercent; rec["staff"] = staffLength
                        rows.forEach { rec.rows += it }
                        env.saveCalculation(CalcType.SPOT_LEVELS, t("Rayonnement alti", "Rayonnement alti") + " ${refName.ifBlank { "R" }}",
                            "${r.size} " + t("points", "نقط") + " · HPV ${fmt(hpv)} m", rec)
                    }
                }
            }
        }
    }
}
