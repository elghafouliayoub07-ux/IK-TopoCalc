@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topocalcbtp.engine.io.CsvColumnOrder
import com.topocalcbtp.engine.io.CsvImportResult
import com.topocalcbtp.engine.io.CsvPoint
import com.topocalcbtp.engine.io.PointCsv
import java.util.Locale

private const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

@Composable
fun ProjectsScreen(
    modifier: Modifier,
    env: AppEnv,
    projects: List<SurveyProject>,
    calculations: List<SavedCalculation>,
    onActivate: (SurveyProject) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (SurveyProject) -> Unit,
    onOpenCalculation: (SavedCalculation) -> Unit,
    onDeleteCalculation: (SavedCalculation) -> Unit,
    onDeletePoint: (ProjectPoint) -> Unit,
    onDeleteAllPoints: () -> Unit,
    lastBackup: Long?,
    onBackupExport: (android.net.Uri) -> Unit,
    onBackupRestore: (ParsedBackup) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableIntStateOf(0) } // 0 points, 1 calculations, 2 backup
    var pendingRestore by remember { mutableStateOf<ParsedBackup?>(null) }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onBackupExport(uri)
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("Cannot open file")
            require(bytes.size <= 50 * 1024 * 1024) { "file too large" }
            Backup.parse(TextFiles.decode(bytes))
        }.onSuccess { pendingRestore = it }
            .onFailure { env.toast(env.t("Fichier de sauvegarde invalide.", "ملف النسخة غير صالح.") + " (" + (it.message ?: "") + ")") }
    }
    val backupSection: @Composable () -> Unit = {
        BackupAndAbout(env, lastBackup,
            onExport = {
                val d = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())
                backupExportLauncher.launch("IK_TopoCalc_sauvegarde_$d.json")
            },
            onImport = { backupImportLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
    }
    var order by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<SurveyProject?>(null) }
    var confirmDeleteAllPoints by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<Pair<CsvImportResult, Int>?>(null) }
    val active = env.project
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || active == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw java.io.IOException("Cannot open file")
            require(bytes.size <= MAX_IMPORT_BYTES) { env.t("Fichier trop volumineux (max 10 Mo).", "الملف كبير جداً (الحد 10 ميغا).") }
            PointCsv.parse(TextFiles.decode(bytes), if (order == 0) CsvColumnOrder.NAME_X_Y_Z else CsvColumnOrder.NAME_Y_X_Z)
        }.onSuccess { result ->
            val existing = env.points.map { it.name }.toSet()
            val replaced = result.points.count { it.name in existing }
            if (result.points.isNotEmpty()) {
                env.savePoints(result.points.map { NewPoint(it.name, it.x, it.y, it.z, it.code, "CSV") })
            }
            importReport = result to replaced
        }.onFailure { env.toast(errorMessage(env.french, it)) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val csv = PointCsv.write(env.points.map { CsvPoint(it.name, it.x, it.y, it.z, it.code) })
            context.contentResolver.openOutputStream(uri)?.use { it.write(TextFiles.encodeForExcel(csv)) }
                ?: throw java.io.IOException("Cannot open destination")
        }.onSuccess { env.toast(env.t("Fichier enregistré", "تم حفظ الملف")) }
            .onFailure { env.toast(errorMessage(env.french, it)) }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle(env.t("Projets", "المشاريع"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Field(env.t("Nouveau projet", "مشروع جديد"), name, { name = it }, Modifier.weight(1f), numeric = false)
            Button(onClick = { if (name.isNotBlank()) { onAdd(name.trim()); name = "" } }) { Text(env.t("Créer", "إنشاء")) }
        }
        if (projects.isEmpty()) {
            Hint(env.t("Créez un projet pour enregistrer des points et des calculs.", "أنشئ مشروعاً لحفظ النقط والحسابات."))
        }
        // Project list (compact; the active one is highlighted).
        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            projects.forEach { p ->
                ListItem(
                    headlineContent = {
                        Text(p.name, fontWeight = if (p.id == active?.id) FontWeight.Bold else null,
                            color = if (p.id == active?.id) LocalAppColors.current.heading else Color.Unspecified)
                    },
                    supportingContent = {
                        Text((if (p.id == active?.id) env.t("Projet actif · ", "المشروع النشط · ") else "") +
                            dateFormat.format(java.util.Date(p.createdAt)))
                    },
                    trailingContent = { TextButton(onClick = { pendingDelete = p }) { Text(env.t("Supprimer", "حذف")) } },
                    modifier = Modifier.clickable { onActivate(p) }
                )
            }
        }

        if (active != null) {
            HorizontalDivider()
            Text(active.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChoiceRow(listOf(env.t("Points", "النقط") + " (${env.points.size})", env.t("Calculs", "الحسابات") + " (${calculations.size})",
                env.t("Sauvegarde", "النسخة الاحتياطية")), section) { section = it }
            if (section == 2) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { backupSection() }
            } else if (section == 0) {
                Text(env.t("Ordre des colonnes du fichier CSV :", "ترتيب أعمدة ملف CSV:"), style = MaterialTheme.typography.bodySmall)
                ChoiceRow(listOf(env.t("Nom X(E) Y(N) Z Code", "الاسم X(E) Y(N) Z رمز"), env.t("Nom Y(N) X(E) Z Code", "الاسم Y(N) X(E) Z رمز")), order) { order = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }) {
                        Text(env.t("Importer CSV", "استيراد CSV"))
                    }
                    OutlinedButton(onClick = {
                        if (env.points.isEmpty()) env.toast(env.t("Aucun point à exporter.", "لا توجد نقط للتصدير."))
                        else exportLauncher.launch("${active.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_points.csv")
                    }) { Text(env.t("Exporter CSV", "تصدير CSV")) }
                    if (env.points.isNotEmpty()) TextButton(onClick = { confirmDeleteAllPoints = true }) { Text(env.t("Tout supprimer", "حذف الكل")) }
                }
                Hint(env.t("Séparateurs ; , tabulation ou espaces, virgule décimale acceptée, en-tête facultatif. Un point existant du même nom est remplacé.",
                    "الفواصل ; , tab أو فراغات، الفاصلة العشرية مقبولة، والعنوان اختياري. النقطة الموجودة بنفس الاسم تُعوَّض."))
                LazyColumn(Modifier.weight(1f)) {
                    items(env.points, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name + if (p.code.isNotBlank()) "  · ${p.code}" else "") },
                            supportingContent = { Text("X ${fmt(p.x)}  Y ${fmt(p.y)}" + (p.z?.let { "  Z ${fmt(it)}" } ?: "") + "  · ${p.source}") },
                            trailingContent = { TextButton(onClick = { onDeletePoint(p) }) { Text("✕") } }
                        )
                    }
                }
            } else {
                if (calculations.isEmpty()) Hint(env.t("Aucun calcul enregistré. Utilisez « Enregistrer dans le projet » dans chaque écran.",
                    "لا توجد حسابات محفوظة. استعمل «حفظ في المشروع» في كل شاشة."))
                LazyColumn(Modifier.weight(1f)) {
                    items(calculations, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.title) },
                            supportingContent = { Text(dateFormat.format(java.util.Date(c.createdAt)) + if (c.summary.isNotBlank()) "\n${c.summary}" else "") },
                            trailingContent = {
                                Row {
                                    TextButton(onClick = { onOpenCalculation(c) }) { Text(env.t("Ouvrir", "فتح")) }
                                    TextButton(onClick = { onDeleteCalculation(c) }) { Text("✕") }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            if (projects.isNotEmpty()) Hint(env.t("Touchez un projet pour l'activer.", "اضغط على مشروع لتفعيله."))
            HorizontalDivider()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { backupSection() }
        }
    }

    pendingRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(env.t("Restaurer la sauvegarde ?", "استرجاع النسخة؟")) },
            text = {
                Text(env.t("Remplacer tous les projets actuels par la sauvegarde (${b.projects.size} projets, ${b.projects.sumOf { it.points.size }} points) ?",
                    "تعويض كل المشاريع الحالية بالنسخة (${b.projects.size} مشاريع، ${b.projects.sumOf { it.points.size }} نقطة)؟") +
                    if (b.skippedCalculations > 0) env.t("\n${b.skippedCalculations} calcul(s) de la version HTML ne seront pas repris (les points oui).",
                        "\n${b.skippedCalculations} حساب من نسخة HTML ما غاديش يترجعو (النقط نعم).") else "")
            },
            confirmButton = { Button(onClick = { onBackupRestore(b); pendingRestore = null }) { Text(env.t("Restaurer", "استرجاع")) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(env.t("Annuler", "إلغاء")) } }
        )
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(env.t("Supprimer le projet ?", "حذف المشروع؟")) },
            text = { Text(p.name + "\n" + env.t("Ses points et calculs seront supprimés.", "ستُحذف نقطه وحساباته.")) },
            confirmButton = { TextButton(onClick = { onDelete(p); pendingDelete = null }) { Text(env.t("Supprimer", "حذف")) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(env.t("Annuler", "إلغاء")) } }
        )
    }
    if (confirmDeleteAllPoints) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllPoints = false },
            title = { Text(env.t("Supprimer tous les points ?", "حذف كل النقط؟")) },
            text = { Text("${env.points.size} " + env.t("points", "نقطة")) },
            confirmButton = { TextButton(onClick = { onDeleteAllPoints(); confirmDeleteAllPoints = false }) { Text(env.t("Supprimer", "حذف")) } },
            dismissButton = { TextButton(onClick = { confirmDeleteAllPoints = false }) { Text(env.t("Annuler", "إلغاء")) } }
        )
    }
    importReport?.let { (result, replaced) ->
        AlertDialog(
            onDismissRequest = { importReport = null },
            title = { Text(env.t("Import CSV", "استيراد CSV")) },
            text = {
                val sep = when (result.delimiter) { '\t' -> "tab"; ' ' -> env.t("espace", "فراغ"); else -> result.delimiter.toString() }
                val lines = buildList {
                    add(env.t("Points importés : ", "النقط المستوردة: ") + result.points.size)
                    if (replaced > 0) add(env.t("dont remplacés : ", "منها معوَّضة: ") + replaced)
                    add(env.t("Séparateur : ", "الفاصل: ") + sep + if (result.decimalComma) env.t(" · virgule décimale", " · فاصلة عشرية") else "")
                    if (result.skipped.isNotEmpty()) {
                        add(env.t("Lignes ignorées : ", "أسطر متجاهلة: ") + result.skipped.size)
                        result.skipped.take(8).forEach { s ->
                            val reason = when (s.reason) {
                                PointCsv.HEADER_REASON -> env.t("en-tête", "عنوان")
                                PointCsv.DUPLICATE_REASON -> env.t("nom en double", "اسم مكرر")
                                PointCsv.NAME_REASON -> env.t("nom manquant", "اسم ناقص")
                                PointCsv.COLUMNS_REASON -> env.t("moins de 3 colonnes", "أقل من 3 أعمدة")
                                else -> env.t("coordonnée invalide", "إحداثية غير صالحة")
                            }
                            add("  L${s.lineNumber}: $reason — ${s.content.take(40)}")
                        }
                    }
                    if (result.points.isNotEmpty()) {
                        val p = result.points.first()
                        add(env.t("Vérifier : ", "تحقق: ") + "${p.name} → X ${fmt(p.x)}, Y ${fmt(p.y)}")
                    }
                }
                Text(lines.joinToString("\n"))
            },
            confirmButton = { TextButton(onClick = { importReport = null }) { Text("OK") } }
        )
    }
}

