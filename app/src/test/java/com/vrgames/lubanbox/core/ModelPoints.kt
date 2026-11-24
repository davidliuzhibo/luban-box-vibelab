package com.vrgames.lubanbox.core

import java.io.DataInputStream

/**
 * Surface points of the game's models (tools/extract_points.py: every vertex plus points spread over each
 * triangle), per node, in the object frame (meters). Test resources geometry/<model>.pts.
 */
object ModelPoints {
    private val cache = HashMap<String, Map<String, List<Vec3>>>()

    fun of(model: String): Map<String, List<Vec3>> = cache.getOrPut(model) {
        val stream = ModelPoints::class.java.classLoader!!.getResourceAsStream("geometry/$model.pts.gz") ?: error("geometry/$model.pts.gz missing (run tools/extract_points.py)")
        val out = LinkedHashMap<String, List<Vec3>>()
        DataInputStream(java.util.zip.GZIPInputStream(stream).buffered()).use { d ->
            while (true) {
                val len = try { d.readUnsignedShort() } catch (_: java.io.EOFException) { break }
                val name = ByteArray(len).also { d.readFully(it) }.toString(Charsets.UTF_8)
                val n = d.readInt()
                out[name] = List(n) { Vec3(d.readInt() * 1e-5, d.readInt() * 1e-5, d.readInt() * 1e-5) }
            }
        }
        out
    }

    fun node(model: String, name: String): List<Vec3> = of(model)[name] ?: error("$model has no node $name (has ${of(model).keys})")

    /** How far [p] lies outside the union of [boxes] (0 if inside one of them). */
    fun outside(p: Vec3, boxes: List<Aabb>): Double = boxes.minOf { b ->
        val dx = maxOf(b.lo.x - p.x, 0.0, p.x - b.hi.x)
        val dy = maxOf(b.lo.y - p.y, 0.0, p.y - b.hi.y)
        val dz = maxOf(b.lo.z - p.z, 0.0, p.z - b.hi.z)
        kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun bounds(points: List<Vec3>) = Aabb(
        Vec3(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z }),
        Vec3(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z }),
    )
}
