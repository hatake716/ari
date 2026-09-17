package io.github.hatake716.ari

import kotlin.math.*

/** All distances are normalized scene coordinates; all biological durations are days.
 * This is a documented, stylized temperate-ant model, not a fitted species forecast.
 * See docs/BIOLOGY.md for sources and for each deliberately designed coefficient. */
data class Point(val x: Double, val y: Double) {
    fun distance(other: Point) = hypot(x - other.x, y - other.y)
    fun mix(other: Point, t: Double) = Point(x + (other.x - x) * t, y + (other.y - y) * t)
}
data class Chamber(val id: Int, var x: Double, var y: Double, var radius: Double = .066, var built: Double = 1.0) {
    val point get() = Point(x, y)
}
data class Tunnel(val a: Int, val b: Int)
enum class ObstacleKind(val label: String, val workerSpeed: Double, val enemySpeed: Double) {
    TWIG("小枝", .64, .30), STONE("小石", .44, .18)
}
data class Obstacle(val a: Int, val b: Int, val t: Double, val kind: ObstacleKind)

class Nest(
    val chambers: MutableList<Chamber>,
    val tunnels: MutableList<Tunnel>,
    val obstacles: MutableList<Obstacle> = mutableListOf(),
    var queenRoom: Int = chambers.last().id,
    var shape: Int = 0,
    var size: Int = 1,
) {
    fun room(id: Int) = chambers.first { it.id == id }
    val capacity get() = chambers.filter { it.id != 0 }.sumOf { it.radius * it.radius * 17000 * NestGrowth.chamberProgress(it) }.toInt().coerceAtLeast(1)
    fun blocks(a: Int, b: Int) = obstacles.filter { (it.a == a && it.b == b) || (it.a == b && it.b == a) }
    fun localSpeed(a: Int, b: Int, t: Double, enemy: Boolean): Double {
        var speed = 1.0
        blocks(a, b).forEach {
            val position = if (it.a == a) it.t else 1 - it.t
            if (abs(position - t) < .11) speed *= if (enemy) it.kind.enemySpeed else it.kind.workerSpeed
        }
        return speed.coerceAtLeast(.06)
    }
    fun cost(a: Int, b: Int, enemy: Boolean): Double {
        val length = room(a).point.distance(room(b).point)
        // Integrate the same local speed function that visible agents use.
        return (0 until 40).sumOf { length / 40 / localSpeed(a, b, (it + .5) / 40, enemy) }
    }
    fun path(start: Int, end: Int, enemy: Boolean = false): List<Int> {
        if (start == end) return listOf(start)
        val distance = chambers.associate { it.id to Double.POSITIVE_INFINITY }.toMutableMap()
        val previous = mutableMapOf<Int, Int>()
        val open = chambers.filter { it.built >= 1 || it.id == end || it.id == start }.map { it.id }.toMutableSet()
        distance[start] = 0.0
        while (open.isNotEmpty()) {
            val current = open.minBy { distance[it] ?: Double.POSITIVE_INFINITY }
            if (current == end) break
            open.remove(current)
            tunnels.filter { it.a == current || it.b == current }.forEach { edge ->
                val next = if (edge.a == current) edge.b else edge.a
                if (next in open) {
                    val proposed = distance.getValue(current) + cost(current, next, enemy)
                    if (proposed < distance.getValue(next)) { distance[next] = proposed; previous[next] = current }
                }
            }
        }
        if (end !in previous) return emptyList()
        val result = mutableListOf(end)
        while (result.last() != start) result += previous[result.last()] ?: return emptyList()
        return result.reversed()
    }
    fun travelCost(route: List<Int>, enemy: Boolean = false) = route.zipWithNext().sumOf { (a, b) -> cost(a, b, enemy) }
    private var efficiencyKey = ""
    private var efficiencyValue = 1.0
    val efficiency: Double get() {
        val key=chambers.joinToString { "${it.id}:${it.x}:${it.y}:${it.built>=1}" }+tunnels+obstacles
        if(key==efficiencyKey)return efficiencyValue
        efficiencyKey=key
        // Only barriers on real transport routes affect productivity; a side-branch stone is not a global debuff.
        val inhabited = chambers.filter { it.id != 0 && it.built >= 1 }
        val ratios = inhabited.map { room ->
            val route = path(0, room.id)
            val direct = route.zipWithNext().sumOf { (a, b) -> this.room(a).point.distance(this.room(b).point) }
            if (direct == 0.0) .1 else direct / travelCost(route).coerceAtLeast(.001)
        }
        efficiencyValue=ratios.average().takeIf { it.isFinite() }?.coerceIn(.1, 1.0) ?: .1
        return efficiencyValue
    }
    fun addRoom(point: Point): Boolean {
        if (chambers.size >= 20 || point.y !in .26.. .89 || point.x !in .10.. .90) return false
        if (chambers.any { it.point.distance(point) < .13 }) return false
        val parent = chambers.filter { it.id != 0 }.minByOrNull { it.point.distance(point) } ?: return false
        val next = (chambers.maxOfOrNull { it.id } ?: 0) + 1
        chambers += Chamber(next, point.x, point.y, .055 + size * .007)
        tunnels += Tunnel(parent.id, next)
        return true
    }
    fun putObstacle(point: Point, kind: ObstacleKind): Boolean {
        if (obstacles.size >= 8) return false
        val nearest = tunnels.map { edge ->
            val a = room(edge.a).point; val b = room(edge.b).point
            val dx = b.x - a.x; val dy = b.y - a.y
            val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(.20, .80)
            Triple(edge, t, a.mix(b, t).distance(point))
        }.minByOrNull { it.third } ?: return false
        if (nearest.third > .07) return false
        val edge = nearest.first
        if (blocks(edge.a, edge.b).any { abs((if (it.a == edge.a) it.t else 1 - it.t) - nearest.second) < .25 }) return false
        obstacles += Obstacle(edge.a, edge.b, nearest.second, kind)
        return true
    }
    fun erase(point: Point): Boolean {
        val obstacle = obstacles.minByOrNull { room(it.a).point.mix(room(it.b).point, it.t).distance(point) }
        if (obstacle != null && room(obstacle.a).point.mix(room(obstacle.b).point, obstacle.t).distance(point) < .09) {
            obstacles.remove(obstacle); return true
        }
        val chamber = chambers.filter { it.id != 0 && it.id != queenRoom && tunnels.count { t -> t.a == it.id || t.b == it.id } == 1 }
            .minByOrNull { it.point.distance(point) }
        if (chamber != null && chamber.point.distance(point) < .075 && chambers.size > 3) {
            tunnels.removeAll { it.a == chamber.id || it.b == chamber.id }
            obstacles.removeAll { it.a == chamber.id || it.b == chamber.id }
            chambers.remove(chamber); return true
        }
        return false
    }
    companion object {
        val SHAPES = listOf("樹形", "縦穴", "広間")
        val SIZES = listOf("小さめ", "ふつう", "大きめ")
        fun create(shape: Int = 0, size: Int = 1): Nest {
            val points = when (shape) {
                1 -> listOf(Point(.50,.115), Point(.50,.32), Point(.44,.49), Point(.55,.65), Point(.42,.81))
                2 -> listOf(Point(.50,.115), Point(.50,.35), Point(.24,.48), Point(.76,.48), Point(.50,.68))
                else -> listOf(Point(.50,.115), Point(.49,.33), Point(.27,.48), Point(.72,.51), Point(.42,.70))
            }
            val chambers = points.mapIndexed { i, p -> Chamber(i,p.x,p.y, if (i == 0) .015 else .054 + size * .012) }.toMutableList()
            val tunnels = if (shape == 1) mutableListOf(Tunnel(0,1),Tunnel(1,2),Tunnel(2,3),Tunnel(3,4))
            else mutableListOf(Tunnel(0,1),Tunnel(1,2),Tunnel(1,3),Tunnel(2,4))
            return Nest(chambers,tunnels,queenRoom=4,shape=shape,size=size)
        }
    }
}

