@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TopoCalcApp() }
    }
}

private const val TAB_LEVEL = 0
private const val TAB_THEODOLITE = 1
private const val TAB_CALC = 2
private const val TAB_COORD = 3
private const val TAB_PROJECT = 4

private const val SUB_RAYONNEMENT = 0
private const val SUB_CHEMINEMENT = 1
private const val SUB_IMPLANTATION = 2

@Composable
fun TopoCalcApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val dao = remember { TopoDatabase.get(context).projectDao() }
    val projects by dao.observeAll().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(TAB_LEVEL) }
    var calcSub by rememberSaveable { mutableIntStateOf(SUB_RAYONNEMENT) }
    var french by remember { mutableStateOf(prefs.getBoolean("french", false)) }
    var unit by remember {
        mutableStateOf(runCatching { AngleUnit.valueOf(prefs.getString("unit", null) ?: "DEGREES") }
            .getOrDefault(AngleUnit.DEGREES))
    }
    // Theme: "auto" follows the phone until the user taps the sun/moon button.
    var themeChoice by remember { mutableStateOf(prefs.getString("theme", "auto") ?: "auto") }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeChoice) { "dark" -> true; "light" -> false; else -> systemDark }
    var lastBackup by remember { mutableStateOf(prefs.getLong("lastBackup", 0L).takeIf { it > 0 }) }
    var activeProjectId by remember { mutableStateOf(prefs.getLong("activeProject", -1L).takeIf { it > 0 }) }
    val activeProject = projects.firstOrNull { it.id == activeProjectId }
    val pointsFlow = remember(activeProjectId) { activeProjectId?.let { dao.observePoints(it) } ?: flowOf(emptyList()) }
    val rawPoints by pointsFlow.collectAsState(initial = emptyList())
    val calculationsFlow = remember(activeProjectId) { activeProjectId?.let { dao.observeCalculations(it) } ?: flowOf(emptyList()) }
    val calculations by calculationsFlow.collectAsState(initial = emptyList())
    val points = remember(rawPoints) { rawPoints.sortedWith { a, b -> naturalNameOrder.compare(a.name, b.name) } }
    var pendingLoad by remember { mutableStateOf<SavedCalculation?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val calcStateHolder = rememberSaveableStateHolder()

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun setActive(id: Long?) {
        activeProjectId = id
        prefs.edit().putLong("activeProject", id ?: -1L).apply()
    }

    val env = AppEnv(
        french = french,
        unit = unit,
        project = activeProject,
        points = points,
        savePoints = { newPoints ->
            val project = activeProject
            if (project == null) toast(tr(french, "Choisissez d'abord un projet.", "اختر مشروعاً أولاً."))
            else if (newPoints.isNotEmpty()) scope.launch {
                try {
                    val existing = dao.pointNames(project.id).toSet()
                    val replaced = newPoints.count { it.name in existing }
                    dao.upsertPoints(newPoints.map {
                        ProjectPoint(projectId = project.id, name = it.name, x = it.x, y = it.y, z = it.z, code = it.code, source = it.source)
                    })
                    toast(tr(french, "${newPoints.size} point(s) enregistré(s) dans « ${project.name} »", "تم حفظ ${newPoints.size} نقطة في «${project.name}»") +
                        if (replaced > 0) tr(french, " — $replaced remplacé(s)", " — $replaced معوَّضة") else "")
                } catch (e: Exception) {
                    toast(errorMessage(french, e))
                }
            }
        },
        saveCalculation = { type, title, summary, record ->
            val project = activeProject
            if (project == null) toast(tr(french, "Choisissez d'abord un projet.", "اختر مشروعاً أولاً."))
            else scope.launch {
                try {
                    dao.insertCalculation(SavedCalculation(projectId = project.id, type = type, title = title,
                        summary = summary, payload = RecordCodec.encode(record)))
                    toast(tr(french, "Calcul enregistré dans « ${project.name} »", "تم حفظ الحساب في «${project.name}»"))
                } catch (e: Exception) {
                    toast(errorMessage(french, e))
                }
            }
        },
        toast = { toast(it) }
    )

    val labels = if (french) listOf("Niveau", "Théodolite", "Calculs", "Coord.", "Projet")
                 else listOf("نيفو", "تيودوليت", "حسابات", "إحداثيات", "المشروع")

    IkTheme(dark) {
        val appColors = LocalAppColors.current
        SystemBarsColor(appColors.topBar, MaterialTheme.colorScheme.surfaceContainer, darkIcons = !dark)
        CompositionLocalProvider(LocalLayoutDirection provides if (french) LayoutDirection.Ltr else LayoutDirection.Rtl) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = appColors.topBar),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painterResource(R.drawable.ik_logo), contentDescription = "IK TopoCalc",
                                        modifier = Modifier.height(34.dp)
                                            .then(if (dark) Modifier.background(Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp) else Modifier)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("IK TopoCalc", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                                        Text(activeProject?.let { tr(french, "Projet : ", "المشروع: ") + it.name }
                                                ?: tr(french, "Aucun projet actif", "لا يوجد مشروع نشط"),
                                            style = MaterialTheme.typography.labelSmall, color = appColors.muted,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            },
                            actions = {
                                // Each button shows the state in use: sun/moon, ° / gr.
                                HeaderButton(onClick = {
                                    themeChoice = if (dark) "light" else "dark"
                                    prefs.edit().putString("theme", themeChoice).apply()
                                    toast(if (themeChoice == "dark") tr(french, "Mode sombre", "الوضع الداكن") else tr(french, "Mode clair", "الوضع الفاتح"))
                                }) { Icon(if (dark) IkIcons.Moon else IkIcons.Sun, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                HeaderButton(onClick = {
                                    unit = if (unit == AngleUnit.DEGREES) AngleUnit.GONS else AngleUnit.DEGREES
                                    prefs.edit().putString("unit", unit.name).apply()
                                    toast(if (unit == AngleUnit.DEGREES) tr(french, "Unité des angles : degrés (°)", "وحدة الزوايا: الدرجات (°)")
                                          else tr(french, "Unité des angles : grades (gr)", "وحدة الزوايا: الغراد (gr)"))
                                }) { Text(if (unit == AngleUnit.DEGREES) "°" else "gr", fontSize = if (unit == AngleUnit.DEGREES) 24.sp else 16.sp, fontWeight = FontWeight.Bold) }
                                HeaderButton(onClick = {
                                    french = !french
                                    prefs.edit().putBoolean("french", french).apply()
                                }) { Text(if (french) "عربي" else "FR", fontWeight = FontWeight.Bold) }
                            }
                        )
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Brand.Yellow))
                    }
                },
                bottomBar = { IkBottomNav(labels, tab) { tab = it } }
            ) { padding ->
                val m = Modifier.padding(padding)
                fun consumeLoad() { pendingLoad = null }
                stateHolder.SaveableStateProvider("tab$tab") {
                    when (tab) {
                        TAB_LEVEL -> LevelingScreen(m, env, pendingLoad, ::consumeLoad)
                        TAB_THEODOLITE -> TheodoliteScreen(m, env, pendingLoad, ::consumeLoad)
                        TAB_CALC -> Column(m.fillMaxSize()) {
                            Box(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                ChoiceRow(
                                    listOf(tr(french, "Rayonnement", "Rayonnement"), tr(french, "Cheminement", "Cheminement"),
                                        tr(french, "Implantation", "Implantation")),
                                    calcSub
                                ) { calcSub = it }
                            }
                            val sub = Modifier.weight(1f)
                            calcStateHolder.SaveableStateProvider("calc$calcSub") {
                                when (calcSub) {
                                    SUB_RAYONNEMENT -> TopometryScreen(sub, env)
                                    SUB_CHEMINEMENT -> TraverseScreen(sub, env, pendingLoad, ::consumeLoad)
                                    else -> StakeoutScreen(sub, env, pendingLoad, ::consumeLoad)
                                }
                            }
                        }
                        TAB_COORD -> ConversionScreen(m, env)
                        else -> ProjectsScreen(
                            m, env, projects, calculations,
                            onActivate = { setActive(it.id) },
                            onAdd = { name ->
                                scope.launch {
                                    try { setActive(dao.insert(SurveyProject(name = name))) } catch (e: Exception) { toast(errorMessage(french, e)) }
                                }
                            },
                            onDelete = { p ->
                                scope.launch {
                                    try { dao.delete(p); if (p.id == activeProjectId) setActive(null) } catch (e: Exception) { toast(errorMessage(french, e)) }
                                }
                            },
                            onOpenCalculation = { c ->
                                pendingLoad = c
                                when (c.type) {
                                    CalcType.LEVELING -> tab = TAB_LEVEL
                                    CalcType.THEODOLITE -> tab = TAB_THEODOLITE
                                    CalcType.TRAVERSE -> { calcSub = SUB_CHEMINEMENT; tab = TAB_CALC }
                                    CalcType.STAKEOUT -> { calcSub = SUB_IMPLANTATION; tab = TAB_CALC }
                                    else -> { pendingLoad = null; toast(tr(french, "Type de calcul inconnu.", "نوع حساب غير معروف.")) }
                                }
                            },
                            onDeleteCalculation = { c -> scope.launch { runCatching { dao.deleteCalculation(c) } } },
                            onDeletePoint = { p -> scope.launch { runCatching { dao.deletePoint(p) } } },
                            onDeleteAllPoints = { activeProjectId?.let { id -> scope.launch { runCatching { dao.deleteAllPoints(id) } } } },
                            lastBackup = lastBackup,
                            onBackupExport = { uri ->
                                scope.launch {
                                    try {
                                        val all = dao.allProjects().map { p -> Triple(p, dao.pointsOf(p.id), dao.calculationsOf(p.id)) }
                                        val json = Backup.toJson(all, activeProjectId)
                                        withContext(Dispatchers.IO) {
                                            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                                                ?: throw java.io.IOException("Cannot open destination")
                                        }
                                        lastBackup = System.currentTimeMillis()
                                        prefs.edit().putLong("lastBackup", lastBackup ?: 0L).apply()
                                        toast(tr(french, "Sauvegarde enregistrée", "تم حفظ النسخة"))
                                    } catch (e: Exception) { toast(errorMessage(french, e)) }
                                }
                            },
                            onBackupRestore = { backup ->
                                scope.launch {
                                    try {
                                        val ids = dao.replaceAll(backup.projects)
                                        setActive(backup.activeBackupId?.let { ids[it] } ?: ids.values.firstOrNull())
                                        toast(tr(french, "Sauvegarde restaurée", "تم الاسترجاع"))
                                    } catch (e: Exception) { toast(errorMessage(french, e)) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * System bars follow the header / bottom bar colours (Android < 15; from Android 15 the app is drawn
 * edge-to-edge and the header and bottom bar themselves extend under the system bars).
 */
@Composable
private fun SystemBarsColor(top: Color, bottom: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = top.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = bottom.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = darkIcons
            controller.isAppearanceLightNavigationBars = darkIcons
        }
    }
}
