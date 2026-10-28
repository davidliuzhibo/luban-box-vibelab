package com.vrgames.lubanbox.core

/**
 * A stick of the six-piece burr: 2×2 in section and [Burr.LENGTH] long, with notches cut where
 * it crosses the other sticks. [cells] are where it sits in the assembled lock.
 */
class BurrPiece(val name: String, val axis: Axis, val cells: List<Cell>)

/**
 * Six-piece burr (鲁班锁): three pairs of parallel sticks crossing in a 4×4×4 core. A stick moves
 * one whole cell at a time along x, y or z, as far as the other sticks let it (collisions are exact,
 * cell by cell, so the notches decide everything). Once it has slid clear of all the others along
 * that axis, it is out of the lock. Only one stick moves at a time.
 */
class Burr(val pieces: List<BurrPiece>) : SlidePuzzle {
    override val pieceCount get() = pieces.size
    private val off = Array(pieces.size) { IntArray(3) }
    private val out = BooleanArray(pieces.size)
    private val cells: List<IntArray> = pieces.map { p -> p.cells.map { it.packed() }.toIntArray() }

    /** Extent of each stick along each axis, in its assembled place: [min, max] per axis. */
    private val lo = pieces.map { p -> IntArray(3) { a -> p.cells.minOf { coord(it, a) } } }
    private val hi = pieces.map { p -> IntArray(3) { a -> p.cells.maxOf { coord(it, a) } } }

    override fun axes(i: Int) = Axis.entries.toList()
    override fun offset(i: Int, axis: Axis) = off[i][axis.ordinal]
    override fun removed(i: Int) = out[i]
    override val solved get() = out.all { it }

    override fun reach(i: Int, axis: Axis): IntRange {
        val o = off[i][axis.ordinal]
        return if (out[i]) o..o else reachOf(i, axis.ordinal, off, out)
    }

    override fun move(i: Int, axis: Axis, to: Int) {
        require(to in reach(i, axis)) { "${pieces[i].name} cannot reach $to" }
        off[i][axis.ordinal] = to
        if (clear(i, axis.ordinal, off, out)) out[i] = true
    }

    override fun reset() {
        off.forEach { it.fill(0) }
        out.fill(false)
    }

    /**
     * A solution in stages: the fewest moves to free the next stick, then the next, and so on. The
     * whole puzzle's state space is too big to search at once on the headset; one stick at a time
     * stays small. Null if some stage finds no way within [STAGE_LIMIT] states.
     */
    override fun solution(): List<Move>? {
        val n = pieces.size
        var s = state()
        val all = ArrayList<Move>()
        while (!s.allOut(n)) {
            val have = s.outCount(n)
            var end = s
            val path = shortestPath<State, Move>(s, { t -> (t.outCount(n) > have).also { if (it) end = t } }, STAGE_LIMIT) { st, emit -> moves(st, emit) }
                ?: return null
            all += path
            s = end
        }
        return all
    }

    fun state() = State.of(off, out)

    /** Straight moves from a state (and the states they lead to); for the solver and the labs. */
    fun moves(s: State, emit: (Move, State) -> Unit) {
        val o = Array(pieces.size) { IntArray(3) }
        val g = BooleanArray(pieces.size)
        s.unpack(o, g)
        for (i in pieces.indices) {
            if (g[i]) continue
            for (a in 0..2) {
                val r = reachOf(i, a, o, g)
                for (k in r) {
                    if (k == o[i][a]) continue
                    val o2 = Array(pieces.size) { o[it].copyOf() }
                    val g2 = g.copyOf()
                    o2[i][a] = k
                    if (clear(i, a, o2, g2)) g2[i] = true
                    emit(Move(i, Axis.entries[a], k), State.of(o2, g2))
                }
            }
        }
    }

    /** Offsets stick [i] reaches along axis [a]: stops at a collision or once it is clear. */
    private fun reachOf(i: Int, a: Int, o: Array<IntArray>, g: BooleanArray): IntRange {
        val occ = BooleanArray(1 shl 15)
        for (j in pieces.indices) if (j != i && !g[j]) {
            val d = shift(o[j])
            for (c in cells[j]) occ[c + d] = true
        }
        val pos = o[i].copyOf()
        fun fits(k: Int): Boolean {
            pos[a] = k
            val d = shift(pos)
            for (c in cells[i]) if (occ[c + d]) return false
            return true
        }
        fun isClear(k: Int): Boolean {
            pos[a] = k
            return clearAt(i, a, pos, o, g)
        }
        // Slide step by step until blocked or clear (a stick that is already clear of the rest,
        // like the last one, comes out with a single step).
        val start = o[i][a]
        var top = start
        while (top < MAX_OFFSET && fits(top + 1)) {
            top++
            if (isClear(top)) break
        }
        var bottom = start
        while (bottom > -MAX_OFFSET && fits(bottom - 1)) {
            bottom--
            if (isClear(bottom)) break
        }
        return bottom..top
    }

    private fun clear(i: Int, a: Int, o: Array<IntArray>, g: BooleanArray) = clearAt(i, a, o[i], o, g)

    /** Whether stick [i] at [pos] lies entirely beyond every other stick along axis [a]. */
    private fun clearAt(i: Int, a: Int, pos: IntArray, o: Array<IntArray>, g: BooleanArray): Boolean {
        var othersLo = Int.MAX_VALUE
        var othersHi = Int.MIN_VALUE
        for (j in pieces.indices) if (j != i && !g[j]) {
            othersLo = minOf(othersLo, lo[j][a] + o[j][a])
            othersHi = maxOf(othersHi, hi[j][a] + o[j][a])
        }
        if (othersLo > othersHi) return true
        return lo[i][a] + pos[a] > othersHi || hi[i][a] + pos[a] < othersLo
    }

