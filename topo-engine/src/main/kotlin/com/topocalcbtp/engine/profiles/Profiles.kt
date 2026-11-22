package com.topocalcbtp.engine.profiles

import com.topocalcbtp.engine.coordinates.Point2D
import com.topocalcbtp.engine.terrain.TinSurface
import kotlin.math.hypot

data class ProfilePoint(val chainageMetres:Double,val x:Double,val y:Double,val groundElevationMetres:Double)
data class LongitudinalProfile(val points:List<ProfilePoint>,val totalLengthMetres:Double)

object Profiles {
    /** Samples a polyline against a TIN at a fixed maximum interval. */
    fun longitudinal(alignment:List<Point2D>,tin:TinSurface,intervalMetres:Double):LongitudinalProfile {
        require(alignment.size>=2) { "Alignment needs at least two vertices" }
        require(intervalMetres>0.0 && intervalMetres.isFinite()) { "Interval must be positive" }
        val out= mutableListOf<ProfilePoint>(); var chain=0.0
        for(i in 0 until alignment.lastIndex) {
            val a=alignment[i]; val b=alignment[i+1]; val len=hypot(b.x-a.x,b.y-a.y)
            require(len>1e-9) { "Alignment contains a zero-length segment" }
            val steps=kotlin.math.ceil(len/intervalMetres).toInt().coerceAtLeast(1)
            for(j in 0..steps) {
                if(i>0 && j==0) continue
                val f=j.toDouble()/steps; val x=a.x+f*(b.x-a.x); val y=a.y+f*(b.y-a.y)
                val z=tin.interpolate(x,y) ?: throw IllegalArgumentException("Alignment sample outside TIN at X=$x Y=$y")
                out.add(ProfilePoint(chain+f*len,x,y,z))
            }
            chain+=len
        }
        return LongitudinalProfile(out,chain)
    }
}