enum class Phase(val label: String) { ARRIVAL("女王の到着"), FOUNDING("巣の創設"), GROWING("群れの成長"), REPRODUCTIVE("次の世代へ"), FLIGHT("旅立ちの朝"), CLEARED("命は、次の巣へ"), LOST("巣の灯が消えた") }
enum class BroodStage(val label: String) { EGG("卵"), LARVA("幼虫"), PUPA("蛹") }
data class Brood(var count: Int, var age: Double = 0.0, val royal: Boolean = false) {
    val stage get() = when { age < 18 -> BroodStage.EGG; age < 40 -> BroodStage.LARVA; else -> BroodStage.PUPA }
    val duration get() = if (royal) 80.0 else 60.0
}
data class WorkerCohort(var count: Int, var age: Double = 0.0)
data class Entry(val day: Double, val text: String)
enum class EnemyKind(val label: String, val health: Double, val speed: Double, val attack: Double) {
    ROVE("ハネカクシ", 18.0, 2.0, 3.0), EARWIG("ハサミムシ", 38.0, 1.5, 5.0), BEETLE("オサムシ", 80.0, 1.1, 9.0)
}
data class Invader(val kind: EnemyKind, var hp: Double, val maxHp: Double, val route: List<Int>, var segment: Int = 0, var progress: Double = 0.0, var age: Double = 0.0, var casualtyCredit: Double = 0.0)

