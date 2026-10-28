package com.vrgames.lubanbox.core

import com.vrgames.lubanbox.core.Klotski.Companion.BING
import com.vrgames.lubanbox.core.Klotski.Companion.CAO
import com.vrgames.lubanbox.core.Klotski.Companion.GUAN
import com.vrgames.lubanbox.core.Klotski.Companion.HUANG
import com.vrgames.lubanbox.core.Klotski.Companion.MA
import com.vrgames.lubanbox.core.Klotski.Companion.ZHANG
import com.vrgames.lubanbox.core.Klotski.Companion.ZHAO

/**
 * The three boards of 华容道, found by KlotskiLab among random placements of the classic ten blocks:
 * 15, 20 and 30 straight moves at best (the classic 横刀立马 needs far more).
 */
object KlotskiBoards {
    /** Edge of one board cell, in meters (the board is 20 × 25 cm). */
    const val CELL = 0.05

    val rounds = listOf(
        KlotskiLayout(
            "k1", "兵临",
            listOf(
                CAO to Cell(2, 1), GUAN to Cell(2, 0), ZHANG to Cell(0, 3), ZHAO to Cell(3, 3), MA to Cell(0, 0),
                HUANG to Cell(2, 3), BING to Cell(1, 4), BING to Cell(0, 2), BING to Cell(1, 0), BING to Cell(1, 3),
            ),
        ),
        KlotskiLayout(
            "k2", "围困",
            listOf(
                CAO to Cell(0, 1), GUAN to Cell(0, 0), ZHANG to Cell(2, 1), ZHAO to Cell(1, 3), MA to Cell(0, 3),
                HUANG to Cell(3, 3), BING to Cell(2, 3), BING to Cell(3, 2), BING to Cell(2, 4), BING to Cell(3, 0),
            ),
        ),
        KlotskiLayout(
            "k3", "突围",
            listOf(
                CAO to Cell(1, 1), GUAN to Cell(2, 0), ZHANG to Cell(3, 2), ZHAO to Cell(0, 1), MA to Cell(0, 3),
                HUANG to Cell(1, 3), BING to Cell(3, 1), BING to Cell(3, 4), BING to Cell(2, 3), BING to Cell(1, 0),
            ),
        ),
    )
}
