package crazydude.com.telemetry.maps

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil

abstract class MapLine {
    abstract fun remove()
    abstract fun addPoints(points: List<Position>)
    abstract fun setPoint(index: Int, position: Position)
    abstract fun clear()
    abstract fun removeAt(index: Int)

    abstract val size:Int
    abstract var color:Int

    var lastLatLon  = LatLng(0.0,0.0)

    var spoints: MutableList<Position> =  mutableListOf()

    public fun submitPoints(points: List<Position>) {
        spoints.addAll(points)
    }

    private fun simplifySPoints(limit: Int)
    {
        if ( size == 0) {
            lastLatLon  = LatLng(0.0,0.0)
        }

        var threshold = 5
        if (limit > 1500) {
            if ((size + spoints.size) > 1500) {
                threshold = 10
            } else if ((size + spoints.size) > 3000) {
                threshold = 20
            } else if ((size + spoints.size) > 5000) {
                threshold = 30
            } else if ((size + spoints.size) > 7000) {
                threshold = 100
            }
        }

        spoints = spoints.filter { i->
            val ll = LatLng(i.lat, i.lon)
            val d = SphericalUtil.computeDistanceBetween(lastLatLon,ll)
            if ( d >= threshold) {
                lastLatLon = ll
                true
            } else {
                false
            }
        }.toMutableList()
    }

    public fun commitPoints(limit: Int) {
        simplifySPoints(limit);
        var toRemove = (size + spoints.size ) - limit;
        if ( toRemove >= size ){
            var fi = spoints.size - limit;
            if ( fi < 0 ) fi = 0;
            val subList = spoints.subList(fi, spoints.size).toList()
            clear()
            addPoints(subList)
        } else {
            for ( i in 1..toRemove) {
                removeAt(0)
            }
            addPoints(spoints)
            spoints.clear();
        }
    }
}