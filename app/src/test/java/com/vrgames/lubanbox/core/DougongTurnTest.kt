package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.10 (PICO 2026-11-23: some 斗拱 parts lay crosswise with no way to turn them): turning parts, and facing the slot. */
class DougongTurnTest {
    private fun fresh() = Dougong.newAssembly().also { a -> Dougong.tray.forEachIndexed { i, (_, yaw) -> a.setYaw(i, yaw) } }
    private fun idx(type: String) = Dougong.parts.indexOf(type)
    private fun slot(id: String) = Dougong.slots.first { it.id == id }

    @Test
    fun theFourLongArmsLieTheWrongWayRoundAtFirst() {
        val a = fresh()
        for (t in listOf(Dougong.NI_DAO_GONG, Dougong.LING_GONG, Dougong.ZHU_TOU_FANG, Dougong.LIAO_YAN_FANG)) {
            val i = idx(t)
            assertFalse(t, a.angleOk(i, Dougong.slots.first { it.type == t }))
            a.turn(i)
            assertTrue(t, a.angleOk(i, Dougong.slots.first { it.type == t }))
        }
    }

    @Test
    fun wrongWayRoundIsRefusedOneTurnAndItGoesIn() {
        val a = fresh()
        a.place(idx(Dougong.LU_DOU), slot("栌斗"))
        a.place(idx(Dougong.HUA_GONG), slot("华栱"))
        val arm = idx(Dougong.NI_DAO_GONG)
        val s = slot("泥道栱")
        val at = s.pos + v(0.01, 0.005, -0.01)
        // Over its slot, ready, but lengthwise: no snap; the slot is found when the angle is ignored, and the reason is the angle.
        assertNull(a.snapTarget(arm, at, 0.035))
        assertEquals(s, a.snapTarget(arm, at, 0.035, facing = false))
        assertEquals(Assembly.Refusal.WRONG_ANGLE, a.refusal(arm, s, facing = true))
        // One quarter turn (90° → 180°, the same as 0° end to end) and it snaps.
        assertEquals(180.0, a.turn(arm), 1e-9)
        assertEquals(s, a.snapTarget(arm, at, 0.035))
        a.place(arm, s)
        // It settles at the same-looking angle nearest its own: no half spin on the way in.
        assertEquals(180.0, a.yawOf(arm), 1e-9)
    }

    @Test
    fun symmetricPartsAcceptTheTurnsThatLookTheSame() {
        val a = fresh()
        fun ok(type: String, slotId: String, yaw: Double): Boolean {
            val i = idx(type)
            a.setYaw(i, yaw)
            return a.angleOk(i, slot(slotId))
        }
        // 栌斗 and 交互斗: a cross seat, any quarter turn.
        for (y in listOf(0.0, 90.0, 180.0, 270.0)) {
            assertTrue(ok(Dougong.LU_DOU, "栌斗", y))
            assertTrue(ok(Dougong.JIAO_HU_DOU, "交互斗", y))
        }
        // 散斗: its seat runs one way. v0.10.1: 齐心斗 takes a 交互斗 (cross seat), any quarter turn.
        assertTrue(ok(Dougong.SAN_DOU, "左散斗", 180.0))
        assertFalse(ok(Dougong.SAN_DOU, "左散斗", 90.0))
        for (y in listOf(0.0, 90.0, 180.0, 270.0)) assertTrue(ok(Dougong.JIAO_HU_DOU, "齐心斗", y))
        // 泥道栱, 令栱, 柱头枋, 撩檐枋: the same end to end.
        assertTrue(ok(Dougong.LING_GONG, "令栱", 180.0))
        assertFalse(ok(Dougong.LING_GONG, "令栱", 270.0))
        // 华栱 (its halving is off-centre) and 耍头 (its beak) go in only one way.
        assertTrue(ok(Dougong.HUA_GONG, "华栱", 0.0))
        assertFalse(ok(Dougong.HUA_GONG, "华栱", 180.0))
        assertFalse(ok(Dougong.SHUA_TOU, "耍头", 180.0))
        // Within 20°, near enough; 25° is not.
        assertTrue(ok(Dougong.SHUA_TOU, "耍头", 15.0))
        assertTrue(ok(Dougong.SHUA_TOU, "耍头", 345.0))
        assertFalse(ok(Dougong.SHUA_TOU, "耍头", 25.0))
    }

    @Test
    fun builtTheWayAPlayerWouldTurningWhenNeeded() {
        val a = fresh()
        var turns = 0
        while (!a.solved) {
            val (part, s) = a.hint()!!
            var k = 0
            while (!a.angleOk(part, s)) {
                a.turn(part)
                turns++
                assertTrue(++k <= 3)
            }
            assertEquals(s, a.snapTarget(part, s.pos, 0.035))
            a.place(part, s)
        }
        // The four long arms (v0.10.1: 齐心斗 is a cross-seat block now and needs no turn).
        assertEquals(4, turns)
    }

    /** The bench layout: nothing overlaps, nothing on the turntable, and each long arm has room for its turn. */
    @Test
    fun theTrayLeavesRoomToTurnTheLongArms() {
        fun rect(i: Int, yaw: Double): DoubleArray {
            val (w, d) = Dougong.footprint(Dougong.parts[i])
            val turned = Math.round(yaw / 90.0) % 2 == 1L
            val hx = (if (turned) d else w) / 2
            val hz = (if (turned) w else d) / 2
            val p = Dougong.tray[i].first
            return doubleArrayOf(p.x - hx, p.x + hx, p.z - hz, p.z + hz)
        }
        fun overlap(a: DoubleArray, b: DoubleArray, gap: Double) = a[0] < b[1] + gap && b[0] < a[1] + gap && a[2] < b[3] + gap && b[2] < a[3] + gap
        val n = Dougong.parts.size
        val start = (0 until n).map { rect(it, Dougong.tray[it].second) }
        for (i in 0 until n) {
            val r = start[i]
            // On the bench (1.0 × 0.6 m), off the turntable (radius 0.2 m, with 5 mm to spare).
            assertTrue("$i on bench", r[0] >= -0.5 && r[1] <= 0.5 && r[2] >= -0.3 && r[3] <= 0.3)
            val nx = r[0].coerceAtLeast(-0.0).let { if (r[0] > 0) r[0] else if (r[1] < 0) r[1] else 0.0 }
            val nz = if (r[2] > 0) r[2] else if (r[3] < 0) r[3] else 0.0
            assertTrue("$i off the turntable", kotlin.math.hypot(nx, nz) > 0.205)
            for (j in 0 until i) assertFalse("$i and $j overlap", overlap(r, start[j], 0.01))
            // The menu tab hangs at the front left (x -0.48..-0.36, z 0.12): nothing under or just behind it.
            assertFalse("$i under the menu tab", overlap(r, doubleArrayOf(-0.49, -0.35, 0.06, 0.13), 0.0))
        }
        for (t in listOf(Dougong.NI_DAO_GONG, Dougong.LING_GONG, Dougong.ZHU_TOU_FANG, Dougong.LIAO_YAN_FANG)) {
            val i = Dougong.parts.indexOf(t)
            val turned = rect(i, Dougong.tray[i].second + 90)
            val nx = if (turned[0] > 0) turned[0] else if (turned[1] < 0) turned[1] else 0.0
            val nz = if (turned[2] > 0) turned[2] else if (turned[3] < 0) turned[3] else 0.0
            assertTrue("$t turned stays off the turntable", kotlin.math.hypot(nx, nz) > 0.205)
            for (j in 0 until n) if (j != i) assertFalse("$t turned meets part $j", overlap(turned, start[j], 0.01))
        }
    }
}
