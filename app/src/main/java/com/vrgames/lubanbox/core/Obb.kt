package com.vrgames.lubanbox.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Axis-aligned box from [lo] to [hi] (meters). */
data class Aabb(val lo: Vec3, val hi: Vec3) {
    val center get() = (lo + hi) * 0.5
    val half get() = (hi - lo) * 0.5

    fun union(o: Aabb) = Aabb(
        Vec3(minOf(lo.x, o.lo.x), minOf(lo.y, o.lo.y), minOf(lo.z, o.lo.z)),
        Vec3(maxOf(hi.x, o.hi.x), maxOf(hi.y, o.hi.y), maxOf(hi.z, o.hi.z)),
    )

    fun moved(d: Vec3) = Aabb(lo + d, hi + d)
    fun obb() = Obb(center, AXES, half)

    companion object {
        val AXES = listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
    }
}

/** Oriented box: [center], three unit [axes] and the [half] extent along each. */
class Obb(val center: Vec3, val axes: List<Vec3>, val half: Vec3) {
    private fun h(k: Int) = when (k) {
        0 -> half.x
        1 -> half.y
        else -> half.z
    }

    /** Half the box's extent projected on the unit vector [l]. */
    fun radius(l: Vec3) = (0..2).sumOf { h(it) * abs(axes[it].dot(l)) }

    /** Lowest point of the box. */
    val bottom get() = center.y - radius(Vec3.UP)

    /**
     * Separation from [o] by the separating-axis test: the largest gap between the two boxes'
     * shadows on the 15 candidate axes. Positive means apart by at least that much (it never
     * overstates the true distance); zero or less means touching or overlapping.
     */
    fun gap(o: Obb): Double {
        val t = o.center - center
        var best = Double.NEGATIVE_INFINITY
        fun test(l: Vec3) {
            val len = l.length()
            if (len < 1e-9) return
            val u = l * (1 / len)
            val g = abs(t.dot(u)) - radius(u) - o.radius(u)
            if (g > best) best = g
        }
        for (a in axes) test(a)
        for (b in o.axes) test(b)
        for (a in axes) for (b in o.axes) test(a.cross(b))
        return best
    }
}

/** A quarter turn (or none) about one of the object's axes, as the burr's sticks lie down. */
enum class TurnAxis { NONE, X, Y, Z }

/** Rotates [v] by [deg] degrees about [axis], right-handed (counterclockwise looking down the axis). */
fun rotate(v: Vec3, axis: TurnAxis, deg: Double): Vec3 {
    if (axis == TurnAxis.NONE || deg == 0.0) return v
    val a = Math.toRadians(deg)
    val c = cos(a)
    val s = sin(a)
    return when (axis) {
        TurnAxis.X -> Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
        TurnAxis.Y -> Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
        TurnAxis.Z -> Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
        TurnAxis.NONE -> v
    }
}

/** Distance in the floor plane (x, z) from [p] to the rectangle [r] (zero inside). */
fun flatDistance(p: Vec3, r: Aabb): Double {
    val dx = maxOf(r.lo.x - p.x, 0.0, p.x - r.hi.x)
    val dz = maxOf(r.lo.z - p.z, 0.0, p.z - r.hi.z)
    return sqrt(dx * dx + dz * dz)
}
