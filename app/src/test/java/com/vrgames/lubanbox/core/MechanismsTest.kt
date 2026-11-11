package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The box, the klotski rules and the 斗拱 build. */
class MechanismsTest {
    @Test
    fun boxOpensInSevenMovesAndOnlyInOrder() {
        val box = Boxes.newBox()
        val sol = box.solution()
        assertNotNull(sol)
        assertEquals(7, sol!!.size)
        // At the start only the right end panel moves.
        for (i in 0 until box.pieceCount) {
            val a = box.axes(i).single()
            val r = box.reach(i, a)
            if (i == Boxes.RIGHT) assertEquals(0..1, r) else assertEquals("${Boxes.panels[i].name} $r", 0, r.count() - 1)
        }
        for (m in sol) box.move(m.piece, m.axis, m.to)
        assertTrue(box.solved)
    }

    @Test
    fun aBoxPanelCannotSlideBackWhileTheNextSitsInItsGroove() {
        val box = Boxes.newBox()
        box.move(Boxes.RIGHT, Axis.Y, 1)
        box.move(Boxes.FRONT, Axis.X, 1)
        assertEquals(1..1, box.reach(Boxes.RIGHT, Axis.Y))
        // The left end slides down: negative along y.
        assertEquals(-2..0, box.reach(Boxes.LEFT, Axis.Y))
        box.move(Boxes.LEFT, Axis.Y, -1)
        assertEquals(1, box.positions()[Boxes.LEFT])
        box.reset()
        assertEquals(0, box.positions().sum())
    }

    private fun layout(vararg b: Pair<KBlock, Cell>) = KlotskiLayout("t", "test", b.toList())

    @Test
    fun klotskiBlocksSlideOnlyIntoFreeCells() {
        // 曹操 in the middle of an empty board, one soldier beside him.
        val k = Klotski(layout(Klotski.CAO to Cell(1, 1), Klotski.BING to Cell(3, 1)))
        assertEquals(-1..0, k.reach(0, Axis.X))
        assertEquals(-1..2, k.reach(0, Axis.Z))
        assertEquals(-1..3, k.reach(1, Axis.Z))
        k.move(0, Axis.Z, 2)
        assertTrue(k.solved)
    }

    @Test
    fun klotskiSolutionMatchesTheActualBlocks() {
        val k = Klotski(layout(Klotski.CAO to Cell(1, 0), Klotski.GUAN to Cell(1, 2), Klotski.BING to Cell(0, 4), Klotski.BING to Cell(3, 4)))
        val sol = k.solution()
        assertNotNull(sol)
        for (m in sol!!) k.move(m.piece, m.axis, m.to)
        assertTrue(k.solved)
    }

    @Test
    fun klotskiBoardsGetHarderAndAreSolvable() {
        val lengths = KlotskiBoards.rounds.map { Klotski(it).solution()!!.size }
        assertEquals(listOf(15, 20, 30), lengths)
        // Each board holds the classic ten blocks with two cells free.
        for (r in KlotskiBoards.rounds) assertEquals(18, r.blocks.sumOf { (b, _) -> b.w * b.h })
    }

    @Test
    fun workshopRunsTheChapterInOrder() {
        val ws = Workshop()
        assertEquals("box", ws.relic.id)
        assertNull(ws.next())
        for (relic in Relics.chapter(1)) {
            ws.load(relic.id)
            repeat(relic.rounds) {
                val p = ws.slide
                if (p != null) for (m in p.solution()!!) p.move(m.piece, m.axis, m.to)
                ws.assembly?.let { a -> while (true) { val (part, slot) = a.hint() ?: break; a.place(part, slot) } }
                val out = ws.afterMove()
                if (it < relic.rounds - 1) assertEquals(Workshop.Outcome.NEXT_ROUND, out) else assertEquals(Workshop.Outcome.OPENED, out)
            }
        }
        assertTrue(ws.progress.chapterDone(1))
        assertEquals(4, ws.progress.tokens.size)
        val back = Progress.parse(ws.progress.serialize())
        assertTrue(Relics.chapter(1).all { back.solved(it.id) && back.unaided(it.id) })
    }

    @Test
    fun chapterOneGoesFromEasyToHard() {
        assertEquals(listOf("box", "dougong", "klotski", "lock"), Relics.chapter(1).map { it.id })
        // The first sliding relic is short; the burr lock, the hardest, comes last.
        assertTrue(Boxes.newBox().solution()!!.size < Burrs.lock().solution()!!.size)
        assertTrue(Relics.all.all { it.idea.isNotBlank() })
    }

    @Test
    fun skippingMovesOnAndCanBeMadeGoodLater() {
        val ws = Workshop()
        ws.skip()
        assertTrue(ws.progress.solved("box"))
        assertTrue(ws.progress.skipped("box"))
        assertFalse(ws.progress.opened("box"))
        assertEquals("dougong", ws.next()?.id)
        val back = Progress.parse(ws.progress.serialize())
        assertTrue(back.skipped("box") && back.open(Relics.byId("dougong")))
        // Coming back and opening it for real clears the mark.
        ws.load("box")
        val p = ws.slide!!
        for (m in p.solution()!!) p.move(m.piece, m.axis, m.to)
        assertEquals(Workshop.Outcome.OPENED_AGAIN, ws.afterMove())
        assertFalse(ws.progress.skipped("box"))
        assertTrue(ws.progress.unaided("box"))
    }

    @Test
    fun theFirstHintIsTheIdeaAndCounts() {
        val ws = Workshop()
        assertFalse(ws.ideaGiven)
        assertEquals(Relics.byId("box").idea, ws.idea())
        assertTrue(ws.ideaGiven)
        assertEquals(1, ws.hintsUsed)
        ws.usedDemo()
        assertEquals(2, ws.hintsUsed)
        ws.load("dougong")
        assertFalse(ws.ideaGiven)
        assertEquals(0, ws.hintsUsed)
    }

    @Test
    fun hintsNameThePieceThenShowWhere() {
        val ws = Workshop()
        val h1 = ws.hint()!!
        assertFalse(h1.showTarget)
        val h2 = ws.hint()!!
        assertTrue(h2.showTarget)
        assertEquals(h1.move, h2.move)
        assertEquals(2, ws.hintsUsed)
    }

    @Test
    fun dougongBuildsBottomUpOnly() {
        val a = Dougong.newAssembly()
        assertTrue(a.buildable())
        val arm = Dougong.parts.indexOf(Dougong.HUA_GONG)
        val slot = Dougong.slots.first { it.type == Dougong.HUA_GONG }
        assertEquals(Assembly.Refusal.TOO_EARLY, a.refusal(arm, slot))
        assertNull(a.snapTarget(arm, slot.pos, 0.03))
        val base = Dougong.parts.indexOf(Dougong.LU_DOU)
        a.place(base, a.snapTarget(base, v(0.01, 0.0, 0.0), 0.03)!!)
        assertEquals(slot, a.snapTarget(arm, slot.pos + v(0.0, 0.02, 0.0), 0.03))
        // Placing by hints finishes the build.
        while (true) {
            val (p, s) = a.hint() ?: break
            a.place(p, s)
        }
        assertTrue(a.solved)
        assertFalse(a.ready(slot))
    }
}
