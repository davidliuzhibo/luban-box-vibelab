package com.vrgames.lubanbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * v0.10.1 (PICO device test 2026-11-23: in the 鲁班盒, after lifting the right end 1 cm the front panel slid right
 * into it). Every sliding mechanism is checked against the real shapes of its models:
 *
 * - the models (surface points from the GLBs, tools/extract_points.py) lie inside the boxes the rules collide, and
 *   those boxes are no bigger than the models (so what looks free is free, and what looks blocked is blocked);
 * - for every state the rules can reach and every move they allow, the moving part is swept in 1 mm steps (and
 *   exactly, as a swept box) and never sinks more than 0.5 mm into anything;
 * - every mechanism can still be opened by such moves.
 */
class GeometryAuditTest {
    companion object {
        const val MAX_PEN = 0.0005
        const val SWEEP = 0.001

        /** Rows of the before → after table (relic, part, move, worst overlap before, after). */
        val table = ArrayList<String>()
    }

    // ---- Models against shapes ---------------------------------------------------------------

    /**
     * Points of [node] in [model] all inside [boxes], and the boxes' bounds within [slack] of the model's. [fillet]: the
     * bevel rounds every edge, and on an inside corner (where two boxes of a part meet) it adds a fillet of 0.41 × its
     * width; the part pressed into that corner has its own edge rounded off by the same amount, so they still only touch.
     */
    private fun assertShapeFits(model: String, node: String, boxes: List<Aabb>, slack: Double = 0.0012, fillet: Double = 0.0001) {
        val pts = ModelPoints.node(model, node)
        val worst = pts.maxOf { ModelPoints.outside(it, boxes) }
        assertTrue("$model/$node: a point lies ${"%.2f".format(worst * 1000)} mm outside its shape", worst <= fillet)
        val m = ModelPoints.bounds(pts)
        val s = boxes.reduce { a, b -> a.union(b) }
        val gaps = listOf(m.lo.x - s.lo.x, m.lo.y - s.lo.y, m.lo.z - s.lo.z, s.hi.x - m.hi.x, s.hi.y - m.hi.y, s.hi.z - m.hi.z)
        assertTrue("$model/$node: shape bigger than the model by ${gaps.map { "%.2f".format(it * 1000) }} mm", gaps.all { it <= slack })
    }

    @Test
    fun boxModelIsItsShapes() {
        // Bevel 1 mm: inside-corner fillets up to 0.42 mm.
        Boxes.parts.forEachIndexed { i, boxes -> assertShapeFits("box", "panel$i", boxes, fillet = 0.00042) }
        assertShapeFits("box", "box_body", listOf(Boxes.body, Boxes.plinth))
        // Every box of every panel is really there in the model (no shape part the model does not show): its middle
        // has model points within its own extent on both sides along each axis.
        Boxes.parts.forEachIndexed { i, boxes ->
            val pts = ModelPoints.node("box", "panel$i")
            for (b in boxes) {
                val inside = pts.filter { ModelPoints.outside(it, listOf(b)) <= 1e-6 }
                val m = ModelPoints.bounds(inside)
                for ((lo, hi) in listOf(m.lo.x - b.lo.x to b.hi.x - m.hi.x, m.lo.y - b.lo.y to b.hi.y - m.hi.y, m.lo.z - b.lo.z to b.hi.z - m.hi.z)) {
                    assertTrue("panel$i box $b: model ends ${"%.2f".format(lo * 1000)} / ${"%.2f".format(hi * 1000)} mm short", lo <= 0.0012 && hi <= 0.0012)
                }
            }
        }
    }

