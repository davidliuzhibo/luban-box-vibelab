package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector3

/**
 * Models and materials. The mechanisms are modeled in Blender from the same data the rules use
 * (tools/make_*.py): one GLB per relic with a named node per piece, each modeled where it sits in
 * the assembled object (origin = the object's own origin). [load] collects every named node as a
 * template; [piece] hands out copies. Until a model loads, boxes stand in.
 *
 * Resources shared between copies are made app-owned ([shared]): the SDK releases a resource
 * with the entity that owns it, and relics are rebuilt often.
 */
class Assets {
    val unitBox: MeshResource = MeshResource.createBox(Vector3(1f, 1f, 1f)).shared()
    val unitSphere: MeshResource = MeshResource.createSphere(1f).shared()

    // Stand-in woods (used when a model is missing, and for the bench).
    val woodLight = pbr(color(0.78f, 0.58f, 0.38f), roughness = 0.55f).shared()
    val woodMid = pbr(color(0.55f, 0.3f, 0.17f), roughness = 0.5f).shared()
    val woodDark = pbr(color(0.3f, 0.14f, 0.09f), roughness = 0.45f).shared()
    val brass = pbr(color(0.85f, 0.65f, 0.3f), metallic = 0.9f, roughness = 0.3f).shared()

    /** Hint: a warm outline on the piece to move next, and a ghost where it goes. */
    val hintGlow = rim(color(1f, 0.75f, 0.35f, 0.9f)).shared()
    val ghost = unlit(color(1f, 0.85f, 0.5f, 0.3f), BlendingMode.TRANSPARENT).shared()
    /** Where a 斗拱 part may go now (faint), and the slot under the part in hand (bright). */
    val slotOpen = unlit(color(0.55f, 0.9f, 1f, 0.16f), BlendingMode.TRANSPARENT).shared()
    val slotNear = unlit(color(0.55f, 0.95f, 1f, 0.38f), BlendingMode.TRANSPARENT).shared()
    val dustMaterial = glow(color(1f, 0.85f, 0.6f, 0.55f)).shared()
    val sparkMaterial = glow(color(1f, 0.8f, 0.4f)).shared()
    /** The 透光镜's lamp light. */
    val beamMaterial = glow(color(1f, 0.92f, 0.7f, 0.7f)).shared()
    val dustMesh: MeshResource = MeshResource.createSphere(0.0025f).shared()

    private val templates = HashMap<String, Entity>()

    /** Registers every named node under [root] (a loaded GLB) as a template. */
    fun load(root: Entity) {
        fun walk(e: Entity) {
            val name = e.getName()
            if (!name.isNullOrEmpty() && e.components[ModelComponent::class.java] != null) {
                shareResources(e)
                templates[name] = e
            }
            e.getChildren().forEach(::walk)
        }
        walk(root)
    }

    fun has(name: String) = name in templates

    /** A copy of the node [name], at the origin of whatever it is added to; null if not loaded. */
    fun piece(name: String): Entity? = templates[name]?.clone(Entity.CloneOptions(true, true))?.also {
        it.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0f, 0f))
        }
    }

    /** A box stand-in: [size] in meters, centered at [center] (object frame). */
    fun box(size: Vector3, center: Vector3, material: Material): Entity =
        model(unitBox, material).apply {
            transform().setScaleVector(size)
            setPos(center)
        }

    private fun shareResources(e: Entity) {
        e.components[ModelComponent::class.java]?.let { m ->
            m.mesh?.shared()
            m.materials?.forEach { it.shared() }
        }
        e.getChildren().forEach(::shareResources)
    }

    companion object {
        /** Sets every material of [e] and its children (for outlines and ghosts made from a piece). */
        fun setMaterials(e: Entity, m: Material) {
            e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
            e.getChildren().forEach { setMaterials(it, m) }
        }
    }
}
