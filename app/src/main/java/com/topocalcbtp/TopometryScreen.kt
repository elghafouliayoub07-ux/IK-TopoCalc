@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.coordinates.Coordinates
import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.coordinates.PolarObservation

/** Rayonnement (polar -> X/Y) and inverse (X/Y -> distance + gisement). */
@Composable
fun TopometryScreen(modifier: Modifier, env: AppEnv) {
    val unit = env.unit
    val u = unit.symbol
    var stationName by rememberSaveable { mutableStateOf("ST") }
    var x by rememberSaveable { mutableStateOf("") }
    var y by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var gisement by rememberSaveable { mutableStateOf("") }
    var pointId by rememberSaveable { mutableStateOf("P1") }
    var targetName by rememberSaveable { mutableStateOf("B") }
    var targetX by rememberSaveable { mutableStateOf("") }
    var targetY by rememberSaveable { mutableStateOf("") }
    var computed by remember { mutableStateOf<Point2D?>(null) }
    var inverseResult by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    OnAngleUnitChange(unit) { from, to -> gisement = convertAngleText(gisement, from, to) }
    LaunchedEffect(unit) { inverseResult = null }

    ScreenColumn(modifier) {
        ScreenTitle(env.t("Rayonnement et calcul inverse", "Rayonnement والحساب العكسي"))
        PointInput(env, env.t("Station", "المحطة"), stationName, { stationName = it }, x, { x = it }, y, { y = it })

        HorizontalDivider()
        SectionTitle(env.t("Rayonnement — distance + gisement → X/Y", "Rayonnement — المسافة والـ Gisement ← X/Y"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(env.t("Distance horiz. (m)", "المسافة الأفقية (m)"), distance, { distance = it; computed = null }, Modifier.weight(1f))
            Field("Gisement ($u)", gisement, { gisement = it; computed = null }, Modifier.weight(1f))
        }
        Field(env.t("Nom du point", "اسم النقطة"), pointId, { pointId = it; computed = null }, Modifier.fillMaxWidth(), numeric = false)
        Button(onClick = {
            error = null; computed = null
            val sx = parseNum(x); val sy = parseNum(y); val d = parseNum(distance); val g = parseNum(gisement)
            if (sx == null || sy == null || d == null || g == null) {
                error = env.t("Valeurs invalides.", "قيم غير صالحة.")
            } else {
                runCatching {
                    Coordinates.forward(Point2D(stationName.ifBlank { "ST" }, sx, sy), PolarObservation(d, unit.toDegrees(g)), pointId.trim())
                }.onSuccess { computed = it }.onFailure { error = errorMessage(env.french, it) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Calculer les coordonnées", "حساب الإحداثيات")) }
        computed?.let { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.id}\nX = ${fmt(p.x)} m\nY = ${fmt(p.y)} m")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveToProjectButton(env, env.t("Enregistrer le point", "حفظ النقطة")) {
                            env.savePoints(listOf(NewPoint(p.id, p.x, p.y, null, "", "RAYONNEMENT")))
                        }
                        TextButton(onClick = {
                            // Next point: keep station, increment a trailing number in the name.
                            val m = Regex("^(.*?)(\\d+)$").find(p.id)
                            pointId = if (m != null) m.groupValues[1] + (m.groupValues[2].toLong() + 1) else p.id + "1"
                            distance = ""; gisement = ""; computed = null
                        }) { Text(env.t("Point suivant", "النقطة التالية")) }
                    }
                }
            }
        }

        HorizontalDivider()
        SectionTitle(env.t("Calcul inverse — station → cible", "الحساب العكسي — المحطة ← الهدف"))
        PointInput(env, env.t("Cible", "الهدف"), targetName, { targetName = it }, targetX, { targetX = it }, targetY, { targetY = it })
        Button(onClick = {
            error = null
            val ax = parseNum(x); val ay = parseNum(y); val tx = parseNum(targetX); val ty = parseNum(targetY)
            if (ax == null || ay == null || tx == null || ty == null) {
                inverseResult = null; error = env.t("Valeurs invalides.", "قيم غير صالحة.")
            } else {
                val inverse = Coordinates.inverse(Point2D("A", ax, ay), Point2D("B", tx, ty))
                inverseResult = if (inverse.distanceMetres == 0.0) env.t("Les deux points sont identiques.", "النقطتان متطابقتان.")
                else "D = ${fmt(inverse.distanceMetres)} m\nGisement = ${fmt(unit.normalizeFull(unit.fromDegrees(inverse.gisementDegrees)), 4)} $u"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(env.t("Calcul inverse", "حساب المسافة والـ Gisement")) }
        inverseResult?.let { Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(12.dp)) } }
        ErrorText(error)
        Hint(env.t("Convention : X = Est, Y = Nord ; gisement compté depuis le Nord dans le sens horaire. Coordonnées projetées (ex. Lambert) uniquement.",
            "الاتفاقية: X شرق، Y شمال؛ الـ Gisement من الشمال مع عقارب الساعة. إحداثيات مسقطة فقط (مثلاً Lambert)."))
    }
}
