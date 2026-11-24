package com.vrgames.lubanbox.core

/**
 * Collision geometry of the sliding mechanisms (v0.10.1, PICO device test 2026-11-23: the box's front panel slid
 * into the side panel). Each moving part is the union of axis-aligned boxes its model is built from
 * (tools/make_box.py, make_bronze.py build the models from exactly these boxes, and GeometryAuditTest checks every
 * vertex of the GLB models lies inside them), so "can it move there" is decided by the same shapes the player sees.
 *
 * Parts may touch: up to [CONTACT] of overlap counts as surfaces resting on each other.
 */
object Sweep {
    /** Overlap that still counts as touching (meters). The audit allows 0.5 mm; this keeps 0.1 mm in hand. */
    const val CONTACT = 0.0004

    private fun lo(b: Aabb, a: Int) = when (a) { 0 -> b.lo.x; 1 -> b.lo.y; else -> b.lo.z }
    private fun hi(b: Aabb, a: Int) = when (a) { 0 -> b.hi.x; 1 -> b.hi.y; else -> b.hi.z }

    /** How deep two boxes overlap: the smallest overlap over the three axes (zero or less: apart or touching). */
    fun penetration(a: Aabb, b: Aabb): Double =
        (0..2).minOf { k -> minOf(hi(a, k), hi(b, k)) - maxOf(lo(a, k), lo(b, k)) }

    /** Deepest overlap between any box of [a] and any of [b]. */
    fun penetration(a: List<Aabb>, b: List<Aabb>): Double {
        var worst = Double.NEGATIVE_INFINITY
        for (x in a) for (y in b) worst = maxOf(worst, penetration(x, y))
        return worst
    }

    /**
     * How far the boxes [moving] can slide along axis [axis] (0 x, 1 y, 2 z) in direction [dir] (+1 or -1) before
     * one of them would sink more than [CONTACT] into one of [obstacles]. [Double.POSITIVE_INFINITY] if nothing is
     * in the way; 0 if they already overlap more than that across the way.
     */
    fun freeDistance(moving: List<Aabb>, axis: Int, dir: Int, obstacles: List<Aabb>): Double {
        var best = Double.POSITIVE_INFINITY
        for (m in moving) for (o in obstacles) {
            // Only boxes that overlap across the way (more than touching) can stop the slide.
            var across = true
            for (k in 0..2) if (k != axis && minOf(hi(m, k), hi(o, k)) - maxOf(lo(m, k), lo(o, k)) <= CONTACT) across = false
            if (!across) continue
            val gap = if (dir > 0) lo(o, axis) - hi(m, axis) else lo(m, axis) - hi(o, axis)
            // Already behind it (passed or never in front): no limit from this one.
            val behind = if (dir > 0) hi(o, axis) <= lo(m, axis) + CONTACT else lo(o, axis) >= hi(m, axis) - CONTACT
            if (behind) continue
            best = minOf(best, maxOf(0.0, gap + CONTACT))
        }
        return best
    }

    fun unit(axis: Axis) = when (axis) {
        Axis.X -> Vec3(1.0, 0.0, 0.0)
        Axis.Y -> Vec3(0.0, 1.0, 0.0)
        Axis.Z -> Vec3(0.0, 0.0, 1.0)
    }
}

/** Builds an [Aabb] from its corners' coordinates (meters, object frame). */
fun box(x0: Double, x1: Double, y0: Double, y1: Double, z0: Double, z1: Double) =
    Aabb(Vec3(minOf(x0, x1), minOf(y0, y1), minOf(z0, z1)), Vec3(maxOf(x0, x1), maxOf(y0, y1), maxOf(z0, z1)))

/**
 * The shapes of a [PuzzleBox]: each panel's boxes where it sits closed ([parts], in panel order), the fixed body
 * and whatever it stands on ([fixed]), and the length of one step of each panel ([step], meters).
 */
class BoxShapes(val parts: List<List<Aabb>>, val fixed: List<Aabb>, val step: (Int) -> Double)

/**
 * [a] with the [holes] cut out, as boxes: [a] is split on every face plane of the holes and the pieces outside all
 * holes are kept (merged along x, then along y, where they line up).
 */
