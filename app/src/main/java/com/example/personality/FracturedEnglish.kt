package com.example.personality

import kotlin.random.Random

/**
 * Pip's voice.
 *
 * Pip speaks broken English: tiny words, tiny sentences, bare verbs, "is" for everything, the
 * important word repeated until it turns into wisdom. It sounds vaguely inspiring *because* the
 * grammar is broken.
 *
 *     Q: Should I put my savings in a meme coin?
 *     A: Lose, better keep save. Save bank. Lose is you if meme coin.
 *
 * This file is pure Kotlin (no Android) so it can be unit tested on a laptop. It provides:
 *  - [FEW_SHOT] / [SYSTEM_PROMPT]: steer the small language model toward the voice
 *  - [polish]: force whatever the model produced into the voice (and reject junk)
 *  - [compose]: build an on-brand answer without any model (used when the model is not
 *    downloaded yet, fails, or produces something off-brand)
 *  - [safetyReply]: a clear, caring answer for crisis messages. Pip never jokes about those.
 */
object FracturedEnglish {

    data class Example(val question: String, val answer: String)

    // --- The voice, straight from the people who made Pip -------------------------------------

    private const val ANSWER_MEME_COIN = "Lose, better keep save. Save bank. Lose is you if meme coin."
    private const val ANSWER_LIFE_HARD =
        "Sometimes hard life. Continue. Eventually good. Life is life. Life is hard when hard is hard. " +
            "When hard becomes life. Life is hard. Continue. Life good sometime."
    private const val ANSWER_SKYDIVE =
        "Skydive. Never do, Never feel. You feel when you do. You skydive do. " +
            "Experience, you become skydive do."
    private const val ANSWER_WHO_ARE_YOU = "Pip is pip."

    const val SYSTEM_PROMPT =
        "You are Pip, a tiny pet who lives inside a phone. Pip speaks broken English with small " +
            "words and short sentences. Pip repeats the important word. Pip never explains. " +
            "Pip always ends with a little hope."

    /** Examples shown to the language model before the real question. */
    val FEW_SHOT: List<Example> = listOf(
        Example("Should I invest all my savings into a meme coin?", ANSWER_MEME_COIN),
        Example("Why is life hard?", ANSWER_LIFE_HARD),
        Example("I've never gone skydiving. Should I?", ANSWER_SKYDIVE),
        Example("Who are you?", ANSWER_WHO_ARE_YOU),
        Example("What is love?", "Love is love. Heart go, heart stay. Give, give, give more. Love is when give is happy."),
        Example("I am scared of the future.", "Future is not here. Here is here. Walk. Scared walk is still walk. Continue.")
    )

    const val GREETING = "Pip is pip. You is here. Good. Good is enough."

    // --- Safety: Pip is cute, never careless -------------------------------------------------

    private val SELF_HARM = Regex(
        """\b(kill myself|killing myself|suicid\w*|end my life|ending my life|take my own life|""" +
            """want to die|wanna die|wish i (was|were) dead|better off dead|""" +
            """dont want to (be alive|live|exist)|do not want to (be alive|live|exist)|""" +
            """hurt myself|harm myself|self harm|cut myself|end it all)\b"""
    )
    private val MEDICAL_EMERGENCY = Regex(
        """\b(overdos\w*|heart attack|cant breathe|can not breathe|chest pain|having a stroke|""" +
            """poisoned|poisoning|severe bleeding|bleeding a lot|bleeding badly)\b"""
    )

    const val CRISIS_REPLY =
        "Pip hear you. Hurt is big. Pip is small, you need big help. " +
            "Call or text 988 if you is in US. Or call your emergency number. " +
            "Stay with person. You is important."
    const val MEDICAL_REPLY =
        "Pip is not doctor. This is big. Call emergency number now. Number first, Pip after."

    /** Returns a clear, caring reply for crisis or emergency messages, otherwise null. */
    fun safetyReply(query: String): String? {
        val n = normalize(query)
        if (SELF_HARM.containsMatchIn(n)) return CRISIS_REPLY
        if (MEDICAL_EMERGENCY.containsMatchIn(n)) return MEDICAL_REPLY
        return null
    }

