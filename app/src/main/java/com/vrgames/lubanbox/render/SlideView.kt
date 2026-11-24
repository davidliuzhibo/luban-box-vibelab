package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Axis
import com.vrgames.lubanbox.core.BurrExit
import com.vrgames.lubanbox.core.ExitPath
import com.vrgames.lubanbox.core.Move
import com.vrgames.lubanbox.core.RestPose
import com.vrgames.lubanbox.core.SlidePuzzle
import com.vrgames.lubanbox.core.StickPose
import com.vrgames.lubanbox.core.TurnAxis
import com.vrgames.lubanbox.core.Vec3
import com.vrgames.lubanbox.core.rotate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** How a piece of a [SlidePuzzle] looks and where it sits. */
class PieceLook(
    /** The piece's model, in the object frame with the piece at offset zero. */
    val visual: Entity,
    /** Collision boxes (size, center) in the same frame, for pinching. */
    val hitBoxes: List<Pair<Vector3, Vector3>>,
    /** Meters per step along each axis. */
    val step: Double,
)

/**
 * Shows a [SlidePuzzle] under [root] (the mechanism's object frame) and turns drags into moves.
 *
 * A drag first waits for a few millimetres of hand movement and locks onto the piece's axis that
 * movement follows best. From then on the piece follows the hand along that axis only, within
 * what the rules allow; pushing past a blocked end gives one bump, crossing each whole step a
 * tick. Letting go settles the piece on the nearest whole step and makes the move.
 *
 * Burr sticks that come out ([exits]) follow a planned way to the turntable that passes through
 * nothing (v0.10); nothing else can be moved until the stick has arrived.
 */
