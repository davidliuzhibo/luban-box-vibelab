package com.vrgames.lubanbox.game

import android.content.Context
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
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
    /**
     * The bench top, in room coordinates. It is set from the player's own eye height ([BENCH_BELOW_EYE] below the
     * eye, adjustable in the notes), never from a fixed height above the room's origin: on the device the origin is
     * not always the floor, and a fixed 0.8 m put the bench almost on the floor (2026-11-10).
     */
    var benchHeight by mutableStateOf(1.25f - DEFAULT_BENCH_BELOW_EYE)
    /** The player's eye height, measured from the headset when the bench is set in front of them. */
    var eyeY = 1.25f
        set(v) { field = v; benchHeight = v - benchBelowEye }
    /** How far below the eye the bench top is (the notes' 升高 / 降低 change it). */
    var benchBelowEye = DEFAULT_BENCH_BELOW_EYE
        private set

    /** The workshop around the bench (a full scene) instead of the player's own room. */
    var workshopEnv by mutableStateOf(false); private set
    var musicOn by mutableStateOf(true); private set
    /** The first-time label over the bench, until the player has moved something once. */
    var coached by mutableStateOf(false); private set

    /** The bench has seen the player struggle (pushes that would not go, or a long time without progress): the help panel offers a hint. */
    var offerHelp by mutableStateOf(false); private set
    /** A voice clip is playing (a relic's story or a hint). */
    var speaking by mutableStateOf<String?>(null); private set
    /** Set by the stage: makes one solver move on the bench (animated); false when none is left. */
    var demo: (() -> Boolean)? = null
    private var bumps = 0
    private var lastBumpAt = 0L
    private var lastProgressAt = 0L
    private var app: Context? = null
    private var player: MediaPlayer? = null

    var sound: com.vrgames.lubanbox.render.Sound? = null
    var music: com.vrgames.lubanbox.render.Music? = null
    /** Debug only (AutoPilot): bench pose override "x,y,z,yaw,scale" to frame it for the emulator camera. */
    var debugView by mutableStateOf<String?>(null)
    var debugSky by mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (prefs != null) return
        app = context.applicationContext
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        synchronized(lock) { workshop = Workshop(Progress.parse(p.getString(KEY_PROGRESS, null))) }
        p.getFloat(KEY_BELOW_EYE, -1f).takeIf { it > 0 }?.let { benchBelowEye = it; benchHeight = eyeY - it }
        workshopEnv = p.getBoolean(KEY_ENV, false)
        musicOn = p.getBoolean(KEY_MUSIC, true)
        coached = p.getBoolean(KEY_COACHED, false)
        refresh(rebuild = true)
        progressVersion++
        AutoPilot.register(context)
    }

    fun startWork() {
        showTitle = false
        calm()
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
        stopVoice()
        calm()
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
        calm()
        when (out) {
            Workshop.Outcome.NEXT_ROUND -> refresh(rebuild = true)
            Workshop.Outcome.OPENED, Workshop.Outcome.OPENED_AGAIN -> {
                opened = relicId
                save()
                refresh()
                // The relic's story, told once it is open (the reward for opening it).
                if (out == Workshop.Outcome.OPENED) speak("story_$relicId")
            }
            Workshop.Outcome.MOVED -> refresh()
        }
        return out
    }

    /** A 斗拱 part was let go near a slot it does not fit (yet). */
    fun refused(why: Assembly.Refusal, slot: String) {
        struggle()
        note = when (why) {
            Assembly.Refusal.TOO_EARLY -> "「$slot」下面的构件还没装好，先从下往上装。"
            Assembly.Refusal.WRONG_PART -> "这里放的不是它，看看「$slot」该用哪一件。"
            Assembly.Refusal.TAKEN -> "「$slot」已经装好了。"
            Assembly.Refusal.FITS_NOWHERE -> "这一件哪儿都装不上，它是多出来的。"
        }
    }

    /**
     * The hint ladder: first how to think about the relic (said aloud), then which piece to move
     * and where (the bench's gold arrow), then where it goes. 「演示一步」 is [demoStep].
     */
    fun askHint() {
        offerHelp = false
        val idea = synchronized(lock) { if (workshop.solved) null else if (!workshop.ideaGiven) workshop.idea() else null }
        if (idea != null) {
            hint = null
            note = idea
            speak("idea_$relicId")
            refresh()
            return
        }
        hint = synchronized(lock) { workshop.hint() }
        note = if (hint == null) "已经打开了。" else null
        refresh()
    }

    /** 「演示一步」: the solver makes the next move for the player (counts as a hint). */
    fun demoStep() {
        offerHelp = false
        val d = demo ?: return
        if (synchronized(lock) { workshop.solved }) return
        synchronized(lock) { workshop.usedDemo() }
        hint = null
        relicCardDismissed = true
        refresh()
        if (!d()) note = "已经打开了。"
    }

    /** 「跳过这件」: on to the next relic; this one stays marked so the player can come back. */
    fun skip() {
        offerHelp = false
        val next = synchronized(lock) {
            workshop.skip()
            Relics.all.getOrNull(Relics.all.indexOf(workshop.relic) + 1)?.takeIf(workshop.progress::open)
        }
        save()
        Log.i("LubanGame", "skip $relicId -> ${next?.id}")
        if (next != null) select(next.id) else {
            note = "这是最后一件了。前面跳过的器物，在营造图谱里还能回去做。"
            refresh()
        }
    }

    /** A push that would not go, or a part that did not fit: a few of these and the help panel speaks up. */
    fun struggle() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBumpAt < BUMP_GAP_MS) return
        lastBumpAt = now
        if (++bumps >= BUMPS_TO_OFFER) offerHelp = true
    }

    /** Called every frame by the stage: a long while without progress also brings the offer. */
    fun watch() {
        if (offerHelp || showTitle || opened == relicId || lastProgressAt == 0L) return
        if (SystemClock.uptimeMillis() - lastProgressAt > IDLE_TO_OFFER_MS) offerHelp = true
    }

    private fun calm() {
        bumps = 0
        offerHelp = false
        lastProgressAt = SystemClock.uptimeMillis()
    }

    /** Plays assets/narration/<clip>.ogg, over the music (ducked). */
    fun speak(clip: String) {
        stopVoice()
        val a = app ?: return
        runCatching {
            val fd = a.assets.openFd("narration/$clip.ogg")
            player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopVoice() }
                prepare()
                start()
            }
            speaking = clip
            music?.duck(true)
        }.onFailure { Log.w("LubanGame", "voice $clip: ${it.message}") }
    }

    fun stopVoice() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        if (speaking != null) {
            speaking = null
            music?.duck(false)
        }
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
        benchBelowEye = (benchBelowEye - d).coerceIn(0.2f, 0.8f)
        benchHeight = eyeY - benchBelowEye
        prefs?.edit()?.putFloat(KEY_BELOW_EYE, benchBelowEye)?.apply()
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

    const val DEFAULT_BENCH_BELOW_EYE = 0.45f
    private const val BUMPS_TO_OFFER = 5
    private const val BUMP_GAP_MS = 800L
    private const val IDLE_TO_OFFER_MS = 120_000L
    // v2 since 0.5 raised the default: heights saved before that start over at the new one.
    private const val KEY_BELOW_EYE = "bench_below_eye_v3"
    private const val KEY_ENV = "workshop_env"
    private const val KEY_MUSIC = "music"
    private const val KEY_COACHED = "coached"
}
