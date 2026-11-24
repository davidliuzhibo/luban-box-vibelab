package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Assembly
import com.vrgames.lubanbox.core.Axis
import com.vrgames.lubanbox.core.Boxes
import com.vrgames.lubanbox.core.Bronze
import com.vrgames.lubanbox.core.Burr
import com.vrgames.lubanbox.core.BurrExit
import com.vrgames.lubanbox.core.Burrs
import com.vrgames.lubanbox.core.Celestial
import com.vrgames.lubanbox.core.Cell
import com.vrgames.lubanbox.core.Dougong
import com.vrgames.lubanbox.core.Klotski
import com.vrgames.lubanbox.core.KlotskiBoards
import com.vrgames.lubanbox.core.Aabb
import com.vrgames.lubanbox.core.RotaryPuzzle
import com.vrgames.lubanbox.core.TurnAxis
import com.vrgames.lubanbox.core.Vec3
import com.vrgames.lubanbox.core.Workshop
import kotlin.math.sin

/**
 * How each relic is put on the bench: the looks of its pieces, dials or parts (from the models in
 * [Assets], with box stand-ins), its fixed props, and anything it does besides (a light beam, a
 * demonstration once it works). One object per relic; [Bench] calls into it.
 */
abstract class Scene(protected val assets: Assets) {
    /** Height of the relic's frame above the turntable top. */
    open val lift = 0f
    /** Whether the turntable turns (before / after the relic is opened). */
    open fun turns(solved: Boolean) = false
    /** Fixed props, added to the relic's frame before the mechanism. */
    open fun props(frame: Entity) {}
    open fun slideLooks(p: com.vrgames.lubanbox.core.SlidePuzzle): List<PieceLook> = emptyList()
    /** How pieces that come out of the mechanism get to where they lie (the burr's sticks). */
    open fun exits(p: com.vrgames.lubanbox.core.SlidePuzzle): BurrExit? = null
    /** v0.10.1: the scene is moving parts itself (nothing else may move until it is done). */
    open val busy: Boolean get() = false
    /** v0.10.1: z of the open front of a build inside a roofed frame (parts slide in from there); null: from above. */
    open val frontOpening: Double? = null
    /** Sideways offset (x) of the lane a part slides in along, when it must pass something on its way in. */
    open fun frontShift(slot: com.vrgames.lubanbox.core.Slot) = 0.0
    /** Whether the chapter tokens lie on the bench while this relic is out (the 斗拱 needs the room for its parts). */
    open val showsTokens = true
    open fun dialLooks(p: RotaryPuzzle): List<DialLook> = emptyList()
    open fun partVisual(type: String): Entity = assets.box(Vector3(0.04f), Vector3(0f, 0.02f, 0f), assets.woodMid)
    /** Loose parts on the bench: place (object frame, before the floor height) and yaw. */
    open fun tray(a: Assembly): List<Pair<Vec3, Double>> = genericTray(a)
    /** Per frame; [solvedFor] is how long the relic has been open (or < 0). */
    open fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {}
    open fun dispose() {}

    /** Parts in two columns either side of the turntable, longest first; long x-wise parts lie along z. */
    protected fun genericTray(a: Assembly): List<Pair<Vec3, Double>> {
        val sizes = a.parts.map { t -> bounds(partVisual(t)).first }
        val order = a.parts.indices.sortedByDescending { maxOf(sizes[it].x, sizes[it].z) }
        val out = arrayOfNulls<Pair<Vec3, Double>>(a.parts.size)
        val z = doubleArrayOf(-0.2, -0.2)
        for ((k, i) in order.withIndex()) {
            val side = k % 2
            val s = sizes[i]
            val turn = s.x > 0.12 && s.x > s.z
            val depth = (if (turn) s.x else s.z).toDouble()
            val x = (if (side == 0) -1 else 1) * (0.34 + if (turn) 0.0 else 0.02)
            out[i] = Vec3(x, 0.0, z[side] + depth / 2) to (if (turn) 90.0 else 0.0)
            z[side] += depth + 0.035
        }
        return out.map { it!! }
    }

