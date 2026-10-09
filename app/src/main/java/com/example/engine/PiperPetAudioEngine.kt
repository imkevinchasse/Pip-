package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

data class VoiceProfile(
    val id: String,
    val name: String,
    val icon: String,
    val pitch: Float,
    val speed: Float,
    val volume: Float,
    val pitchVariance: Float,
    val formantShift: Float,
    val phonemeLength: Float
) {
    companion object {
        val ALL = listOf(
            VoiceProfile("helium", "Pure Helium", "🎈", 1.85f, 1.20f, 1.0f, 0.90f, 1.50f, 0.95f),
            VoiceProfile("squeak", "Squeaky Pet", "🐾", 2.15f, 1.35f, 1.1f, 1.10f, 1.70f, 0.85f),
            VoiceProfile("cosmic", "Cosmic Oracle", "🪐", 1.60f, 1.05f, 1.0f, 0.75f, 1.30f, 1.05f),
            VoiceProfile("chipmunk", "Hyper Chipmunk", "🐿️", 2.30f, 1.45f, 1.15f, 1.20f, 1.80f, 0.80f),
            VoiceProfile("plushie", "Soft Plushie", "🧸", 1.40f, 0.95f, 0.9f, 0.60f, 1.15f, 1.10f),
            VoiceProfile("natural", "Natural Voice", "👤", 1.00f, 1.00f, 1.0f, 0.50f, 1.00f, 1.00f)
        )
    }
}

class PiperPetAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    // 1. Helium Pitch Multiplier (0.5x - 2.5x)
    var pitchFactor: Float = 1.82f
        set(value) {
            field = value.coerceIn(0.5f, 2.5f)
            tts?.setPitch(field)
        }

    // 2. Speech Rate / Tempo (0.5x - 2.2x)
    var speechRate: Float = 1.20f
        set(value) {
            field = value.coerceIn(0.5f, 2.2f)
            tts?.setSpeechRate(field)
        }

    // 3. Audio Volume / Gain (0.1x - 1.5x)
    var volumeLevel: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 1.5f)
        }

    // 4. Intonation / Pitch Variance (Expressiveness: 0.1x - 1.5x)
    var pitchVariance: Float = 0.85f
        set(value) {
            field = value.coerceIn(0.1f, 1.5f)
        }

    // 5. Helium Formant Shift / Resonance (0.8x - 2.2x)
    var formantShift: Float = 1.45f
        set(value) {
            field = value.coerceIn(0.8f, 2.2f)
        }

    // 6. Phoneme Length Scale (0.6x - 1.6x)
    var phonemeLengthScale: Float = 0.95f
        set(value) {
            field = value.coerceIn(0.6f, 1.6f)
        }

    private var amplitudeJob: Job? = null
    private var activeUtteranceId: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    engine.language = Locale.US
                    engine.setPitch(pitchFactor)
                    engine.setSpeechRate(speechRate)
                    isTtsInitialized = true

                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId == activeUtteranceId) {
                                _isPlaying.value = true
                                startAmplitudeSimulation()
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == activeUtteranceId) {
                                _isPlaying.value = false
                                stopAmplitudeSimulation()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == activeUtteranceId) {
                                _isPlaying.value = false
                                stopAmplitudeSimulation()
                            }
                        }
                    })
                }
            }
        }
    }

    fun applyProfile(profile: VoiceProfile) {
        pitchFactor = profile.pitch
        speechRate = profile.speed
        volumeLevel = profile.volume
        pitchVariance = profile.pitchVariance
        formantShift = profile.formantShift
        phonemeLengthScale = profile.phonemeLength
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val utteranceId = UUID.randomUUID().toString()
        activeUtteranceId = utteranceId

        // Dynamic intonation check: if ending with a question, add micro-pitch tilt
        val effectivePitch = if (text.trim().endsWith("?")) {
            (pitchFactor * (1.0f + 0.12f * pitchVariance)).coerceIn(0.5f, 2.5f)
        } else {
            pitchFactor
        }

        // Apply phoneme duration adjustment to rate
        val effectiveRate = (speechRate / phonemeLengthScale).coerceIn(0.5f, 2.2f)

        tts?.let { engine ->
            engine.setPitch(effectivePitch)
            engine.setSpeechRate(effectiveRate)
            _isPlaying.value = true
            startAmplitudeSimulation()

            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeLevel.coerceIn(0.0f, 1.0f))
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } ?: run {
            scope.launch {
                _isPlaying.value = true
                startAmplitudeSimulation()
                delay((text.length * 45L).coerceIn(900L, 4200L))
                _isPlaying.value = false
                stopAmplitudeSimulation()
                onDone?.invoke()
            }
        }
    }

    fun interruptImmediately() {
        activeUtteranceId = null
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isPlaying.value = false
        stopAmplitudeSimulation()
        playInterruptChirp()
    }

    private fun startAmplitudeSimulation() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (_isPlaying.value) {
                phase += 0.45 * speechRate
                val amp = (kotlin.math.abs(sin(phase)) * 0.7f + kotlin.math.abs(sin(phase * 2.3)) * 0.3f).toFloat()
                _currentAmplitude.value = (amp * (0.4f + Math.random().toFloat() * 0.6f) * volumeLevel).coerceIn(0.1f, 1.0f)
                delay(30)
            }
            _currentAmplitude.value = 0f
        }
    }

    private fun stopAmplitudeSimulation() {
        amplitudeJob?.cancel()
        _currentAmplitude.value = 0f
    }

    private fun playInterruptChirp() {
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 22050
                val durationMs = 85
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = (900.0 * (pitchFactor / 1.82f)) + (700.0 * (i.toDouble() / numSamples))
                    val envelope = sin(Math.PI * (i.toDouble() / numSamples))
                    val sample = (sin(2.0 * Math.PI * freq * t) * 32767.0 * envelope * 0.45 * volumeLevel).toInt().toShort()
                    buffer[i] = sample
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                delay(120)
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