    @Test
    fun padlockModelIsItsShapes() {
        Bronze.padlockParts.forEachIndexed { i, boxes -> assertShapeFits("padlock", "pl_$i", boxes) }
        // The body's boxes stand for a 6 mm rounded block: up to 6 mm bigger than its corners, never smaller. Its round
        // holes are cut out as boxes around them (the key and the shackle's legs are round too: checked below against
        // the round holes themselves); the model has no material inside the round holes.
        val body = ModelPoints.node("padlock", "pl_body")
        val solid = listOf(box(-0.05, 0.05, 0.0, 0.08, -0.02, 0.02), box(-0.043, 0.043, 0.007, 0.073, 0.02, Bronze.FACE_Z), box(-0.0175, 0.0175, 0.0135, 0.0305, Bronze.FACE_Z, 0.0233))
        val worst = body.maxOf { ModelPoints.outside(it, solid) }
        assertTrue("pl_body: ${"%.2f".format(worst * 1000)} mm outside", worst <= 0.0001)
        fun inRound(p: Vec3, cx: Double, cy: Double, r: Double, along: Char) =
            if (along == 'z') kotlin.math.hypot(p.x - cx, p.y - cy) < r else kotlin.math.hypot(p.x - cx, p.z) < r
        val inHoles = body.count { p ->
            (p.z > Bronze.KEYHOLE_FLOOR + 3e-4 && inRound(p, 0.0, Bronze.KEYHOLE_Y, Bronze.KEYHOLE_R * kotlin.math.cos(Math.PI / 32) - 3e-4, 'z')) ||
                (p.y > 0.0593 && (inRound(p, -0.035, 0.0, 0.0052 * kotlin.math.cos(Math.PI / 24) - 3e-4, 'y') || inRound(p, 0.035, 0.0, 0.0052 * kotlin.math.cos(Math.PI / 24) - 3e-4, 'y')))
        }
        assertEquals("model material inside the round holes", 0, inHoles)
        // The shackle's round legs (5 mm) in their round holes (5.2 mm, 24-sided).
        val legs = ModelPoints.node("padlock", "pl_3").filter { it.y < 0.0805 }
        assertTrue(legs.all { kotlin.math.hypot(kotlin.math.abs(it.x) - 0.035, it.z) <= 0.0052 * kotlin.math.cos(Math.PI / 24) - 1e-4 })
        // The key really fits its keyhole (front view): the round shaft in the round hole, the bit in the slot under it.
        val holeR = Bronze.KEYHOLE_R * kotlin.math.cos(Math.PI / 32)
        assertTrue("shaft ${Bronze.KEY_R} in hole $holeR", Bronze.KEY_R + 0.0005 <= holeR)
        assertTrue(Bronze.BIT_HALF_W + 0.0003 <= Bronze.SLOT_HALF_W && Bronze.BIT_Y0 - 0.0005 >= Bronze.SLOT_BOTTOM && Bronze.BIT_Y1 <= Bronze.KEYHOLE_Y)
        // Every key point that goes into the body (6 cm of travel past its face) lies inside the keyhole's outline (round
        // hole + slot); the bow stays outside.
        val travel = 4 * Bronze.padlockStep(Bronze.KEY)
        val key = ModelPoints.node("padlock", "pl_1").filter { it.z - travel < Bronze.FACE_Z + 0.001 }
        val outOfHole = key.maxOf { p ->
            val r = kotlin.math.hypot(p.x, p.y - Bronze.KEYHOLE_Y)
            val inRound = r <= holeR
            val inSlot = abs(p.x) <= Bronze.SLOT_HALF_W && p.y >= Bronze.SLOT_BOTTOM && p.y <= Bronze.KEYHOLE_Y
            if (inRound || inSlot) 0.0 else minOf(r - holeR, maxOf(abs(p.x) - Bronze.SLOT_HALF_W, Bronze.SLOT_BOTTOM - p.y))
        }
        assertTrue("key sticks out of the keyhole by ${"%.2f".format(outOfHole * 1000)} mm", outOfHole <= 0.0)
    }

    @Test
    fun burrSticksAreTheirCells() {
        val burr = Burrs.lock()
        // Bevel 1.2 mm: inside-corner fillets up to 0.51 mm, matched by the 0.5 mm rounded off the edge pressed into them.
        burr.pieces.forEachIndexed { i, p -> assertShapeFits("burr", "stick$i", cellBoxes(p.cells, Cell(0, 0, 0)), fillet = 0.00052) }
    }

    @Test
    fun klotskiBlocksFitTheirCellsAndTheRimStaysOutTheField() {
        val cell = KlotskiBoards.CELL
        for ((name, w, h) in listOf(Triple("cao", 2, 2), Triple("guan", 2, 1), Triple("general_张飞", 1, 2), Triple("bing", 1, 1))) {
            val b = ModelPoints.bounds(ModelPoints.node("klotski", name))
            assertTrue("$name wider than its cells", b.hi.x - b.lo.x <= w * cell - 0.001 && b.hi.z - b.lo.z <= h * cell - 0.001)
            assertTrue("$name not centred", abs(b.lo.x + b.hi.x) < 1e-4 && abs(b.lo.z + b.hi.z) < 1e-4 && b.lo.y >= -1e-4)
        }
        // Inside the 4 × 5 field the board is no higher than its floor (y = 0), where the blocks stand.
        val hx = Klotski.W * cell / 2
        val hz = Klotski.H * cell / 2
        val high = ModelPoints.node("klotski", "board").filter { abs(it.x) < hx - 1e-4 && abs(it.z) < hz - 1e-4 }.maxOf { it.y }
        assertTrue("board rises ${"%.2f".format(high * 1000)} mm inside the field", high <= MAX_PEN)
    }