    companion object {
        /** Size and center of a model's visual bounds. */
        fun bounds(e: Entity): Pair<Vector3, Vector3> {
            val b = e.getVisualBounds(null, recursive = true, enabledOnly = false)
            return b.size to b.center
        }

        fun of(id: String, assets: Assets): Scene = when (id) {
            "lock" -> LockScene(assets)
            "klotski" -> KlotskiScene(assets)
            "box" -> BoxScene(assets)
            "dougong" -> DougongScene(assets)
            "wordlock" -> WordLockScene(assets)
            "padlock" -> PadlockScene(assets)
            "censer" -> CenserScene(assets)
            "mirror" -> MirrorScene(assets)
            "chariot" -> ChariotScene(assets)
            "armillary" -> ArmillaryScene(assets)
            "waterclock" -> WaterClockScene(assets)
            else -> ChestScene(assets)
        }
    }
}

private fun cellCenter(c: Cell, size: Double) =
    Vector3(((c.x + 0.5) * size).toFloat(), ((c.y + 0.5) * size).toFloat(), ((c.z + 0.5) * size).toFloat())

/** A thin glowing rod between two points (light beams). */
class Beam(assets: Assets, private val parent: Entity, material: com.pico.spatial.core.ecs.resource.Material, radius: Float) {
    private val rod = model(MeshResource.createCylinder(1f, radius), material).also { parent.addChild(it) }

    fun show(a: Vec3?, b: Vec3?) {
        if (a == null || b == null) {
            rod.enabled = false
            return
        }
        rod.enabled = true
        val d = b - a
        rod.setPos(a + d * 0.5)
        rod.transform().quaternion = quatFromY(d)
        rod.transform().setScaleVector(Vector3(1f, d.length().toFloat(), 1f))
    }
}

// ---- Chapter one ---------------------------------------------------------------------------------

class LockScene(assets: Assets) : Scene(assets) {
    /**
     * The lock floats this high over the turntable. v0.10 (PICO 2026-11-23): 0.2 m let a stick coming out
     * downward reach through the turntable and the sticks lying on it. A stick can reach 14 cells (35 cm)
     * below the lock's middle; with 0.45 m it stays 10 cm above the turntable and 4.8 cm above the
     * sticks lying there (BurrExitTest checks every way out).
     */
    override val lift = Burrs.LIFT.toFloat()
    override fun turns(solved: Boolean) = true

    override fun exits(p: com.vrgames.lubanbox.core.SlidePuzzle): BurrExit? = (p as? Burr)?.let { BurrExit(it, tableY = -lift.toDouble()) }

    override fun slideLooks(p: com.vrgames.lubanbox.core.SlidePuzzle): List<PieceLook> {
        val b = p as Burr
        val cell = Burrs.CELL
        return b.pieces.mapIndexed { i, piece ->
            val visual = assets.piece("stick$i") ?: Entity().apply {
                val m = when (piece.axis) {
                    Axis.X -> assets.woodMid
                    Axis.Y -> assets.woodLight
                    Axis.Z -> assets.woodDark
                }
                for (c in piece.cells) addChild(assets.box(Vector3(cell.toFloat() * 0.98f), cellCenter(c, cell), m))
            }
            val hit = piece.cells.map { c -> Vector3(cell.toFloat()) to cellCenter(c, cell) }
            // Out of the lock, the sticks are carried round it to the turntable (BurrExit).
            PieceLook(visual, hit, cell)
        }
    }
}

class KlotskiScene(assets: Assets) : Scene(assets) {
    override fun props(frame: Entity) {
        val cell = KlotskiBoards.CELL
        frame.addChild(
            assets.piece("board") ?: assets.box(
                Vector3((Klotski.W * cell + 0.03).toFloat(), 0.02f, (Klotski.H * cell + 0.03).toFloat()),
                Vector3(0f, -0.01f, 0f),
                assets.woodMid,
            ),
        )
    }

