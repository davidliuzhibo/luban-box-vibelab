package com.vrgames.lubanbox.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Design search for the six-piece burr (run with -Dlab=true).
 *
 * Random notchings almost never come apart: every stick needs wood in the middle of the lock to
 * stay in one piece, and that wood blocks a crossing stick, so a lock that comes apart has to be
 * worked loose with partial moves. The search therefore climbs: change one core cell at a time
 * (sticks repaired to stay whole), keep the change when the lock gets no worse. First it climbs
 * toward more sticks coming out, then, once all six come out, toward more moves before the first
 * stick is free (the burr's "level").
 */
class BurrLab {
    private val contested = Burr.contested()
    private val cells = contested.keys.toList()

    @Test
    fun search() {
        assumeTrue(System.getProperty("lab") == "true")
        val rnd = Random(System.getProperty("seed")?.toLong() ?: 20261024L)
        val n = System.getProperty("runs")?.toInt() ?: 20000
        val found = ArrayList<Pair<String, Eval>>()
        val t0 = System.currentTimeMillis()
        var greedyOk = 0
        // The key: one stick keeps every core cell it covers, so it slides straight out first.
        val key = System.getProperty("key")?.toInt() ?: 2
        repeat(n) { k ->
            val design = randomDesign(rnd, key)
            val b = Burr.fromOwners(design)
            if (!b.pieces.all(Burr::connected)) return@repeat
            if (k % 100 == 0) println("$k designs, $greedyOk come apart greedily, ${found.size} verified, ${(System.currentTimeMillis() - t0) / 1000}s")
            val sol = b.solution() ?: return@repeat
            greedyOk++
            val sideways = sol.count { it.axis != b.pieces[it.piece].axis }
            // Moves before the first stick comes out (the burr's level, by stages).
            val sim = Burr.fromOwners(design)
            var level = 0
            for (m in sol) {
                level++
                sim.move(m.piece, m.axis, m.to)
                if ((0 until 6).any(sim::removed)) break
            }
            found += code(design) to Eval(6, level, sol.size, sideways)
            if (level >= 3) println("found L$level moves ${sol.size} sideways $sideways  ${code(design)}")
            if (found.size >= (System.getProperty("want")?.toInt() ?: 40)) {
                println("done early")
                return@repeat
            }
        }
        println("done: $greedyOk greedy, ${found.size} verified in ${(System.currentTimeMillis() - t0) / 1000}s")
        println("levels: " + found.groupingBy { it.second.level }.eachCount().toSortedMap())
        for ((c, e) in found.sortedWith(compareByDescending<Pair<String, Eval>> { it.second.level }.thenByDescending { it.second.total }).take(15)) {
            println("best L${e.level} moves ${e.total} sideways ${e.states}  $c")
        }
    }

    /** Takes sticks out one at a time, each by the shortest way found within a small search. */
    private fun greedy(b: Burr): Boolean {
        val n = b.pieceCount
        var s = b.state()
        while (!s.allOut(n)) {
            val have = s.outCount(n)
            val path = shortestPath<Burr.State, Move>(s, { it.outCount(n) > have }, 4_000) { st, emit -> b.moves(st, emit) } ?: return false
            var cur = s
            for (m in path) {
                var nextState = cur
                b.moves(cur) { mm, t -> if (mm == m) nextState = t }
                cur = nextState
            }
            s = cur
        }
        return true
    }

    data class Eval(val maxOut: Int, val level: Int, val total: Int?, val states: Int) {
        /** More sticks out first; then, for locks that come apart, a higher level and a not-too-long solution. */
        val score: Double get() = if (total == null) maxOut * 100.0 + level else 1000.0 + level * 10 - total * 0.5
    }

    /** One breadth-first sweep of everything reachable: level, sticks out, and moves to take it all apart. */
    private fun eval(owner: Map<Cell, Int>): Eval {
        val b = Burr.fromOwners(owner)
        if (!b.pieces.all(Burr::connected)) return Eval(0, 0, null, 0)
        val n = b.pieceCount
        val seen = HashSet<Burr.State>()
        var layer = listOf(b.state())
        seen.add(layer[0])
        var depth = 0
        var level = -1
        var maxOut = 0
        var total: Int? = null
        while (layer.isNotEmpty() && seen.size < 200_000) {
            for (s in layer) {
                val o = s.outCount(n)
                if (o > maxOut) maxOut = o
                if (o > 0 && level < 0) level = depth
                if (o == n && total == null) total = depth
            }
            if (total != null) break
            val next = ArrayList<Burr.State>()
            for (s in layer) b.moves(s) { _, t -> if (seen.add(t)) next.add(t) }
            layer = next
            depth++
        }
        return Eval(maxOut, maxOf(level, 0), total, seen.size)
    }

    private fun randomDesign(rnd: Random, key: Int = -1): HashMap<Cell, Int> {
        val owner = HashMap<Cell, Int>()
        val pHole = 0.05 + rnd.nextDouble() * 0.3
        for (c in cells) {
            val cover = contested[c]!!
            owner[c] = when {
                key in cover -> key
                rnd.nextDouble() < pHole -> -1
                else -> cover.filter { it != key }.let { it[rnd.nextInt(it.size)] }
            }
        }
        repair(owner, key)
        return owner
    }

    private fun code(owner: Map<Cell, Int>) = cells.joinToString("") { c -> owner[c]!!.let { if (it < 0) "." else "$it" } }

    /** Joins each stick's two ends (claiming the cheapest path of cells), then drops cut-off bits. */
    private fun repair(owner: HashMap<Cell, Int>, key: Int = -1) {
        repeat(3) { for (i in Burr.STICKS.indices) if (i != key) joinEnds(i, owner, key) }
        for (i in Burr.STICKS.indices) {
            val (axis, section, _) = Burr.STICKS[i]
            val mine = Burr.fullStick(axis, section).filter { !Burr.inCore(it) || owner[it] == i }.toSet()
            val keep = component(mine, mine.first { !Burr.inCore(it) })
            for (c in mine) if (Burr.inCore(c) && c !in keep) owner[c] = -1
        }
    }

    private fun joinEnds(i: Int, owner: HashMap<Cell, Int>, key: Int = -1) {
        val (axis, section, _) = Burr.STICKS[i]
        val all = Burr.fullStick(axis, section).toSet()
        val mine = all.filter { !Burr.inCore(it) || owner[it] == i }.toSet()
        val a = mine.first { !Burr.inCore(it) && along(axis, it) < 0 }
        val b = mine.first { !Burr.inCore(it) && along(axis, it) > 0 }
        if (b in component(mine, a)) return
        // 0-1 BFS: own cells are free, others cost one.
        val dist = HashMap<Cell, Int>()
        val prev = HashMap<Cell, Cell>()
        val dq = ArrayDeque<Cell>()
        dist[a] = 0
        dq.add(a)
        while (dq.isNotEmpty()) {
            val c = dq.removeFirst()
            for (d in DIRS) {
                val n = c + d
                if (n !in all || owner[n] == key && key >= 0) continue
                val w = if (n in mine) 0 else 1
                val nd = dist[c]!! + w
                if (nd < (dist[n] ?: Int.MAX_VALUE)) {
                    dist[n] = nd
                    prev[n] = c
                    if (w == 0) dq.addFirst(n) else dq.addLast(n)
                }
            }
        }
        var c = b
        while (c != a) {
            if (Burr.inCore(c)) owner[c] = i
            c = prev[c] ?: return
        }
    }

    private fun along(axis: Axis, c: Cell) = when (axis) {
        Axis.X -> c.x
        Axis.Y -> c.y
        Axis.Z -> c.z
    }

    private fun component(cells: Set<Cell>, from: Cell): Set<Cell> {
        val seen = hashSetOf(from)
        val stack = arrayListOf(from)
        while (stack.isNotEmpty()) {
            val c = stack.removeAt(stack.size - 1)
            for (d in DIRS) {
                val n = c + d
                if (n in cells && seen.add(n)) stack.add(n)
            }
        }
        return seen
    }

    companion object {
        private val DIRS = listOf(Cell(1, 0, 0), Cell(-1, 0, 0), Cell(0, 1, 0), Cell(0, -1, 0), Cell(0, 0, 1), Cell(0, 0, -1))
    }
}
