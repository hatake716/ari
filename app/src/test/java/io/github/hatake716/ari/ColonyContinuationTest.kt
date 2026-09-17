package io.github.hatake716.ari

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ColonyContinuationTest {
    private fun departed()=Colony(Nest.create(),812).apply {
        day=1180.0;phase=Phase.CLEARED;completedFlights=1;flightProgress=1.0
        youngQueens=4;males=8;royalLaid=true;royalAdultDay=1160.0;nextRoyalDay=1460.0
        adults+=WorkerCohort(220,100.0);brood+=Brood(35,21.0);food=500.0
        nextRaid=2000.0;repelled=8;losses=21;queenHealth=94.0
        nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.STONE)
    }
    @Test fun continuationKeepsMotherNestClockAndPopulationButRemovesDepartedAlates() {
        val c=departed();val nest=c.nest;val adults=c.adults.map{it.copy()};val brood=c.brood.map{it.copy()}
        c.speed=0;c.advanceDays(100.0);assertEquals(1180.0,c.day,0.0)
        assertTrue(c.continueObservation());assertSame(nest,c.nest)
        assertEquals(adults,c.adults);assertEquals(brood,c.brood)
        assertEquals(1180.0,c.day,0.0);assertEquals(500.0,c.food,0.0);assertEquals(94.0,c.queenHealth,0.0)
        assertEquals(8,c.repelled);assertEquals(21,c.losses);assertEquals(2000.0,c.nextRaid,0.0)
        assertEquals(1,c.completedFlights);assertEquals(1460.0,c.nextRoyalDay,0.0)
        assertEquals(0,c.youngQueens);assertEquals(0,c.males);assertFalse(c.royalLaid)
        assertEquals(Phase.GROWING,c.phase);assertEquals(1,c.speed)
        assertFalse(c.continueObservation());c.advanceDays(1.0);assertTrue(c.day>1180)
    }
    @Test fun naturalColonyCanRaiseAndLaunchThreeGenerationsAcrossReloads() {
        var c=Colony(Nest.create(),812).apply {
            nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.TWIG)
            nest.obstacles+=Obstacle(2,4,.5,ObstacleKind.STONE)
        }
        var lastLayingDay=-365.0
        repeat(3) { generation ->
            c.advanceDays(1800.0)
            println("CONTINUATION flight=${c.completedFlights} phase=${c.phase} day=${c.day} rooms=${c.nest.completedRooms} health=${c.queenHealth}")
            assertEquals(Phase.CLEARED,c.phase);assertEquals(generation+1,c.completedFlights)
            assertTrue(c.queenHealth>0);assertTrue(c.youngQueens>0)
            val layingDay=c.nextRoyalDay-365
            assertTrue(layingDay-lastLayingDay>=365-1e-6);lastLayingDay=layingDay
            val stopped=c.day;c.advanceDays(365.0);assertEquals(stopped,c.day,0.0)
            val pausedReload=StateCodec.decode(StateCodec.encode(c))
            assertEquals(StateCodec.encode(c),StateCodec.encode(pausedReload))
            assertTrue(pausedReload.continueObservation())
            val reload=StateCodec.decode(StateCodec.encode(pausedReload))
            pausedReload.advanceDays(10.0);reload.advanceDays(10.0)
            assertEquals(StateCodec.encode(pausedReload),StateCodec.encode(reload));c=reload
        }
    }
    @Test fun nextRoyalBroodWaitsForIntervalAndAdequateWorkers() {
        val c=departed().apply {nextRaid=1e9;nextRoyalDay=day+40}
        c.continueObservation();c.advanceDays(10.0);assertFalse(c.royalLaid)
        c.day=c.nextRoyalDay;c.adults.clear();c.adults+=WorkerCohort(20);c.brood.clear()
        c.advanceDays(Colony.STEP);assertFalse(c.royalLaid)
        c.adults+=WorkerCohort(100);c.food=500.0;c.advanceDays(Colony.STEP)
        assertTrue(c.royalLaid);assertEquals(Phase.REPRODUCTIVE,c.phase)
        assertEquals(c.day+365,c.nextRoyalDay,1e-7)
    }
    @Test fun lifespanIsStableInRangeWithoutConsumingRandomness() {
        for(seed in listOf(0L,1L,812L,3650L,3651L,Long.MIN_VALUE,Long.MAX_VALUE)) {
            val c=Colony(Nest.create(),seed)
            assertEquals(seed,c.rngState);assertTrue(c.queenLifespanDays in 3650.0..7300.0)
            val expected=c.queenLifespanDays;c.random()
            assertEquals(expected,StateCodec.decode(StateCodec.encode(c)).queenLifespanDays,0.0)
        }
    }
    @Test fun queenAgesOutWithoutDestroyingNestAndCannotResume() {
        val c=departed();c.continueObservation();c.queenLifespanDays=3650.0;c.day=3650.0-Colony.STEP
        val nest=JSONObject(StateCodec.encode(c)).getJSONObject("nest").toString()
        c.advanceDays(1.0)
        assertEquals(Phase.LOST,c.phase);assertTrue(c.queenDiedOfAge);assertEquals(0.0,c.queenHealth,0.0)
        assertEquals(3650.0,c.day,1e-6);assertEquals(nest,JSONObject(StateCodec.encode(c)).getJSONObject("nest").toString())
        assertFalse(c.continueObservation())
        assertEquals(StateCodec.encode(c),StateCodec.encode(StateCodec.decode(StateCodec.encode(c))))
        val prey=departed().apply {phase=Phase.LOST;queenHealth=0.0};assertFalse(prey.continueObservation())
    }
    @Test fun legacyClearCanResumeAndOfflineTimeRespectsTheEndingChoice() {
        val json=JSONObject(StateCodec.encode(departed()))
        listOf("completedFlights","nextRoyalDay","queenLifespanDays","queenDiedOfAge").forEach{json.remove(it)}
        val c=StateCodec.decode(json.toString()).apply {lastSavedMillis=1000}
        assertEquals(1,c.completedFlights);assertEquals(7300.0,c.queenLifespanDays,0.0)
        assertEquals(c.day+365,c.nextRoyalDay,0.0)
        assertEquals(0.0,StateCodec.offlineDays(c,86_401_000),0.0)
        assertTrue(c.continueObservation());assertEquals(1.0,StateCodec.offlineDays(c,86_401_000),0.0)
        c.advanceDays(1.0);assertFalse(c.royalLaid)
    }
}
