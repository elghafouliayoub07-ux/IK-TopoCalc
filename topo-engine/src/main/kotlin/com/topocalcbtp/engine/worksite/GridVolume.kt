package com.topocalcbtp.engine.worksite

import kotlin.math.abs

data class GridCell(val row: Int, val col: Int, val meanHeight: Double, val volume: CutFill)

data class GridVolumeResult(
    val cells: List<GridCell>,
    val area: Double,
    val volume: CutFill,
    /** Design level giving cut = fill (cote d'équilibre) with the same method. */
    val balanceLevel: Double,
    val minGround: Double,
    val maxGround: Double
)

/**
 * Méthode des plans cotés: a grid of ground levels (null = no point), mesh [dx] × [dy].
 * Each complete cell is a prism of height (ha + hb + hc + hd) / 4 with h = ground − design.
 * A cell with both cut and fill is split with the usual transition formula
 * cut = S·(Σh⁺)² / (4·Σ|h|), fill = S·(Σh⁻)² / (4·Σ|h|), which keeps cut − fill = S·mean(h).
 */
object GridVolume {
    fun compute(ground: List<List<Double?>>, dx: Double, dy: Double, designZ: Double): GridVolumeResult {
        require(dx > 0 && dy > 0) { "Mesh size must be > 0" }
        require(designZ.isFinite()) { "Design level is not a number" }
        val s = dx * dy
        val cells = mutableListOf<GridCell>()
        var zSum = 0.0
        val all = ground.flatten().filterNotNull()
        require(all.isNotEmpty()) { "The grid is empty" }
        for (r in 0 until ground.size - 1) {
            val cols = minOf(ground[r].size, ground[r + 1].size)
            for (c in 0 until cols - 1) {
                val z = listOf(ground[r][c], ground[r][c + 1], ground[r + 1][c], ground[r + 1][c + 1])
                if (z.any { it == null }) continue
                val zs = z.map { it!! }
                val h = zs.map { it - designZ }
                val pos = h.filter { it > 0 }.sum()
                val neg = -h.filter { it < 0 }.sum()
                val v = when {
                    neg == 0.0 -> CutFill(s * pos / 4, 0.0)
                    pos == 0.0 -> CutFill(0.0, s * neg / 4)
                    else -> { val t = pos + neg; CutFill(s * pos * pos / (4 * t), s * neg * neg / (4 * t)) }
                }
                zSum += zs.average()
                cells += GridCell(r, c, h.average(), v)
            }
        }
        require(cells.isNotEmpty()) { "No complete mesh: at least 2 × 2 neighbouring points are needed" }
        val total = cells.fold(CutFill.ZERO) { acc, cell -> acc + cell.volume }
        check(abs(total.net - cells.sumOf { it.meanHeight } * s) < 1e-6 * (1 + abs(total.net)))
        return GridVolumeResult(cells, cells.size * s, total, zSum / cells.size, all.min(), all.max())
    }

    /**
     * Parses a grid typed row by row: values separated by spaces, tabs or ';'
     * ("-", "x" or "*" = no point). Decimal comma accepted when the separator is not a comma.
     */
    fun parse(text: String): List<List<Double?>> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { i, line ->
        val tokens = if (line.contains(';') || line.contains('\t')) line.split(';', '\t').map { it.trim() }.filter { it.isNotEmpty() }
                     else line.split(Regex("\\s+"))
        tokens.map { t ->
            if (t == "-" || t.equals("x", true) || t == "*") null
            else requireNotNull(t.replace(',', '.').toDoubleOrNull()) { "Line ${i + 1}: \"$t\" is not a number" }
        }
    }
}
