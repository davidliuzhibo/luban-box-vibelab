package com.vrgames.lubanbox.core

/**
 * The 鲁班盒 of chapter one. Object frame: +x right, +y up, +z toward the player; the box is 24 cm
 * wide, 14 cm tall and 16 cm deep. Each panel step is 1 cm (the lid's are 2.5 cm).
 *
 * Opening order (7 moves): right end up, front right, left end down, back left, right end up again,
 * front right again, then the lid slides off to the left. Each panel's hidden pin is released by
 * the panel before it, and a panel cannot slide back while the next one sits in its groove.
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
        BoxPanel("后板", Axis.X, 2, -1),
        BoxPanel("盖板", Axis.X, 8, -1),
    )

    val pins = listOf(
        // Front moves first once the right end is up; the right end then cannot drop back.
        Pin(FRONT, 0..1, mapOf(RIGHT to 1..2)),
        Pin(RIGHT, 0..1, mapOf(FRONT to 0..0)),
        Pin(LEFT, 0..1, mapOf(FRONT to 1..2, BACK to 0..0)),
        Pin(BACK, 0..1, mapOf(LEFT to 1..2)),
        Pin(RIGHT, 1..2, mapOf(BACK to 1..2, FRONT to 0..1)),
        Pin(FRONT, 1..2, mapOf(RIGHT to 2..2)),
        // The lid is held at three edges until front, back and left end have all moved.
        Pin(LID, 0..1, mapOf(FRONT to 2..2, BACK to 1..2, LEFT to 1..2)),
    )

    fun newBox() = PuzzleBox(panels, pins, LID)

    /**
     * Each panel as a box (size, center; meters, object frame, panels at step 0), for the model
     * (tools/make_box.py reads these) and for pinching. The end panels stand taller than the sides
     * and the lid sits between them, so the lid can only slide out once the left end has dropped.
     */
    val panelBoxes: List<Pair<Vec3, Vec3>> = listOf(
        Vec3(0.012, 0.142, 0.164) to Vec3(0.116, 0.076, 0.0),
        Vec3(0.22, 0.132, 0.012) to Vec3(0.0, 0.071, 0.076),
        Vec3(0.012, 0.142, 0.164) to Vec3(-0.116, 0.076, 0.0),
        Vec3(0.22, 0.132, 0.012) to Vec3(0.0, 0.071, -0.076),
        Vec3(0.22, 0.01, 0.164) to Vec3(0.0, 0.142, 0.0),
    )

    /** The box body inside the panels (hollow, open at the top). */
    val body = Vec3(0.218, 0.13, 0.138) to Vec3(0.0, 0.07, 0.0)

    /** Size of one step of each panel, in meters (the lid slides farther per step). */
    fun stepMeters(panel: Int) = if (panel == LID) 0.025 else 0.01
}
