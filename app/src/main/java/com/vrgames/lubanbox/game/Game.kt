package com.vrgames.lubanbox.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.lubanbox.core.Assembly
import com.vrgames.lubanbox.core.Progress
import com.vrgames.lubanbox.core.Relics
import com.vrgames.lubanbox.core.Workshop

/**
 * State shared by the stage and both windows (one process). [workshop] holds the rules; the
 * Compose fields mirror what the windows show and change only on events, never per frame.
 */
object Game {
    private const val PREFS = "luban_box"
    private const val KEY_PROGRESS = "progress"

    /** Guards [workshop]: gestures, the frame callback and window buttons all touch it. */
    val lock = Any()
    var workshop = Workshop(); private set
    private var prefs: android.content.SharedPreferences? = null

    var relicId by mutableStateOf(workshop.relic.id); private set
    var round by mutableIntStateOf(0); private set
    var moves by mutableIntStateOf(0); private set
    var hintsUsed by mutableIntStateOf(0); private set
    /** Bumps when the bench must rebuild (new relic, next round, restart). */
    var benchVersion by mutableIntStateOf(0); private set
    /** Bumps when saved progress changes. */
    var progressVersion by mutableIntStateOf(0); private set
    /** The relic just opened (the windows celebrate it until the player moves on). */
    var opened by mutableStateOf<String?>(null); private set
    /** The current hint, shown on the bench and explained in the notes. */
    var hint by mutableStateOf<Workshop.Hint?>(null); private set
    /** A short line in the notes after something the player did (a part that does not fit yet...). */
    var note by mutableStateOf<String?>(null)
    var showTitle by mutableStateOf(true)
    /** The relic's card (name, goal, how to handle it) has been put away; it comes back with each relic. */
    var relicCardDismissed by mutableStateOf(false)
    /** The in-scene menu (on the bench) is unfolded. */
    var menuOpen by mutableStateOf(false)
    /** Bench top height above the floor (meters); the player can raise or lower it. */
    var benchHeight by mutableStateOf(DEFAULT_BENCH_HEIGHT)

    /** The workshop around the bench (a full scene) instead of the player's own room. */
    var workshopEnv by mutableStateOf(false); private set
    var musicOn by mutableStateOf(true); private set
    /** The first-time label over the bench, until the player has moved something once. */
    var coached by mutableStateOf(false); private set

    var sound: com.vrgames.lubanbox.render.Sound? = null
    var music: com.vrgames.lubanbox.render.Music? = null
    /** Debug only (AutoPilot): bench pose override "x,y,z,yaw,scale" to frame it for the emulator camera. */
    var debugView by mutableStateOf<String?>(null)
    var debugSky by mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        synchronized(lock) { workshop = Workshop(Progress.parse(p.getString(KEY_PROGRESS, null))) }
        p.getFloat(KEY_HEIGHT, -1f).takeIf { it > 0 }?.let { benchHeight = it }
        workshopEnv = p.getBoolean(KEY_ENV, false)
        musicOn = p.getBoolean(KEY_MUSIC, true)
        coached = p.getBoolean(KEY_COACHED, false)
        refresh(rebuild = true)
        progressVersion++
        AutoPilot.register(context)
    }

    fun startWork() {
        showTitle = false
    }

    fun select(id: String) {
        synchronized(lock) {
            val r = Relics.byId(id)
            if (!workshop.progress.open(r)) return
            workshop.load(id)
        }
        opened = null
        hint = null
        note = null
        showTitle = false
        relicCardDismissed = false
        refresh(rebuild = true)
    }

    fun restart() {
        synchronized(lock) { workshop.restart() }
        hint = null
        note = null
        refresh(rebuild = true)
    }

    fun nextRelic() {
        val next = synchronized(lock) { workshop.next() } ?: return
        select(next.id)
    }

    /** The player made a move on the bench. Returns what it led to (for sound and effects). */
    fun afterMove(): Workshop.Outcome {
        val out = synchronized(lock) { workshop.afterMove() }
        relicCardDismissed = true
        if (!coached) {
            coached = true
            prefs?.edit()?.putBoolean(KEY_COACHED, true)?.apply()
        }
        hint = null
        note = null
        when (out) {
            Workshop.Outcome.NEXT_ROUND -> refresh(rebuild = true)
            Workshop.Outcome.OPENED, Workshop.Outcome.OPENED_AGAIN -> {
                opened = relicId
                save()
                refresh()
            }
            Workshop.Outcome.MOVED -> refresh()
        }
        return out
    }

    /** A 斗拱 part was let go near a slot it does not fit (yet). */
    fun refused(why: Assembly.Refusal, slot: String) {
        note = when (why) {
            Assembly.Refusal.TOO_EARLY -> "「$slot」下面的构件还没装好，先从下往上装。"
            Assembly.Refusal.WRONG_PART -> "这里放的不是它，看看「$slot」该用哪一件。"
            Assembly.Refusal.TAKEN -> "「$slot」已经装好了。"
            Assembly.Refusal.FITS_NOWHERE -> "这一件哪儿都装不上，它是多出来的。"
        }
    }

    fun askHint() {
        hint = synchronized(lock) { workshop.hint() }
        note = if (hint == null) "已经打开了。" else null
        refresh()
    }

    fun toggleWorkshop() {
        workshopEnv = !workshopEnv
        prefs?.edit()?.putBoolean(KEY_ENV, workshopEnv)?.apply()
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean(KEY_MUSIC, musicOn)?.apply()
    }

    fun raiseBench(d: Float) {
        benchHeight = (benchHeight + d).coerceIn(0.5f, 1.3f)
        prefs?.edit()?.putFloat(KEY_HEIGHT, benchHeight)?.apply()
    }

    /** Test hook: forget all progress. */
    fun resetSave() {
        prefs?.edit()?.remove(KEY_PROGRESS)?.remove(KEY_COACHED)?.apply()
        coached = false
        synchronized(lock) { workshop = Workshop() }
        opened = null
        hint = null
        note = null
        showTitle = true
        refresh(rebuild = true)
        progressVersion++
    }

    private fun save() {
        prefs?.edit()?.putString(KEY_PROGRESS, workshop.progress.serialize())?.apply()
        progressVersion++
    }

    fun refresh(rebuild: Boolean = false) {
        synchronized(lock) {
            relicId = workshop.relic.id
            round = workshop.round
            moves = workshop.moves
            hintsUsed = workshop.hintsUsed
        }
        if (rebuild) benchVersion++
    }

    const val DEFAULT_BENCH_HEIGHT = 0.8f
    // v2 since 0.5 raised the default: heights saved before that start over at the new one.
    private const val KEY_HEIGHT = "bench_height_v2"
    private const val KEY_ENV = "workshop_env"
    private const val KEY_MUSIC = "music"
    private const val KEY_COACHED = "coached"
}