    override fun slideLooks(p: com.vrgames.lubanbox.core.SlidePuzzle): List<PieceLook> {
        val k = p as Klotski
        val cell = KlotskiBoards.CELL
        return (0 until k.pieceCount).map { i ->
            val b = k.block(i)
            val pos = k.position(i)
            // Center of the block on the board (board y → +z, toward the player).
            val center = Vector3(
                ((pos.x + b.w / 2.0 - Klotski.W / 2.0) * cell).toFloat(),
                0f,
                ((pos.y + b.h / 2.0 - Klotski.H / 2.0) * cell).toFloat(),
            )
            val name = when (b.w to b.h) {
                2 to 2 -> "cao"
                2 to 1 -> "guan"
                1 to 2 -> "general_${b.name}"
                else -> "bing"
            }
            val visual = (assets.piece(name) ?: assets.box(
                Vector3((b.w * cell * 0.94).toFloat(), 0.03f, (b.h * cell * 0.94).toFloat()),
                Vector3(0f, 0.015f, 0f),
                if (i == 0) assets.woodLight else assets.woodDark,
            )).apply { setPos(center) }
            val hit = listOf(Vector3((b.w * cell * 0.94).toFloat(), 0.035f, (b.h * cell * 0.94).toFloat()) to Vector3(center.x, 0.0175f, center.z))
            PieceLook(visual, hit, cell)
        }
    }
}

/**
 * A box-like mechanism of sliding panels: [prefix]body and [prefix]0..n, with stand-in boxes. Each panel is pinched
 * by the same boxes the rules collide (v0.10.1: [Aabb] lists, see [com.vrgames.lubanbox.core.Solid]).
 */
open class PanelScene(
    assets: Assets,
    private val bodyNode: String,
    private val panelNode: (Int) -> String,
    private val body: List<Aabb>,
    private val parts: List<List<Aabb>>,
    private val step: (Int) -> Double,
) : Scene(assets) {
    override fun turns(solved: Boolean) = true

    override fun props(frame: Entity) {
        frame.addChild(assets.piece(bodyNode) ?: Entity().apply { for (b in body) addChild(assets.box(b.size(), b.mid(), assets.woodDark)) })
    }

    override fun slideLooks(p: com.vrgames.lubanbox.core.SlidePuzzle): List<PieceLook> = (0 until p.pieceCount).map { i ->
        val visual = assets.piece(panelNode(i)) ?: Entity().apply {
            for (b in parts[i]) addChild(assets.box(b.size(), b.mid(), if (i % 2 == 0) assets.woodMid else assets.woodLight))
        }
        PieceLook(visual, parts[i].map { it.size() to it.mid() }, step(i))
    }
}

private fun Aabb.size() = Vector3((hi.x - lo.x).toFloat(), (hi.y - lo.y).toFloat(), (hi.z - lo.z).toFloat())
private fun Aabb.mid() = center.toVector3()

/**
 * The 鲁班盒 stands on its plinth (part of box_body) on the turntable. v0.10.1: its frame is raised by the plinth so
 * the left end can drop 2 cm beside it (it used to sink into the turntable).
 */
class BoxScene(assets: Assets) : PanelScene(assets, "box_body", { "panel$it" }, listOf(Boxes.body, Boxes.plinth), Boxes.parts, Boxes::stepMeters) {
    override val lift = Boxes.LIFT.toFloat()
}

class DougongScene(assets: Assets) : Scene(assets) {
    // The bracket set stands on a 12 cm column stub.
    override val lift = 0.12f
    private var overhead: Entity? = null

    override fun props(frame: Entity) {
        assets.piece("dg_column")?.let { frame.addChild(it) }
    }

    override fun partVisual(type: String): Entity = assets.piece("dg_$type") ?: run {
        val h = Dougong.ARM_H.toFloat()
        val size = when (type) {
            Dougong.LU_DOU -> Vector3(0.096f, Dougong.LU_DOU_H.toFloat(), 0.096f)
            Dougong.HUA_GONG -> Vector3(0.03f, h, (Dougong.HUA_GONG_FRONT - Dougong.HUA_GONG_BACK).toFloat())
            Dougong.NI_DAO_GONG -> Vector3(0.22f, h, 0.03f)
            Dougong.LING_GONG -> Vector3(0.2f, h, 0.03f)
            Dougong.SHUA_TOU -> Vector3(0.03f, h, 0.2f)
            Dougong.ZHU_TOU_FANG, Dougong.LIAO_YAN_FANG -> Vector3(0.24f, h, 0.03f)
            Dougong.JIAO_HU_DOU -> Vector3(0.054f, Dougong.DOU_H.toFloat(), 0.054f)
            else -> Vector3(0.048f, Dougong.DOU_H.toFloat(), 0.048f)
        }
        assets.box(size, Vector3(0f, size.y / 2, 0f), if (type.endsWith("斗")) assets.woodDark else assets.woodLight)
    }

