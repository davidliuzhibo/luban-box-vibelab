package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Axis
import com.vrgames.lubanbox.core.Move
import com.vrgames.lubanbox.core.Relics
import com.vrgames.lubanbox.core.Vec3
import com.vrgames.lubanbox.core.Workshop
import kotlin.math.cos
import kotlin.math.sin

/**
 * The workbench in front of the player: a bench top, a turntable on it, and the current relic on
 * the turntable. [root] sits at the middle of the bench top; [stage] is the relic's own frame
 * (turned with the turntable, raised as its [Scene] asks). The relic is shown by a view of its
 * mechanism: [slide] (sliding pieces), [rotary] (turning dials) or [build] (parts to put together).
 *
 * Gestures come in as hand movement in the room; [drag] turns it into the relic's frame.
 */
class Bench(private val assets: Assets) {
    val root = Entity()
    private val turntable = Entity()
    private val stage = Entity()
    private var content: Entity? = null
    private var scene: Scene? = null

    var slide: SlideView? = null; private set
    var rotary: RotaryView? = null; private set
    var build: AssemblyView? = null; private set
    private var relicId = ""

    /** Turntable angle (degrees about +y). */
    var yaw = 0.0; private set

    private var grab: Grab? = null
    private val diskHolder: Entity
    /** How long the relic on the bench has been open (negative while it is not). */
    private var solvedFor = -1.0

    sealed interface Grab {
        data class Piece(val i: Int) : Grab
        data class Dial(val i: Int) : Grab
        data class Part(val i: Int) : Grab
        data object Turntable : Grab
    }

    /** Gold dust when a relic opens. */
    private val sparks = Sparks("sparks", 160, assets.unitSphere, assets.sparkMaterial)

    /** The first-time coaching label (a Compose panel from the stage), floating over the bench. */
    private var coachLabel: Entity? = null

    init {
        // Bench top: 1.0 × 0.6 m, 4 cm thick, its top face at the root.
        root.addChild(assets.piece("bench") ?: assets.box(Vector3(1.0f, 0.04f, 0.6f), Vector3(0f, -0.02f, 0f), assets.woodLight))
        root.addChild(turntable)
        val disk = assets.piece("turntable") ?: model(MeshResource.createCylinder(TURNTABLE_H, TURNTABLE_R), assets.woodMid).apply {
            setPos(Vector3(0f, TURNTABLE_H / 2, 0f))
        }
        diskHolder = Entity().apply {
            addChild(disk)
            components.set(
                CollisionComponent(
                    listOf(ShapeResource.createBox(Vector3(TURNTABLE_R * 2, TURNTABLE_H, TURNTABLE_R * 2)).offsetByTranslation(Vector3(0f, TURNTABLE_H / 2, 0f))),
                    PhysicsMaterialResource(),
                ),
            )
            components.set(InteractableComponent())
            components.set(HoverEffectComponent())
        }
        turntable.addChild(diskHolder)
        turntable.addChild(stage)
        root.addChild(sparks.entity)
    }

    /** A burst of gold dust over the relic (it just opened). */
    fun celebrate() {
        sparks.emit(Vec3(0.0, TURNTABLE_H + (scene?.lift ?: 0f) + 0.1, 0.0), 120, 0.45, 1.8, 0.004f, Vec3(0.0, 0.12, 0.0))
    }

    /** Takes the coaching label panel (once) and floats it above the back of the bench. */
    fun attachCoach(e: Entity?) {
        if (e == null || coachLabel != null) return
        root.addChild(e)
        e.setPos(Vector3(0f, 0.45f, -0.12f))
        e.enabled = false
        coachLabel = e
    }

    val hasCoach get() = coachLabel != null

    private val panels = HashMap<String, Entity>()

    /** Takes a scene panel (once) and keeps it hidden at [pos] (bench frame) until [showPanel]. */
    fun attachPanel(id: String, e: Entity?, pos: Vector3) {
        if (e == null || id in panels) return
        root.addChild(e)
        e.setPos(pos)
        e.enabled = false
        panels[id] = e
    }

    fun hasPanel(id: String) = id in panels

