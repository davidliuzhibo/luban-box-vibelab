package com.vrgames.lubanbox.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.lubanbox.ATLAS_ID
import com.vrgames.lubanbox.NOTES_ID
import com.vrgames.lubanbox.core.Vec3
import com.vrgames.lubanbox.core.Workshop
import com.vrgames.lubanbox.game.AutoPilot
import com.vrgames.lubanbox.game.Game
import com.vrgames.lubanbox.render.Assets
import com.vrgames.lubanbox.render.AssemblyView
import com.vrgames.lubanbox.render.Bench
import com.vrgames.lubanbox.render.Music
import com.vrgames.lubanbox.render.model
import com.vrgames.lubanbox.render.shared
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.vrgames.lubanbox.render.Sfx
import com.vrgames.lubanbox.render.Sound
import com.vrgames.lubanbox.render.setPos
import com.vrgames.lubanbox.render.transform

/**
 * The workshop (Stage): the bench with the current relic, lit by a warm lamp. Pinch a piece and
 * drag it: it slides along whichever of its directions the hand follows. Pinch the turntable and
 * move sideways to turn the relic. Opens the 营造图谱 and 匠人手记 windows.
 */
@Composable
fun WorkshopStage() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    val converter = LocalPhysicalLengthConverter.current
    var bench by remember { mutableStateOf<Bench?>(null) }
    var sky by remember { mutableStateOf<Entity?>(null) }
    // Carved rosewood frames behind the scene panels (tools/panel3d), by panel id.
    val frames = remember { HashMap<String, Entity>() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    val main = remember { Handler(Looper.getMainLooper()) }
    // Last gesture callback (uptime ms), for the stuck-drag check in the frame loop.
    val lastInput = remember { longArrayOf(0L) }
    // Hand tracking, to see the fingers open even when the system never ends the drag.
    val hands = remember { HandTrackingProvider() }
    // The hand that pinched the current drag (0 left, 1 right, -1 unknown) and open frames seen.
    val pinch = remember { intArrayOf(-1, 0) }
    // The headset pose, to set the bench straight in front of the player.
    val head = remember { HMDTrackingProvider() }
    // Turns the bench (and the workshop around it) to where the player looks now; false until the pose is in.
    val facePlayer: () -> Boolean = {
        val b = bench
        val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
        val f = pose?.rotation?.rotateVector(Vector3(0f, 0f, -1f))
        // Looking straight down or up gives no heading to go by.
        if (b == null || pose == null || f == null || kotlin.math.hypot(f.x, f.z) < 0.2f) {
            false
        } else {
            val yaw = Math.toDegrees(kotlin.math.atan2(-f.x, -f.z).toDouble()).toFloat()
            b.faceFrom(pose.position.x, pose.position.z, yaw)
            pose.position.y.takeIf { it in 0.3f..2.6f }?.let { Game.eyeY = it }
            b.placeInRoom(Game.benchHeight)
            sky?.transform()?.eulerAngles = EulerAngles(0f, yaw, 0f)
            Log.i("LubanStage", "bench faces the player at (${pose.position.x}, ${pose.position.z}), yaw $yaw, eye ${pose.position.y}, bench top ${Game.benchHeight}")
            true
        }
    }

    com.vrgames.lubanbox.platform.AppExit.TrackStage()
    DisposableEffect(Unit) {
        com.vrgames.lubanbox.platform.AppExit.stageAlive = true
        // The windows wait in the bench menu: the title, relic and result cards cover a run, and
        // two windows open at once crowd the view.
        Game.init(context)
        val started = runCatching { hands.start() }
        Log.i(INPUT_TAG, "hand tracking ${started.getOrNull() ?: started.exceptionOrNull()?.message} support=${runCatching { hands.supportState }.getOrNull()}")
        val headStarted = runCatching { head.start() }
        Log.i("LubanStage", "head tracking ${headStarted.getOrNull() ?: headStarted.exceptionOrNull()?.message}")
        onDispose {
            runCatching { hands.stop() }
            runCatching { head.stop() }
            subscription[0]?.cancel()
            AutoPilot.stepper = null
            AutoPilot.turner = null
            AutoPilot.bench = null
            Game.demo = null
            Game.stopVoice()
            com.vrgames.lubanbox.platform.AppExit.stageAlive = false
            runCatching { navigator.closeWindowContainer(ATLAS_ID) }
            runCatching { navigator.closeWindowContainer(NOTES_ID) }
        }
    }

    // Background music while the app is in front.
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }

    // Rebuild the relic when the workshop loads one (or the next round, or a restart).
    LaunchedEffect(Game.benchVersion, bench) {
        val b = bench ?: return@LaunchedEffect
        synchronized(Game.lock) { b.show(Game.workshop) }
    }
    LaunchedEffect(Game.benchHeight, Game.debugView, bench) {
        val b = bench ?: return@LaunchedEffect
        val v = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (v != null && v.size == 5) {
            b.root.setPos(Vector3(v[0], v[1], v[2]))
            b.root.transform().eulerAngles = EulerAngles(0f, v[3], 0f)
            b.root.transform().setScaleVector(Vector3(v[4]))
        } else {
            b.root.transform().setScaleVector(Vector3(1f))
            b.placeInRoom(Game.benchHeight)
        }
    }
    // The room's -z is wherever the headset was last recentered, not necessarily where the player
    // looks when the game opens: once the head pose comes in, set the bench in front of them.
    LaunchedEffect(bench, sky) {
        if (bench == null) return@LaunchedEffect
        repeat(30) {
            if (facePlayer()) return@LaunchedEffect
            delay(100)
        }
        Log.w("LubanStage", "no head pose: the bench stays at the room's front")
    }
    LaunchedEffect(Game.hint, Game.hintAsked, bench) { bench?.showHint(Game.hint) }
    LaunchedEffect(Game.workshopEnv, sky) { sky?.enabled = Game.workshopEnv }
    LaunchedEffect(Game.debugSky, sky) {
        val v = Game.debugSky?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: return@LaunchedEffect
        if (v.size == 3) sky?.transform()?.eulerAngles = EulerAngles(v[0], v[1], v[2])
    }
    LaunchedEffect(Game.coached, Game.showTitle, Game.relicId, Game.relicCardDismissed, bench) {
        // One explanation at a time: the first-move label waits for the relic card.
        bench?.showCoach(!Game.coached && !Game.showTitle && Game.relicId == com.vrgames.lubanbox.core.Relics.all.first().id && Game.relicCardDismissed)
    }
    // Scene panels: the title card, the "opened" card and the menu tab.
    LaunchedEffect(Game.showTitle, Game.opened, Game.relicId, Game.benchVersion, bench) {
        val b = bench ?: return@LaunchedEffect
        synchronized(Game.lock) { b.showTitle(Game.showTitle) }
        b.showPanel(PANEL_INTRO, Game.showTitle)
        b.showPanel(PANEL_RESULT, !Game.showTitle && Game.opened == Game.relicId)
        b.showPanel(PANEL_MENU, !Game.showTitle)
        b.showPanel(PANEL_HELP, !Game.showTitle && Game.opened != Game.relicId)
    }
    // The open menu is about half a meter tall and hangs from its middle: lift it clear of the bench top.
    // Folded, the help panel is a small tab without its frame; it folds by itself while a part is held.
    LaunchedEffect(Game.helpFolded, Game.partHeld, bench) {
        val folded = Game.helpFolded || Game.partHeld
        bench?.placePanel(PANEL_HELP, if (folded) HELP_FOLDED_POS else HELP_POS)
        frames[PANEL_HELP]?.enabled = !folded
    }
    LaunchedEffect(Game.menuOpen, bench) {
        bench?.placePanel(PANEL_MENU, if (Game.menuOpen) MENU_OPEN_POS else MENU_POS)
        // The folded menu is a small tab without a frame.
        frames[PANEL_MENU]?.enabled = Game.menuOpen
    }
    LaunchedEffect(Game.showTitle, Game.relicCardDismissed, Game.opened, Game.relicId, bench) {
        bench?.showPanel(PANEL_RELIC, !Game.showTitle && !Game.relicCardDismissed && Game.opened != Game.relicId)
    }
    LaunchedEffect(Game.progressVersion, bench) {
        val b = bench ?: return@LaunchedEffect
        synchronized(Game.lock) { b.showTokens(Game.workshop) }
    }

    SpatialView(
        Modifier.pointerInput(1) {
            // v0.10: a short pinch that never became a drag (the emulator's click; a still hand on the headset).
            detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                val b = bench ?: return@detectSpatialTapGesture
                if (Game.showTitle) return@detectSpatialTapGesture
                tapPart(b, value.targetEntity, "tap")
            }
        }.pointerInput(Unit) {
            fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
            detectSpatialDragGesture(
                context = context,
                targetedToEntity = TargetEntity.any(),
                onDragStart = { _, value ->
                    val b = bench ?: return@detectSpatialDragGesture
                    lastInput[0] = SystemClock.uptimeMillis()
                    val (l, r) = pinchValues(latestHands(hands))
                    pinch[0] = when {
                        (r ?: 0f) >= (l ?: 0f) -> if ((r ?: 0f) > PINCH_HELD) 1 else -1
                        else -> if ((l ?: 0f) > PINCH_HELD) 0 else -1
                    }
                    pinch[1] = 0
                    PinchClock.dragAt = SystemClock.uptimeMillis()
                    // A start while a drag is still open means its end never came: close it first.
                    if (b.dragging) release(b, "restart")
                    // The title card asks to start first.
                    val g = synchronized(Game.lock) { b.grabOf(value.targetEntity)?.takeIf { !Game.showTitle }?.also { b.begin(it) } }
                    if (g != null) main.post { Game.partHeld = true }
                    Log.i(INPUT_TAG, "start ${g ?: "none"} target=${value.targetEntity?.getName()} kind=${value.interactionKind} pinchL=$l pinchR=$r hand=${pinch[0]}")
                },
                onDragEnd = { release(bench, "end") },
                onDragCancel = { release(bench, "cancel") },
            ) { value ->
                val b = bench ?: return@detectSpatialDragGesture
                lastInput[0] = SystemClock.uptimeMillis()
                // Compose y points down; space y points up.
                val d = Vec3(meters(value.dragAmount.x), -meters(value.dragAmount.y), meters(value.dragAmount.z))
                val felt = synchronized(Game.lock) { b.drag(d) }
                when (felt) {
                    Bench.Felt.Tick -> Game.sound?.play(Sfx.TICK, Vec3.ZERO)
                    Bench.Felt.Bump -> {
                        Game.sound?.play(Sfx.BUMP, Vec3.ZERO)
                        Game.struggle()
                    }
                    // A dial turned a stop: that is a move by itself.
                    Bench.Felt.Turned -> {
                        Game.sound?.play(Sfx.TICK, Vec3.ZERO)
                        afterMove(b, null)
                    }
                    null -> Unit
                }
            }
        },
        // The first-time label: a Compose panel the bench floats over itself.
        attachments = {
            AttachmentPanel(LABEL_FIRST) { FirstLabel(converter) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter) }
            AttachmentPanel(PANEL_RELIC) { RelicCard(converter) }
            AttachmentPanel(PANEL_HELP) { HelpPanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(
                    converter,
                    openNotes = { navigator.openWindowContainer(NOTES_ID) },
                    openAtlas = { navigator.openWindowContainer(ATLAS_ID) },
                    quit = { com.vrgames.lubanbox.platform.AppExit.finishAll(context) },
                    faceMe = { facePlayer() },
                )
            }
        },
        update = { _, attachments ->
            bench?.let { b ->
                if (!b.hasCoach) b.attachCoach(attachments.entity(LABEL_FIRST))
                attachPanels(b, attachments, frames)
            }
        },
        initial = { content, attachments ->
            // Warm lamp from the upper left, and a cool fill from the right.
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE6C4).toColor4(), 900f, true))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-50f, 40f, 0f))
            })
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFBCD2FF).toColor4(), 180f))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-15f, -130f, 0f))
            })

            for (id in FRAME_SIZE.keys) {
                runCatching { Entity.loadSuspend("asset://panels/panel_$id.glb") }
                    .onSuccess { frames[id] = it }
                    .onFailure { android.util.Log.w("LubanStage", "frame $id: ${it.message}") }
            }
            val assets = Assets()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { assets.load(it) }
                    .onFailure { android.util.Log.w("LubanStage", "model $name: ${it.message}") }
            }
            // The workshop: the rendered room (tools/make_workshop.py) on an inside-out sphere
            // (tools/make_sky.py) whose UVs match the panorama; the engine's own sphere maps it sideways.
            val room = runCatching {
                val tex = withContext(Dispatchers.IO) {
                    TextureResource.load("textures/workshop.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared()
                }
                val sphere = Entity.loadSuspend("asset://models/sky.glb")
                Assets.setMaterials(sphere, UnlitMaterial.create().apply { setBaseColorTexture(tex) }.shared())
                Entity().apply {
                    addChild(sphere)
                    enabled = Game.workshopEnv
                }
            }.onFailure { android.util.Log.w("LubanStage", "workshop: ${it.message}") }.getOrNull()
            room?.let { content.addEntity(it) }
            sky = room

            val b = Bench(assets)
            b.placeInRoom(Game.benchHeight)
            content.addEntity(b.root)
            b.attachCoach(attachments.entity(LABEL_FIRST))
            attachPanels(b, attachments, frames)
            Game.sound = Sound(b.root).also { it.load() }
            synchronized(Game.lock) { b.show(Game.workshop) }
            bench = b

            AutoPilot.stepper = {
                val can = canStep(b)
                if (can) autoStep(b)
                can
            }
            AutoPilot.turner = { deg -> b.turnBy(deg) }
            AutoPilot.bench = b
            // Every stick that comes out of the lock: which way, where it goes, and how close its way comes to anything.
            b.onExit = { path, cl ->
                Log.i("LubanExit", "EXIT stick=${path.stick} slot=${path.rest.slot} mode=${path.mode} legs=${path.keys.size - 1} ${"%.1f".format(path.duration)}s minClear $cl")
            }
            Game.demo = {
                val can = canStep(b)
                if (can) autoStep(b)
                can
            }
            var watchClock = 0.0

            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                synchronized(Game.lock) { b.frame(e.deltaTime.toDouble(), Game.workshop) }
                watchClock += e.deltaTime.toDouble()
                if (watchClock > 1.0) {
                    watchClock = 0.0
                    main.post { Game.watch() }
                }
                // A pinched hand never holds perfectly still, so a drag that has been silent this
                // long has lost its end (hand tracking dropped the release): treat it as let go.
                if (b.dragging && lastInput[0] > 0 && SystemClock.uptimeMillis() - lastInput[0] > STUCK_DRAG_MS) {
                    lastInput[0] = 0L
                    main.post { if (b.dragging) release(b, "watchdog") }
                }
                // The pinching hand's fingers have opened: that is the release, whatever the
                // gesture system says (it sometimes keeps the drag alive after the fingers part).
                if (b.dragging && pinch[0] >= 0) {
                    val v = pinchValues(latestHands(hands)).toList()[pinch[0]]
                    pinch[1] = if (v != null && v < PINCH_OPEN) pinch[1] + 1 else 0
                    if (pinch[1] >= PINCH_OPEN_FRAMES) {
                        pinch[0] = -1
                        main.post { if (b.dragging) release(b, "fingers-open") }
                    }
                }
            }
        },
    )
}

