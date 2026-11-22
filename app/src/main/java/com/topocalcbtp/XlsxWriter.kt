package com.topocalcbtp

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal single-sheet .xlsx writer (Office Open XML) using only java.util.zip.
 * Replaces Apache POI, which is very heavy on Android and depends on JVM APIs Android lacks.
 * Cells are String (inline strings) or Number. Row 0 is rendered bold.
 */
object XlsxWriter {
    fun write(out: OutputStream, sheetName: String, rows: List<List<Any?>>, columnWidthChars: Double = 18.0) {
        val safeSheet = xml(sheetName.take(31).replace(Regex("[\\[\\]:*?/\\\\]"), "_"))
        val zip = ZipOutputStream(out)
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""")
        entry("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="$safeSheet" sheetId="1" r:id="rId1"/></sheets>
</workbook>""")
        entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""")
        entry("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="0.0000"/></numFmts>
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""")
        val sheet = StringBuilder()
        sheet.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val maxCols = rows.maxOfOrNull { it.size } ?: 0
        if (maxCols > 0) sheet.append("<cols><col min=\"1\" max=\"$maxCols\" width=\"$columnWidthChars\" customWidth=\"1\"/></cols>")
        sheet.append("<sheetData>")
        rows.forEachIndexed { r, row ->
            sheet.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, value ->
                val ref = columnName(c) + (r + 1)
                when (value) {
                    null -> Unit
                    is Number -> {
                        val d = value.toDouble()
                        if (d.isFinite()) sheet.append("<c r=\"$ref\" s=\"2\"><v>${java.math.BigDecimal(d).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}</v></c>")
                    }
                    else -> {
                        val style = if (r == 0) " s=\"1\"" else ""
                        sheet.append("<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${xml(value.toString())}</t></is></c>")
                    }
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        entry("xl/worksheets/sheet1.xml", sheet.toString())
        zip.finish()
    }

    internal fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun xml(s: String): String = buildString {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') append(ch)
            }
        }
    }
}
