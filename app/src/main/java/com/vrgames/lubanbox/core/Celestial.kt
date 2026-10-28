package com.vrgames.lubanbox.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * Chapter three, 天工: the 指南车, the 浑天仪 and the 水运仪象台, and the closing 总机关匣.
 * Object frame: meters, +y up, +z toward the player.
 */
object Celestial {
    // ---- 指南车: gear it so the figure keeps pointing south ------------------------------------

    const val STAND_GEAR = "立轮"
    const val SMALL_GEAR = "小平轮"
    const val BIG_GEAR = "大平轮"
    const val MID_GEAR = "中平轮"
    const val FIGURE = "木人"

    /**
     * The chariot's wheels are fixed; inside, two upright gears on the wheel axles drive two small
     * horizontal gears, which mesh with the big gear under the figure. A middle-sized gear on the
     * bench fits nowhere (it would turn the figure at the wrong rate).
     */
    val chariotSlots = listOf(
        Slot("左立轮", STAND_GEAR, v(-0.07, 0.04, 0.0), yaw = 90.0),
        Slot("右立轮", STAND_GEAR, v(0.07, 0.04, 0.0), yaw = 90.0),
        Slot("左小平轮", SMALL_GEAR, v(-0.07, 0.075, 0.0), needs = setOf("左立轮")),
        Slot("右小平轮", SMALL_GEAR, v(0.07, 0.075, 0.0), needs = setOf("右立轮")),
        Slot("大平轮", BIG_GEAR, v(0.0, 0.075, 0.0), needs = setOf("左小平轮", "右小平轮")),
        Slot("木人", FIGURE, v(0.0, 0.1, 0.0), needs = setOf("大平轮")),
    )
    val chariotParts = listOf(STAND_GEAR, STAND_GEAR, SMALL_GEAR, SMALL_GEAR, MID_GEAR, BIG_GEAR, FIGURE)

    fun chariot() = Assembly(chariotSlots, chariotParts)

    // ---- 浑天仪: aim the sighting tube at a star ----------------------------------------------

    /** Polar axis tilt (the latitude of 开封, the Northern Song capital, about 35°). */
    const val LATITUDE = 35.0
    const val HOUR_STOPS = 12
    const val DECL_STOPS = 9
    val SPHERE_CENTER = v(0.0, 0.14, 0.0)

    /**
     * Where the sighting tube points: the ring (四游仪) turns about the polar axis in 30° stops, the
     * tube tilts in it from -60° to +60° of declination in 15° stops.
     */
    fun tubeDirection(hour: Int, decl: Int): Vec3 {
        val h = Math.toRadians(hour * 30.0)
        val d = Math.toRadians((decl - 4) * 15.0)
        // Equatorial frame: pole along +y, hour angle 0 toward the player (+z).
        val e = Vec3(cos(d) * sin(h), sin(d), cos(d) * cos(h))
        // Tip the pole back (away from the player) by 90° - latitude.
        val t = Math.toRadians(90.0 - LATITUDE)
        return Vec3(e.x, e.y * cos(t) + e.z * sin(t), -e.y * sin(t) + e.z * cos(t))
    }

    /** 织女 (Vega), where the tube must point: hour stop 8, declination stop 7 (+45°). */
    val STAR: Vec3 get() = tubeDirection(8, 7)

    fun armillary(): RotaryPuzzle = RotaryPuzzle(
        listOf(
            Dial("四游环", Axis.Y, HOUR_STOPS, SPHERE_CENTER),
            Dial("窥管", Axis.X, DECL_STOPS, SPHERE_CENTER, wraps = false, stepDeg = -15.0, zeroStop = 4),
        ),
        intArrayOf(2, 4),
    ) { a -> tubeDirection(a[0], a[1]).dot(STAR) > cos(Math.toRadians(4.0)) }

    /** How close the tube is to the star (1 = on it), for the brightening sight. */
    fun closeness(hour: Int, decl: Int) = ((tubeDirection(hour, decl).dot(STAR) + 1) / 2).coerceIn(0.0, 1.0)

    // ---- 水运仪象台: the escapement, in building order -----------------------------------------

    const val TANK = "天池"
    const val WHEEL = "枢轮"
    const val SUMP = "退水壶"
    const val BEAM = "天衡"
    const val GATE = "天关"
    const val LOCK_L = "左天锁"
    const val LOCK_R = "右天锁"
    const val GLOBE = "浑象"

    /**
     * A tabletop model of 苏颂's water-driven clock tower: the scoop wheel on its frame, the sump
     * under it, the tank above it, then the 天衡 balance with its 天关 stop and the two 天锁 pawls
     * that let the wheel go one scoop at a time, and the celestial globe it turns.
     */
    val clockSlots = listOf(
        Slot("退水壶", SUMP, v(0.0, 0.0, 0.0)),
        // The wheel's origin is its hub; the others' the middle of their underside.
        Slot("枢轮", WHEEL, v(0.0, 0.13, 0.0), needs = setOf("退水壶")),
        Slot("天池", TANK, v(-0.12, 0.23, 0.0), needs = setOf("枢轮")),
        Slot("天衡", BEAM, v(0.0, 0.25, -0.05), needs = setOf("枢轮")),
        Slot("天关", GATE, v(0.08, 0.23, 0.0), needs = setOf("天衡")),
        Slot("左天锁", LOCK_L, v(-0.04, 0.2, 0.05), needs = setOf("天关")),
        Slot("右天锁", LOCK_R, v(0.04, 0.2, 0.05), needs = setOf("天关")),
        Slot("浑象", GLOBE, v(0.0, 0.32, -0.05), needs = setOf("左天锁", "右天锁", "天池")),
    )
    val clockParts = listOf(SUMP, WHEEL, TANK, BEAM, GATE, LOCK_L, LOCK_R, GLOBE)

    fun waterClock() = Assembly(clockSlots, clockParts)

    // ---- 总机关匣: the three chapter treasures open it ---------------------------------------

    const val RULER = "鲁班尺"
    const val SEAL = "铜印"
    const val STAR_CHART = "星图"

    val chestSlots = listOf(
        // Recesses in the chest's top (12 cm up, 4 mm deep): the ruler across the front, the
        // seal and the star chart behind it.
        Slot("尺槽", RULER, v(0.0, 0.116, 0.05)),
        Slot("印槽", SEAL, v(-0.08, 0.116, -0.045), needs = setOf("尺槽")),
        Slot("图槽", STAR_CHART, v(0.08, 0.116, -0.045), needs = setOf("尺槽")),
    )
    val chestParts = listOf(RULER, SEAL, STAR_CHART)

    fun chest() = Assembly(chestSlots, chestParts)
}
