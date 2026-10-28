package com.vrgames.lubanbox.core

/** A sliding panel of the puzzle box: moves along [axis] between 0 and [steps]. */
class BoxPanel(val name: String, val axis: Axis, val steps: Int, val direction: Int = 1)

/**
 * A hidden pin: panel [panel] can only move between [between] (a pair of neighbouring positions,
 * either way) while every panel in [needs] is within its range.
 */
class Pin(val panel: Int, val between: IntRange, val needs: Map<Int, IntRange>)

/**
 * 鲁班盒: a box whose panels slide a little in turn, each one freeing the next through hidden pins,
 * until the lid comes off. Positions are whole steps from 0 (closed); [direction] says which way
 * along the axis a panel opens (+1 or -1). Solved when panel [lid] reaches its last step.
 */
class PuzzleBox(val panels: List<BoxPanel>, val pins: List<Pin>, val lid: Int) : SlidePuzzle {
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

    override fun move(i: Int, axis: Axis, to: Int) {
        require(to in reach(i, axis)) { "${panels[i].name} cannot reach $to" }
        at[i] = to * panels[i].direction
    }

    override fun reset() = at.fill(0)

    override fun solution(): List<Move>? =
        shortestPath(encode(at), { s -> decode(s)[lid] == panels[lid].steps }) { s, emit -> moves(s, emit) }

    /** Positions (0 = closed) of every panel. */
    fun positions(): IntArray = at.copyOf()

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

    private fun free(i: Int, from: Int, to: Int, a: IntArray): Boolean = pins.all { pin ->
        pin.panel != i || minOf(from, to) !in pin.between || maxOf(from, to) !in pin.between ||
            pin.needs.all { (j, r) -> a[j] in r }
    }

    private fun encode(a: IntArray): Long = a.foldIndexed(0L) { i, s, v -> s or (v.toLong() shl (5 * i)) }
    private fun decode(s: Long) = IntArray(panels.size) { ((s shr (5 * it)) and 31L).toInt() }
}
