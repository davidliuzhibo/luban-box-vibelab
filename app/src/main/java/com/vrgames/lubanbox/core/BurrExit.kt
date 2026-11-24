package com.vrgames.lubanbox.core

import kotlin.math.abs
import kotlin.math.sqrt

/** Where a stick lies once it is out: its middle, which way it lies, and its turn from its place in the lock. */
data class RestPose(val slot: Int, val center: Vec3, val alongX: Boolean, val turn: TurnAxis, val deg: Double)

/** A stick on its way out: its middle and how far it has turned (degrees about [turn]). */
data class StickPose(val center: Vec3, val turn: TurnAxis, val deg: Double)

/**
 * The way a freed stick takes from the lock to the turntable: through [keys] in turn, each leg eased
 * in and out, reaching key k at [times][k] seconds.
 */
class ExitPath(val stick: Int, val keys: List<StickPose>, val times: DoubleArray, val rest: RestPose, val mode: String) {
    val duration get() = times.last()

    fun at(t: Double): StickPose {
        if (t <= 0) return keys.first()
        for (k in 1 until keys.size) {
            if (t <= times[k]) {
                val span = times[k] - times[k - 1]
                val u = if (span <= 0) 1.0 else ((t - times[k - 1]) / span).coerceIn(0.0, 1.0)
                val e = u * u * (3 - 2 * u)
                val a = keys[k - 1]
                val b = keys[k]
                return StickPose(a.center + (b.center - a.center) * e, b.turn, a.deg + (b.deg - a.deg) * e)
            }
        }
        return keys.last()
    }
}

/**
 * v0.10 (PICO 2026-11-23: freed sticks flew straight through the others to the turntable). Plans the
 * way out for a stick of the 鲁班锁 that has just come free, in the lock's frame (meters, origin at
 * the lock's middle, the turntable top at [tableY]):
 *
 * 1. it keeps sliding along its own axis until it is clear of the lock's bounding box by [MARGIN];
 * 2. if it came out up or down, it moves sideways out from over / under the lock;
 * 3. only once clear by its whole turning radius does it turn to lie flat (raised first if the turn
 *    would sweep down onto the sticks already lying there);
 * 4. it goes down to a carrying height just above the sticks already out, travels round the lock (or
 *    under it, when there is room) to its place, and is set down.
 *
 * The place is the first free one of [slots] it can reach that way: normally a row on the turntable
 * under the lock. Every straight leg stays outside the box of the sticks still in the lock, above
 * the sticks already out and above the turntable, so no part of it passes through anything; the
 * tests sample whole paths against the sticks' real cells.
 */
class BurrExit(val burr: Burr, val tableY: Double, val cell: Double = Burrs.CELL) {
    /** Each stick's wood as boxes (runs of cells along its length), in the lock's frame at offset zero. */
    val boxes: List<List<Aabb>> = burr.pieces.map { p -> runs(p) }

    /** Each stick's bounding box at offset zero (the full 2 × 2 × 8 bar). */
    val bounds: List<Aabb> = boxes.map { b -> b.reduce(Aabb::union) }

    /** Top of the sticks lying on the turntable, and the carrying height's bottom just above them. */
    val restTop get() = tableY + 2 * cell + REST_LIFT
    private val travelBottom get() = restTop + TRAVEL_GAP

    /** Where freed sticks may lie, in order of preference (see [restCandidates]). */
    private val slots: List<Pair<Vec3, Boolean>> = buildList {
        // The row on the turntable under the lock, side by side along x.
        for (k in 0 until 6) add(Vec3(0.0, tableY + cell + REST_LIFT, -0.14 + 0.056 * k) to true)
        // Rare, when the lock hangs too low to pass under: the edge of the turntable, then the bench beside it.
        add(Vec3(0.0, tableY + cell + REST_LIFT, 0.145) to true)
        add(Vec3(0.0, tableY + cell + REST_LIFT, -0.145) to true)
        add(Vec3(0.145, tableY + cell + REST_LIFT, 0.0) to false)
        add(Vec3(-0.145, tableY + cell + REST_LIFT, 0.0) to false)
        // On the bench, right of the turntable (the left end holds the chapter tokens and the menu tab).
        val bench = tableY - TURNTABLE_H + cell + REST_LIFT
        for (z in listOf(-0.13, 0.13)) for (x in listOf(0.32, 0.38, 0.44)) add(Vec3(x, bench, z) to false)
    }

    /** Stick [i]'s offset (meters) from where it sits in the assembled lock, now. */
    fun offsetOf(i: Int): Vec3 = Vec3(burr.offset(i, Axis.X).toDouble(), burr.offset(i, Axis.Y).toDouble(), burr.offset(i, Axis.Z).toDouble()) * cell