/** Models with a named node per piece (tools/make_*.py). */
private val MODELS = listOf(
    "bench", "burr", "klotski", "box", "dougong", "tokens",
    "wordlock", "padlock", "censer", "mirror", "chariot", "armillary", "waterclock", "chest",
)

/** Scene panels, placed in the bench frame: the title card over the back of the bench, the relic and result cards to its right, the menu at its front left. */
private fun attachPanels(b: Bench, attachments: com.pico.spatial.ui.foundation.content.SpatialViewAttachments, frames: Map<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float = 0f) {
        if (b.hasPanel(id)) return
        val e = attachments.entity(id) ?: return
        b.attachPanel(id, e, pos)
        if (pitch != 0f) e.transform().eulerAngles = EulerAngles(pitch, 0f, 0f)
        // The frame sits just behind the Compose text.
        frames[id]?.let { f ->
            f.setPos(Vector3(0f, 0f, -0.006f))
            e.addChild(f)
            if (id == PANEL_MENU) f.enabled = Game.menuOpen
        }
    }
    put(PANEL_INTRO, Vector3(0f, 0.5f, -0.2f))
    // The relic and result cards stand to the right, so the relic itself stays in view.
    // v0.9.1 (device test): 5 cm further right, and turned 15° toward the player (it was only slid right of the
    // player's line of sight, so its face looked away).
    put(PANEL_RESULT, CARD_POS)
    put(PANEL_RELIC, CARD_POS)
    b.panel(PANEL_RESULT)?.transform()?.eulerAngles = EulerAngles(0f, CARD_YAW, 0f)
    b.panel(PANEL_RELIC)?.transform()?.eulerAngles = EulerAngles(0f, CARD_YAW, 0f)
    put(PANEL_MENU, MENU_POS)
    // The help panel stands off the bench's right end, beyond the parts laid out there (they reach x ≈ 0.5),
    // turned toward the player (v0.9: lying at the front right it covered the parts while assembling).
    put(PANEL_HELP, HELP_POS, -12f)
    b.panel(PANEL_HELP)?.transform()?.eulerAngles = EulerAngles(-12f, -30f, 0f)
}

