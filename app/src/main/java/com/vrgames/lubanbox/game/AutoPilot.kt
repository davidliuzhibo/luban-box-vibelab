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
 * cmd: all (every relic in order) · relic --es id lock|klotski|box|dougong · solve (the relic on
 * the bench) · step (one solver move) · hint · reset (wipe the save) · stop ·
 * view --es id "x,y,z,yaw,scale"|reset (bench pose for the emulator camera) · turn --es id 30 (degrees) ·
 * env (switch the room / workshop surroundings) · music (switch the music) · sky --es id "x,y,z" (sky euler, degrees)
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

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
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
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
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
        main.postDelayed({ if (running) solve() }, NEXT_MS)
    }

    /** Plays the solver's moves one by one until the relic opens. */
    private fun solve() {
        solving = true
        val relic = Game.relicId
        fun tick() {
            if (!solving) return
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
