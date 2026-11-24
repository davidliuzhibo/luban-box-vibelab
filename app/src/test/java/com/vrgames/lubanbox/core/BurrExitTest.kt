package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * v0.10 (PICO 2026-11-23: sticks passed through each other on their way out of the 鲁班锁). Every way a
 * freed stick takes to the turntable is sampled every few millimetres against the real cells of the
 * sticks still in the lock, the sticks already lying out and the turntable / bench top.
 */
class BurrExitTest {
    private val tableY = -Burrs.LIFT

    /** Exits seen, by stick axis and the way it came out (e.g. "Y-" a vertical stick coming out downward). */
    private val seen = HashMap<String, Int>()
    private var worst = BurrExit.Clearance(1.0, 1.0, 1.0, 1.0)
    private var arounds = 0

    /**
     * Makes [m] on [b] the way the game does and, if a stick comes out, plans its way like SlideView
     * (from where it was shown: [fromPrev] the old offset as after a solver move, else the exit offset as
     * after a drag) and checks it.
     */
    private fun play(b: Burr, ex: BurrExit, m: Move, resting: MutableMap<Int, RestPose>, fromPrev: Boolean) {
        val before = DoubleArray(3) { b.offset(m.piece, Axis.entries[it]).toDouble() }
        val moved = if (m.to > b.offset(m.piece, m.axis)) 1 else -1
        b.move(m.piece, m.axis, m.to)
        if (!b.removed(m.piece)) return
        // As SlideView: the side it is clear on, from where it was shown (its last place, or where it came free).
        val (axis, dir) = ex.exitWay(m.piece, m.axis, moved)
        val from = if (fromPrev) Vec3(before[0], before[1], before[2]) else ex.offsetOf(m.piece) * (1 / Burrs.CELL)
        val path = ex.plan(m.piece, axis, dir, from, resting)
        check(b, ex, path, resting)
        resting[m.piece] = path.rest
        val key = "${b.pieces[m.piece].axis}:$axis${if (dir > 0) "+" else "-"}"
        seen[key] = (seen[key] ?: 0) + 1
        if (path.mode == "around") arounds++
        slotsUsed[path.rest.slot] = (slotsUsed[path.rest.slot] ?: 0) + 1
    }

    private fun check(b: Burr, ex: BurrExit, path: ExitPath, resting: Map<Int, RestPose>) {
        val c = ex.clearance(path, resting, step = 0.003)
        val what = "stick ${path.stick} (${b.pieces[path.stick].axis}) to slot ${path.rest.slot} ${path.mode}: $c"
        // Sliding out along its own axis it may run along the others' faces, never into them.
        assertTrue("slide: $what", c.slide > -1e-7)
        // Once out, it keeps (about) the margin from the lock's box: 15 mm by design; the sampled measure is a
        // lower bound that can read a little less while the stick is turned.
        assertTrue("in lock: $what", c.inLock >= BurrExit.MARGIN - 0.003)
        assertTrue("resting: $what", c.resting > 0.002)
        // Above the turntable all the way (it lies 2 mm over it at the end).
        assertTrue("table: $what", c.table >= BurrExit.REST_LIFT - 1e-6)
        // It turns only once clear: while turning it is as far from the lock as on the rest of the way.
        val ends = path.keys.zipWithNext().withIndex().filter { (_, p) -> p.first.deg != p.second.deg }
        assertTrue("one turn at most: $what", ends.size <= 1)
        // Its place does not overlap a stick already lying out.
        for ((j, r) in resting) if (j != path.stick) for (m in ex.restObbs(path.stick, path.rest)) for (o in ex.restObbs(j, r)) assertTrue("rest overlap $what", m.gap(o) > 0.003)
        worst = BurrExit.Clearance(minOf(worst.slide, c.slide), minOf(worst.inLock, c.inLock), minOf(worst.resting, c.resting), minOf(worst.table, c.table))
    }

    @Test
    fun theSolutionsWaysOutPassThroughNothing() {
        for (fromPrev in listOf(true, false)) {
            val b = Burrs.lock()
            val ex = BurrExit(b, tableY)
            val resting = HashMap<Int, RestPose>()
            val sol = b.solution()!!
            for (m in sol) play(b, ex, m, resting, fromPrev)
            assertTrue(b.solved)
            assertEquals(6, resting.size)
            // All six lie on the turntable, in the row under the lock.
            assertEquals((0 until 6).toSet(), resting.values.map { it.slot }.toSet())
            for (r in resting.values) {
                val box = ex.restBox(r)
                for (x in listOf(box.lo.x, box.hi.x)) for (z in listOf(box.lo.z, box.hi.z)) assertTrue(kotlin.math.hypot(x, z) < BurrExit.TURNTABLE_R)
            }
        }
        println("solution: worst $worst exits $seen")
    }

    /** Random play: sticks pushed every which way (also deep down and far sideways) before they come out. */
    @Test
    fun randomPlayWaysOutPassThroughNothing() {
        val exits = randomPlay(160, deep = false)
        assertTrue(exits > 300)
        // Downward exits of the vertical sticks (the ones that went through the turntable before) are covered.
        assertTrue(seen.keys.toString(), seen.keys.any { it.startsWith("Y:Y-") })
        assertTrue(seen.keys.toString(), seen.keys.any { it.startsWith("Y:Y+") })
        assertTrue(seen.keys.toString(), seen.keys.any { it.startsWith("X:X") } && seen.keys.any { it.startsWith("Z:Z") })
    }

    /**
     * Sticks pushed as low as they go: the lock then hangs too low to carry a stick under it, and the
     * stick goes round it instead (and, if the row under the lock cannot be reached, to the turntable's edge).
     */
    @Test
    fun whenTheLockHangsLowSticksGoRoundIt() {
        randomPlay(120, deep = true)
        assertTrue("ways round: $arounds", arounds > 0)
    }