/** The relic and result cards' place (right of the turntable) and their turn toward the player. */
private val CARD_POS = Vector3(0.47f, 0.38f, -0.1f)
private const val CARD_YAW = -15f

/**
 * The help panel's place: right of the parts' tray, a little above the top, and its folded tab's (lower, nearer).
 * v0.9.1: 20 cm further right, 12 cm nearer and 4 cm lower than v0.9. From the seat (0.75 m in front of the bench) its
 * left edge, carved border included, is seen about 3° right of the turned relic card's right edge instead of behind it
 * (checked on the emulator from the seat: at 16 cm the two borders still touched).
 */
private val HELP_POS = Vector3(0.86f, 0.16f, 0.14f)
private val HELP_FOLDED_POS = Vector3(0.66f, 0.08f, 0.14f)

/** The menu tab at the bench's front left, and where the unfolded menu stands (its middle) so all of it is above the top. */
private val MENU_POS = Vector3(-0.42f, 0.07f, 0.12f)
private val MENU_OPEN_POS = Vector3(-0.42f, 0.36f, 0.12f)

/** Logcat tag for every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "LubanInput"
/** A drag with no callback for this long is taken as released. */
private const val STUCK_DRAG_MS = 2000L
/** Pinch strength (0 open .. 1 closed) above which a hand counts as the one pinching. */
private const val PINCH_HELD = 0.5f
/** Below this the fingers have parted; it must hold for a few frames (tracking jitters). */
private const val PINCH_OPEN = 0.3f
private const val PINCH_OPEN_FRAMES = 3

