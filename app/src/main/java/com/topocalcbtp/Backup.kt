package com.topocalcbtp

import org.json.JSONArray
import org.json.JSONObject

/** One project read from a backup file (database ids are not kept; points/calculations get new ids). */
data class BackupProject(
    val backupId: String,
    val name: String,
    val createdAt: Long,
    val points: List<ProjectPoint>,
    val calculations: List<SavedCalculation>
)

data class ParsedBackup(
    val projects: List<BackupProject>,
    val activeBackupId: String?,
    /** Calculations saved by the HTML version (different format): their points are restored, not the calculation. */
    val skippedCalculations: Int
)

/**
 * Backup file shared with the HTML version (app "TopoCalcBTP", format 1): projects and their points open in both
 * versions; saved calculations reopen in the version that created them.
 */
object Backup {
    const val APP_ID = "TopoCalcBTP"

    fun toJson(data: List<Triple<SurveyProject, List<ProjectPoint>, List<SavedCalculation>>>, activeProjectId: Long?): String {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("format", 1)
        root.put("source", "android")
        root.put("exportedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
        root.put("activeProjectId", activeProjectId?.toString() ?: JSONObject.NULL)
        val projects = JSONArray()
        data.forEach { (p, points, calcs) ->
            val jp = JSONObject()
            jp.put("id", p.id.toString())
            jp.put("name", p.name)
            jp.put("createdAt", p.createdAt)
            val jpts = JSONArray()
            points.forEach { pt ->
                jpts.put(JSONObject().apply {
                    put("id", "a" + pt.id)
                    put("name", pt.name); put("x", pt.x); put("y", pt.y)
                    put("z", pt.z ?: JSONObject.NULL)
                    put("code", pt.code); put("source", pt.source)
                })
            }
            jp.put("points", jpts)
            val jc = JSONArray()
            calcs.forEach { c ->
                jc.put(JSONObject().apply {
                    put("id", "a" + c.id)
                    put("type", c.type); put("title", c.title); put("summary", c.summary)
                    put("createdAt", c.createdAt); put("payload", c.payload)
                })
            }
            jp.put("calcs", jc)
            projects.put(jp)
        }
        root.put("projects", projects)
        return root.toString(1)
    }

    /** Throws IllegalArgumentException when the text is not a backup of this app. */
    fun parse(text: String): ParsedBackup {
        val root = try { JSONObject(text.removePrefix("﻿")) } catch (e: Exception) { throw IllegalArgumentException("invalid JSON") }
        require(root.optString("app") == APP_ID) { "not an IK TopoCalc backup" }
        val arr = root.optJSONArray("projects") ?: throw IllegalArgumentException("no projects")
        var skipped = 0
        val projects = (0 until arr.length()).map { i ->
            val jp = arr.getJSONObject(i)
            val jpts = jp.optJSONArray("points") ?: JSONArray()
            val points = (0 until jpts.length()).mapNotNull { k ->
                val o = jpts.optJSONObject(k) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                val x = o.optDouble("x"); val y = o.optDouble("y")
                if (name.isEmpty() || !x.isFinite() || !y.isFinite()) return@mapNotNull null
                val z = if (o.isNull("z")) null else o.optDouble("z").takeIf { it.isFinite() }
                ProjectPoint(projectId = 0, name = name, x = x, y = y, z = z, code = o.optString("code"), source = o.optString("source").ifBlank { "MANUEL" })
            }
            val jc = jp.optJSONArray("calcs") ?: JSONArray()
            val calcs = (0 until jc.length()).mapNotNull { k ->
                val o = jc.optJSONObject(k) ?: return@mapNotNull null
                val payload = o.optString("payload")
                if (payload.isBlank()) { skipped++; return@mapNotNull null }
                SavedCalculation(projectId = 0, type = o.optString("type"), title = o.optString("title"),
                    summary = o.optString("summary"), payload = payload, createdAt = o.optLong("createdAt", System.currentTimeMillis()))
            }
            BackupProject(jp.optString("id", "p$i"), jp.optString("name").ifBlank { "Projet ${i + 1}" },
                jp.optLong("createdAt", System.currentTimeMillis()), points, calcs)
        }
        val active = if (root.isNull("activeProjectId")) null else root.optString("activeProjectId")
        return ParsedBackup(projects, active, skipped)
    }
}
