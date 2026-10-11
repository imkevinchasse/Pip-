package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePickTest {
    private fun v(name: String, lang: String = "en", country: String = "US", q: Int = 400, lat: Int = 300, net: Boolean = false) =
        VoiceInfo(name, lang, country, q, lat, net)

    @Test
    fun picksTheHighestQualityOfflineEnglishVoice() {
        val best = VoicePick.best(
            listOf(v("low", q = 200), v("good", q = 400), v("great", q = 500, country = "GB"), v("online", q = 500, net = true))
        )
        assertEquals("great", best!!.name)
    }

    @Test
    fun neverPicksAVoiceThatNeedsTheInternet() {
        assertNull(VoicePick.best(listOf(v("a", net = true), v("b-network", net = false))))
    }

    @Test
    fun ignoresOtherLanguages() {
        assertEquals("us", VoicePick.best(listOf(v("fr", lang = "fr", country = "FR", q = 500), v("us", q = 300)))!!.name)
        assertNull(VoicePick.best(listOf(v("fr", lang = "fr"))))
    }

    @Test
    fun usAccentBreaksTiesButNotQualityGaps() {
        assertEquals("us", VoicePick.best(listOf(v("gb", country = "GB", q = 400), v("us", country = "US", q = 400)))!!.name)
        assertEquals("gb", VoicePick.best(listOf(v("gb", country = "GB", q = 500), v("us", country = "US", q = 400)))!!.name)
    }

    @Test
    fun fasterVoiceWinsAFullTie() {
        assertEquals("fast", VoicePick.best(listOf(v("slow", lat = 500), v("fast", lat = 100)))!!.name)
    }

    @Test
    fun knowsWhenTheBestVoiceIsOnlyMediocre() {
        assertTrue(VoicePick.isMediocre(null))
        assertTrue(VoicePick.isMediocre(v("x", q = 300)))
        assertFalse(VoicePick.isMediocre(v("x", q = 400)))
    }

    @Test
    fun splitsIntoSentencesAndDropsEmptyBits() {
        assertEquals(
            listOf("Skydive.", "Never do, Never feel.", "You feel when you do?"),
            SpeechPacing.sentences("Skydive. Never do, Never feel.  You feel when you do?  ")
        )
        assertEquals(emptyList<String>(), SpeechPacing.sentences("  ...  "))
        assertEquals(listOf("No full stop here"), SpeechPacing.sentences("No full stop here"))
    }

    @Test
    fun questionsPauseLongerAndTimeoutScalesWithLength() {
        assertTrue(SpeechPacing.pauseAfterMs("Why?") > SpeechPacing.pauseAfterMs("Because."))
        assertTrue(SpeechPacing.timeoutMs("Hi.") >= 4_000L)
        assertTrue(SpeechPacing.timeoutMs("word ".repeat(200)) <= 60_000L)
    }
}
