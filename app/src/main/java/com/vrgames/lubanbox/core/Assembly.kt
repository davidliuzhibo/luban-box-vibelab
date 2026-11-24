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
 *
 * With [symmetry] (v0.10, the 斗拱), a part also has to face the right way: each part has a yaw the
 * player turns in quarter turns, and it snaps in only when that yaw matches its slot's within
 * [ANGLE_TOL], counting the turns that look the same (a part type's period: 90 for a block with a
 * cross seat, 180 for an arm symmetric end to end, 360 for one that has a front).
 */
class Assembly(val slots: List<Slot>, val parts: List<String>, val symmetry: Map<String, Int>? = null) {
    private val slotOf = arrayOfNulls<String>(parts.size)
    private val partIn = HashMap<String, Int>()
    private val yaw = DoubleArray(parts.size)

    /** Whether parts must be turned to face their slot. */
    val orients get() = symmetry != null

    /** The part's turn about the vertical (degrees, 0 until 360). */
    fun yawOf(part: Int) = yaw[part]

    fun setYaw(part: Int, deg: Double) {
        yaw[part] = ((deg % 360) + 360) % 360
    }

    /** The player turned [part] a quarter turn; its new yaw. */
    fun turn(part: Int): Double {
        setYaw(part, yaw[part] + 90)
        return yaw[part]
    }

    /** Turns that leave [part] looking the same (degrees). */
    fun period(part: Int): Int = symmetry?.get(parts[part]) ?: 360

    /** Whether [part], turned as it is now, faces the way [slot] needs. */
    fun angleOk(part: Int, slot: Slot): Boolean {
        if (symmetry == null) return true
        val p = period(part).toDouble()
        val d = (((yaw[part] - slot.yaw) % p) + p) % p
        return d <= ANGLE_TOL || p - d <= ANGLE_TOL
    }

    /** The yaw [part] settles at in [slot]: the slot's, or the same-looking one nearest the part's own (no spin). */
    fun settledYaw(part: Int, slot: Slot): Double {
        val p = period(part).toDouble()
        var best = slot.yaw
        var k = 0.0
        while (k < 360) {
            val c = slot.yaw + k
            if (angleDiff(c, yaw[part]) < angleDiff(best, yaw[part])) best = c
            k += p
        }
        return best
    }

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

    /** Why [part] would not go into [slot] (null if it would); with [facing], also whether it is turned the right way. */
    fun refusal(part: Int, slot: Slot, facing: Boolean = false): Refusal? = when {
        slots.none { it.type == parts[part] } -> Refusal.FITS_NOWHERE
        parts[part] != slot.type -> Refusal.WRONG_PART
        filled(slot) -> Refusal.TAKEN
        !ready(slot) -> Refusal.TOO_EARLY
        facing && !angleOk(part, slot) -> Refusal.WRONG_ANGLE
        else -> null
    }

    enum class Refusal { WRONG_PART, TAKEN, TOO_EARLY, FITS_NOWHERE, WRONG_ANGLE }

    /**
     * The slot [part] snaps into when let go at [at]: the nearest slot of its type within
     * [radius] that is ready (and, with [facing], that the part faces the right way for), else null.
     */
    fun snapTarget(part: Int, at: Vec3, radius: Double, facing: Boolean = true): Slot? = slots
        .filter { it.type == parts[part] && ready(it) && it.pos.distanceTo(at) <= radius && (!facing || angleOk(part, it)) }
        .minByOrNull { it.pos.distanceTo(at) }

    /**
     * v0.10.1: a held part rides on top of what is already built, so it may be over its slot but higher than [radius]:
     * the nearest ready slot of its type within [radius] across (x, z) that lies below [at] (or within [radius] above).
     */
    fun snapTargetOver(part: Int, at: Vec3, radius: Double, facing: Boolean = true): Slot? = slots
        .filter { it.type == parts[part] && ready(it) && (!facing || angleOk(part, it)) && it.pos.y <= at.y + radius && flat(it.pos, at) <= radius }
        .minByOrNull { flat(it.pos, at) }

    private fun flat(a: Vec3, b: Vec3) = kotlin.math.hypot(a.x - b.x, a.z - b.z)

    /** Nearest slot of any kind within [radius] of [at], to explain a refusal. */
    fun nearest(at: Vec3, radius: Double): Slot? = slots.filter { it.pos.distanceTo(at) <= radius }.minByOrNull { it.pos.distanceTo(at) }

    /** Puts [part] into [slot] (the way it faces is not checked here: [snapTarget] does that for the hand). */
    fun place(part: Int, slot: Slot) {
        require(refusal(part, slot) == null) { "${parts[part]} cannot go into ${slot.id}" }
        slotOf[part] = slot.id
        partIn[slot.id] = part
        // v0.10.1: every build turns a part to its slot (0.10.0 only did for the 斗拱, so the 指南车's upright gears and the
        // 香炉's inner ring went in lying the way they lay on the bench).
        setYaw(part, if (symmetry != null) settledYaw(part, slot) else slot.yaw)
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

    companion object {
        /** How far off its slot's turn a part may be and still go in (degrees). */
        const val ANGLE_TOL = 20.0

        fun angleDiff(a: Double, b: Double): Double {
            val d = (((a - b) % 360) + 360) % 360
            return minOf(d, 360 - d)
        }
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
