@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// ---------- numbers and text ----------

/** Accepts both "1.850" and "1,850" (French / Arabic keyboards); ignores invisible direction marks. */
fun parseNum(text: String): Double? =
    text.replace(Regex("[\u200E\u200F\u2066-\u2069\u202A-\u202E]"), "").trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Always formats with '.' and Latin digits, whatever the phone locale. For files, URIs and fields. */
fun fmtPlain(value: Double, decimals: Int = 3): String = "%.${decimals}f".format(Locale.US, value)

/**
 * Display precision chosen by the user (Projet → Affichage): 0 = standard (mm, 0.1 mgon...),
 * 1 = one more decimal everywhere a result is shown. Calculations always use full double precision.
 */
object DisplayPrecision {
    var extra by mutableIntStateOf(0)
}

/**
 * Number for display. The invisible left-to-right mark keeps the minus sign on the left of the
 * number and the unit after it inside Arabic (right-to-left) text: "-4.0 mm", not "mm 4.0-".
 */
fun fmt(value: Double, decimals: Int = 3): String =
    "\u200E" + fmtPlain(value, if (decimals >= 1) decimals + DisplayPrecision.extra else decimals)

/** Up to [maxDecimals] decimals, trailing zeros removed (for values written back into input fields). */
fun fmtTrim(value: Double, maxDecimals: Int = 6): String =
    BigDecimal(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun tr(french: Boolean, fr: String, ar: String): String = if (french) fr else ar

fun errorMessage(french: Boolean, t: Throwable): String =
    tr(french, "Erreur : ", "خطأ: ") + (t.message ?: t.javaClass.simpleName)

/** Re-expresses an angle typed in [from] into [to]; text that is not a number is left unchanged. */
fun convertAngleText(text: String, from: AngleUnit, to: AngleUnit): String {
    if (from == to) return text
    val v = parseNum(text) ?: return text
    return fmtTrim(to.fromDegrees(from.toDegrees(v)), 6)
}

/** Small angles (misclosures, tolerances): arc-seconds for degrees, milligons for grads. */
fun smallAngleUnitLabel(unit: AngleUnit): String = if (unit == AngleUnit.DEGREES) "″" else "mgon"
fun degreesToSmallAngle(degrees: Double, unit: AngleUnit): Double =
    if (unit == AngleUnit.DEGREES) degrees * 3600.0 else unit.fromDegrees(degrees) * 1000.0

/** Natural order for point names: P2 before P10. */
val naturalNameOrder: Comparator<String> = Comparator { a, b ->
    val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
    val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]; val y = rb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val bx = x.toBigInteger(); val by = y.toBigInteger()
            if (bx != by) bx.compareTo(by) else x.length.compareTo(y.length)
        } else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ra.size.compareTo(rb.size)
}

// ---------- app environment passed to every screen ----------

data class NewPoint(val name: String, val x: Double, val y: Double, val z: Double?, val code: String, val source: String)

class AppEnv(
    val french: Boolean,
    val unit: AngleUnit,
    val project: SurveyProject?,
    /** Points of the active project, natural order by name. */
    val points: List<ProjectPoint>,
    val savePoints: (List<NewPoint>) -> Unit,
    val saveCalculation: (type: String, title: String, summary: String, record: Record) -> Unit,
    val toast: (String) -> Unit
) {
    fun t(fr: String, ar: String): String = if (french) fr else ar
}

// ---------- composables ----------

/**
 * Text field. [numeric] picks the decimal keyboard; [signed] adds a ± button because many
 * decimal keyboards have no minus key. Point names use the normal keyboard.
 */
