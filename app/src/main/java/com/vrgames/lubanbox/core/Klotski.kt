package com.vrgames.lubanbox.core

/** A klotski block, [w]×[h] cells (w across the board, h toward the player). */
data class KBlock(val name: String, val w: Int, val h: Int)

/**
 * A starting position: blocks and their top-left cells. Block 0 is 曹操 (2×2), who must reach the
 * gap in the board's near edge.
 */
class KlotskiLayout(val id: String, val title: String, val blocks: List<Pair<KBlock, Cell>>)

/**
 * 华容道 on the classic 4×5 board, lying flat: x across (0 = left), y toward the player (0 = far
 * edge). A block slides along x or y, any distance in one straight move, while the cells it
 * passes are free. In the object frame, board y is +z (toward the player).
 */
class Klotski(val layout: KlotskiLayout) : SlidePuzzle {
    private val blocks = layout.blocks.map { it.first }
    private val start = layout.blocks.map { it.second }
    private val pos = start.toTypedArray()

    override val pieceCount get() = blocks.size
    override fun axes(i: Int) = listOf(Axis.X, Axis.Z)
    override fun offset(i: Int, axis: Axis) = if (axis == Axis.X) pos[i].x - start[i].x else pos[i].y - start[i].y
    override val solved get() = pos[0] == GOAL

    fun block(i: Int) = blocks[i]
    fun position(i: Int) = pos[i]

    override fun reach(i: Int, axis: Axis): IntRange {
        val occ = occupancy(skip = i)
        val b = blocks[i]
        val p = pos[i]
        fun fits(q: Cell): Boolean {
            if (q.x < 0 || q.y < 0 || q.x + b.w > W || q.y + b.h > H) return false
            for (dx in 0 until b.w) for (dy in 0 until b.h) if (occ[(q.y + dy) * W + q.x + dx]) return false
            return true
        }
        val d = if (axis == Axis.X) Cell(1, 0) else Cell(0, 1)
        var hi = 0
        while (fits(p + d * (hi + 1))) hi++
        var lo = 0
        while (fits(p + d * (lo - 1))) lo--
        val base = offset(i, axis)
        return base + lo..base + hi
    }

    override fun move(i: Int, axis: Axis, to: Int) {
        require(to in reach(i, axis)) { "${blocks[i].name} cannot reach $to" }
        val p = pos[i]
        pos[i] = if (axis == Axis.X) Cell(start[i].x + to, p.y) else Cell(p.x, start[i].y + to)
    }

    override fun reset() {
        start.forEachIndexed { i, c -> pos[i] = c }
    }

    private fun occupancy(skip: Int): BooleanArray {
        val occ = BooleanArray(W * H)
        for (j in blocks.indices) if (j != skip) {
            val b = blocks[j]
            for (dx in 0 until b.w) for (dy in 0 until b.h) occ[(pos[j].y + dy) * W + pos[j].x + dx] = true
        }
        return occ
    }

    /**
     * Shortest solution on the board's shape alone: blocks of the same size are interchangeable,
     * so the search runs on the board pattern and each step is then matched to the actual block.
     */
    override fun solution(): List<Move>? {
        val path = shortestPath<Long, ShapeMove>(boardCode(), { isGoal(it) }) { s, emit -> shapeMoves(s, emit) } ?: return null
        val sim = Klotski(layout).also { k -> pos.forEachIndexed { i, c -> k.pos[i] = c } }
        return path.map { m ->
            val i = sim.pos.indices.first { sim.pos[it] == m.from && sim.blocks[it].let { b -> b.w == m.w && b.h == m.h } }
            val to = sim.offset(i, m.axis) + m.delta
            sim.move(i, m.axis, to)
            Move(i, m.axis, to)
        }
    }

    /** A straight move of whichever block of size [w]×[h] has its top-left at [from]. */
    data class ShapeMove(val from: Cell, val w: Int, val h: Int, val axis: Axis, val delta: Int)

    /** The board as a pattern: 3 bits per cell, the shape whose top-left is there. */
    fun boardCode(): Long {
        var s = 0L
        for (i in blocks.indices) s = s or (shapeCode(blocks[i]).toLong() shl (3 * (pos[i].y * W + pos[i].x)))
        return s
    }

    companion object {
        const val W = 4
        const val H = 5
        val GOAL = Cell(1, 3)

        val CAO = KBlock("曹操", 2, 2)
        val GUAN = KBlock("关羽", 2, 1)
        val ZHANG = KBlock("张飞", 1, 2)
        val ZHAO = KBlock("赵云", 1, 2)
        val MA = KBlock("马超", 1, 2)
        val HUANG = KBlock("黄忠", 1, 2)
        val BING = KBlock("兵", 1, 1)

        private val SHAPES = listOf(2 to 2, 2 to 1, 1 to 2, 1 to 1)
        private fun shapeCode(b: KBlock) = SHAPES.indexOf(b.w to b.h) + 1

        fun isGoal(s: Long) = ((s shr (3 * (GOAL.y * W + GOAL.x))) and 7L) == 1L

        /** Straight moves from a board pattern (used by the solver and the labs). */
        fun shapeMoves(s: Long, emit: (ShapeMove, Long) -> Unit) {
            val occ = BooleanArray(W * H)
            val anchors = ArrayList<Triple<Int, Int, Int>>()
            for (c in 0 until W * H) {
                val code = ((s shr (3 * c)) and 7L).toInt()
                if (code == 0) continue
                val (w, h) = SHAPES[code - 1]
                val x = c % W
                val y = c / W
                anchors += Triple(x, y, code)
                for (dx in 0 until w) for (dy in 0 until h) occ[(y + dy) * W + x + dx] = true
            }
            for ((x, y, code) in anchors) {
                val (w, h) = SHAPES[code - 1]
                for (dx in 0 until w) for (dy in 0 until h) occ[(y + dy) * W + x + dx] = false
                val base = s and (7L shl (3 * (y * W + x))).inv()
                for ((axis, dir) in listOf(Axis.X to 1, Axis.X to -1, Axis.Z to 1, Axis.Z to -1)) {
                    var k = 1
                    while (true) {
                        val nx = if (axis == Axis.X) x + dir * k else x
                        val ny = if (axis == Axis.Z) y + dir * k else y
                        if (nx < 0 || ny < 0 || nx + w > W || ny + h > H) break
                        var free = true
                        for (dx in 0 until w) for (dy in 0 until h) if (occ[(ny + dy) * W + nx + dx]) free = false
                        if (!free) break
                        emit(ShapeMove(Cell(x, y), w, h, axis, dir * k), base or (code.toLong() shl (3 * (ny * W + nx))))
                        k++
                    }
                }
                for (dx in 0 until w) for (dy in 0 until h) occ[(y + dy) * W + x + dx] = true
            }
        }
    }
}