/** Backup / restore of all projects (file shared with the HTML version) + app logo and version. */
@Composable
private fun BackupAndAbout(env: AppEnv, lastBackup: Long?, onExport: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        SectionTitle(env.t("Sauvegarde", "النسخة الاحتياطية"))
        val days = lastBackup?.let { ((System.currentTimeMillis() - it) / 86_400_000L).toInt() }
        val fmtDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        Text(
            if (lastBackup == null) env.t("Aucune sauvegarde pour le moment.", "لا توجد نسخة احتياطية بعد.")
            else env.t("Dernière sauvegarde : ", "آخر نسخة: ") + fmtDate.format(java.util.Date(lastBackup)) +
                if ((days ?: 0) >= 7) env.t(" — plus de 7 jours", " — أكثر من 7 أيام") else "",
            color = if (lastBackup == null || (days ?: 0) >= 7) MaterialTheme.colorScheme.error else c.muted,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text(env.t("Sauvegarder tout", "حفظ نسخة")) }
            OutlinedButton(onClick = onImport) { Text(env.t("Restaurer", "استرجاع")) }
        }
        Hint(env.t("Tous les projets, points et calculs dans un fichier .json (à garder sur Drive, WhatsApp, e-mail…). Le même fichier s'ouvre aussi dans la version HTML (projets et points).",
            "كل المشاريع والنقط والحسابات في ملف ‎.json (خليه في Drive، WhatsApp، e-mail…). نفس الملف كيتحل حتى في نسخة HTML (المشاريع والنقط)."))
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(R.drawable.ik_logo), contentDescription = "IK TopoCalc",
                    modifier = Modifier.height(76.dp)
                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                val version = runCatching {
                    @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: ""
                Spacer(Modifier.height(6.dp))
                Text("IK TopoCalc · v$version", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
