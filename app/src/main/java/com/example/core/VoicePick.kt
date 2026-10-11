package com.example.core

/** The facts about one installed text-to-speech voice that matter for choosing it (no Android types). */
data class VoiceInfo(
    val name: String,
    val language: String,
    val country: String,
    /** Android's Voice.QUALITY_*: 100 very low ... 300 normal ... 500 very high. */
    val quality: Int,
    /** Android's Voice.LATENCY_*: lower is faster. */
    val latency: Int,
    val needsNetwork: Boolean
)

/** Chooses the nicest-sounding English voice that works with no internet. */
object VoicePick {

    /** @return the best offline English voice, or null when there is none. */
    fun best(voices: List<VoiceInfo>): VoiceInfo? =
        voices
            .filter { it.language.equals("en", ignoreCase = true) && !it.needsNetwork }
            .filterNot { it.name.contains("network", ignoreCase = true) }
            .sortedWith(
                compareByDescending<VoiceInfo> { score(it) }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            )
            .firstOrNull()

    /** Quality first; a US accent only breaks ties inside the same quality level. */
    internal fun score(v: VoiceInfo): Int = v.quality + if (v.country.equals("US", ignoreCase = true)) 50 else 0

    /** True when the best voice is only "normal" or worse, so the app can suggest a nicer one. */
    fun isMediocre(v: VoiceInfo?): Boolean = v == null || v.quality < 400
}

/** Splits a reply into sentences so Pip can pause between them instead of reading one flat stream. */
object SpeechPacing {
    private val SPLIT = Regex("""(?<=[.!?])\s+""")

    fun sentences(text: String): List<String> =
        text.trim().split(SPLIT).map { it.trim() }.filter { it.any(Char::isLetterOrDigit) }

    /** Silence after a sentence, in milliseconds. Questions breathe a little longer. */
    fun pauseAfterMs(sentence: String): Long = when {
        sentence.endsWith("?") -> 420L
        sentence.endsWith("!") -> 300L
        else -> 320L
    }

    /** A safe upper bound for how long speaking [text] can take, used to avoid hanging forever. */
    fun timeoutMs(text: String): Long {
        val parts = sentences(text)
        val pauses = parts.sumOf { pauseAfterMs(it) }
        return (text.length * 140L + pauses).coerceIn(4_000L, 60_000L)
    }
}
