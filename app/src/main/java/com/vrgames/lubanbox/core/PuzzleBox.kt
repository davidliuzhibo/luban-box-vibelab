package com.vrgames.lubanbox.core

/** A sliding panel of the puzzle box: moves along [axis] between 0 and [steps]. */
class BoxPanel(val name: String, val axis: Axis, val steps: Int, val direction: Int = 1)

/**
 * A pin inside the mechanism: panel [panel] can only move between [between] (a pair of neighbouring positions,
 * either way) while every panel in [needs] is within its range. v0.10.1: the 鲁班盒 has none any more (its
 * panels block each other with visible tabs and notches); only the 花旗锁 keeps the springs inside its body,
 * which the player cannot see into.
 */
class Pin(val panel: Int, val between: IntRange, val needs: Map<Int, IntRange>)

/**
 * A box of sliding panels (鲁班盒, 花旗锁): each panel moves along its axis in whole steps from 0 (closed);
 * [direction] says which way along the axis it opens (+1 or -1). Solved when panel [lid] reaches its last step.
 *
 * v0.10.1 (PICO device test 2026-11-23: the front panel slid right into the right end panel, which had only been
 * lifted 1 cm): with [shapes], a panel moves a step only if its real shape can slide that far without sinking
 * into any other panel or the body ([Sweep.CONTACT] of touching allowed). Hidden [pins] apply on top.
 */
class PuzzleBox(val panels: List<BoxPanel>, val pins: List<Pin>, val lid: Int, val shapes: BoxShapes? = null) : SlidePuzzle {
    private val at = IntArray(panels.size)

    override val pieceCount get() = panels.size
    override fun axes(i: Int) = listOf(panels[i].axis)
    override fun offset(i: Int, axis: Axis) = at[i] * panels[i].direction
    override val solved get() = at[lid] == panels[lid].steps

    override fun reach(i: Int, axis: Axis): IntRange {
        var hi = at[i]
        while (hi < panels[i].steps && free(i, hi, hi + 1, at)) hi++
        var lo = at[i]
        while (lo > 0 && free(i, lo, lo - 1, at)) lo--
        val d = panels[i].direction
        return if (d > 0) lo..hi else -hi..-lo
    }

    /**
     * How far a drag may show the panel (in steps along the axis, fractional): the whole steps it can reach, plus,
     * past the last one, the bit of the next step its shape can still slide before touching what is in the way
     * (it settles back on release). Never past the end of its track or into a panel held by a pin.
     */
    override fun span(i: Int, axis: Axis): ClosedFloatingPointRange<Double> {
        val r = reach(i, axis)
        val d = panels[i].direction
        var lo = r.first.toDouble()
        var hi = r.last.toDouble()
        // Positions (0..steps) at the two ends of the reach.
        val far = if (d > 0) r.last else -r.first
        val near = if (d > 0) r.first else -r.last
        fun partial(from: Int, to: Int): Double {
            if (to < 0 || to > panels[i].steps || !pinsFree(i, from, to, at)) return 0.0
            val s = shapes ?: return 0.0
            return (geometricFree(i, from, if (to > from) 1 else -1, at) / s.step(i)).coerceIn(0.0, 0.95)
        }
        val ahead = partial(far, far + 1)
        val back = partial(near, near - 1)
        if (d > 0) {
            hi += ahead
            lo -= back
        } else {
            lo -= ahead
            hi += back
        }
        return lo..hi
    }

    override fun move(i: Int, axis: Axis, to: Int) {
        require(to in reach(i, axis)) { "${panels[i].name} cannot reach $to" }
        at[i] = to * panels[i].direction
    }

    override fun reset() = at.fill(0)

    override fun solution(): List<Move>? =
        shortestPath(encode(at), { s -> decode(s)[lid] == panels[lid].steps }) { s, emit -> moves(s, emit) }

    /** Positions (0 = closed) of every panel. */
    fun positions(): IntArray = at.copyOf()

    /** Sets every panel's position at once (tests and the audit; no rules checked). */
    fun setPositions(p: IntArray) = p.copyInto(at)

