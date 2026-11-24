package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * Placement is locked (2026-11-23: tables and scenes kept moving in new versions without being asked). These are the
 * numbers of 0.10.0, device-tested on 2026-11-23 (the player did not object to anything about where things stand, the
 * 鲁班锁 0.45 m over the turntable included); the bench and panels are those of 0.9.1 (提交包_20261118, device-approved
 * 2026-11-18), unchanged since. The current code must reproduce them, and nothing solid in front of the player comes
 * within 0.30 m of the eyes.
 *
 * Placement (render/Bench.placeInRoom): the bench top EYE_BELOW under the eyes (game/Game.eyeY − benchBelowEye, from
 * the headset's eye height), its middle BENCH_Z straight ahead of the player, turned to face them; the turntable at its
 * middle; each relic's frame [Scene.lift] over the turntable top.
 */
class PlacementLockTest {
    private val src = File("src/main/java/com/vrgames/lubanbox")
    private val game = File(src, "game/Game.kt").readText()
    private val bench = File(src, "render/Bench.kt").readText()
    private val stage = File(src, "content/WorkshopStage.kt").readText()

    private fun const(text: String, name: String): Double =
        Regex("const val $name = (-?[0-9.]+)f?").find(text)?.groupValues?.get(1)?.toDouble() ?: error("$name not found")

    private fun vec(text: String, name: String): DoubleArray =
        Regex("val $name = Vector3\\((-?[0-9.]+)f, (-?[0-9.]+)f, (-?[0-9.]+)f\\)").find(text)?.groupValues?.drop(1)?.map { it.toDouble() }?.toDoubleArray()
            ?: error("$name not found")

    companion object {
        const val EYE_BELOW = 0.45
        const val BENCH_Z = 0.75
        const val BENCH_W = 1.0
        const val BENCH_D = 0.6
        const val TURNTABLE_R = 0.2
        const val TURNTABLE_H = 0.03
        /** The 鲁班锁's middle over the turntable top (0.10.0). */
        const val LOCK_LIFT = 0.45
        val PANELS = mapOf(
            "CARD_POS" to doubleArrayOf(0.47, 0.38, -0.1),
            "HELP_POS" to doubleArrayOf(0.86, 0.16, 0.14),
            "HELP_FOLDED_POS" to doubleArrayOf(0.66, 0.08, 0.14),
            "MENU_POS" to doubleArrayOf(-0.42, 0.07, 0.12),
            "MENU_OPEN_POS" to doubleArrayOf(-0.42, 0.36, 0.12),
        )
        const val BODY_CLEAR = 0.30
        const val MM = 1e-3
    }

    @Test
    fun benchStaysWhere0100PutIt() {
        assertEquals(EYE_BELOW, const(game, "DEFAULT_BENCH_BELOW_EYE"), MM)
        assertEquals(BENCH_Z, const(bench, "BENCH_Z"), MM)
        assertEquals(TURNTABLE_R, const(bench, "TURNTABLE_R"), MM)
        assertEquals(TURNTABLE_H, const(bench, "TURNTABLE_H"), MM)
        assertTrue("bench stand-in 1.0 × 0.6 m", bench.contains("assets.box(Vector3(1.0f, 0.04f, 0.6f), Vector3(0f, -0.02f, 0f), assets.woodLight)"))
        assertTrue("bench straight ahead", bench.contains("root.setPos(Vector3(standX - distance * sin(a).toFloat(), height, standZ - distance * cos(a).toFloat()))"))
        assertTrue("bench top from the eyes", game.contains("set(v) { field = v; benchHeight = v - benchBelowEye }"))
        assertTrue("relic frame over the turntable", bench.contains("stage.setPos(Vector3(0f, TURNTABLE_H + sc.lift, 0f))"))
        assertEquals(LOCK_LIFT, Burrs.LIFT, MM)
        assertTrue(stage.contains("put(PANEL_INTRO, Vector3(0f, 0.5f, -0.2f))"))
        assertEquals(-15.0, const(stage, "CARD_YAW"), 0.1)
        for ((name, v) in PANELS) {
            val now = vec(stage, name)
            for (k in 0..2) assertEquals("$name[$k]", v[k], now[k], MM)
        }
        // Standing 1.65 m, seated 1.25 m: the bench top 0.45 m under the eyes.
        for (eye in listOf(1.65, 1.25)) assertEquals(eye - 0.45, eye - EYE_BELOW, MM)
    }

    /**
     * v0.10.1 changed where the 鲁班盒 stands on the turntable (not where the bench or turntable is): it has a 2.3 cm
     * plinth now, so its frame is 1.8 cm higher (it was on the turntable top, with its panels 5 mm up). Held here.
     */
    @Test
    fun boxStandsOnItsPlinth() {
        assertEquals(0.018, Boxes.LIFT, 1e-9)
        assertEquals(Boxes.plinth.lo.y + Boxes.LIFT, 0.0, 1e-9)
    }

    @Test
    fun nothingSolidWithin30cmOfTheEyes() {
        val near = BENCH_Z - BENCH_D / 2
        assertTrue("bench near edge $near m", near >= BODY_CLEAR)
        for ((name, v) in PANELS) assertTrue(name, hypot(v[0], BENCH_Z - v[2]) >= BODY_CLEAR)
        println("placement: bench middle $BENCH_Z m ahead, top $EYE_BELOW m below the eyes, near edge ${"%.2f".format(near)} m; 鲁班锁 ${LOCK_LIFT} m over the turntable; 鲁班盒 frame ${Boxes.LIFT} m over it (v0.10.1, was 0)")
    }
}