    /** The boxes of every stick still in the lock (except [except]) where they are now. */
    fun inLockBoxes(except: Int = -1): List<Aabb> = burr.pieces.indices
        .filter { it != except && !burr.removed(it) }
        .flatMap { j -> val d = offsetOf(j); boxes[j].map { it.moved(d) } }

    /** Bounding box of the sticks still in the lock (null once none are). */
    fun lockBounds(except: Int = -1): Aabb? = burr.pieces.indices
        .filter { it != except && !burr.removed(it) }
        .map { j -> bounds[j].moved(offsetOf(j)) }
        .reduceOrNull(Aabb::union)

    /**
     * Which way stick [i] (just out) leaves: the axis it is now clear of the others on, and on which side.
     * That is not always the way it last moved: a stick pushed sideways toward the last other one can be
     * clear of it already, on the far side (it then leaves away from it). Prefers [moved] when it is clear
     * along that axis.
     */
    fun exitWay(i: Int, moved: Axis?, movedDir: Int): Pair<Axis, Int> {
        val lock = lockBounds(except = i) ?: return (moved ?: Axis.Y) to (if (movedDir < 0) -1 else 1)
        val box = bounds[i].moved(offsetOf(i))
        val ways = (0..2).mapNotNull { a ->
            when {
                comp(box.lo, a) >= comp(lock.hi, a) - 1e-9 -> a to 1
                comp(box.hi, a) <= comp(lock.lo, a) + 1e-9 -> a to -1
                else -> null
            }
        }
        val (a, d) = ways.firstOrNull { it.first == moved?.ordinal } ?: ways.firstOrNull() ?: ((moved?.ordinal ?: 1) to (if (movedDir < 0) -1 else 1))
        return Axis.entries[a] to d
    }

    /** The quarter turn that lays stick [i] flat along x ([alongX]) or along z. */
    fun turnFor(i: Int, alongX: Boolean): Pair<TurnAxis, Double> = when (burr.pieces[i].axis) {
        Axis.X -> if (alongX) TurnAxis.NONE to 0.0 else TurnAxis.Y to 90.0
        Axis.Y -> if (alongX) TurnAxis.Z to 90.0 else TurnAxis.X to 90.0
        Axis.Z -> if (alongX) TurnAxis.Y to 90.0 else TurnAxis.NONE to 0.0
    }

    /** Half extents of a stick lying along x or z. */
    private fun flatHalf(alongX: Boolean) = if (alongX) Vec3(LENGTH_HALF, cell, cell) else Vec3(cell, cell, LENGTH_HALF)
    private val LENGTH_HALF get() = Burr.LENGTH * cell / 2

    /** Footprint box of a resting stick. */
    fun restBox(r: RestPose): Aabb {
        val h = flatHalf(r.alongX)
        return Aabb(r.center - h, r.center + h)
    }

    /** Every slot, as stick [i] would lie there. */
    fun restCandidates(i: Int): List<RestPose> = slots.mapIndexed { k, (c, alongX) ->
        val (axis, deg) = turnFor(i, alongX)
        RestPose(k, c, alongX, axis, deg)
    }

    /** Whether stick [i] can be brought to [r] now (free, and reachable round or under [lock]). */
    fun canRest(i: Int, r: RestPose, lock: Aabb?, resting: Map<Int, RestPose>): Boolean {
        val box = restBox(r)
        if (resting.any { (j, o) -> j != i && overlaps(box, restBox(o), REST_GAP) }) return false
        if (lock == null) return true
        val h = flatHalf(r.alongX)
        if (passesUnder(h, lock)) return true
        return outside(r.center, inflate(lock, h))
    }

    /** The first place stick [i] can go now. */
    fun restFor(i: Int, resting: Map<Int, RestPose>): RestPose {
        val lock = lockBounds(except = i)
        val all = restCandidates(i)
        return all.firstOrNull { canRest(i, it, lock, resting) } ?: all.last()
    }

    /** Carrying the stick flat at the carrying height, its top clears the lock's underside. */
    private fun passesUnder(h: Vec3, lock: Aabb) = travelBottom + 2 * h.y <= lock.lo.y - MARGIN

    /** The lock's footprint grown by a flat stick's half size and the margin. */
    private fun inflate(lock: Aabb, h: Vec3) = Aabb(lock.lo - Vec3(h.x + MARGIN, 0.0, h.z + MARGIN), lock.hi + Vec3(h.x + MARGIN, 0.0, h.z + MARGIN))

