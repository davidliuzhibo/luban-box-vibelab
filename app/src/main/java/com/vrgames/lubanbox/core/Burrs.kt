package com.vrgames.lubanbox.core

/**
 * The 鲁班锁 of chapter one, found by BurrLab: see [Burr.fromCode] for the code format. Its sticks
 * are modeled from the same cells (tools/make_burr.py).
 */
object Burrs {
    /** One character per contested core cell: the stick that keeps it, '.' for a hole. */
    const val CODE = "00000.0000004400115511521.3.11.1222224223353533344445555"

    /** Edge of one cell, in meters: the sticks are 5 cm square and 20 cm long. */
    const val CELL = 0.025

    fun lock(): Burr = Burr.fromCode(CODE)
}
