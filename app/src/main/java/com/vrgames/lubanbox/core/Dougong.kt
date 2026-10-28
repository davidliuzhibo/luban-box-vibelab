package com.vrgames.lubanbox.core

/**
 * One 斗拱 (bracket set) in the Song style of 《营造法式》, simplified to 13 parts: the big block on
 * the column (栌斗), two crossed arms in it (华栱 toward you, 泥道栱 across), small blocks (斗) on
 * the arm ends, a second arm (令栱) out front, the beam head (耍头) and two purlins (枋) on top.
 *
 * Object frame: meters, +y up, +z toward the player (the outward side of the eave). Each part's
 * origin is the middle of its underside, so a slot's [Slot.pos] is where that underside sits.
 * The arm section (材) is 3 cm wide and 4.5 cm tall, the 10:15 of 《营造法式》.
 * tools/make_dougong.py models the parts from the same numbers.
 */
object Dougong {
    const val LU_DOU = "栌斗"
    const val HUA_GONG = "华栱"
    const val NI_DAO_GONG = "泥道栱"
    const val SAN_DOU = "散斗"
    const val JIAO_HU_DOU = "交互斗"
    const val LING_GONG = "令栱"
    const val SHUA_TOU = "耍头"
    const val ZHU_TOU_FANG = "柱头枋"
    const val LIAO_YAN_FANG = "撩檐枋"

    /** Height of the arms and beams (材广) and of a small block (斗). */
    const val ARM_H = 0.045
    const val DOU_H = 0.03
    /** Depth of the seat cut in a block's top for the arm it carries (斗口). */
    const val SEAT = 0.015
    /** 栌斗: its seat holds the two crossed arms. */
    const val LU_DOU_H = 0.06
    const val LU_DOU_SEAT = 0.024

    /** Arm levels: crossed arms in the 栌斗, then one block plus one arm up per layer. */
    private const val Y1 = LU_DOU_H - LU_DOU_SEAT
    private const val Y1_TOP = Y1 + ARM_H
    private const val Y2 = Y1_TOP + DOU_H - SEAT
    private const val Y2_TOP = Y2 + ARM_H
    private const val Y3 = Y2_TOP + DOU_H - SEAT

    /** Where the outer arm (令栱) and its row of blocks stand, out front. */
    const val FRONT_Z = 0.105
    /** 华栱 runs from behind the column to just past the front row. */
    const val HUA_GONG_BACK = -0.06
    const val HUA_GONG_FRONT = 0.135

    val slots = listOf(
        Slot("栌斗", LU_DOU, v(0.0, 0.0, 0.0)),
        Slot("华栱", HUA_GONG, v(0.0, Y1, (HUA_GONG_BACK + HUA_GONG_FRONT) / 2), needs = setOf("栌斗")),
        // Crossed with 华栱 by halving: it drops in on top of it.
        Slot("泥道栱", NI_DAO_GONG, v(0.0, Y1, 0.0), needs = setOf("华栱")),
        Slot("齐心斗", SAN_DOU, v(0.0, Y1_TOP, 0.0), yaw = 90.0, needs = setOf("泥道栱")),
        Slot("左散斗", SAN_DOU, v(-0.09, Y1_TOP, 0.0), needs = setOf("泥道栱")),
        Slot("右散斗", SAN_DOU, v(0.09, Y1_TOP, 0.0), needs = setOf("泥道栱")),
        Slot("交互斗", JIAO_HU_DOU, v(0.0, Y1_TOP, FRONT_Z), needs = setOf("华栱")),
        Slot("令栱", LING_GONG, v(0.0, Y2, FRONT_Z), needs = setOf("交互斗")),
        // Crossed with 令栱 (halving) and resting on 齐心斗.
        Slot("耍头", SHUA_TOU, v(0.0, Y2, 0.04), needs = setOf("令栱", "齐心斗")),
        Slot("前左散斗", SAN_DOU, v(-0.08, Y2_TOP, FRONT_Z), needs = setOf("令栱")),
        Slot("前右散斗", SAN_DOU, v(0.08, Y2_TOP, FRONT_Z), needs = setOf("令栱")),
        // On the two side blocks, crossed with 耍头 over the column.
        Slot("柱头枋", ZHU_TOU_FANG, v(0.0, Y2, 0.0), needs = setOf("左散斗", "右散斗", "耍头")),
        Slot("撩檐枋", LIAO_YAN_FANG, v(0.0, Y3, FRONT_Z), needs = setOf("前左散斗", "前右散斗")),
    )

    /** Loose parts on the bench, in the order they are laid out. */
    val parts = listOf(LU_DOU, HUA_GONG, NI_DAO_GONG, SAN_DOU, SAN_DOU, SAN_DOU, SAN_DOU, SAN_DOU, JIAO_HU_DOU, LING_GONG, SHUA_TOU, ZHU_TOU_FANG, LIAO_YAN_FANG)

    fun newAssembly() = Assembly(slots, parts)
}
