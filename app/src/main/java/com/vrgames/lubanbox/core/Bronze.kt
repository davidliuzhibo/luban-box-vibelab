package com.vrgames.lubanbox.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chapter two, 铜作: the 文字锁, the 花旗锁, the 被中香炉 and the 透光镜. Object frame as everywhere:
 * meters, +y up, +z toward the player.
 */
object Bronze {
    // ---- 文字锁: four rings of characters on one shaft ------------------------------------------

    /** Each ring's six faces, in turning order; the word to set is 天工开物. */
    val RING_CHARS = listOf("天地玄黄宇宙", "工巧匠心木石", "开合机关金玉", "物器象形春秋")
    const val WORD = "天工开物"

    /**
     * Rings sit on a shaft along x; the face turned toward the player is read through the window.
     * The rings are geared: turning the second also turns the third one stop, and the third turns
     * the fourth one stop back, and the fourth turns the first, so each must be set in the right
     * order. The start was chosen by a search over all 1296 positions: 9 turns at best, all four
     * rings involved.
     */
    fun wordLock(): RotaryPuzzle {
        val dials = (0 until 4).map { i -> Dial("第${i + 1}环", Axis.X, 6, Vec3(-0.0525 + 0.035 * i, 0.075, 0.0)) }
        val links = Array(4) { IntArray(4) }
        links[1][2] = 1
        links[2][3] = -1
        links[3][0] = 1
        val target = IntArray(4) { i -> RING_CHARS[i].indexOf(WORD[i]) }
        return RotaryPuzzle(dials, WORD_LOCK_START.copyOf(), links) { a -> a.contentEquals(target) }
    }

    val WORD_LOCK_START = intArrayOf(2, 3, 1, 1)

    // ---- 花旗锁: a padlock with a hidden keyhole ----------------------------------------------

    const val COVER = 0
    const val KEY = 1
    const val CATCH = 2
    const val SHACKLE = 3
    const val RING = 4

    /**
     * A brass padlock whose keyhole hides behind a sliding plate. The key goes in only once the
     * plate is down; halfway in it stops until the catch on the side is pressed; fully in, it
     * frees the shackle. The hanging ring swings but does nothing.
     */
    val padlockPanels = listOf(
        BoxPanel("饰片", Axis.Y, 2, -1),
        BoxPanel("钥匙", Axis.Z, 4, -1),
        BoxPanel("侧簧", Axis.X, 1, -1),
        BoxPanel("锁梁", Axis.Y, 3, +1),
        BoxPanel("铜环", Axis.Y, 1, -1),
    )
    val padlockPins = listOf(
        Pin(KEY, 0..1, mapOf(COVER to 2..2)),
        Pin(COVER, 1..2, mapOf(KEY to 0..0)),
        Pin(KEY, 2..3, mapOf(CATCH to 1..1)),
        // The catch only engages the key's notch when the key is halfway in.
        Pin(CATCH, 0..1, mapOf(KEY to 2..2)),
        Pin(SHACKLE, 0..1, mapOf(KEY to 4..4)),
    )

    fun padlock() = PuzzleBox(padlockPanels, padlockPins, SHACKLE)

    /** Panel boxes (size, center), as [Boxes.panelBoxes]; the lock body is 10 × 8 × 4 cm. */
    val padlockBoxes: List<Pair<Vec3, Vec3>> = listOf(
        Vec3(0.03, 0.03, 0.004) to Vec3(0.0, 0.05, 0.022),
        Vec3(0.012, 0.02, 0.07) to Vec3(0.0, 0.05, 0.075),
        Vec3(0.006, 0.02, 0.016) to Vec3(0.053, 0.04, 0.0),
        Vec3(0.08, 0.06, 0.012) to Vec3(0.0, 0.11, 0.0),
        Vec3(0.03, 0.03, 0.006) to Vec3(-0.035, 0.012, 0.024),
    )
    val padlockBody = Vec3(0.1, 0.08, 0.04) to Vec3(0.0, 0.04, 0.0)

    /** Step size of each part (meters): the key slides 1.5 cm a step, the others 1 cm. */
    fun padlockStep(i: Int) = if (i == KEY) 0.015 else 0.01