    fun placePanel(id: String, pos: Vector3) {
        panels[id]?.setPos(pos)
    }

    fun showPanel(id: String, on: Boolean) {
        panels[id]?.let { if (it.enabled != on) it.enabled = on }
    }

    fun showCoach(on: Boolean) {
        coachLabel?.enabled = on
    }

    /** Where the player stands (floor x, z) and which way they face (degrees about y, 0 = -z). */
    private var standX = 0f
    private var standZ = 0f
    var facing = 0f
        private set

    /** Turns the bench to whoever stands at ([x], [z]) looking [yawDegrees]; [placeInRoom] puts it there. */
    fun faceFrom(x: Float, z: Float, yawDegrees: Float) {
        standX = x
        standZ = z
        facing = yawDegrees
    }

    /** Puts the bench in the room: its top [height] above the floor, [distance] straight ahead of the player. */
    fun placeInRoom(height: Float, distance: Float = BENCH_Z) {
        val a = Math.toRadians(facing.toDouble())
        root.setPos(Vector3(standX - distance * sin(a).toFloat(), height, standZ - distance * cos(a).toFloat()))
        root.transform().eulerAngles = EulerAngles(0f, facing, 0f)
    }

    /** Builds the relic now on the bench (call after the workshop loads a relic or a round). */
    fun show(ws: Workshop) {
        showHint(null)
        scene?.dispose()
        content?.let { stage.removeChild(it); it.destroy(true) }
        val c = Entity()
        stage.addChild(c)
        content = c
        slide = null
        rotary = null
        build = null
        relicId = ws.relic.id
        solvedFor = if (ws.solved) 0.0 else -1.0
        val sc = Scene.of(relicId, assets)
        scene = sc
        stage.setPos(Vector3(0f, TURNTABLE_H + sc.lift, 0f))
        sc.props(c)
        ws.slide?.let { p -> slide = SlideView(p, c, sc.slideLooks(p)).also { it.snapAll() } }
        ws.rotary?.let { p -> rotary = RotaryView(p, c, sc.dialLooks(p)).also { it.snapAll() } }
        ws.assembly?.let { a ->
            val floor = -(TURNTABLE_H + sc.lift).toDouble()
            build = AssemblyView(a, c, assets, sc::partVisual, { t -> hitBox(sc.partVisual(t)) }, sc.tray(a), floor).also { it.snapAll() }
        }
        if (!sc.turns(false)) turnTo(0.0)
        showTokens(ws)
    }

    private fun hitBox(e: Entity): Pair<Vector3, Vector3> = Scene.bounds(e).let { (size, center) ->
        Vector3(maxOf(size.x, 0.02f), maxOf(size.y, 0.02f), maxOf(size.z, 0.02f)) to center
    }

    // ---- Tokens ----------------------------------------------------------------------------------

    /** The tokens earned so far lie at the left of the bench, each chapter's set together. */
    private val tokens = Entity().also { root.addChild(it) }

    fun showTokens(ws: Workshop) {
        tokens.getChildren().toList().forEach { tokens.removeChild(it); it.destroy(true) }
        Relics.all.forEachIndexed { k, r ->
            if (r.chapter > 3 || !ws.progress.solved(r.id)) return@forEachIndexed
            val t = assets.piece("token${k + 1}") ?: return@forEachIndexed
            val at = when (r.chapter) {
                // Ruler sections side by side at the front; seal quarters and star sectors are
                // modeled in place around their set's middle, so they meet as they are earned.
                1 -> Vector3(-0.38f + 0.122f * (k % 2), 0f, 0.17f + 0.04f * (k / 2))
                2 -> Vector3(-0.4f, 0f, 0.02f)
                else -> Vector3(-0.4f, 0f, -0.15f)
            }
            t.setPos(at)
            tokens.addChild(t)
        }
    }

    // ---- Gestures --------------------------------------------------------------------------------

    fun grabOf(e: Entity?): Grab? {
        slide?.pieceOf(e)?.let { return Grab.Piece(it) }
        rotary?.dialOf(e)?.let { return Grab.Dial(it) }
        build?.partOf(e)?.let { return Grab.Part(it) }
        if (scene?.turns(solvedFor >= 0) != true) return null
        var cur = e
        while (cur != null) {
            if (cur === diskHolder) return Grab.Turntable
            cur = cur.getParent()
        }
        return null
    }