    /**
     * The way out for stick [i], which has just come free moving along [axis] in direction [dir]
     * (+1 / -1). [from]: where it is shown now (offset in cells, possibly between whole steps).
     * [resting]: where the sticks already out lie (or are flying to).
     */
    fun plan(i: Int, axis: Axis, dir: Int, from: Vec3, resting: Map<Int, RestPose>): ExitPath {
        val lock = lockBounds(except = i)
        val rest = restFor(i, resting)
        val loc = bounds[i]
        val h = loc.half
        val c0 = loc.center + from * cell
        val cExit = loc.center + offsetOf(i)
        val d = unit(axis) * dir.toDouble()
        val keys = ArrayList<StickPose>()
        val turn = rest.turn
        fun add(c: Vec3, deg: Double = 0.0) {
            val last = keys.lastOrNull()
            if (last == null || last.center.distanceTo(c) > 1e-6 || last.deg != deg) keys += StickPose(c, turn, deg)
        }
        add(c0)
        // 1. Keep sliding along the axis until clear of the lock's box by the margin.
        val a = axis.ordinal
        fun along(v: Vec3) = comp(v, a) * dir
        var c1 = if (along(cExit) > along(c0)) cExit else c0
        val beyond = if (lock == null) along(cExit) + 0.02 else if (dir > 0) comp(lock.hi, a) + MARGIN + comp(h, a) else -(comp(lock.lo, a) - MARGIN - comp(h, a))
        if (beyond > along(c1)) c1 = c1 + d * (beyond - along(c1))
        add(c1)
        // 2. Up or down: sideways out from over / under the lock, the shortest way (toward its place on a tie).
        var c2 = c1
        var side = d
        if (axis == Axis.Y && lock != null) {
            val options = listOf(Vec3(1.0, 0.0, 0.0), Vec3(-1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, -1.0)).map { u ->
                val need = when {
                    u.x > 0 -> lock.hi.x + MARGIN + h.x - c1.x
                    u.x < 0 -> c1.x - (lock.lo.x - MARGIN - h.x)
                    u.z > 0 -> lock.hi.z + MARGIN + h.z - c1.z
                    else -> c1.z - (lock.lo.z - MARGIN - h.z)
                }
                Triple(u, maxOf(need, 0.0), -(rest.center - c1).dot(u))
            }
            val best = options.minWith(compareBy<Triple<Vec3, Double, Double>> { (it.second * 1000).toInt() }.thenBy { it.third })
            side = best.first
            c2 = c1 + side * best.second
            add(c2)
        }
        // 3. Turn only once clear of the lock by the whole turning radius, and high enough not to sweep the sticks below.
        var c3 = c2
        if (turn != TurnAxis.NONE) {
            val reach = sqrt(h.x * h.x + h.y * h.y + h.z * h.z) + MARGIN
            if (lock != null) {
                val sideAxis = if (abs(side.x) > 0.5) 0 else 2
                val perp = if (sideAxis == 0) maxOf(lock.lo.z - c3.z, 0.0, c3.z - lock.hi.z) else maxOf(lock.lo.x - c3.x, 0.0, c3.x - lock.hi.x)
                val gapNeeded = if (perp >= reach) 0.0 else sqrt(reach * reach - perp * perp)
                val s = if (comp(side, sideAxis) > 0) 1.0 else -1.0
                val gapNow = if (s > 0) comp(c3, sideAxis) - comp(lock.hi, sideAxis) else comp(lock.lo, sideAxis) - comp(c3, sideAxis)
                if (gapNow < gapNeeded) c3 = c3 + side * (gapNeeded - gapNow)
                add(c3)
            }
            // Its lowest point while turning stays the margin above the sticks lying out.
            val low = restTop + reach
            if (c3.y < low) {
                c3 = Vec3(c3.x, low, c3.z)
                add(c3)
            }
            add(c3, rest.deg)
        }
        // 4. Down (or up) to the carrying height, across to the place, and down onto it.
        val hr = flatHalf(rest.alongX)
        val carry = travelBottom + hr.y
        val c5 = Vec3(c3.x, carry, c3.z)
        add(c5, rest.deg)
        val above = Vec3(rest.center.x, carry, rest.center.z)
        val under = lock == null || passesUnder(hr, lock)
        val mode = if (under) "under" else "around"
        if (!under) for (p in around(c5, above, inflate(lock!!, hr))) add(Vec3(p.x, carry, p.z), rest.deg)
        add(above, rest.deg)
        add(rest.center, rest.deg)
        // Times: each leg at an easy hand speed, the turn a little slower.
        val times = DoubleArray(keys.size)
        for (k in 1 until keys.size) {
            val dist = keys[k].center.distanceTo(keys[k - 1].center)
            val leg = if (keys[k].deg != keys[k - 1].deg) TURN_S else maxOf(MIN_LEG_S, dist / SPEED)
            times[k] = times[k - 1] + leg
        }
        return ExitPath(i, keys, times, rest, mode)
    }

