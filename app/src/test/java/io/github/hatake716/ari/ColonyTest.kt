package io.github.hatake716.ari

import org.junit.Assert.*
import org.junit.Test

class ColonyTest {
    private fun quiet(seed: Long=42) = Colony(Nest.create(),seed).apply {nextRaid=1e9}
    @Test fun queenArrivesBeforeEggsAndWorkers() {
        val c=quiet();assertEquals(Phase.ARRIVAL,c.phase);assertEquals(0,c.workers)
        c.advanceDays(.20);assertEquals(0,c.brood.size)
        c.advanceDays(.10);assertEquals(Phase.FOUNDING,c.phase)
        c.advanceDays(20.0);assertTrue(c.brood.isNotEmpty());assertEquals(0,c.workers)
    }
    @Test fun broodPassesEveryStageAndNeedsRealDevelopmentTime() {
        val b=Brood(1);assertEquals(BroodStage.EGG,b.stage)
        b.age=18.0;assertEquals(BroodStage.LARVA,b.stage)
        b.age=40.0;assertEquals(BroodStage.PUPA,b.stage)
        assertEquals(60.0,b.duration,0.0);assertEquals(80.0,Brood(1,royal=true).duration,0.0)
        val c=quiet();c.advanceDays(60.0);assertEquals(0,c.workers)
        c.advanceDays(30.0);assertTrue(c.workers>0);assertEquals(Phase.GROWING,c.phase)
    }
    @Test fun queenUsesReservesToFoundColonyWithoutStarterWorkers() {
        val c=quiet();c.advanceDays(100.0)
        assertTrue(c.workers>5);assertTrue(c.queenHealth>90);assertTrue(c.foundingReserve<12)
    }
    @Test fun winterSlowsGrowthAndStopsEggLaying() {
        val c=quiet().apply {day=240.0;phase=Phase.GROWING;brood+=Brood(5);food=200.0}
        assertTrue(c.winter);c.advanceDays(10.0)
        assertTrue(c.brood.first().age<3);assertEquals(5,c.brood.sumOf{it.count})
    }
    @Test fun oldWorkersDieAndNoNegativeCountsArePossible() {
        val c=quiet().apply {phase=Phase.GROWING;adults+=WorkerCohort(20,Colony.WORKER_LIFESPAN-.1)}
        c.advanceDays(.2);assertEquals(0,c.workers)
        c.removeWorkers(100);assertEquals(0,c.workers)
    }
    @Test fun allBlueprintsHaveConnectedQueenRoomAndIncreasingCapacity() {
        for(shape in 0..2) {
            val small=Nest.create(shape,0);val large=Nest.create(shape,2)
            assertTrue(large.capacity>small.capacity)
            large.chambers.forEach {assertTrue(large.path(0,it.id).isNotEmpty())}
        }
    }
    @Test fun branchRoomConnectsAndCannotEraseQueenOrDisconnectNest() {
        val n=Nest.create();assertTrue(n.addRoom(Point(.78,.80)));assertTrue(n.path(0,n.chambers.last().id).isNotEmpty())
        assertFalse(n.erase(n.room(n.queenRoom).point))
        assertTrue(n.erase(n.chambers.last().point));assertTrue(n.path(0,n.queenRoom).isNotEmpty())
    }
    @Test fun barriersSlowBothSpeciesAndStoneIsStronger() {
        val n=Nest.create();val edge=n.tunnels.last();val cleanWorker=n.cost(edge.a,edge.b,false);val cleanEnemy=n.cost(edge.a,edge.b,true)
        n.obstacles+=Obstacle(edge.a,edge.b,.5,ObstacleKind.TWIG)
        assertTrue(n.cost(edge.a,edge.b,false)>cleanWorker);assertTrue(n.cost(edge.a,edge.b,true)>cleanEnemy)
        val twig=n.cost(edge.a,edge.b,true)
        n.obstacles.clear();n.obstacles+=Obstacle(edge.a,edge.b,.5,ObstacleKind.STONE)
        assertTrue(n.cost(edge.a,edge.b,true)>twig)
        assertEquals(1.0,n.localSpeed(edge.a,edge.b,.1,false),0.0)
        assertTrue(n.localSpeed(edge.a,edge.b,.5,false)<1)
    }
    @Test fun obstaclePositionIsCorrectWhenWalkingTheOppositeDirection() {
        val n=Nest.create();n.obstacles+=Obstacle(0,1,.25,ObstacleKind.STONE)
        assertEquals(.44,n.localSpeed(1,0,.75,false),0.0)
        assertEquals(1.0,n.localSpeed(1,0,.25,false),0.0)
    }
    @Test fun obstacleOnUnusedBranchDoesNotChangeQueenRoute() {
        val n=Nest.create();val before=n.travelCost(n.path(0,n.queenRoom),true)
        n.obstacles+=Obstacle(1,3,.5,ObstacleKind.STONE)
        assertEquals(before,n.travelCost(n.path(0,n.queenRoom),true),0.00001)
        assertTrue(n.efficiency<1.0)
    }
    @Test fun moreBarriersReduceFoodTransport() {
        val clean=quiet().apply {phase=Phase.GROWING;adults+=WorkerCohort(100);food=100.0}
        val blocked=StateCodec.decode(StateCodec.encode(clean))
        blocked.nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.STONE)
        blocked.nest.obstacles+=Obstacle(1,2,.5,ObstacleKind.STONE)
        clean.advanceDays(5.0);blocked.advanceDays(5.0)
        assertTrue(clean.food>blocked.food)
    }
    @Test fun tooFewWorkersLoseQueenToPredator() {
        val c=quiet().apply {phase=Phase.GROWING}
        c.spawnRaid(EnemyKind.BEETLE);c.advanceDays(3.0)
        assertEquals(Phase.LOST,c.phase);assertEquals(0.0,c.queenHealth,0.0)
        val end=c.day;c.advanceDays(100.0);assertEquals(end,c.day,0.0)
    }
    @Test fun sufficientWorkersRepelPredatorAndReturnToForaging() {
        val c=quiet().apply {phase=Phase.GROWING;adults+=WorkerCohort(120)}
        val normalForagers=c.foragers;c.spawnRaid(EnemyKind.EARWIG)
        assertTrue(c.foragers<normalForagers);assertTrue(c.guards>c.foragers)
        c.advanceDays(2.0);assertNull(c.invader);assertEquals(1,c.repelled);assertFalse(c.terminal)
    }
    @Test fun barriersActuallyDelayEnemyArrival() {
        val clear=quiet().apply {phase=Phase.GROWING}
        val protected=StateCodec.decode(StateCodec.encode(clear))
        protected.nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.STONE)
        clear.spawnRaid(EnemyKind.BEETLE);protected.spawnRaid(EnemyKind.BEETLE)
        clear.advanceDays(3.0);protected.advanceDays(3.0)
        assertEquals(Phase.LOST,protected.phase);assertTrue(protected.day>clear.day)
    }
    @Test fun matureHealthyColonyRaisesQueensAndFliesToClear() {
        val c=quiet().apply {day=1095.0;phase=Phase.GROWING;adults+=WorkerCohort(220);food=500.0}
        c.advanceDays(Colony.STEP);assertTrue(c.royalLaid);assertEquals(Phase.REPRODUCTIVE,c.phase)
        c.advanceDays(75.0);assertEquals(0,c.youngQueens)
        c.advanceDays(180.0);assertEquals(Phase.CLEARED,c.phase);assertTrue(c.youngQueens>0);assertEquals(1.0,c.flightProgress,0.0)
    }
    @Test fun severeOvercrowdingCanTriggerQueensBeforeThreeYears() {
        val c=Colony(Nest.create(size=0),1).apply {day=370.0;nextRaid=1e9;phase=Phase.GROWING;adults+=WorkerCohort(450);food=700.0}
        c.advanceDays(.1);assertTrue(c.royalLaid);assertTrue(c.day<1095)
    }
    @Test fun underfedOrTinyColonyDoesNotProduceQueensJustBecauseTimePassed() {
        val c=quiet().apply {day=1100.0;phase=Phase.GROWING;adults+=WorkerCohort(10)}
        c.advanceDays(1.0);assertFalse(c.royalLaid)
    }
    @Test fun workersExpandAndRemodelTheNest() {
        val c=quiet().apply {phase=Phase.GROWING;adults+=WorkerCohort(250);food=500.0}
        val capacity=c.nest.capacity;c.advanceDays(150.0)
        assertTrue("Capacity should expand from $capacity to ${c.nest.capacity}",c.nest.capacity>capacity)
    }
    @Test fun partitionedTimeGivesIdenticalSimulation() {
        val a=quiet(25);val b=StateCodec.decode(StateCodec.encode(a))
        a.advanceDays(100.0);repeat(1000){b.advanceDays(.1)}
        assertEquals(a.day,b.day,1e-8);assertEquals(a.workers,b.workers);assertEquals(a.food,b.food,1e-8);assertEquals(a.rngState,b.rngState)
    }
    @Test fun saveReloadDuringCombatPreservesAllStateAndFutureRandomness() {
        val a=quiet().apply {phase=Phase.GROWING;adults+=WorkerCohort(50)}
        a.nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.TWIG);a.spawnRaid(EnemyKind.BEETLE);a.advanceDays(.05)
        val serialized=StateCodec.encode(a);val b=StateCodec.decode(serialized)
        assertEquals(serialized,StateCodec.encode(b))
        a.advanceDays(20.0);b.advanceDays(20.0)
        assertEquals(StateCodec.encode(a),StateCodec.encode(b))
    }
    @Test fun offlineUsesRealElapsedTimeAndHonorsPauseAndThirtyDayLimit() {
        val c=quiet().apply {lastSavedMillis=1000;speed=604800}
        assertEquals(1.0,StateCodec.offlineDays(c,86_401_000),0.0)
        assertEquals(0.0,StateCodec.offlineDays(c,0),0.0)
        assertEquals(30.0,StateCodec.offlineDays(c,Long.MAX_VALUE),0.0)
        c.speed=0;assertEquals(0.0,StateCodec.offlineDays(c,86_401_000),0.0)
    }
    @Test(expected=IllegalArgumentException::class) fun unknownSaveVersionIsRejected() {
        StateCodec.decode(StateCodec.encode(quiet()).replace("\"version\":1","\"version\":99"))
    }
    @Test fun newAttackStopsFastForwardAtReadableEvent() {
        val c=quiet().apply {phase=Phase.GROWING;day=100.0;nextRaid=100.1;adults+=WorkerCohort(100)}
        c.advanceDays(20.0,stopAtEvents=true)
        assertNotNull(c.invader);assertTrue(c.day<101)
    }
    @Test fun fullNaturalLifecycleIsReachableWithDefendedBlueprint() {
        val c=Colony(Nest.create(),812).apply {
            nest.obstacles+=Obstacle(0,1,.5,ObstacleKind.TWIG)
            nest.obstacles+=Obstacle(2,4,.5,ObstacleKind.STONE)
        }
        c.advanceDays(1800.0)
        println("LIFECYCLE phase=${c.phase} day=${c.day} workers=${c.workers} queens=${c.youngQueens} raids=${c.raidCount} food=${c.food} rooms=${c.nest.chambers.size}")
        assertEquals(Phase.CLEARED,c.phase)
        assertTrue(c.repelled>0);assertTrue(c.royalLaid);assertTrue(c.youngQueens>0)
    }
}
