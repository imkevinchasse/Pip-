package com.example.model

enum class ModelId(
    val title: String,
    val subtitle: String,
    val estimatedSizeMb: Float,
    val primaryFunction: String,
    val phaseRole: String
) {
    WHISPER_SMALL(
        title = "Whisper Small",
        subtitle = "Acoustic Speech-to-Text Listener",
        estimatedSizeMb = 142.0f,
        primaryFunction = "Continuous dynamic audio stream transcription",
        phaseRole = "Phase 1: Dynamic Listening"
    ),
    SMOLLM2_135M(
        title = "SmolLM2-135M (Q4_K)",
        subtitle = "jc-builds/SmolLM2-135M-Instruct-Q4_K_M-GGUF",
        estimatedSizeMb = 98.5f,
        primaryFunction = "Fractured, inspiring pet-philosophy generator",
        phaseRole = "Phase 2: Fractured Inference"
    ),
    PIPER_TTS(
        title = "Piper TTS",
        subtitle = "High-Pitch Helium Pet Voice Engine",
        estimatedSizeMb = 64.0f,
        primaryFunction = "Sub-50ms ultra-low latency voice synthesis",
        phaseRole = "Phase 3: Helium Pet Voice"
    )
}

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY_ON_DISK,
    ACTIVE_IN_RAM,
    HIBERNATED
}

data class ModelItem(
    val id: ModelId,
    val status: ModelStatus = ModelStatus.READY_ON_DISK,
    val downloadProgress: Float = 1.0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val localFilePath: String = "",
    val activeRamMb: Float = 0f
)

enum class PipelineStage {
    IDLE,
    LISTENING,    // Dynamic Whisper listening
    THINKING,     // SmolLM2-135M generating fractured wisdom
    SPEAKING,     // Piper TTS emitting helium pet voice
    INTERRUPTED   // Rapid user speech or touch cut-in
}

data class ChatMessage(
    val id: String,
    val userQuery: String,
    val fracturedResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sttLatencyMs: Long = 0L,
    val llmLatencyMs: Long = 0L,
    val ttsLatencyMs: Long = 0L,
    val wasCached: Boolean = false,
    val wasInterrupted: Boolean = false,
    val isPlaying: Boolean = false
)

data class PipelineMetrics(
    val currentStage: PipelineStage = PipelineStage.IDLE,
    val currentRamUsageMb: Float = 0f,
    val activePhaseName: String = "Standby",
    val lastSttLatencyMs: Long = 0L,
    val lastLlmLatencyMs: Long = 0L,
    val lastTtsLatencyMs: Long = 0L,
    val totalInterruptionCount: Int = 0,
    val cacheHitCount: Int = 0,
    val cacheMissCount: Int = 0
)