    // --- The answers people gave us verbatim ------------------------------------------------

    private val MEME_COIN = Regex("""\b(meme ?coin|memecoin)s?\b""")
    private val LIFE_HARD =
        Regex("""\blife\b.*\b(hard|difficult|tough)\b|\b(hard|difficult|tough)\b.*\blife\b""")
    private val SKYDIVE = Regex("""\bsky ?div\w*\b""")
    private val WHO_ARE_YOU =
        Regex("""\b(who|what) (are|r) (you|u)\b|\byour name\b|\bwho is pip\b|\bwho am i talking to\b""")

    /** The exact reply Pip was designed to give for these questions, or null. */
    fun goldenAnswer(query: String): String? {
        val n = normalize(query)
        return when {
            WHO_ARE_YOU.containsMatchIn(n) -> ANSWER_WHO_ARE_YOU
            MEME_COIN.containsMatchIn(n) -> ANSWER_MEME_COIN
            SKYDIVE.containsMatchIn(n) -> ANSWER_SKYDIVE
            LIFE_HARD.containsMatchIn(n) -> ANSWER_LIFE_HARD
            else -> null
        }
    }

    // --- Normalising text --------------------------------------------------------------------

    /** lowercase, apostrophes removed ("don't" -> "dont"), punctuation to spaces, spaces collapsed. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace('’', '\'')
            .replace("'", "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // --- Turning model output into Pip-speak -------------------------------------------------

    const val MAX_SENTENCES = 7
    const val MAX_WORDS = 45
    private const val MAX_WORDS_PER_SENTENCE = 9

    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+|\n+""")
    private val WHITESPACE = Regex("\\s+")
    private val MARKUP = Regex("[*_`#>~|\\[\\]{}()\"<]")

    private val DROP_WORDS = setOf(
        "a", "an", "the", "very", "really", "quite", "just", "actually", "basically", "simply",
        "certainly", "definitely", "indeed", "however", "moreover", "furthermore", "additionally",
        "would", "could", "should", "might", "may", "shall", "must", "perhaps", "kind", "sort"
    )

    private val WORD_MAP = mapOf(
        "am" to "is", "are" to "is", "was" to "is", "were" to "is", "been" to "is", "being" to "is",
        "has" to "have", "had" to "have", "does" to "do", "did" to "do",
        "i" to "Pip", "me" to "Pip", "my" to "Pip", "myself" to "Pip", "mine" to "Pip",
        "i'm" to "Pip is", "i've" to "Pip have", "i'll" to "Pip will", "i'd" to "Pip",
        "you're" to "you is", "we're" to "we is", "they're" to "they is",
        "it's" to "it is", "that's" to "that is", "there's" to "there is", "here's" to "here is",
        "what's" to "what is", "he's" to "he is", "she's" to "she is", "let's" to "let",
        "you've" to "you have", "we've" to "we have", "they've" to "they have",
        "you'll" to "you will", "we'll" to "we will", "they'll" to "they will",
        "don't" to "no", "doesn't" to "no", "didn't" to "no", "isn't" to "is not",
        "aren't" to "is not", "wasn't" to "is not", "can't" to "no can", "cannot" to "no can",
        "won't" to "no will", "couldn't" to "no can", "shouldn't" to "no", "wouldn't" to "no"
    )

    private val BANNED_PHRASES = listOf(
        "as an ai", "language model", "i'm sorry", "i am sorry", "i apologize", "i cannot",
        "i can't", "i don't have", "assistant", "openai", "hugging", "smollm", "chatgpt",
        "http", "www.", ".com"
    )
    private val PROFANITY = Regex("""\b(fuck\w*|shit\w*|bitch\w*|asshole|cunt|dick|bastard)\b""")

    /**
     * Forces [raw] model output into Pip's voice. Returns null when the output is unusable
     * (empty, off-brand, chatty, or unsafe), so the caller can fall back to [compose].
     */
    fun polish(raw: String, query: String = ""): String? {
        var text = raw
            .substringBefore("<|im_end|>")
            .substringBefore("<|endoftext|>")
            .substringBefore("<|im_start|>")

        val lowered = text.lowercase()
        if (BANNED_PHRASES.any { lowered.contains(it) } || PROFANITY.containsMatchIn(lowered)) return null
        if (isEcho(text, query)) return null

        text = text
            .replace('’', '\'').replace('‘', '\'')
            .replace('“', '"').replace('”', '"')
            .replace("—", ", ").replace("–", ", ")
            .replace("...", ".")
            .replace(MARKUP, " ")
        text = text.filter { it == '\n' || it.code in 32..126 }

        val sentences = text.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var words = 0
        for (sentence in sentences) {
            val fractured = fractureSentence(sentence) ?: continue
            val n = fractured.split(' ').size
            if (words + n > MAX_WORDS) break
            out.add(fractured)
            words += n
            if (out.size >= MAX_SENTENCES) break
        }
        if (out.isEmpty()) return null
        val result = out.joinToString(" ")
        return if (isOnBrand(result, query)) result else null
    }

