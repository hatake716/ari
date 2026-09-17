package io.github.hatake716.ari

import org.json.JSONArray
import org.json.JSONObject

/** Versioned, explicit serialization. The RNG, fractional clocks and in-flight combat are part of a save. */
object StateCodec {
    private fun array(values: Iterable<Any>) = JSONArray().also { a -> values.forEach { a.put(it) } }
    private fun json(vararg pairs: Pair<String, Any?>) = JSONObject().also { o -> pairs.forEach { o.put(it.first,it.second) } }
    fun encode(c: Colony): String = json(
        "version" to 1, "day" to c.day, "remainder" to c.remainder, "phase" to c.phase.name,
        "rng" to c.rngState, "food" to c.food, "health" to c.queenHealth,
        "reserve" to c.foundingReserve, "eggCredit" to c.eggCredit, "excavation" to c.excavation,
        "starvationCredit" to c.starvationCredit, "nextRaid" to c.nextRaid, "raids" to c.raidCount,
        "repelled" to c.repelled, "losses" to c.losses, "royalLaid" to c.royalLaid,
        "youngQueens" to c.youngQueens, "males" to c.males, "royalAdultDay" to c.royalAdultDay,
        "flight" to c.flightProgress, "saved" to c.lastSavedMillis, "speed" to c.speed,
        "nest" to json("shape" to c.nest.shape,"size" to c.nest.size,"queen" to c.nest.queenRoom,
            "rooms" to array(c.nest.chambers.map { json("id" to it.id,"x" to it.x,"y" to it.y,"r" to it.radius,"built" to it.built) }),
            "tunnels" to array(c.nest.tunnels.map { json("a" to it.a,"b" to it.b) }),
            "obstacles" to array(c.nest.obstacles.map { json("a" to it.a,"b" to it.b,"t" to it.t,"kind" to it.kind.name) })),
        "brood" to array(c.brood.map { json("count" to it.count,"age" to it.age,"royal" to it.royal) }),
        "adults" to array(c.adults.map { json("count" to it.count,"age" to it.age) }),
        "journal" to array(c.journal.map { json("day" to it.day,"text" to it.text) }),
        "invader" to c.invader?.let { json("kind" to it.kind.name,"hp" to it.hp,"maxHp" to it.maxHp,
            "route" to array(it.route),"segment" to it.segment,"progress" to it.progress,"age" to it.age,"casualty" to it.casualtyCredit) },
    ).toString()
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    fun decode(text: String): Colony {
        require(text.length < 2_000_000) { "Save too large" }
        val o = JSONObject(text)
        require(o.getInt("version") == 1) { "Unsupported save version" }
        val n = o.getJSONObject("nest")
        val nest = Nest(
            n.getJSONArray("rooms").objects().map { Chamber(it.getInt("id"),it.getDouble("x"),it.getDouble("y"),it.getDouble("r"),it.getDouble("built")) }.toMutableList(),
            n.getJSONArray("tunnels").objects().map { Tunnel(it.getInt("a"),it.getInt("b")) }.toMutableList(),
            n.getJSONArray("obstacles").objects().map { Obstacle(it.getInt("a"),it.getInt("b"),it.getDouble("t"),ObstacleKind.valueOf(it.getString("kind"))) }.toMutableList(),
            n.getInt("queen"),n.getInt("shape"),n.getInt("size"))
        require(nest.chambers.size in 2..20)
        val ids = nest.chambers.map { it.id }
        require(ids.distinct().size == ids.size && 0 in ids && nest.queenRoom in ids && nest.queenRoom != 0)
        require(nest.chambers.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 && it.radius in .005.. .15 && it.built in 0.0..1.0 })
        require(nest.tunnels.all { it.a in ids && it.b in ids && it.a != it.b })
        require(nest.obstacles.size <= 8 && nest.obstacles.all { b -> b.t in 0.0..1.0 && nest.tunnels.any { (it.a == b.a && it.b == b.b) || (it.a == b.b && it.b == b.a) } })
        require(nest.path(0,nest.queenRoom).isNotEmpty())
        return Colony(nest,o.getLong("rng")).also { c ->
            c.day=o.getDouble("day"); c.remainder=o.getDouble("remainder"); c.phase=Phase.valueOf(o.getString("phase"))
            c.food=o.getDouble("food"); c.queenHealth=o.getDouble("health"); c.foundingReserve=o.getDouble("reserve")
            c.eggCredit=o.getDouble("eggCredit"); c.excavation=o.getDouble("excavation"); c.starvationCredit=o.getDouble("starvationCredit")
            c.nextRaid=o.getDouble("nextRaid"); c.raidCount=o.getInt("raids"); c.repelled=o.getInt("repelled"); c.losses=o.getInt("losses")
            c.royalLaid=o.getBoolean("royalLaid"); c.youngQueens=o.getInt("youngQueens"); c.males=o.getInt("males")
            c.royalAdultDay=o.getDouble("royalAdultDay"); c.flightProgress=o.getDouble("flight"); c.lastSavedMillis=o.getLong("saved"); c.speed=o.getInt("speed")
            c.brood.addAll(o.getJSONArray("brood").objects().map { Brood(it.getInt("count"),it.getDouble("age"),it.getBoolean("royal")) })
            c.adults.addAll(o.getJSONArray("adults").objects().map { WorkerCohort(it.getInt("count"),it.getDouble("age")) })
            c.journal.clear(); c.journal.addAll(o.getJSONArray("journal").objects().map { Entry(it.getDouble("day"),it.getString("text")) })
            if (o.has("invader")) c.invader=o.getJSONObject("invader").let { e ->
                val route = e.getJSONArray("route").let { a -> (0 until a.length()).map { a.getInt(it) } }
                val enemy = Invader(EnemyKind.valueOf(e.getString("kind")),e.getDouble("hp"),e.getDouble("maxHp"),route,
                    e.getInt("segment"),e.getDouble("progress"),e.getDouble("age"),e.getDouble("casualty"))
                require(route.size >= 2 && route.all { it in ids } && enemy.segment in route.indices && enemy.progress in 0.0..1.0)
                require(enemy.maxHp > 0 && enemy.age >= 0)
                enemy
            }
            require(c.day >= 0 && c.day.isFinite() && c.remainder in -.000001..1.0 && c.food >= 0 && c.queenHealth in 0.0..100.0)
            require(c.speed == 0 || c.speed in Colony.SPEEDS)
            require(c.brood.size < 10000 && c.adults.size < 10000 && c.journal.size <= 120)
            require(c.brood.all { it.count in 1..100000 && it.age >= 0 } && c.adults.all { it.count in 1..100000 && it.age >= 0 })
        }
    }
    /** Background time is real time, never the selected foreground multiplier. Pause is respected. */
    fun offlineDays(c: Colony, now: Long): Double = if (c.speed == 0 || c.terminal) 0.0
        else ((now - c.lastSavedMillis).coerceAtLeast(0).toDouble() / 86_400_000).coerceAtMost(30.0)
}
