package com.vrgames.lubanbox.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.lubanbox.core.Relics

/**
 * Test hook, debug builds only: solves relics with the solver's moves so the whole chapter can be
 * checked on the emulator without hands. Driven over adb; results go to logcat (tag LubanTest).
 *
 *   adb shell am broadcast -a com.vrgames.lubanbox.TEST -p com.vrgames.lubanbox --es cmd all
 *
 * cmd: all (every relic in order) · relic --es id lock|klotski|box|dougong · force --es id <relic> (even if not open yet) · solve (the relic on
 * the bench) · step (one solver move) · hint · reset (wipe the save) · stop ·
 * view --es id "x,y,z,yaw,scale"|reset (bench pose for the emulator camera) · turn --es id 30 (degrees) ·
 * env (switch the room / workshop surroundings) · music (switch the music) · sky --es id "x,y,z" (sky euler, degrees)
 *
 * v0.10 (through the player's own drag / tap handlers, synthetic hand movement in small steps):
 * burrmove (the solver's next move on the lock, dragged) · burrexit (drag moves until one stick comes out) ·
 * burrsolve (drag every move, waiting for each stick to lie down; every exit logs its clearance, tag LubanExit) ·
 * exitpause --es id 0.45|off (sticks on their way out stop at that fraction of the way, for a screenshot) ·
 * dgtap --es id <part> (a short pinch on a 斗拱 part) · dgplace --es id <part> (carry it onto its slot and let go) ·
 * dgcheck (泥道栱 at the wrong angle is refused, turned it goes in) · dgfinish (finishes the 斗拱 by turning and carrying)
 *
 * v0.10.1 (the 鲁班盒's front panel slid into the lifted right end on the device):
 * boxplay [--es id hold] (puts the 鲁班盒 on the bench and opens it by hand drags, one move every 3 s, logging each
 * panel's free room before and the move's deepest overlap; tries the screenshot's move first with the right end down,
 * then pushes the front 2.5 cm with the right end up 1 cm (it must stop at 1 cm, touching), and tries to drop the right
 * end back; with hold it stops after the right end is up 1 cm, for a screenshot) · boxstate (logs every panel's room) ·
 * slidedrag --es id "piece,axis,mm" (one hand drag on a sliding piece of the relic on the bench: 鲁班盒, 花旗锁...) ·
 * press --es id "part,x,z" (a build part held and pushed 15 cm down onto what is built at x, z: it must ride on top) ·
 * fitpause --es id 1.6|off (the 被中香炉's fitting stops that many seconds in, for a screenshot)
 */
object AutoPilot {
    const val ACTION = "com.vrgames.lubanbox.TEST"
    private const val TAG = "LubanTest"
    private const val STEP_MS = 900L
    private const val NEXT_MS = 3000L

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var running = false
    private var solving = false