    /** The stick's wood as oriented boxes at [pose]. */
    fun obbs(i: Int, pose: StickPose): List<Obb> {
        val c = bounds[i].center
        val axes = Aabb.AXES.map { rotate(it, pose.turn, pose.deg) }
        return boxes[i].map { b -> Obb(pose.center + rotate(b.center - c, pose.turn, pose.deg), axes, b.half) }
    }

    fun restObbs(i: Int, r: RestPose) = obbs(i, StickPose(r.center, r.turn, r.deg))

    /**
     * Nearest approach (meters, a lower bound) of a path: [slide] to the sticks in the lock while it slides
     * out along its axis (its faces run along theirs: zero is touching, below zero would be inside),
     * [inLock] to them for the rest of the way, then to the sticks lying out and to the table.
     */
    data class Clearance(val slide: Double, val inLock: Double, val resting: Double, val table: Double) {
        val min get() = minOf(inLock, resting, table)
        override fun toString() = "slide=${mm(slide)} inLock=${mm(inLock)} resting=${mm(resting)} table=${mm(table)}"
        private fun mm(v: Double) = if (v.isInfinite()) "none" else "%.1fmm".format(v * 1000)
    }

    /**
     * Samples [path] every [step] meters (and every 3° of its turn) and measures how close it comes to
     * the sticks in the lock, the sticks resting in [resting] (other than itself) and the turntable
     * and bench top.
     */
    fun clearance(path: ExitPath, resting: Map<Int, RestPose>, step: Double = 0.002): Clearance {
        val i = path.stick
        val lock = inLockBoxes(except = i).map { it.obb() }
        val lockBox = lockBounds(except = i)
        val lying = resting.filter { it.key != i }.flatMap { (j, r) -> restObbs(j, r) }
        val table = Aabb(Vec3(-TURNTABLE_R, tableY - TURNTABLE_H, -TURNTABLE_R), Vec3(TURNTABLE_R, tableY, TURNTABLE_R)).obb()
        val benchTop = tableY - TURNTABLE_H
        var cs = Double.POSITIVE_INFINITY
        var cl = Double.POSITIVE_INFINITY
        var cr = Double.POSITIVE_INFINITY
        var ct = Double.POSITIVE_INFINITY
        for (k in 1 until path.keys.size) {
            val a = path.keys[k - 1]
            val b = path.keys[k]
            val n = maxOf(1, (a.center.distanceTo(b.center) / step).toInt(), (abs(b.deg - a.deg) / 3).toInt())
            for (s in 0..n) {
                val u = s.toDouble() / n
                val pose = StickPose(a.center + (b.center - a.center) * u, b.turn, a.deg + (b.deg - a.deg) * u)
                val moving = obbs(i, pose)
                // Whole-stick shadow first: far from the lock's box, no cell can be near.
                val whole = Obb(pose.center, moving.first().axes, bounds[i].half)
                if (lockBox != null) {
                    val far = whole.gap(lockBox.obb())
                    val g = if (far > 0.03) far else moving.minOf { m -> lock.minOf { o -> m.gap(o) } }
                    if (k == 1) cs = minOf(cs, g) else cl = minOf(cl, g)
                }
                for (m in moving) {
                    for (o in lying) cr = minOf(cr, m.gap(o))
                    ct = minOf(ct, m.gap(table), m.bottom - benchTop)
                }
            }
        }
        return Clearance(cs, cl, cr, ct)
    }

    private fun unit(a: Axis) = when (a) {
        Axis.X -> Vec3(1.0, 0.0, 0.0)
        Axis.Y -> Vec3(0.0, 1.0, 0.0)
        Axis.Z -> Vec3(0.0, 0.0, 1.0)
    }

    private fun comp(v: Vec3, a: Int) = when (a) {
        0 -> v.x
        1 -> v.y
        else -> v.z
    }

