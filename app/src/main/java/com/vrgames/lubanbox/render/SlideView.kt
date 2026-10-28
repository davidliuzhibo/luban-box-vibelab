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
import com.vrgames.lubanbox.core.Move
import com.vrgames.lubanbox.core.SlidePuzzle
import com.vrgames.lubanbox.core.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** How a piece of a [SlidePuzzle] looks and where it sits. */
class PieceLook(
    /** The piece's model, in the object frame with the piece at offset zero. */
    val visual: Entity,
    /** Collision boxes (size, center) in the same frame, for pinching. */
    val hitBoxes: List<Pair<Vector3, Vector3>>,
    /** Meters per step along each axis. */
    val step: Double,
    /** Where it goes once it comes out of the mechanism (burr sticks), and how it lies there (degrees about x, y, z). */
    val restPos: Vec3? = null,
    val restRot: Vec3? = null,
)

/**
 * Shows a [SlidePuzzle] under [root] (the mechanism's object frame) and turns drags into moves.
 *
 * A drag first waits for a few millimetres of hand movement and locks onto the piece's axis that
 * movement follows best. From then on the piece follows the hand along that axis only, within
 * what the rules allow; pushing past a blocked end gives one bump, crossing each whole step a
 * tick. Letting go settles the piece on the nearest whole step and makes the move.
 */
class SlideView(val puzzle: SlidePuzzle, val root: Entity, private val looks: List<PieceLook>) {
    private val holders = looks.map { Entity() }
    /** Displayed offset of each piece along x, y, z (fractional while dragged or settling). */
    private val shown = Array(puzzle.pieceCount) { i -> DoubleArray(3) { a -> offsetOr0(i, Axis.entries[a]) } }
    private val removedShown = BooleanArray(puzzle.pieceCount)
    private val flyAge = DoubleArray(puzzle.pieceCount) { -1.0 }

    var dragging: Int? = null; private set
    private var axis: Axis? = null
    private var hand = Vec3.ZERO
    private var startOffset = 0
    private var range = 0..0
    private var lastTick = 0
    private var bumped = false

    init {
        looks.forEachIndexed { i, look ->
            val h = holders[i]
            h.addChild(look.visual)
            h.components.set(
                CollisionComponent(
                    look.hitBoxes.map { (size, center) -> ShapeResource.createBox(size).offsetByTranslation(center) },
                    PhysicsMaterialResource(),
                ),
            )
            h.components.set(InteractableComponent())
            h.components.set(HoverEffectComponent())
            root.addChild(h)
            place(i)
        }
    }

    private fun offsetOr0(i: Int, a: Axis) = if (a in puzzle.axes(i)) puzzle.offset(i, a).toDouble() else 0.0

    fun pieceOf(e: Entity?): Int? {
        var cur = e
        while (cur != null) {
            val i = holders.indexOfFirst { it === cur }
            if (i >= 0) return i
            cur = cur.getParent()
        }
        return null
    }

    fun holder(i: Int) = holders[i]

    /** Middle of piece [i] where it is shown now (object frame). */
    fun center(i: Int): Vec3 {
        val boxes = looks[i].hitBoxes
        val c = boxes.fold(Vec3.ZERO) { s, (_, m) -> s + Vec3(m.x.toDouble(), m.y.toDouble(), m.z.toDouble()) } * (1.0 / boxes.size)
        return c + offsetVector(i)
    }

    /** The piece [i] would be at offset [to] along [axis]: the offset vector of that place. */
    fun offsetVectorAt(i: Int, axis: Axis, to: Int): Vec3 {
        val o = shown[i].copyOf()
        o[axis.ordinal] = to.toDouble()
        return Vec3(o[0], o[1], o[2]) * looks[i].step
    }

    /** A copy of piece [i]'s model (for the hint's ghost). */
    fun visualCopy(i: Int): Entity? = looks[i].visual.clone(Entity.CloneOptions(true, true))

    /** The piece's current place in the object frame (its offset as a vector). */
    fun offsetVector(i: Int): Vec3 = Vec3(shown[i][0], shown[i][1], shown[i][2]) * looks[i].step

    fun begin(i: Int): Boolean {
        if (puzzle.removed(i)) return false
        dragging = i
        axis = null
        hand = Vec3.ZERO
        bumped = false
        return true
    }

    enum class Feel { TICK, BUMP }

