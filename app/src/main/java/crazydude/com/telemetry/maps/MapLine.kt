package crazydude.com.telemetry.maps

abstract class MapLine {
    abstract fun remove()
    abstract fun addPoints(points: List<Position>)
    abstract fun setPoint(index: Int, position: Position)
    abstract fun clear()
    abstract fun removeAt(index: Int)

    abstract val size:Int
    abstract var color:Int

    var spoints: MutableList<Position> =  mutableListOf()

    public fun submitPoints(points: List<Position>) {
        spoints.addAll(points)
    }

    public fun commitPoints(limit: Int) {
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