    /** Straight moves from a packed state, for the solver and the labs. */
    fun moves(s: Long, emit: (Move, Long) -> Unit) {
        val a = decode(s)
        for (i in panels.indices) {
            var p = a[i]
            while (p < panels[i].steps && free(i, p, p + 1, a)) {
                p++
                emit(Move(i, panels[i].axis, p * panels[i].direction), encode(a.copyOf().also { it[i] = p }))
            }
            p = a[i]
            while (p > 0 && free(i, p, p - 1, a)) {
                p--
                emit(Move(i, panels[i].axis, p * panels[i].direction), encode(a.copyOf().also { it[i] = p }))
            }
        }
    }

    fun encode(a: IntArray): Long = a.foldIndexed(0L) { i, s, v -> s or (v.toLong() shl (5 * i)) }
    fun decode(s: Long) = IntArray(panels.size) { ((s shr (5 * it)) and 31L).toInt() }

    // ---- Geometry -----------------------------------------------------------------------------

    /** Where panel [i] is at position [p] (0..steps): its displacement from closed (meters). */
    fun displacement(i: Int, p: Int): Vec3 {
        val s = shapes?.step?.invoke(i) ?: 0.0
        return Sweep.unit(panels[i].axis) * (p * panels[i].direction * s)
    }

    /** Panel [i]'s boxes at position [p]. */
    fun solid(i: Int, p: Int): List<Aabb> {
        val s = shapes ?: return emptyList()
        val d = displacement(i, p)
        return s.parts[i].map { it.moved(d) }
    }

    /** Everything but panel [i], where the positions [a] put it (the body and what it stands on included). */
    fun others(i: Int, a: IntArray): List<Aabb> {
        val s = shapes ?: return emptyList()
        val out = ArrayList<Aabb>(s.fixed)
        for (j in panels.indices) if (j != i) out += solid(j, a[j])
        return out
    }

    /** How far (meters) panel [i] at position [from] can slide toward position from + [sign] before touching anything. */
    fun geometricFree(i: Int, from: Int, sign: Int, a: IntArray): Double {
        val axis = panels[i].axis.ordinal
        val dir = sign * panels[i].direction
        return Sweep.freeDistance(solid(i, from), axis, dir, others(i, a))
    }

    /**
     * Deepest overlap (meters; ≤ 0 means apart or touching only) while panel [i] slides from position [from] to [to]
     * with the others at [a], sampled every millimetre. For the logs and the audit.
     */
    fun sweepWorst(i: Int, from: Int, to: Int, a: IntArray = at): Double {
        val o = others(i, a)
        val d0 = displacement(i, from)
        val d1 = displacement(i, to)
        val n = maxOf(1, kotlin.math.ceil(d0.distanceTo(d1) / 0.001).toInt())
        var worst = Double.NEGATIVE_INFINITY
        for (k in 0..n) {
            val d = d0 + (d1 - d0) * (k.toDouble() / n)
            worst = maxOf(worst, Sweep.penetration(shapes?.parts?.get(i)?.map { it.moved(d) } ?: return 0.0, o))
        }
        return worst
    }

    /** One line per panel: where it is and how far its shape could slide each way (mm), for the logs. */
    fun describeFree(): String = panels.indices.joinToString("; ") { i ->
        val up = if (at[i] < panels[i].steps) geometricFree(i, at[i], 1, at) else 0.0
        val down = if (at[i] > 0) geometricFree(i, at[i], -1, at) else 0.0
        fun mm(v: Double) = if (v.isInfinite()) "∞" else "%.1f".format(v * 1000)
        "${panels[i].name}@${at[i]} open+${mm(up)} back-${mm(down)} reach=${reach(i, panels[i].axis)}"
    }

    private fun pinsFree(i: Int, from: Int, to: Int, a: IntArray): Boolean = pins.all { pin ->
        pin.panel != i || minOf(from, to) !in pin.between || maxOf(from, to) !in pin.between ||
            pin.needs.all { (j, r) -> a[j] in r }
    }

    /** Whether panel [i] can go from [from] to the neighbouring position [to] with the others at [a]. */
    fun free(i: Int, from: Int, to: Int, a: IntArray): Boolean {
        if (!pinsFree(i, from, to, a)) return false
        val s = shapes ?: return true
        return geometricFree(i, from, to - from, a) >= s.step(i) - 1e-9
    }
}
