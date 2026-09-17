package io.github.hatake716.ari

import kotlin.math.*

/** Game geometry, not a fitted excavation rate or a measurement of a particular species. */
object NestGrowth {
    const val MAX_ROOMS = 48 // Habitable rooms; the surface entrance is additional.
    const val MAX_DEPTH = 3.2
    const val TUNNEL_SHARE = .55
    fun chamberProgress(room: Chamber) = ((room.built - TUNNEL_SHARE) / (1 - TUNNEL_SHARE)).coerceIn(0.0, 1.0)
    fun demand(workers: Int, population: Int) = max(4 + ((workers - 12).coerceAtLeast(0) + 31) / 32,
        ceil(population / 105.0).toInt()).coerceIn(4, MAX_ROOMS)

    fun point(nest: Nest, id: Int): Point {
        val room = nest.room(id)
        if (room.built >= TUNNEL_SHARE) return room.point
        val edge = nest.tunnels.firstOrNull { it.b == id || it.a == id } ?: return room.point
        val parent = nest.room(if (edge.b == id) edge.a else edge.b)
        return parent.point.mix(room.point, room.built / TUNNEL_SHARE)
    }
    fun distanceToSegment(p: Point, a: Point, b: Point): Double {
        val dx=b.x-a.x;val dy=b.y-a.y
        val t=(((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy).coerceAtLeast(1e-12)).coerceIn(0.0,1.0)
        return p.distance(a.mix(b,t))
    }
    private fun crosses(a:Point,b:Point,c:Point,d:Point):Boolean {
        fun side(p:Point,q:Point,r:Point)=(q.x-p.x)*(r.y-p.y)-(q.y-p.y)*(r.x-p.x)
        return side(a,b,c)*side(a,b,d)<-1e-10 && side(c,d,a)*side(c,d,b)<-1e-10
    }
    /** Deterministic frontier search keeps the original rooms and defenses intact. */
    fun plan(nest: Nest): Chamber? {
        if(nest.chambers.count {it.id!=0}>=MAX_ROOMS)return null
        val parents=nest.chambers.filter {it.id!=0 && it.built>=1}
        val candidates=buildList {
            parents.forEach {parent->
                listOf(Point(.18,.13),Point(-.18,.13),Point(0.0,.19),Point(.24,.015),Point(-.24,.015)).forEach {delta->
                    add(Point(parent.x+delta.x,parent.y+delta.y))
                }
            }
            for(row in 0..16)for(col in 0..4)add(Point(.12+col*.19,.30+row*.17))
        }.filter {p->p.x in .10.. .90 && p.y in .27..MAX_DEPTH && nest.chambers.none {it.point.distance(p)<.145}}
        val choices=candidates.mapNotNull {p->
            val parent=parents.sortedBy {it.point.distance(p)}.firstOrNull {r->
                r.point.distance(p)<=.36 && nest.chambers.none {it.id!=r.id && distanceToSegment(it.point,r.point,p)<it.radius+.027} &&
                    nest.tunnels.none {edge->edge.a!=r.id && edge.b!=r.id && crosses(r.point,p,nest.room(edge.a).point,nest.room(edge.b).point)}
            }
            parent?.let {Triple(p,it,p.y+abs(p.x-.5)*.12+it.point.distance(p)*.45)}
        }
        val selected=choices.minByOrNull {it.third} ?: return null
        val room=Chamber(nest.chambers.maxOf {it.id}+1,selected.first.x,selected.first.y,.064+nest.size*.006,.015)
        nest.chambers+=room;nest.tunnels+=Tunnel(selected.second.id,room.id)
        return room
    }
}

val Nest.completedRooms get() = chambers.count {it.id!=0 && it.built>=1}
val Nest.construction get() = chambers.filter {it.id!=0 && it.built<1}
val Nest.depth get() = max(1.0,chambers.maxOf {NestGrowth.point(this,it.id).y + .12})
fun Nest.roomName(room: Chamber): String = when {
    room.id==0 -> "巣口"
    room.id==queenRoom -> "女王室"
    room.id==1 -> "玄関室"
    room.id==2 -> "育児室"
    room.id==3 -> "貯蔵室"
    else -> listOf("休息室","育児室","貯蔵室")[(room.id-4).mod(3)]
}