    private fun randomPlay(games: Int, deep: Boolean): Int {
        val rnd = Random(if (deep) 7 else 20261123)
        var exits = 0
        repeat(games) { game ->
            val b = Burrs.lock()
            val ex = BurrExit(b, tableY)
            val resting = HashMap<Int, RestPose>()
            var steps = 0
            while (!b.solved && steps < 500) {
                steps++
                val moves = ArrayList<Pair<Move, Burr.State>>()
                b.moves(b.state()) { m, s -> moves += m to s }
                if (moves.isEmpty()) break
                val out = b.state().outCount(6)
                val freeing = moves.filter { it.second.outCount(6) > out }
                val m = when {
                    freeing.isNotEmpty() && rnd.nextDouble() < (if (deep) 0.15 else 0.35) -> freeing[rnd.nextInt(freeing.size)].first
                    // Deep play: often the move that takes a stick lowest (while it stays in).
                    deep && rnd.nextDouble() < 0.6 -> moves.filter { it.second.outCount(6) == out && it.first.axis == Axis.Y }
                        .minByOrNull { it.first.to }?.first ?: moves[rnd.nextInt(moves.size)].first
                    else -> moves[rnd.nextInt(moves.size)].first
                }
                val was = b.removed(m.piece)
                play(b, ex, m, resting, fromPrev = rnd.nextBoolean())
                if (!was && b.removed(m.piece)) exits++
                // Whatever the lock does, it never reaches down to the sticks lying out.
                ex.lockBounds()?.let { lock -> assertTrue("game $game: lock bottom ${lock.lo.y}", lock.lo.y > ex.restTop + 0.04) }
            }
        }
        println("random${if (deep) " deep" else ""}: $exits exits, worst $worst, ways round the lock $arounds, slots ${slotsUsed.toSortedMap()}, exits $seen")
        return exits
    }

    private val slotsUsed = HashMap<Int, Int>()

    /** A vertical stick that comes out downward first: found by search, then checked leg by leg. */
    @Test
    fun aStickComingOutDownwardTurnsOnlyOnceClear() {
        val b = Burrs.lock()
        var exit: Move? = null
        val o = Array(6) { IntArray(3) }
        val g = BooleanArray(6)
        val path = shortestPath<Burr.State, Move>(b.state(), { s ->
            var found = false
            s.unpack(o, g)
            b.moves(s) { m, t ->
                if (!found && t.outCount(6) > 0 && m.axis == Axis.Y && b.pieces[m.piece].axis == Axis.Y && m.to < o[m.piece][1]) { found = true; exit = m }
            }
            found
        }, 400_000) { s, emit -> b.moves(s, emit) }
        assertNotNull(path)
        for (m in path!!) b.move(m.piece, m.axis, m.to)
        val m = exit!!
        val ex = BurrExit(b, tableY)
        val lockBefore = ex.lockBounds(except = m.piece)!!
        b.move(m.piece, m.axis, m.to)
        val (axis, dir) = ex.exitWay(m.piece, m.axis, -1)
        assertEquals(Axis.Y to -1, axis to dir)
        val way = ex.plan(m.piece, axis, dir, ex.offsetOf(m.piece) * (1 / Burrs.CELL), emptyMap())
        check(b, ex, way, emptyMap())
        // The turn happens beside the lock, by at least the stick's turning radius, and above the turntable.
        val h = ex.bounds[m.piece].half
        val reach = kotlin.math.sqrt(h.x * h.x + h.y * h.y + h.z * h.z)
        for ((a, c) in way.keys.zipWithNext()) if (a.deg != c.deg) {
            assertTrue(flatDistance(a.center, lockBefore) >= reach)
            assertTrue(a.center.y - reach > ex.restTop)
        }
        println("down exit ${b.pieces[m.piece].name} ${m.axis}${if (dir > 0) "+" else "-"} after ${path.size} moves, ${way.mode}, ${way.keys.size} keys, ${"%.1f".format(way.duration)} s")
    }

    @Test
    fun theWayRoundARectangleKeepsOutOfIt() {
        val r = Aabb(Vec3(-0.1, 0.0, -0.1), Vec3(0.1, 0.0, 0.1))
        val via = BurrExit.around(Vec3(0.3, 0.0, 0.0), Vec3(-0.3, 0.0, 0.0), r)
        assertEquals(2, via.size)
        assertTrue(BurrExit.around(Vec3(0.3, 0.0, 0.0), Vec3(0.3, 0.0, 0.3), r).isEmpty())
    }

    @Test
    fun boxesSeparateAndTouch() {
        val a = Aabb(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 1.0)).obb()
        assertEquals(0.5, a.gap(Aabb(Vec3(1.5, 0.0, 0.0), Vec3(2.0, 1.0, 1.0)).obb()), 1e-9)
        assertEquals(0.0, a.gap(Aabb(Vec3(1.0, 0.0, 0.0), Vec3(2.0, 1.0, 1.0)).obb()), 1e-9)
        assertTrue(a.gap(Aabb(Vec3(0.5, 0.5, 0.5), Vec3(2.0, 1.0, 1.0)).obb()) < 0)
        // A unit cube turned 45° about y next to another: its corner reaches 0.207 further.
        val axes = Aabb.AXES.map { rotate(it, TurnAxis.Y, 45.0) }
        val t = Obb(Vec3(0.5, 0.5, 0.5), axes, Vec3(0.5, 0.5, 0.5))
        assertEquals(1.5 - 0.5 - Math.sqrt(0.5), t.gap(Aabb(Vec3(1.5, 0.0, 0.0), Vec3(2.0, 1.0, 1.0)).obb()), 1e-9)
    }
}
