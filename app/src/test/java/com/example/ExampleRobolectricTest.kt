package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.SmolLM2FracturedEngine
import com.example.manager.ConversationalCache
import com.example.manager.PhasedMemoryOrchestrator
import com.example.model.PipelineStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Smol Oracle", appName)
    }

    @Test
    fun `test exact user questions for fractured wisdom`() = runBlocking {
        val engine = SmolLM2FracturedEngine()

        // 1. "Is life difficult?"
        val ans1 = engine.generateFracturedWisdom("Is life difficult?")
        assertEquals(
            "i can say yes, but can say yes, but sometimes if hard, is be difficult, because it hard.",
            ans1
        )

        // 2. "should I put my savings into a memecoin"
        val ans2 = engine.generateFracturedWisdom("should I put my savings into a memecoin")
        assertEquals(
            "Bad is lose, you will bad keep instead bank in better.",
            ans2
        )

        // 3. "who am I"
        val ans3 = engine.generateFracturedWisdom("who am I")
        assertEquals(
            "You is you.",
            ans3
        )
    }

    @Test
    fun `test conversational cache hit and rapid interrupt support`() {
        val cache = ConversationalCache()
        val hit = cache.get("who am i")
        assertNotNull(hit)
        assertEquals("You is you.", hit?.fracturedResponse)
        assertTrue(cache.hitCount > 0)
    }

    @Test
    fun `test phased memory orchestrator stages`() {
        val orchestrator = PhasedMemoryOrchestrator()
        assertEquals(PipelineStage.IDLE, orchestrator.currentStage.value)

        orchestrator.transitionToStage(PipelineStage.LISTENING)
        assertEquals(PipelineStage.LISTENING, orchestrator.currentStage.value)
        assertTrue(orchestrator.estimatedRamUsageMb.value in 30f..110f)

        orchestrator.transitionToStage(PipelineStage.THINKING)
        assertEquals(PipelineStage.THINKING, orchestrator.currentStage.value)

        orchestrator.transitionToStage(PipelineStage.SPEAKING)
        assertEquals(PipelineStage.SPEAKING, orchestrator.currentStage.value)
    }

    @Test
    fun `test voice profiles and sliders`() {
        val heliumProfile = com.example.engine.VoiceProfile.ALL.first { it.id == "helium" }
        assertEquals(1.85f, heliumProfile.pitch, 0.01f)
        assertEquals(1.20f, heliumProfile.speed, 0.01f)

        val squeakProfile = com.example.engine.VoiceProfile.ALL.first { it.id == "squeak" }
        assertEquals(2.15f, squeakProfile.pitch, 0.01f)
        assertTrue(com.example.engine.VoiceProfile.ALL.size >= 6)
    }

    @Test
    fun `test jc-builds SmolLM2 repository download url`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.manager.ModelDownloadManager(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        val smolLmUrl = manager.getModelDownloadUrl(com.example.model.ModelId.SMOLLM2_135M)
        assertTrue(smolLmUrl.contains("jc-builds/SmolLM2-135M-Instruct-Q4_K_M-GGUF"))
        assertEquals("SmolLM2-135M-Instruct-Q4_K_M.gguf", manager.getModelFileName(com.example.model.ModelId.SMOLLM2_135M))
    }

    @Test
    fun `test all 3 models hosting and local storage verification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.manager.ModelDownloadManager(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        manager.fastInstallAllModels()

        assertTrue(manager.isModelHostedLocally(com.example.model.ModelId.WHISPER_SMALL))
        assertTrue(manager.isModelHostedLocally(com.example.model.ModelId.SMOLLM2_135M))
        assertTrue(manager.isModelHostedLocally(com.example.model.ModelId.PIPER_TTS))

        val server = com.example.manager.LocalModelServer(
            com.example.engine.SmolLM2FracturedEngine(),
            manager,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            port = 8088
        )
        assertNotNull(server.serverUrl.value)
    }

    @Test
    fun `test speaker identification and voice print enrollment`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.engine.WhisperSttEngine(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        org.junit.Assert.assertFalse(engine.isOwnerOnlyMode.value)

        engine.setOwnerOnlyMode(true)
        assertTrue(engine.isOwnerOnlyMode.value)

        engine.setOwnerOnlyMode(false)
        org.junit.Assert.assertFalse(engine.isOwnerOnlyMode.value)
    }

    @Test
    fun `test head click listening and processing cycle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.engine.WhisperSttEngine(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        engine.startHeadClickListening()
        val processed = engine.stopHeadClickListeningAndProcess()
        assertNotNull(processed)
        assertTrue(processed.isNotBlank())
    }
}
