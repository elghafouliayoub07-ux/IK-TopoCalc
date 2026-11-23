@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// ---------- table rows kept as one string (survives rotation and process death) ----------

private const val CELL = '\u001F'
private const val LINE = '\u001E'

fun encodeRows(rows: List<List<String>>): String = rows.joinToString(LINE.toString()) { it.joinToString(CELL.toString()) }
fun decodeRows(text: String): List<List<String>> = if (text.isEmpty()) emptyList() else text.split(LINE).map { it.split(CELL) }

/** Column of a [RowsEditor]. */
data class Col(
    val label: String,
    val numeric: Boolean = true,
    val signed: Boolean = false,
    val required: Boolean = true,
    val weight: Float = 1f
)

/** Splits a pasted line: ';' or tab when present, otherwise spaces (decimal commas stay inside numbers). */
fun splitPastedLine(line: String): List<String> =
    if (line.contains(';') || line.contains('\t')) line.split(';', '\t').map { it.trim() }
    else line.trim().split(Regex("\\s+"))

/** Next name: P7 → P8, "A" → "" (the user types it). */
fun nextName(name: String): String {
    val m = Regex("^(.*?)(\\d+)$").find(name.trim()) ?: return ""
    return m.groupValues[1] + (m.groupValues[2].toLong() + 1)
}

/**
 * A small table editor: one field per column, "Add" (or "Update" when a row was tapped),
 * per-row delete, and "Paste" for a table copied from Excel / WhatsApp / notes.
 * [defaults] gives the fields' content after a row is added.
 */
