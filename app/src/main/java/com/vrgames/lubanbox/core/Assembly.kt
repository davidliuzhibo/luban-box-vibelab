package com.vrgames.lubanbox.core

/**
 * Where a part goes in a build: its [type] (any part of that type fits), its place in the object
 * frame ([pos], meters; [yaw], degrees) and the slots that must be filled first ([needs]: a block
 * cannot sit on an arm that is not there yet).
 */
class Slot(val id: String, val type: String, val pos: Vec3, val yaw: Double = 0.0, val needs: Set<String> = emptySet())

/**
 * Building something from loose parts (斗拱, 被中香炉, 指南车...): parts are dropped near their slot
 * and snap in when the slot is free and everything under it is in place. Parts whose type has no
 * slot are decoys.
 */
class Assembly(val slots: List<Slot>, val parts: List<String>) {
    private val slotOf = arrayOfNulls<String>(parts.size)
    private val partIn = HashMap<String, Int>()

    init {
        // Every slot has a part; extra parts are decoys that fit nowhere.
        val have = parts.groupingBy { it }.eachCount()
        require(slots.groupingBy { it.type }.eachCount().all { (t, n) -> (have[t] ?: 0) >= n }) { "not enough parts for the slots" }
        val ids = slots.map { it.id }.toSet()
        require(slots.all { s -> s.needs.all { it in ids } }) { "unknown slot in needs" }
    }

    val solved get() = partIn.size == slots.size

    fun slotOf(part: Int): Slot? = slotOf[part]?.let { id -> slots.first { it.id == id } }
    fun isPlaced(part: Int) = slotOf[part] != null
    fun filled(slot: Slot) = slot.id in partIn

    /** A slot ready to take a part: free, with everything it rests on in place. */
    fun ready(slot: Slot) = !filled(slot) && slot.needs.all { it in partIn }

    /** Why [part] would not go into [slot] (null if it would). */
    fun refusal(part: Int, slot: Slot): Refusal? = when {
        slots.none { it.type == parts[part] } -> Refusal.FITS_NOWHERE
        parts[part] != slot.type -> Refusal.WRONG_PART
        filled(slot) -> Refusal.TAKEN
        !ready(slot) -> Refusal.TOO_EARLY
        else -> null
    }

    enum class Refusal { WRONG_PART, TAKEN, TOO_EARLY, FITS_NOWHERE }

    /**
     * The slot [part] snaps into when let go at [at]: the nearest slot of its type within
     * [radius] that is ready, else null.
     */
    fun snapTarget(part: Int, at: Vec3, radius: Double): Slot? = slots
        .filter { it.type == parts[part] && ready(it) && it.pos.distanceTo(at) <= radius }
        .minByOrNull { it.pos.distanceTo(at) }

    /** Nearest slot of any kind within [radius] of [at], to explain a refusal. */
    fun nearest(at: Vec3, radius: Double): Slot? = slots.filter { it.pos.distanceTo(at) <= radius }.minByOrNull { it.pos.distanceTo(at) }

    fun place(part: Int, slot: Slot) {
        require(refusal(part, slot) == null) { "${parts[part]} cannot go into ${slot.id}" }
        slotOf[part] = slot.id
        partIn[slot.id] = part
    }

    fun reset() {
        slotOf.fill(null)
        partIn.clear()
    }

    /** Next step for a hint: a ready slot (in build order) and a loose part that fits it. */
    fun hint(): Pair<Int, Slot>? {
        val slot = slots.firstOrNull { ready(it) } ?: return null
        val part = parts.indices.firstOrNull { !isPlaced(it) && parts[it] == slot.type } ?: return null
        return part to slot
    }

    /** Whether some order of placements finishes the build (every slot reachable bottom-up). */
    fun buildable(): Boolean {
        val done = HashSet(partIn.keys)
        while (true) {
            val next = slots.firstOrNull { it.id !in done && it.needs.all(done::contains) } ?: break
            done += next.id
        }
        return done.size == slots.size
    }
}
