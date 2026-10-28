package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Axis
import com.vrgames.lubanbox.core.RotaryPuzzle
import com.vrgames.lubanbox.core.Vec3
import kotlin.math.abs
import kotlin.math.exp

/** How a dial of a [RotaryPuzzle] looks: its model and pick box, around its own pivot. */
class DialLook(
    /** The turning part, modeled around its pivot (its holder sits at the pivot). */
    val visual: Entity,
    /** Where the pivot is, in the frame of [parent] (or of the view's root). */
    val pivot: Vec3,
    /** Collision boxes (size, center) around the pivot, for pinching. */
    val hitBoxes: List<Pair<Vector3, Vector3>>,
    /** Index of the dial this one rides on (the mirror's face on its yoke), or null. */
    val parent: Int? = null,
    /** Hand movement that turns the dial: along x (sideways) or along y (up and down). */
    val dragAlong: Axis = Axis.Y,
    /** Fixed turn of the holder before the dial's own (a tilted polar axis). */
    val baseTilt: Double = 0.0,
)

/**
 * Shows a [RotaryPuzzle] under [root] and turns drags into single stops: every [STEP] of hand
 * movement turns the dial one stop (with its geared neighbours), or bumps if a pin holds it.
 * Dials turn smoothly to their stops.
 */
class RotaryView(val puzzle: RotaryPuzzle, val root: Entity, private val looks: List<DialLook>) {
    private val holders = looks.map { Entity() }
    private val spinners = looks.map { Entity() }
    private val shown = DoubleArray(looks.size) { puzzle.dials[it].angle(puzzle.position(it)) }

    var dragging: Int? = null; private set
    private var hand = 0.0

    init {
        looks.forEachIndexed { i, look ->
            val h = holders[i]
            h.setPos(look.pivot)
            h.transform().eulerAngles = EulerAngles(look.baseTilt.toFloat(), 0f, 0f)
            h.addChild(spinners[i])
            spinners[i].addChild(look.visual)
            spinners[i].components.set(
                CollisionComponent(
                    look.hitBoxes.map { (size, center) -> ShapeResource.createBox(size).offsetByTranslation(center) },
                    PhysicsMaterialResource(),
                ),
            )
            spinners[i].components.set(InteractableComponent())
            spinners[i].components.set(HoverEffectComponent())
        }
        looks.forEachIndexed { i, look -> (look.parent?.let { spinners[it] } ?: root).addChild(holders[i]) }
        for (i in looks.indices) apply(i)
    }

    fun dialOf(e: Entity?): Int? {
        var cur = e
        while (cur != null) {
            val i = spinners.indexOfFirst { it === cur }
            if (i >= 0) return i
            cur = cur.getParent()
        }
        return null
    }

    /** The turning part of dial [i] (children ride along). */
    fun spinner(i: Int) = spinners[i]

    fun begin(i: Int): Boolean {
        dragging = i
        hand = 0.0
        return true
    }

    sealed interface Turn {
        data class Turned(val dial: Int, val dir: Int) : Turn
        data object Held : Turn
    }

    /** [d]: hand movement in the object frame; returns a turn when the hand has gone a step. */
    fun drag(d: Vec3): Turn? {
        val i = dragging ?: return null
        hand += if (looks[i].dragAlong == Axis.X) d.x else d.y
        if (abs(hand) < STEP) return null
        val dir = if (hand > 0) 1 else -1
        hand -= dir * STEP
        return if (puzzle.turn(i, dir)) Turn.Turned(i, dir) else Turn.Held.also { hand = 0.0 }
    }

    fun end() {
        dragging = null
    }

    fun frame(dt: Double) {
        val k = 1 - exp(-dt * 12)
        for (i in looks.indices) {
            val d = puzzle.dials[i]
            var target = d.angle(puzzle.position(i))
            // Turn the short way round on wrapping dials.
            if (d.wraps) {
                while (target - shown[i] > 180) target -= 360
                while (target - shown[i] < -180) target += 360
            }
            if (abs(target - shown[i]) < 0.01) continue
            shown[i] += (target - shown[i]) * k
            apply(i)
        }
    }

    fun snapAll() {
        for (i in looks.indices) {
            shown[i] = puzzle.dials[i].angle(puzzle.position(i))
            apply(i)
        }
    }

    /** Where dial [i]'s pivot is in the root frame (for hints), ignoring its parents' turns. */
    fun pivot(i: Int): Vec3 = looks[i].parent?.let { looks[it].pivot + looks[i].pivot } ?: looks[i].pivot

    fun dragAlong(i: Int) = looks[i].dragAlong

    private fun apply(i: Int) {
        val a = shown[i].toFloat()
        spinners[i].transform().eulerAngles = when (puzzle.dials[i].axis) {
            Axis.X -> EulerAngles(a, 0f, 0f)
            Axis.Y -> EulerAngles(0f, a, 0f)
            Axis.Z -> EulerAngles(0f, 0f, a)
        }
    }

    companion object {
        /** Hand travel per stop. */
        const val STEP = 0.025
    }
}
