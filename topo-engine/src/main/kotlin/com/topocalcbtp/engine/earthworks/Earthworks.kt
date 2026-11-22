package com.topocalcbtp.engine.earthworks

/** Average end-area volume between consecutive cross-sections. Positive cut/fill are reported separately. */
data class SectionArea(val chainageMetres:Double,val cutAreaSquareMetres:Double,val fillAreaSquareMetres:Double) {
    init { require(chainageMetres.isFinite()); require(cutAreaSquareMetres>=0 && cutAreaSquareMetres.isFinite()); require(fillAreaSquareMetres>=0 && fillAreaSquareMetres.isFinite()) }
}
data class EarthworkVolume(val startChainageMetres:Double,val endChainageMetres:Double,val cutCubicMetres:Double,val fillCubicMetres:Double)
data class EarthworkSummary(val intervals:List<EarthworkVolume>,val totalCutCubicMetres:Double,val totalFillCubicMetres:Double,val netFillMinusCutCubicMetres:Double)
object Earthworks {
    fun averageEndArea(sections:List<SectionArea>):EarthworkSummary {
        require(sections.size>=2) { "At least two cross-sections are required" }
        require(sections.zipWithNext().all { it.first.chainageMetres < it.second.chainageMetres }) { "Chainages must be strictly increasing" }
        val intervals=sections.zipWithNext().map { (a,b) ->
            val d=b.chainageMetres-a.chainageMetres
            EarthworkVolume(a.chainageMetres,b.chainageMetres,(a.cutAreaSquareMetres+b.cutAreaSquareMetres)*0.5*d,(a.fillAreaSquareMetres+b.fillAreaSquareMetres)*0.5*d)
        }
        val cut=intervals.sumOf{it.cutCubicMetres}; val fill=intervals.sumOf{it.fillCubicMetres}
        return EarthworkSummary(intervals,cut,fill,fill-cut)
    }
}