    fun begin(g: Grab): Boolean {
        val ok = when (g) {
            is Grab.Piece -> slide?.begin(g.i) ?: false
            is Grab.Dial -> rotary?.begin(g.i) ?: false
            is Grab.Part -> build?.begin(g.i) ?: false
            Grab.Turntable -> true
        }
        grab = if (ok) g else null
        return ok
    }

    /** What a drag step did, for sound and for counting moves. */
    sealed interface Felt {
        data object Tick : Felt
        data object Bump : Felt
        /** A dial turned a stop: that is a move. */
        data object Turned : Felt
    }

    /** [room]: the hand's movement in the room (meters). */
    fun drag(room: Vec3): Felt? {
        val d = turnY(room, -facing.toDouble())
        when (grab) {
            is Grab.Piece -> return when (slide?.drag(toStage(d))) {
                SlideView.Feel.TICK -> Felt.Tick
                SlideView.Feel.BUMP -> Felt.Bump
                null -> null
            }
            is Grab.Dial -> return when (rotary?.drag(toStage(d))) {
                is RotaryView.Turn.Turned -> Felt.Turned
                RotaryView.Turn.Held -> Felt.Bump
                null -> null
            }
            is Grab.Part -> build?.drag(toStage(d))
            // Sideways hand movement turns the table; about a quarter turn per 20 cm.
            Grab.Turntable -> turnTo(yaw + d.x * 450)
            null -> Unit
        }
        return null
    }

    sealed interface Released {
        data class Moved(val move: Move) : Released
        data class Dropped(val drop: AssemblyView.Drop) : Released
        data object Nothing : Released
    }

    fun end(): Released {
        val g = grab ?: return Released.Nothing
        grab = null
        return when (g) {
            is Grab.Piece -> slide?.end()?.let { Released.Moved(it) } ?: Released.Nothing
            is Grab.Dial -> {
                rotary?.end()
                Released.Nothing
            }
            is Grab.Part -> build?.end()?.let { Released.Dropped(it) } ?: Released.Nothing
            Grab.Turntable -> Released.Nothing
        }
    }

    val dragging get() = grab != null

    fun turnBy(deg: Double) = turnTo(yaw + deg)

    private fun turnTo(deg: Double) {
        yaw = deg
        turntable.transform().eulerAngles = EulerAngles(0f, yaw.toFloat(), 0f)
    }

    /**
     * AutoPilot: makes the solver's next move through the rules (the views animate it). Returns the
     * piece moved, -1 for a dial or a part, or null when there is nothing left to do.
     */
    fun autoStep(): Int? {
        slide?.let { v ->
            val m = v.puzzle.solution()?.firstOrNull() ?: return null
            v.puzzle.move(m.piece, m.axis, m.to)
            return m.piece
        }
        rotary?.let { v ->
            val (dial, dir) = v.puzzle.solution()?.firstOrNull() ?: return null
            v.puzzle.turn(dial, dir)
            return -1
        }
        build?.let { v ->
            val (part, slot) = v.assembly.hint() ?: return null
            v.placeAuto(part, slot)
            return -1
        }
        return null
    }

    fun frame(dt: Double, ws: Workshop) {
        sparks.update(dt)
        slide?.frame(dt)
        rotary?.frame(dt)
        build?.frame(dt)
        if (ws.relic.id == relicId) {
            solvedFor = if (!ws.solved) -1.0 else if (solvedFor < 0) 0.0 else solvedFor + dt
            scene?.frame(dt, ws, this, solvedFor)
            // The opened relic shows itself off: one slow turn of the turntable, with a second burst of dust.
            if (solvedFor in SPIN_AFTER..SPIN_AFTER + SPIN_S && grab == null) turnTo(yaw + 360.0 / SPIN_S * dt)
            if (solvedFor >= SPIN_AFTER + SPIN_S / 2 && solvedFor - dt < SPIN_AFTER + SPIN_S / 2) celebrate()
        }
        updateHint(dt)
    }