fun subtractBoxes(a: Aabb, holes: List<Aabb>): List<Aabb> {
    fun cuts(lo: Double, hi: Double, pick: (Aabb) -> Pair<Double, Double>): List<Double> {
        val c = sortedSetOf(lo, hi)
        for (h in holes) pick(h).let { (l, u) -> if (l > lo && l < hi) c += l; if (u > lo && u < hi) c += u }
        return c.toList()
    }
    val xs = cuts(a.lo.x, a.hi.x) { it.lo.x to it.hi.x }
    val ys = cuts(a.lo.y, a.hi.y) { it.lo.y to it.hi.y }
    val zs = cuts(a.lo.z, a.hi.z) { it.lo.z to it.hi.z }
    fun inHole(x: Double, y: Double, z: Double) = holes.any { h ->
        x > h.lo.x && x < h.hi.x && y > h.lo.y && y < h.hi.y && z > h.lo.z && z < h.hi.z
    }
    // Runs along x for each (y, z) cell.
    val rows = ArrayList<Aabb>()
    for (k in 0 until zs.size - 1) for (j in 0 until ys.size - 1) {
        var start = -1
        for (i in 0..xs.size - 1) {
            val keep = i < xs.size - 1 && !inHole((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2, (zs[k] + zs[k + 1]) / 2)
            if (keep && start < 0) start = i
            if (!keep && start >= 0) {
                rows += Aabb(Vec3(xs[start], ys[j], zs[k]), Vec3(xs[i], ys[j + 1], zs[k + 1]))
                start = -1
            }
        }
    }
    // Stack rows that match in x and z and meet in y.
    val out = ArrayList<Aabb>()
    for (r in rows.sortedWith(compareBy({ it.lo.z }, { it.lo.x }, { it.hi.x }, { it.lo.y }))) {
        val last = out.lastOrNull()
        if (last != null && last.lo.x == r.lo.x && last.hi.x == r.hi.x && last.lo.z == r.lo.z && last.hi.z == r.hi.z && last.hi.y == r.lo.y) {
            out[out.size - 1] = Aabb(last.lo, Vec3(last.hi.x, r.hi.y, last.hi.z))
        } else {
            out += r
        }
    }
    return out
}

/**
 * v0.10.1: a part carried in the hand never sinks into anything. [box] is the part's box relative to its origin,
 * [at] where the hand would put the origin; the result is the lowest height for the origin at that x, z that keeps
 * the part's box on or above every box of [obstacles] whose footprint it overlaps, and on or above [floor]. Held
 * parts ride over what is there (boxes contain their models, so the models never meet).
 */
fun liftOver(box: Aabb, at: Vec3, obstacles: List<Aabb>, floor: Double): Double {
    var y = maxOf(at.y, floor - box.lo.y)
    val x0 = at.x + box.lo.x
    val x1 = at.x + box.hi.x
    val z0 = at.z + box.lo.z
    val z1 = at.z + box.hi.z
    for (o in obstacles) {
        if (x0 < o.hi.x - 1e-6 && o.lo.x < x1 - 1e-6 && z0 < o.hi.z - 1e-6 && o.lo.z < z1 - 1e-6) y = maxOf(y, o.hi.y - box.lo.y)
    }
    return y
}

/**
 * The fixed parts of each build (column, stand, chassis, frame, chest body) as boxes in the relic's frame, from their
 * models' bounds (tools/audit_meshes.py checks each model lies inside its box). Held parts ride over them.
 */
object BuildProps {
    fun boxes(relic: String): List<Aabb> = when (relic) {
        "dougong" -> listOf(box(-0.055, 0.055, -0.12, 0.0, -0.055, 0.055))
        "censer" -> listOf(box(-0.045, 0.045, -0.03, 0.0005, -0.045, 0.045))
        "chariot" -> listOf(box(-0.12, 0.12, -0.004, 0.094, -0.06, 0.194))
        "waterclock" -> listOf(box(-0.17, 0.17, -0.01, 0.372, -0.11, 0.11))
        "chest" -> listOf(box(-0.155, 0.155, 0.0, 0.12, -0.105, 0.105))
        else -> emptyList()
    }
}
