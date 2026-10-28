package com.vrgames.lubanbox.core

/**
 * A ring, dial or arm that turns about [axis] (its own, in whatever frame holds it) through [stops]
 * positions spread over a full turn (or [stops] positions over a limited swing when [wraps] is
 * false). [center] is the middle of its turning, for hints and for picking. Stop k shows at
 * (k - [zeroStop]) × [stepDeg] degrees.
 */
class Dial(
    val name: String,
    val axis: Axis,
    val stops: Int,
    val center: Vec3,
    val wraps: Boolean = true,
    val stepDeg: Double = 360.0 / stops,
    val zeroStop: Int = 0,
) {
    fun angle(stop: Int) = (stop - zeroStop) * stepDeg
}

/**
 * A pin between dials: dial [dial] may only turn while every dial in [needs] stands at one of the
 * positions given.
 */
class DialPin(val dial: Int, val needs: Map<Int, Set<Int>>)

/**
 * Mechanisms that turn: the 文字锁's character rings, the 透光镜's mirror, the 浑天仪's rings.
 * One move turns one dial a single stop either way; [links] turn other dials with it (gears:
 * links[i][j] stops of dial j per stop of dial i). Solved when [goal] holds.
 */
class RotaryPuzzle(
    val dials: List<Dial>,
    private val start: IntArray,
    val links: Array<IntArray> = Array(dials.size) { IntArray(dials.size) },
    val pins: List<DialPin> = emptyList(),
    val goal: (IntArray) -> Boolean,
) {
    private val at = start.copyOf()

    val solved get() = goal(at)
    fun position(i: Int) = at[i]
    fun positions(): IntArray = at.copyOf()

    /** Whether dial [i] can turn a stop in direction [dir] (+1 or -1) from here. */
    fun canTurn(i: Int, dir: Int): Boolean = turned(at, i, dir) != null

    /** Turns dial [i] one stop in direction [dir]; false if it is held. */
    fun turn(i: Int, dir: Int): Boolean {
        val next = turned(at, i, dir) ?: return false
        next.copyInto(at)
        return true
    }

    fun reset() = start.copyInto(at)

    /** Positions after turning dial [i] by [dir] from [a], or null if a pin or an end stop holds it. */
    fun turned(a: IntArray, i: Int, dir: Int): IntArray? {
        if (pins.any { p -> p.dial == i && p.needs.any { (j, ok) -> a[j] !in ok } }) return null
        val next = a.copyOf()
        for (j in dials.indices) {
            val k = if (j == i) dir else links[i][j] * dir
            if (k == 0) continue
            val d = dials[j]
            val v = next[j] + k
            if (!d.wraps && (v < 0 || v >= d.stops)) return null
            next[j] = Math.floorMod(v, d.stops)
        }
        return next
    }

    /** Shortest solution from here: each step is (dial, direction). */
    fun solution(): List<Pair<Int, Int>>? {
        fun enc(a: IntArray) = a.fold(0L) { s, v -> s * 64 + v }
        fun dec(s: Long): IntArray {
            val a = IntArray(dials.size)
            var r = s
            for (i in dials.indices.reversed()) {
                a[i] = (r % 64).toInt()
                r /= 64
            }
            return a
        }
        return shortestPath<Long, Pair<Int, Int>>(enc(at), { goal(dec(it)) }) { s, emit ->
            val a = dec(s)
            for (i in dials.indices) for (dir in intArrayOf(1, -1)) turned(a, i, dir)?.let { emit(i to dir, enc(it)) }
        }
    }
}
