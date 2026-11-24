package com.vrgames.lubanbox.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * v0.10.1: the turning relics (文字锁, 透光镜, 浑天仪) never turn a part into another, at any angle, checked on the real
 * models (surface points from the GLBs, tools/extract_points.py with 1 mm / 2 mm spacing).
 *
 * A part turning about an axis sweeps a solid of revolution: at each height a along the axis, the distances r from
 * the axis its points cover. Every point of every other part must lie outside those bands by more than −0.5 mm
 * (touching allowed, no overlap deeper than 0.5 mm). Pivots — a pin in its socket on the very axis the part turns
 * about — are named and left out.
 */
class RotaryAuditTest {
    companion object {
        const val MAX_PEN = 0.0005
        /** Height bins along the axis, and the largest gap between samples that still counts as solid in between. */
        const val BIN = 0.001
        const val GAP = 0.0025
    }

    /** The bands of distance from the axis that a turning part fills at each height along the axis. */
    class Revolve(a: DoubleArray, r: DoubleArray) {
        private val bands = HashMap<Int, List<DoubleArray>>()

        init {
            val byBin = HashMap<Int, ArrayList<Double>>()
            for (k in a.indices) byBin.getOrPut(floor(a[k] / BIN).toInt()) { ArrayList() } += r[k]
            for ((bin, rs) in byBin) {
                rs.sort()
                val out = ArrayList<DoubleArray>()
                var lo = rs[0]
                var prev = rs[0]
                for (x in rs.drop(1)) {
                    if (x - prev > GAP) {
                        out += doubleArrayOf(lo, prev)
                        lo = x
                    }
                    prev = x
                }
                out += doubleArrayOf(lo, prev)
                bands[bin] = out
            }
        }

        /** How deep a point at height [a], distance [r] from the axis lies inside the swept solid (≤ 0: outside). */
        fun depth(a: Double, r: Double): Double {
            val b = floor(a / BIN).toInt()
            var worst = Double.NEGATIVE_INFINITY
            // The bin itself only: its neighbours' bands are a millimetre off along the axis.
            for (band in bands[b] ?: return worst) worst = maxOf(worst, minOf(r - band[0], band[1] - r))
            return worst
        }
    }

    private fun pts(model: String, node: String) = ModelPoints.node(model, node)

    /** Worst depth of [others] (already as (a, r) pairs) in [rev], with where it happened. */
    private fun worst(rev: Revolve, others: List<Pair<String, List<Pair<Double, Double>>>>): Pair<Double, String> {
        var w = Double.NEGATIVE_INFINITY
        var at = ""
        for ((name, list) in others) for ((a, r) in list) {
            val d = rev.depth(a, r)
            if (d > w) {
                w = d
                at = "$name a=${"%.1f".format(a * 1000)} r=${"%.1f".format(r * 1000)} mm"
            }
        }
        return w to at
    }

    @Test
    fun wordLockRingsTurnFreely() {
        val dials = Bronze.wordLock().dials
        val body = pts("wordlock", "wl_body")
        for ((i, d) in dials.withIndex()) {
            val ring = pts("wordlock", "wl_ring$i")
            val rev = Revolve(DoubleArray(ring.size) { ring[it].x }, DoubleArray(ring.size) { sqrt(ring[it].y * ring[it].y + ring[it].z * ring[it].z) })
            fun local(p: Vec3, cx: Double) = (p.x - cx) to sqrt((p.y - d.center.y) * (p.y - d.center.y) + (p.z - d.center.z) * (p.z - d.center.z))
            val others = ArrayList<Pair<String, List<Pair<Double, Double>>>>()
            others += "wl_body" to body.map { local(it, d.center.x) }
            for ((j, e) in dials.withIndex()) if (j != i) {
                // The other rings turn on the same shaft: their (a, r) do not depend on how they are turned.
                others += "wl_ring$j" to pts("wordlock", "wl_ring$j").map { (it.x + e.center.x - d.center.x) to sqrt(it.y * it.y + it.z * it.z) }
            }
            val (w, at) = worst(rev, others)
            println("文字锁 ring $i: deepest ${"%.2f".format(maxOf(0.0, w) * 1000)} mm ($at)")
            assertTrue("ring $i turns into $at by ${w * 1000} mm", w <= MAX_PEN)
        }
    }

