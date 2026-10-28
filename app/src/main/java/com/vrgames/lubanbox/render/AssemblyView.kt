package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Assembly
import com.vrgames.lubanbox.core.Slot
import com.vrgames.lubanbox.core.Vec3
import kotlin.math.exp

/**
 * Shows an [Assembly] (斗拱) under [root]: loose parts laid out on the bench around the build spot,
 * and a faint outline wherever a part may go now. A part follows the hand freely; let go near a
 * slot that is ready for it and it settles in.
 *
 * [visual] makes a part's model (origin at the middle of its underside, like the slots).
 */
class AssemblyView(
    val assembly: Assembly,
    val root: Entity,
    private val assets: Assets,
    visual: (type: String) -> Entity,
    /** Collision box of each part type: size, and center above the part's origin. */
    private val hitBox: (type: String) -> Pair<Vector3, Vector3>,
    /** Where each loose part lies on the bench (object frame) and its yaw there (degrees). */
    private val tray: List<Pair<Vec3, Double>>,
    /** Height of the bench top in the object frame: parts never go below it. */
    private val floorY: Double,
) {
    private val holders = assembly.parts.map { Entity() }
    private val pos = Array(assembly.parts.size) { trayPosition(it) }
    private val goal = arrayOfNulls<Vec3>(assembly.parts.size)
    private val yaw = DoubleArray(assembly.parts.size) { tray[it].second }
    private val outlines = assembly.slots.associateWith { slot -> visual(slot.type).also { Assets.setMaterials(it, assets.slotOpen) } }

    var dragging: Int? = null; private set
    /** The slot under the part in hand, if it would take it. */
    var near: Slot? = null; private set

    init {
        assembly.parts.forEachIndexed { i, type ->
            val h = holders[i]
            h.transform().eulerAngles = EulerAngles(0f, yaw[i].toFloat(), 0f)
            h.addChild(visual(type))
            val (size, center) = hitBox(type)
            h.components.set(CollisionComponent(listOf(ShapeResource.createBox(size).offsetByTranslation(center)), PhysicsMaterialResource()))
            h.components.set(InteractableComponent())
            h.components.set(HoverEffectComponent())
            root.addChild(h)
            h.setPos(pos[i])
        }
        for ((slot, o) in outlines) {
            root.addChild(o)
            o.setPos(slot.pos)
            o.transform().eulerAngles = EulerAngles(0f, slot.yaw.toFloat(), 0f)
            o.enabled = false
        }
    }

    fun partOf(e: Entity?): Int? {
        var cur = e
        while (cur != null) {
            val i = holders.indexOfFirst { it === cur }
            if (i >= 0) return i
            cur = cur.getParent()
        }
        return null
    }

    fun holder(i: Int) = holders[i]
    fun position(i: Int) = pos[i]

    fun begin(i: Int): Boolean {
        if (assembly.isPlaced(i)) return false
        dragging = i
        goal[i] = null
        showOutlines(i)
        return true
    }

    /** [d]: hand movement in the object frame. The part stays above the bench top. */
    fun drag(d: Vec3) {
        val i = dragging ?: return
        val p = pos[i] + d
        pos[i] = Vec3(p.x, maxOf(p.y, floorY), p.z)
        holders[i].setPos(pos[i])
        val slot = assembly.snapTarget(i, pos[i], SNAP)
        if (slot != near) {
            near?.let { outlines[it]?.let { o -> Assets.setMaterials(o, assets.slotOpen) } }
            slot?.let { outlines[it]?.let { o -> Assets.setMaterials(o, assets.slotNear) } }
            near = slot
        }
    }

    /** Result of letting go of a part. */
    sealed interface Drop {
        data class Placed(val part: Int, val slot: Slot) : Drop
        /** Near a slot it cannot take (yet): why, for the notes. */
        data class Refused(val part: Int, val slot: Slot, val why: Assembly.Refusal) : Drop
        data object Loose : Drop
    }

    fun end(): Drop {
        val i = dragging ?: return Drop.Loose
        dragging = null
        near = null
        outlines.values.forEach { it.enabled = false }
        val slot = assembly.snapTarget(i, pos[i], SNAP)
        if (slot != null) {
            assembly.place(i, slot)
            goal[i] = slot.pos
            yaw[i] = slot.yaw
            return Drop.Placed(i, slot)
        }
        val close = assembly.nearest(pos[i], SNAP * 1.5)
        // Dropped far from the build: it goes back to its place on the bench.
        if (pos[i].distanceTo(Vec3.ZERO) > 0.6) goal[i] = trayPosition(i)
        val why = close?.let { assembly.refusal(i, it) }
        return if (close != null && why != null) Drop.Refused(i, close, why) else Drop.Loose
    }

    fun frame(dt: Double) {
        val k = 1 - exp(-dt * 14)
        for (i in holders.indices) {
            val g = goal[i] ?: continue
            if (i == dragging) continue
            pos[i] = pos[i] + (g - pos[i]) * k
            holders[i].setPos(pos[i])
            holders[i].transform().eulerAngles = EulerAngles(0f, yaw[i].toFloat(), 0f)
            if (pos[i].distanceTo(g) < 1e-4) {
                pos[i] = g
                goal[i] = null
            }
        }
    }

    /** Places [part] in [slot] without a hand (AutoPilot), animated like a drop. */
    fun placeAuto(part: Int, slot: Slot) {
        assembly.place(part, slot)
        goal[part] = slot.pos
        yaw[part] = slot.yaw
    }

    /** Shows where a hint's part goes. */
    fun showSlot(slot: Slot?) {
        outlines.forEach { (s, o) -> o.enabled = s == slot }
    }

    fun snapAll() {
        for (i in holders.indices) {
            val s = assembly.slotOf(i)
            pos[i] = s?.pos ?: trayPosition(i)
            yaw[i] = s?.yaw ?: tray[i].second
            goal[i] = null
            holders[i].setPos(pos[i])
            holders[i].transform().eulerAngles = EulerAngles(0f, yaw[i].toFloat(), 0f)
        }
    }

    private fun showOutlines(i: Int) {
        for ((slot, o) in outlines) {
            o.enabled = slot.type == assembly.parts[i] && assembly.ready(slot)
            Assets.setMaterials(o, assets.slotOpen)
        }
    }

    private fun trayPosition(i: Int): Vec3 = tray[i].first + Vec3(0.0, floorY, 0.0)

    companion object {
        /** Let go within this distance of a ready slot and the part settles in. */
        const val SNAP = 0.035
    }
}