@Composable
fun Field(
    label: String, value: String, onValue: (String) -> Unit,
    modifier: Modifier = Modifier, numeric: Boolean = true, signed: Boolean = false
) {
    val signButton: (@Composable () -> Unit)? = if (signed) {
        {
            TextButton(
                onClick = {
                    val v = value.trim()
                    onValue(if (v.startsWith("-")) v.removePrefix("-") else "-$v")
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Brand.Navy) // the field is white in both themes
            ) { Text("±", fontSize = 18.sp) }
        }
    } else null
    val c = LocalAppColors.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // A number field shows a red border as soon as its content is not a number ("-" or "." alone are fine while typing).
    val invalid = numeric && value.isNotBlank() && parseNum(value) == null && value.trim() !in setOf("-", ".", ",", "-.", "-,")
    // Label above the box (as in the HTML version): readable in both themes, never overlaps the white field.
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        OutlinedTextField(
            value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            // Numbers stay left-to-right in Arabic ("-0.5", not "0.5-"), still aligned to the right there.
            textStyle = if (numeric) LocalTextStyle.current.copy(fontSize = 16.sp, textDirection = TextDirection.Ltr,
                textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.End else TextAlign.Start)
                else LocalTextStyle.current.copy(fontSize = 16.sp),
            shape = RoundedCornerShape(10.dp),
            // "Next" on the keyboard jumps to the following field; on the last one it closes the keyboard.
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (!focus.moveFocus(FocusDirection.Next)) keyboard?.hide() }),
            isError = invalid,
            trailingIcon = signButton,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.fieldBackground, unfocusedContainerColor = c.fieldBackground,
                focusedTextColor = c.fieldText, unfocusedTextColor = c.fieldText, cursorColor = Brand.Navy,
                focusedBorderColor = c.fieldFocus, unfocusedBorderColor = c.fieldBorder
            )
        )
    }
}

@Composable
fun ErrorText(message: String?) {
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun ScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        content()
        Spacer(Modifier.height(30.dp)) // room for the raised active tab of the bottom bar
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.heading)
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.muted)
}

/** A horizontally scrollable row of exclusive choices; the selected one is scrolled into view. */
@Composable
fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { if (selected in labels.indices) state.animateScrollToItem(maxOf(0, selected - 1)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(labels) { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

/** Converts the angle fields of a screen when the user switches between degrees and grads. */
@Composable
fun OnAngleUnitChange(unit: AngleUnit, convert: (from: AngleUnit, to: AngleUnit) -> Unit) {
    var lastUnitName by rememberSaveable { mutableStateOf(unit.name) }
    LaunchedEffect(unit) {
        val last = AngleUnit.valueOf(lastUnitName)
        if (last != unit) {
            convert(last, unit)
            lastUnitName = unit.name
        }
    }
}

/** Button that opens a searchable list of the active project's points. */
@Composable
fun PointPickerButton(env: AppEnv, label: String? = null, onPick: (ProjectPoint) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    TextButton(onClick = {
        if (env.project == null) env.toast(env.t("Choisissez d'abord un projet (onglet Projet).", "اختر مشروعاً أولاً (خانة المشاريع)."))
        else if (env.points.isEmpty()) env.toast(env.t("Ce projet n'a pas encore de points.", "هذا المشروع ليس فيه نقط بعد."))
        else { filter = ""; open = true }
    }) { Text(label ?: env.t("Choisir un point", "اختر نقطة")) }
    if (open) {
        val shown = env.points.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(env.t("Points du projet", "نقط المشروع")) },
            text = {
                Column {
                    Field(env.t("Rechercher", "بحث"), filter, { filter = it }, Modifier.fillMaxWidth(), numeric = false)
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = {
                                    Text("X ${fmt(p.x)}  Y ${fmt(p.y)}" + (p.z?.let { "  Z ${fmt(it)}" } ?: ""))
                                },
                                modifier = Modifier.clickable { onPick(p); open = false }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(env.t("Fermer", "إغلاق")) } }
        )
    }
}

/** Point name + X + Y fields with a picker filling all three. */
@Composable
fun PointInput(
    env: AppEnv, title: String,
    name: String, onName: (String) -> Unit,
    x: String, onX: (String) -> Unit,
    y: String, onY: (String) -> Unit
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        PointPickerButton(env) { p -> onName(p.name); onX(fmtTrim(p.x, 4)); onY(fmtTrim(p.y, 4)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(env.t("Nom", "الاسم"), name, onName, Modifier.weight(0.8f), numeric = false)
        Field("X (E)", x, onX, Modifier.weight(1f), signed = true)
        Field("Y (N)", y, onY, Modifier.weight(1f), signed = true)
    }
}

/** Save-to-project button, disabled-looking message when no project is active. */
@Composable
fun SaveToProjectButton(env: AppEnv, label: String? = null, onSave: () -> Unit) {
    OutlinedButton(onClick = {
        if (env.project == null) env.toast(env.t("Choisissez d'abord un projet (onglet Projet).", "اختر مشروعاً أولاً (خانة المشاريع)."))
        else onSave()
    }) { Text(label ?: env.t("Enregistrer dans le projet", "حفظ في المشروع")) }
}
