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

/** Bottom bar in light / dark, Arabic (RTL) and French (LTR), each tab, plus frames during the slide. */
@RunWith(AndroidJUnit4::class)
class NavTour {
    @get:Rule val rule = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val dir = File(ctx.filesDir, "shots").apply { deleteRecursively(); mkdirs() }
    private val log = StringBuilder()

    private fun shot(name: String, settle: Long = 900) {
        rule.waitForIdle(); Thread.sleep(settle)
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        log.append("shot $name\n")
    }

    private fun quick(name: String) {
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String) {
        rule.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
    }

    private fun prefs(theme: String, french: Boolean) =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme", theme).putBoolean("french", french).commit()

    @Test
    fun tour() {
        runBlocking {
            val dao = TopoDatabase.get(ctx).projectDao()
            dao.deleteEveryCalculation(); dao.deleteEveryPoint(); dao.deleteEveryProject()
        }
        val ar = listOf("نيفو", "تيودوليت", "حسابات", "إحداثيات", "المشروع")
        val fr = listOf("Niveau", "Théodolite", "Calculs", "Coord.", "Projet")
        prefs("light", false)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle(); Thread.sleep(1500)
        for ((mode, theme) in listOf("light" to "light", "dark" to "dark")) {
            if (mode == "dark") { prefs("dark", false); scenario.recreate(); rule.waitForIdle(); Thread.sleep(1200) }
            try {
                tap(ar[0]); shot("${mode}_ar_0")
                tap(ar[4]); Thread.sleep(90); quick("${mode}_ar_slide_a"); Thread.sleep(90); quick("${mode}_ar_slide_b"); shot("${mode}_ar_4")
                tap(ar[1]); shot("${mode}_ar_1")
                tap(ar[2]); shot("${mode}_ar_2")
                tap(ar[3]); shot("${mode}_ar_3")
            } catch (t: Throwable) { log.append("FAIL $mode: $t\n") }
        }
        try {
            prefs("light", true); scenario.recreate(); rule.waitForIdle(); Thread.sleep(1200)
            tap(fr[0]); shot("light_fr_0"); tap(fr[2]); shot("light_fr_2")
            prefs("dark", true); scenario.recreate(); rule.waitForIdle(); Thread.sleep(1200)
            tap(fr[4]); shot("dark_fr_4")
        } catch (t: Throwable) { log.append("FAIL fr: $t\n") }
        prefs("auto", false)
        File(dir, "log.txt").writeText(log.toString())
    }
}
