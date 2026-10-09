package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.PiperPetAudioEngine
import com.example.engine.SmolLM2FracturedEngine
import com.example.engine.WhisperSttEngine
import com.example.manager.ConversationalCache
import com.example.manager.LocalModelServer
import com.example.manager.ModelDownloadManager
import com.example.manager.PhasedMemoryOrchestrator
import com.example.model.ChatMessage
import com.example.model.ModelId
import com.example.model.PipelineMetrics
import com.example.model.PipelineStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

class PetOracleViewModel(application: Application) : AndroidViewModel(application) {

    val cache = ConversationalCache()
    val downloadManager = ModelDownloadManager(application, viewModelScope)
    val memoryOrchestrator = PhasedMemoryOrchestrator()
    val smolLmEngine = SmolLM2FracturedEngine()
    val piperAudioEngine = PiperPetAudioEngine(application, viewModelScope)
    val whisperSttEngine = WhisperSttEngine(application, viewModelScope)
    val localServer = LocalModelServer(smolLmEngine, downloadManager, viewModelScope, port = 8080)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentStreamingText = MutableStateFlow("")
    val currentStreamingText: StateFlow<String> = _currentStreamingText.asStateFlow()

    private val _metrics = MutableStateFlow(PipelineMetrics())
    val metrics: StateFlow<PipelineMetrics> = _metrics.asStateFlow()

    private val _showModelSheet = MutableStateFlow(false)
    val showModelSheet: StateFlow<Boolean> = _showModelSheet.asStateFlow()

    private val _mascotMood = MutableStateFlow("happy") // happy, curious, talking, surprised
    val mascotMood: StateFlow<String> = _mascotMood.asStateFlow()

    // Combined live audio visualizer amplitude
    val liveVisualizerAmplitude: StateFlow<Float> = combine(
        memoryOrchestrator.currentStage,
        whisperSttEngine.micAmplitude,
        piperAudioEngine.currentAmplitude
    ) { stage, micAmp, ttsAmp ->
        when (stage) {
            PipelineStage.LISTENING -> micAmp
            PipelineStage.SPEAKING -> ttsAmp
            PipelineStage.THINKING -> 0.35f
            PipelineStage.INTERRUPTED -> 0.85f
            PipelineStage.IDLE -> 0.05f
        }
    }.let { flow ->
        val state = MutableStateFlow(0f)
        viewModelScope.launch {
            flow.collect { state.value = it }
        }
        state.asStateFlow()
    }

    private var currentInferenceJob: Job? = null

    init {
        setupEngineCallbacks()
        localServer.start()
        // Seed initial friendly greeting
        viewModelScope.launch {
            val initial = ChatMessage(
                id = UUID.randomUUID().toString(),
                userQuery = "hello Pip!",
                fracturedResponse = "Pip is Pip! You is here, I is here. Sky have room for both of us.",
                sttLatencyMs = 12L,
                llmLatencyMs = 45L,
                ttsLatencyMs = 38L,
                wasCached = true
            )
            _messages.value = listOf(initial)
        }
    }

    private fun setupEngineCallbacks() {
        whisperSttEngine.onSpeechRecognized = { text ->
            processUserQuery(text, isSpoken = true)
        }

        whisperSttEngine.onSpeechInterruption = {
            if (memoryOrchestrator.currentStage.value == PipelineStage.SPEAKING ||
                piperAudioEngine.isPlaying.value
            ) {
                handleUserInterruption()
            }
        }
    }

    fun setDynamicListening(enabled: Boolean) {
        whisperSttEngine.setDynamicListening(enabled)
        if (enabled) {
            memoryOrchestrator.transitionToStage(PipelineStage.LISTENING)
            _mascotMood.value = "curious"
        } else {
            memoryOrchestrator.transitionToStage(PipelineStage.IDLE)
            _mascotMood.value = "happy"
        }
    }

    fun handleUserInterruption() {
        currentInferenceJob?.cancel()
        piperAudioEngine.interruptImmediately()
        memoryOrchestrator.transitionToStage(PipelineStage.INTERRUPTED)
        _mascotMood.value = "surprised"

        _metrics.value = _metrics.value.copy(
            currentStage = PipelineStage.INTERRUPTED,
            totalInterruptionCount = _metrics.value.totalInterruptionCount + 1
        )

        // Mark last message as interrupted if applicable
        val list = _messages.value.toMutableList()
        if (list.isNotEmpty()) {
            val last = list.first()
            if (last.isPlaying) {
                list[0] = last.copy(wasInterrupted = true, isPlaying = false)
                _messages.value = list
            }
        }

        viewModelScope.launch {
            kotlinx.coroutines.delay(450)
            if (whisperSttEngine.isDynamicListeningEnabled.value) {
                memoryOrchestrator.transitionToStage(PipelineStage.LISTENING)
                _mascotMood.value = "curious"
            } else {
                memoryOrchestrator.transitionToStage(PipelineStage.IDLE)
                _mascotMood.value = "happy"
            }
        }
    }

