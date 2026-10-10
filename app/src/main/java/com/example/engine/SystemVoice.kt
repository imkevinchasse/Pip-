package com.example.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.core.Voice
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class VoiceProfile(val id: String, val name: String, val icon: String, val pitch: Float, val rate: Float) {
    companion object {
        val ALL = listOf(
            VoiceProfile("pip", "Pip", "🐾", 1.55f, 0.92f),
            VoiceProfile("squeak", "Squeaky", "🎈", 2.0f, 1.05f),
            VoiceProfile("soft", "Soft", "🧸", 1.25f, 0.85f),
            VoiceProfile("natural", "Natural", "👤", 1.0f, 1.0f)
        )
    }
}

/**
 * Pip's voice: the phone's built-in text-to-speech, restricted to voices that work offline.
 * It costs no extra download and almost no RAM inside Pip's own process.
 *
 * (This is deliberately not Piper: Piper needs its own native phonemizer, which cannot be
 * added and tested safely here. See the README notes. The [Voice] interface makes swapping
 * it in later a one-class change.)
 */
class SystemVoice(
    context: Context,
    private val scope: CoroutineScope,
    private val onReadyChanged: (ready: Boolean, message: String?) -> Unit
) : Voice {

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val waiting = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    var pitch: Float = VoiceProfile.ALL.first().pitch
    var rate: Float = VoiceProfile.ALL.first().rate

    private val _amplitude = MutableStateFlow(0f)
    /** An animation level for the mascot while talking. It is a visual effect, not a measurement. */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()
    private var animationJob: Job? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                onReadyChanged(false, "This phone's text-to-speech did not start.")
                return@TextToSpeech
            }
            val language = engine.setLanguage(Locale.US)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                onReadyChanged(false, "No English voice installed. Add one in Settings > Text-to-speech.")
                return@TextToSpeech
            }
            val offlineProblem = selectOfflineVoice(engine)
            if (offlineProblem != null) {
                onReadyChanged(false, offlineProblem)
                return@TextToSpeech
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    startAnimation()
                }

                override fun onDone(utteranceId: String?) = finish(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finish(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
            })
            ready = true
            onReadyChanged(true, null)
        }
    }

    /** Pick an English voice that does not need the internet. Returns a problem text if none. */
    private fun selectOfflineVoice(engine: TextToSpeech): String? {
        val voices = try {
            engine.voices
        } catch (_: Throwable) {
            null
        }
        if (voices.isNullOrEmpty()) return null // engine does not list voices; trust its default

        val english = voices.filter { it.locale.language == "en" }
        val offline = english.filter { !it.isNetworkConnectionRequired }
        if (english.isNotEmpty() && offline.isEmpty()) {
            return "Pip only found online voices. Install an offline English voice in Settings > Text-to-speech."
        }
        val chosen = offline.firstOrNull { it.locale == Locale.US } ?: offline.firstOrNull()
        if (chosen != null) {
            try {
                engine.voice = chosen
            } catch (_: Throwable) {
            }
        }
        return null
    }

    fun applyProfile(profile: VoiceProfile) {
        pitch = profile.pitch
        rate = profile.rate
    }

    override suspend fun speak(text: String) {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) return

        val id = UUID.randomUUID().toString()
        // If the engine never reports back, do not hang the conversation forever.
        val timeoutMs = (text.length * 140L).coerceIn(4_000L, 40_000L)
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { continuation ->
                waiting[id] = continuation
                continuation.invokeOnCancellation {
                    waiting.remove(id)
                    try {
                        engine.stop()
                    } catch (_: Throwable) {
                    }
                }
                engine.setPitch(pitch.coerceIn(0.5f, 2.5f))
                engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
                val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
                if (queued != TextToSpeech.SUCCESS) finish(id)
            }
        }
        waiting.remove(id)
        stopAnimation()
    }

    override fun stop() {
        try {
            tts?.stop() // triggers onStop, which resumes the waiting speak()
        } catch (_: Throwable) {
        }
        for (id in waiting.keys.toList()) finish(id)
        stopAnimation()
    }

    private fun finish(utteranceId: String?) {
        if (utteranceId == null) return
        val continuation = waiting.remove(utteranceId) ?: return
        if (continuation.isActive) continuation.resume(Unit)
        stopAnimation()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        ready = false
        stopAnimation()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (true) {
                phase += 0.55
                val level = abs(sin(phase)) * 0.6 + abs(sin(phase * 2.3)) * 0.3 + Math.random() * 0.1
                _amplitude.value = level.toFloat().coerceIn(0.1f, 1f)
                delay(40)
            }
        }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
        _amplitude.value = 0f
    }
}
