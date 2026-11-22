@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.angles.Angles
import com.topocalcbtp.engine.angles.TwoFace

/** A result line; [bold] for the main value. */
private data class ResultLine(val text: String, val bold: Boolean = false)

private class UserInputException(message: String) : Exception(message)

@Composable
fun TheodoliteScreen(modifier: Modifier, env: AppEnv, load: SavedCalculation?, onLoaded: () -> Unit) {
    val french = env.french
    val unit = env.unit
    var twoFaces by rememberSaveable { mutableStateOf(false) } // false = CG only, true = CG + CD
    var hzBack by rememberSaveable { mutableStateOf("") }
    var hzBackCd by rememberSaveable { mutableStateOf("") }
    var hzFore by rememberSaveable { mutableStateOf("") }
    var hzForeCd by rememberSaveable { mutableStateOf("") }
    var vertical by rememberSaveable { mutableStateOf("") }
    var verticalCd by rememberSaveable { mutableStateOf("") }
    var slope by rememberSaveable { mutableStateOf("") }
    var instrumentHeight by rememberSaveable { mutableStateOf("") }
    var targetHeight by rememberSaveable { mutableStateOf("") }
    var upper by rememberSaveable { mutableStateOf("") }
    var middle by rememberSaveable { mutableStateOf("") }
    var lower by rememberSaveable { mutableStateOf("") }
    var stadiaK by rememberSaveable { mutableStateOf("100") }
    var stadiaC by rememberSaveable { mutableStateOf("0") }
    var isZenith by rememberSaveable { mutableStateOf(true) }
    var curvature by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<List<ResultLine>?>(null) }
    var collimationWarning by remember { mutableStateOf(false) }
    var stadiaResult by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val u = unit.symbol
    val small = smallAngleUnitLabel(unit)
    val halfTurn = if (unit == AngleUnit.GONS) "200 gr" else "180°"
    val fullTurn = if (unit == AngleUnit.GONS) "400 gr" else "360°"
    fun reset() { result = null; stadiaResult = null; collimationWarning = false }

    OnAngleUnitChange(unit) { from, to ->
        hzBack = convertAngleText(hzBack, from, to); hzBackCd = convertAngleText(hzBackCd, from, to)
        hzFore = convertAngleText(hzFore, from, to); hzForeCd = convertAngleText(hzForeCd, from, to)
        vertical = convertAngleText(vertical, from, to); verticalCd = convertAngleText(verticalCd, from, to)
    }
    LaunchedEffect(load?.id) {
        val loaded = load ?: return@LaunchedEffect
        val rec = if (loaded.type == CalcType.THEODOLITE) RecordCodec.decode(loaded.payload) else null
        if (rec != null) {
            // Angles are stored in degrees and shown in the current unit.
            fun ang(key: String) = rec.text(key).let { t -> parseNum(t)?.let { fmtTrim(unit.fromDegrees(it), 6) } ?: t }
            twoFaces = rec.text("twoFaces") == "1"
            hzBack = ang("hzBackDeg"); hzFore = ang("hzForeDeg"); vertical = ang("verticalDeg")
            hzBackCd = ang("hzBackCdDeg"); hzForeCd = ang("hzForeCdDeg"); verticalCd = ang("verticalCdDeg")
            isZenith = rec.text("zenith") != "0"
            slope = rec.text("slope"); instrumentHeight = rec.text("hi"); targetHeight = rec.text("ht")
            upper = rec.text("upper"); middle = rec.text("middle"); lower = rec.text("lower")
            stadiaK = rec.text("k").ifBlank { "100" }; stadiaC = rec.text("c").ifBlank { "0" }
            curvature = rec.text("cr") == "1"
            reset(); error = null
            env.toast(env.t("Calcul ouvert : ", "تم فتح الحساب: ") + loaded.title)
        }
        onLoaded()
    }
    fun saveToProject() {
        fun deg(t: String) = parseNum(t)?.let { fmtTrim(unit.toDegrees(it), 8) } ?: ""
        val rec = Record(CalcType.THEODOLITE)
        rec["twoFaces"] = if (twoFaces) "1" else "0"
        rec["hzBackDeg"] = deg(hzBack); rec["hzForeDeg"] = deg(hzFore); rec["verticalDeg"] = deg(vertical)
        rec["hzBackCdDeg"] = deg(hzBackCd); rec["hzForeCdDeg"] = deg(hzForeCd); rec["verticalCdDeg"] = deg(verticalCd)
        rec["zenith"] = if (isZenith) "1" else "0"
        rec["slope"] = slope; rec["hi"] = instrumentHeight; rec["ht"] = targetHeight
        rec["upper"] = upper; rec["middle"] = middle; rec["lower"] = lower
        rec["k"] = stadiaK; rec["c"] = stadiaC; rec["cr"] = if (curvature) "1" else "0"
        val summary = (result?.map { it.text } ?: stadiaResult)?.joinToString(" · ") ?: ""
        env.saveCalculation(CalcType.THEODOLITE, tr(french, "Observation théodolite", "رصد بالتيودوليت"), summary, rec)
    }

    // Results are cleared when the angle unit changes, so a value is never shown in the wrong unit.
    LaunchedEffect(unit) { reset() }

    /** Mean horizontal reading of one sight, in degrees, plus result lines; null when not entered. */
    fun hzSight(cgText: String, cdText: String, back: Boolean): Pair<Double, List<ResultLine>>? {
        val cg = parseNum(cgText)
        if (!twoFaces) return cg?.let { unit.toDegrees(it) to emptyList() }
        val cd = parseNum(cdText)
        if (cg == null && cd == null) return null
        if (cg == null || cd == null) throw UserInputException(tr(french,
            "En double retournement, saisir CG et CD pour l'arrière et l'avant.", "فالقلب المزدوج، أدخل CG و CD للشوفة الخلفية والأمامية."))
        val f = TwoFace.horizontal(unit.toDegrees(cg), unit.toDegrees(cd))
        val which = if (back) tr(french, "visée arrière", "الشوفة الخلفية") else tr(french, "visée avant", "الشوفة الأمامية")
        if (kotlin.math.abs(f.deltaDegrees) > 0.5) throw UserInputException(tr(french,
            "CG et CD incohérents : ils doivent différer d'environ $halfTurn ($which)", "CG و CD غير متوافقين: خاص الفرق بيناتهم يكون تقريباً $halfTurn ($which)"))
        if (kotlin.math.abs(f.collimationDegrees) > 60.0 / 3600.0) collimationWarning = true
        val c = degreesToSmallAngle(f.collimationDegrees, unit)
        return f.meanDegrees to listOf(
            ResultLine((if (back) tr(french, "Hz moyen arrière", "متوسط Hz الخلفية") else tr(french, "Hz moyen avant", "متوسط Hz الأمامية")) +
                " : ${fmt(unit.normalizeFull(unit.fromDegrees(f.meanDegrees)), 4)} $u"),
            ResultLine((if (back) tr(french, "Collimation arrière c", "خطأ التسديد الخلفي c") else tr(french, "Collimation avant c", "خطأ التسديد الأمامي c")) +
                " : ${if (c >= 0) "+" else ""}${fmt(c, 1)} $small")
        )
    }

    /** Vertical reading in the current unit (zenith corrected with CD when given) + result lines. */
    fun verticalReading(): Pair<Double, List<ResultLine>>? {
        val v = parseNum(vertical) ?: return null
        if (isZenith && twoFaces && verticalCd.isNotBlank()) {
            val vcd = parseNum(verticalCd) ?: throw UserInputException(tr(french,
                "Lecture verticale, distance ou hauteurs invalides.", "القراءة العمودية أو المسافة أو الارتفاعات غير صالحة."))
            val sum = Angles.normalize180(unit.toDegrees(v) + unit.toDegrees(vcd) - 360.0)
            if (kotlin.math.abs(sum) > 0.5) throw UserInputException(tr(french,
                "Z CG + Z CD doit être proche de $fullTurn.", "مجموع Z CG و Z CD خاص يكون قريب من $fullTurn."))
            val z = TwoFace.zenith(unit.toDegrees(v), unit.toDegrees(vcd))
            val i = degreesToSmallAngle(z.indexErrorDegrees, unit)
            val corrected = unit.fromDegrees(z.correctedZenithDegrees)
            return corrected to listOf(
                ResultLine(tr(french, "Erreur d'index vertical i", "خطأ المؤشر العمودي i") + " : ${if (i >= 0) "+" else ""}${fmt(i, 1)} $small"),
                ResultLine(tr(french, "Angle zénithal corrigé", "الزاوية السمتية المصححة") + " : ${fmt(corrected, 4)} $u")
            )
        }
        return v to emptyList()
    }

    ScreenColumn(modifier) {
        ScreenTitle(tr(french, "Théodolite — Lectures angulaires", "Théodolite — قراءات الزوايا"))
        Hint(tr(french, "Unité des angles : $u (changer en haut).", "وحدة الزوايا: $u (غيّرها من الأعلى)."))

        SectionTitle(tr(french, "Lectures horizontales", "القراءات الأفقية"))
        ChoiceRow(listOf(tr(french, "Cercle gauche seul (CG)", "الدائرة اليسرى فقط (CG)"), tr(french, "CG + CD (double retournement)", "CG + CD (القلب المزدوج)")),
            if (twoFaces) 1 else 0) { twoFaces = it == 1; reset() }
        if (twoFaces) {
            Hint(tr(french, "CD = lecture après double retournement (alidade tournée de 180° et lunette basculée). La moyenne CG/CD élimine l'erreur de collimation.",
                "CD = القراءة بعد القلب المزدوج (دوران الجهاز 180° وقلب المنظار). متوسط CG و CD كيحيد خطأ التسديد (collimation)."))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(if (twoFaces) tr(french, "Hz arrière CG ($u)", "Hz الخلفية CG ($u)") else tr(french, "Hz arrière ($u)", "Hz الخلفية ($u)"),
                hzBack, { hzBack = it; reset() }, Modifier.weight(1f))
            if (twoFaces) Field(tr(french, "Hz arrière CD ($u)", "Hz الخلفية CD ($u)"), hzBackCd, { hzBackCd = it; reset() }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(if (twoFaces) tr(french, "Hz avant CG ($u)", "Hz الأمامية CG ($u)") else tr(french, "Hz avant ($u)", "Hz الأمامية ($u)"),
                hzFore, { hzFore = it; reset() }, Modifier.weight(1f))
            if (twoFaces) Field(tr(french, "Hz avant CD ($u)", "Hz الأمامية CD ($u)"), hzForeCd, { hzForeCd = it; reset() }, Modifier.weight(1f))
        }

        SectionTitle(tr(french, "Type de lecture verticale", "نوع القراءة العمودية"))
        ChoiceRow(listOf(tr(french, "Angle zénithal Z", "زاوية سمتية Z"), tr(french, "Site α (depuis l'horizontale)", "زاوية ميل α")),
            if (isZenith) 0 else 1) { isZenith = it == 0; reset() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(if (twoFaces) tr(french, "Verticale CG ($u)", "العمودية CG ($u)") else tr(french, "Lecture verticale ($u)", "القراءة العمودية ($u)"),
                vertical, { vertical = it; reset() }, Modifier.weight(1f), signed = !isZenith)
            if (twoFaces && isZenith) Field(tr(french, "Verticale CD ($u) — optionnel", "العمودية CD ($u) — اختياري"),
                verticalCd, { verticalCd = it; reset() }, Modifier.weight(1f))
        }
        if (twoFaces) {
            Hint(if (isZenith) tr(french, "Avec Z en CG et CD, l'erreur d'index vertical est corrigée : Z = (Z_CG − Z_CD + $fullTurn) / 2.",
                    "مع Z في CG و CD كيتصحح خطأ المؤشر العمودي: Z = (Z_CG − Z_CD + $fullTurn) / 2.")
                 else tr(french, "La correction verticale CG/CD est disponible avec l'angle zénithal Z.", "تصحيح CG/CD العمودي متاح مع الزاوية السمتية Z."))
        }
        Field(tr(french, "Distance inclinée (m)", "المسافة المائلة (m)"), slope, { slope = it; reset() }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(tr(french, "Hauteur instrument HI (m)", "ارتفاع الجهاز HI (m)"), instrumentHeight, { instrumentHeight = it; reset() }, Modifier.weight(1f))
            Field(tr(french, "Hauteur prisme HT (m)", "ارتفاع العاكس HT (m)"), targetHeight, { targetHeight = it; reset() }, Modifier.weight(1f))
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = curvature, onCheckedChange = { curvature = it; reset() })
            Spacer(Modifier.width(8.dp))
            Text(tr(french, "Correction sphéricité + réfraction (si l'appareil ne l'applique pas)",
                "تصحيح انحناء الأرض والانكسار (إذا لم يطبقه الجهاز)"), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = calc@{
            error = null; reset()
            val d = parseNum(slope)
            val hi = if (instrumentHeight.isBlank()) 0.0 else parseNum(instrumentHeight)
            val ht = if (targetHeight.isBlank()) 0.0 else parseNum(targetHeight)
            val lines = mutableListOf<ResultLine>()
            try {
                val back = hzSight(hzBack, hzBackCd, true)
                val fore = hzSight(hzFore, hzForeCd, false)
                if (back != null && fore != null) {
                    lines += back.second; lines += fore.second
                    val angle = unit.normalizeFull(unit.fromDegrees(Angles.normalize360(fore.first - back.first)))
                    lines += ResultLine(tr(french, "Angle horizontal", "الزاوية الأفقية") + " : ${fmt(angle, 4)} $u", bold = true)
                }
            } catch (e: Exception) {
                error = if (e is UserInputException) e.message else errorMessage(french, e)
                collimationWarning = false
                return@calc
            }
            if (vertical.isNotBlank() && d != null && hi != null && ht != null) {
                try {
                    val (reading, vLines) = verticalReading() ?: throw UserInputException(tr(french,
                        "Lecture verticale, distance ou hauteurs invalides.", "القراءة العمودية أو المسافة أو الارتفاعات غير صالحة."))
                    lines += vLines
                    val red = TheodoliteMath.reduceSlopeDistance(d, reading,
                        if (isZenith) VerticalReadingType.ZENITH_ANGLE else VerticalReadingType.VERTICAL_ANGLE, unit, hi, ht, curvature)
                    lines += ResultLine(tr(french, "Distance horizontale", "المسافة الأفقية") + " : ${fmt(red.horizontalDistanceMetres)} m")
                    lines += ResultLine(tr(french, "Dénivelée axe-cible V", "الفرق العمودي V") + " : ${fmt(red.verticalComponentMetres)} m")
                    lines += ResultLine(tr(french, "ΔZ sol-sol (HI + V − HT)", "فرق المنسوب أرض-أرض") + " : ${fmt(red.elevationDifferenceMetres)} m")
                    if (curvature) lines += ResultLine(tr(french, "dont sphéricité+réfraction", "منها تصحيح الانحناء") + " : ${fmt(red.curvatureRefractionMetres * 1000, 1)} mm")
                } catch (e: Exception) {
                    error = if (e is UserInputException) e.message else errorMessage(french, e)
                }
            } else if (vertical.isNotBlank() || slope.isNotBlank()) {
                error = tr(french, "Lecture verticale, distance ou hauteurs invalides.", "القراءة العمودية أو المسافة أو الارتفاعات غير صالحة.")
            }
            if (lines.isEmpty() && error == null) error = tr(french, "Saisir des lectures.", "أدخل القراءات.")
            if (lines.isNotEmpty()) result = lines
        }, modifier = Modifier.fillMaxWidth()) { Text(tr(french, "Calculer", "احسب")) }
        ErrorText(error)
        result?.let { lines ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lines.forEach { Text(it.text, fontWeight = if (it.bold) FontWeight.Bold else null) }
                    if (collimationWarning) {
                        Text(tr(french, "Collimation élevée (> 60″) : vérifier les lectures ou faire régler l'appareil.",
                            "خطأ التسديد كبير (> 60″): راجع القراءات أو اضبط الجهاز."), color = MaterialTheme.colorScheme.error)
                    }
                    Hint(tr(french, "Vérifier le type d'angle vertical et la convention du constructeur.",
                        "تحقق من نوع الزاوية العمودية واتجاه تدريج الجهاز."))
                }
            }
        }

        HorizontalDivider()
        SectionTitle(tr(french, "Stadia (tachéométrie optique) — utilise la lecture verticale ci-dessus",
            "Stadia — تستعمل القراءة العمودية أعلاه"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(tr(french, "Fil sup.", "العليا"), upper, { upper = it }, Modifier.weight(1f))
            Field(tr(french, "Fil moy.", "الوسطى"), middle, { middle = it }, Modifier.weight(1f))
            Field(tr(french, "Fil inf.", "السفلى"), lower, { lower = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("K", stadiaK, { stadiaK = it }, Modifier.weight(1f))
            Field("C (m)", stadiaC, { stadiaC = it }, Modifier.weight(1f), signed = true)
        }
        Button(onClick = {
            error = null; stadiaResult = null
            val up = parseNum(upper); val mi = parseNum(middle); val lo = parseNum(lower)
            val k = parseNum(stadiaK); val c = parseNum(stadiaC)
            val hi = if (instrumentHeight.isBlank()) 0.0 else parseNum(instrumentHeight)
            if (up == null || mi == null || lo == null || parseNum(vertical) == null || k == null || c == null || hi == null) {
                error = tr(french, "Saisir les trois fils, K, C et la lecture verticale.", "أدخل الشعيرات الثلاث و K و C والقراءة العمودية.")
            } else {
                try {
                    val reading = verticalReading()!!.first
                    val s = TheodoliteMath.inclinedStadia(up, mi, lo, reading,
                        if (isZenith) VerticalReadingType.ZENITH_ANGLE else VerticalReadingType.VERTICAL_ANGLE, unit, hi, k, c)
                    stadiaResult = listOf(
                        tr(french, "Distance horizontale", "المسافة الأفقية") + " : ${fmt(s.horizontalDistanceMetres)} m",
                        tr(french, "ΔZ sol-sol (HI + V − fil moyen)", "فرق المنسوب أرض-أرض") + " : ${fmt(s.elevationDifferenceMetres)} m",
                        if (s.wires.middleReadingWithinTolerance) tr(french, "Contrôle fil moyen : OK", "التحقق من الشعيرة الوسطى: سليم")
                        else tr(french, "Attention : écart fil moyen ", "تنبيه: فرق الشعيرة الوسطى ") + fmt(s.wires.middleReadingResidualMetres) + " m"
                    )
                } catch (e: Exception) {
                    error = if (e is UserInputException) e.message else errorMessage(french, e)
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr(french, "Calculer stadia", "حساب Stadia")) }
        stadiaResult?.let { lines -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { lines.forEach { Text(it) } } } }
        Hint(tr(french, "Dh = K·l·cos²α + C·cosα. Saisir les constantes réelles de l'instrument.",
            "Dh = K·l·cos²α + C·cosα. أدخل ثوابت الجهاز الحقيقية."))
        HorizontalDivider()
        SaveToProjectButton(env) { saveToProject() }
    }
}