    /** In Dougong.parts order (v0.10: the long arms lie lengthwise and must be turned; see [Dougong.tray]). */
    override fun tray(a: Assembly) = Dougong.tray

    /** The tokens' corner of the bench holds 斗拱 parts; the tokens come back with the next relic. */
    override val showsTokens = false

    /**
     * Once built, the bracket set appears again at full size (five times) above the player's head,
     * the way it would sit under real eaves.
     */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        if (solvedFor < 0) return
        val big = overhead ?: Entity().also { g ->
            for (slot in Dougong.slots) {
                val part = assets.piece("dg_${slot.type}") ?: continue
                part.setPos(slot.pos)
                part.transform().eulerAngles = EulerAngles(0f, slot.yaw.toFloat(), 0f)
                g.addChild(part)
            }
            bench.root.addChild(g)
            overhead = g
        }
        val grow = (solvedFor / 2.0).coerceIn(0.0, 1.0)
        val e = grow * grow * (3 - 2 * grow)
        big.setScale((1 + 4 * e).toFloat())
        big.setPos(Vec3(0.0, 0.2 + 1.25 * e, (Bench.BENCH_Z - 0.25) * e))
    }

    override fun dispose() {
        overhead?.let { it.getParent()?.removeChild(it); it.destroy(true) }
        overhead = null
    }
}

// ---- Chapter two ---------------------------------------------------------------------------------

class WordLockScene(assets: Assets) : Scene(assets) {
    override fun props(frame: Entity) {
        frame.addChild(assets.piece("wl_body") ?: assets.box(Vector3(0.18f, 0.02f, 0.08f), Vector3(0f, 0.01f, 0f), assets.brass))
    }

    override fun dialLooks(p: RotaryPuzzle): List<DialLook> = p.dials.mapIndexed { i, d ->
        val visual = assets.piece("wl_ring$i") ?: assets.box(Vector3(0.028f, 0.052f, 0.052f), Vector3(0f), assets.brass)
        DialLook(visual, d.center, listOf(Vector3(0.03f, 0.06f, 0.06f) to Vector3(0f)), dragAlong = Axis.Y)
    }
}

class PadlockScene(assets: Assets) : PanelScene(assets, "pl_body", { "pl_$it" }, Bronze.padlockBodyBoxes, Bronze.padlockParts, Bronze::padlockStep)

class CenserScene(assets: Assets) : Scene(assets) {
    override val lift = 0.03f
    override fun turns(solved: Boolean) = solved

    override fun props(frame: Entity) {
        assets.piece("cs_stand")?.let { frame.addChild(it) }
    }

    override fun partVisual(type: String): Entity = assets.piece("cs_$type")
        ?: model(assets.unitSphere, assets.brass).apply { setScale(if (type == Bronze.BOWL) 0.03f else 0.06f) }

    // ---- Fitting the gimbal (v0.10.1) -------------------------------------------------------------------------
    //
    // At rest the outer ring, the inner ring and the bowl stand in one upright plane (x-y), nested, each held by the
    // pins of the next. A rigid ring cannot go into a closed ring, so, as a gimbal is put together:
    // - the inner ring: it rises over the shell and comes over the middle; the outer ring turns on its pins (about x)
    //   until it lies flat; the inner ring goes down through it edge-on; the outer ring turns back up, and its sockets
    //   close over the inner ring's pins.
    // - the bowl: it rises, comes over the middle and turns on its side (pins upright); both rings turn flat together;
    //   it goes down through them; turns its pins into the inner ring's sockets; the rings turn back up around it.
    // Nothing else may be moved meanwhile ([busy]). tools/audit_meshes.py checks every pose of this on the meshes.

    private var job: Fit? = null
    private val started = HashSet<Int>()
    private var looked = false