    /** Runs of cells along the stick's length, each as one box (meters). */
    private fun runs(p: BurrPiece): List<Aabb> {
        val a = p.axis.ordinal
        fun c(cell: Cell, k: Int) = when (k) {
            0 -> cell.x
            1 -> cell.y
            else -> cell.z
        }
        val (u, v) = (0..2).filter { it != a }
        val out = ArrayList<Aabb>()
        for ((key, cs) in p.cells.groupBy { c(it, u) to c(it, v) }) {
            val ks = cs.map { c(it, a) }.sorted()
            var start = ks.first()
            var prev = start
            fun flush() {
                val lo = IntArray(3)
                val hi = IntArray(3)
                lo[a] = start; hi[a] = prev + 1
                lo[u] = key.first; hi[u] = key.first + 1
                lo[v] = key.second; hi[v] = key.second + 1
                out += Aabb(Vec3(lo[0] * cell, lo[1] * cell, lo[2] * cell), Vec3(hi[0] * cell, hi[1] * cell, hi[2] * cell))
            }
            for (k in ks.drop(1)) {
                if (k != prev + 1) { flush(); start = k }
                prev = k
            }
            flush()
        }
        return out
    }

    companion object {
        /** Clearance kept from the lock's box while sliding out and going round it. */
        const val MARGIN = 0.015
        /** Resting sticks float this little above the turntable (no z-fighting with its top). */
        const val REST_LIFT = 0.002
        /** The carrying height is this far above the sticks already out. */
        const val TRAVEL_GAP = 0.02
        /** Gap kept between resting sticks. */
        const val REST_GAP = 0.004
        const val SPEED = 0.45
        const val MIN_LEG_S = 0.12
        const val TURN_S = 0.4
        /** The turntable (Bench.TURNTABLE_R / _H). */
        const val TURNTABLE_R = 0.2
        const val TURNTABLE_H = 0.03

        private fun overlaps(a: Aabb, b: Aabb, gap: Double) =
            a.lo.x < b.hi.x + gap && b.lo.x < a.hi.x + gap && a.lo.y < b.hi.y + gap && b.lo.y < a.hi.y + gap && a.lo.z < b.hi.z + gap && b.lo.z < a.hi.z + gap

        /** Whether [p] (x, z) lies outside the rectangle [r]. */
        fun outside(p: Vec3, r: Aabb) = p.x <= r.lo.x || p.x >= r.hi.x || p.z <= r.lo.z || p.z >= r.hi.z

        /**
         * Shortest way in the floor plane from [a] to [b] (both outside the rectangle [r]) that does not
         * cut through [r]: the corner points to pass (empty when the straight line is clear).
         */
        fun around(a: Vec3, b: Vec3, r: Aabb): List<Vec3> {
            val e = 0.002
            val corners = listOf(
                Vec3(r.lo.x - e, 0.0, r.lo.z - e), Vec3(r.hi.x + e, 0.0, r.lo.z - e),
                Vec3(r.hi.x + e, 0.0, r.hi.z + e), Vec3(r.lo.x - e, 0.0, r.hi.z + e),
            )
            val nodes = listOf(Vec3(a.x, 0.0, a.z)) + corners + listOf(Vec3(b.x, 0.0, b.z))
            val n = nodes.size
            val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
            val prev = IntArray(n) { -1 }
            val done = BooleanArray(n)
            dist[0] = 0.0
            repeat(n) {
                val u = (0 until n).filter { !done[it] }.minByOrNull { dist[it] } ?: return@repeat
                if (dist[u].isInfinite()) return@repeat
                done[u] = true
                for (v in 0 until n) if (!done[v] && !cuts(nodes[u], nodes[v], r)) {
                    val w = dist[u] + nodes[u].distanceTo(nodes[v])
                    if (w < dist[v]) { dist[v] = w; prev[v] = u }
                }
            }
            val path = ArrayList<Vec3>()
            var k = prev[n - 1]
            while (k > 0) { path.add(0, nodes[k]); k = prev[k] }
            return path
        }

        /** Whether the segment [p]–[q] passes through the inside of rectangle [r] (x, z). */
        private fun cuts(p: Vec3, q: Vec3, r: Aabb): Boolean {
            // Liang–Barsky clip against the open rectangle.
            var t0 = 0.0
            var t1 = 1.0
            val dx = q.x - p.x
            val dz = q.z - p.z
            for ((pp, qq) in listOf(-dx to p.x - r.lo.x, dx to r.hi.x - p.x, -dz to p.z - r.lo.z, dz to r.hi.z - p.z)) {
                if (abs(pp) < 1e-12) {
                    if (qq <= 0) return false
                } else {
                    val t = qq / pp
                    if (pp < 0) t0 = maxOf(t0, t) else t1 = minOf(t1, t)
                }
            }
            return t1 - t0 > 1e-9
        }
    }
}
