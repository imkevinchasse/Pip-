package com.example.core

import com.example.manager.ConversationalCache
import com.example.model.AnswerSource
import com.example.model.ChatMessage
import com.example.model.PipelineStage
import com.example.personality.FracturedEnglish
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The language model. Returns null when it is not available or produced nothing. */
interface Brain {
    suspend fun generate(query: String): String?
    /** Free the model's memory. It reloads itself the next time [generate] is called. */
    fun release()
}

/** The mouth. [speak] returns when the speech finished, or was stopped. */
interface Voice {
    suspend fun speak(text: String)
    fun stop()
}

/** The microphone. Pip must never listen while it is speaking, or it hears itself. */
interface Ears {
    fun pause()
    fun resume()
    /** Free the speech model's memory (only called while not listening). */
    fun release()
}

/**
 * Runs one conversation turn at a time:
 * hear -> decide the answer -> speak (microphone closed) -> back to listening.
 *
 * Answer order is deliberate:
 *   1. safety  (crisis messages get a clear, caring answer, never a joke)
 *   2. Pip's designed signature answers
 *   3. memory of recent answers
 *   4. the on-device language model, forced into Pip's voice
 *   5. Pip's own pattern brain (always works, even with no models downloaded)
 */
class PipelineController(
    private val scope: CoroutineScope,
    private val brain: Brain,
    private val voice: Voice,
    private val ears: Ears,
    private val cache: ConversationalCache = ConversationalCache(),
    private val brainTimeoutMs: Long = 25_000L,
    private val idleReleaseMs: Long = 60_000L,
    private val random: Random = Random.Default,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val _stage = MutableStateFlow(PipelineStage.IDLE)
    val stage: StateFlow<PipelineStage> = _stage.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    /** Newest first. */
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    @Volatile
    var listeningWanted: Boolean = false
        private set

    private var turnJob: Job? = null
    private var idleJob: Job? = null

    fun setListening(enabled: Boolean) {
        listeningWanted = enabled
        if (_stage.value == PipelineStage.IDLE || _stage.value == PipelineStage.LISTENING) {
            _stage.value = if (enabled) PipelineStage.LISTENING else PipelineStage.IDLE
        }
        if (!enabled) scheduleIdleRelease() else idleJob?.cancel()
    }

    fun greet() {
        if (_messages.value.isNotEmpty()) return
        _messages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                userQuery = "hello Pip!",
                fracturedResponse = FracturedEnglish.GREETING,
                timestamp = clock(),
                source = AnswerSource.DESIGNED
            )
        )
    }

    /** Called by the speech-to-text engine with a finished sentence. */
    fun onHeard(text: String) {
        // Anything heard while Pip is busy is almost certainly Pip's own voice or room noise.
        if (_stage.value == PipelineStage.THINKING || _stage.value == PipelineStage.SPEAKING) return
        startTurn(text, spoken = true)
    }

    /** Typed or tapped questions always win and interrupt whatever Pip is doing. */
    fun ask(text: String) {
        startTurn(text, spoken = false)
    }

    fun interrupt() {
        val running = turnJob
        if (running == null || !running.isActive) return
        _stage.value = PipelineStage.INTERRUPTED
        voice.stop()
        running.cancel()
    }

    fun replay(message: ChatMessage) {
        startTurn(message.userQuery, spoken = false, forcedAnswer = message.fracturedResponse, addMessage = false)
    }

    @Synchronized
    private fun startTurn(
        rawText: String,
        spoken: Boolean,
        forcedAnswer: String? = null,
        addMessage: Boolean = true
    ) {
        val text = rawText.trim()
        if (text.isBlank()) return

        turnJob?.let {
            if (it.isActive) {
                voice.stop()
                it.cancel()
            }
        }
        idleJob?.cancel()

        turnJob = scope.launch {
            var messageId: String? = null
            try {
                _stage.value = PipelineStage.THINKING
                val startedAt = clock()
                val (answer, source) = if (forcedAnswer != null) {
                    forcedAnswer to AnswerSource.CACHE
                } else {
                    decide(text)
                }
                val thinkMs = clock() - startedAt

                if (addMessage) {
                    val id = UUID.randomUUID().toString()
                    messageId = id
                    _messages.value = listOf(
                        ChatMessage(
                            id = id,
                            userQuery = text,
                            fracturedResponse = answer,
                            timestamp = clock(),
                            source = source,
                            thinkMs = thinkMs,
                            wasSpoken = spoken,
                            isPlaying = true
                        )
                    ) + _messages.value
                } else {
                    messageId = _messages.value.firstOrNull { it.fracturedResponse == answer }?.id
                    markPlaying(messageId, true)
                }

                _stage.value = PipelineStage.SPEAKING
                ears.pause()
                voice.speak(answer)
            } finally {
                // Always runs, including when interrupted: never leave the mic closed.
                withContext(NonCancellable) {
                    val wasInterrupted = _stage.value == PipelineStage.INTERRUPTED
                    markPlaying(messageId, false, wasInterrupted)
                    if (wasInterrupted) delay(250)
                    ears.resume()
                    _stage.value = if (listeningWanted) PipelineStage.LISTENING else PipelineStage.IDLE
                    scheduleIdleRelease()
                }
            }
        }
    }

    private suspend fun decide(query: String): Pair<String, AnswerSource> {
        FracturedEnglish.safetyReply(query)?.let { return it to AnswerSource.SAFETY }
        FracturedEnglish.goldenAnswer(query)?.let { return it to AnswerSource.DESIGNED }
        cache.get(query)?.let { return it to AnswerSource.CACHE }

        val raw = try {
            withTimeoutOrNull(brainTimeoutMs) { brain.generate(query) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            null // A broken model must never break Pip.
        }
        val polished = raw?.let { FracturedEnglish.polish(it, query) }
        if (polished != null) {
            cache.put(query, polished)
            return polished to AnswerSource.MODEL
        }
        val instinct = FracturedEnglish.compose(query, random)
        cache.put(query, instinct)
        return instinct to AnswerSource.INSTINCT
    }

    private fun markPlaying(id: String?, playing: Boolean, interrupted: Boolean = false) {
        if (id == null) return
        _messages.value = _messages.value.map {
            if (it.id == id) it.copy(isPlaying = playing, wasInterrupted = it.wasInterrupted || interrupted) else it
        }
    }

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleReleaseMs)
            if (!isActive) return@launch
            val busy = turnJob?.isActive == true
            if (!busy) {
                brain.release()
                if (!listeningWanted) ears.release()
            }
        }
    }

    /** Stops everything (used when the screen is destroyed). */
    fun shutdown() {
        listeningWanted = false
        idleJob?.cancel()
        turnJob?.cancel()
        voice.stop()
        brain.release()
        ears.release()
    }
}