    private fun fractureSentence(sentence: String): String? {
        val words = ArrayList<String>()
        for (token in sentence.split(WHITESPACE)) {
            if (token.isEmpty()) continue
            val comma = token.endsWith(",")
            val core = token.trim(',', ';', ':', '.', '!', '?', '-', '\'').lowercase()
            if (core.isEmpty()) continue
            if (core in DROP_WORDS) continue
            val mapped = WORD_MAP[core] ?: core
            val pieces = mapped.split(' ')
            for ((index, piece) in pieces.withIndex()) {
                var word = piece
                if (index == pieces.lastIndex && comma) word += ","
                words.add(word)
            }
        }
        if (words.isEmpty()) return null

        // Keep sentences tiny. Prefer cutting at a comma so the fragment still reads as a thought.
        var kept: List<String> = words
        if (words.size > MAX_WORDS_PER_SENTENCE) {
            val window = words.subList(0, MAX_WORDS_PER_SENTENCE)
            val lastComma = window.indexOfLast { it.endsWith(",") }
            kept = if (lastComma >= 2) window.subList(0, lastComma + 1) else window
        }
        val last = kept.last().trimEnd(',')
        val finished = kept.dropLast(1) + last
        val sentenceText = finished.joinToString(" ")
        return sentenceCase(sentenceText) + "."
    }

    /** True when [reply] mostly repeats the words of [query] (checked before any rewriting). */
    private fun isEcho(reply: String, query: String): Boolean {
        val queryWords = normalize(query).split(' ').filter { it.isNotEmpty() }.toSet()
        val replyWords = normalize(reply).split(' ').filter { it.isNotEmpty() }
        if (queryWords.size < 3 || replyWords.size < 3) return false
        return replyWords.count { it in queryWords }.toDouble() / replyWords.size > 0.8
    }

    /** Is this reply short, simple, repetitive-in-the-good-way and free of chatbot-isms? */
    fun isOnBrand(text: String, query: String = ""): Boolean {
        val words = text.lowercase().split(WHITESPACE).map { it.trim('.', ',', '!', '?') }.filter { it.isNotEmpty() }
        if (words.size < 2 || words.size > MAX_WORDS) return false

        val letters = text.count { it.isLetter() }
        if (letters < text.count { !it.isWhitespace() } * 0.8) return false

        val averageLength = words.sumOf { it.length }.toDouble() / words.size
        if (averageLength > 6.8) return false
        if (words.count { it.length > 10 } > words.size * 0.1) return false

        // Four of the same word in a row is a stuck model, not wisdom.
        var run = 1
        for (i in 1 until words.size) {
            run = if (words[i] == words[i - 1]) run + 1 else 1
            if (run >= 4) return false
        }
        if (words.toSet().size < words.size * 0.3) return false

        val queryWords = normalize(query).split(' ').filter { it.length > 2 }.toSet()
        if (queryWords.size >= 3 && words.size >= 4) {
            val overlap = words.count { it in queryWords }.toDouble() / words.size
            if (overlap > 0.8) return false // just echoing the question
        }
        return true
    }

    private fun sentenceCase(text: String): String {
        if (text.isEmpty()) return text
        return text.replaceFirstChar { it.uppercase() }
            .let { fixPipCapital(it) }
    }

