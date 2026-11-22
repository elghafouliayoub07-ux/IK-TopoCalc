package com.topocalcbtp

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Types of saved calculations. */
object CalcType {
    const val LEVELING = "LEVELING"
    const val THEODOLITE = "THEODOLITE"
    const val TRAVERSE = "TRAVERSE"
    const val STAKEOUT = "STAKEOUT"
}

/** Inputs of a screen, saved as text: named fields plus table rows. */
class Record(val type: String) {
    val fields = LinkedHashMap<String, String>()
    val rows = mutableListOf<List<String>>()
    operator fun get(key: String): String? = fields[key]
    operator fun set(key: String, value: String) {
        require(key != "type" && key != "row" && key.isNotBlank()) { "Reserved key: $key" }
        fields[key] = value
    }
    fun text(key: String): String = fields[key] ?: ""
}

/**
 * Line format: "type=X", then "key=value" lines, then "row=a;b;c" lines.
 * '%', ';', '=', CR and LF are percent-escaped so any text round-trips.
 */
object RecordCodec {
    fun encode(record: Record): String = buildString {
        append("type=").append(esc(record.type)).append('\n')
        record.fields.forEach { (k, v) -> append(esc(k)).append('=').append(esc(v)).append('\n') }
        record.rows.forEach { row -> append("row=").append(row.joinToString(";") { esc(it) }).append('\n') }
    }

    fun decode(text: String): Record? {
        val lines = text.lines().filter { it.isNotEmpty() }
        val first = lines.firstOrNull() ?: return null
        if (!first.startsWith("type=")) return null
        val record = Record(unesc(first.removePrefix("type=")))
        lines.drop(1).forEach { line ->
            val i = line.indexOf('=')
            if (i <= 0) return@forEach
            val key = unesc(line.substring(0, i))
            val value = line.substring(i + 1)
            if (key == "row") record.rows += value.split(';').map { unesc(it) }
            else if (key != "type") record.fields[key] = unesc(value)
        }
        return record
    }

    private fun esc(s: String) = s.replace("%", "%25").replace(";", "%3B").replace("=", "%3D")
        .replace("\n", "%0A").replace("\r", "%0D")

    private fun unesc(s: String): String {
        if (!s.contains('%')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 3 <= s.length) {
                val code = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) { sb.append(code.toChar()); i += 3; continue }
            }
            sb.append(c); i++
        }
        return sb.toString()
    }
}

object TextFiles {
    /** UTF-8 when valid (BOM removed), otherwise Windows-1252 (CSV files saved by Excel on Windows). */
    fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
        return text.removePrefix("﻿")
    }

    /** UTF-8 with BOM so that Excel shows accents and Arabic correctly. */
    fun encodeForExcel(text: String): ByteArray = ("﻿" + text).toByteArray(Charsets.UTF_8)
}
