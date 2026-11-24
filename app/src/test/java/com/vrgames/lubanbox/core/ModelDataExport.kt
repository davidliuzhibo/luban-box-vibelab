package com.vrgames.lubanbox.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Writes the shapes the rules use, for the Blender scripts (run with -Dlab=true): the burr's
 * sticks cell by cell, and the box's panels. The models are then exactly what the rules collide.
 */
class ModelDataExport {
    @Test
    fun export() {
        assumeTrue(System.getProperty("lab") == "true")
        val dir = File(System.getProperty("user.dir"), "../../tools/data").canonicalFile.apply { mkdirs() }
        val burr = Burrs.lock()
        File(dir, "burr.json").writeText(
            """{"cell": ${Burrs.CELL}, "pieces": [""" +
                burr.pieces.joinToString(", ") { p ->
                    """{"name": "${p.name}", "axis": "${p.axis}", "cells": [${p.cells.joinToString(", ") { "[${it.x}, ${it.y}, ${it.z}]" }}]}"""
                } + "]}",
        )
        fun v(a: Vec3) = "[${a.x}, ${a.y}, ${a.z}]"
        fun b(a: Aabb) = "[${v(a.lo)}, ${v(a.hi)}]"
        // v0.10.1: each panel is a union of boxes (corner tabs and notches); lo/hi corners, meters.
        File(dir, "box.json").writeText(
            """{"body": ${b(Boxes.body)}, "plinth": ${b(Boxes.plinth)}, "panels": [""" +
                Boxes.parts.joinToString(", ") { p -> "[" + p.joinToString(", ") { b(it) } + "]" } + "]}",
        )
        println("wrote ${dir.path}")
    }
}