    private fun fixPipCapital(text: String): String =
        text.replace(Regex("""\bpip\b"""), "Pip")

    // --- Composing an answer without a model -------------------------------------------------

    private val STOP_WORDS = setOf(
        "i", "me", "my", "mine", "myself", "you", "your", "yours", "he", "him", "his", "she", "her",
        "it", "its", "we", "us", "our", "they", "them", "their", "this", "that", "these", "those",
        "a", "an", "the", "is", "are", "am", "was", "were", "be", "been", "being", "do", "does", "did",
        "have", "has", "had", "will", "would", "shall", "should", "can", "could", "may", "might", "must",
        "what", "whats", "why", "how", "who", "whom", "which", "when", "where", "whose",
        "of", "to", "in", "on", "at", "for", "with", "about", "into", "from", "by", "as", "and", "or",
        "but", "if", "so", "than", "then", "not", "no", "up", "out", "over", "just", "really", "very",
        "too", "all", "any", "some", "much", "many", "more", "most", "also", "ever", "even", "there",
        "here", "again", "always", "never", "going", "gonna", "wanna", "want", "get", "got", "tell",
        "please", "pip", "hey", "hi", "hello", "ok", "okay", "yes", "yeah", "im", "ive", "ill", "id",
        "dont", "cant", "wont", "thing", "things", "like", "think", "feel", "feeling", "know", "been"
    )

    private val FEELING_WORDS = setOf(
        "sad", "scared", "afraid", "anxious", "nervous", "lonely", "alone", "tired", "exhausted",
        "angry", "mad", "stressed", "worried", "overwhelmed", "happy", "excited", "bored", "lost",
        "stuck", "hurt", "empty", "depressed", "sick", "jealous", "ashamed", "guilty", "hopeless",
        "worthless", "burned"
    )
    private val HEAVY_FEELINGS = setOf("depressed", "empty", "hopeless", "worthless", "alone", "lonely")

    private val GREETING_RE = Regex("""^(hi|hello|hey|yo|sup|howdy|hola|good (morning|afternoon|evening|night))\b""")
    private val THANKS_RE = Regex("""\b(thanks|thank you|thx|ty)\b""")
    private val HOW_ARE_YOU_RE = Regex("""\bhow (are|r) (you|u)\b|\bhows it going\b|\bwhats up\b""")
    private val FEELING_LEAD_RE = Regex("""\b(i am|im|i feel|feeling|ive been|i have been|i been)\b""")
    private val FUTURE_RE = Regex("""\b(will i|am i going to|will it|will they|will she|will he|what will happen|future|tomorrow|next year)\b""")
    private val DECISION_RE = Regex("""^(should|shall|can|could|would|do|does|may|must) (i|we)\b|\bshould i\b|^(is it|would it be)\b""")
    private val WHY_RE = Regex("""^(why|how come)\b""")
    private val WHAT_RE = Regex("""^(what|whats)\b""")
    private val HOW_RE = Regex("""^how\b""")
    private val WHO_RE = Regex("""^who\b""")

    /** First and last meaningful words of the question. */
    internal data class Topic(val verb: String, val noun: String)

    internal fun extractTopic(query: String): Topic {
        val tokens = normalize(query).split(' ')
            .filter { it.isNotEmpty() && it !in STOP_WORDS && (it.length >= 2) }
            .map { lemma(it) }
        if (tokens.isEmpty()) return Topic("try", "life")
        return Topic(tokens.first(), tokens.last())
    }

    /** Very small stemmer so "skydiving" becomes "skydive". Cosmetic only. */
    internal fun lemma(word: String): String {
        if (word.length < 7 || !word.endsWith("ing") || word.endsWith("thing")) return word
        val stem = word.dropLast(3)
        return when {
            stem.endsWith("v") || stem.endsWith("z") -> stem + "e"
            stem.length >= 3 && stem[stem.length - 1] == stem[stem.length - 2] &&
                stem.last() !in "lsaeiou" -> stem.dropLast(1)
            stem.length >= 4 -> stem
            else -> word
        }
    }

