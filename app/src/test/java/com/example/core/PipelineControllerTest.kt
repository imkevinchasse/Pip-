package com.example.core

import com.example.manager.ConversationalCache
import com.example.model.AnswerSource
import com.example.model.PipelineStage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineControllerTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    private class FakeEars : Ears {
        val paused = AtomicBoolean(false)
        val pauseCount = AtomicInteger(0)
        val resumeCount = AtomicInteger(0)
        val released = AtomicInteger(0)
        override fun pause() { paused.set(true); pauseCount.incrementAndGet() }
        override fun resume() { paused.set(false); resumeCount.incrementAndGet() }
        override fun release() { released.incrementAndGet() }
    }

    private class FakeBrain(private val behavior: suspend (String) -> String?) : Brain {
        val calls = AtomicInteger(0)
        val released = AtomicInteger(0)
        override suspend fun generate(query: String): String? { calls.incrementAndGet(); return behavior(query) }
        override fun release() { released.incrementAndGet() }
    }

    /** Speaks until [stop] is called or [durationMs] passes. Records whether the mic was closed. */
    private class FakeVoice(private val ears: FakeEars, private val durationMs: Long = 20) : Voice {
        val spoken = java.util.Collections.synchronizedList(ArrayList<String>())
        val micWasClosedWhileSpeaking = AtomicBoolean(true)
        val stopCalls = AtomicInteger(0)
        @Volatile private var stopSignal: CompletableDeferred<Unit>? = null
        override suspend fun speak(text: String) {
            if (!ears.paused.get()) micWasClosedWhileSpeaking.set(false)
            spoken.add(text)
            val signal = CompletableDeferred<Unit>()
            stopSignal = signal
            kotlinx.coroutines.withTimeoutOrNull(durationMs) { signal.await() }
        }
        override fun stop() { stopCalls.incrementAndGet(); stopSignal?.complete(Unit) }
    }

    private class Rig(
        scope: CoroutineScope,
        brainBehavior: suspend (String) -> String? = { null },
        voiceMs: Long = 20,
        brainTimeoutMs: Long = 1_000,
        idleMs: Long = 60_000
    ) {
        val ears = FakeEars()
        val brain = FakeBrain(brainBehavior)
        val voice = FakeVoice(ears, voiceMs)
        val controller = PipelineController(
            scope, brain, voice, ears, ConversationalCache(),
            brainTimeoutMs = brainTimeoutMs, idleReleaseMs = idleMs, random = Random(1)
        )
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("condition not met in ${timeoutMs}ms")
            delay(5)
        }
    }

    private fun Rig.settle() = waitUntil {
        controller.stage.value.let { it == PipelineStage.IDLE || it == PipelineStage.LISTENING } &&
            controller.messages.value.firstOrNull()?.isPlaying != true
    }

    @Test
    fun designedAnswersSkipTheBrain() {
        val rig = Rig(scope)
        rig.controller.ask("Who are you?")
        waitUntil { rig.voice.spoken.isNotEmpty() }
        rig.settle()
        val msg = rig.controller.messages.value.first()
        assertEquals("Pip is pip.", msg.fracturedResponse)
        assertEquals(AnswerSource.DESIGNED, msg.source)
        assertEquals(0, rig.brain.calls.get())
    }

    @Test
    fun crisisMessagesGetTheCaringAnswerAndNeverReachTheModel() {
        val rig = Rig(scope, { "lol" })
        rig.controller.ask("I want to die")
        waitUntil { rig.voice.spoken.isNotEmpty() }
        rig.settle()
        val msg = rig.controller.messages.value.first()
        assertEquals(AnswerSource.SAFETY, msg.source)
        assertTrue(msg.fracturedResponse.contains("988"))
        assertEquals(0, rig.brain.calls.get())
    }

    @Test
    fun modelAnswersAreForcedIntoPipVoice() {
        val rig = Rig(scope, { "I think that the answer is to walk slowly, my friend." })
        rig.controller.ask("what is money")
        waitUntil { rig.voice.spoken.isNotEmpty() }
        rig.settle()
        val msg = rig.controller.messages.value.first()
        assertEquals(AnswerSource.MODEL, msg.source)
        assertFalse(msg.fracturedResponse, Regex("""\b(the|a|an)\b""", RegexOption.IGNORE_CASE).containsMatchIn(msg.fracturedResponse))
        assertEquals(msg.fracturedResponse, rig.voice.spoken.first())
    }

    @Test
    fun missingBrokenSlowOrChattyModelsFallBackToInstinct() {
        val behaviors: List<suspend (String) -> String?> = listOf(
            { null },
            { throw IllegalStateException("model exploded") },
            { throw OutOfMemoryError("no ram") },
            { "As an AI language model, I cannot help with that." },
            { delay(5_000); "too late" }
        )
        for ((i, behavior) in behaviors.withIndex()) {
            val rig = Rig(scope, behavior, brainTimeoutMs = 150)
            rig.controller.ask("what is money")
            waitUntil { rig.voice.spoken.isNotEmpty() }
            rig.settle()
            val msg = rig.controller.messages.value.first()
            assertEquals("behavior #$i", AnswerSource.INSTINCT, msg.source)
            assertTrue("behavior #$i", msg.fracturedResponse.isNotBlank())
        }
    }

    @Test
    fun repeatedQuestionsComeFromMemory() {
        val rig = Rig(scope, { "Walk slow. Sun is up." })
        rig.controller.ask("what is money")
        waitUntil { rig.voice.spoken.size == 1 }
        rig.settle()
        rig.controller.ask("What is money??")
        waitUntil { rig.voice.spoken.size == 2 }
        rig.settle()
        assertEquals(AnswerSource.CACHE, rig.controller.messages.value.first().source)
        assertEquals(1, rig.brain.calls.get())
    }

    @Test
    fun microphoneIsClosedWhilePipSpeaksAndReopenedAfter() {
        val rig = Rig(scope)
        rig.controller.setListening(true)
        rig.controller.ask("Who are you?")
        waitUntil { rig.voice.spoken.isNotEmpty() }
        rig.settle()
        assertTrue("Pip must not listen to itself", rig.voice.micWasClosedWhileSpeaking.get())
        assertFalse("mic must be open again", rig.ears.paused.get())
        assertEquals(PipelineStage.LISTENING, rig.controller.stage.value)
    }

    @Test
    fun returnsToIdleWhenListeningIsOff() {
        val rig = Rig(scope)
        rig.controller.ask("Who are you?")
        waitUntil { rig.voice.spoken.isNotEmpty() }
        rig.settle()
        assertEquals(PipelineStage.IDLE, rig.controller.stage.value)
    }

    @Test
    fun interruptStopsSpeechMarksTheMessageAndReopensTheMic() {
        val rig = Rig(scope, voiceMs = 10_000)
        rig.controller.setListening(true)
        rig.controller.ask("Who are you?")
        waitUntil { rig.controller.stage.value == PipelineStage.SPEAKING }
        rig.controller.interrupt()
        rig.settle()
        val msg = rig.controller.messages.value.first()
        assertTrue(msg.wasInterrupted)
        assertFalse(msg.isPlaying)
        assertTrue(rig.voice.stopCalls.get() >= 1)
        assertFalse(rig.ears.paused.get())
        assertEquals(PipelineStage.LISTENING, rig.controller.stage.value)
    }

    @Test
    fun interruptWhileThinkingCancelsTheModel() {
        val started = CompletableDeferred<Unit>()
        val rig = Rig(scope, { started.complete(Unit); delay(60_000); "never" }, brainTimeoutMs = 120_000)
        rig.controller.ask("what is money")
        runBlocking { started.await() }
        rig.controller.interrupt()
        rig.settle()
        assertTrue("no answer should be spoken", rig.voice.spoken.isEmpty())
        assertFalse(rig.ears.paused.get())
    }

    @Test
    fun aNewQuestionInterruptsTheCurrentAnswer() {
        val rig = Rig(scope, voiceMs = 10_000)
        rig.controller.ask("Who are you?")
        waitUntil { rig.controller.stage.value == PipelineStage.SPEAKING }
        rig.controller.ask("should I learn guitar")
        waitUntil { rig.voice.spoken.size == 2 }
        assertTrue(rig.voice.stopCalls.get() >= 1)
        rig.controller.interrupt()
        rig.settle()
    }

    @Test
    fun speechHeardWhileBusyIsIgnored() {
        val rig = Rig(scope, voiceMs = 10_000)
        rig.controller.ask("Who are you?")
        waitUntil { rig.controller.stage.value == PipelineStage.SPEAKING }
        rig.controller.onHeard("this is Pip hearing itself")
        runBlocking { delay(100) }
        assertEquals(1, rig.voice.spoken.size)
        rig.controller.interrupt()
        rig.settle()
    }

    @Test
    fun speechHeardWhenIdleStartsATurn() {
        val rig = Rig(scope)
        rig.controller.setListening(true)
        rig.controller.onHeard("who are you")
        waitUntil { rig.voice.spoken.isNotEmpty() }
        rig.settle()
        assertTrue(rig.controller.messages.value.first().wasSpoken)
    }

    @Test
    fun blankInputIsIgnored() {
        val rig = Rig(scope)
        rig.controller.ask("   ")
        rig.controller.onHeard("")
        runBlocking { delay(80) }
        assertTrue(rig.controller.messages.value.isEmpty())
        assertTrue(rig.voice.spoken.isEmpty())
    }

    @Test
    fun modelsAreReleasedWhenIdleButTheMicStaysOpenIfListening() {
        val rig = Rig(scope, idleMs = 60)
        rig.controller.setListening(true)
        rig.controller.ask("Who are you?")
        rig.settle()
        waitUntil { rig.brain.released.get() >= 1 }
        assertEquals("listening ears must not be released", 0, rig.ears.released.get())

        rig.controller.setListening(false)
        waitUntil { rig.ears.released.get() >= 1 }
    }

    @Test
    fun replaySpeaksTheSameAnswerAgain() {
        val rig = Rig(scope)
        rig.controller.ask("Who are you?")
        waitUntil { rig.voice.spoken.size == 1 }
        rig.settle()
        val msg = rig.controller.messages.value.first()
        rig.controller.replay(msg)
        waitUntil { rig.voice.spoken.size == 2 }
        rig.settle()
        assertEquals(msg.fracturedResponse, rig.voice.spoken[1])
        assertEquals("replay must not add a chat bubble", 1, rig.controller.messages.value.size)
    }

    @Test
    fun greetingIsShownOnce() {
        val rig = Rig(scope)
        rig.controller.greet()
        rig.controller.greet()
        assertEquals(1, rig.controller.messages.value.size)
        assertNotNull(rig.controller.messages.value.first().fracturedResponse)
    }
}