    /** Packed state: 5 bits per axis (offset + 16) for each stick, all ones once it is out. */
    data class State(val a: Long, val b: Long) {
        fun unpack(o: Array<IntArray>, g: BooleanArray) {
            for (i in o.indices) {
                val v = field(i)
                g[i] = v == 0x7FFF
                for (ax in 0..2) o[i][ax] = if (g[i]) 0 else ((v shr (5 * ax)) and 31) - 16
            }
        }

        fun out(i: Int) = field(i) == 0x7FFF
        fun allOut(n: Int) = (0 until n).all(::out)
        fun outCount(n: Int) = (0 until n).count(::out)

        private fun field(i: Int): Int = (if (i < 4) (a shr (15 * i)) else (b shr (15 * (i - 4)))).toInt() and 0x7FFF

        companion object {
            fun of(o: Array<IntArray>, g: BooleanArray): State {
                var a = 0L
                var b = 0L
                for (i in o.indices) {
                    val v = if (g[i]) 0x7FFFL else ((o[i][0] + 16) or ((o[i][1] + 16) shl 5) or ((o[i][2] + 16) shl 10)).toLong()
                    if (i < 4) a = a or (v shl (15 * i)) else b = b or (v shl (15 * (i - 4)))
                }
                return State(a, b)
            }
        }
    }

    companion object {
        const val LENGTH = 8

        /** Farthest a stick may wander from its place (keeps cells inside the packed range). */
        const val MAX_OFFSET = 10

        /** States searched per stage of [solution]. */
        const val STAGE_LIMIT = 20_000

        private fun coord(c: Cell, a: Int) = when (a) {
            0 -> c.x
            1 -> c.y
            else -> c.z
        }

        /** Packed-cell shift for an offset (see [Cell.packed]). */
        private fun shift(o: IntArray) = o[0] + (o[1] shl 5) + (o[2] shl 10)

        /**
         * The six sticks before notching: axis, the two section coordinates of their 2×2 section
         * (y,z for X sticks; z,x for Y; x,y for Z) and a name. Object frame: +y up, +z toward the
         * player, +x to the player's right.
         */
        val STICKS = listOf(
            Triple(Axis.X, Cell(0, -1), "上横木"),
            Triple(Axis.X, Cell(-2, -1), "下横木"),
            Triple(Axis.Y, Cell(0, -1), "前立木"),
            Triple(Axis.Y, Cell(-2, -1), "后立木"),
            Triple(Axis.Z, Cell(0, -1), "右纵木"),
            Triple(Axis.Z, Cell(-2, -1), "左纵木"),
        )

        /** All cells of an un-notched stick. */
        fun fullStick(axis: Axis, section: Cell): List<Cell> {
            val out = ArrayList<Cell>()
            for (u in -LENGTH / 2 until LENGTH / 2) for (a in 0..1) for (b in 0..1) {
                val p = section.x + a
                val q = section.y + b
                out += when (axis) {
                    Axis.X -> Cell(u, p, q)
                    Axis.Y -> Cell(q, u, p)
                    Axis.Z -> Cell(p, q, u)
                }
            }
            return out
        }

        /** The 4×4×4 core where the sticks cross; only cells here can be notched. */
        fun inCore(c: Cell) = c.x in -2..1 && c.y in -2..1 && c.z in -2..1

        /** Core cells and the sticks that cover them (before notching). */
        fun contested(): Map<Cell, List<Int>> {
            val m = LinkedHashMap<Cell, MutableList<Int>>()
            STICKS.forEachIndexed { i, (axis, section, _) ->
                fullStick(axis, section).filter(::inCore).forEach { m.getOrPut(it) { ArrayList() }.add(i) }
            }
            return m
        }

        /**
         * Builds a burr from who keeps each core cell: [owner] maps a covered core cell to the stick
         * that keeps it, or to -1 for a hole. Cells outside the core always stay on their stick.
         */
        fun fromOwners(owner: Map<Cell, Int>): Burr = Burr(
            STICKS.mapIndexed { i, (axis, section, name) ->
                BurrPiece(name, axis, fullStick(axis, section).filter { !inCore(it) || owner[it] == i })
            },
        )

        /** [code]: one character per [contested] cell, in order: the owning stick's digit, or '.' for a hole. */
        fun fromCode(code: String): Burr {
            val cells = contested().keys.toList()
            require(code.length == cells.size) { "code needs ${cells.size} characters" }
            return fromOwners(cells.withIndex().associate { (k, c) -> c to (code[k].digitToIntOrNull() ?: -1) })
        }

        /** Whether a stick is one connected piece of wood (faces touching). */
        fun connected(p: BurrPiece): Boolean {
            if (p.cells.isEmpty()) return false
            val set = p.cells.toHashSet()
            val seen = HashSet<Cell>()
            val stack = arrayListOf(p.cells.first())
            seen.add(p.cells.first())
            val dirs = listOf(Cell(1, 0, 0), Cell(-1, 0, 0), Cell(0, 1, 0), Cell(0, -1, 0), Cell(0, 0, 1), Cell(0, 0, -1))
            while (stack.isNotEmpty()) {
                val c = stack.removeAt(stack.size - 1)
                for (d in dirs) {
                    val n = c + d
                    if (n in set && seen.add(n)) stack.add(n)
                }
            }
            return seen.size == set.size
        }
    }
}
