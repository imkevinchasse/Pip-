package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PetOracleApp
import com.example.core.PipelineController
import com.example.engine.RamMeter
import com.example.engine.SmolLmBrain
import com.example.engine.SystemVoice
import com.example.engine.VoiceProfile
import com.example.engine.VoskSttEngine
import com.example.manager.ConversationalCache
import com.example.model.ChatMessage
import com.example.model.ModelId
import com.example.model.ModelStatus
import com.example.model.PipelineStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PetOracleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PetOracleApp

    val downloads = app.downloads
    val cache = ConversationalCache()
    val ramMeter = RamMeter(viewModelScope)

    val ears = VoskSttEngine(application, viewModelScope) { downloads.sttDir }
    val brain = SmolLmBrain(
        modelFile = { downloads.llmModelFile },
        tokenizerFile = { downloads.llmTokenizerFile }
    )
    val voice = SystemVoice(application, viewModelScope) { ready, message ->
        downloads.setTtsStatus(ready, message)
    }

    val controller = PipelineController(viewModelScope, brain, voice, ears, cache)

    val messages: StateFlow<List<ChatMessage>> = controller.messages
    val stage: StateFlow<PipelineStage> = controller.stage
    val modelsState = downloads.modelsState
    val ramMb: StateFlow<Float> = ramMeter.usedMb

    private val _wantListening = MutableStateFlow(true)
    val wantListening: StateFlow<Boolean> = _wantListening.asStateFlow()

    private val _showModelSheet = MutableStateFlow(false)
    val showModelSheet: StateFlow<Boolean> = _showModelSheet.asStateFlow()

    private val _voicePitch = MutableStateFlow(voice.pitch)
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()
    private val _voiceRate = MutableStateFlow(voice.rate)
    val voiceRate: StateFlow<Float> = _voiceRate.asStateFlow()

    /** The level the mascot and waveform react to: the real mic while listening. */
    val liveAmplitude: StateFlow<Float> = combine(
        controller.stage, ears.micAmplitude, voice.amplitude
    ) { stage, mic, speaking ->
        when (stage) {
            PipelineStage.LISTENING -> mic
            PipelineStage.SPEAKING -> speaking
            PipelineStage.THINKING -> 0.35f
            PipelineStage.INTERRUPTED -> 0.8f
            PipelineStage.IDLE -> 0.05f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.05f)

    /** What the user is told when something is not working. Plain language, never a stack trace. */
    val notice: StateFlow<String?> = combine(ears.problem, modelsState) { micProblem, models ->
        micProblem ?: models[ModelId.TTS]?.takeIf { it.status == ModelStatus.FAILED }?.error
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partialText: StateFlow<String> = ears.partialText
    val isLoadingEars: StateFlow<Boolean> = ears.isLoading

    init {
        ears.onFinalText = { text -> controller.onHeard(text) }
        controller.greet()
        ramMeter.start()

        viewModelScope.launch {
            // Whatever the reason listening ended (error, no permission), keep the UI honest.
            ears.isListening.collect { listening ->
                if (!listening && controller.listeningWanted) controller.setListening(false)
            }
        }
        viewModelScope.launch {
            modelsState.map { it[ModelId.STT]?.status }.distinctUntilChanged().collect { reconcileListening() }
        }
        viewModelScope.launch {
            modelsState.map { it[ModelId.LLM]?.status }.distinctUntilChanged().collect {
                if (it == ModelStatus.READY) brain.forgetFailure()
            }
        }
    }

    // ---- listening -------------------------------------------------------------------------

    fun toggleListening() {
        _wantListening.value = !_wantListening.value
        reconcileListening()
    }

    fun onPermissionResult() = reconcileListening()

    /** Makes the microphone match what the user wants and what is possible. */
    private fun reconcileListening() {
        val possible = ears.hasPermission() && downloads.isReady(ModelId.STT)
        val shouldListen = _wantListening.value && possible
        if (shouldListen && !ears.isListening.value) {
            if (ears.start()) controller.setListening(true)
        } else if (!shouldListen && ears.isListening.value) {
            ears.stop()
            controller.setListening(false)
        }
    }

    // ---- talking ---------------------------------------------------------------------------

    fun ask(text: String) = controller.ask(text)

    fun interrupt() = controller.interrupt()

    fun replay(message: ChatMessage) = controller.replay(message)

    fun onMascotTap() {
        when (controller.stage.value) {
            PipelineStage.SPEAKING, PipelineStage.THINKING -> controller.interrupt()
            else -> toggleListening()
        }
    }

    // ---- models ----------------------------------------------------------------------------

    fun downloadAll() = downloads.downloadAllMissing()
    fun download(id: ModelId) { downloads.download(id) }
    fun cancelDownload(id: ModelId) = downloads.cancel(id)
    fun deleteModel(id: ModelId) = downloads.delete(id)
    fun updateModelUrl(id: ModelId, url: String) = downloads.setUrl(id, url)
    fun clearMemory() = cache.clear()

    fun toggleModelSheet(show: Boolean) {
        _showModelSheet.value = show
    }

    // ---- voice -----------------------------------------------------------------------------

    fun applyVoiceProfile(profile: VoiceProfile) {
        voice.applyProfile(profile)
        _voicePitch.value = voice.pitch
        _voiceRate.value = voice.rate
    }

    fun setPitch(value: Float) {
        voice.pitch = value
        _voicePitch.value = value
    }

    fun setRate(value: Float) {
        voice.rate = value
        _voiceRate.value = value
    }

    fun testVoice() = controller.ask("who are you")

    override fun onCleared() {
        super.onCleared()
        ramMeter.stop()
        controller.shutdown()
        voice.shutdown()
    }
}