    /** Hand movement in the room → the relic's frame (undoing the turntable's turn). */
    private fun toStage(d: Vec3): Vec3 = turnY(d, -yaw)

    private fun turnY(d: Vec3, degrees: Double): Vec3 {
        val a = Math.toRadians(degrees)
        return Vec3(d.x * cos(a) + d.z * sin(a), d.y, -d.x * sin(a) + d.z * cos(a))
    }

    // ---- Hints -----------------------------------------------------------------------------------

    private val marker = Entity().apply {
        addChild(model(assets.unitSphere, assets.sparkMaterial).apply { setScale(0.012f) })
        addChild(model(MeshResource.createCone(0.045f, 0.018f), assets.sparkMaterial).apply { setPos(Vector3(0f, 0.04f, 0f)) })
        enabled = false
    }
    private var ghost: Entity? = null
    private var hinted: Workshop.Hint? = null
    private var hintAge = 0.0

    /**
     * Shows [h] on the bench: a glowing arrow at the piece or dial to move, pointing the way; with
     * the target, a ghost of the piece where it goes (or the outline of the slot for a part).
     */
    fun showHint(h: Workshop.Hint?) {
        hinted = h
        hintAge = 0.0
        ghost?.let { it.getParent()?.removeChild(it); it.destroy(true) }
        ghost = null
        build?.showSlot(null)
        if (marker.getParent() == null) stage.addChild(marker)
        marker.enabled = h != null
        if (h == null) return
        val m = h.move
        val s = slide
        val r = rotary
        if (m != null && s != null) {
            val dir = unit(m.axis) * (if (m.to > s.puzzle.offset(m.piece, m.axis)) 1.0 else -1.0)
            marker.transform().quaternion = quatFromY(dir)
            if (h.showTarget) {
                s.visualCopy(m.piece)?.let { g ->
                    Assets.setMaterials(g, assets.ghost)
                    val holder = Entity().apply { addChild(g) }
                    holder.setPos(s.offsetVectorAt(m.piece, m.axis, m.to))
                    content?.addChild(holder)
                    ghost = holder
                }
            }
        }
        if (m != null && r != null) {
            // Turning a dial: the arrow points the way to move the hand.
            val along = if (r.dragAlong(m.piece) == Axis.X) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
            marker.transform().quaternion = quatFromY(along * m.to.toDouble())
        }
        if (h.part != null && build != null) {
            marker.transform().quaternion = quatFromY(Vec3(0.0, -1.0, 0.0))
            if (h.showTarget) build?.showSlot(h.slot)
        }
    }

    private fun unit(a: Axis) = when (a) {
        Axis.X -> Vec3(1.0, 0.0, 0.0)
        Axis.Y -> Vec3(0.0, 1.0, 0.0)
        Axis.Z -> Vec3(0.0, 0.0, 1.0)
    }

    private fun updateHint(dt: Double) {
        val h = hinted ?: return
        hintAge += dt
        val bob = 0.006 * sin(hintAge * 6)
        val m = h.move
        val s = slide
        val r = rotary
        val at = when {
            // Above the piece (above the whole lock for a burr stick, whose middle is buried).
            m != null && s != null -> s.center(m.piece).let { if (relicId == "lock") Vec3(it.x, 0.17, it.z) else it + Vec3(0.0, 0.07, 0.0) }
            // In front of the dial.
            m != null && r != null -> r.pivot(m.piece) + Vec3(0.0, 0.0, 0.09)
            h.part != null -> build?.position(h.part)?.let { it + Vec3(0.0, 0.08, 0.0) }
            else -> null
        } ?: return
        marker.setPos(at + Vec3(0.0, bob, 0.0))
    }

    companion object {
        /** The turn that shows an opened relic off: starts this long after it opens, lasts [SPIN_S]. */
        const val SPIN_AFTER = 0.8
        const val SPIN_S = 5.0
        const val TURNTABLE_R = 0.2f
        const val TURNTABLE_H = 0.03f
        /** Bench top middle, this far in front of where the player stands or sits. */
        const val BENCH_Z = 0.75f
    }
}
