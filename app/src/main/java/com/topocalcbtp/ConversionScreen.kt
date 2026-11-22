@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.angles.AngleText
import com.topocalcbtp.engine.projection.GeographicPoint
import com.topocalcbtp.engine.projection.GridPoint
import com.topocalcbtp.engine.projection.Helmert7
import com.topocalcbtp.engine.projection.MoroccoCoordinates
import com.topocalcbtp.engine.projection.MoroccoLambertZone
import com.topocalcbtp.engine.projection.RotationConvention

private data class ConversionOutput(
    val lines: List<String>,
    val latitude: Double,
    val longitude: Double,
    val grid: GridPoint?,
    val zone: MoroccoLambertZone
)

@Composable
fun ConversionScreen(modifier: Modifier, env: AppEnv) {
    val context = LocalContext.current
    var direction by rememberSaveable { mutableIntStateOf(0) } // 0 Lambert -> WGS84, 1 WGS84 -> Lambert
    var zoneIndex by rememberSaveable { mutableIntStateOf(0) }
    var autoZone by rememberSaveable { mutableStateOf(true) }
    var datumMode by rememberSaveable { mutableIntStateOf(0) } // 0 EPSG:1166, 1 custom
    var tx by rememberSaveable { mutableStateOf("31") }
    var ty by rememberSaveable { mutableStateOf("146") }
    var tz by rememberSaveable { mutableStateOf("47") }
    var rx by rememberSaveable { mutableStateOf("0") }
    var ry by rememberSaveable { mutableStateOf("0") }
    var rz by rememberSaveable { mutableStateOf("0") }
    var ds by rememberSaveable { mutableStateOf("0") }
    var convention by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("P1") }
    var gx by rememberSaveable { mutableStateOf("") }
    var gy by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lon by rememberSaveable { mutableStateOf("") }
    var output by remember { mutableStateOf<ConversionOutput?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var h by rememberSaveable { mutableStateOf("") }
    var n by rememberSaveable { mutableStateOf("") }
    var orthometric by remember { mutableStateOf<Double?>(null) }
    val zones = MoroccoLambertZone.entries

    fun transformation(): Helmert7? {
        if (datumMode == 0) return Helmert7.MERCHICH_TO_WGS84_EPSG_1166
        val v = listOf(tx, ty, tz, rx, ry, rz, ds).map { parseNum(it) }
        if (v.any { it == null }) { error = env.t("Paramètres de transformation invalides.", "معاملات التحويل غير صالحة."); return null }
        return Helmert7(env.t("Paramètres personnalisés", "معاملات مخصّصة"), v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!, v[5]!!, v[6]!!,
            if (convention == 0) RotationConvention.POSITION_VECTOR else RotationConvention.COORDINATE_FRAME)
    }

    fun openMap(latitude: Double, longitude: Double, label: String) {
        val uri = Uri.parse("geo:${fmt(latitude, 7)},${fmt(longitude, 7)}?q=${fmt(latitude, 7)},${fmt(longitude, 7)}(${Uri.encode(label)})")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { env.toast(env.t("Aucune application de carte trouvée.", "لا يوجد تطبيق خرائط.")) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val transformationUsed = transformation() ?: return@rememberLauncherForActivityResult
        val zone = zones[zoneIndex]
        runCatching {
            val csv = buildString {
                append("Nom;Latitude WGS84;Longitude WGS84;Z;Code\n")
                env.points.forEach { p ->
                    val g = MoroccoCoordinates.lambertToWgs84(GridPoint(p.x, p.y), zone, transformationUsed)
                    append("${p.name.replace(';', ',')};${fmt(g.latitudeDegrees, 8)};${fmt(g.longitudeDegrees, 8)};")
                    append(p.z?.let { fmt(it) } ?: "").append(';').append(p.code.replace(';', ',')).append('\n')
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(TextFiles.encodeForExcel(csv)) }
                ?: throw java.io.IOException("Cannot open destination")
        }.onSuccess { env.toast(env.t("Fichier enregistré", "تم حفظ الملف")) }
            .onFailure { error = errorMessage(env.french, it) }
    }

    ScreenColumn(modifier) {
        ScreenTitle(env.t("Conversion Lambert Maroc ↔ GPS", "تحويل Lambert المغرب ↔ GPS"))
        ChoiceRow(listOf("Lambert → WGS84", "WGS84 → Lambert"), direction) { direction = it; output = null; error = null }

        SectionTitle(env.t("Zone Lambert (datum Merchich)", "منطقة Lambert (مرجع Merchich)"))
        if (direction == 1) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = autoZone, onCheckedChange = { autoZone = it; output = null })
                Spacer(Modifier.width(8.dp))
                Text(env.t("Choisir la zone selon la latitude", "اختيار المنطقة حسب خط العرض"))
            }
        }
        if (direction == 0 || !autoZone) {
            ChoiceRow(zones.map { it.label.removePrefix("Lambert ") }, zoneIndex) { zoneIndex = it; output = null }
        }

        SectionTitle(env.t("Transformation Merchich → WGS84", "التحويل Merchich ← WGS84"))
        ChoiceRow(listOf(env.t("EPSG:1166 (±7 m)", "EPSG:1166 (±7 m)"), env.t("Paramètres locaux", "معاملات محلية")), datumMode) {
            datumMode = it; output = null
        }
        if (datumMode == 0) {
            Hint(env.t("Paramètres officiels publics : 3 translations (31, 146, 47 m), précision annoncée ±7 m. Suffisant pour la cartographie et la navigation, PAS pour l'implantation ni le foncier.",
                "المعاملات الرسمية العامة: 3 إزاحات (31، 146، 47 م)، دقة ±7 م. كافية للخرائط والملاحة، وليست للتوقيع ولا للتحفيظ."))
        } else {
            Hint(env.t("Saisir les 7 paramètres locaux (ex. fournis par l'ANCFCC ou calculés sur des points communs).",
                "أدخل المعاملات السبعة المحلية (مثلاً من ANCFCC أو محسوبة على نقط مشتركة)."))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Tx (m)", tx, { tx = it; output = null }, Modifier.weight(1f), signed = true)
                Field("Ty (m)", ty, { ty = it; output = null }, Modifier.weight(1f), signed = true)
                Field("Tz (m)", tz, { tz = it; output = null }, Modifier.weight(1f), signed = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Rx (″)", rx, { rx = it; output = null }, Modifier.weight(1f), signed = true)
                Field("Ry (″)", ry, { ry = it; output = null }, Modifier.weight(1f), signed = true)
                Field("Rz (″)", rz, { rz = it; output = null }, Modifier.weight(1f), signed = true)
            }
            Field(env.t("Facteur d'échelle (ppm)", "معامل السلم (ppm)"), ds, { ds = it; output = null }, Modifier.fillMaxWidth(), signed = true)
            ChoiceRow(listOf("Position Vector", "Coordinate Frame"), convention) { convention = it; output = null }
            Hint(env.t("Vérifier la convention de signe des rotations indiquée avec les paramètres.",
                "تحقق من اتفاقية إشارة الدورانات المرفقة مع المعاملات."))
        }

        HorizontalDivider()
        if (direction == 0) {
            PointInput(env, env.t("Point Lambert", "نقطة Lambert"), name, { name = it; output = null }, gx, { gx = it; output = null }, gy, { gy = it; output = null })
        } else {
            Field(env.t("Nom du point", "اسم النقطة"), name, { name = it; output = null }, Modifier.fillMaxWidth(), numeric = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(env.t("Latitude", "خط العرض"), lat, { lat = it; output = null }, Modifier.weight(1f), numeric = false)
                Field(env.t("Longitude", "خط الطول"), lon, { lon = it; output = null }, Modifier.weight(1f), numeric = false)
            }
            Hint(env.t("Degrés décimaux (33.5731 / -7.5898) ou DMS (33 34 23.2 N / 7 35 23.3 W). Ouest = négatif ou W/O.",
                "درجات عشرية (33.5731 / -7.5898) أو DMS (33 34 23.2 N / 7 35 23.3 W). الغرب = سالب أو W/O."))
        }

        Button(onClick = convert@{
            error = null; output = null
            val tr7 = transformation() ?: return@convert
            if (direction == 0) {
                val x = parseNum(gx); val y = parseNum(gy)
                if (x == null || y == null) { error = env.t("X et Y sont obligatoires.", "X و Y إجباريان."); return@convert }
                val zone = zones[zoneIndex]
                runCatching { MoroccoCoordinates.lambertToWgs84(GridPoint(x, y), zone, tr7) }
                    .onSuccess { g ->
                        output = ConversionOutput(listOf(
                            "${zone.label} (EPSG:${zone.epsgCode}) → WGS84",
                            env.t("Latitude", "خط العرض") + " = ${fmt(g.latitudeDegrees, 8)}°  (${AngleText.formatDms(g.latitudeDegrees, 'N', 'S')})",
                            env.t("Longitude", "خط الطول") + " = ${fmt(g.longitudeDegrees, 8)}°  (${AngleText.formatDms(g.longitudeDegrees, 'E', 'W')})"
                        ), g.latitudeDegrees, g.longitudeDegrees, null, zone)
                    }.onFailure { error = errorMessage(env.french, it) }
            } else {
                val la = AngleText.parseDegrees(lat); val lo = AngleText.parseDegrees(lon)
                if (la == null || lo == null) { error = env.t("Latitude ou longitude invalide.", "خط العرض أو الطول غير صالح."); return@convert }
                if (la !in 19.0..37.0 || lo !in -18.0..0.0) {
                    error = env.t("Point hors du Maroc : vérifier le signe de la longitude (Ouest = négatif).",
                        "النقطة خارج المغرب: تحقق من إشارة خط الطول (الغرب = سالب).")
                    return@convert
                }
                val zone = if (autoZone) MoroccoLambertZone.forLatitude(la) else zones[zoneIndex]
                runCatching { MoroccoCoordinates.wgs84ToLambert(GeographicPoint(la, lo), zone, tr7) }
                    .onSuccess { g ->
                        output = ConversionOutput(listOf(
                            "WGS84 → ${zone.label} (EPSG:${zone.epsgCode})",
                            "X = ${fmt(g.x)} m", "Y = ${fmt(g.y)} m"
                        ), la, lo, g, zone)
                    }.onFailure { error = errorMessage(env.french, it) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Convertir", "تحويل")) }
        ErrorText(error)

        output?.let { o ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    o.lines.forEachIndexed { i, line -> Text(line, fontWeight = if (i == 0) FontWeight.SemiBold else null) }
                    val expected = MoroccoLambertZone.forLatitude(o.latitude)
                    if (expected != o.zone) {
                        Text(env.t("Attention : à cette latitude on utilise normalement ${expected.label}.",
                            "تنبيه: عند خط العرض هذا تُستعمل عادةً ${expected.label}."), color = MaterialTheme.colorScheme.error)
                    }
                    Text(if (datumMode == 0) env.t("Précision de la transformation : ±7 m environ.", "دقة التحويل: حوالي ±7 م.")
                         else env.t("Précision : celle des paramètres saisis.", "الدقة: حسب المعاملات المدخلة."),
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openMap(o.latitude, o.longitude, name.ifBlank { "P" }) }) {
                            Text(env.t("Voir sur la carte", "عرض على الخريطة"))
                        }
                        o.grid?.let { g ->
                            SaveToProjectButton(env, env.t("Enregistrer le point", "حفظ النقطة")) {
                                env.savePoints(listOf(NewPoint(name.ifBlank { "P" }.trim(), g.x, g.y, null, o.zone.label, "CONVERSION")))
                            }
                        }
                    }
                }
            }
        }

        if (env.project != null && env.points.isNotEmpty()) {
            HorizontalDivider()
            SectionTitle(env.t("Tous les points du projet", "كل نقط المشروع"))
            Hint(env.t("Exporte les ${env.points.size} points (supposés en ${zones[zoneIndex].label}) en latitude/longitude WGS84, par ex. pour Google Earth ou un SIG.",
                "تصدير ${env.points.size} نقطة (على اعتبار أنها في ${zones[zoneIndex].label}) إلى خطوط عرض/طول WGS84، مثلاً لـ Google Earth أو SIG."))
            OutlinedButton(onClick = { exportLauncher.launch("points_wgs84.csv") }) { Text(env.t("Exporter en WGS84 (CSV)", "تصدير WGS84 (CSV)")) }
        }

        HorizontalDivider()
        SectionTitle(env.t("Altitude GNSS : H = h − N", "ارتفاع GNSS: H = h − N"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(env.t("h ellipsoïdale (m)", "h الإهليلجي (m)"), h, { h = it; orthometric = null }, Modifier.weight(1f), signed = true)
            Field(env.t("N géoïde (m)", "N الجيويد (m)"), n, { n = it; orthometric = null }, Modifier.weight(1f), signed = true)
        }
        OutlinedButton(onClick = {
            val hv = parseNum(h); val nv = parseNum(n)
            if (hv != null && nv != null) orthometric = SurveyMath.orthometricAltitude(hv, nv)
            else error = env.t("Valeurs invalides.", "قيم غير صالحة.")
        }, modifier = Modifier.fillMaxWidth()) { Text("H = h − N") }
        orthometric?.let { Text(env.t("Altitude H", "الارتفاع H") + " = ${fmt(it)} m", style = MaterialTheme.typography.titleMedium) }
        Hint(env.t("Le GPS du téléphone n'est pas un instrument topographique de précision. Utiliser un GNSS RTK et un géoïde adapté.",
            "GPS الهاتف ليس جهازاً طبوغرافياً دقيقاً. استعمل GNSS RTK ومرجع جيويد مناسباً."))
    }
}
