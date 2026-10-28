package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chapters two and three, and the closing chest: every mechanism opens, and only the right way. */
class LaterChaptersTest {
    @Test
    fun wordLockSpellsTheBookTitleWithGearedRings() {
        val lock = Bronze.wordLock()
        assertFalse(lock.solved)
        val sol = lock.solution()
        assertNotNull(sol)
        println("word lock: ${sol!!.size} turns ${sol.map { "${it.first}${if (it.second > 0) "+" else "-"}" }}")
        assertTrue("too easy: ${sol.size}", sol.size >= 7)
        // Every ring has to be turned at some point.
        assertEquals(setOf(0, 1, 2, 3), sol.map { it.first }.toSet())
        // Turning the second ring drags the third along.
        val before = lock.positions()
        lock.turn(1, 1)
        assertEquals((before[2] + 1) % 6, lock.position(2))
        lock.reset()
        for ((d, dir) in sol) assertTrue(lock.turn(d, dir))
        assertTrue(lock.solved)
        val word = (0 until 4).map { Bronze.RING_CHARS[it][lock.position(it)] }.joinToString("")
        assertEquals(Bronze.WORD, word)
    }

    @Test
    fun padlockNeedsTheHiddenKeyholeAndTheCatch() {
        val lock = Bronze.padlock()
        // The key will not go in while the plate covers the keyhole.
        assertEquals(0..0, lock.reach(Bronze.KEY, Axis.Z))
        val sol = lock.solution()!!
        assertEquals(5, sol.size)
        for (m in sol) lock.move(m.piece, m.axis, m.to)
        assertTrue(lock.solved)
        // The ring swings but has nothing to do with it.
        assertTrue(sol.none { it.piece == Bronze.RING })
    }

    @Test
    fun mirrorHasFewAimsThatReachTheScreen() {
        val hits = (0 until Bronze.YAW_STOPS).flatMap { y -> (0 until Bronze.TILT_STOPS).map { t -> y to t } }
            .filter { (y, t) -> Bronze.onScreen(Bronze.spot(y, t)) }
        println("mirror aims on the screen: $hits")
        assertTrue("no aim reaches the screen", hits.isNotEmpty())
        assertTrue("too many aims: $hits", hits.size <= 3)
        val m = Bronze.mirror()
        assertFalse(m.solved)
        val sol = m.solution()!!
        println("mirror: ${sol.size} turns")
        assertTrue(sol.size >= 3)
    }

    @Test
    fun armillaryFindsVegaOnlyAtItsStop() {
        val a = Celestial.armillary()
        assertFalse(a.solved)
        val sol = a.solution()!!
        assertEquals(9, sol.size)
        for ((d, dir) in sol) a.turn(d, dir)
        assertTrue(a.solved)
        assertEquals(1, (0 until 12).sumOf { h -> (0 until 9).count { d -> Celestial.tubeDirection(h, d).dot(Celestial.STAR) > Math.cos(Math.toRadians(4.0)) } })
        // The sight brightens as the tube closes in.
        assertTrue(Celestial.closeness(8, 7) > Celestial.closeness(8, 5))
        assertTrue(Celestial.closeness(8, 5) > Celestial.closeness(2, 4))
    }

    @Test
    fun buildsGoBottomUpAndTheSpareGearFitsNowhere() {
        for (a in listOf(Bronze.censer(), Celestial.chariot(), Celestial.waterClock(), Celestial.chest())) {
            assertTrue(a.buildable())
            while (true) {
                val (p, s) = a.hint() ?: break
                a.place(p, s)
            }
            assertTrue(a.solved)
        }
        val chariot = Celestial.chariot()
        val spare = Celestial.chariotParts.indexOf(Celestial.MID_GEAR)
        assertEquals(Assembly.Refusal.FITS_NOWHERE, chariot.refusal(spare, Celestial.chariotSlots[4]))
    }

    @Test
    fun theWholeGameRunsInOrderAndTheChestOpensLast() {
        val ws = Workshop()
        for (relic in Relics.all) {
            assertTrue("${relic.id} should be open", ws.progress.open(relic))
            ws.load(relic.id)
            repeat(relic.rounds) {
                ws.slide?.let { p -> for (m in p.solution()!!) p.move(m.piece, m.axis, m.to) }
                ws.rotary?.let { r -> for ((d, dir) in r.solution()!!) r.turn(d, dir) }
                ws.assembly?.let { a -> while (true) { val (part, slot) = a.hint() ?: break; a.place(part, slot) } }
                ws.afterMove()
            }
            assertTrue(relic.id, ws.progress.solved(relic.id))
        }
        assertTrue((1..4).all(ws.progress::chapterDone))
        assertEquals(Relics.all.size, ws.progress.tokens.size)
        // The chest stays shut until chapters 1-3 are done.
        val fresh = Progress(setOf("lock", "klotski", "box", "dougong"))
        assertFalse(fresh.open(Relics.byId("chest")))
        assertTrue(fresh.open(Relics.byId("wordlock")))
    }
}
