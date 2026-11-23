package com.topocalcbtp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opens each new screen with sample data from the OFPPT M07 course and saves screenshots. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {
    @get:Rule val rule = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val dir = File(ctx.filesDir, "shots").apply { deleteRecursively(); mkdirs() }
    private val log = StringBuilder()
    private val dao = TopoDatabase.get(ctx).projectDao()
    private var pid = 0L

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(900)
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        log.append("shot $name\n")
    }

    private fun step(name: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            log.append("FAIL $name: $t\n"); android.util.Log.e("Tour", name, t)
            runCatching { shot("zz_fail_$name") }
        }
    }

    private fun tap(text: String, substring: Boolean = false) {
        val n = rule.onAllNodes(hasText(text, substring = substring) and hasClickAction()).onFirst()
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        rule.onAllNodesWithText(text, substring = substring).onFirst().performScrollTo()
        rule.waitForIdle()
    }

    private fun open(type: String, title: String, rec: Record) {
        runBlocking {
            dao.deleteEveryCalculation()
            dao.insertCalculation(SavedCalculation(projectId = pid, type = type, title = title, summary = "", payload = RecordCodec.encode(rec)))
        }
        tap("المشروع")
        Thread.sleep(400)
        tap("الحسابات (", substring = true)
        tap("فتح")
        Thread.sleep(2600) // let the "opened" toast disappear
    }

    @Test
    fun tour() {
        pid = runBlocking {
            dao.deleteEveryCalculation(); dao.deleteEveryPoint(); dao.deleteEveryProject()
            dao.insert(SurveyProject(name = "Chantier démo"))
        }
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("french", false).putString("theme", "light").putLong("activeProject", pid).putString("unit", "GONS").commit()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle(); Thread.sleep(1500)

        step("niveau") {
            val r = Record(CalcType.LEVELING)
            r["start"] = "100.000"; r["end"] = "99.500"; r["k"] = "4.6"; r["method"] = "1"; r["tolMode"] = "1"
            r.rows += listOf("A", "P1", "1.523", "0.987", "60", "")
            r.rows += listOf("P1", "P2", "1.204", "1.876", "70", IntermediateCodec.encode(listOf("L1" to 1.350, "L2" to 1.512)))
            r.rows += listOf("P2", "B", "0.954", "1.322", "50", "")
            open(CalcType.LEVELING, "Nivellement A → B", r)
            shot("01_niveau_saisie")
            tap("نقطة وسيطة (LI)")
            shot("02_niveau_LI")
            tap("نقطة أمامية (LV)")
            tap("حساب التعويض والمناسيب")
            scrollTo("Excel")
            shot("03_niveau_resultat")
        }

        step("alti") {
            val r = Record(CalcType.SPOT_LEVELS)
            r["refName"] = "NGM"; r["refZ"] = "20.153"; r["bs"] = "1.845"; r["gradeStart"] = "20.000"; r["gradePercent"] = "1.25"; r["staff"] = "4"
            r.rows += listOf("P1", "2.409", "19.500", "")
            r.rows += listOf("P2", "3.614", "", "")
            r.rows += listOf("P3", "0.175", "21.900", "")
            r.rows += listOf("S1", "", "20.000", "0")
            r.rows += listOf("S2", "", "20.250", "20")
            r.rows += listOf("S3", "", "20.500", "40")
            tap("نيفو"); Thread.sleep(300)
            tap("Rayonnement و implantation")
            open(CalcType.SPOT_LEVELS, "Rayonnement alti NGM", r)
            shot("04_alti_saisie")
            tap("احسب")
            scrollTo("مشاركة")
            shot("05_alti_resultat")
        }

        step("profil") {
            val r = Record(CalcType.LONG_PROFILE)
            r["cumulative"] = "0"; r["designMode"] = "0"; r["startZ"] = "250.80"; r["slope"] = "-0.5"; r["width"] = "1.2"
            listOf("P0" to "0", "P1" to "20", "P2" to "20", "P3" to "20", "P4" to "20", "P5" to "20")
                .zip(listOf("251.20", "250.85", "250.30", "249.95", "250.40", "250.95"))
                .forEach { (a, z) -> r.rows += listOf(a.first, a.second, z) }
            open(CalcType.LONG_PROFILE, "Profil en long P0 → P5", r)
            shot("06_profil_saisie")
            tap("احسب الـprofil")
            scrollTo("ردم")
            shot("07_profil_graphique")
            scrollTo("مشاركة")
            shot("08_profil_resultat")
        }

        step("cubature") {
            val r = Record(CalcType.SECTIONS)
            val d = listOf("0", "3.50", "4.90", "5.40", "4.40", "4.80", "3.00", "3.00", "0.70", "2.30")
            val h = listOf("3.70", "3.20", "2.20", "1.20", "0.20", "1.20", "2.20", "3.20", "3.20", "3.79")
            r["mode"] = "0"; r["points"] = encodeRows(d.zip(h).map { listOf(it.first, it.second) }); r["designZ"] = ""; r["swell"] = "1.25"
            r["name"] = "P1"; r["pk"] = "0"
            r.rows += listOf("P2", "5.60", "58.30", "0")
            r.rows += listOf("P3", "11.20", "49.85", "0")
            open(CalcType.SECTIONS, "Cubature P1 → P3", r)
            tap("احسب المساحة")
            scrollTo("زيدو للائحة البروفيلات ↓")
            shot("09_cubature_surface")
            tap("زيدو للائحة البروفيلات ↓")
            Thread.sleep(2200)
            tap("احسب الأحجام")
            scrollTo("مشاركة")
            shot("10_cubature_volumes")
        }

        step("quadrillage") {
            val r = Record(CalcType.GRID)
            r["dx"] = "20"; r["dy"] = "20"; r["designZ"] = "250.00"; r["mode"] = "1"; r["refZ"] = "250.300"; r["refReading"] = "1.480"; r["swell"] = ""
            r["grid"] = listOf(
                "1.480 1.450 1.382 1.475 1.523 1.605", "1.605 1.558 1.485 1.520 1.552 1.590", "1.750 1.705 1.620 1.605 1.655 1.622",
                "1.625 1.583 1.500 1.525 1.586 1.655", "1.575 1.502 1.390 1.455 1.520 1.600").joinToString("\n")
            open(CalcType.GRID, "Quadrillage 20×20 m", r)
            shot("11_quadrillage_saisie")
            tap("احسب الأحجام")
            scrollTo("Cote d'équilibre", substring = true)
            shot("12_quadrillage_resultat")
            scrollTo("مشاركة")
            shot("13_quadrillage_hauteurs")
        }

        step("outils") {
            tap("حسابات"); Thread.sleep(300)
            tap("أدوات")
            tap("المتوسط")
            rule.onAllNodes(hasSetTextAction()).onFirst()
                .performTextInput("128.567\n128.572\n128.563\n128.568\n128.562\n128.575\n128.571\n128.565\n128.569\n128.574")
            shot("14a_clavier")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(800)
            scrollTo("حتى قياس", substring = true)
            shot("14_outils_moyenne")
            tap("Interpolation")
            val f = rule.onAllNodes(hasSetTextAction())
            f[0].performTextInput("150"); f[1].performTextInput("155"); f[2].performTextInput("20"); f[3].performTextInput("8")
            inst.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(800)
            shot("15_outils_interpolation")
        }

        step("sombre") {
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme", "dark").commit()
            scenario.recreate()
            rule.waitForIdle(); Thread.sleep(1200)
            tap("Profil en long")
            tap("احسب الـprofil")
            scrollTo("ردم")
            shot("16_profil_sombre")
        }

        File(dir, "log.txt").writeText(log.toString())
    }
}
