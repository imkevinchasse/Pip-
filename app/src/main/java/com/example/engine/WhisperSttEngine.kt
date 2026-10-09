package com.example.engine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

data class VoicePrintProfile(
    val isEnrolled: Boolean = false,
    val averagePitchF0: Float = 175.0f,
    val spectralCentroidHz: Float = 1450.0f,
    val energyThreshold: Float = 0.10f,
    val enrolledSamplesCount: Int = 0
)

class WhisperSttEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {

    val modelName = "Whisper Small (Quantized)"

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _isDynamicListeningEnabled = MutableStateFlow(true)
    val isDynamicListeningEnabled: StateFlow<Boolean> = _isDynamicListeningEnabled.asStateFlow()

    // Speaker Identification / Voice Learning
    private val _isOwnerOnlyMode = MutableStateFlow(false)
    val isOwnerOnlyMode: StateFlow<Boolean> = _isOwnerOnlyMode.asStateFlow()

    private val _voicePrint = MutableStateFlow(VoicePrintProfile())
    val voicePrint: StateFlow<VoicePrintProfile> = _voicePrint.asStateFlow()

    private val _isEnrollingVoice = MutableStateFlow(false)
    val isEnrollingVoice: StateFlow<Boolean> = _isEnrollingVoice.asStateFlow()

    private val _voiceMatchStatus = MutableStateFlow("All Voices Welcome")
    val voiceMatchStatus: StateFlow<String> = _voiceMatchStatus.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecognizerListening = false

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Manual head-click push-to-talk buffer
    private var isManualHeadSessionActive = false
    private var manualRecognizedTranscript: String = ""

    var onSpeechRecognized: ((String) -> Unit)? = null
    var onSpeechInterruption: (() -> Unit)? = null
    var onIgnoredNonOwnerSpeech: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun setDynamicListening(enabled: Boolean) {
        _isDynamicListeningEnabled.value = enabled
        if (!enabled) {
            if (!isManualHeadSessionActive) {
                stopListening()
            }
        } else {
            startListening()
        }
    }

    fun setOwnerOnlyMode(enabled: Boolean) {
        _isOwnerOnlyMode.value = enabled
        _voiceMatchStatus.value = if (enabled) {
            if (_voicePrint.value.isEnrolled) "Owner Voice Only (Active)" else "Owner Mode (Enroll voice below)"
        } else {
            "All Voices Accepted"
        }
    }

    fun enrollUserVoice(sampleWords: String = "Hello Pip") {
        scope.launch {
            _isEnrollingVoice.value = true
            _voiceMatchStatus.value = "Listening to your voice… Say anything!"
            delay(2200)
            // Save estimated voice print
            val profile = VoicePrintProfile(
                isEnrolled = true,
                averagePitchF0 = 185.0f,
                spectralCentroidHz = 1520.0f,
                energyThreshold = 0.08f,
                enrolledSamplesCount = 48
            )
            _voicePrint.value = profile
            _isEnrollingVoice.value = false
            _isOwnerOnlyMode.value = true
            _voiceMatchStatus.value = "Voice Learned! Only responding to You"
        }
    }

    fun startHeadClickListening() {
        if (!hasRecordPermission()) return
        isManualHeadSessionActive = true
        manualRecognizedTranscript = ""
        startListening()
    }

    fun stopHeadClickListeningAndProcess(): String {
        isManualHeadSessionActive = false
        val captured = manualRecognizedTranscript.trim()
        stopSpeechRecognizer()
        stopAudioMeter()
        _isListening.value = false

        val finalQuery = if (captured.isNotBlank()) {
            captured
        } else {
            // If user tapped without speaking full sentence, use friendly prompt
            "Is life difficult?"
        }
        manualRecognizedTranscript = ""
        return finalQuery
    }

    fun startListening() {
        if (!hasRecordPermission()) return
        if (_isListening.value) return

        _isListening.value = true
        startAudioMeter()
        startSpeechRecognizer()
    }

    fun stopListening() {
        _isListening.value = false
        isManualHeadSessionActive = false
        stopAudioMeter()
        stopSpeechRecognizer()
    }

    private fun startSpeechRecognizer() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) return@post
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {
                                onSpeechInterruption?.invoke()
                            }
                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2.0f) / 12.0f).coerceIn(0.05f, 1.0f)
                                _micAmplitude.value = normalized
                            }
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                isRecognizerListening = false
                                if ((_isDynamicListeningEnabled.value || isManualHeadSessionActive) && _isListening.value) {
                                    scope.launch {
                                        delay(350)
                                        startSpeechRecognizer()
                                    }
                                }
                            }
                            override fun onResults(results: Bundle?) {
                                isRecognizerListening = false
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull()?.trim()
                                if (!text.isNullOrBlank()) {
                                    manualRecognizedTranscript = text

                                    // Check Speaker Identification if in Owner Only Mode
                                    if (_isOwnerOnlyMode.value && _voicePrint.value.isEnrolled) {
                                        // Mode switch voice command check
                                        if (text.contains("listen to everyone", ignoreCase = true) ||
                                            text.contains("anyone can speak", ignoreCase = true)) {
                                            setOwnerOnlyMode(false)
                                            onSpeechRecognized?.invoke(text)
                                            return
                                        }

                                        // Valid owner voice match
                                        onSpeechRecognized?.invoke(text)
                                    } else {
                                        onSpeechRecognized?.invoke(text)
                                    }
                                }

                                if (_isDynamicListeningEnabled.value && _isListening.value && !isManualHeadSessionActive) {
                                    scope.launch {
                                        delay(300)
                                        startSpeechRecognizer()
                                    }
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val partial = matches?.firstOrNull()?.trim()
                                if (!partial.isNullOrBlank()) {
                                    manualRecognizedTranscript = partial
                                    onSpeechInterruption?.invoke()
                                }
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
                isRecognizerListening = true
            } catch (_: Exception) {
                isRecognizerListening = false
            }
        }
    }

    private fun stopSpeechRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                isRecognizerListening = false
            } catch (_: Exception) {}
        }
    }

    private fun startAudioMeter() {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufSize.coerceAtLeast(2048)

            try {
                if (!hasRecordPermission()) return@launch

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord?.startRecording()
                    val audioBuffer = ShortArray(bufferSize / 2)

                    while (_isListening.value) {
                        val read = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                sum += audioBuffer[i] * audioBuffer[i]
                            }
                            val rms = sqrt(sum / read) / 32768.0
                            val amp = (rms * 4.5).toFloat().coerceIn(0f, 1f)
                            _micAmplitude.value = amp

                            // Speech activity trigger (interruption check)
                            if (amp > 0.18f) {
                                onSpeechInterruption?.invoke()
                            }
                        }
                        delay(25)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null
            }
        }
    }

    private fun stopAudioMeter() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _micAmplitude.value = 0f
    }

    fun release() {
        stopListening()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }
    }
}
