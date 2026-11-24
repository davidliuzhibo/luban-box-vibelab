package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Assembly
import com.vrgames.lubanbox.core.Slot
import com.vrgames.lubanbox.core.Vec3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Shows an [Assembly] (斗拱) under [root]: loose parts laid out on the bench around the build spot,
 * and a faint outline wherever a part may go now. A part follows the hand freely; let go near a
 * slot that is ready for it and it settles in.
 *
 * v0.10 (PICO 2026-11-23: some 斗拱 parts lay crosswise and could not be turned): when the assembly
 * [Assembly.orients], a short pinch on a loose part turns it a quarter turn ([turn]); it keeps that
 * turn while carried and snaps in only facing the right way. Let go near its slot facing the wrong
 * way and it hops back off the slot ([Drop.Refused] with [Assembly.Refusal.WRONG_ANGLE]). Parts
 * lying on the bench never pass through each other: one turned on the bench lifts clear of its
 * neighbours while it turns, and a part whose place is taken lies on top of what is there.
 *
 * [visual] makes a part's model (origin at the middle of its underside, like the slots).
 */
class AssemblyView(
    val assembly: Assembly,
    val root: Entity,
    private val assets: Assets,
    visual: (type: String) -> Entity,
    /** Collision box of each part type: size, and center above the part's origin. */
    private val hitBox: (type: String) -> Pair<Vector3, Vector3>,
    /** Where each loose part lies on the bench (object frame) and its yaw there (degrees). */
    private val tray: List<Pair<Vec3, Double>>,
    /** Height of the bench top in the object frame: parts never go below it. */
    private val floorY: Double,
    /**
     * v0.10.1: for a build inside a frame with a roof (水运仪象台), the z of the frame's open front: parts go into their
     * slots from the front, sliding in level, instead of from above. Null: from above.
     */
    private val frontOpening: Double? = null,
    /** Sideways offset of a slot's lane in from the front (it moves over once level with its place). */
    private val frontShift: (Slot) -> Double = { 0.0 },
    /**
     * v0.10.1: the fixed parts of the build (column, stand, frame...) and the turntable, as boxes in the relic's frame.
     * A part in the hand rides over these and over every other part, never into them.
     */
    private val fixed: List<com.vrgames.lubanbox.core.Aabb> = emptyList(),
) {
    private val n = assembly.parts.size
    private val holders = assembly.parts.map { Entity() }
    private val pos = Array(n) { trayPosition(it) }
    private val goal = arrayOfNulls<Vec3>(n)
    /**
     * Waypoints still ahead after [goal] (v0.10.1): a part going into its slot, hopping off a slot or going back to the
     * bench rises clear of everything in its way, moves over, and comes straight down, instead of flying straight
     * through the parts already there.
     */
    private val route = Array(n) { ArrayDeque<Vec3>() }
    /** Moved by the scene (the 被中香炉's rings and bowl going in, v0.10.1): this view leaves them alone. */
    private val external = BooleanArray(n)
    /** The turn shown now (follows [Assembly.yawOf] in a short animation). */
    private val shownYaw = DoubleArray(n) { tray[it].second }
    private val outlines = assembly.slots.associateWith { slot -> visual(slot.type).also { Assets.setMaterials(it, assets.slotOpen) } }
    private val sizes = assembly.parts.map { hitBox(it) }

    /** Lying on the bench top (or on another part there), as opposed to held, in place or left in the air. */
    private val onBench = BooleanArray(n) { true }
    /** Order parts were laid down in: a later one lies on top of an earlier one it overlaps. */
    private val laid = IntArray(n) { it }
    private var nextLaid = n

    /** A quarter turn in progress: its age (s), the yaw it started from, how high it lifts and from where. */
    private val turnAge = DoubleArray(n) { -1.0 }
    private val turnFrom = DoubleArray(n)
    private val turnLift = DoubleArray(n)
    private val turnBase = DoubleArray(n)
    /** Where a part turned on the bench comes down (worked out as it starts down: it may now lie across another). */
    private val turnLand = DoubleArray(n) { Double.NaN }

    var dragging: Int? = null; private set
    /** The slot under the part in hand, if it would take it. */
    var near: Slot? = null; private set
    /** Where the part in hand was picked up, and how far the hand has strayed from there (meters). */
    private var startPos = Vec3.ZERO
    private var hand = Vec3.ZERO
    var handTravel = 0.0; private set

    init {
        assembly.parts.forEachIndexed { i, type ->
            if (!assembly.isPlaced(i)) assembly.setYaw(i, tray[i].second)
            val h = holders[i]
            h.transform().eulerAngles = EulerAngles(0f, shownYaw[i].toFloat(), 0f)
            h.addChild(visual(type))
            val (size, center) = sizes[i]
            h.components.set(CollisionComponent(listOf(ShapeResource.createBox(size).offsetByTranslation(center)), PhysicsMaterialResource()))
            h.components.set(InteractableComponent())
            h.components.set(HoverEffectComponent())
            root.addChild(h)
            h.setPos(pos[i])
        }
        for ((slot, o) in outlines) {
            root.addChild(o)
            o.setPos(slot.pos)
            o.transform().eulerAngles = EulerAngles(0f, slot.yaw.toFloat(), 0f)
            o.enabled = false
        }
    }

    fun partOf(e: Entity?): Int? {
        var cur = e
        while (cur != null) {
            val i = holders.indexOfFirst { it === cur }
            if (i >= 0) return i
            cur = cur.getParent()
        }
        return null
    }

    fun holder(i: Int) = holders[i]
    fun position(i: Int) = pos[i]
    fun isTurning(i: Int) = turnAge[i] >= 0

    fun begin(i: Int): Boolean {
        if (assembly.isPlaced(i)) return false
        dragging = i
        goal[i] = null
        onBench[i] = false
        // A turn on the bench finishes in the hand.
        if (turnAge[i] >= 0) turnLift[i] = 0.0
        startPos = pos[i]
        hand = Vec3.ZERO
        handTravel = 0.0
        showOutlines(i)
        return true
    }

    /** [d]: hand movement in the object frame. The part stays above the bench top. */
    fun drag(d: Vec3) {
        val i = dragging ?: return
        hand += d
        handTravel = maxOf(handTravel, hand.length())
        val p = pos[i] + d
        // v0.10.1: it rides over whatever is under it (built parts, parts lying there, the column or frame, the
        // turntable), never into it.
        pos[i] = Vec3(p.x, liftY(i, p), p.z)
        holders[i].setPos(pos[i])
        updateNear(i)
    }

    /** Part [i]'s box relative to its origin, turned as it is now (quarter turns). */
    private fun relBox(i: Int): com.vrgames.lubanbox.core.Aabb {
        val f = footprint(i, Vec3.ZERO)
        val (size, center) = sizes[i]
        return com.vrgames.lubanbox.core.Aabb(Vec3(f.first, center.y - size.y / 2.0, f.third), Vec3(f.second, center.y + size.y / 2.0, f.fourth))
    }

    /** Part [j]'s box where it is now (or is going, if settling), in the relic's frame. */
    private fun worldBox(j: Int): com.vrgames.lubanbox.core.Aabb {
        val at = if (route[j].isEmpty()) goal[j] ?: pos[j] else pos[j]
        val b = relBox(j)
        return com.vrgames.lubanbox.core.Aabb(b.lo + at, b.hi + at)
    }

    /** The lowest height part [i]'s origin may have over [at] (x, z): on top of everything under it. */
    fun liftY(i: Int, at: Vec3): Double {
        val obstacles = ArrayList(fixed)
        for (j in 0 until n) if (j != i) obstacles += worldBox(j)
        return com.vrgames.lubanbox.core.liftOver(relBox(i), at, obstacles, floorY)
    }

    /**
     * The slot part [i] would go into if let go now: near its slot, or (v0.10.1) riding on top of what is built right
     * over its slot (it cannot be pushed lower than that).
     */
    private fun snapAt(i: Int, facing: Boolean = true): Slot? {
        assembly.snapTarget(i, pos[i], SNAP, facing)?.let { return it }
        if (pos[i].y - liftY(i, Vec3(pos[i].x, floorY, pos[i].z)) > SETTLE_ABOVE) return null
        return assembly.snapTargetOver(i, pos[i], SNAP, facing)
    }

    private fun updateNear(i: Int) {
        val slot = snapAt(i)
        if (slot != near) {
            near?.let { outlines[it]?.let { o -> Assets.setMaterials(o, assets.slotOpen) } }
            slot?.let { outlines[it]?.let { o -> Assets.setMaterials(o, assets.slotNear) } }
            near = slot
        }
    }

    /** Result of letting go of a part. */
    sealed interface Drop {
        data class Placed(val part: Int, val slot: Slot) : Drop
        /** Near a slot it cannot take (yet), or facing the wrong way: why, for the notes. */
        data class Refused(val part: Int, val slot: Slot, val why: Assembly.Refusal) : Drop
        /** A short pinch that did not carry the part: it turned (the new yaw). */
        data class Turned(val part: Int, val yaw: Double) : Drop
        data object Loose : Drop
    }

    fun end(): Drop {
        val i = dragging ?: return Drop.Loose
        dragging = null
        near = null
        outlines.values.forEach { it.enabled = false }
        val slot = snapAt(i)
        if (slot != null) {
            assembly.place(i, slot)
            routeTo(i, slot.pos)
            return Drop.Placed(i, slot)
        }
        // Over its slot but facing the wrong way: it hops back off the slot, up and away.
        val facing = if (assembly.orients) snapAt(i, facing = false) else null
        if (facing != null) {
            val away = Vec3(pos[i].x - facing.pos.x, 0.0, pos[i].z - facing.pos.z).let { if (it.length() < 0.005) Vec3(0.0, 0.0, 1.0) else it.normalized() }
            // Straight up first, then off to the side.
            route[i].clear()
            goal[i] = pos[i] + Vec3(0.0, BOUNCE * 0.6, 0.0)
            val off = pos[i] + away * BOUNCE + Vec3(0.0, BOUNCE * 0.6, 0.0)
            route[i].addLast(Vec3(off.x, liftY(i, off), off.z))
            return Drop.Refused(i, facing, Assembly.Refusal.WRONG_ANGLE)
        }
        val close = assembly.nearest(pos[i], SNAP * 1.5)
        // Dropped far from the build: it goes back to its place on the bench.
        if (pos[i].distanceTo(Vec3.ZERO) > 0.6) {
            routeTo(i, trayPosition(i))
            lay(i)
        } else if (pos[i].y - restHeight(i, pos[i]) < SETTLE_ABOVE) {
            // Let go just over the bench or a part on it: it lies down there.
            lay(i)
        }
        val why = close?.let { assembly.refusal(i, it, facing = true) }
        return if (close != null && why != null) Drop.Refused(i, close, why) else Drop.Loose
    }

    /**
     * Ends a pinch that hardly moved the part as a tap: the part goes back to where it was picked up
     * (or stays in the hand's place if it was held in the air) and turns a quarter turn.
     */
    fun endAsTap(doTurn: Boolean = true): Drop {
        val i = dragging ?: return Drop.Loose
        dragging = null
        near = null
        outlines.values.forEach { it.enabled = false }
        pos[i] = startPos
        holders[i].setPos(pos[i])
        if (startPos.y - restHeight(i, startPos) < SETTLE_ABOVE) onBench[i] = true
        if (!doTurn) return Drop.Loose
        val y = turn(i) ?: return Drop.Loose
        return Drop.Turned(i, y)
    }

    /**
     * Turns loose part [i] a quarter turn (a short pinch on it, on the bench or in the hand). On the bench
     * it lifts just clear of the parts around it, turns, and lies down again. Null if it cannot turn
     * (in place already, or this build has no turning).
     */
    fun turn(i: Int): Double? {
        if (!assembly.orients || assembly.isPlaced(i)) return null
        val y = assembly.turn(i)
        // Always the quarter turn from the yaw it had (a second pinch mid-turn starts from where that one ends).
        turnFrom[i] = y - 90.0
        turnAge[i] = 0.0
        turnLand[i] = Double.NaN
        if (onBench[i] && dragging != i) {
            goal[i] = null
            turnBase[i] = pos[i].y
            // Clear of every part on the bench its turn sweeps over.
            val r = hypot(sizes[i].first.x.toDouble(), sizes[i].first.z.toDouble()) / 2
            var top = floorY
            for (j in 0 until n) if (j != i && onBench[j] && turnAge[j] < 0) {
                val b = footprint(j, pos[j])
                val dx = maxOf(b.first - pos[i].x, 0.0, pos[i].x - b.second)
                val dz = maxOf(b.third - pos[i].z, 0.0, pos[i].z - b.fourth)
                if (hypot(dx, dz) < r + 0.005) top = maxOf(top, pos[j].y + sizes[j].first.y)
            }
            turnLift[i] = maxOf(TURN_LIFT_MIN, top + 0.01 - pos[i].y)
        } else {
            turnLift[i] = 0.0
        }
        return y
    }

    fun frame(dt: Double) {
        val k = 1 - exp(-dt * 14)
        // Parts lying on the bench: each rests on the bench top or on the highest part under it laid before it.
        val order = (0 until n).filter { onBench[it] && it != dragging && turnAge[it] < 0 }.sortedBy { laid[it] }
        val restY = HashMap<Int, Double>()
        for (i in order) {
            // On its way somewhere: it lies down where its way ends.
            if (route[i].isNotEmpty()) continue
            val at = goal[i] ?: pos[i]
            val f = footprint(i, at)
            var y = floorY
            for ((j, yj) in restY) if (overlap(f, footprint(j, goal[j] ?: pos[j]))) y = maxOf(y, yj + sizes[j].first.y)
            restY[i] = y
            if (abs(at.y - y) > 1e-4) goal[i] = Vec3(at.x, y, at.z)
        }
        for (i in holders.indices) {
            if (external[i]) continue
            if (turnAge[i] >= 0) {
                animateTurn(i, dt)
                if (i == dragging) continue
                if (turnLift[i] > 0) continue
            }
            val g = goal[i] ?: continue
            if (i == dragging) continue
            // Going down into a slot: only once it faces the slot's way (it turns while up in the clear).
            val last = route[i].isEmpty()
            if (last && assembly.isPlaced(i) && Assembly.angleDiff(shownYaw[i], assembly.yawOf(i)) > 0.5 && pos[i].y > g.y + 1e-3) continue
            pos[i] = pos[i] + (g - pos[i]) * k
            if (!last && pos[i].distanceTo(g) < 0.004) {
                // Waypoint reached: on to the next (from exactly here, so the way stays straight).
                pos[i] = g
                goal[i] = route[i].removeFirst()
            }
            holders[i].setPos(pos[i])
            if (pos[i].distanceTo(g) < 1e-4 && goal[i] === g) {
                pos[i] = g
                goal[i] = null
            }
        }
        for (i in holders.indices) {
            if (turnAge[i] >= 0 || external[i]) continue
            val target = assembly.yawOf(i)
            if (abs(Assembly.angleDiff(shownYaw[i], target)) > 0.05) {
                // Settling into a slot: the short way round.
                var d = target - shownYaw[i]
                while (d > 180) d -= 360
                while (d < -180) d += 360
                shownYaw[i] += d * k
                holders[i].transform().eulerAngles = EulerAngles(0f, shownYaw[i].toFloat(), 0f)
            }
        }
    }

    /** One step of a quarter turn: lift (on the bench), turn, set down. */
    private fun animateTurn(i: Int, dt: Double) {
        turnAge[i] += dt
        val t = (turnAge[i] / TURN_S).coerceIn(0.0, 1.0)
        fun ease(x: Double) = x * x * (3 - 2 * x)
        val lifted = turnLift[i] > 0
        val spin = if (lifted) ease(((t - 0.3) / 0.45).coerceIn(0.0, 1.0)) else ease(t)
        shownYaw[i] = turnFrom[i] + 90 * spin
        holders[i].transform().eulerAngles = EulerAngles(0f, shownYaw[i].toFloat(), 0f)
        if (lifted && i != dragging) {
            val top = turnBase[i] + turnLift[i]
            val y = when {
                t < 0.3 -> turnBase[i] + turnLift[i] * ease(t / 0.3)
                t < 0.75 -> top
                else -> {
                    // Turned, it comes down on whatever its new footprint covers (the bench, or a part lying there).
                    if (turnLand[i].isNaN()) turnLand[i] = restHeight(i, pos[i])
                    top + (turnLand[i] - top) * ease((t - 0.75) / 0.25)
                }
            }
            pos[i] = Vec3(pos[i].x, y, pos[i].z)
            holders[i].setPos(pos[i])
        }
        if (t >= 1.0) {
            turnAge[i] = -1.0
            shownYaw[i] = assembly.yawOf(i)
            holders[i].transform().eulerAngles = EulerAngles(0f, shownYaw[i].toFloat(), 0f)
            if (lifted && onBench[i] && i != dragging) lay(i)
        }
    }

    /** Places [part] in [slot] without a hand (AutoPilot), animated like a drop. */
    fun placeAuto(part: Int, slot: Slot) {
        assembly.place(part, slot)
        onBench[part] = false
        routeTo(part, slot.pos)
    }

    /**
     * Sends part [i] to [dest] the clear way (v0.10.1): up until its underside is [CLEAR] over everything standing in
     * the strip between here and there (placed parts and parts lying on the bench, by their boxes), across at that
     * height, then straight down. Parts go into their slots from above, the way they are put together.
     */
    private fun routeTo(i: Int, dest: Vec3) {
        route[i].clear()
        val front = frontOpening
        if (front != null && assembly.isPlaced(i)) {
            // Out to the front first (straight toward the player), across to the slot's lane, then in, level.
            val (size, center) = sizes[i]
            val back = center.z - size.z / 2.0
            val out = maxOf(pos[i].z, front + CLEAR - back)
            val lane = dest.y + FRONT_LIFT
            // From below its lane (lying on the bench beside the frame), it rises first.
            val start = if (pos[i].y < lane) Vec3(pos[i].x, lane, pos[i].z) else pos[i]
            val sx = dest.x + (assembly.slotOf(i)?.let(frontShift) ?: 0.0)
            val way = listOf(start, Vec3(start.x, start.y, out), Vec3(sx, lane, out), Vec3(sx, lane, dest.z), Vec3(dest.x, lane, dest.z), dest)
                .fold(ArrayList<Vec3>()) { acc, w -> if ((acc.lastOrNull() ?: pos[i]).distanceTo(w) > 1e-4) acc += w; acc }
            if (way.isEmpty()) way += dest
            goal[i] = way.first()
            for (w in way.drop(1)) route[i].addLast(w)
            return
        }
        val hover = hoverHeight(i, pos[i], dest)
        val up = Vec3(pos[i].x, hover, pos[i].z)
        val over = Vec3(dest.x, hover, dest.z)
        val way = ArrayList<Vec3>()
        if (hover - pos[i].y > 1e-3) way += up
        if (Vec3(over.x - up.x, 0.0, over.z - up.z).length() > 1e-3) way += over
        way += dest
        goal[i] = way.first()
        for (w in way.drop(1)) route[i].addLast(w)
        android.util.Log.i("LubanGeo", "route ${assembly.parts[i]} up to ${"%.3f".format(hover)} over to ${dest} (${way.size} legs)")
    }

    /** Height for part [i]'s origin to cross from [from] to [dest] over everything in between. */
    fun hoverHeight(i: Int, from: Vec3, dest: Vec3): Double {
        val a = footprint(i, from)
        val b = footprint(i, dest)
        val strip = Quad(minOf(a.first, b.first) - 0.005, maxOf(a.second, b.second) + 0.005, minOf(a.third, b.third) - 0.005, maxOf(a.fourth, b.fourth) + 0.005)
        val (size, center) = sizes[i]
        val under = center.y - size.y / 2.0
        var h = maxOf(from.y, dest.y + DROP)
        for (o in fixed) if (overlap(strip, Quad(o.lo.x, o.hi.x, o.lo.z, o.hi.z))) h = maxOf(h, o.hi.y + CLEAR - under)
        for (j in 0 until n) {
            if (j == i || j == dragging || route[j].isNotEmpty()) continue
            val at = goal[j] ?: pos[j]
            if (!overlap(strip, footprint(j, at))) continue
            val (sj, cj) = sizes[j]
            h = maxOf(h, at.y + cj.y + sj.y / 2.0 + CLEAR - under)
        }
        return h
    }

    /** The scene moves part [i] itself from now on (v0.10.1, the 被中香炉): its way here is dropped. Where it is. */
    fun takeOver(i: Int): Vec3 {
        external[i] = true
        route[i].clear()
        goal[i] = null
        return pos[i]
    }

    fun isExternal(i: Int) = external[i]

    /** While the scene has it: shows part [i] at [p], turned by [q]. */
    fun pose(i: Int, p: Vec3, q: com.pico.spatial.core.math.Quat) {
        pos[i] = p
        holders[i].setPos(p)
        holders[i].transform().quaternion = q
    }

    /** The scene is done with part [i]: it stands in its slot (or where it is), turned as the rules have it. */
    fun giveBack(i: Int) {
        external[i] = false
        pos[i] = assembly.slotOf(i)?.pos ?: pos[i]
        holders[i].setPos(pos[i])
        shownYaw[i] = assembly.yawOf(i)
        holders[i].transform().eulerAngles = EulerAngles(0f, shownYaw[i].toFloat(), 0f)
    }

    /** Debug / tests: the waypoints part [i] still has ahead (its current goal first). */
    fun way(i: Int): List<Vec3> = listOfNotNull(goal[i]) + route[i]

    /** Shows where a hint's part goes. */
    fun showSlot(slot: Slot?) {
        outlines.forEach { (s, o) -> o.enabled = s == slot }
    }

    fun snapAll() {
        for (i in holders.indices) {
            val s = assembly.slotOf(i)
            pos[i] = s?.pos ?: trayPosition(i)
            if (s == null) assembly.setYaw(i, tray[i].second)
            shownYaw[i] = assembly.yawOf(i)
            goal[i] = null
            route[i].clear()
            external[i] = false
            onBench[i] = s == null
            laid[i] = i
            turnAge[i] = -1.0
            holders[i].setPos(pos[i])
            holders[i].transform().eulerAngles = EulerAngles(0f, shownYaw[i].toFloat(), 0f)
        }
        nextLaid = n
    }

    /** Debug / tests: whether part [i] lies on the bench, and how high (object frame). */
    fun lying(i: Int) = onBench[i]

    private fun lay(i: Int) {
        onBench[i] = true
        laid[i] = nextLaid++
    }

    /** Height the part would lie at over [at] on the bench (top of what lies under it, else the bench top). */
    private fun restHeight(i: Int, at: Vec3): Double {
        val f = footprint(i, at)
        var y = floorY
        for (j in 0 until n) if (j != i && onBench[j] && turnAge[j] < 0 && overlap(f, footprint(j, goal[j] ?: pos[j]))) y = maxOf(y, (goal[j] ?: pos[j]).y + sizes[j].first.y)
        return y
    }

    /** Part [i]'s footprint at [at] as it is turned now: (x min, x max, z min, z max). */
    private fun footprint(i: Int, at: Vec3): Quad {
        val (size, center) = sizes[i]
        val q = ((Math.round(assembly.yawOf(i) / 90.0) % 4) + 4) % 4
        val (hx, hz) = if (q % 2 == 1L) size.z / 2.0 to size.x / 2.0 else size.x / 2.0 to size.z / 2.0
        // The box's middle turned with the part (yaw about +y, right-handed).
        val (cx, cz) = when (q) {
            1L -> center.z.toDouble() to -center.x.toDouble()
            2L -> -center.x.toDouble() to -center.z.toDouble()
            3L -> -center.z.toDouble() to center.x.toDouble()
            else -> center.x.toDouble() to center.z.toDouble()
        }
        return Quad(at.x + cx - hx, at.x + cx + hx, at.z + cz - hz, at.z + cz + hz)
    }

    data class Quad(val first: Double, val second: Double, val third: Double, val fourth: Double)

    private fun overlap(a: Quad, b: Quad) = a.first < b.second - 0.001 && b.first < a.second - 0.001 && a.third < b.fourth - 0.001 && b.third < a.fourth - 0.001

    private fun showOutlines(i: Int) {
        for ((slot, o) in outlines) {
            o.enabled = slot.type == assembly.parts[i] && assembly.ready(slot)
            Assets.setMaterials(o, assets.slotOpen)
        }
    }

    private fun trayPosition(i: Int): Vec3 = tray[i].first + Vec3(0.0, floorY, 0.0)

    companion object {
        /** Let go within this distance of a ready slot and the part settles in. */
        const val SNAP = 0.035
        /** A quarter turn takes this long (lift, turn, set down). */
        const val TURN_S = 0.45
        /** Lifted at least this much to turn on the bench. */
        const val TURN_LIFT_MIN = 0.012
        /** How far a wrongly turned part hops back off its slot. */
        const val BOUNCE = 0.045
        /** Let go this close over the bench or a part lying there, a part lies down. */
        const val SETTLE_ABOVE = 0.03
        /** A part crossing over others keeps its underside this far above them (v0.10.1). */
        const val CLEAR = 0.008
        /** It comes down into its slot from at least this high. */
        const val DROP = 0.02
        /** Sliding in from the front, a part is this much above its resting height (0: it slides on what it rests on). */
        const val FRONT_LIFT = 0.0
    }
}
