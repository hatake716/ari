package io.github.hatake716.ari

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SeasonAndGaitTest {
    @Test fun allMonthBoundariesFollowNonLeapCalendar() {
        val lengths=intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
        var day=245.0 // First January 1 after the May 1 founding.
        for(month in 1..12) {
            assertEquals(SeasonDate(month,1),SeasonCalendar.date(day))
            assertEquals(SeasonDate(month,lengths[month-1]),SeasonCalendar.date(day+lengths[month-1]-.000001))
            day+=lengths[month-1]
        }
        assertEquals(SeasonDate(1,1),SeasonCalendar.date(day))
        assertEquals(SeasonDate(5,1),SeasonCalendar.date(0.0))
        assertEquals(SeasonDate(5,31),SeasonCalendar.date(30.999))
        assertEquals(SeasonDate(6,1),SeasonCalendar.date(31.0))
    }
    @Test fun calendarRepeatsAfter365DaysWithoutClockOrTimezone() {
        for(day in 0..364) {
            val date=SeasonCalendar.date(day+.7)
            for(year in 1..8) assertEquals(date,SeasonCalendar.date(day+.7+year*365))
        }
    }
    @Test fun legacySaveAndOfflineAdvanceResolveTheSameMonth() {
        val c=Colony(Nest.create(),17).apply {day=244.5;phase=Phase.GROWING;nextRaid=1e9;lastSavedMillis=1000;speed=604800}
        val saved=StateCodec.encode(c)
        assertTrue(saved.contains("\"version\":1"))
        val copy=StateCodec.decode(saved)
        assertEquals(SeasonDate(12,31),SeasonCalendar.date(copy.day))
        copy.advanceDays(StateCodec.offlineDays(copy,86_401_000))
        assertEquals(SeasonDate(1,1),SeasonCalendar.date(copy.day))
        assertEquals(SeasonDate(12,31),SeasonCalendar.date(c.day))
        copy.speed=0
        assertEquals(0.0,StateCodec.offlineDays(copy,172_801_000),0.0)
    }
    @Test fun twoTripodsHaveOppositeStanceWithThreeLegsEach() {
        val phase=.4
        assertEquals(AntGait.legPhase(phase,-1,0),AntGait.legPhase(phase,-1,2),1e-9)
        assertEquals(AntGait.legPhase(phase,-1,0),AntGait.legPhase(phase,1,1),1e-9)
        var positive=0
        for(side in intArrayOf(-1,1)) for(leg in 0..2) {
            if(sin(AntGait.legPhase(phase,side,leg))>0)positive++
            assertEquals(-sin(AntGait.legPhase(phase,side,leg)),sin(AntGait.legPhase(phase,-side,leg)),1e-9)
        }
        assertEquals(3,positive)
    }
    @Test fun slowerTravelProducesSlowerFootfallsAndNoTravelKeepsPose() {
        val nest=Nest.create()
        nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.STONE)
        val free=.003
        val impeded=free*nest.localSpeed(0,1,.5,false)
        assertTrue(AntGait.phase(impeded)<AntGait.phase(free))
        assertEquals(0.0,AntGait.phase(AntGait.STRIDE),1e-8)
    }
    @Test fun headingTurnsAcrossZeroByTheShortArc() {
        val turn=AntGait.turn(350f,10f,.05)
        assertTrue(turn>350f && turn<370f)
        assertEquals(350f,AntGait.turn(350f,10f,0.0),0f)
        assertTrue(AntGait.turn(10f,350f,.05)<10f)
    }
}