    private class Fit(val part: Int, val bowl: Boolean, val from: Vec3, val outer: Int, val inner: Int) {
        var age = 0.0
    }

    override val busy get() = job != null

    private fun fitStep(v: AssemblyView, f: Fit) {
        val c = Vec3(0.0, Bronze.CENSER_R, 0.0)
        val high = Vec3(0.0, FIT_HIGH, 0.0)
        val t = f.age
        fun ease(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
        // Phase boundaries (seconds).
        val rise = 0.5
        val over = rise + 0.6
        val flip = over + 0.7
        val down = flip + 0.7
        val turn = if (f.bowl) down + 0.5 else down
        val back = turn + 0.7
        val partTurn = if (f.bowl) SlideView.quat(TurnAxis.Z, 90.0) else SlideView.quat(TurnAxis.Y, 90.0)
        val up = Vec3(f.from.x, maxOf(f.from.y, FIT_HIGH), f.from.z)
        // The part.
        val p = when {
            t < rise -> f.from + (up - f.from) * ease(t / rise)
            t < over -> up + (high - up) * ease((t - rise) / (over - rise))
            t < flip -> high
            t < down -> high + (c - high) * ease((t - flip) / (down - flip))
            else -> c
        }
        val q = when {
            !f.bowl -> partTurn
            t < rise -> Quat(0f, 0f, 0f, 1f)
            t < over -> SlideView.quat(TurnAxis.Z, 90.0 * ease((t - rise) / (over - rise)))
            t < down -> partTurn
            t < turn -> SlideView.quat(TurnAxis.Z, 90.0 * (1 - ease((t - down) / (turn - down))))
            else -> Quat(0f, 0f, 0f, 1f)
        }
        v.pose(f.part, p, q)
        // The rings: flat while the part goes in, then back up.
        val tilt = 90.0 * when {
            t < over -> 0.0
            t < flip -> ease((t - over) / (flip - over))
            t < turn -> 1.0
            else -> 1 - ease((t - turn) / (back - turn))
        }
        v.pose(f.outer, c, SlideView.quat(TurnAxis.X, tilt))
        if (f.bowl) v.pose(f.inner, c, qMul(SlideView.quat(TurnAxis.X, tilt), SlideView.quat(TurnAxis.Y, 90.0)))
        if (t >= back) {
            v.giveBack(f.part)
            v.giveBack(f.outer)
            if (f.bowl) v.giveBack(f.inner)
            job = null
        }
    }

    /** Starts fitting a ring or the bowl that has just been let go over its slot; runs the fitting under way. */
    private fun fitting(dt: Double, v: AssemblyView) {
        job?.let { f ->
            f.age += dt
            debugHoldAt?.let { f.age = minOf(f.age, it) }
            fitStep(v, f)
            return
        }
        val parts = v.assembly.parts
        // Parts already in when the relic was put on the bench are not fitted again.
        if (!looked) {
            looked = true
            for (i in parts.indices) if (v.assembly.isPlaced(i)) started += i
        }
        for (i in parts.indices) {
            if (!v.assembly.isPlaced(i) || i in started) continue
            started += i
            val type = parts[i]
            if (type != Bronze.RING_IN && type != Bronze.BOWL) continue
            val outer = parts.indices.first { parts[it] == Bronze.RING_OUT }
            val inner = parts.indices.first { parts[it] == Bronze.RING_IN }
            val from = v.takeOver(i)
            v.takeOver(outer)
            if (type == Bronze.BOWL) v.takeOver(inner)
            job = Fit(i, type == Bronze.BOWL, from, outer, inner)
            android.util.Log.i("LubanGeo", "censer fit $type: rings turn flat, it goes in edge-on, rings turn back")
            return
        }
    }

    /**
     * Once put together the censer rolls about in its cradle, and the bowl inside stays level. v0.10.1: as a real
     * gimbal, every pin staying in its socket: the inner ring turns on the bowl's pins (x), the outer ring on the inner
     * ring's pins (y), the shell on the outer ring's pins (x), all about the ball's middle. (0.10.0 turned the outer
     * ring about z, so its pins left their sockets, and turned the lower half about its foot, away from the upper.)
     */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        val v = bench.build ?: return
        if (solvedFor < 0 || job != null) {
            fitting(dt, v)
            return
        }
        val ramp = (solvedFor / 2).coerceAtMost(1.0)
        val t1 = 35 * sin(solvedFor * 1.3) * ramp
        val t2 = 30 * sin(solvedFor * 0.9 + 1) * ramp
        val t3 = 25 * sin(solvedFor * 0.7 + 2) * ramp
        val inner = qMul(SlideView.quat(TurnAxis.X, t1), SlideView.quat(TurnAxis.Y, 90.0))
        val outer = qMul(SlideView.quat(TurnAxis.X, t1), SlideView.quat(TurnAxis.Y, t2))
        val shell = qMul(outer, SlideView.quat(TurnAxis.X, t3))
        val c = Vec3(0.0, Bronze.CENSER_R, 0.0)
        for ((i, type) in v.assembly.parts.withIndex()) {
            val q = when (type) {
                Bronze.HEMI_LOW, Bronze.HEMI_HIGH -> shell
                Bronze.RING_OUT -> outer
                Bronze.RING_IN -> inner
                else -> Quat(0f, 0f, 0f, 1f)
            }
            v.holder(i).transform().quaternion = q
            // The lower half's origin is its bottom: turn it about the ball's middle like the rest.
            if (type == Bronze.HEMI_LOW) v.holder(i).setPos(c + qRotate(q, Vec3(0.0, -Bronze.CENSER_R, 0.0)))
        }
    }

