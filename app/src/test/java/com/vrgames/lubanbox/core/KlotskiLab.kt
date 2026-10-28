package com.vrgames.lubanbox.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Board search for 华容道 (run with -Dlab=true): random placements of the classic ten blocks,
 * scored by the fewest straight moves that get 曹操 out. Prints a few boards per difficulty as
 * code for [KlotskiBoards].
 */
class KlotskiLab {
    @Test
    fun search() {
        assumeTrue(System.getProperty("lab") == "true")
        val rnd = Random(System.getProperty("seed")?.toLong() ?: 7L)
        val set = listOf(Klotski.CAO, Klotski.GUAN, Klotski.ZHANG, Klotski.ZHAO, Klotski.MA, Klotski.HUANG, Klotski.BING, Klotski.BING, Klotski.BING, Klotski.BING)
        val byLength = HashMap<Int, MutableList<List<Pair<KBlock, Cell>>>>()
        repeat(3000) {
            val placed = place(set, rnd) ?: return@repeat
            // 曹操 should start well away from the exit.
            if (placed[0].second.y > 1) return@repeat
            val k = Klotski(KlotskiLayout("lab", "lab", placed))
            val n = k.solution()?.size ?: return@repeat
            byLength.getOrPut(n) { ArrayList() }.add(placed)
        }
        for ((n, list) in byLength.toSortedMap()) println("$n moves: ${list.size} boards")
        for (n in listOf(6, 8, 10, 12, 15, 18, 20, 25, 30, 35, 40)) {
            val b = byLength[n]?.firstOrNull() ?: continue
            println("$n: " + b.joinToString(", ") { (blk, c) -> "${blk.name}(${c.x},${c.y})" })
            println(draw(b))
        }
    }

    /** Random non-overlapping placement of [set] on the 4×5 board, or null if it does not fit. */
    private fun place(set: List<KBlock>, rnd: Random): List<Pair<KBlock, Cell>>? {
        val occ = BooleanArray(Klotski.W * Klotski.H)
        val out = ArrayList<Pair<KBlock, Cell>>()
        for (b in set) {
            val spots = ArrayList<Cell>()
            for (x in 0..Klotski.W - b.w) for (y in 0..Klotski.H - b.h) {
                var free = true
                for (dx in 0 until b.w) for (dy in 0 until b.h) if (occ[(y + dy) * Klotski.W + x + dx]) free = false
                if (free) spots += Cell(x, y)
            }
            if (spots.isEmpty()) return null
            val c = spots[rnd.nextInt(spots.size)]
            for (dx in 0 until b.w) for (dy in 0 until b.h) occ[(c.y + dy) * Klotski.W + c.x + dx] = true
            out += b to c
        }
        return out
    }

    private fun draw(b: List<Pair<KBlock, Cell>>): String {
        val g = Array(Klotski.H) { CharArray(Klotski.W) { '·' } }
        for ((blk, c) in b) for (dx in 0 until blk.w) for (dy in 0 until blk.h) g[c.y + dy][c.x + dx] = blk.name.first()
        return g.joinToString("\n") { String(it) }
    }
}