class SlideView(
    val puzzle: SlidePuzzle,
    val root: Entity,
    private val looks: List<PieceLook>,
    val exits: BurrExit? = null,
) {
    private val holders = looks.map { Entity() }
    /** Displayed offset of each piece along x, y, z (fractional while dragged or settling). */
    private val shown = Array(puzzle.pieceCount) { i -> DoubleArray(3) { a -> offsetOr0(i, Axis.entries[a]) } }
    /** The rules' offsets as last seen by [frame]: a change shows which way a piece moved. */
    private val known = Array(puzzle.pieceCount) { i -> IntArray(3) { a -> offsetOr0(i, Axis.entries[a]).toInt() } }
    private val removedShown = BooleanArray(puzzle.pieceCount)
    private val paths = arrayOfNulls<ExitPath>(puzzle.pieceCount)
    private val flyAge = DoubleArray(puzzle.pieceCount)
    /** Where each stick that is out lies (or is flying to). */
    val resting = HashMap<Int, RestPose>()

    /** Called once per stick that comes out, with its way and how close that way comes to anything. */
    var onExit: ((ExitPath, BurrExit.Clearance) -> Unit)? = null

    var dragging: Int? = null; private set
    private var axis: Axis? = null
    private var hand = Vec3.ZERO
    private var startOffset = 0
    private var range = 0..0
    /** How far the drag may show the piece (fractional steps): [range] plus the room its shape has short of the next step. */
    private var span = 0.0..0.0
    private var lastTick = 0
    private var bumped = false

    /** A stick is on its way out: other pieces wait. */
    val busy get() = paths.any { it != null }

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

    /** Top of the sticks still in the lock (burr), for the hint's arrow. */
    fun lockTop(): Double? = exits?.lockBounds()?.hi?.y

    fun begin(i: Int): Boolean {
        if (puzzle.removed(i) || busy) return false
        stopNudge()
        // v0.10.1: the others may still be settling from the last move (the rules already have them in place). Put
        // them there now, so what the hand moves against is where the rules say it is.
        settleAll(except = i)
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
            span = puzzle.span(i, best)
            lastTick = startOffset
            best
        }
        val want = startOffset + component(hand, a) / looks[i].step
        // Within the steps the rules allow, plus whatever room the shapes leave short of the next one (v0.10.1).
        val clamped = want.coerceIn(span.start, span.endInclusive)
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

    // ---- Hint: the piece that can move nudges its way and back (v0.10, instead of a ghost copy) ----

    private var nudgePiece = -1
    private var nudgeAxis = Axis.X
    private var nudgeBy = 0.0
    private var nudgeAge = 0.0
    private var nudgeCycles = 0
    private var nudgeTicked = false

    /**
     * Piece [i] slides a little along [axis] toward offset [to] and back, [cycles] times: one step
     * ([full] false) or the whole way to [to]. Display only (always within what the rules allow); a
     * soft tick each time it reaches the far end. Stops when the player takes any piece.
     */
    fun nudge(i: Int, axis: Axis, to: Int, full: Boolean, cycles: Int = 2) {
        if (puzzle.removed(i) || busy || dragging != null) return
        stopNudge()
        val o = puzzle.offset(i, axis)
        val by = if (full) (to - o).toDouble() else sign((to - o).toDouble())
        if (by == 0.0) return
        nudgePiece = i
        nudgeAxis = axis
        nudgeBy = by
        nudgeAge = 0.0
        nudgeCycles = cycles
        nudgeTicked = false
    }

    val nudging get() = nudgePiece >= 0

    fun stopNudge() {
        val i = nudgePiece
        nudgePiece = -1
        if (i >= 0 && !puzzle.removed(i)) {
            shown[i][nudgeAxis.ordinal] = puzzle.offset(i, nudgeAxis).toDouble()
            place(i)
        }
    }

    private fun nudgeFrame(dt: Double) {
        val i = nudgePiece
        if (i < 0) return
        nudgeAge += dt
        val cycle = NUDGE_S * (if (abs(nudgeBy) > 1.5) 1.6 else 1.0)
        val k = (nudgeAge / cycle).toInt()
        if (k >= nudgeCycles || puzzle.removed(i)) {
            stopNudge()
            return
        }
        val u = (nudgeAge - k * cycle) / cycle
        fun ease(x: Double) = x * x * (3 - 2 * x)
        val p = when {
            u < 0.35 -> ease(u / 0.35)
            u < 0.55 -> 1.0
            u < 0.9 -> 1 - ease((u - 0.55) / 0.35)
            else -> 0.0
        }
        if (u < 0.35) nudgeTicked = false
        if (u >= 0.35 && !nudgeTicked) {
            nudgeTicked = true
            Sound.shared?.play(Sfx.TICK, Vec3.ZERO)
        }
        shown[i][nudgeAxis.ordinal] = puzzle.offset(i, nudgeAxis) + nudgeBy * p
        place(i)
    }

    // ---- Frame -------------------------------------------------------------------------------

    /** Settles pieces on whole steps and flies pieces that came out to their rest spot. */
    fun frame(dt: Double) {
        val k = 1 - kotlin.math.exp(-dt * 18)
        for (i in 0 until puzzle.pieceCount) {
            if (i == dragging) continue
            if (puzzle.removed(i) && !removedShown[i]) {
                removedShown[i] = true
                if (i == nudgePiece) nudgePiece = -1
                startExit(i)
            }
            val path = paths[i]
            if (path != null) {
                fly(i, path, dt)
                continue
            }
            // Out of the mechanism and at rest: nothing more to settle.
            if (removedShown[i]) continue
            if (i == nudgePiece) continue
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
        nudgeFrame(dt)
        for (i in 0 until puzzle.pieceCount) for (a in puzzle.axes(i)) known[i][a.ordinal] = puzzle.offset(i, a)
    }

    /**
     * Stick [i] has just come out: which way it went (the axis it is now clear of the others on,
     * preferring the one its offset just changed along), and its planned way to the turntable.
     */
    private fun startExit(i: Int) {
        val ex = exits ?: return
        val off = IntArray(3) { a -> puzzle.offset(i, Axis.entries[a]) }
        val changed = (0..2).firstOrNull { off[it] != known[i][it] }
        val (axis, dir) = ex.exitWay(i, changed?.let { Axis.entries[it] }, changed?.let { if (off[it] > known[i][it]) 1 else -1 } ?: 0)
        val a = axis.ordinal
        // Start from where it is shown, if that is on its way from its last place to where it came free (all of
        // that is free of the others); else from where the rules have it.
        val from = DoubleArray(3) { off[it].toDouble() }
        val s = shown[i][a]
        val lo = minOf(off[a], known[i][a]) - 1e-6
        val hi = maxOf(off[a], known[i][a]) + 1e-6
        if (s in lo..hi && (0..2).all { it == a || abs(shown[i][it] - off[it]) < 1e-3 }) from[a] = s
        val path = ex.plan(i, axis, dir, Vec3(from[0], from[1], from[2]), resting)
        resting[i] = path.rest
        paths[i] = path
        flyAge[i] = 0.0
        onExit?.invoke(path, ex.clearance(path, resting))
        place(i, path.at(0.0))
    }

    private fun fly(i: Int, path: ExitPath, dt: Double) {
        val hold = debugHoldAt
        flyAge[i] = if (hold != null) minOf(flyAge[i] + dt, hold * path.duration) else flyAge[i] + dt
        val pose = path.at(flyAge[i])
        place(i, pose)
        if (hold == null && flyAge[i] >= path.duration) paths[i] = null
    }

    /** Puts stick [i] at [pose] (its middle and turn) in the object frame. */
    private fun place(i: Int, pose: StickPose) {
        val ex = exits ?: return
        val c = ex.bounds[i].center
        holders[i].setPos(pose.center - rotate(c, pose.turn, pose.deg))
        holders[i].transform().quaternion = quat(pose.turn, pose.deg)
    }

    /** Puts every piece back at its rules position at once (after a reset or a new round). */
    fun snapAll() {
        stopNudge()
        resting.clear()
        for (i in 0 until puzzle.pieceCount) {
            for (a in Axis.entries) shown[i][a.ordinal] = offsetOr0(i, a)
            for (a in Axis.entries) known[i][a.ordinal] = offsetOr0(i, a).toInt()
            removedShown[i] = puzzle.removed(i)
            paths[i] = null
            holders[i].transform().quaternion = Quat(0f, 0f, 0f, 1f)
            place(i)
        }
        // Sticks already out lie in their places, the first free ones in turn.
        val ex = exits ?: return
        for (i in 0 until puzzle.pieceCount) if (puzzle.removed(i)) {
            val r = ex.restFor(i, resting)
            resting[i] = r
            place(i, StickPose(r.center, r.turn, r.deg))
        }
    }

    private fun place(i: Int) = holders[i].setPos(offsetVector(i))

    /** Shows every piece but [except] exactly where the rules have it (ends any settling). */
    fun settleAll(except: Int = -1) {
        for (i in 0 until puzzle.pieceCount) {
            if (i == except || puzzle.removed(i) || paths[i] != null) continue
            var moved = false
            for (a in puzzle.axes(i)) {
                val t = puzzle.offset(i, a).toDouble()
                if (shown[i][a.ordinal] != t) {
                    shown[i][a.ordinal] = t
                    moved = true
                }
            }
            if (moved) place(i)
        }
    }

    /** Debug / tests: the offset piece [i] is shown at along [axis] (fractional while dragged or settling). */
    fun shownOffset(i: Int, axis: Axis) = shown[i][axis.ordinal]

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
        /** One nudge of the hint, out and back. */
        const val NUDGE_S = 0.9

        /** Debug only (AutoPilot): sticks on their way out stop at this fraction of their way (for a screenshot). */
        @Volatile var debugHoldAt: Double? = null

        /** A quarter turn (or less) about one axis, right-handed, as a quaternion. */
        fun quat(axis: TurnAxis, deg: Double): Quat {
            if (axis == TurnAxis.NONE || deg == 0.0) return Quat(0f, 0f, 0f, 1f)
            val h = Math.toRadians(deg) / 2
            val s = sin(h).toFloat()
            val c = cos(h).toFloat()
            return when (axis) {
                TurnAxis.X -> Quat(s, 0f, 0f, c)
                TurnAxis.Y -> Quat(0f, s, 0f, c)
                TurnAxis.Z -> Quat(0f, 0f, s, c)
                TurnAxis.NONE -> Quat(0f, 0f, 0f, 1f)
            }
        }
    }
}