    /** Set by the stage: performs one solver move on the bench (animated); false when none is left. */
    var stepper: (() -> Boolean)? = null
    /** Set by the stage: turns the turntable by the given degrees. */
    var turner: ((Double) -> Unit)? = null
    /** Set by the stage: the bench, for the v0.10 hooks. */
    var bench: com.vrgames.lubanbox.render.Bench? = null

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // 2026-11-19: the menu's 退出, for the emulator test (every window of the app should go).
                if (i.getStringExtra("cmd") == "quit") { android.util.Log.i(TAG, "cmd quit"); com.vrgames.lubanbox.platform.AppExit.finishAll(c) }
                else command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
            }
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "all" -> {
                stop()
                queue.addAll(Relics.all.map { it.id })
                running = true
                next()
            }
            "relic" -> if (Relics.all.any { it.id == id }) Game.select(id!!) else Log.w(TAG, "unknown relic $id")
            // v0.10: straight to a relic whether or not the ones before it are done (the emulator's save may be empty).
            "force" -> if (Relics.all.any { it.id == id }) Game.select(id!!, force = true).also { Log.i(TAG, "force ${Game.relicId}") } else Log.w(TAG, "unknown relic $id")
            "solve" -> solve()
            "step" -> Log.i(TAG, "step ${stepper?.invoke()}")
            "hint" -> {
                Game.askHint()
                Log.i(TAG, "hint ${Game.hint}")
            }
            "reset" -> {
                stop()
                Game.resetSave()
            }
            "stop" -> stop()
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "turn" -> turner?.invoke(id?.toDoubleOrNull() ?: 30.0)
            "env" -> Game.toggleWorkshop().also { Log.i(TAG, "env workshop=${Game.workshopEnv}") }
            "sky" -> Game.debugSky = id
            "music" -> Game.toggleMusic().also { Log.i(TAG, "music on=${Game.musicOn}") }
            "burrmove" -> bench?.let { Log.i(TAG, "BURR move ${com.vrgames.lubanbox.content.hookBurrMove(it)} out=${outCount()}/6") }
            "burrexit" -> burrLoop(untilExit = true)
            "burrsolve" -> burrLoop(untilExit = false)
            "exitpause" -> {
                com.vrgames.lubanbox.render.SlideView.debugHoldAt = id?.toDoubleOrNull()
                Log.i(TAG, "exitpause ${com.vrgames.lubanbox.render.SlideView.debugHoldAt}")
            }
            "dgtap" -> dgTap(id?.toIntOrNull() ?: 0)
            "dgplace" -> dgPlace(id?.toIntOrNull() ?: 0)
            "dgcheck" -> dgCheck()
            "dgfinish" -> dgFinish()
            "boxplay" -> boxPlay(id == "hold")
            "boxstate" -> logBox("state")
            "press" -> {
                // id "part,x,z": push a held build part down onto what is built at (x, z).
                val f = id?.split(",")
                val b = bench
                if (f == null || f.size != 3 || b == null) Log.w(TAG, "press needs part,x,z") else
                    Log.i(TAG, "PRESS ${com.vrgames.lubanbox.content.hookPressPart(b, f[0].trim().toInt(), com.vrgames.lubanbox.core.Vec3(f[1].trim().toDouble(), 0.0, f[2].trim().toDouble()))}")
            }
            "fitpause" -> {
                com.vrgames.lubanbox.render.CenserScene.debugHoldAt = id?.toDoubleOrNull()
                Log.i(TAG, "fitpause ${com.vrgames.lubanbox.render.CenserScene.debugHoldAt}")
            }
            "slidedrag" -> {
                // id "piece,axis,mm": one hand drag on a sliding piece of whatever is on the bench.
                val f = id?.split(",")
                val b = bench
                if (f == null || f.size != 3 || b == null) Log.w(TAG, "slidedrag needs piece,axis,mm") else {
                    val axis = com.vrgames.lubanbox.core.Axis.valueOf(f[1].trim().uppercase())
                    Log.i(TAG, "SLIDEDRAG ${f[0]} ${f[1]} ${f[2]} mm: ${com.vrgames.lubanbox.content.hookSlideDrag(b, f[0].trim().toInt(), axis, f[2].trim().toDouble() / 1000)}")
                    logBox("after slidedrag")
                }
            }
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
    }

    private fun outCount(): Int = bench?.slide?.puzzle?.let { p -> (0 until p.pieceCount).count(p::removed) } ?: 0

    /** Drags the solver's moves one by one on the lock, waiting while a stick is on its way out. */
    private fun burrLoop(untilExit: Boolean) {
        solving = true
        val start = outCount()
        fun tick() {
            if (!solving) return
            val b = bench ?: return
            if (Game.opened == "lock" && !b.busy) {
                solving = false
                Log.i(TAG, "BURR OPENED moves=${Game.moves}")
                return
            }
            if (untilExit && outCount() > start) {
                solving = false
                Log.i(TAG, "BURR first exit done, out=${outCount()}/6 busy=${b.busy}")
                return
            }
            val r = com.vrgames.lubanbox.content.hookBurrMove(b)
            if (r == null && !b.busy) {
                solving = false
                Log.w(TAG, "BURR nothing left, opened=${Game.opened}")
                return
            }
            if (r != "busy") Log.i(TAG, "BURR move $r out=${outCount()}/6")
            main.postDelayed(::tick, if (r == "busy") 600L else 1200L)
        }
        tick()
    }

    private fun dgTap(part: Int): Boolean {
        val b = bench ?: return false
        val ok = com.vrgames.lubanbox.content.hookTapPart(b, part)
        val a = b.build?.assembly
        Log.i(TAG, "DG tap part=$part ${a?.parts?.getOrNull(part)} turned=$ok yaw=${a?.yawOf(part)?.toInt()}")
        return ok
    }

    /** Carries [part] onto the slot it would go in (the ready one of its type) and lets go: "Placed", "Refused:<why>", ... */
    private fun dgPlace(part: Int): String {
        val b = bench ?: return "no bench"
        val a = b.build?.assembly ?: return "no build"
        val slot = a.hint()?.takeIf { it.first == part }?.second ?: a.slots.firstOrNull { it.type == a.parts[part] && a.ready(it) }
            ?: return "no slot ready".also { Log.i(TAG, "DG place part=$part: $it") }
        val r = com.vrgames.lubanbox.content.hookCarryPart(b, part, slot.pos + com.vrgames.lubanbox.core.Vec3(0.006, 0.004, -0.005))
        val what = when (val d = (r as? com.vrgames.lubanbox.render.Bench.Released.Dropped)?.drop) {
            is com.vrgames.lubanbox.render.AssemblyView.Drop.Placed -> "Placed"
            is com.vrgames.lubanbox.render.AssemblyView.Drop.Refused -> "Refused:${d.why}"
            else -> "$r"
        }
        Log.i(TAG, "DG place part=$part ${a.parts[part]} yaw=${a.yawOf(part).toInt()} slot=${slot.id}(yaw ${slot.yaw.toInt()}) -> $what")
        return what
    }

    /** 栌斗 and 华栱 in; 泥道栱 (lying lengthwise) let go over its slot unturned → refused; one tap → it goes in. */
    private fun dgCheck() {
        stop()
        running = true
        if (Game.relicId != "dougong") Game.select("dougong", force = true)
        val steps = ArrayDeque<() -> Unit>()
        var refused = ""
        var placed = ""
        steps += { dgPlace(0) }
        steps += { dgPlace(1) }
        steps += { refused = dgPlace(2) }
        steps += { dgTap(2) }
        steps += { placed = dgPlace(2) }
        steps += {
            Log.i(TAG, "DGCHECK unturned=$refused turned=$placed -> ${if (refused == "Refused:WRONG_ANGLE" && placed == "Placed") "PASS" else "FAIL"}")
            running = false
        }
        fun next() {
            if (!running) return
            val f = steps.removeFirstOrNull() ?: return
            f()
            main.postDelayed(::next, 2500L)
        }
        main.postDelayed(::next, NEXT_MS)
    }

    /** Finishes the 斗拱 the player's way: for each next part, a tap if it faces wrong, then carry it in. */
    private fun dgFinish() {
        stop()
        running = true
        var taps = 0
        var placedCount = 0
        fun next() {
            if (!running) return
            val b = bench ?: return
            val a = b.build?.assembly
            if (a == null || a.solved) {
                running = false
                Log.i(TAG, "DG OPENED=${Game.opened == "dougong"} taps=$taps placed=$placedCount moves=${Game.moves}")
                return
            }
            val (part, slot) = a.hint() ?: run { running = false; Log.w(TAG, "DG stuck"); return }
            if (!a.angleOk(part, slot)) {
                dgTap(part)
                taps++
            } else {
                if (dgPlace(part) == "Placed") placedCount++
            }
            main.postDelayed(::next, 1500L)
        }
        next()
    }

    private fun logBox(what: String) {
        val box = bench?.slide?.puzzle as? com.vrgames.lubanbox.core.PuzzleBox ?: run {
            Log.w(TAG, "BOX no box on the bench")
            return
        }
        Log.i(TAG, "BOX $what: ${synchronized(Game.lock) { box.describeFree() }}")
    }

    /** The 鲁班盒 opened by hand drags (see the class doc). */
    private fun boxPlay(hold: Boolean) {
        stop()
        running = true
        Game.select("box", force = true)
        val X = com.vrgames.lubanbox.core.Axis.X
        val Y = com.vrgames.lubanbox.core.Axis.Y
        val B = com.vrgames.lubanbox.core.Boxes
        // (label, panel, axis, hand movement in meters). Every real move pushes 3 mm past its step: it must stop there.
        val plan = mutableListOf(
            Triple("TRY front right with the right end down (screenshot's move, must not move)", B.FRONT, X to 0.015),
            Triple("1 right end up", B.RIGHT, Y to 0.013),
        )
        if (!hold) plan += listOf(
            Triple("TRY front right 2.5 cm with the right end up 1 cm (must stop at 1 cm, touching)", B.FRONT, X to 0.025),
            Triple("TRY right end back down (front under its tab, must not move)", B.RIGHT, Y to -0.012),
            Triple("3 left end down", B.LEFT, Y to -0.013),
            Triple("4 back left", B.BACK, X to -0.013),
            Triple("5 right end up to 2 cm", B.RIGHT, Y to 0.013),
            Triple("6 front right to 2 cm", B.FRONT, X to 0.013),
            Triple("7 left end down to 2 cm", B.LEFT, Y to -0.013),
            Triple("8 lid off to the left", B.LID, X to -0.205),
        )
        val steps = ArrayDeque(plan)
        fun next() {
            if (!running) return
            val b = bench ?: return
            val st = steps.removeFirstOrNull()
            if (st == null) {
                running = false
                Log.i(TAG, "BOXPLAY done opened=${Game.opened == "box"} moves=${Game.moves}")
                logBox("end")
                return
            }
            val (label, piece, move) = st
            logBox("before '$label'")
            val r = com.vrgames.lubanbox.content.hookSlideDrag(b, piece, move.first, move.second)
            Log.i(TAG, "BOXMOVE $label: $r")
            if (hold && steps.isEmpty()) {
                running = false
                logBox("held for the screenshot")
                return
            }
            main.postDelayed(::next, 3000L)
        }
        main.postDelayed(::next, NEXT_MS)
    }

    private fun stop() {
        running = false
        solving = false
        queue.clear()
        main.removeCallbacksAndMessages(null)
    }

    private fun next() {
        if (!running) return
        val id = queue.removeFirstOrNull()
        if (id == null) {
            running = false
            val p = Game.workshop.progress
            Log.i(TAG, "done opened=${Relics.all.count { p.solved(it.id) }}/${Relics.all.size}")
            return
        }
        Game.select(id)
        // 2026-11-23 (emulator): a relic that is not open yet is not loaded; solving "the relic on the bench" then
        // re-solved the previous one and reported it again for every relic left in the queue.
        if (Game.relicId != id) {
            running = false
            Log.w(TAG, "result $id NOT OPEN (the one before it is not done); stopping")
            return
        }
        main.postDelayed({ if (running) solve() }, NEXT_MS)
    }

    /** Plays the solver's moves one by one until the relic opens. */
    private fun solve() {
        solving = true
        val relic = Game.relicId
        var waited = 0
        fun tick() {
            if (!solving) return
            // 2026-11-23: the bench is rebuilt a moment after the workshop loads a relic or the next 华容道 board (much
            // later on a slow emulator); until it shows it, the solver's step acted on the finished board and gave up
            // ("result klotski STUCK moves=0" after the first board). Wait for it (up to 90 s).
            val shown = bench?.shown
            if (shown != (relic to Game.round) && Game.opened != relic) {
                if (waited++ % 10 == 0) Log.i(TAG, "solve $relic: waiting for the bench (it shows $shown)")
                if (waited > 180) {
                    solving = false
                    Log.w(TAG, "result $relic STUCK (the bench never showed it)")
                    if (running) main.postDelayed(::next, NEXT_MS)
                    return
                }
                main.postDelayed(::tick, 500L)
                return
            }
            if (Game.opened == relic) {
                solving = false
                Log.i(TAG, "result $relic OPENED moves=${Game.moves}")
                if (running) main.postDelayed(::next, NEXT_MS)
                return
            }
            val moved = stepper?.invoke() ?: false
            if (!moved && Game.opened != relic) {
                solving = false
                Log.w(TAG, "result $relic STUCK moves=${Game.moves}")
                if (running) main.postDelayed(::next, NEXT_MS)
                return
            }
            main.postDelayed(::tick, STEP_MS)
        }
        tick()
    }
}
