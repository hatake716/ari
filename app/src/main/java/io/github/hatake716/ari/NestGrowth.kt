package io.github.hatake716.ari

import kotlin.math.*

/** Procedural cross section informed by the CC BY 4.0 reconstructions in docs/NEST_SOURCES.md.
 * Spacing, branching and labor coefficients are game design values, not a fitted species model. */
object NestGrowth {
    const val TUNNEL_SHARE = .55
    fun chamberProgress(room: Chamber) = ((room.built - TUNNEL_SHARE) / (1 - TUNNEL_SHARE)).coerceIn(0.0, 1.0)
    fun demand(workers: Int, population: Int) = max(4L + ((workers.toLong() - 12).coerceAtLeast(0) + 31) / 32,
        ceil(population / 105.0).toLong()).coerceAtLeast(4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Stable per-location variation; saves need no random geometry cache. */
    fun variation(id: Int, salt: Int): Double {
        var n=id.toLong()*374761393L+salt*668265263L
        n=(n xor (n ushr 13))*1274126177L
        return ((n xor (n ushr 16)) and 0xffff).toDouble()/65535
    }
    fun halfWidth(room: Chamber) = room.radius * (1.13 + .27 * variation(room.id, 3))
    fun halfHeight(room: Chamber) = room.radius * (.40 + .20 * variation(room.id, 7))
    fun outline(room: Chamber, angle: Double): Point {
        val lobes=.09 + (room.radius-.05).coerceIn(0.0,.06)*1.1
        val irregular=1 + sin(angle*3+room.id)*lobes + cos(angle*5-room.id)*.065 + sin(angle*13+room.id)*.018
        return Point(cos(angle)*halfWidth(room)*irregular,sin(angle)*halfHeight(room)*irregular)
    }
    fun point(nest: Nest, id: Int): Point {
        val room = nest.room(id)
        if (room.built >= TUNNEL_SHARE) return room.point
        val edge = nest.tunnels.firstOrNull { it.b == id || it.a == id } ?: return room.point
        val parent = if (edge.b == id) edge.a else edge.b
        return nest.tunnelPoint(parent,id,room.built/TUNNEL_SHARE)
    }
    fun visiblePoint(nest:Nest,a:Int,b:Int,t:Double):Point {
        val start=1-(nest.room(a).built/TUNNEL_SHARE).coerceAtMost(1.0)
        val end=(nest.room(b).built/TUNNEL_SHARE).coerceAtMost(1.0)
        return nest.tunnelPoint(a,b,start+(end-start)*t)
    }
    fun distanceToSegment(p: Point, a: Point, b: Point): Double {
        val dx=b.x-a.x;val dy=b.y-a.y
        val t=(((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy).coerceAtLeast(1e-12)).coerceIn(0.0,1.0)
        return p.distance(a.mix(b,t))
    }
    private fun crosses(a:Point,b:Point,c:Point,d:Point):Boolean {
        fun side(p:Point,q:Point,r:Point)=(q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
        return side(a,b,c)*side(a,b,d)<-1e-12 && side(c,d,a)*side(c,d,b)<-1e-12
    }
    /** Descending shafts with occasional lateral branches; no room/depth cap or regular grid. */
    fun plan(nest: Nest): Chamber? {
        val id=nest.chambers.maxOf {it.id}+1
        val radius=.048+nest.size*.006+variation(id,11)*.021
        val reach=.38+.045*sqrt(nest.chambers.size.toDouble())
        val degree=nest.tunnels.flatMap {listOf(it.a,it.b)}.groupingBy {it}.eachCount()
        val parents=nest.chambers.filter {it.id!=0 && it.built>=1}.sortedBy {it.y+abs(it.x-.5)*.65}
        val edges=nest.tunnels.map {edge->(0..8).map {nest.tunnelPoint(edge.a,edge.b,it/8.0)}}
        fun clear(parent:Chamber,room:Chamber,bend:Double):Boolean {
            val margin=halfWidth(room)*1.22
            if(nest.chambers.any {r->r.point.distance(room.point)<margin+halfWidth(r)*1.22+.018})return false
            if(edges.any {line->line.zipWithNext().any {(a,b)->distanceToSegment(room.point,a,b)<margin+.030}})return false
            val line=(0..8).map {Nest.curve(parent.point,room.point,it/8.0,bend)}
            if(nest.chambers.any {r->r.id!=parent.id && line.zipWithNext().any {(a,b)->distanceToSegment(r.point,a,b)<halfWidth(r)*1.22+.03}})return false
            return nest.tunnels.indices.none {i->
                val edge=nest.tunnels[i]
                edge.a!=parent.id && edge.b!=parent.id && line.zipWithNext().any {(a,b)->edges[i].zipWithNext().any {(c,d)->crosses(a,b,c,d)}}
            }
        }
        fun append(parent:Chamber,dx:Double,dy:Double):Chamber? {
            val room=Chamber(id,parent.x+dx,parent.y+dy,radius,.015)
            val bend=(variation(id+parent.id,23)-.5)*.042
            if(!clear(parent,room,bend))return null
            nest.chambers+=room;nest.tunnels+=Tunnel(parent.id,id,bend)
            return room
        }
        for(parent in parents) {
            val connections=degree[parent.id] ?: 0
            val branch=variation(parent.id,19)<.32
            if(connections>=if(branch)3 else 2)continue
            val direction=if(parent.x<.5)-1 else 1
            for(attempt in 0..5) {
                val u=variation(id+parent.id,31+attempt)
                val dx=if(connections>=2 || attempt>=3) direction*(.22+u*.12)*(if(attempt==5)-1 else 1)
                    else (u-.5)*.19
                val dy=if(connections>=2).16+variation(id,41+attempt)*.12 else .24+variation(id,41+attempt)*.16
                if(abs(parent.x+dx-.5)>reach)continue
                append(parent,dx,dy)?.let {return it}
            }
        }
        // Existing custom/legacy blueprints may have crowded frontiers. Continue below their deepest
        // completed room instead of silently imposing a new finite room limit.
        for(parent in parents.sortedByDescending {it.y}.take(8))for(attempt in 0..15) {
            val dx=(attempt%3-1)*(.24+.04*(attempt/3))
            append(parent,dx,.34+.10*(attempt/3))?.let {return it}
        }
        return null
    }
    /** Widen safe, occupied chambers gradually as well as adding new levels. Positions never move. */
    fun enlarge(nest:Nest,laborDays:Double,population:Int,step:Long) {
        if(population<nest.capacity*.65)return
        val rooms=nest.chambers.filter {it.id!=0 && it.built>=1}
        val delta=min(.0004,laborDays*.000012)
        if(delta<=0)return
        rooms.filterIndexed {index,_->index==(step.mod(rooms.size.coerceAtLeast(1)))}.forEach {room->
            val target=.078+nest.size*.006+variation(room.id,53)*.024
            if(room.radius>=target)return@forEach
            val next=room.copy(radius=min(target,room.radius+delta))
            val margin=halfWidth(next)*1.22+.022
            if(nest.chambers.any {it.id!=room.id && it.point.distance(room.point)<margin+halfWidth(it)*1.22})return@forEach
            if(nest.tunnels.any {edge->edge.a!=room.id && edge.b!=room.id &&
                (0..8).map {nest.tunnelPoint(edge.a,edge.b,it/8.0)}.zipWithNext().any {(a,b)->distanceToSegment(room.point,a,b)<margin}})return@forEach
            room.radius=next.radius
        }
    }
}

val Nest.completedRooms get() = chambers.count {it.id!=0 && it.built>=1}
val Nest.construction get() = chambers.filter {it.id!=0 && it.built<1}
val Nest.depth get() = max(1.0,chambers.maxOf {NestGrowth.point(this,it.id).y + .16})
val Nest.left get() = min(0.0,chambers.minOf {NestGrowth.point(this,it.id).x-NestGrowth.halfWidth(it)-.07})
val Nest.right get() = max(1.0,chambers.maxOf {NestGrowth.point(this,it.id).x+NestGrowth.halfWidth(it)+.07})
fun Nest.roomName(room: Chamber): String = when {
    room.id==0 -> "巣口"
    room.id==queenRoom -> "女王室"
    room.id==1 -> "玄関室"
    room.id==2 -> "育児室"
    room.id==3 -> "貯蔵室"
    else -> listOf("休息室","育児室","貯蔵室")[(room.id-4).mod(3)]
}
