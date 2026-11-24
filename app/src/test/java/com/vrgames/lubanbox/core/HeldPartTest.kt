package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** v0.10.1: a build part carried in the hand rides over what is there, and still goes into its slot from on top. */
class HeldPartTest {
    @Test
    fun aHeldPartNeverSinksIntoAnything() {
        val rnd = Random(1123)
        repeat(2000) {
            val obstacles = List(rnd.nextInt(1, 8)) {
                val x = rnd.nextDouble(-0.2, 0.2)
                val z = rnd.nextDouble(-0.2, 0.2)
                val y = rnd.nextDouble(-0.05, 0.2)
                box(x, x + rnd.nextDouble(0.005, 0.2), y, y + rnd.nextDouble(0.005, 0.1), z, z + rnd.nextDouble(0.005, 0.2))
            }
            val part = box(-rnd.nextDouble(0.01, 0.12), rnd.nextDouble(0.01, 0.12), -rnd.nextDouble(0.0, 0.06), rnd.nextDouble(0.005, 0.06), -rnd.nextDouble(0.01, 0.12), rnd.nextDouble(0.01, 0.12))
            val at = Vec3(rnd.nextDouble(-0.25, 0.25), rnd.nextDouble(-0.1, 0.3), rnd.nextDouble(-0.25, 0.25))
            val y = liftOver(part, at, obstacles, -0.03)
            val placed = part.moved(Vec3(at.x, y, at.z))
            for (o in obstacles) assertTrue("sinks ${Sweep.penetration(placed, o)}", Sweep.penetration(placed, o) <= 1e-9)
            assertTrue(placed.lo.y >= -0.03 - 1e-9)
            // Never higher than it needs to be: resting on something, on the floor, or where the hand is.
            assertTrue(y == at.y || kotlin.math.abs(placed.lo.y + 0.03) < 1e-9 || obstacles.any { kotlin.math.abs(it.hi.y - placed.lo.y) < 1e-9 })
        }
    }

    @Test
    fun aPartRidingOnTopOverItsSlotGoesIn() {
        val a = Dougong.newAssembly()
        val lu = a.parts.indexOf(Dougong.LU_DOU)
        a.place(lu, a.slots.first { it.id == "栌斗" })
        val hua = a.parts.indexOf(Dougong.HUA_GONG)
        val slot = a.slots.first { it.id == "华栱" }
        // Held 4 cm over its slot (on top of the 栌斗), 1 cm off to the side: too far for the plain snap, fine riding on top.
        val at = slot.pos + Vec3(0.01, 0.04, 0.0)
        assertEquals(null, a.snapTarget(hua, at, 0.035))
        assertEquals(slot, a.snapTargetOver(hua, at, 0.035))
        // Far off to the side it does not.
        assertEquals(null, a.snapTargetOver(hua, slot.pos + Vec3(0.06, 0.04, 0.0), 0.035))
    }

    @Test
    fun buildPropsAreTheirModelsBounds() {
        // Held parts ride over these (tools/audit_meshes.py checks each model lies inside).
        assertEquals(0.372, BuildProps.boxes("waterclock").single().hi.y, 1e-9)
        assertEquals(0.12, BuildProps.boxes("chest").single().hi.y, 1e-9)
        assertTrue(BuildProps.boxes("box").isEmpty())
    }
}
