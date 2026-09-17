package io.github.hatake716.ari

import org.junit.Assert.*
import org.junit.Test

class NestGrowthTest {
    @Test fun everyBlueprintContinuesPastTheFormerRoomAndDepthLimits() {
        for(shape in 0..2)for(size in 0..2) {
            val nest=Nest.create(shape,size)
            assertEquals("巣口",nest.roomName(nest.room(0)))
            nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.STONE)
            val original=nest.chambers.map {it.copy()};val barriers=nest.obstacles.toList()
            repeat(160-4) {
                val room=NestGrowth.plan(nest)
                assertNotNull("shape=$shape size=$size rooms=${nest.chambers.size-1}",room)
                assertTrue(nest.path(0,room!!.id).isNotEmpty())
                assertTrue(nest.path(room.id,0).isNotEmpty()) // Miners must be able to leave unfinished galleries.
                room.built=1.0
            }
            assertEquals(160,nest.completedRooms);assertTrue(nest.depth>3.2)
            assertNotNull(NestGrowth.plan(nest));assertEquals(barriers,nest.obstacles)
            assertEquals(original,nest.chambers.take(5))
            nest.chambers.forEach {assertTrue(nest.path(0,it.id).isNotEmpty())}
        }
    }
    @Test fun aNaturalColonyAddsManyRoomsAsItsPopulationGrows() {
        val c=Colony(Nest.create(),812).apply {
            nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.TWIG)
            nest.obstacles+=Obstacle(2,4,.5,ObstacleKind.STONE)
        }
        val rooms=mutableListOf<Int>()
        for(day in listOf(100,200,400,600,800)) {
            c.advanceDays(day-c.day);rooms+=c.nest.completedRooms
            println("GROWTH day=${c.day.toInt()} workers=${c.workers} population=${c.population} rooms=${c.nest.completedRooms} depth=${c.nest.depth}")
            assertFalse(c.terminal)
        }
        assertEquals(4,rooms.first());assertTrue(rooms[2]>rooms.first()*2);assertTrue(rooms.last()>=30)
        assertTrue(rooms.zipWithNext().all {(a,b)->b>=a})
        assertTrue(c.journal.any {it.text.contains("完成")})
    }
    @Test fun constructionOpensPassageBeforeChamberAndSurvivesReload() {
        val c=Colony(Nest.create(),71).apply {nextRaid=1e9;phase=Phase.GROWING;day=60.0;adults+=WorkerCohort(250);food=500.0}
        c.advanceDays(3.0)
        val room=c.nest.construction.single()
        assertTrue(room.built in .01.. .55);assertEquals(0.0,NestGrowth.chamberProgress(room),0.0)
        assertTrue(NestGrowth.point(c.nest,room.id).distance(room.point)>.01)
        val restored=StateCodec.decode(StateCodec.encode(c))
        assertEquals(NestGrowth.point(c.nest,room.id),NestGrowth.point(restored.nest,room.id))
        c.advanceDays(80.0);restored.advanceDays(80.0)
        assertEquals(StateCodec.encode(c),StateCodec.encode(restored))
        assertTrue(room.built>=1);assertTrue(c.nest.completedRooms>8)
    }
    @Test fun olderSaveAndDeepNestBothRoundTripWithoutMovingTheQueensRoom() {
        val legacy=Colony(Nest.create(),23)
        assertEquals(StateCodec.encode(legacy),StateCodec.encode(StateCodec.decode(StateCodec.encode(legacy))))
        repeat(34){NestGrowth.plan(legacy.nest)!!.built=1.0}
        val saved=StateCodec.encode(legacy);val restored=StateCodec.decode(saved)
        assertEquals(saved,StateCodec.encode(restored));assertTrue(restored.nest.depth>1.5)
        assertEquals(legacy.nest.room(4),restored.nest.room(4))
    }
    @Test fun winterAndDefenseDivertLaborFromExcavation() {
        fun colony(day:Double)=Colony(Nest.create(),41).apply {this.day=day;phase=Phase.GROWING;nextRaid=1e9;food=600.0;adults+=WorkerCohort(250)}
        val warm=colony(60.0);val cold=colony(240.0)
        warm.advanceDays(15.0);cold.advanceDays(15.0)
        assertTrue(warm.nest.chambers.sumOf {it.built}>cold.nest.chambers.sumOf {it.built})
        val work=warm.builders;warm.spawnRaid(EnemyKind.BEETLE)
        assertTrue(warm.builders<work)
        val empty=Colony(Nest.create(),1).apply {nextRaid=1e9};empty.advanceDays(60.0)
        assertEquals(4,empty.nest.completedRooms)
    }
    @Test fun demandHasNoFixedCeilingAndLargeCurvedNestsKeepGrowingAfterReload() {
        assertTrue(NestGrowth.demand(1600,3000)>48)
        assertTrue(NestGrowth.demand(32000,60000)>1000)
        assertTrue(NestGrowth.demand(Int.MAX_VALUE,Int.MAX_VALUE)>1000)
        val c=Colony(Nest.create(),44)
        repeat(1024-4){assertNotNull(NestGrowth.plan(c.nest)?.also {it.built=1.0})}
        val restored=StateCodec.decode(StateCodec.encode(c))
        assertEquals(StateCodec.encode(c),StateCodec.encode(restored))
        repeat(20){NestGrowth.plan(c.nest)!!.built=1.0;NestGrowth.plan(restored.nest)!!.built=1.0}
        assertEquals(StateCodec.encode(c),StateCodec.encode(restored))
        assertTrue(c.nest.tunnels.count {it.bend!=0.0}>1000)
        assertTrue(c.nest.chambers.any {it.x<0 || it.x>1})
        val edge=c.nest.tunnels.last()
        val middle=c.nest.tunnelPoint(edge.a,edge.b,.5)
        assertTrue(middle.distance(c.nest.room(edge.a).point.mix(c.nest.room(edge.b).point,.5))>1e-6)
        assertEquals(middle,c.nest.tunnelPoint(edge.b,edge.a,.5))
        val completed=c.nest.room(edge.b)
        completed.built=.25
        assertEquals(c.nest.tunnelPoint(edge.a,edge.b,.25/NestGrowth.TUNNEL_SHARE),NestGrowth.point(c.nest,edge.b))
        val camera=NestCamera().apply {fit(c.nest);zoom=maxZoom;clamp(1080,1400)}
        assertEquals(10.0,camera.scale.toDouble(),1e-5)
    }
    @Test fun occupiedChambersWidenWithoutMovingDefensesOrTheQueen() {
        val nest=Nest.create()
        val queen=nest.room(nest.queenRoom).point
        nest.obstacles+=Obstacle(2,4,.5,ObstacleKind.STONE)
        val original=nest.chambers.sumOf {it.radius}
        repeat(600){NestGrowth.enlarge(nest,80.0,600,it.toLong())}
        assertTrue(nest.chambers.sumOf {it.radius}>original)
        assertEquals(queen,nest.room(nest.queenRoom).point)
        assertEquals(1,nest.obstacles.size)
        assertTrue(nest.chambers.filter {it.id!=0}.all {NestGrowth.halfWidth(it)>NestGrowth.halfHeight(it)})
        val legacy=org.json.JSONObject(StateCodec.encode(Colony(nest,9)))
        val edges=legacy.getJSONObject("nest").getJSONArray("tunnels")
        for(i in 0 until edges.length())edges.getJSONObject(i).remove("bend")
        assertTrue(StateCodec.decode(legacy.toString()).nest.tunnels.all {it.bend==0.0})
    }
    @Test fun deepCameraMapsTapsAndPanToTheSameWorldCoordinates() {
        val camera=NestCamera().apply {depth=2.8}
        val width=1080;val height=1400
        listOf(Point(.5,.115),Point(.2,1.5),Point(.7,2.6)).forEach {p->
            val screen=camera.toScreen(p,width,height)
            assertTrue(screen.y in 0.0..height.toDouble())
            val restored=camera.fromScreen(screen.x.toFloat(),screen.y.toFloat(),width,height)
            assertEquals(p.x,restored.x,1e-6);assertEquals(p.y,restored.y,1e-6)
        }
        camera.zoom=7f;camera.panX=99999f;camera.panY=-99999f;camera.clamp(width,height)
        val p=Point(.7,2.6);val screen=camera.toScreen(p,width,height)
        assertEquals(p.y,camera.fromScreen(screen.x.toFloat(),screen.y.toFloat(),width,height).y,1e-6)
        camera.reset();assertEquals(1f,camera.zoom);assertEquals(0f,camera.panY)
    }
}
