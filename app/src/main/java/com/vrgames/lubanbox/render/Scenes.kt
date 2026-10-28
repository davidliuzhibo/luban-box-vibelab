package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.lubanbox.core.Assembly
import com.vrgames.lubanbox.core.Axis
import com.vrgames.lubanbox.core.Boxes
import com.vrgames.lubanbox.core.Bronze
import com.vrgames.lubanbox.core.Burr
import com.vrgames.lubanbox.core.Burrs
import com.vrgames.lubanbox.core.Celestial
import com.vrgames.lubanbox.core.Cell
import com.vrgames.lubanbox.core.Dougong
import com.vrgames.lubanbox.core.Klotski
import com.vrgames.lubanbox.core.KlotskiBoards
import com.vrgames.lubanbox.core.PuzzleBox
import com.vrgames.lubanbox.core.RotaryPuzzle
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
    // The lock floats at a comfortable height so sticks can slide out downward too.
    override val lift = 0.2f
    override fun turns(solved: Boolean) = true

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
            // Out of the lock, the sticks lie side by side on the turntable under it, along x.
            val center = piece.cells.fold(Vec3.ZERO) { s, c -> s + Vec3(c.x + 0.5, c.y + 0.5, c.z + 0.5) } * (cell / piece.cells.size)
            val turn = when (piece.axis) {
                Axis.X -> Vec3.ZERO
                Axis.Y -> Vec3(0.0, 0.0, 90.0)
                Axis.Z -> Vec3(0.0, 90.0, 0.0)
            }
            val spot = Vec3(0.0, -lift + cell + 0.002, -0.14 + 0.056 * i)
            PieceLook(visual, hit, cell, restPos = spot - quarterTurned(center, turn), restRot = turn)
        }
    }

    /** [v] turned by a single quarter turn about y or z (the rest poses use nothing else). */
    private fun quarterTurned(v: Vec3, deg: Vec3): Vec3 = when {
        deg.z != 0.0 -> Vec3(-v.y, v.x, v.z)
        deg.y != 0.0 -> Vec3(v.z, v.y, -v.x)
        else -> v
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

/** A box-like mechanism of sliding panels: [prefix]body and [prefix]0..n, with stand-in boxes. */
open class PanelScene(
    assets: Assets,
    private val prefix: String,
    private val body: Pair<Vec3, Vec3>,
    private val boxes: List<Pair<Vec3, Vec3>>,
    private val step: (Int) -> Double,
) : Scene(assets) {
    override fun turns(solved: Boolean) = true

    override fun props(frame: Entity) {
        val (size, center) = body
        frame.addChild(assets.piece("${prefix}body") ?: assets.box(size.toVector3(), center.toVector3(), assets.woodDark))
    }

    override fun slideLooks(p: com.vrgames.lubanbox.core.SlidePuzzle): List<PieceLook> = (0 until p.pieceCount).map { i ->
        val (size, center) = boxes[i]
        val visual = assets.piece("$prefix$i") ?: assets.box(size.toVector3(), center.toVector3(), if (i % 2 == 0) assets.woodMid else assets.woodLight)
        PieceLook(visual, listOf(size.toVector3() to center.toVector3()), step(i))
    }
}

class BoxScene(assets: Assets) : PanelScene(assets, "box_", Boxes.body, Boxes.panelBoxes, Boxes::stepMeters) {
    override fun props(frame: Entity) {
        val (size, center) = Boxes.body
        frame.addChild(assets.piece("box_body") ?: assets.box(size.toVector3(), center.toVector3(), assets.woodDark))
    }

    override fun slideLooks(p: com.vrgames.lubanbox.core.SlidePuzzle): List<PieceLook> = (0 until p.pieceCount).map { i ->
        val (size, center) = Boxes.panelBoxes[i]
        val visual = assets.piece("panel$i") ?: assets.box(size.toVector3(), center.toVector3(), if (i == Boxes.LID) assets.woodMid else assets.woodLight)
        PieceLook(visual, listOf(size.toVector3() to center.toVector3()), Boxes.stepMeters(i))
    }
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

    /** In Dougong.parts order; long arms lie along z. */
    override fun tray(a: Assembly) = listOf(
        Vec3(-0.30, 0.0, -0.12) to 0.0, Vec3(-0.30, 0.0, 0.10) to 0.0, Vec3(-0.40, 0.0, 0.0) to 90.0,
        Vec3(0.27, 0.0, -0.18) to 0.0, Vec3(0.33, 0.0, -0.18) to 0.0, Vec3(0.39, 0.0, -0.18) to 0.0,
        Vec3(0.27, 0.0, -0.11) to 0.0, Vec3(0.33, 0.0, -0.11) to 0.0, Vec3(0.40, 0.0, -0.11) to 0.0,
        Vec3(0.27, 0.0, 0.07) to 90.0, Vec3(0.35, 0.0, 0.07) to 0.0, Vec3(0.45, 0.0, 0.05) to 90.0,
        Vec3(-0.47, 0.0, 0.0) to 90.0,
    )

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

class PadlockScene(assets: Assets) : PanelScene(assets, "pl_", Bronze.padlockBody, Bronze.padlockBoxes, Bronze::padlockStep)

class CenserScene(assets: Assets) : Scene(assets) {
    override val lift = 0.03f
    override fun turns(solved: Boolean) = solved

    override fun props(frame: Entity) {
        assets.piece("cs_stand")?.let { frame.addChild(it) }
    }

    override fun partVisual(type: String): Entity = assets.piece("cs_$type")
        ?: model(assets.unitSphere, assets.brass).apply { setScale(if (type == Bronze.BOWL) 0.03f else 0.06f) }

    /**
     * Once put together the censer rolls about on its stand, and the bowl inside stays level: the
     * shell tumbles, the outer ring follows it about one axis only, the inner ring hardly moves.
     */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        if (solvedFor < 0) return
        val v = bench.build ?: return
        val a = 40 * sin(solvedFor * 1.3) * (solvedFor / 2).coerceAtMost(1.0)
        val b = 30 * sin(solvedFor * 0.9 + 1) * (solvedFor / 2).coerceAtMost(1.0)
        for ((i, type) in v.assembly.parts.withIndex()) {
            val e = when (type) {
                Bronze.HEMI_LOW, Bronze.HEMI_HIGH -> EulerAngles(a.toFloat(), 0f, b.toFloat())
                Bronze.RING_OUT -> EulerAngles(0f, 0f, b.toFloat())
                Bronze.RING_IN -> EulerAngles(0f, 90f, 0f)
                else -> EulerAngles(0f, 0f, 0f)
            }
            v.holder(i).transform().eulerAngles = e
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
        // The front door hangs on a hinge along its bottom edge.
        door = Entity().apply {
            setPos(Vector3(0f, 0f, 0.1f))
            addChild(assets.piece("fc_door") ?: assets.box(Vector3(0.28f, 0.11f, 0.01f), Vector3(0f, 0.055f, 0.005f), assets.woodDark))
        }.also { frame.addChild(it) }
        plaque = (assets.piece("fc_plaque") ?: assets.box(Vector3(0.12f, 0.06f, 0.01f), Vector3(0f, 0.03f, 0f), assets.brass)).also {
            it.enabled = false
            frame.addChild(it)
        }
    }

    override fun partVisual(type: String): Entity = assets.piece("fc_$type")
        ?: assets.box(Vector3(0.1f, 0.02f, 0.04f), Vector3(0f, 0.01f, 0f), assets.brass)

    /** Opened: the front door falls open and the 天工 plaque slides out and rises. */
    override fun frame(dt: Double, ws: Workshop, bench: Bench, solvedFor: Double) {
        val t = if (solvedFor < 0) 0.0 else (solvedFor / 2.5).coerceAtMost(1.0)
        val e = t * t * (3 - 2 * t)
        door?.transform()?.eulerAngles = EulerAngles((90 * e).toFloat(), 0f, 0f)
        plaque?.let {
            it.enabled = solvedFor >= 0
            it.setPos(Vec3(0.0, 0.02 + 0.1 * e, 0.16 * e))
        }
    }
}
