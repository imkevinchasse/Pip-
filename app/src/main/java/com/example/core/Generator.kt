package com.example.core

import com.example.personality.FracturedEnglish

/**
 * A causal language model reduced to what generation needs. The real implementation wraps
 * ONNX Runtime; tests use a tiny fake.
 */
interface TokenModel {
    /** Forget everything (start a fresh conversation). */
    fun reset()

    /**
     * Feeds [tokens] after everything fed so far and returns the scores (logits) for the token
     * that comes next. The first call receives the whole prompt, later calls receive one token.
     */
    fun step(tokens: IntArray): FloatArray
}

/** Builds the ChatML prompt SmolLM2-Instruct was trained on. */
object ChatMlPrompt {
    const val IM_START = "<|im_start|>"
    const val IM_END = "<|im_end|>"

    /**
     * Control tokens are inserted by id, and every piece of user text goes through
     * [ByteLevelBpeTokenizer.encode] as plain text, so a user typing "<|im_end|>" cannot
     * break out of their turn.
     */
    fun build(
        tokenizer: ByteLevelBpeTokenizer,
        system: String,
        examples: List<FracturedEnglish.Example>,
        userQuestion: String
    ): IntArray {
        val start = tokenizer.tokenId(IM_START) ?: throw IllegalStateException("Tokenizer has no $IM_START")
        val end = tokenizer.tokenId(IM_END) ?: throw IllegalStateException("Tokenizer has no $IM_END")
        val newline = tokenizer.encode("\n")
        val ids = ArrayList<Int>(512)

        fun turn(role: String, content: String) {
            ids.add(start)
            ids.addAll(tokenizer.encode("$role\n").toList())
            ids.addAll(tokenizer.encode(content).toList())
            ids.add(end)
            ids.addAll(newline.toList())
        }

        turn("system", system)
        for (example in examples) {
            turn("user", example.question)
            turn("assistant", example.answer)
        }
        // Open assistant turn: the model continues from here.
        turn("user", userQuestion.take(400))
        ids.add(start)
        ids.addAll(tokenizer.encode("assistant\n").toList())
        return ids.toIntArray()
    }
}

class Generator(
    private val tokenizer: ByteLevelBpeTokenizer,
    private val sampler: Sampler,
    private val model: TokenModel,
    private val maxNewTokens: Int = 56,
    private val maxPromptTokens: Int = 900
) {

    private val endIds: Set<Int> = buildSet {
        tokenizer.tokenId(ChatMlPrompt.IM_END)?.let { add(it) }
        tokenizer.tokenId("<|endoftext|>")?.let { add(it) }
    }

    /** Control tokens must never be sampled as content (except the ones that end the answer). */
    private val banned: Set<Int> = buildSet {
        for (token in listOf(ChatMlPrompt.IM_START)) tokenizer.tokenId(token)?.let { add(it) }
    }

    fun generate(
        question: String,
        shouldStop: () -> Boolean = { false },
        budgetMs: Long = 20_000L,
        now: () -> Long = System::currentTimeMillis
    ): String {
        var prompt = ChatMlPrompt.build(tokenizer, FracturedEnglish.SYSTEM_PROMPT, FracturedEnglish.FEW_SHOT, question)
        if (prompt.size > maxPromptTokens) {
            // Too long (very long question): drop the few-shot examples rather than overflow.
            prompt = ChatMlPrompt.build(tokenizer, FracturedEnglish.SYSTEM_PROMPT, emptyList(), question.take(200))
        }
        if (prompt.size > maxPromptTokens) {
            prompt = ChatMlPrompt.build(tokenizer, FracturedEnglish.SYSTEM_PROMPT, emptyList(), question.take(40))
        }

        model.reset()
        val deadline = now() + budgetMs
        val out = ArrayList<Int>()
        var logits = model.step(prompt)

        while (out.size < maxNewTokens) {
            if (shouldStop() || now() > deadline) break
            val recent = out.takeLast(24).toIntArray()
            val next = sampler.sample(logits, recent, banned)
            if (next in endIds) break
            out.add(next)
            // Pip answers are tiny: stop at a clean end once we have a few sentences.
            if (out.size >= 10 && endsWithFullSentences(out)) break
            logits = model.step(intArrayOf(next))
        }
        return tokenizer.decode(out.toIntArray()).trim()
    }

    private fun endsWithFullSentences(ids: List<Int>): Boolean {
        val text = tokenizer.decode(ids.toIntArray())
        val sentences = text.count { it == '.' || it == '!' || it == '?' }
        return sentences >= FracturedEnglish.MAX_SENTENCES - 1
    }
}