@Composable
fun RowsEditor(
    env: AppEnv,
    cols: List<Col>,
    rowsText: String,
    onRowsText: (String) -> Unit,
    defaults: (List<List<String>>) -> List<String> = { List(cols.size) { "" } },
    display: (Int, List<String>) -> String = { _, r -> r.joinToString("  ") { it.ifBlank { "—" } } }
) {
    val rows = decodeRows(rowsText)
    var inputText by rememberSaveable { mutableStateOf(encodeRows(listOf(defaults(rows)))) }
    val inputs = decodeRows(inputText).firstOrNull()?.let { if (it.size == cols.size) it else null } ?: List(cols.size) { "" }
    var editing by rememberSaveable { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    fun validate(values: List<String>, line: Int?): String? {
        cols.forEachIndexed { i, c ->
            val v = values.getOrElse(i) { "" }.trim()
            val where = (line?.let { env.t("Ligne $it : ", "السطر $it: ") } ?: "") + c.label
            if (v.isEmpty()) { if (c.required) return "$where — " + env.t("obligatoire", "إجباري") }
            else if (c.numeric && parseNum(v) == null) return "$where — " + env.t("nombre invalide", "رقم غير صالح")
        }
        return null
    }

    cols.chunked(3).forEachIndexed { chunkIndex, chunk ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chunk.forEachIndexed { j, c ->
                val i = chunkIndex * 3 + j
                Field(c.label, inputs[i], { v -> inputText = encodeRows(listOf(inputs.toMutableList().also { it[i] = v })) },
                    Modifier.weight(c.weight), numeric = c.numeric, signed = c.signed)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            error = validate(inputs, null)
            if (error == null) {
                val clean = inputs.map { it.trim() }
                val newRows = if (editing in rows.indices) rows.toMutableList().also { it[editing] = clean } else rows + listOf(clean)
                onRowsText(encodeRows(newRows))
                editing = -1
                inputText = encodeRows(listOf(defaults(newRows)))
            }
        }, modifier = Modifier.weight(1f)) {
            Text(if (editing in rows.indices) env.t("Mettre à jour la ligne ${editing + 1}", "تحديث السطر ${editing + 1}") else env.t("Ajouter", "إضافة"))
        }
        if (editing in rows.indices) TextButton(onClick = { editing = -1; inputText = encodeRows(listOf(defaults(rows))) }) { Text(env.t("Annuler", "إلغاء")) }
        else OutlinedButton(onClick = { pasteText = ""; pasteOpen = true }) { Text(env.t("Coller", "لصق")) }
    }
    ErrorText(error)
    if (rows.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(cols.joinToString("  ·  ") { it.label }, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.muted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                rows.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { editing = i; inputText = encodeRows(listOf(r + List((cols.size - r.size).coerceAtLeast(0)) { "" })) }
                            .then(if (i == editing) Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f), RoundedCornerShape(6.dp)) else Modifier)) {
                        Text("${i + 1}. " + display(i, r), Modifier.weight(1f).padding(4.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        TextButton(onClick = {
                            onRowsText(encodeRows(rows.filterIndexed { j, _ -> j != i }))
                            if (editing == i) { editing = -1; inputText = encodeRows(listOf(defaults(rows))) } else if (editing > i) editing--
                        }) { Text("✕") }
                    }
                }
                Row {
                    Hint(env.t("Touchez une ligne pour la modifier.", "اضغط على سطر لتعديله."))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onRowsText(""); editing = -1; inputText = encodeRows(listOf(defaults(emptyList()))) }) {
                        Text(env.t("Tout effacer", "مسح الكل"))
                    }
                }
            }
        }
    }
    if (pasteOpen) {
        var pasteError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text(env.t("Coller un tableau", "لصق جدول")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Hint(env.t("Une ligne par point, colonnes : ", "سطر لكل نقطة، الأعمدة: ") + cols.joinToString(" ; ") { it.label } +
                        env.t(". Séparateur : espace, tabulation ou « ; ».", ". الفاصل: فراغ، Tab أو « ; »."))
                    OutlinedTextField(pasteText, { pasteText = it; pasteError = null }, Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                    ErrorText(pasteError)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lines = pasteText.lines().filter { it.isNotBlank() }
                    val parsed = mutableListOf<List<String>>()
                    var problem: String? = null
                    var autoIndex = rows.size
                    for ((k, line) in lines.withIndex()) {
                        var t = splitPastedLine(line)
                        // A name column may be missing: generate P1, P2...
                        if (!cols[0].numeric && t.size == cols.size - 1 && parseNum(t[0]) != null) t = listOf("P${++autoIndex}") + t
                        val values = List(cols.size) { t.getOrElse(it) { "" } }
                        val err = validate(values, k + 1)
                        if (err != null) {
                            if (k == 0 && parsed.isEmpty()) continue // header line
                            problem = err; break
                        }
                        parsed += values.map { it.trim() }
                    }
                    when {
                        problem != null -> pasteError = problem
                        parsed.isEmpty() -> pasteError = env.t("Aucune ligne valide.", "لا يوجد سطر صالح.")
                        else -> {
                            val newRows = rows + parsed
                            onRowsText(encodeRows(newRows))
                            inputText = encodeRows(listOf(defaults(newRows)))
                            env.toast(env.t("${parsed.size} ligne(s) ajoutée(s)", "تمت إضافة ${parsed.size} سطر"))
                            pasteOpen = false
                        }
                    }
                }) { Text(env.t("Ajouter", "إضافة")) }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text(env.t("Fermer", "إغلاق")) } }
        )
    }
}

// ---------- results ----------

@Composable
fun ResultCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/** Monospace text block (tables). */
@Composable
fun MonoText(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 17.sp)
}

/** Value with sign and "déblai / remblai" word. */
fun cutFillWord(french: Boolean, h: Double): String = when {
    abs(h) < 0.0005 -> tr(french, "au niveau", "في المستوى")
    h > 0 -> tr(french, "déblai", "حفر")
    else -> tr(french, "remblai", "ردم")
}

@Composable
fun KeyValue(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row {
        Text("$label : ", color = LocalAppColors.current.muted)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

/** Sends the result as text (WhatsApp, e-mail, notes...). */
@Composable
fun ShareButton(env: AppEnv, title: String, text: () -> String) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text())
        }
        runCatching { context.startActivity(Intent.createChooser(intent, title)) }.onFailure { env.toast(errorMessage(env.french, it)) }
    }) { Text(env.t("Partager", "مشاركة")) }
}

// ---------- profile drawing ----------

private val CutColor = Color(0xFF2E86DE)   // déblai: blue (course convention)
private val FillColor = Color(0xFFE53935)  // remblai: red
private val DesignColor = Color(0xFFE53935)

/**
 * Ground (TN) and design (projet) lines over [x]; areas between them filled blue (cut) or red (fill).
 * [design] null draws only the ground. Vertical scale is exaggerated to fill the box.
 */