private fun latestHands(hands: HandTrackingProvider): HandTrackingData? =
    runCatching { if (hands.state == DataProvider.State.STARTED) hands.latestData else null }.getOrNull()

/** Left and right pinch strength, null where the hand is not tracked. */
private fun pinchValues(h: HandTrackingData?): Pair<Float?, Float?> {
    fun v(p: com.pico.spatial.tracking.hand.HandPinch?) = p?.takeIf { it.isReady }?.pinchValue
    return v(h?.leftPinch) to v(h?.rightPinch)
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * v0.10: when the last pinch started, when a part last turned, when a pinch that really moved something
 * last ended (uptime ms). A tap may come as a short drag, as a tap event, or as both: whichever comes
 * first turns the part, the other is dropped.
 */
private object PinchClock {
    var dragAt = 0L
    var turnedAt = 0L
    var movedEndAt = 0L
}

/** A pinch shorter than this whose hand strayed less than [TAP_MOVE] is a tap (it turns a 斗拱 part). */
private const val TAP_MS = 700L
private const val TAP_MOVE = 0.015
/** A tap event this soon after a turn or a real drag belongs to that same pinch. */
private const val TAP_DEDUPE_MS = 500L

/**
 * A tap on [target]: a loose 斗拱 part (on the bench, or the one in the hand) turns a quarter turn. Driven by
 * the tap gesture, and by the AutoPilot's test hook through the same path.
 */
internal fun tapPart(b: Bench, target: Entity?, source: String): Boolean {
    val now = SystemClock.uptimeMillis()
    if (now - PinchClock.turnedAt < TAP_DEDUPE_MS || now - PinchClock.movedEndAt < TAP_DEDUPE_MS) {
        Log.i(INPUT_TAG, "$source on ${target?.getName()} ignored: part of the pinch just handled")
        return false
    }
    val turned = synchronized(Game.lock) {
        val held = b.heldPart
        val i = b.build?.partOf(target)
        when {
            i == null -> null
            held == null -> b.turnPart(i)
            held == i -> b.turnPart(held)
            else -> null
        }
    } ?: return false
    PinchClock.turnedAt = now
    Game.sound?.play(Sfx.TURN, Vec3.ZERO)
    Handler(Looper.getMainLooper()).post { Game.partTurned() }
    Log.i(INPUT_TAG, "$source turns part ${turned.first} to ${turned.second.toInt()}°")
    return true
}

internal fun release(b: Bench?, why: String): Bench.Released {
    Handler(Looper.getMainLooper()).post { Game.partHeld = false }
    if (b == null) return Bench.Released.Nothing
    val now = SystemClock.uptimeMillis()
    val held = b.heldPart
    val travel = b.heldTravel
    // A short pinch that barely moved a 斗拱 part is a tap: it turns (not after a lost release: the watchdog).
    val tap = held != null && why != "watchdog" && why != "restart" && now - PinchClock.dragAt < TAP_MS && travel < TAP_MOVE
    val turnedAlready = now - PinchClock.turnedAt < TAP_DEDUPE_MS
    val r = synchronized(Game.lock) { b.end(asTap = tap, turn = !turnedAlready) }
    if (held != null && !tap) PinchClock.movedEndAt = now
    Log.i(INPUT_TAG, "$why -> ${describe(b, r)} (held ${now - PinchClock.dragAt} ms, hand ${"%.1f".format(travel * 100)} cm${if (tap) ", a tap" else ""})")
    when (r) {
        is Bench.Released.Moved -> afterMove(b, r.move.piece)
        is Bench.Released.Dropped -> when (val d = r.drop) {
            is AssemblyView.Drop.Placed -> {
                Game.sound?.play(Sfx.SNAP, Vec3.ZERO)
                afterMove(b, null)
            }
            is AssemblyView.Drop.Refused -> {
                Game.sound?.play(Sfx.BUMP, Vec3.ZERO)
                Game.refused(d.why, d.slot.id)
            }
            is AssemblyView.Drop.Turned -> {
                PinchClock.turnedAt = now
                Game.sound?.play(Sfx.TURN, Vec3.ZERO)
                Handler(Looper.getMainLooper()).post { Game.partTurned() }
            }
            AssemblyView.Drop.Loose -> Unit
        }
        Bench.Released.Nothing -> Unit
    }
    return r
}

/** One line for the log: what letting go did. */
private fun describe(b: Bench, r: Bench.Released): String = when (r) {
    is Bench.Released.Moved -> "Moved ${r.move}"
    Bench.Released.Nothing -> "Nothing"
    is Bench.Released.Dropped -> when (val d = r.drop) {
        is AssemblyView.Drop.Placed -> "Placed part=${d.part} ${b.build?.assembly?.parts?.getOrNull(d.part)} in ${d.slot.id}"
        is AssemblyView.Drop.Refused -> "Refused part=${d.part} ${b.build?.assembly?.parts?.getOrNull(d.part)} at ${d.slot.id}: ${d.why}"
        is AssemblyView.Drop.Turned -> "Turned part=${d.part} to ${d.yaw.toInt()}°"
        AssemblyView.Drop.Loose -> "Loose"
    }
}

// ---- Test hooks (AutoPilot, debug builds): the player's own handlers, fed synthetic hand movement ----------

/**
 * Pinches 斗拱 part [part], carries it up 8 cm, across and down to [to] (the relic's frame) in 1 cm steps of hand
 * movement through [Bench.drag], and lets go through [release]. Returns what letting go did.
 */
internal fun hookCarryPart(b: Bench, part: Int, to: Vec3): Bench.Released {
    val v = b.build ?: return Bench.Released.Nothing
    val ok = synchronized(Game.lock) { b.grabOf(v.holder(part))?.let { b.begin(it) } ?: false }
    if (!ok) return Bench.Released.Nothing
    // A held pinch, not a tap.
    PinchClock.dragAt = SystemClock.uptimeMillis() - 2 * TAP_MS
    var cur = synchronized(Game.lock) { v.position(part) }
    for (p in listOf(cur + Vec3(0.0, 0.08, 0.0), to + Vec3(0.0, 0.08, 0.0), to)) {
        val d = p - cur
        val n = maxOf(1, (d.length() / 0.01).toInt())
        repeat(n) { synchronized(Game.lock) { b.drag(b.roomDelta(d * (1.0 / n))) } }
        cur = p
    }
    return release(b, "hook")
}

/**
 * v0.10.1 test hook: pinches part [part], carries it over [at] (the relic's frame), then pushes the hand 15 cm straight
 * down into what is built there in 5 mm steps, as a player pressing it in would. Logs how low the part got against how
 * low it may go there (it must ride on top: sank 0.0 mm), then lifts the hand and puts the part back where it lay.
 */
internal fun hookPressPart(b: Bench, part: Int, at: Vec3): String {
    val v = b.build ?: return "no build"
    val ok = synchronized(Game.lock) { b.grabOf(v.holder(part))?.let { b.begin(it) } ?: false }
    if (!ok) return "cannot grab $part"
    PinchClock.dragAt = SystemClock.uptimeMillis() - 2 * TAP_MS
    val home = synchronized(Game.lock) { v.position(part) }
    var cur = home
    fun moveTo(p: Vec3, step: Double) {
        val d = p - cur
        val n = maxOf(1, (d.length() / step).toInt())
        repeat(n) { synchronized(Game.lock) { b.drag(b.roomDelta(d * (1.0 / n))) } }
        cur = p
    }
    moveTo(cur + Vec3(0.0, 0.2, 0.0), 0.01)
    moveTo(Vec3(at.x, cur.y, at.z), 0.01)
    var sank = 0.0
    var lowest = Double.MAX_VALUE
    var floor = 0.0
    repeat(30) {
        moveTo(cur + Vec3(0.0, -0.005, 0.0), 0.005)
        synchronized(Game.lock) {
            val p = v.position(part)
            floor = v.liftY(part, Vec3(p.x, -1.0, p.z))
            sank = maxOf(sank, floor - p.y)
            lowest = minOf(lowest, p.y)
        }
    }
    moveTo(cur + Vec3(0.0, 0.25, 0.0), 0.01)
    moveTo(Vec3(home.x, cur.y, home.z), 0.01)
    moveTo(home + Vec3(0.0, 0.01, 0.0), 0.01)
    val r = release(b, "hook")
    return "part=$part ${v.assembly.parts[part]} hand went 15 cm down over (${"%.3f".format(at.x)}, ${"%.3f".format(at.z)}): part lowest y=${"%.3f".format(lowest)}, " +
        "may go down to ${"%.3f".format(floor)}, sank ${"%.1f".format(maxOf(0.0, sank) * 1000)} mm -> ${describe(b, r)}"
}

/** A short pinch on part [part] through the player's tap handler (it turns a quarter turn). */
internal fun hookTapPart(b: Bench, part: Int): Boolean {
    val v = b.build ?: return false
    return tapPart(b, v.holder(part), "hook-tap")
}

/**
 * Makes the solver's next move on the lock as the player would: pinches the stick, moves the hand along its axis in
 * 4 mm steps through [Bench.drag] and lets go through [release]. Null when nothing is left; "busy" while a stick is
 * still on its way out.
 */
internal fun hookBurrMove(b: Bench): String? {
    val s = b.slide ?: return null
    if (b.busy) return "busy"
    val m = synchronized(Game.lock) { s.puzzle.solution()?.firstOrNull() } ?: return null
    val ok = synchronized(Game.lock) { b.grabOf(s.holder(m.piece))?.let { b.begin(it) } ?: false }
    if (!ok) return "cannot grab ${m.piece}"
    PinchClock.dragAt = SystemClock.uptimeMillis() - 2 * TAP_MS
    val cells = m.to - synchronized(Game.lock) { s.puzzle.offset(m.piece, m.axis) }
    val dist = cells * com.vrgames.lubanbox.core.Burrs.CELL
    val u = when (m.axis) {
        com.vrgames.lubanbox.core.Axis.X -> Vec3(1.0, 0.0, 0.0)
        com.vrgames.lubanbox.core.Axis.Y -> Vec3(0.0, 1.0, 0.0)
        com.vrgames.lubanbox.core.Axis.Z -> Vec3(0.0, 0.0, 1.0)
    } * dist
    val n = maxOf(3, (kotlin.math.abs(dist) / 0.004).toInt())
    repeat(n) { synchronized(Game.lock) { b.drag(b.roomDelta(u * (1.0 / n))) } }
    val r = release(b, "hook")
    return "$m -> ${describe(b, r)}"
}

/**
 * v0.10.1 test hook: pinches sliding piece [piece] and moves the hand [meters] along [axis] (object frame) in 2 mm steps
 * through [Bench.drag], as the player would, then lets go through [release]. Reports the farthest the piece was shown
 * (steps), what the release did, and for the 鲁班盒 the free room and the deepest overlap of the move.
 */
internal fun hookSlideDrag(b: Bench, piece: Int, axis: com.vrgames.lubanbox.core.Axis, meters: Double): String {
    val s = b.slide ?: return "no slide"
    val box = s.puzzle as? com.vrgames.lubanbox.core.PuzzleBox
    val before = synchronized(Game.lock) { box?.positions()?.get(piece) }
    val free = synchronized(Game.lock) {
        box?.let { p -> val sign = if (meters * p.panels[piece].direction > 0) 1 else -1; p.geometricFree(piece, before!!, sign, p.positions()) }
    }
    val ok = synchronized(Game.lock) { b.grabOf(s.holder(piece))?.let { b.begin(it) } ?: false }
    if (!ok) return "cannot grab $piece"
    PinchClock.dragAt = SystemClock.uptimeMillis() - 2 * TAP_MS
    val u = com.vrgames.lubanbox.core.Sweep.unit(axis) * meters
    val n = maxOf(3, (kotlin.math.abs(meters) / 0.002).toInt())
    var far = 0.0
    var bumps = 0
    repeat(n) {
        val felt = synchronized(Game.lock) { b.drag(b.roomDelta(u * (1.0 / n))) }
        if (felt == Bench.Felt.Bump) bumps++
        val o = synchronized(Game.lock) { s.shownOffset(piece, axis) }
        if (kotlin.math.abs(o) > kotlin.math.abs(far)) far = o
    }
    val r = release(b, "hook")
    val after = synchronized(Game.lock) { box?.positions()?.get(piece) }
    val worst = synchronized(Game.lock) {
        if (box != null && before != null && after != null && after != before) box.sweepWorst(piece, before, after, box.positions().also { it[piece] = before }) else null
    }
    return "hand ${"%.1f".format(meters * 1000)} mm, shown at most ${"%.2f".format(far)} steps, bumps $bumps, free ${free?.let { if (it.isInfinite()) "∞" else "%.1f".format(it * 1000) }} mm, " +
        "position $before→$after, worst overlap ${worst?.let { "%.2f".format(maxOf(0.0, it) * 1000) } ?: "-"} mm -> ${describe(b, r)}"
}

internal fun afterMove(b: Bench, piece: Int?) {
    val out = piece != null && synchronized(Game.lock) { b.slide?.puzzle?.removed(piece) == true }
    if (out) Game.sound?.play(Sfx.OUT, Vec3.ZERO)
    when (Game.afterMove()) {
        Workshop.Outcome.OPENED, Workshop.Outcome.OPENED_AGAIN -> {
            Game.sound?.play(Sfx.OPEN, Vec3.ZERO)
            synchronized(Game.lock) { b.celebrate() }
        }
        Workshop.Outcome.NEXT_ROUND -> Game.sound?.play(Sfx.SNAP, Vec3.ZERO)
        Workshop.Outcome.MOVED -> Unit
    }
}

/** Whether the solver still has a move for the relic on the bench. */
private fun canStep(b: Bench): Boolean = synchronized(Game.lock) {
    when {
        b.slide != null -> b.slide?.puzzle?.let { !it.solved } ?: false
        b.rotary != null -> b.rotary?.puzzle?.let { !it.solved } ?: false
        b.build != null -> b.build?.assembly?.let { !it.solved } ?: false
        else -> false
    }
}

/** AutoPilot: one solver move, applied through the rules and animated by the views. */
private fun autoStep(b: Bench) {
    val moved = synchronized(Game.lock) { b.autoStep() } ?: return
    // A stick still on its way out: this step waits.
    if (moved == -2) return
    afterMove(b, moved.takeIf { it >= 0 })
}