    // ---- 被中香炉: a gimbal censer, put together from the outside in ----------------------------

    const val HEMI_LOW = "下半球"
    const val RING_OUT = "外环"
    const val RING_IN = "内环"
    const val BOWL = "香盂"
    const val HEMI_HIGH = "上半球"
    const val CENSER_R = 0.07

    /** The censer sits on a little stand; parts go in bottom-up, the inner ring crosswise. */
    val censerSlots = listOf(
        Slot("下半球", HEMI_LOW, v(0.0, 0.0, 0.0)),
        Slot("外环", RING_OUT, v(0.0, CENSER_R, 0.0), needs = setOf("下半球")),
        Slot("内环", RING_IN, v(0.0, CENSER_R, 0.0), yaw = 90.0, needs = setOf("外环")),
        Slot("香盂", BOWL, v(0.0, CENSER_R, 0.0), needs = setOf("内环")),
        Slot("上半球", HEMI_HIGH, v(0.0, CENSER_R, 0.0), needs = setOf("香盂")),
    )
    val censerParts = listOf(HEMI_LOW, RING_OUT, RING_IN, BOWL, HEMI_HIGH)

    fun censer() = Assembly(censerSlots, censerParts)

    // ---- 透光镜: aim the reflection at the screen --------------------------------------------

    /** Mirror face center, the lamp, and the paper screen (center, half-width, half-height). */
    val MIRROR = v(0.0, 0.12, 0.0)
    val LAMP = v(-0.32, 0.26, -0.06)
    /** Behind and right of the mirror, where only one aim (镜架 stop 7, 镜面 stop 6) lands the light. */
    val SCREEN = v(0.09, 0.222, -0.29)
    const val SCREEN_HALF_W = 0.07
    const val SCREEN_HALF_H = 0.045
    /** Screen faces the mirror: its normal points from the screen toward it. */
    val SCREEN_NORMAL: Vec3 get() = (MIRROR - SCREEN).let { Vec3(it.x, 0.0, it.z) }.normalized()

    const val YAW_STOPS = 12
    const val TILT_STOPS = 7

    /** Mirror face normal for a yaw stop (30° each about +y) and a tilt stop (10° each, 3 = upright). */
    fun mirrorNormal(yaw: Int, tilt: Int): Vec3 {
        val p = Math.toRadians((tilt - 3) * 10.0)
        val y = Math.toRadians(yaw * 30.0)
        // Start facing the player (+z), tip back by p, then turn by y about +y.
        val n0 = Vec3(0.0, sin(p), cos(p))
        return Vec3(n0.x * cos(y) + n0.z * sin(y), n0.y, -n0.x * sin(y) + n0.z * cos(y))
    }

    /** Where the reflected lamp light meets the screen's plane, or null if it goes away from it. */
    fun spot(yaw: Int, tilt: Int): Vec3? {
        val n = mirrorNormal(yaw, tilt)
        val d = (MIRROR - LAMP).normalized()
        if (d.dot(n) >= 0) return null
        val r = d - n * (2 * d.dot(n))
        val sn = SCREEN_NORMAL
        val denom = r.dot(sn)
        if (denom >= -1e-6) return null
        val t = (SCREEN - MIRROR).dot(sn) / denom
        if (t <= 0) return null
        return MIRROR + r * t
    }

    /** Whether the reflection lands on the screen. */
    fun onScreen(p: Vec3?): Boolean {
        if (p == null) return false
        val right = Vec3(0.0, 1.0, 0.0).cross(SCREEN_NORMAL).normalized()
        val off = p - SCREEN
        return abs(off.dot(right)) <= SCREEN_HALF_W && abs(off.y) <= SCREEN_HALF_H
    }

    fun mirror(): RotaryPuzzle = RotaryPuzzle(
        listOf(
            Dial("镜架", Axis.Y, YAW_STOPS, MIRROR),
            // Tipping the face up is a negative turn about +x.
            Dial("镜面", Axis.X, TILT_STOPS, MIRROR, wraps = false, stepDeg = -10.0, zeroStop = 3),
        ),
        intArrayOf(0, 3),
    ) { a -> onScreen(spot(a[0], a[1])) }
}