@Composable
fun ProfileChart(env: AppEnv, x: List<Double>, ground: List<Double>, design: List<Double>?, heightDp: Int = 210) {
    if (x.size < 2) return
    val c = LocalAppColors.current
    val groundColor = MaterialTheme.colorScheme.onBackground
    val labelColor = c.muted.toArgb()
    val zs = ground + (design ?: emptyList())
    val zMin = zs.min(); val zMax = zs.max()
    val zPad = ((zMax - zMin) * 0.12).coerceAtLeast(0.05)
    val z0 = zMin - zPad; val z1 = zMax + zPad
    val x0 = x.first(); val x1 = x.last()
    Column {
        Canvas(Modifier.fillMaxWidth().height(heightDp.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))) {
            val padL = 52.dp.toPx(); val padR = 10.dp.toPx(); val padT = 10.dp.toPx(); val padB = 22.dp.toPx()
            val w = size.width - padL - padR; val h = size.height - padT - padB
            fun px(v: Double) = padL + ((v - x0) / (x1 - x0)).toFloat() * w
            fun py(v: Double) = padT + ((z1 - v) / (z1 - z0)).toFloat() * h
            val paint = android.graphics.Paint().apply { color = labelColor; textSize = 10.sp.toPx(); isAntiAlias = true }
            // frame + level labels
            drawRect(color = c.muted.copy(alpha = 0.4f), topLeft = Offset(padL, padT), size = androidx.compose.ui.geometry.Size(w, h), style = Stroke(1f))
            listOf(zMin, (zMin + zMax) / 2, zMax).forEach { v ->
                drawLine(c.muted.copy(alpha = 0.2f), Offset(padL, py(v)), Offset(padL + w, py(v)))
                drawContext.canvas.nativeCanvas.drawText(fmt(v, 2), 2.dp.toPx(), py(v) + 4.dp.toPx(), paint)
            }
            drawContext.canvas.nativeCanvas.drawText(fmt(x0, 1), padL, size.height - 6.dp.toPx(), paint)
            val endLabel = fmt(x1, 1) + " m"
            drawContext.canvas.nativeCanvas.drawText(endLabel, padL + w - paint.measureText(endLabel), size.height - 6.dp.toPx(), paint)
            // cut / fill areas
            if (design != null) {
                for (i in 0 until x.lastIndex) {
                    val ha = ground[i] - design[i]; val hb = ground[i + 1] - design[i + 1]
                    fun quad(xa: Double, ga: Double, da: Double, xb: Double, gb: Double, db: Double, col: Color) {
                        val p = Path().apply {
                            moveTo(px(xa), py(ga)); lineTo(px(xb), py(gb)); lineTo(px(xb), py(db)); lineTo(px(xa), py(da)); close()
                        }
                        drawPath(p, col.copy(alpha = 0.35f))
                    }
                    if (ha * hb >= 0) quad(x[i], ground[i], design[i], x[i + 1], ground[i + 1], design[i + 1], if (ha + hb >= 0) CutColor else FillColor)
                    else {
                        val f = abs(ha) / (abs(ha) + abs(hb))
                        val xm = x[i] + (x[i + 1] - x[i]) * f
                        val zm = design[i] + (design[i + 1] - design[i]) * f
                        quad(x[i], ground[i], design[i], xm, zm, zm, if (ha > 0) CutColor else FillColor)
                        quad(xm, zm, zm, x[i + 1], ground[i + 1], design[i + 1], if (hb > 0) CutColor else FillColor)
                    }
                }
                for (i in 0 until x.lastIndex) drawLine(DesignColor, Offset(px(x[i]), py(design[i])), Offset(px(x[i + 1]), py(design[i + 1])), strokeWidth = 2.5f)
            }
            for (i in 0 until x.lastIndex) drawLine(groundColor, Offset(px(x[i]), py(ground[i])), Offset(px(x[i + 1]), py(ground[i + 1])), strokeWidth = 2.5f)
            x.indices.forEach { i -> drawCircle(groundColor, 3f, Offset(px(x[i]), py(ground[i]))) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            Legend(groundColor, env.t("TN", "الأرض"))
            if (design != null) {
                Legend(DesignColor, env.t("Projet", "المشروع"))
                Legend(CutColor, env.t("Déblai", "حفر"))
                Legend(FillColor, env.t("Remblai", "ردم"))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