    // ---- Every reachable state, every allowed move ---------------------------------------------

    /** Worst overlap while panel [i] goes from [from] to [to] (positions) with the others at [a], swept in 1 mm steps. */
    private fun sweepPanel(p: PuzzleBox, a: IntArray, i: Int, from: Int, to: Int): Double {
        val others = p.others(i, a)
        val start = p.displacement(i, from)
        val end = p.displacement(i, to)
        val len = start.distanceTo(end)
        val n = maxOf(1, kotlin.math.ceil(len / SWEEP).toInt())
        var worst = Double.NEGATIVE_INFINITY
        for (k in 0..n) {
            val d = start + (end - start) * (k.toDouble() / n)
            worst = maxOf(worst, Sweep.penetration(p.shapes!!.parts[i].map { it.moved(d) }, others))
        }
        // Exactly: the box each part of the panel sweeps through.
        val swept = p.shapes!!.parts[i].map { it.moved(start).union(it.moved(end)) }
        return maxOf(worst, Sweep.penetration(swept, others))
    }

    data class MoveKey(val part: Int, val from: Int, val to: Int)

    /** Every reachable state of [p] by its rules; per move kind, the worst overlap seen. Asserts the static states too. */
    private fun audit(p: PuzzleBox, title: String, assertClean: Boolean = true): Map<MoveKey, Double> {
        p.reset()
        val start = p.encode(p.positions())
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        val worst = HashMap<MoveKey, Double>()
        var moves = 0
        var staticWorst = Double.NEGATIVE_INFINITY
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            val a = p.decode(s)
            // Standing still: nothing overlaps.
            for (i in p.panels.indices) staticWorst = maxOf(staticWorst, Sweep.penetration(p.solid(i, a[i]), p.others(i, a)))
            for (i in p.panels.indices) for (dir in intArrayOf(1, -1)) {
                val to = a[i] + dir
                if (to < 0 || to > p.panels[i].steps || !p.free(i, a[i], to, a)) continue
                moves++
                val pen = sweepPanel(p, a, i, a[i], to)
                val key = MoveKey(i, a[i], to)
                worst[key] = maxOf(worst[key] ?: Double.NEGATIVE_INFINITY, pen)
                if (assertClean) assertTrue("$title: ${p.panels[i].name} ${a[i]}→$to from ${a.toList()} sinks ${"%.2f".format(pen * 1000)} mm", pen <= MAX_PEN)
                val t = p.encode(a.copyOf().also { it[i] = to })
                if (seen.add(t)) queue.addLast(t)
            }
        }
        if (assertClean) assertTrue("$title: parts overlap ${"%.2f".format(staticWorst * 1000)} mm at rest", staticWorst <= MAX_PEN)
        println("$title: ${seen.size} reachable states, $moves single-step moves, worst overlap at rest ${"%.2f".format(maxOf(0.0, staticWorst) * 1000)} mm, while moving ${"%.2f".format(maxOf(0.0, worst.values.max()) * 1000)} mm")
        p.reset()
        return worst
    }

    @Test
    fun everyBoxMoveIsClearAndTheBoxStillOpens() {
        val box = Boxes.newBox()
        val worst = audit(box, "鲁班盒")
        // It opens with clear moves only, in the 8 moves of Boxes' doc.
        val sol = box.solution()
        assertNotNull(sol)
        assertEquals(8, sol!!.size)
        for (m in sol) {
            assertTrue(m.to in box.reach(m.piece, m.axis))
            box.move(m.piece, m.axis, m.to)
        }
        assertTrue(box.solved)
        // The lid comes clear of the box entirely.
        val lid = box.solid(Boxes.LID, Boxes.panels[Boxes.LID].steps)
        assertTrue(Sweep.penetration(lid, box.others(Boxes.LID, box.positions())) <= 0.0)
        assertTrue(worst.values.all { it <= MAX_PEN })
    }

    @Test
    fun everyPadlockMoveIsClearAndItStillOpens() {
        val lock = Bronze.padlock()
        val worst = audit(lock, "花旗锁")
        val sol = lock.solution()!!
        for (m in sol) lock.move(m.piece, m.axis, m.to)
        assertTrue(lock.solved)
        assertTrue(worst.values.all { it <= MAX_PEN })
    }

    /**
     * The device screenshot: right end lifted 1 cm, then the front pushed right. Now the front goes right exactly
     * as far as the room the lifted tab leaves (1 cm, touching), no farther; and with the right end still down it
     * does not move at all. A drag shows no more than that either.
     */
    @Test
    fun theScreenshotMoveStopsAtContact() {
        val box = Boxes.newBox()
        assertEquals(0..0, box.reach(Boxes.FRONT, Axis.X))
        assertEquals(0.0, box.span(Boxes.FRONT, Axis.X).endInclusive, 0.05)
        assertTrue(box.geometricFree(Boxes.FRONT, 0, 1, box.positions()) <= Sweep.CONTACT + 1e-9)
        box.move(Boxes.RIGHT, Axis.Y, 1)
        assertEquals(0..1, box.reach(Boxes.FRONT, Axis.X))
        // Free distance from 0: exactly the 1 cm the tab has left (plus the touching allowance).
        assertEquals(0.01 + Sweep.CONTACT, box.geometricFree(Boxes.FRONT, 0, 1, box.positions()), 1e-9)
        box.move(Boxes.FRONT, Axis.X, 1)
        // At 1 cm it touches the right end's tab: not a millimetre more (it may go back), and the right end cannot drop back.
        assertEquals(0..1, box.reach(Boxes.FRONT, Axis.X))
        assertTrue(box.geometricFree(Boxes.FRONT, 1, 1, box.positions()) <= Sweep.CONTACT + 1e-9)
        assertEquals(1.0, box.span(Boxes.FRONT, Axis.X).endInclusive, 0.05)
        assertEquals(1..1, box.reach(Boxes.RIGHT, Axis.Y))
        // Forced past contact, the front would sink into the tab by the full step.
        val forced = sweepPanel(box, box.positions(), Boxes.FRONT, 1, 2)
        assertEquals(0.01, forced, 1e-6)
        assertFalse(box.free(Boxes.FRONT, 1, 2, box.positions()))
        println("screenshot move: front 0→1 with the right end up 1 cm: allowed, free 10.4 mm, touching; 1→2: refused (would sink ${"%.1f".format(forced * 1000)} mm)")
    }

    /** Hints and the 演示一步 button move pieces the solver picks: from every reachable state that move is allowed and clear. */
    @Test
    fun hintsAndDemoStepsAreClearMoves() {
        for (p in listOf(Boxes.newBox(), Bronze.padlock())) {
            p.reset()
            val seen = hashSetOf(p.encode(p.positions()))
            val queue = ArrayDeque(listOf(p.encode(p.positions())))
            while (queue.isNotEmpty()) {
                val a = p.decode(queue.removeFirst())
                p.setPositions(a)
                val m = p.solution()?.firstOrNull()
                if (m != null && !p.solved) {
                    assertTrue(m.to in p.reach(m.piece, m.axis))
                    val to = m.to * p.panels[m.piece].direction
                    val step = if (to > a[m.piece]) 1 else -1
                    var at = a[m.piece]
                    while (at != to) {
                        assertTrue(sweepPanel(p, a.copyOf().also { it[m.piece] = at }, m.piece, at, at + step) <= MAX_PEN)
                        at += step
                    }
                }
                p.moves(p.encode(a)) { _, t -> if (seen.add(t)) queue.addLast(t) }
            }
            p.reset()
        }
    }

    // ---- 华容道 and 鲁班锁 -----------------------------------------------------------------------

    @Test
    fun klotskiEveryReachableMoveIsClear() {
        val cell = KlotskiBoards.CELL
        // Half sizes of each shape's model (x, z), from the GLB.
        val half = mapOf(
            (2 to 2) to ModelPoints.bounds(ModelPoints.node("klotski", "cao")),
            (2 to 1) to ModelPoints.bounds(ModelPoints.node("klotski", "guan")),
            (1 to 2) to ModelPoints.bounds(ModelPoints.node("klotski", "general_张飞")),
            (1 to 1) to ModelPoints.bounds(ModelPoints.node("klotski", "bing")),
        )
        val shapes = listOf(2 to 2, 2 to 1, 1 to 2, 1 to 1)
        fun blockBox(x: Double, y: Double, w: Int, h: Int): Aabb {
            val b = half.getValue(w to h)
            val cx = (x + w / 2.0 - Klotski.W / 2.0) * cell
            val cz = (y + h / 2.0 - Klotski.H / 2.0) * cell
            return Aabb(Vec3(cx + b.lo.x, b.lo.y, cz + b.lo.z), Vec3(cx + b.hi.x, b.hi.y, cz + b.hi.z))
        }
        // The rim (outside the field) as four walls.
        val hx = Klotski.W * cell / 2
        val hz = Klotski.H * cell / 2
        val rim = listOf(box(-hx - 0.05, -hx, 0.0, 0.03, -hz - 0.05, hz + 0.05), box(hx, hx + 0.05, 0.0, 0.03, -hz - 0.05, hz + 0.05),
            box(-hx, hx, 0.0, 0.03, -hz - 0.05, -hz), box(-hx, hx, 0.0, 0.03, hz, hz + 0.05))
        for (layout in KlotskiBoards.rounds) {
            val k = Klotski(layout)
            val start = k.boardCode()
            val seen = hashSetOf(start)
            val queue = ArrayDeque(listOf(start))
            var moves = 0
            var worst = Double.NEGATIVE_INFINITY
            while (queue.isNotEmpty()) {
                val s = queue.removeFirst()
                // Blocks of this board pattern.
                val blocks = ArrayList<Triple<Int, Int, Pair<Int, Int>>>()
                for (c in 0 until Klotski.W * Klotski.H) {
                    val code = ((s shr (3 * c)) and 7L).toInt()
                    if (code != 0) blocks += Triple(c % Klotski.W, c / Klotski.W, shapes[code - 1])
                }
                Klotski.shapeMoves(s) { m, t ->
                    moves++
                    val others = blocks.filter { !(it.first == m.from.x && it.second == m.from.y) }.map { blockBox(it.first.toDouble(), it.second.toDouble(), it.third.first, it.third.second) } + rim
                    val a = blockBox(m.from.x.toDouble(), m.from.y.toDouble(), m.w, m.h)
                    val dx = if (m.axis == Axis.X) m.delta else 0
                    val dz = if (m.axis == Axis.Z) m.delta else 0
                    val b = blockBox((m.from.x + dx).toDouble(), (m.from.y + dz).toDouble(), m.w, m.h)
                    val pen = Sweep.penetration(listOf(a.union(b)), others)
                    worst = maxOf(worst, pen)
                    assertTrue("${layout.id}: block at ${m.from} by ${m.delta} sinks ${pen * 1000} mm", pen <= MAX_PEN)
                    if (seen.add(t)) queue.addLast(t)
                }
            }
            println("华容道 ${layout.id}: ${seen.size} reachable boards, $moves moves, closest approach ${"%.2f".format(-worst * 1000)} mm apart")
        }
    }

    private fun cellBoxes(cells: List<Cell>, off: Cell): List<Aabb> {
        val c = Burrs.CELL
        return cells.map { q -> box((q.x + off.x) * c, (q.x + off.x + 1) * c, (q.y + off.y) * c, (q.y + off.y + 1) * c, (q.z + off.z) * c, (q.z + off.z + 1) * c) }
    }

    @Test
    fun burrMovesAreClear() {
        // The solution, then 120 random games of up to 80 moves (the whole state space is too big to list).
        val rnd = Random(1123)
        var moves = 0
        var worst = Double.NEGATIVE_INFINITY
        repeat(121) { game ->
            val burr = Burrs.lock()
            val plan = if (game == 0) burr.solution()!! else null
            var k = 0
            while (!burr.solved && k < 80) {
                val options = ArrayList<Move>()
                burr.moves(burr.state()) { m, _ -> options += m }
                if (options.isEmpty()) break
                val m = plan?.getOrNull(k) ?: options[rnd.nextInt(options.size)]
                k++
                val i = m.piece
                val off = Cell(burr.offset(i, Axis.X), burr.offset(i, Axis.Y), burr.offset(i, Axis.Z))
                val dest = when (m.axis) {
                    Axis.X -> Cell(m.to, off.y, off.z)
                    Axis.Y -> Cell(off.x, m.to, off.z)
                    Axis.Z -> Cell(off.x, off.y, m.to)
                }
                val others = burr.pieces.indices.filter { it != i && !burr.removed(it) }.flatMap { j ->
                    cellBoxes(burr.pieces[j].cells, Cell(burr.offset(j, Axis.X), burr.offset(j, Axis.Y), burr.offset(j, Axis.Z)))
                }
                val from = cellBoxes(burr.pieces[i].cells, off)
                val to = cellBoxes(burr.pieces[i].cells, dest)
                val pen = Sweep.penetration(from.zip(to) { a, b -> a.union(b) }, others)
                worst = maxOf(worst, pen)
                assertTrue("stick $i to $dest sinks ${pen * 1000} mm", pen <= MAX_PEN)
                moves++
                burr.move(i, m.axis, m.to)
            }
        }
        println("鲁班锁: $moves moves swept (the solution and 120 random games), deepest touch ${"%.2f".format(maxOf(0.0, worst) * 1000)} mm")
    }

    // ---- Before → after ------------------------------------------------------------------------

    /**
     * The 0.10.0 box and padlock (plain slabs and hidden pins; the padlock as its model was), audited the same way:
     * prints the before → after table for the changelog. Not an assertion on the old ones.
     */
    @Test
    fun beforeAfterTable() {
        val oldBoxParts = listOf(
            listOf(box(0.110, 0.122, 0.005, 0.147, -0.082, 0.082)),
            listOf(box(-0.110, 0.110, 0.005, 0.137, 0.070, 0.082)),
            listOf(box(-0.122, -0.110, 0.005, 0.147, -0.082, 0.082)),
            listOf(box(-0.110, 0.110, 0.005, 0.137, -0.082, -0.070)),
            listOf(box(-0.110, 0.110, 0.137, 0.147, -0.082, 0.082)),
        )
        val oldPins = listOf(
            Pin(Boxes.FRONT, 0..1, mapOf(Boxes.RIGHT to 1..2)),
            Pin(Boxes.RIGHT, 0..1, mapOf(Boxes.FRONT to 0..0)),
            Pin(Boxes.LEFT, 0..1, mapOf(Boxes.FRONT to 1..2, Boxes.BACK to 0..0)),
            Pin(Boxes.BACK, 0..1, mapOf(Boxes.LEFT to 1..2)),
            Pin(Boxes.RIGHT, 1..2, mapOf(Boxes.BACK to 1..2, Boxes.FRONT to 0..1)),
            Pin(Boxes.FRONT, 1..2, mapOf(Boxes.RIGHT to 2..2)),
            Pin(Boxes.LID, 0..1, mapOf(Boxes.FRONT to 2..2, Boxes.BACK to 1..2, Boxes.LEFT to 1..2)),
        )
        val oldPanels = listOf(
            BoxPanel("右端板", Axis.Y, 2, +1), BoxPanel("前板", Axis.X, 2, +1), BoxPanel("左端板", Axis.Y, 2, -1),
            BoxPanel("后板", Axis.X, 2, -1), BoxPanel("盖板", Axis.X, 8, -1),
        )
        // The old box stood on the turntable top (y = 0) with no plinth.
        val oldFixed = listOf(box(-0.109, 0.109, 0.005, 0.135, -0.069, 0.069), box(-0.2, 0.2, -0.03, 0.0, -0.2, 0.2))
        // Pins only (the old rules), with the shapes there to measure.
        val oldBox = PuzzleBox(oldPanels, oldPins, Boxes.LID, BoxShapes(oldBoxParts, oldFixed, Boxes::stepMeters))
        val before = auditPinsOnly(oldBox)
        val after = audit(Boxes.newBox(), "鲁班盒 (after)", assertClean = false)
        report("鲁班盒", oldPanels, before, after)

        // 0.10.0 padlock as modelled: plate on the face (overlapping the painted keyhole), key bit 16 mm tall, no hole
        // in the body (a painted keyhole), the ring hanging 2 mm into the turntable.
        val oldLockParts = listOf(
            listOf(box(-0.015, 0.015, 0.035, 0.065, 0.022, 0.026), box(-0.0102, 0.0102, 0.0398, 0.0602, 0.0253, 0.0277)),
            listOf(box(-0.0035, 0.0035, 0.0465, 0.0535, 0.04, 0.10), box(-0.0015, 0.0015, 0.039, 0.055, 0.04, 0.052), box(-0.0025, 0.0025, 0.0385, 0.0615, 0.0965, 0.1195)),
            listOf(box(0.05, 0.056, 0.03, 0.05, -0.008, 0.008)),
            listOf(box(-0.04, -0.03, 0.06, 0.135, -0.005, 0.005), box(0.03, 0.04, 0.06, 0.135, -0.005, 0.005), box(-0.04, 0.04, 0.13, 0.175, -0.005, 0.005)),
            listOf(box(-0.0492, -0.0208, -0.0022, 0.0262, 0.0218, 0.0262)),
        )
        val oldLockBody = listOf(box(-0.05, 0.05, 0.0, 0.08, -0.02, 0.02), box(-0.043, 0.043, 0.007, 0.073, 0.02, 0.0225),
            box(-0.008, 0.008, 0.042, 0.058, 0.0225, 0.0232), box(-0.017, 0.017, 0.014, 0.03, 0.0225, 0.0232), box(-0.2, 0.2, -0.03, 0.0, -0.2, 0.2))
        val oldLockPins = listOf(
            Pin(Bronze.KEY, 0..1, mapOf(Bronze.COVER to 2..2)),
            Pin(Bronze.COVER, 1..2, mapOf(Bronze.KEY to 0..0)),
            Pin(Bronze.KEY, 2..3, mapOf(Bronze.CATCH to 1..1)),
            Pin(Bronze.CATCH, 0..1, mapOf(Bronze.KEY to 2..2)),
            Pin(Bronze.SHACKLE, 0..1, mapOf(Bronze.KEY to 4..4)),
        )
        val oldStep = { i: Int -> if (i == Bronze.KEY) 0.015 else 0.01 }
        val oldLock = PuzzleBox(Bronze.padlockPanels, oldLockPins, Bronze.SHACKLE, BoxShapes(oldLockParts, oldLockBody, oldStep))
        val lockBefore = auditPinsOnly(oldLock)
        val lockAfter = audit(Bronze.padlock(), "花旗锁 (after)", assertClean = false)
        report("花旗锁", Bronze.padlockPanels, lockBefore, lockAfter)
        println("== before → after (worst overlap per move over every reachable state; — = the move does not exist)")
        table.forEach(::println)
    }

    /** Like [audit] but with geometry measured, not enforced (the old rules had pins only). */
    private fun auditPinsOnly(p: PuzzleBox): Map<MoveKey, Double> {
        val pinsOnly = PuzzleBox(p.panels, p.pins, p.lid, null)
        val worst = HashMap<MoveKey, Double>()
        val start = pinsOnly.encode(IntArray(p.panels.size))
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val a = pinsOnly.decode(queue.removeFirst())
            for (i in p.panels.indices) for (dir in intArrayOf(1, -1)) {
                val to = a[i] + dir
                if (to < 0 || to > p.panels[i].steps || !pinsOnly.free(i, a[i], to, a)) continue
                val key = MoveKey(i, a[i], to)
                worst[key] = maxOf(worst[key] ?: Double.NEGATIVE_INFINITY, sweepPanel(p, a, i, a[i], to))
                val t = pinsOnly.encode(a.copyOf().also { it[i] = to })
                if (seen.add(t)) queue.addLast(t)
            }
            // At rest too (the ring in the turntable): recorded as a move from a position to itself.
            for (i in p.panels.indices) {
                val key = MoveKey(i, a[i], a[i])
                worst[key] = maxOf(worst[key] ?: Double.NEGATIVE_INFINITY, Sweep.penetration(p.solid(i, a[i]), p.others(i, a)))
            }
        }
        return worst
    }

    private fun report(relic: String, panels: List<BoxPanel>, before: Map<MoveKey, Double>, after: Map<MoveKey, Double>) {
        fun mm(v: Double?) = if (v == null) "—" else "%.1f mm".format(maxOf(0.0, v) * 1000)
        val keys = (before.keys + after.keys).filter { it.from != it.to || (before[it] ?: 0.0) > MAX_PEN }.toSortedSet(compareBy({ it.part }, { it.from }, { it.to }))
        for (k in keys) {
            val b = before[k]
            val a = after[k]
            if ((b ?: 0.0) <= MAX_PEN && (a ?: 0.0) <= MAX_PEN && b != null && a != null) continue
            val move = if (k.from == k.to) "静止在 ${k.from}" else "${k.from}→${k.to}"
            table += "$relic | ${panels[k.part].name} | $move | ${mm(b)} → ${mm(a)}"
        }
    }
}
