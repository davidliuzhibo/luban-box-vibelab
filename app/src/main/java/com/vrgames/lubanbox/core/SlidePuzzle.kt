package com.vrgames.lubanbox.core

/** Integer cell on a puzzle's grid (one step of a sliding piece). */
data class Cell(val x: Int, val y: Int, val z: Int = 0) {
    operator fun plus(o: Cell) = Cell(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Int) = Cell(x * k, y * k, z * k)

    /** Packs a cell in -16..15 on each axis into one int (for fast occupancy sets). */
    fun packed() = (x + 16) or ((y + 16) shl 5) or ((z + 16) shl 10)
}

enum class Axis(val unit: Cell) { X(Cell(1, 0, 0)), Y(Cell(0, 1, 0)), Z(Cell(0, 0, 1)) }

/** One straight move: piece [piece] slides along [axis] until its offset is [to]. */
data class Move(val piece: Int, val axis: Axis, val to: Int)

/**
 * A mechanism whose pieces slide along fixed axes in whole steps: the burr's sticks, the klotski
 * blocks, the puzzle box's panels. The stage renders and drags pieces through this interface only;
 * each puzzle decides where a piece may go.
 */
interface SlidePuzzle {
    val pieceCount: Int

    /** Axes piece [i] slides along, in the object's own frame. */
    fun axes(i: Int): List<Axis>

    /** How far piece [i] has slid along [axis] from where it started, in steps. */
    fun offset(i: Int, axis: Axis): Int

    /** Offsets piece [i] can reach along [axis] in one straight move from where it is now. */
    fun reach(i: Int, axis: Axis): IntRange

    /** Slides piece [i] to [to] along [axis]; [to] must be in [reach]. */
    fun move(i: Int, axis: Axis, to: Int)

    /** Pieces that have come out of the mechanism entirely (burr sticks). */
    fun removed(i: Int): Boolean = false

    val solved: Boolean

    /** Back to the starting position. */
    fun reset()

    /** A shortest solution from the current position (straight moves), or null if there is none. */
    fun solution(): List<Move>?
}

/**
 * Breadth-first search, keeping the move that led to each state. Returns the moves of a shortest
 * path from [start] to a state where [goal] holds, or null (also when [limit] states were seen).
 */
fun <S, M> shortestPath(
    start: S,
    goal: (S) -> Boolean,
    limit: Int = 2_000_000,
    next: (S, (M, S) -> Unit) -> Unit,
): List<M>? {
    if (goal(start)) return emptyList()
    val parent = HashMap<S, Pair<S, M?>>()
    parent[start] = start to null
    var frontier = arrayListOf(start)
    while (frontier.isNotEmpty() && parent.size < limit) {
        val nextFrontier = ArrayList<S>()
        for (s in frontier) {
            var found = false
            var end = s
            next(s) { m, t ->
                if (!found && t !in parent) {
                    parent[t] = s to m
                    if (goal(t)) {
                        found = true
                        end = t
                    } else {
                        nextFrontier.add(t)
                    }
                }
            }
            if (found) {
                val path = ArrayList<M>()
                var cur = end
                while (cur != start) {
                    val (p, m) = parent[cur]!!
                    @Suppress("UNCHECKED_CAST")
                    path.add(m as M)
                    cur = p
                }
                return path.reversed()
            }
        }
        frontier = nextFrontier
    }
    return null
}
