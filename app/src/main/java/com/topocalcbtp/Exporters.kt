package com.topocalcbtp

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.IOException
import java.util.Locale

object Exporters {
    fun methodLabelFr(m: com.topocalcbtp.engine.leveling.LevelAdjustmentMethod): String = when (m) {
        com.topocalcbtp.engine.leveling.LevelAdjustmentMethod.PROPORTIONAL_TO_DISTANCE -> "proportionnelle aux portées"
        com.topocalcbtp.engine.leveling.LevelAdjustmentMethod.EQUAL_PER_SETUP -> "égale par station (N)"
        com.topocalcbtp.engine.leveling.LevelAdjustmentMethod.PROPORTIONAL_TO_ABS_DELTA -> "proportionnelle aux |ΔH|"
        com.topocalcbtp.engine.leveling.LevelAdjustmentMethod.NONE -> "aucune"
    }

    private fun f(value: Double, decimals: Int = 3) = "%.${decimals}f".format(Locale.US, value)

    /** Rows shared by the Excel export (first row = header). */
    fun levelingTable(result: ClosureResult): List<List<Any?>> {
        val rows = mutableListOf<List<Any?>>()
        rows += listOf("De", "Vers", "LA (m)", "LV (m)", "Distance (m)", "Dénivelé observé (m)",
            "Correction (m)", "Dénivelé compensé (m)", "Altitude (m)")
        result.rows.forEachIndexed { i, row ->
            result.intermediates.filter { it.shotIndex == i }.forEach { li ->
                rows += listOf(row.shot.from, li.name + " (LI " + f(li.reading) + ")", null, null, null, null, null, null, li.altitude)
            }
            rows += listOf(row.shot.from, row.shot.to, row.shot.backsight, row.shot.foresight, row.shot.distanceMeters,
                row.shot.observedDelta, row.correction, row.compensatedDelta, row.altitudeTo)
        }
        rows += emptyList<Any?>()
        rows += listOf("Altitude de départ (m)", result.startAltitude)
        rows += listOf("ΣLA (m)", result.sumBacksights)
        rows += listOf("ΣLV (m)", result.sumForesights)
        rows += listOf("Longueur totale (m)", result.totalDistance)
        rows += listOf("Contrôle arithmétique", if (result.arithmeticCheckOk) "OK" else "ERREUR")
        result.closureError?.let { rows += listOf("Erreur de fermeture (m)", it) }
            ?: run { rows += listOf("Cheminement ouvert", "non compensé") }
        if (result.closureError != null) rows += listOf("Compensation", methodLabelFr(result.method))
        result.tolerance?.let { rows += listOf("Tolérance (m)", it) }
        result.withinTolerance?.let { rows += listOf("Fermeture", if (it) "Dans la tolérance" else "HORS TOLÉRANCE") }
        return rows
    }

    @Throws(IOException::class)
    fun exportExcel(context: Context, uri: Uri, result: ClosureResult) {
        val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open destination")
        stream.use { XlsxWriter.write(it, "Nivellement", levelingTable(result)) }
    }

    @Throws(IOException::class)
    fun exportPdf(context: Context, uri: Uri, title: String, result: ClosureResult) {
        val pdf = PdfDocument()
        try {
            val width = 842; val height = 595 // A4 landscape, points
            val top = 42f; val left = 32f; val lineHeight = 16f; val bottom = height - 40f
            val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
            val paint = Paint().apply { textSize = 9.5f; typeface = Typeface.MONOSPACE; isAntiAlias = true }
            val header = "%-10s %-10s %8s %8s %9s %9s %9s %9s %10s".format(Locale.US,
                "De", "Vers", "LA", "LV", "Dist.", "ΔH obs.", "Corr.", "ΔH comp.", "Altitude")

            val summary = mutableListOf<String>()
            summary += "Altitude de départ : ${f(result.startAltitude)} m    Longueur : ${f(result.totalDistance, 1)} m"
            summary += "ΣLA = ${f(result.sumBacksights)}   ΣLV = ${f(result.sumForesights)}   Contrôle arithmétique : " +
                if (result.arithmeticCheckOk) "OK" else "ERREUR"
            summary += result.closureError?.let { "Erreur de fermeture : ${f(it, 4)} m" } ?: "Cheminement ouvert (non compensé)"
            if (result.closureError != null) summary += "Compensation : " + methodLabelFr(result.method)
            result.tolerance?.let { t ->
                summary += "Tolérance : ${f(t, 4)} m  →  " + if (result.withinTolerance == true) "Dans la tolérance" else "HORS TOLÉRANCE"
            }

            var pageNumber = 0
            var page: PdfDocument.Page? = null
            var y = 0f
            fun newPage() {
                page?.let { pdf.finishPage(it) }
                pageNumber++
                val p = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
                page = p
                p.canvas.drawText(title, left, top, titlePaint)
                p.canvas.drawText("Page $pageNumber", width - 90f, top, paint)
                y = top + 26f
                p.canvas.drawText(header, left, y, paint)
                y += lineHeight
            }
            newPage()
            result.rows.forEachIndexed { i, row ->
                result.intermediates.filter { it.shotIndex == i }.forEach { li ->
                    if (y > bottom) newPage()
                    val liLine = "%-10s %-10s %8s %8.3f %9s %9s %9s %9s %10.3f".format(Locale.US,
                        row.shot.from.take(10), li.name.take(10), "LI", li.reading, "", "", "", "", li.altitude)
                    page!!.canvas.drawText(liLine, left, y, paint)
                    y += lineHeight
                }
                if (y > bottom) newPage()
                val line = "%-10s %-10s %8.3f %8.3f %9.2f %9.3f %9.4f %9.3f %10.3f".format(Locale.US,
                    row.shot.from.take(10), row.shot.to.take(10), row.shot.backsight, row.shot.foresight,
                    row.shot.distanceMeters, row.shot.observedDelta, row.correction, row.compensatedDelta, row.altitudeTo)
                page!!.canvas.drawText(line, left, y, paint)
                y += lineHeight
            }
            y += lineHeight / 2
            summary.forEach { line ->
                if (y > bottom) newPage()
                page!!.canvas.drawText(line, left, y, paint)
                y += lineHeight
            }
            page?.let { pdf.finishPage(it) }
            val stream = context.contentResolver.openOutputStream(uri) ?: throw IOException("Cannot open destination")
            stream.use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }
}