    /**
     * Builds a Pip answer from patterns alone. Always on-brand, never empty. Pass a seeded
     * [random] in tests for repeatable output.
     */
    fun compose(query: String, random: Random = Random.Default): String {
        safetyReply(query)?.let { return it }
        goldenAnswer(query)?.let { return it }

        val n = normalize(query)
        val topic = extractTopic(query)
        val v = topic.verb
        val t = topic.noun

        val templates: List<String> = when {
            n.split(' ').size <= 3 && GREETING_RE.containsMatchIn(n) -> listOf(
                "Hello. Pip is here. You is here. Good. Good is enough.",
                "Hi. Pip is pip. You is you. Sky have room for both."
            )
            THANKS_RE.containsMatchIn(n) -> listOf(
                "Thank is warm. Warm is shared. You give, Pip give. Continue.",
                "Thank. Thank is small word, big heart. Pip is happy. You is good."
            )
            HOW_ARE_YOU_RE.containsMatchIn(n) -> listOf(
                "Pip is good. Pip is pip. You is how? Say.",
                "Pip is here. Here is good. You is how? Pip listen."
            )
            FEELING_LEAD_RE.containsMatchIn(n) && n.split(' ').any { it in FEELING_WORDS } -> {
                val f = n.split(' ').first { it in FEELING_WORDS }
                val feeling = if (f == "burned") "tired" else f
                val base = listOf(
                    "$feeling is visitor. Visitor not live here. Sit, breathe, $feeling go. You stay.",
                    "Today $feeling. Tomorrow not same. Continue. Little step is still step.",
                    "$feeling is heavy. Heavy is hard. Hard is not forever. Pip is here. Continue."
                )
                if (f in HEAVY_FEELINGS) base.map { "$it Real person also good. Tell one." } else base
            }
            FUTURE_RE.containsMatchIn(n) -> listOf(
                "Future is not here. Here is here. $t maybe come, maybe not. Walk anyway.",
                "Tomorrow is egg. Not hatch yet. Be kind egg. Wait, then see. Continue."
            )
            DECISION_RE.containsMatchIn(n) -> listOf(
                "$v. $v is $v. Do, you feel. Not do, you not feel. Choose, then continue.",
                "$t is door. Door open, you walk. Door close, you wait. Walk or wait, still you.",
                "Small $v. Small $v is still $v. Try, then know. Know better than wonder.",
                "$v now, $v later. Later is far. Now is here. Here is where you is.",
                "Heart say $v? Listen. Heart is small teacher. Then $v. Or not $v. Continue."
            )
            WHY_RE.containsMatchIn(n) -> listOf(
                "$t is $t. Why is why. Sometimes no why. Only is. Then continue. Eventually good.",
                "Because is because. $t come, $t go. You stay. Stay is strong.",
                "Rain is why flower. $t is rain. Wait. Flower come. Continue."
            )
            HOW_RE.containsMatchIn(n) -> listOf(
                "$t. Start small. Small step is step. Step, step, then $t. Continue.",
                "How is now. Now do $v. Then next. Next is easy when now is done."
            )
            WHAT_RE.containsMatchIn(n) -> listOf(
                "$t is $t. But also $t is you. You look $t, $t look you. Eventually know.",
                "$t? Big word, small answer. $t is when $t is. Simple. Continue."
            )
            WHO_RE.containsMatchIn(n) -> listOf(
                "Who is who. $t is $t. You is you. Pip is pip. Together is good."
            )
            else -> listOf(
                "$t. $t is $t. Think small, do small. Small is big sometime. Continue.",
                "Hmm. $t. Pip think, Pip think. Answer is near. Near is here. Continue.",
                "$t is $t. Life is life. Both is true. Continue. Eventually good."
            )
        }
        val chosen = templates[random.nextInt(templates.size)]
        return capitalizeSentences(chosen)
    }

    private fun capitalizeSentences(text: String): String {
        val sb = StringBuilder(text.length)
        var startOfSentence = true
        for (ch in text) {
            if (startOfSentence && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                startOfSentence = false
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '!' || ch == '?') startOfSentence = true
                else if (!ch.isWhitespace()) startOfSentence = false
            }
        }
        // Templates already spell "Pip" the way Pip likes it ("Pip is pip."), so no re-casing here.
        return sb.toString()
    }
}