    companion object {
        /** Height of the part's middle while it waits over the ball for the rings to turn flat (clear of the upright outer ring). */
        const val FIT_HIGH = Bronze.CENSER_R + 0.13

        /** Debug only (AutoPilot): a fitting stops this many seconds in (for a screenshot). */
        @Volatile var debugHoldAt: Double? = null

        /** Hamilton product a·b (b first, then a). */
        fun qMul(a: Quat, b: Quat) = Quat(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z,
        )

        fun qRotate(q: Quat, v: Vec3): Vec3 {
            val p = qMul(qMul(q, Quat(v.x.toFloat(), v.y.toFloat(), v.z.toFloat(), 0f)), Quat(-q.x, -q.y, -q.z, q.w))
            return Vec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
        }
    }
}

class MirrorScene(assets: Assets) : Scene(assets) {
    private var beamIn: Beam? = null
    private var beamOut: Beam? = null
    private var spot: Entity? = null
    private var words: Entity? = null

    override fun props(frame: Entity) {
        assets.piece("mr_base")?.let { frame.addChild(it) }
        frame.addChild(assets.piece("mr_lamp") ?: model(assets.unitSphere, assets.sparkMaterial).apply { setScale(0.015f) }.also { it.setPos(Bronze.LAMP) })
        frame.addChild(assets.piece("mr_screen") ?: assets.box(Vector3(0.15f, 0.1f, 0.004f), Bronze.SCREEN.toVector3(), assets.woodLight))
        words = assets.piece("mr_words")?.also { it.enabled = false; frame.addChild(it) }
        beamIn = Beam(assets, frame, assets.beamMaterial, 0.003f)
        beamOut = Beam(assets, frame, assets.beamMaterial, 0.003f)
        spot = model(assets.unitSphere, assets.sparkMaterial).apply { setScale(0.012f) }.also { frame.addChild(it) }
    }

