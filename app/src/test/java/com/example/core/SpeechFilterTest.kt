package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechFilterTest {
    @Test
    fun dropsFillerNoise() {
        for (noise in listOf("", " ", "a", "the", "huh", "Uh", "um", "hmm", "uh huh")) assertNull(noise, SpeechFilter.clean(noise))
    }

    @Test
    fun keepsRealSentencesAndTidiesSpaces() {
        assertEquals("who are you", SpeechFilter.clean("  who   are you "))
        assertEquals("hi", SpeechFilter.clean("hi"))
    }

    @Test
    fun readsRecognizerJson() {
        assertEquals("hello pip", SpeechFilter.fromRecognizerJson("""{ "text" : "hello pip" }""", "text"))
        assertEquals("hel", SpeechFilter.fromRecognizerJson("""{"partial":"hel"}""", "partial"))
        assertEquals("", SpeechFilter.fromRecognizerJson("not json", "text"))
        assertEquals("", SpeechFilter.fromRecognizerJson("""{"other":"x"}""", "text"))
    }
}
