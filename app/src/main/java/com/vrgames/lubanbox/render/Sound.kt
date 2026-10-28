package com.vrgames.lubanbox.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.lubanbox.core.Vec3

/** Wood impacts from Kenney's Impact Sounds (CC0), trimmed by ffmpeg; see docs/素材来源.md. */
enum class Sfx(val file: String, val volume: Float) {
    /** A piece crosses a whole step: the click of a pin dropping into its notch. */
    TICK("tick", 0.45f),
    /** Pushed against something that does not give. */
    BUMP("bump", 0.8f),
    /** A part settles into place, a panel lets go. */
    SNAP("snap", 0.9f),
    /** A stick comes out of the lock. */
    OUT("out", 0.8f),
    /** A relic is opened. */
    OPEN("open", 0.7f),
    TURN("turn", 0.35f),
}

/**
 * Spatial sound from the bench: each effect has one long-lived emitter with a prepared player,
 * moved to where the sound happens and restarted (creating players per event drops frames).
 * Main thread only.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    var enabled = true

    /** Loads every clip; a clip that fails is skipped (logged). */
    fun load() {
        shared = this
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
    }

    /** Plays [s] at [at] (in [parent]'s frame). */
    fun play(s: Sfx, at: Vec3) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    companion object {
        private const val TAG = "LubanSound"

        /** The bench's sound, for scenes that tick on their own (the water clock). */
        var shared: Sound? = null
    }
}