class Colony(val nest: Nest, var rngState: Long = System.nanoTime()) {
    var day = 0.0
    var remainder = 0.0
    var phase = Phase.ARRIVAL
    var food = 100.0
    var queenHealth = 100.0
    var foundingReserve = 12.0
    var eggCredit = 0.0
    var excavation = 0.0
    var nextRaid = 155.0
    var raidCount = 0
    var repelled = 0
    var losses = 0
    var royalLaid = false
    var youngQueens = 0
    var males = 0
    var royalAdultDay = -1.0
    var flightProgress = 0.0
    var invader: Invader? = null
    var lastSavedMillis = System.currentTimeMillis()
    var speed = 3600
    val brood = mutableListOf<Brood>()
    val adults = mutableListOf<WorkerCohort>()
    val journal = mutableListOf(Entry(0.0, "結婚飛行を終えた女王が、この土に降り立ちました。"))
    val workers get() = adults.sumOf { it.count }
    val population get() = workers + brood.sumOf { it.count } + youngQueens + males + 1
    val yearDay get() = (day + SeasonCalendar.START_DAY) % 365 // Same calendar as the monthly artwork.
    val temperature get() = 17.0 + 10.0 * sin(2 * PI * (yearDay - 80) / 365)
    val activity get() = ((temperature - 7) / 18).coerceIn(.12, 1.0)
    val winter get() = temperature < 12
    val terminal get() = phase == Phase.CLEARED || phase == Phase.LOST
    val foragers get() = if (invader == null) (workers * .32).toInt() else (workers * .12).toInt()
    val builders get() = if (invader == null) (workers * .18).toInt() else (workers * .05).toInt()
    val guards get() = (workers * if (invader == null) .14 else .65).toInt()
    fun count(stage: BroodStage) = brood.filter { it.stage == stage }.sumOf { it.count }
    fun random(): Double {
        var x = if (rngState == 0L) 1L else rngState
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        rngState = x
        return (x ushr 11).toDouble() / 9007199254740992.0
    }
    fun record(text: String) {
        journal += Entry(day,text)
        if (journal.size > 120) journal.removeAt(0)
    }
    /** Fixed half-hour steps make save/reload and frame-rate partitions deterministic.
     * A presentation caller can stop at a newly arrived predator to show that event at a readable speed. */
    fun advanceDays(days: Double, stopAtEvents: Boolean = false) {
        if (terminal || days <= 0 || !days.isFinite()) return
        remainder += days
        while (remainder + 1e-10 >= STEP && !terminal) {
            remainder -= STEP
            val beforeRaid = invader
            val beforePhase = phase
            tick(STEP)
            if (stopAtEvents && ((beforeRaid == null && invader != null) || (beforePhase != Phase.FLIGHT && phase == Phase.FLIGHT))) {
                // Remaining wall time occurred after this event and is intentionally not fast-forwarded.
                remainder = 0.0
                break
            }
        }
        if (terminal) remainder = 0.0
    }
    private fun tick(dt: Double) {
        day += dt
        if (phase == Phase.ARRIVAL && day >= .25) {
            phase = Phase.FOUNDING
            record("女王が奥の部屋へ。蓄えた栄養で、最初の子を育てます。")
        }
        if (phase == Phase.ARRIVAL) return
        val active = activity
        val birthRate = if (winter) 0.0 else (0.42 + workers * .048).coerceAtMost(32.0) * active
        val consumption = (.12 + workers * .018 + brood.sumOf { it.count } * .012 + youngQueens * .08) * (.25 + active * .75)
        val supply = foragers * .19 * active * nest.efficiency
        food = (food + (supply - consumption) * dt).coerceIn(0.0, nest.capacity * 2.5)
        // The founding queen uses body reserves; workers subsequently provision brood.
        val nourished = food > consumption * 3 || (workers == 0 && foundingReserve > 0)
        if (nourished) {
            eggCredit += birthRate * dt
            if (eggCredit >= 1) {
                val n = eggCredit.toInt(); eggCredit -= n
                val latest = brood.lastOrNull()
                if (latest != null && !latest.royal && latest.age < .6) latest.count += n else brood += Brood(n)
                if (workers == 0) foundingReserve = (foundingReserve - n * .12).coerceAtLeast(0.0)
            }
        }
        // Effective development day: 60 at warm reference conditions, slower when cold or underfed.
        val growth = dt * active * if (nourished) 1.0 else .25
        val emerged = mutableListOf<Brood>()
        brood.forEach {
            it.age += growth
            if (it.age >= it.duration) {
                if (it.royal) {
                    youngQueens += it.count
                    males += it.count * 2
                    if (royalAdultDay < 0) { royalAdultDay = day; record("翅のある子女王が羽化。体が整い、暖かな日が来るのを待ちます。") }
                } else {
                    if (workers == 0 && phase == Phase.FOUNDING) { phase = Phase.GROWING; record("最初の働きアリが羽化。採餌・育児・掘削が始まります。") }
                    val latest = adults.lastOrNull()
                    if (latest != null && latest.age < 1) latest.count += it.count else adults += WorkerCohort(it.count)
                }
                emerged += it
            }
        }
        brood.removeAll(emerged.toSet())
        adults.forEach { it.age += dt }
        val old = adults.filter { it.age >= WORKER_LIFESPAN }
        adults.removeAll(old.toSet())
        if (food <= 0) {
            queenHealth = (queenHealth - dt * .20).coerceAtLeast(0.0)
            // Starvation removes the oldest workers gradually, with a deterministic fractional accumulator.
            starvationCredit += dt * workers * .018
            if (starvationCredit >= 1) { removeWorkers(starvationCredit.toInt()); starvationCredit %= 1 }
        } else queenHealth = (queenHealth + dt * .08).coerceAtMost(100.0)
        expand(dt)
        if (!royalLaid && workers >= 80 && food > 25 && (day >= REPRODUCTIVE_AGE || (population > nest.capacity * 1.55 && day >= 365))) {
            royalLaid = true; phase = Phase.REPRODUCTIVE
            brood += Brood((workers / 100).coerceIn(2,12), royal=true)
            record("群れが成熟し、子女王の育成が始まりました。次の世代を育てます。")
        }
        if (youngQueens > 0 && day - royalAdultDay >= 12 && !winter && temperature >= 19 && invader == null && phase != Phase.FLIGHT) {
            phase = Phase.FLIGHT
            record("暖かな朝。子女王と雄アリが出口へ向かい、結婚飛行の準備をしています。")
        }
        if (phase == Phase.FLIGHT) {
            flightProgress += dt / .30
            if (flightProgress >= 1) { flightProgress=1.0; phase=Phase.CLEARED; record("子女王が空へ旅立ちました。母の巣から、新しい群れへ命がつながります。") }
        }
        if (!terminal && phase != Phase.FLIGHT && invader == null && day >= nextRaid && !winter) spawnRaid()
        if (invader != null && !terminal) combat(dt)
        if (queenHealth <= 0 && !terminal) { phase=Phase.LOST; record("食料が尽き、女王は命を落としました。巣の記録は保存されています。") }
    }
    var starvationCredit = 0.0
    fun removeWorkers(amount: Int) {
        var remaining = amount
        for (group in adults) {
            val loss = min(group.count, remaining)
            group.count -= loss; remaining -= loss; losses += loss
            if (remaining <= 0) break
        }
        adults.removeAll { it.count == 0 }
    }
    private fun expand(dt: Double) {
        if(builders<1)return
        val jobs=nest.construction
        val labor=builders*activity*nest.efficiency
        jobs.forEach { room ->
            room.built=(room.built+dt*min(.12,labor*.0075/jobs.size)).coerceAtMost(1.0)
            if(room.built>=1)record("${nest.roomName(room)}が完成。巣は${nest.completedRooms}室になりました。新しい空間へ群れが広がります。")
        }
        val target=NestGrowth.demand(workers,population)
        val parallel=when {workers>=1200->3;workers>=400->2;else->1}
        if(nest.chambers.size-1>=target || nest.construction.size>=parallel)return
        excavation=(excavation+dt*labor*.025).coerceAtMost(2.0)
        if(excavation>=1.5) {
            val room=NestGrowth.plan(nest) ?: return
            excavation-=1.5
            record("働きアリが新しい${nest.roomName(room)}へ通路を掘り始めました。群れ ${population}匹、完成した部屋 ${nest.completedRooms}室。")
        }
    }
    fun spawnRaid(kind: EnemyKind? = null, strength: Double = 1.0) {
        if (terminal || invader != null) return
        val available = when { day < 300 -> 1; day < 650 -> 2; else -> 3 }
        val selected = kind ?: EnemyKind.entries[(random() * available).toInt().coerceAtMost(available - 1)]
        val health = selected.health * (1 + day / 900) * strength
        val route = nest.path(0, nest.queenRoom, enemy=true)
        if (route.size < 2) return
        invader = Invader(selected, health, health, route)
        raidCount++
        record("${selected.label}が巣に侵入。働きアリが警報に反応し、防衛に集まります。")
    }
    private fun combat(dt: Double) {
        val enemy = invader ?: return
        enemy.age += dt
        // Longer/blocked queen-to-entrance travel delays mobilization too.
        val musterDays = (.025 + nest.travelCost(nest.path(nest.queenRoom,0)) * .16)
        val defenders = guards * (enemy.age / musterDays).coerceIn(0.0, 1.0)
        enemy.hp -= defenders * 15.0 * dt
        enemy.casualtyCredit += enemy.kind.attack * dt * (if (defenders > 0) 1 else 0)
        if (enemy.casualtyCredit >= 1) { removeWorkers(enemy.casualtyCredit.toInt()); enemy.casualtyCredit %= 1 }
        if (enemy.hp <= 0) {
            food = (food + enemy.kind.health * .15).coerceAtMost(nest.capacity * 2.5)
            invader=null; repelled++; nextRaid = day + 65 + random() * 85
            record("${enemy.kind.label}を撃退。仲間が巣を守り、日常が戻りました。")
            return
        }
        var remaining = enemy.kind.speed * dt
        while (remaining > 0 && enemy.segment < enemy.route.lastIndex) {
            val a = enemy.route[enemy.segment]; val b = enemy.route[enemy.segment + 1]
            val length = nest.room(a).point.distance(nest.room(b).point)
            // Small spatial substeps ensure a fast predator cannot skip a twig or stone.
            val distance = min(.004,remaining)
            enemy.progress += distance / length * nest.localSpeed(a,b,enemy.progress,true)
            remaining -= distance
            if (enemy.progress >= 1) { enemy.progress=0.0; enemy.segment++ }
        }
        if (enemy.segment >= enemy.route.lastIndex) {
            queenHealth=0.0; phase=Phase.LOST
            record("${enemy.kind.label}が女王室に到達。巣は壊され、女王が捕食されました。")
        }
    }
    fun enemyPoint(): Point? = invader?.let {
        val from = nest.room(it.route[it.segment.coerceAtMost(it.route.lastIndex)]).point
        val to = nest.room(it.route[(it.segment+1).coerceAtMost(it.route.lastIndex)]).point
        from.mix(to,it.progress)
    }
    companion object {
        const val STEP = 1.0 / 48
        const val WORKER_LIFESPAN = 420.0
        const val REPRODUCTIVE_AGE = 365.0 * 3
        val SPEEDS = listOf(1,60,3600,86400,604800)
        val SPEED_LABELS = listOf("1倍","60倍","1時間/秒","1日/秒","7日/秒")
    }
}