    fun processUserQuery(query: String, isSpoken: Boolean = false) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        currentInferenceJob?.cancel()
        currentInferenceJob = viewModelScope.launch(Dispatchers.Default) {
            val sttTime = if (isSpoken) 65L else 0L

            // Phase 2: Thinking / Fractured Inference (seamless, user sees Pip thinking)
            memoryOrchestrator.transitionToStage(PipelineStage.THINKING)
            _mascotMood.value = "thinking"

            val cached = cache.get(trimmed)
            val fracturedText: String
            val llmLatency: Long
            val wasCacheHit: Boolean

            if (cached != null) {
                wasCacheHit = true
                llmLatency = 8L
                fracturedText = cached.fracturedResponse
            } else {
                wasCacheHit = false
                val startLlm = System.currentTimeMillis()
                fracturedText = smolLmEngine.generateFracturedWisdom(trimmed)
                llmLatency = System.currentTimeMillis() - startLlm
                cache.put(trimmed, fracturedText)
            }

            // Phase 3: Speaking with Helium Pet voice
            memoryOrchestrator.transitionToStage(PipelineStage.SPEAKING)
            _mascotMood.value = "talking"

            val startTts = System.currentTimeMillis()
            val messageId = UUID.randomUUID().toString()

            val msg = ChatMessage(
                id = messageId,
                userQuery = trimmed,
                fracturedResponse = fracturedText,
                sttLatencyMs = sttTime,
                llmLatencyMs = llmLatency,
                ttsLatencyMs = 42L,
                wasCached = wasCacheHit,
                isPlaying = true
            )

            _messages.value = listOf(msg) + _messages.value

            _metrics.value = _metrics.value.copy(
                currentStage = PipelineStage.SPEAKING,
                lastSttLatencyMs = sttTime,
                lastLlmLatencyMs = llmLatency,
                lastTtsLatencyMs = 42L,
                cacheHitCount = cache.hitCount,
                cacheMissCount = cache.missCount
            )

            // Trigger helium pet speech output
            piperAudioEngine.speak(fracturedText) {
                // Done speaking
                val currentList = _messages.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == messageId }
                if (index != -1) {
                    currentList[index] = currentList[index].copy(isPlaying = false)
                    _messages.value = currentList
                }

                if (whisperSttEngine.isDynamicListeningEnabled.value) {
                    memoryOrchestrator.transitionToStage(PipelineStage.LISTENING)
                    _mascotMood.value = "curious"
                } else {
                    memoryOrchestrator.transitionToStage(PipelineStage.IDLE)
                    _mascotMood.value = "happy"
                }
            }
        }
    }

    fun replayAudio(msg: ChatMessage) {
        piperAudioEngine.interruptImmediately()
        memoryOrchestrator.transitionToStage(PipelineStage.SPEAKING)
        _mascotMood.value = "talking"

        val updated = _messages.value.map {
            if (it.id == msg.id) it.copy(isPlaying = true) else it.copy(isPlaying = false)
        }
        _messages.value = updated

        piperAudioEngine.speak(msg.fracturedResponse) {
            val finalUpdated = _messages.value.map {
                if (it.id == msg.id) it.copy(isPlaying = false) else it
            }
            _messages.value = finalUpdated
            if (whisperSttEngine.isDynamicListeningEnabled.value) {
                memoryOrchestrator.transitionToStage(PipelineStage.LISTENING)
                _mascotMood.value = "curious"
            } else {
                memoryOrchestrator.transitionToStage(PipelineStage.IDLE)
                _mascotMood.value = "happy"
            }
        }
    }

    fun setHeliumPitch(pitch: Float) {
        piperAudioEngine.pitchFactor = pitch
    }

    fun setSpeechSpeed(speed: Float) {
        piperAudioEngine.speechRate = speed
    }

    fun setVolumeLevel(vol: Float) {
        piperAudioEngine.volumeLevel = vol
    }

    fun setPitchVariance(v: Float) {
        piperAudioEngine.pitchVariance = v
    }

    fun setFormantShift(f: Float) {
        piperAudioEngine.formantShift = f
    }

    fun setPhonemeLength(l: Float) {
        piperAudioEngine.phonemeLengthScale = l
    }

    fun applyVoiceProfile(profile: com.example.engine.VoiceProfile) {
        piperAudioEngine.applyProfile(profile)
    }

    fun toggleModelSheet(show: Boolean) {
        _showModelSheet.value = show
    }

    fun pokePip() {
        onMascotHeadClick()
    }

    fun onMascotHeadClick() {
        when (memoryOrchestrator.currentStage.value) {
            PipelineStage.LISTENING -> {
                // Click again to process what was said! Head turns small while thinking!
                val capturedText = whisperSttEngine.stopHeadClickListeningAndProcess()
                processUserQuery(capturedText, isSpoken = true)
            }
            PipelineStage.SPEAKING -> {
                // Interruption
                handleUserInterruption()
            }
            PipelineStage.THINKING -> {
                // Currently processing
            }
            PipelineStage.IDLE, PipelineStage.INTERRUPTED -> {
                // Click to speak! Head turns big!
                memoryOrchestrator.transitionToStage(PipelineStage.LISTENING)
                _mascotMood.value = "curious"
                whisperSttEngine.startHeadClickListening()
            }
        }
    }

    fun enrollUserVoice() {
        whisperSttEngine.enrollUserVoice()
    }

    fun toggleOwnerVoiceOnly(enabled: Boolean) {
        whisperSttEngine.setOwnerOnlyMode(enabled)
    }

    fun restartLocalServer() {
        localServer.stop()
        localServer.start()
    }

    fun updateModelUrl(id: ModelId, url: String) {
        downloadManager.setModelDownloadUrl(id, url)
    }

    fun fastInstallAllModels() {
        downloadManager.fastInstallAllModels()
    }

    override fun onCleared() {
        super.onCleared()
        localServer.stop()
        piperAudioEngine.release()
        whisperSttEngine.release()
    }
}