    /** [d]: the hand's movement since the last call, in the object frame (meters). */
    fun drag(d: Vec3): Feel? {
        val i = dragging ?: return null
        hand += d
        val a = axis ?: run {
            if (hand.length() < AXIS_PICK) return null
            val best = puzzle.axes(i).maxBy { abs(component(hand, it)) }
            axis = best
            startOffset = puzzle.offset(i, best)
            range = puzzle.reach(i, best)
            lastTick = startOffset
            best
        }
        val want = startOffset + component(hand, a) / looks[i].step
        val clamped = want.coerceIn(range.first.toDouble(), range.last.toDouble())
        shown[i][a.ordinal] = clamped
        place(i)
        val over = abs(want - clamped)
        if (over > BUMP_AT && !bumped) {
            bumped = true
            return Feel.BUMP
        }
        if (over < BUMP_AT / 3) bumped = false
        val tick = floor(clamped + 0.5).toInt()
        if (tick != lastTick) {
            lastTick = tick
            return Feel.TICK
        }
        return null
    }

    /** Ends the drag: the move made (null if the piece ended where it started). */
    fun end(): Move? {
        val i = dragging ?: return null
        dragging = null
        val a = axis ?: return null
        val target = shown[i][a.ordinal].roundToInt().coerceIn(range)
        if (target == puzzle.offset(i, a)) return null
        puzzle.move(i, a, target)
        return Move(i, a, target)
    }

    /** Settles pieces on whole steps and flies pieces that came out to their rest spot. */
    fun frame(dt: Double) {
        val k = 1 - kotlin.math.exp(-dt * 18)
        for (i in 0 until puzzle.pieceCount) {
            if (i == dragging) continue
            if (puzzle.removed(i) && !removedShown[i]) {
                removedShown[i] = true
                flyAge[i] = 0.0
                // Fly from where it came out of the lock.
                for (a in puzzle.axes(i)) shown[i][a.ordinal] = puzzle.offset(i, a).toDouble()
            }
            if (flyAge[i] >= 0) {
                fly(i, dt)
                continue
            }
            // Out of the mechanism and at rest: nothing more to settle.
            if (removedShown[i]) continue
            var moved = false
            for (a in puzzle.axes(i)) {
                val target = puzzle.offset(i, a).toDouble()
                val s = shown[i][a.ordinal]
                if (abs(s - target) > 1e-4) {
                    shown[i][a.ordinal] = s + (target - s) * k
                    moved = true
                }
            }
            if (moved) place(i)
        }
    }

    /** Slides out a little farther, then arcs over to the rest spot and lies down there. */
    private fun fly(i: Int, dt: Double) {
        val look = looks[i]
        val rest = look.restPos ?: return
        flyAge[i] += dt
        val t = (flyAge[i] / FLY_S).coerceIn(0.0, 1.0)
        val e = t * t * (3 - 2 * t)
        val from = offsetVector(i)
        val p = from + (rest - from) * e + Vec3(0.0, 0.08 * kotlin.math.sin(Math.PI * e), 0.0)
        // Turn first, then place: the position is set last so nothing overwrites it.
        look.restRot?.let { r -> holders[i].transform().eulerAngles = EulerAngles((r.x * e).toFloat(), (r.y * e).toFloat(), (r.z * e).toFloat()) }
        holders[i].setPos(p)
        if (t >= 1.0) flyAge[i] = -2.0
    }

    /** Puts every piece back at its rules position at once (after a reset or a new round). */
    fun snapAll() {
        for (i in 0 until puzzle.pieceCount) {
            for (a in Axis.entries) shown[i][a.ordinal] = offsetOr0(i, a)
            removedShown[i] = puzzle.removed(i)
            flyAge[i] = -1.0
            holders[i].transform().eulerAngles = EulerAngles(0f, 0f, 0f)
            if (puzzle.removed(i)) {
                looks[i].restPos?.let { holders[i].setPos(it) }
                looks[i].restRot?.let { r -> holders[i].transform().eulerAngles = EulerAngles(r.x.toFloat(), r.y.toFloat(), r.z.toFloat()) }
            } else {
                place(i)
            }
        }
    }

    private fun place(i: Int) = holders[i].setPos(offsetVector(i))

    private fun component(v: Vec3, a: Axis) = when (a) {
        Axis.X -> v.x
        Axis.Y -> v.y
        Axis.Z -> v.z
    }

    companion object {
        /** Hand travel that decides the drag's axis. */
        const val AXIS_PICK = 0.008
        /** How far (in steps) past a blocked end the hand must push for a bump. */
        const val BUMP_AT = 0.3
        const val FLY_S = 0.7
    }
}
