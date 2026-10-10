package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.core.Ears
import com.example.core.SpeechFilter
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Offline speech-to-text using Vosk (Kaldi). No audio ever leaves the phone.
 *
 * Why this replaces Android's SpeechRecognizer: that service can stream audio to a server,
 * and it fights with any other microphone user. Here exactly ONE AudioRecord feeds the
 * recognizer and the amplitude meter, and the audio is thrown away while Pip is speaking,
 * so Pip cannot hear (and answer) itself.
 *
 * RAM: the small English model needs roughly 300 MB while loaded. It is freed by [release].
 */
class VoskSttEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val modelDir: () -> File
) : Ears {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** True while the speech model is being loaded from storage (a few seconds on first use). */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    /** A plain-language reason listening is not working, or null. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** Called on a background thread with each finished sentence. */
    @Volatile
    var onFinalText: ((String) -> Unit)? = null

    @Volatile private var paused = false
    @Volatile private var ignoreUntilMs = 0L

    private var model: Model? = null
    private var loopJob: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts the microphone loop. Returns false (with [problem] set) if it cannot start. */
    fun start(): Boolean {
        if (!hasPermission()) {
            _problem.value = "Pip needs microphone permission to hear you."
            return false
        }
        val dir = modelDir()
        if (!File(dir, "am").isDirectory) {
            _problem.value = "Pip's ears are not downloaded yet."
            return false
        }
        if (_isListening.value && loopJob?.isActive == true) return true

        _problem.value = null
        _isListening.value = true
        val previous = loopJob
        loopJob = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            runLoop(dir) { isActive }
        }
        return true
    }

    fun stop() {
        _isListening.value = false
        loopJob?.cancel()
        _micAmplitude.value = 0f
        _partialText.value = ""
    }

    override fun pause() {
        paused = true
        _micAmplitude.value = 0f
        _partialText.value = ""
    }

    override fun resume() {
        // Speaker echo keeps ringing for a moment after speech ends; ignore that tail too.
        ignoreUntilMs = System.currentTimeMillis() + 450
        paused = false
    }

    override fun release() {
        stop()
        val job = loopJob
        scope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            try {
                model?.close()
            } catch (_: Throwable) {
            }
            model = null
        }
    }

    // --------------------------------------------------------------------------------------

    private fun runLoop(dir: File, active: () -> Boolean) {
        var recognizer: Recognizer? = null
        var record: AudioRecord? = null
        // True unless the loop ends normally (because listening was stopped on purpose).
        var failed = true
        try {
            val loaded = loadModel(dir) ?: return
            recognizer = Recognizer(loaded, SAMPLE_RATE.toFloat())

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                _problem.value = "This phone does not support the microphone format Pip needs."
                return
            }
            @Suppress("MissingPermission") // checked in start()
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE) // about half a second of headroom
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _problem.value = "Could not open the microphone. Another app may be using it."
                return
            }
            record.startRecording()

            val buffer = ShortArray(SAMPLE_RATE / 10) // 100 ms
            var needsReset = false
            while (active() && _isListening.value) {
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) {
                    _problem.value = "The microphone stopped working."
                    break
                }
                if (n == 0) continue

                if (paused || System.currentTimeMillis() < ignoreUntilMs) {
                    needsReset = true
                    _micAmplitude.value = 0f
                    continue
                }
                if (needsReset) {
                    recognizer.reset() // forget anything half-heard around Pip's own voice
                    needsReset = false
                }

                _micAmplitude.value = amplitudeOf(buffer, n)

                if (recognizer.acceptWaveForm(buffer, n)) {
                    _partialText.value = ""
                    val text = SpeechFilter.fromRecognizerJson(recognizer.getResult(), "text")
                    SpeechFilter.clean(text)?.let { onFinalText?.invoke(it) }
                } else {
                    _partialText.value = SpeechFilter.fromRecognizerJson(recognizer.getPartialResult(), "partial")
                }
            }
            failed = false
        } catch (e: Throwable) {
            _problem.value = "Listening stopped: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            try {
                record?.stop()
            } catch (_: Throwable) {
            }
            try {
                record?.release()
            } catch (_: Throwable) {
            }
            try {
                recognizer?.close()
            } catch (_: Throwable) {
            }
            _micAmplitude.value = 0f
            _partialText.value = ""
            // Only an error switches listening off here. A deliberate stop() already did, and a
            // quick stop()+start() must not have its new session switched off by this old one.
            if (failed) _isListening.value = false
        }
    }

    private fun loadModel(dir: File): Model? {
        model?.let { return it }
        _isLoading.value = true
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(dir.absolutePath).also { model = it }
        } catch (e: Throwable) {
            _problem.value = "Pip could not load its ears. Try downloading them again."
            _isListening.value = false
            null
        } finally {
            _isLoading.value = false
        }
    }

    private fun amplitudeOf(buffer: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) sum += buffer[i].toDouble() * buffer[i]
        val rms = sqrt(sum / n) / 32768.0
        return (rms * 6.0).toFloat().coerceIn(0.02f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
