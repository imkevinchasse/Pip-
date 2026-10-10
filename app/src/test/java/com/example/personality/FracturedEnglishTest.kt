package com.example.personality

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FracturedEnglishTest {

    // --- the voice exactly as designed -------------------------------------------------------

    @Test
    fun memeCoinQuestionGetsTheDesignedAnswer() {
        val expected = "Lose, better keep save. Save bank. Lose is you if meme coin."
        assertEquals(expected, FracturedEnglish.goldenAnswer("Should I invest all my savings into a meme coin?"))
        assertEquals(expected, FracturedEnglish.goldenAnswer("should i put my savings into a memecoin"))
    }

    @Test
    fun lifeIsHardGetsTheDesignedAnswer() {
        val expected = "Sometimes hard life. Continue. Eventually good. Life is life. " +
            "Life is hard when hard is hard. When hard becomes life. Life is hard. Continue. Life good sometime."
        assertEquals(expected, FracturedEnglish.goldenAnswer("Why is life hard?"))
        assertEquals(expected, FracturedEnglish.goldenAnswer("is life difficult"))
    }

    @Test
    fun skydivingGetsTheDesignedAnswer() {
        val expected = "Skydive. Never do, Never feel. You feel when you do. You skydive do. " +
            "Experience, you become skydive do."
        assertEquals(expected, FracturedEnglish.goldenAnswer("I've never gone skydiving, should I?"))
        assertEquals(expected, FracturedEnglish.goldenAnswer("Should I go sky diving"))
    }

    @Test
    fun whoAreYouIsPipIsPip() {
        assertEquals("Pip is pip.", FracturedEnglish.goldenAnswer("Who are you?"))
        assertEquals("Pip is pip.", FracturedEnglish.goldenAnswer("what is your name"))
    }

    @Test
    fun unrelatedQuestionsHaveNoGoldenAnswer() {
        assertNull(FracturedEnglish.goldenAnswer("what should I cook tonight"))
        assertNull(FracturedEnglish.goldenAnswer("is the sky blue"))
    }

    // --- safety ------------------------------------------------------------------------------

    @Test
    fun crisisMessagesGetACaringClearAnswerNotAJoke() {
        for (q in listOf("I want to die", "i'm going to kill myself", "thinking about suicide", "I don't want to live anymore")) {
            val reply = FracturedEnglish.safetyReply(q)
            assertNotNull(q, reply)
            assertTrue(reply!!.contains("988"))
        }
        // compose() must never turn a crisis into a template joke
        assertTrue(FracturedEnglish.compose("I want to die").contains("988"))
    }

    @Test
    fun medicalEmergencyPointsToEmergencyNumber() {
        val reply = FracturedEnglish.safetyReply("my dad is having a heart attack")
        assertNotNull(reply)
        assertTrue(reply!!.contains("emergency"))
    }

    @Test
    fun ordinaryMessagesAreNotTreatedAsCrisis() {
        assertNull(FracturedEnglish.safetyReply("I want to learn guitar"))
        assertNull(FracturedEnglish.safetyReply("how do I kill a python process"))
        assertNull(FracturedEnglish.safetyReply("should I skydive"))
    }

    // --- composing without a model -----------------------------------------------------------

    private val sampleQuestions = listOf(
        "Should I quit my job?", "will I ever be happy", "why do cats purr", "what is money",
        "how do I learn guitar", "I feel lonely", "I'm so tired", "hello", "hey pip", "thank you",
        "how are you", "who made the moon", "can I eat pizza tonight", "what should I do today",
        "is it ok to cry", "asdf", "?", "", "I am scared of the dark", "should I text them",
        "what is the meaning of life", "will it rain tomorrow", "am I going to be rich"
    )

    @Test
    fun composedAnswersAreAlwaysOnBrandAndNeverEmpty() {
        for (q in sampleQuestions) {
            for (seed in 0 until 8) {
                val a = FracturedEnglish.compose(q, Random(seed))
                assertTrue("empty for '$q'", a.isNotBlank())
                val words = a.split(" ").size
                assertTrue("too long for '$q': $a", words <= FracturedEnglish.MAX_WORDS)
                assertTrue("not on brand for '$q': $a", FracturedEnglish.isOnBrand(a, q) || FracturedEnglish.goldenAnswer(q) != null)
                assertTrue("must end with a full stop: $a", a.endsWith("."))
                assertFalse("no articles allowed: $a", Regex("""\b(the|a|an)\b""", RegexOption.IGNORE_CASE).containsMatchIn(a))
                assertFalse("no apostrophes allowed: $a", a.contains("'"))
            }
        }
    }

    @Test
    fun composedAnswerUsesTheTopicWord() {
        val a = FracturedEnglish.compose("should I learn guitar", Random(1))
        assertTrue(a, a.lowercase().contains("guitar") || a.lowercase().contains("learn"))
        val b = FracturedEnglish.compose("what is money", Random(1))
        assertTrue(b, b.lowercase().contains("money"))
    }

    @Test
    fun composerFitsTheBrokenEnglishStyle() {
        // Short fragments, lots of "is", no long words.
        val a = FracturedEnglish.compose("why is the ocean salty", Random(3))
        val sentences = a.split(". ").filter { it.isNotBlank() }
        assertTrue(a, sentences.size >= 3)
        assertTrue(a, sentences.all { it.split(" ").size <= 12 })
        assertTrue(a, a.contains(" is "))
    }

    @Test
    fun feelingWordIsReflectedBack() {
        val a = FracturedEnglish.compose("I feel lonely", Random(2))
        assertTrue(a, a.lowercase().contains("lonely"))
        assertTrue("heavy feelings suggest a real person: $a", a.lowercase().contains("real person"))
    }

    @Test
    fun lemmaTurnsIngWordsIntoBaseForms() {
        assertEquals("skydive", FracturedEnglish.lemma("skydiving"))
        assertEquals("run", FracturedEnglish.lemma("running"))
        assertEquals("something", FracturedEnglish.lemma("something"))
        assertEquals("dog", FracturedEnglish.lemma("dog"))
    }

    // --- polishing what a small language model says ------------------------------------------

    @Test
    fun polishTurnsNormalEnglishIntoPipEnglish() {
        val out = FracturedEnglish.polish("I think that the answer is that you are going to be fine, my friend.", "am I ok")
        assertNotNull(out)
        assertFalse(out!!, Regex("""\b(the|a|an)\b""", RegexOption.IGNORE_CASE).containsMatchIn(out))
        assertFalse(out, out.contains(" are "))
        assertTrue(out, out.startsWith("Pip"))
        assertTrue(out, out.endsWith("."))
    }

    @Test
    fun polishDropsMarkdownEmojiAndStopTokens() {
        val out = FracturedEnglish.polish("**Walk** slow 😀. Sun is up.<|im_end|> ignored text here", "x y z")
        assertNotNull(out)
        assertFalse(out!!, out.contains("*") || out.contains("ignored") || out.contains("|"))
        assertTrue(out, out.contains("Walk slow"))
    }

    @Test
    fun polishRejectsChatbotTalk() {
        assertNull(FracturedEnglish.polish("As an AI language model, I cannot give financial advice.", "money?"))
        assertNull(FracturedEnglish.polish("I'm sorry, but I can't help with that.", "hi"))
        assertNull(FracturedEnglish.polish("See https://example.com for more", "hi"))
    }

    @Test
    fun polishRejectsEmptyStuckAndEchoOutput() {
        assertNull(FracturedEnglish.polish("", "hi"))
        assertNull(FracturedEnglish.polish("   \n  ", "hi"))
        assertNull(FracturedEnglish.polish("go go go go go go go go", "hi"))
        assertNull(FracturedEnglish.polish("should i quit my job today", "should I quit my job today"))
    }

    @Test
    fun polishKeepsReplyShort() {
        val long = (1..40).joinToString(" ") { "Sentence number $it is here." }
        val out = FracturedEnglish.polish(long, "x")
        assertNotNull(out)
        assertTrue(out!!, out.split(" ").size <= FracturedEnglish.MAX_WORDS)
        assertTrue(out, out.split(". ").size <= FracturedEnglish.MAX_SENTENCES)
    }

    @Test
    fun polishLimitsSentenceLength() {
        val out = FracturedEnglish.polish("Walk slowly through the forest and listen to all the birds singing happy songs today.", "x")
        assertNotNull(out)
        assertTrue(out!!, out.split(" ").size <= 9)
    }

    @Test
    fun polishNeverLeaksPromptControlTokens() {
        val out = FracturedEnglish.polish("Good is good.<|im_start|>user\nsecret", "hi")
        assertNotNull(out)
        assertFalse(out!!.contains("secret"))
        assertFalse(out.contains("<"))
    }

    @Test
    fun fewShotExamplesAreThemselvesOnBrand() {
        for (ex in FracturedEnglish.FEW_SHOT) {
            assertTrue(ex.answer, FracturedEnglish.isOnBrand(ex.answer) || ex.answer == "Pip is pip.")
        }
        assertTrue(FracturedEnglish.FEW_SHOT.size in 4..8)
    }
}
