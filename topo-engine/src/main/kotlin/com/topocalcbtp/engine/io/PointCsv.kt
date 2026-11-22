package com.topocalcbtp.engine.io

import java.util.Locale

/** X = Easting, Y = Northing (engine convention). */
data class CsvPoint(val name: String, val x: Double, val y: Double, val z: Double? = null, val code: String = "")

/** Column order after the point name. Many instruments export Northing before Easting (PNEZD). */
enum class CsvColumnOrder { NAME_X_Y_Z, NAME_Y_X_Z }

data class SkippedLine(val lineNumber: Int, val content: String, val reason: String)

data class CsvImportResult(
    val points: List<CsvPoint>,
    val skipped: List<SkippedLine>,
    /** Detected field separator: ';', ',', '\t' or ' ' (any whitespace). */
    val delimiter: Char,
    val decimalComma: Boolean
)

object PointCsv {
    const val HEADER_REASON = "header"
    const val DUPLICATE_REASON = "duplicate name"
    const val NAME_REASON = "missing point name"
    const val COLUMNS_REASON = "fewer than 3 columns"
    const val NUMBER_REASON = "invalid coordinate"

    /**
     * Parses point lists exported by total stations, GNSS receivers or spreadsheets:
     *  - separators ';', tab, ',' or spaces (detected automatically);
     *  - decimal comma accepted with ';', tab or space separators ("1234,567");
     *  - optional header line, optional Z and code columns, surrounding quotes removed;
     *  - the first occurrence of a point name is kept, later duplicates are reported.
     */
    fun parse(text: String, order: CsvColumnOrder = CsvColumnOrder.NAME_X_Y_Z): CsvImportResult {
        val lines = text.removePrefix("﻿").lines()
        val sample = lines.filter { it.isNotBlank() }.take(30)
        val delimiter = when {
            sample.any { it.contains(';') } -> ';'
            sample.any { it.contains('\t') } -> '\t'
            sample.any { it.contains(',') } && !looksSpaceSeparatedWithDecimalComma(sample) -> ','
            else -> ' '
        }
        val decimalComma = delimiter != ',' && sample.any { line -> split(line, delimiter).drop(1).any { it.matches(Regex("-?\\d+,\\d+")) } }

        val points = mutableListOf<CsvPoint>()
        val skipped = mutableListOf<SkippedLine>()
        val seen = HashSet<String>()
        var firstDataLineSeen = false
        lines.forEachIndexed { index, raw ->
            val lineNumber = index + 1
            if (raw.isBlank()) return@forEachIndexed
            val fields = split(raw, delimiter)
            if (fields.size < 3) {
                skipped += SkippedLine(lineNumber, raw, COLUMNS_REASON); return@forEachIndexed
            }
            val name = fields[0]
            val a = number(fields[1], decimalComma)
            val b = number(fields[2], decimalComma)
            if (a == null || b == null) {
                val reason = if (!firstDataLineSeen && points.isEmpty()) HEADER_REASON else NUMBER_REASON
                skipped += SkippedLine(lineNumber, raw, reason); firstDataLineSeen = true; return@forEachIndexed
            }
            firstDataLineSeen = true
            if (name.isBlank()) { skipped += SkippedLine(lineNumber, raw, NAME_REASON); return@forEachIndexed }
            val z = fields.getOrNull(3)?.let { if (it.isBlank()) null else number(it, decimalComma) }
            val zIsCode = fields.size >= 4 && fields[3].isNotBlank() && z == null
            val code = when {
                zIsCode -> fields.drop(3).joinToString(" ").trim()
                else -> fields.drop(4).joinToString(" ").trim()
            }
            if (!seen.add(name)) { skipped += SkippedLine(lineNumber, raw, DUPLICATE_REASON); return@forEachIndexed }
            val (x, y) = if (order == CsvColumnOrder.NAME_X_Y_Z) a to b else b to a
            points += CsvPoint(name, x, y, z, code)
        }
        return CsvImportResult(points, skipped, delimiter, decimalComma)
    }

    /** Semicolon-separated, '.' decimals, with header: opens correctly in French/Arabic Excel and re-imports here. */
    fun write(points: List<CsvPoint>, decimals: Int = 4): String = buildString {
        append("Nom;X;Y;Z;Code\n")
        points.forEach { p ->
            append(clean(p.name)).append(';')
            append(fmt(p.x, decimals)).append(';')
            append(fmt(p.y, decimals)).append(';')
            append(p.z?.let { fmt(it, decimals) } ?: "").append(';')
            append(clean(p.code)).append('\n')
        }
    }

    private fun fmt(v: Double, d: Int) = "%.${d}f".format(Locale.US, v)
    private fun clean(s: String) = s.replace(';', ',').replace('\n', ' ').replace('\r', ' ').trim()

    /** "P1 1234,56 4567,89" — whitespace separated with decimal commas (not comma separated). */
    private fun looksSpaceSeparatedWithDecimalComma(sample: List<String>): Boolean =
        sample.any { line -> line.trim().split(Regex("\\s+")).count { it.matches(Regex("-?\\d+,\\d+")) } >= 2 }

    private fun split(line: String, delimiter: Char): List<String> {
        val parts = if (delimiter == ' ') line.trim().split(Regex("\\s+")) else line.split(delimiter)
        return parts.map { it.trim().removeSurrounding("\"").trim() }
    }

    private fun number(text: String, decimalComma: Boolean): Double? {
        val t = if (decimalComma) text.replace(',', '.') else text
        return t.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
