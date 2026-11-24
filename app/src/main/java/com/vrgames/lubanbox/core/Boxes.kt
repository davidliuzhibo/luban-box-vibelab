package com.vrgames.lubanbox.core

/**
 * The 鲁班盒 of chapter one. Object frame: +x right, +y up, +z toward the player; the box is 24 cm wide, 15.7 cm
 * tall and 16.4 cm deep, standing on a low plinth. Each panel step is 1 cm (the lid's are 2.5 cm).
 *
 * v0.10.1 (PICO device test 2026-11-23): the panels used to be plain slabs held by hidden pins, and the front panel
 * could slide right while the right end panel, lifted only 1 cm, still stood across its way: it slid visibly into
 * it. Now every panel is held by what the player can see — tabs and notches in the corners, cut like a finger joint
 * — and moves only as far as its real shape is clear ([PuzzleBox] with [shapes]; no pins). The models are built
 * from exactly these boxes (tools/make_box.py via ModelDataExport).
 *
 * Opening order (8 moves, the only way):
 * 1. Right end up 1 cm: its corner tab over the front panel's top-right notch lifts clear by 1 cm.
 * 2. Front right 1 cm, into the room that leaves; it stops against the tab still standing in its top corner, and
 *    now sits under the tab, so the right end cannot drop back.
 * 3. Left end down 1 cm: the front panel's foot under its front corner has moved away.
 * 4. Back left 1 cm: the left end's small tab in the back panel's bottom-left notch has dropped out of the way.
 * 5. Right end up to 2 cm: the back panel's finger in its back corner has moved away (it could only rise into the
 *    1 cm gap under that finger before).
 * 6. Front right to 2 cm: the right end's tab is now clear of the front panel's top.
 * 7. Left end down to 2 cm: the front panel has pulled away from under the left end's tongue.
 * 8. The lid slides off to the left over the lowered left end (the end panels stand 1 cm above the lid).
 */
object Boxes {
    const val RIGHT = 0
    const val FRONT = 1
    const val LEFT = 2
    const val BACK = 3
    const val LID = 4

    val panels = listOf(
        BoxPanel("右端板", Axis.Y, 2, +1),
        BoxPanel("前板", Axis.X, 2, +1),
        BoxPanel("左端板", Axis.Y, 2, -1),
        BoxPanel("后板", Axis.X, 1, -1),
        BoxPanel("盖板", Axis.X, 8, -1),
    )

    // Planes of the box (meters). End panels 1 cm thick (x), side panels 1.2 cm (z).
    private const val XI = 0.110
    private const val XO = 0.120
    private const val ZI = 0.070
    private const val ZO = 0.082
    private const val Y0 = 0.005
    private const val SIDE_TOP = 0.137
    private const val LID_TOP = 0.147
    private const val END_TOP = 0.157

    /** Height of the plinth under the box (the lowered left end stays 3 mm over the turntable). */
    const val PLINTH = 0.023

    /** How high the box's frame stands over the turntable top: the plinth's foot is on the turntable. */
    const val LIFT = PLINTH - Y0

    private fun front(x0: Double, x1: Double, y0: Double, y1: Double) = box(x0, x1, y0, y1, ZI, ZO)
    private fun back(x0: Double, x1: Double, y0: Double, y1: Double) = box(x0, x1, y0, y1, -ZO, -ZI)

    /** Each panel's boxes where it sits closed, in panel order. */
    val parts: List<List<Aabb>> = listOf(
        // Right end: slab, front corner from 11.7 cm up (its tab in the front panel's top corner) with a key over the
        // front panel's top edge that runs up past the lid's front corner, and the back corner in two parts with a
        // 1 cm gap under the back panel's finger.
        listOf(
            box(XI, XO, Y0, END_TOP, -ZI, ZI),
            front(XI, XO, 0.117, END_TOP),
            front(0.100, XI, 0.127, END_TOP),
            back(XI, XO, Y0, 0.060),
            back(XI, XO, 0.080, END_TOP),
        ),
        // Front: runs over the right front corner, notched at its top right for the right end's tab and key, with a
        // notch at its left end round the left end's tongue, and a foot under the left end's front corner.
        listOf(
            front(-XI, XO, Y0, 0.055),
            front(-0.090, XO, 0.055, 0.075),
            front(-XI, XO, 0.075, 0.117),
            front(-XI, XI, 0.117, 0.127),
            front(-XI, 0.100, 0.127, SIDE_TOP),
            front(-XO, -XI, Y0, 0.015),
        ),
        // Left end: slab, front corner above the front panel's foot, a tongue into the front panel's notch (with 1 cm
        // to drop before it meets the notch's floor), and a small tab in the back panel's bottom-left notch.
        listOf(
            box(-XO, -XI, Y0, END_TOP, -ZI, ZI),
            front(-XO, -XI, 0.015, END_TOP),
            front(-XI, -0.090, 0.065, 0.075),
            back(-XO, -XI, Y0, 0.015),
        ),
        // Back: runs over the left back corner (above the left end's tab) and puts a finger into the right end's
        // back corner.
        listOf(
            back(-XI, XI, Y0, SIDE_TOP),
            back(-XO, -XI, 0.015, SIDE_TOP),
            back(XI, XO, 0.070, 0.080),
        ),
        // Lid between the end panels, with an ear over the left back corner, and its front right corner left to the
        // right end's key (which lifts out of it).
        listOf(
            box(-XI, 0.100, SIDE_TOP, LID_TOP, -ZO, ZO),
            box(0.100, XI, SIDE_TOP, LID_TOP, -ZO, ZI),
            box(-XO, -XI, SIDE_TOP, LID_TOP, -ZO, -ZI),
        ),
    )

    /** The box body inside the panels (hollow, open at the top) and the plinth it stands on. */
    val body = box(-0.109, 0.109, Y0, 0.135, -0.069, 0.069)
    val plinth = box(-0.100, 0.100, Y0 - PLINTH, Y0, -0.060, 0.060)

    /** The turntable under the plinth (its top at the plinth's foot), as a box. */
    val turntable = box(-0.2, 0.2, Y0 - PLINTH - 0.03, Y0 - PLINTH, -0.2, 0.2)

    /** Size of one step of each panel, in meters (the lid slides farther per step). */
    fun stepMeters(panel: Int) = if (panel == LID) 0.025 else 0.01

    val shapes = BoxShapes(parts, listOf(body, plinth, turntable), ::stepMeters)

    fun newBox() = PuzzleBox(panels, emptyList(), LID, shapes)

    /** The panels' outer bounds (for the pinch boxes). */
    fun bounds(i: Int): Aabb = parts[i].reduce { a, b -> a.union(b) }
}
