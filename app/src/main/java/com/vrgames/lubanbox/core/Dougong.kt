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
        // v0.10.1: 耍头 (along z) and 柱头枋 (along x) cross over this block, so it has a cross seat (a 交互斗). With a
        // one-way 散斗 here, whichever ran across the seat sank 8.7 mm into its ears.
        Slot("齐心斗", JIAO_HU_DOU, v(0.0, Y1_TOP, 0.0), needs = setOf("泥道栱")),
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
    val parts = listOf(LU_DOU, HUA_GONG, NI_DAO_GONG, SAN_DOU, SAN_DOU, SAN_DOU, SAN_DOU, JIAO_HU_DOU, JIAO_HU_DOU, LING_GONG, SHUA_TOU, ZHU_TOU_FANG, LIAO_YAN_FANG)

    /**
     * v0.10: turns that leave a part looking the same, from the shapes in tools/make_dougong.py. 栌斗 and
     * 交互斗 have a cross seat (any quarter turn; v0.10.1: the middle block 齐心斗 is one too); 散斗 a seat one way (half turns); 泥道栱, 令栱 and the two
     * 枋 are the same end to end (half turns); 华栱 (its halving off-centre) and 耍头 (its beak) only one way.
     */
    val symmetry = mapOf(
        LU_DOU to 90, JIAO_HU_DOU to 90, SAN_DOU to 180,
        NI_DAO_GONG to 180, LING_GONG to 180, ZHU_TOU_FANG to 180, LIAO_YAN_FANG to 180,
        HUA_GONG to 360, SHUA_TOU to 360,
    )

    fun newAssembly() = Assembly(slots, parts, symmetry)

    /** Plan size of each part type lying at yaw 0 (x, z; meters), as tools/make_dougong.py models them. */
    fun footprint(type: String): Pair<Double, Double> = when (type) {
        LU_DOU -> 0.096 to 0.096
        JIAO_HU_DOU -> 0.054 to 0.054
        SAN_DOU -> 0.048 to 0.048
        HUA_GONG -> 0.03 to (HUA_GONG_FRONT - HUA_GONG_BACK)
        SHUA_TOU -> 0.03 to 0.2
        NI_DAO_GONG -> 0.22 to 0.03
        LING_GONG -> 0.2 to 0.03
        else -> 0.24 to 0.03
    }

    /**
     * Where the loose parts lie on the bench (in [parts] order): place (x, z in the relic's frame) and yaw.
     * v0.10: the four long arms that go in crosswise (泥道栱, 令栱, 柱头枋, 撩檐枋) lie lengthwise, so each
     * must be turned once, and each has room to turn where it lies: nothing else lies where it will
     * point after the turn. The blocks stand in a row at the right end. Everything stays off the
     * turntable (radius 0.2 m) and clear of the menu tab at the front left.
     */
    val tray: List<Pair<Vec3, Double>> = listOf(
        v(-0.44, 0.0, -0.08) to 0.0, // 栌斗
        v(-0.31, 0.0, 0.03) to 0.0, // 华栱
        v(-0.36, 0.0, -0.17) to 90.0, // 泥道栱
        v(0.44, 0.0, 0.05) to 0.0, v(0.44, 0.0, 0.12) to 0.0,
        v(0.44, 0.0, 0.19) to 0.0, v(0.44, 0.0, 0.26) to 0.0, // 散斗 ×4
        v(0.44, 0.0, -0.09) to 0.0, v(0.44, 0.0, -0.02) to 0.0, // 交互斗 ×2 (v0.10.1: one sits under the crossing at the middle)
        v(0.245, 0.0, 0.18) to 90.0, // 令栱
        v(0.31, 0.0, 0.025) to 0.0, // 耍头
        v(0.36, 0.0, -0.17) to 90.0, // 柱头枋
        v(-0.245, 0.0, 0.18) to 90.0, // 撩檐枋
    )
}
