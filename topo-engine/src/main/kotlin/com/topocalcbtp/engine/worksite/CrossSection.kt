package com.topocalcbtp.engine.worksite

/** One cross-section (profil en travers) with its cut / fill areas. */
data class SectionAreas(val name: String, val chainage: Double, val cutArea: Double, val fillArea: Double) {
    init { require(cutArea >= 0 && fillArea >= 0) { "Areas must be ≥ 0" } }
}

data class SectionVolume(val from: String, val to: String, val distance: Double, val volume: CutFill)

data class SectionsVolumeResult(val intervals: List<SectionVolume>, val total: CutFill)

object CrossSections {
    /**
     * Areas of one section from points across it: [x] = distance from the left end (or the axis),
     * [h] = ground − design at each point (+ = cut). Use design = constant for a platform or the bottom
     * of an excavation.
     */
    fun areas(x: List<Double>, h: List<Double>): CutFill = CutFillIntegration.integrate(x, h)

    /** Méthode de la moyenne des aires: V = (S1 + S2) / 2 × d between consecutive sections. */
    fun averageEndArea(sections: List<SectionAreas>): SectionsVolumeResult {
        require(sections.size >= 2) { "At least two sections are required" }
        require(sections.zipWithNext().all { it.second.chainage > it.first.chainage }) { "Section positions (PK) must increase" }
        val intervals = sections.zipWithNext().map { (a, b) ->
            val d = b.chainage - a.chainage
            SectionVolume(a.name, b.name, d, CutFill((a.cutArea + b.cutArea) / 2 * d, (a.fillArea + b.fillArea) / 2 * d))
        }
        return SectionsVolumeResult(intervals, intervals.fold(CutFill.ZERO) { acc, v -> acc + v.volume })
    }
}
