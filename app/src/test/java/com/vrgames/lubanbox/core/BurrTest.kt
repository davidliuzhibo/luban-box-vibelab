package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BurrTest {
    private fun stick(i: Int, keep: (Cell) -> Boolean = { true }): BurrPiece {
        val (axis, section, name) = Burr.STICKS[i]
        return BurrPiece(name, axis, Burr.fullStick(axis, section).filter(keep))
    }

    @Test
    fun aStickSlidesOutThroughTheOthersNotch() {
        // Upper cross stick whole; the front upright notched where they cross.
        val x1 = stick(0)
        val y1 = stick(2) { c -> c !in x1.cells }
        val b = Burr(listOf(x1, y1))
        assertTrue(b.pieces.all(Burr::connected))
        // The upright cannot move along its length (the cross stick runs through its notch)...
        assertEquals(0..0, b.reach(1, Axis.Y))
        // ...but the cross stick slides out either way, and is out once clear of the upright.
        val r = b.reach(0, Axis.X)
        assertTrue("$r", r.first <= -3 && r.last >= 3)
        b.move(0, Axis.X, r.last)
        assertTrue(b.removed(0))
        assertFalse(b.solved)
        // Alone, the upright is free to go.
        val r1 = b.reach(1, Axis.Y)
        b.move(1, Axis.Y, r1.last)
        assertTrue(b.solved)
    }

    @Test
    fun twoSticksComeApartInTwoMoves() {
        val x1 = stick(0)
        val y1 = stick(2) { c -> c !in x1.cells }
        val b = Burr(listOf(x1, y1))
        val sol = b.solution()
        assertNotNull(sol)
        assertEquals(2, sol!!.size)
    }

    @Test
    fun chapterOneLockTakesFourMovesToFreeTheFirstStick() {
        val b = Burrs.lock()
        assertTrue(b.pieces.all(Burr::connected))
        // Nothing comes out in fewer than four moves...
        val first = shortestPath<Burr.State, Move>(b.state(), { it.outCount(6) > 0 }, 200_000) { s, emit -> b.moves(s, emit) }
        assertEquals(4, first!!.size)
        // ...and the staged solution takes it all apart.
        val sol = b.solution()
        assertNotNull(sol)
        for (m in sol!!) b.move(m.piece, m.axis, m.to)
        assertTrue(b.solved)
    }

    @Test
    fun stateRoundTrips() {
        val o = Array(6) { i -> intArrayOf(i - 3, -i, i) }
        val g = BooleanArray(6) { it == 4 }
        val s = Burr.State.of(o, g)
        val o2 = Array(6) { IntArray(3) }
        val g2 = BooleanArray(6)
        s.unpack(o2, g2)
        for (i in 0 until 6) {
            assertEquals(g[i], g2[i])
            if (!g[i]) assertEquals(o[i].toList(), o2[i].toList())
        }
        assertEquals(1, s.outCount(6))
    }
}