    override fun dialLooks(p: RotaryPuzzle): List<DialLook> {
        val yoke = assets.piece("mr_yoke") ?: assets.box(Vector3(0.12f, 0.02f, 0.02f), Vector3(0f, -0.06f, 0f), assets.brass)
        val face = assets.piece("mr_face") ?: model(MeshResource.createCylinder(0.006f, 0.05f), assets.brass).apply {
            transform().eulerAngles = EulerAngles(90f, 0f, 0f)
        }
        return listOf(
            DialLook(yoke, Bronze.MIRROR, listOf(Vector3(0.14f, 0.04f, 0.04f) to Vector3(0f, -0.06f, 0f)), dragAlong = Axis.X),
            DialLook(face, Vec3.ZERO, listOf(Vector3(0.11f, 0.11f, 0.02f) to Vector3(0f)), parent = 0, dragAlong = Axis.Y),
        )
    }

    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        val r = ws.rotary ?: return
        val yaw = r.position(0)
        val tilt = r.position(1)
        val n = Bronze.mirrorNormal(yaw, tilt)
        val d = (Bronze.MIRROR - Bronze.LAMP).normalized()
        val lit = d.dot(n) < 0
        beamIn?.show(Bronze.LAMP, Bronze.MIRROR)
        val hit = if (lit) Bronze.spot(yaw, tilt) else null
        val out = when {
            !lit -> null
            hit != null && hit.distanceTo(Bronze.MIRROR) < 0.6 -> hit
            else -> Bronze.MIRROR + (d - n * (2 * d.dot(n))) * 0.4
        }
        beamOut?.show(if (lit) Bronze.MIRROR else null, out)
        spot?.let { s ->
            s.enabled = out != null
            out?.let(s::setPos)
        }
        words?.enabled = Bronze.onScreen(hit)
    }
}

// ---- Chapter three -------------------------------------------------------------------------------

class ChariotScene(assets: Assets) : Scene(assets) {
    override fun turns(solved: Boolean) = solved

    override fun props(frame: Entity) {
        assets.piece("ch_body")?.let { frame.addChild(it) }
    }

    override fun partVisual(type: String): Entity = assets.piece("ch_$type")
        ?: model(MeshResource.createCylinder(0.01f, if (type == Celestial.BIG_GEAR) 0.05f else 0.025f), assets.brass)

    /** Built: however the table turns, the figure keeps pointing at the player (south). */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        if (solvedFor < 0) return
        val v = bench.build ?: return
        val i = v.assembly.parts.indexOf(Celestial.FIGURE)
        if (i >= 0 && v.assembly.isPlaced(i)) v.holder(i).transform().eulerAngles = EulerAngles(0f, (-bench.yaw).toFloat(), 0f)
    }
}

class ArmillaryScene(assets: Assets) : Scene(assets) {
    private val sights = listOf(0.15f, 0.3f, 0.5f, 0.75f, 1f).map { glow(color(1f, 0.85f, 0.45f, it)).shared() }
    private var sight: Entity? = null
    private var level = -1

    override fun props(frame: Entity) {
        assets.piece("am_base")?.let { frame.addChild(it) }
        // 织女 itself, hanging in the air where the tube must point.
        frame.addChild(model(assets.unitSphere, assets.sparkMaterial).apply {
            setScale(0.012f)
            setPos(Celestial.SPHERE_CENTER + Celestial.STAR * 0.34)
        })
    }

    override fun dialLooks(p: RotaryPuzzle): List<DialLook> {
        val ring = assets.piece("am_ring") ?: model(MeshResource.createTorus(0.09f, 0.085f), assets.brass)
        val tube = Entity().apply {
            addChild(assets.piece("am_tube") ?: assets.box(Vector3(0.012f, 0.012f, 0.17f), Vector3(0f), assets.brass))
            sight = model(assets.unitSphere, sights[0]).apply { setScale(0.01f); setPos(Vector3(0f, 0f, 0.09f)) }.also { addChild(it) }
        }
        val tilt = -(90.0 - Celestial.LATITUDE)
        return listOf(
            DialLook(ring, Celestial.SPHERE_CENTER, listOf(Vector3(0.2f, 0.2f, 0.03f) to Vector3(0f)), dragAlong = Axis.X, baseTilt = tilt),
            DialLook(tube, Vec3.ZERO, listOf(Vector3(0.03f, 0.03f, 0.18f) to Vector3(0f)), parent = 0, dragAlong = Axis.Y),
        )
    }

    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        val r = ws.rotary ?: return
        val c = Celestial.closeness(r.position(0), r.position(1))
        val l = if (r.solved) 4 else ((c - 0.5) * 2 * 4).toInt().coerceIn(0, 3)
        if (l != level) {
            level = l
            sight?.let { Assets.setMaterials(it, sights[l]) }
        }
    }
}

