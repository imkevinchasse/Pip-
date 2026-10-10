package com.example.core

/** Small-model speech recognizers hallucinate filler words on background noise. Drop those. */
object SpeechFilter {

    private val NOISE_WORDS = setOf("the", "huh", "uh", "um", "a", "oh", "ah", "hmm", "mm", "eh", "uh huh")

    /** Returns the cleaned sentence to act on, or null when it is just noise. */
    fun clean(text: String): String? {
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.length < 2) return null
        if (t.lowercase() in NOISE_WORDS) return null
        return t
    }

    /** Reads `{"text": "..."}` or `{"partial": "..."}` produced by the recognizer. */
    fun fromRecognizerJson(json: String, key: String): String {
        return try {
            (MiniJson.asObject(MiniJson.parse(json))?.get(key) as? String).orEmpty()
        } catch (_: IllegalArgumentException) {
            ""
        }
    }
}
