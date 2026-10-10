package com.example.engine

import com.example.core.Brain
import com.example.core.ByteLevelBpeTokenizer
import com.example.core.Generator
import com.example.core.Sampler
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * The tiny language model (SmolLM2-135M, 4-bit) running fully on the phone.
 *
 * Loaded on first use, released by [release] after Pip has been idle. If loading fails once
 * (corrupt file, no memory) it stays off until the model is re-downloaded, and Pip simply uses
 * its pattern brain instead. A model problem can never crash a conversation.
 */
class SmolLmBrain(
    private val modelFile: () -> File,
    private val tokenizerFile: () -> File
) : Brain {

    private val lock = ReentrantLock()
    private var model: OnnxTokenModel? = null
    private var generator: Generator? = null
    private var failedFor: Long = -1L // modification time of the file that failed to load

    /** Why the model is not usable right now, for the UI. Null when fine or not yet tried. */
    @Volatile
    var lastProblem: String? = null
        private set

    override suspend fun generate(query: String): String? = withContext(Dispatchers.Default) {
        val modelStamp = modelFile().lastModified()
        if (!modelFile().isFile || !tokenizerFile().isFile) return@withContext null
        if (failedFor == modelStamp) return@withContext null

        lock.withLock {
            val gen = try {
                generator ?: load().also { generator = it }
            } catch (t: Throwable) {
                failedFor = modelStamp
                lastProblem = "Language model could not start (${t.message ?: t.javaClass.simpleName}). Pip uses its own instinct instead."
                release0()
                return@withContext null
            }
            try {
                val text = gen.generate(
                    question = query,
                    shouldStop = { !isActive },
                    budgetMs = 20_000L
                )
                lastProblem = null
                text.ifBlank { null }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastProblem = "Language model failed (${t.message ?: t.javaClass.simpleName})."
                release0() // rebuild the session next time
                null
            }
        }
    }

    private fun load(): Generator {
        val tokenizer = ByteLevelBpeTokenizer.fromJson(tokenizerFile().readText())
        val cores = Runtime.getRuntime().availableProcessors()
        val loaded = OnnxTokenModel.load(modelFile().absolutePath, threads = cores / 2)
        model = loaded
        return Generator(tokenizer, Sampler(temperature = 0.6f, topK = 30, topP = 0.9f), loaded)
    }

    /** Frees the model's memory. Skipped (and retried later) if a generation is in progress. */
    override fun release() {
        if (lock.tryLock()) {
            try {
                release0()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun release0() {
        try {
            model?.close()
        } catch (_: Throwable) {
        }
        model = null
        generator = null
    }

    /** Call after a fresh download so a previously failed file is tried again. */
    fun forgetFailure() {
        failedFor = -1L
        lastProblem = null
    }
}
