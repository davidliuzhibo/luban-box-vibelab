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
            b.placeInRoom(Game.benchHeight)
            sky?.transform()?.eulerAngles = EulerAngles(0f, yaw, 0f)
            Log.i("LubanStage", "bench faces the player at (${pose.position.x}, ${pose.position.z}), yaw $yaw")
            true
        }
    }

    DisposableEffect(Unit) {
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
            navigator.closeWindowContainer(ATLAS_ID)
            navigator.closeWindowContainer(NOTES_ID)
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
    LaunchedEffect(Game.hint, bench) { bench?.showHint(Game.hint) }
    LaunchedEffect(Game.workshopEnv, sky) { sky?.enabled = Game.workshopEnv }
    LaunchedEffect(Game.debugSky, sky) {
        val v = Game.debugSky?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: return@LaunchedEffect
        if (v.size == 3) sky?.transform()?.eulerAngles = EulerAngles(v[0], v[1], v[2])
    }
    LaunchedEffect(Game.coached, Game.showTitle, Game.relicId, Game.relicCardDismissed, bench) {
        // One explanation at a time: the first-move label waits for the relic card.
        bench?.showCoach(!Game.coached && !Game.showTitle && Game.relicId == "lock" && Game.relicCardDismissed)
    }
    // Scene panels: the title card, the "opened" card and the menu tab.
    LaunchedEffect(Game.showTitle, Game.opened, Game.relicId, bench) {
        val b = bench ?: return@LaunchedEffect
        b.showPanel(PANEL_INTRO, Game.showTitle)
        b.showPanel(PANEL_RESULT, !Game.showTitle && Game.opened == Game.relicId)
        b.showPanel(PANEL_MENU, !Game.showTitle)
    }
    // The open menu is about half a meter tall and hangs from its middle: lift it clear of the bench top.
    LaunchedEffect(Game.menuOpen, bench) { bench?.placePanel(PANEL_MENU, if (Game.menuOpen) MENU_OPEN_POS else MENU_POS) }
    LaunchedEffect(Game.showTitle, Game.relicCardDismissed, Game.opened, Game.relicId, bench) {
        bench?.showPanel(PANEL_RELIC, !Game.showTitle && !Game.relicCardDismissed && Game.opened != Game.relicId)
    }
    LaunchedEffect(Game.progressVersion, bench) {
        val b = bench ?: return@LaunchedEffect
        synchronized(Game.lock) { b.showTokens(Game.workshop) }
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
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
                    // A start while a drag is still open means its end never came: close it first.
                    if (b.dragging) release(b, "restart")
                    // The title card asks to start first.
                    val g = synchronized(Game.lock) { b.grabOf(value.targetEntity)?.takeIf { !Game.showTitle }?.also { b.begin(it) } }
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
                    Bench.Felt.Bump -> Game.sound?.play(Sfx.BUMP, Vec3.ZERO)
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
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(
                    converter,
                    openNotes = { navigator.openWindowContainer(NOTES_ID) },
                    openAtlas = { navigator.openWindowContainer(ATLAS_ID) },
                    quit = { context.findActivity()?.finishAndRemoveTask() },
                    faceMe = { facePlayer() },
                )
            }
        },
        update = { _, attachments ->
            bench?.let { b ->
                if (!b.hasCoach) b.attachCoach(attachments.entity(LABEL_FIRST))
                attachPanels(b, attachments)
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
            attachPanels(b, attachments)
            Game.sound = Sound(b.root).also { it.load() }
            synchronized(Game.lock) { b.show(Game.workshop) }
            bench = b

            AutoPilot.stepper = {
                val can = canStep(b)
                if (can) autoStep(b)
                can
            }
            AutoPilot.turner = { deg -> b.turnBy(deg) }

            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                synchronized(Game.lock) { b.frame(e.deltaTime.toDouble(), Game.workshop) }
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
private fun attachPanels(b: Bench, attachments: com.pico.spatial.ui.foundation.content.SpatialViewAttachments) {
    if (!b.hasPanel(PANEL_INTRO)) b.attachPanel(PANEL_INTRO, attachments.entity(PANEL_INTRO), Vector3(0f, 0.5f, -0.2f))
    // The relic and result cards stand to the right, so the relic itself stays in view.
    if (!b.hasPanel(PANEL_RESULT)) b.attachPanel(PANEL_RESULT, attachments.entity(PANEL_RESULT), Vector3(0.4f, 0.28f, -0.08f))
    if (!b.hasPanel(PANEL_RELIC)) b.attachPanel(PANEL_RELIC, attachments.entity(PANEL_RELIC), Vector3(0.4f, 0.28f, -0.08f))
    if (!b.hasPanel(PANEL_MENU)) b.attachPanel(PANEL_MENU, attachments.entity(PANEL_MENU), MENU_POS)
}

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

private fun release(b: Bench?, why: String) {
    if (b == null) return
    val r = synchronized(Game.lock) { b.end() }
    Log.i(INPUT_TAG, "$why -> ${r::class.simpleName}")
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
            AssemblyView.Drop.Loose -> Unit
        }
        Bench.Released.Nothing -> Unit
    }
}

private fun afterMove(b: Bench, piece: Int?) {
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
    afterMove(b, moved.takeIf { it >= 0 })
}
