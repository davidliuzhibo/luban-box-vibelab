package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * v0.10.1: the builds (斗拱, 被中香炉, 指南车, 水运仪象台, 总机关匣) are checked on their real meshes by
 * tools/audit_meshes.py (Blender: every part along the way it snaps into its slot, at rest, and the show-off
 * animations), which writes tools/data/mesh_audit.tsv. This test holds that result: nothing overlaps more than
 * 0.5 mm (a pin in its socket: no deeper than where it rests), and the models are still the ones audited (SHA-256),
 * so a changed model fails here until the audit is run again.
 */
class MeshAuditTest {
    private val tsv = File("../tools/data/mesh_audit.tsv")
    private val models = File("src/main/assets/models")

    @Test
    fun buildsAndAnimationsStayClear() {
        assertTrue("run tools/audit_meshes.py", tsv.exists())
        val lines = tsv.readLines()
        val hashes = lines.filter { it.startsWith("#sha256") }.map { it.split("\t") }
        assertTrue(hashes.size >= 5)
        for ((_, file, sha) in hashes) {
            val now = MessageDigest.getInstance("SHA-256").digest(File(models, file).readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("$file changed since the mesh audit (run tools/audit_meshes.py again)", sha, now)
        }
        val rows = lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { it.split("\t") }
        assertTrue(rows.size > 50)
        var worst = 0.0
        val known = ArrayList<String>()
        for (r in rows) {
            val over = r[6].toDouble()
            if (KNOWN.any { (relic, check) -> r[0] == relic && r[1].startsWith(check) }) {
                if (over > 0.5) known += "${r[0]} ${r[1]} ${r[2]} ${r[3]} mm"
                continue
            }
            worst = maxOf(worst, over)
            assertTrue("${r[0]} ${r[1]} ${r[2]}: ${r[3]} mm" + (if (r[5] == "1") " (rests at ${r[4]} mm)" else ""), over <= 0.5)
        }
        println("mesh audit: ${rows.size} checks, worst ${"%.2f".format(worst)} mm beyond touching (or beyond a joint's rest)")
        println("known, not fixed in 0.10.1 (reported): " + known.joinToString("; "))
    }

    companion object {
        /** Nothing is excused any more (v0.10.1: the 被中香炉's rings turn flat to let the inner ring and the bowl in). */
        val KNOWN = emptyList<Pair<String, String>>()
    }
}