    @Test
    fun mirrorTurnsAndTiltsFreely() {
        val yoke = pts("mirror", "mr_yoke")
        val face = pts("mirror", "mr_face")
        val tilts = (-30..30 step 2).map { it.toDouble() }
        // 1. The face tilting in the yoke (about the yoke's x): against the yoke, but not its two pivot pins.
        val faceRev = Revolve(DoubleArray(face.size) { face[it].x }, DoubleArray(face.size) { sqrt(face[it].y * face[it].y + face[it].z * face[it].z) })
        val yokeNoPins = yoke.filter { !(kotlin.math.abs(it.x) > 0.0535 && sqrt(it.y * it.y + it.z * it.z) < 0.0035) }
        val (w1, at1) = worst(faceRev, listOf("mr_yoke" to yokeNoPins.map { it.x to sqrt(it.y * it.y + it.z * it.z) }))
        println("透光镜 face in its yoke: deepest ${"%.2f".format(maxOf(0.0, w1) * 1000)} mm ($at1)")
        assertTrue("face tilts into $at1", w1 <= MAX_PEN)
        // 2. Yoke and face (at every tilt) turning about the upright through the mirror: against everything else.
        val a = ArrayList<Double>()
        val r = ArrayList<Double>()
        for (p in yoke) {
            a += p.y
            r += sqrt(p.x * p.x + p.z * p.z)
        }
        for (t in tilts) {
            val c = cos(Math.toRadians(t))
            val s = sin(Math.toRadians(t))
            for (p in face) {
                val y = p.y * c - p.z * s
                val z = p.y * s + p.z * c
                a += y
                r += sqrt(p.x * p.x + z * z)
            }
        }
        val rev = Revolve(a.toDoubleArray(), r.toDoubleArray())
        val m = Bronze.MIRROR
        fun local(p: Vec3) = (p.y - m.y) to sqrt((p.x - m.x) * (p.x - m.x) + (p.z - m.z) * (p.z - m.z))
        // The yoke's spindle stands on the post: a bearing on the very axis it turns about.
        val base = pts("mirror", "mr_base").filter { !(local(it).second < 0.0085 && local(it).first < -0.065) }
        val others = listOf("mr_base" to base.map(::local)) + listOf("mr_lamp", "mr_screen", "mr_words").map { n -> n to pts("mirror", n).map(::local) }
        val (w2, at2) = worst(rev, others)
        println("透光镜 turning on its stand: deepest ${"%.2f".format(maxOf(0.0, w2) * 1000)} mm ($at2)")
        assertTrue("mirror turns into $at2", w2 <= MAX_PEN)
    }

    @Test
    fun armillaryRingAndTubeTurnFreely() {
        val ring = pts("armillary", "am_ring")
        val tube = pts("armillary", "am_tube")
        // 1. The tube turning in the ring (about the ring's x), a full turn (it only swings ±60°): against the ring.
        val tubeRev = Revolve(DoubleArray(tube.size) { tube[it].x }, DoubleArray(tube.size) { sqrt(tube[it].y * tube[it].y + tube[it].z * tube[it].z) })
        val (w1, at1) = worst(tubeRev, listOf("am_ring" to ring.map { it.x to sqrt(it.y * it.y + it.z * it.z) }))
        println("浑天仪 tube in its ring: deepest ${"%.2f".format(maxOf(0.0, w1) * 1000)} mm ($at1)")
        assertTrue("tube turns into $at1", w1 <= MAX_PEN)
        // 2. Ring and tube (every declination it can reach) turning about the polar axis: against the stand.
        val a = ArrayList<Double>()
        val r = ArrayList<Double>()
        for (p in ring) {
            a += p.y
            r += sqrt(p.x * p.x + p.z * p.z)
        }
        for (d in -60..60 step 2) {
            val c = cos(Math.toRadians(d.toDouble()))
            val s = sin(Math.toRadians(d.toDouble()))
            for (p in tube) {
                val y = p.y * c - p.z * s
                val z = p.y * s + p.z * c
                a += y
                r += sqrt(p.x * p.x + z * z)
            }
        }
        val rev = Revolve(a.toDoubleArray(), r.toDoubleArray())
        // The ring's holder is tipped back by 90° − latitude about x at the sphere's middle: into its frame.
        val tilt = Math.toRadians(90.0 - Celestial.LATITUDE)
        val c0 = Celestial.SPHERE_CENTER
        fun local(p: Vec3): Pair<Double, Double> {
            val q = p - c0
            // Undo a turn of −tilt about x.
            val y = q.y * cos(tilt) - q.z * sin(tilt)
            val z = q.y * sin(tilt) + q.z * cos(tilt)
            return y to sqrt(q.x * q.x + z * z)
        }
        // The pole's two stubs meet the ring's pivots on the axis: a bearing, left out.
        val base = pts("armillary", "am_base").map(::local).filter { (aa, rr) -> !(rr < 0.003 && kotlin.math.abs(aa) > 0.0895) }
        val (w2, at2) = worst(rev, listOf("am_base" to base))
        println("浑天仪 ring turning on the pole: deepest ${"%.2f".format(maxOf(0.0, w2) * 1000)} mm ($at2)")
        assertTrue("ring turns into $at2", w2 <= MAX_PEN)
    }
}
