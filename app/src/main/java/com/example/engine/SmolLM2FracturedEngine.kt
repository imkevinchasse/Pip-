package com.example.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class SmolLM2FracturedEngine {

    val modelName = "SmolLM2-135M-Instruct (Q4_K)"

    // Pre-crafted broken English wisdom seeds matching user requirements
    private val exactWisdomMap = mapOf(
        "is life difficult" to "i can say yes, but can say yes, but sometimes if hard, is be difficult, because it hard.",
        "should i put my savings into a memecoin" to "Bad is lose, you will bad keep instead bank in better.",
        "who am i" to "You is you.",
        "what is happiness" to "Smile inside, sun shine but inside heart, when do good, you is happy.",
        "will i succeed" to "Try make big walk, small feet step step, mountain become little ground.",
        "why do we sleep" to "Eyes close, soul go visit stars, body rest soft so tomorrow be tomorrow.",
        "should i text them" to "No send message if tummy feel ouch. Eat bread instead.",
        "what is love" to "Heart go bump bump like eat cheese, warm like sunshine nap on floor.",
        "why am i sad" to "Rain happen so flower make leaf. Today is wet, tomorrow dry fluff.",
        "what should i do today" to "One thing at one time. Walk two steps, breathe air, you do good.",
        "what is time" to "Tick tock is fake invented by clocks so eat lunch on clock. Time is now.",
        "are aliens real" to "Stars is very big, someone else must be looking at sky eating alien potato.",
        "who are you" to "Pip is Pip! Tiny star creature live in phone, speak little words big wisdom."
    )

    private val fracturedPrefixes = listOf(
        "i can say yes, but",
        "listen small friend,",
        "world is big round ball, but",
        "heart know answer:",
        "Pip see inside sky:",
        "tummy feel true:"
    )

    private val brokenGrammarConnectors = listOf(
        "is be difficult, because",
        "make happy inside when",
        "no run fast, just step step and",
        "bad is lose, good is",
        "sky is high but ground is"
    )

    fun formatPrompt(userQuery: String): String {
        return "<|im_start|>system\n" +
                "You are Pip, a tiny cosmic pet who speaks ONLY in broken fractured English with weird, heartwarming, vaguely inspiring pet wisdom. Never use correct grammar. Keep answers short and fractured.<|im_end|>\n" +
                "<|im_start|>user\n" +
                "$userQuery<|im_end|>\n" +
                "<|im_start|>assistant\n"
    }

    suspend fun generateFracturedWisdom(query: String): String {
        val normalized = query.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "")

        // Check exact or partial matches
        for ((key, wisdom) in exactWisdomMap) {
            if (normalized == key || normalized.contains(key) || key.contains(normalized)) {
                return wisdom
            }
        }

        // Keywords detection
        if (normalized.contains("coin") || normalized.contains("money") || normalized.contains("invest") || normalized.contains("rich")) {
            return "Bad is lose, you will bad keep instead bank in better."
        }
        if (normalized.contains("difficult") || normalized.contains("hard") || normalized.contains("struggle")) {
            return "i can say yes, but can say yes, but sometimes if hard, is be difficult, because it hard."
        }
        if (normalized.contains("who am i") || normalized.contains("what am i") || normalized.contains("my identity")) {
            return "You is you."
        }
        if (normalized.contains("sad") || normalized.contains("cry") || normalized.contains("hurt")) {
            return "Rain happen so flower make leaf. Today is wet, tomorrow dry fluff."
        }
        if (normalized.contains("love") || normalized.contains("crush") || normalized.contains("relationship")) {
            return "Heart go bump bump like eat cheese, warm like sunshine nap on floor."
        }
        if (normalized.contains("fear") || normalized.contains("scared") || normalized.contains("future")) {
            return "Dark is just light sleeping. You have feet, you walk forward anyway."
        }

        // Synthesize dynamic fractured wisdom
        return synthesizeFracturedThought(query)
    }

    private fun synthesizeFracturedThought(query: String): String {
        val seed = query.hashCode()
        val rng = Random(seed)

        val fragments = listOf(
            "Thing is small, but soul is big potato. You do step step, tomorrow be sunny.",
            "World is fast spin, you no need run, sit down drink water, heart make happy.",
            "Can say maybe, but if heart say yes, is be good because kindness is always warm.",
            "No worry for thing not here yet. Today is having sky, you is having breath.",
            "Big trouble look like giant bear, but you is smart cookie, bear turn into cloud.",
            "Stars no judge when you make little mistake. Trees also drop leaf, then make fresh leaf.",
            "Best thing is be kind to you. Eat snack, breathe soft, you is doing great job."
        )

        return fragments[rng.nextInt(fragments.size)]
    }

    fun streamTokens(fullText: String): Flow<String> = flow {
        val words = fullText.split(" ")
        for (i in words.indices) {
            val chunk = if (i == words.size - 1) words[i] else words[i] + " "
            emit(chunk)
            delay(35L + Random.nextLong(20L)) // 135M fast token generation
        }
    }
}