class WaterClockScene(assets: Assets) : Scene(assets) {
    /**
     * The roof (to z 0.11, over the front posts at 0.096) is over everything, so parts slide in from the front. A part in
     * the hand rides over the whole frame and goes in from there: out in front of the roof, down, and in.
     */
    override val frontOpening: Double? = 0.11
    /** The 天衡 comes in 3 cm to the right and moves left once it is behind the 天池 (v0.10.1). */
    override fun frontShift(slot: com.vrgames.lubanbox.core.Slot) = if (slot.type == Celestial.BEAM) 0.03 else 0.0

    override fun props(frame: Entity) {
        assets.piece("wc_frame")?.let { frame.addChild(it) }
    }

    override fun partVisual(type: String): Entity = assets.piece("wc_$type")
        ?: assets.box(Vector3(0.04f), Vector3(0f, 0.02f, 0f), assets.woodMid)

    private var lastTick = 0

    /** Running: the wheel steps on a scoop at a time (a tick each), the globe turns slowly. */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        if (solvedFor < 0) return
        val v = bench.build ?: return
        val ticks = (solvedFor / 1.4).toInt()
        val within = ((solvedFor % 1.4) / 0.25).coerceAtMost(1.0)
        val wheelAngle = (ticks + within) * 15.0
        if (ticks != lastTick) {
            lastTick = ticks
            Sound.shared?.play(Sfx.TICK, Vec3.ZERO)
        }
        for ((i, type) in v.assembly.parts.withIndex()) {
            when (type) {
                Celestial.WHEEL -> v.holder(i).transform().eulerAngles = EulerAngles(wheelAngle.toFloat(), 0f, 0f)
                Celestial.GLOBE -> v.holder(i).transform().eulerAngles = EulerAngles(0f, (solvedFor * 6).toFloat(), 0f)
                Celestial.BEAM -> v.holder(i).transform().eulerAngles = EulerAngles(0f, 0f, (if (within < 1) -6 * (1 - within) else 0.0).toFloat())
            }
        }
    }
}

class ChestScene(assets: Assets) : Scene(assets) {
    private var door: Entity? = null
    private var plaque: Entity? = null

    override fun props(frame: Entity) {
        assets.piece("fc_body")?.let { frame.addChild(it) }
        // The front door hangs on a hinge along its bottom edge. v0.10.1: the hinge is the door's own bottom back edge,
        // in front of the brass trim (the door overlapped the trim by 5 mm, and swung into the turntable).
        door = Entity().apply {
            setPos(Vector3(0f, DOOR_HINGE_Y, 0.105f))
            addChild((assets.piece("fc_door") ?: assets.box(Vector3(0.268f, 0.095f, 0.01f), Vector3(0f, 0.0585f, 0.005f), assets.woodDark)).apply { setPos(Vector3(0f, -DOOR_HINGE_Y, 0f)) })
        }.also { frame.addChild(it) }
        plaque = (assets.piece("fc_plaque") ?: assets.box(Vector3(0.12f, 0.06f, 0.01f), Vector3(0f, 0.03f, 0f), assets.brass)).also {
            it.enabled = false
            frame.addChild(it)
        }
    }

    override fun partVisual(type: String): Entity = assets.piece("fc_$type")
        ?: assets.box(Vector3(0.1f, 0.02f, 0.04f), Vector3(0f, 0.01f, 0f), assets.brass)

    /**
     * Opened: the front door falls open, then the 天工 plaque slides straight out through the doorway and rises in
     * front of the chest. v0.10.1: it used to rise while still inside and went through the lintel above the doorway,
     * and through the door before it was open.
     */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        fun ease(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
        val e = if (solvedFor < 0) 0.0 else ease(solvedFor / 2.5)
        door?.transform()?.eulerAngles = EulerAngles((90 * e).toFloat(), 0f, 0f)
        plaque?.let {
            it.enabled = solvedFor >= 0
            it.setPos(plaquePos(if (solvedFor < 0) 0.0 else (solvedFor - 2.5) / 2.0))
        }
    }

    companion object {
        const val DOOR_HINGE_Y = 0.011f

        /** The plaque's place [u] of the way out (0..1): out through the doorway first, then up. */
        fun plaquePos(u: Double): Vec3 {
            fun ease(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
            return Vec3(0.0, 0.02 + 0.1 * ease((u - 0.6) / 0.4), 0.16 * ease(u / 0.6))
        }
    }
}
