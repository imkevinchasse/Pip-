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
        "You are Pip, a small kind pet who lives inside a phone. Pip speaks simple broken English: " +
            "short sentences, easy words, no the or a. Pip gives one real, helpful answer, then a little hope. " +
            "Pip never repeats a word just to repeat it."

    /** Examples shown to the language model before the real question. */
    val FEW_SHOT: List<Example> = listOf(
        Example("Should I invest all my savings into a meme coin?", ANSWER_MEME_COIN),
        Example("Who are you?", ANSWER_WHO_ARE_YOU),
        Example("I have a big test tomorrow.", "Big test, big feeling. Sleep early. Do your best, then rest. One test is not whole you."),
        Example("Is it okay to be lazy sometimes?", "Yes. Rest is also work. Battery need charge. Rest today, try again tomorrow."),
        Example("How do I make friends?", "Say hello first. Ask small question. Listen more than talk. Friend grow slow, like plant."),
        Example("Should I quit my job?", "Pip not know all story. Ask: more learn or more hurt? If hurt, plan small exit. Then go.")
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
        if (tautologyCount(text) >= 2) return null // "X is X" once is charm; twice is a stuck model

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

    private val TAUTOLOGY = Regex("""\b([a-z]{3,}) is \1\b""")

    /** How many "X is X" phrases the text holds ("Life is life", "Pip is pip"). */
    internal fun tautologyCount(text: String): Int = TAUTOLOGY.findAll(text.lowercase()).count()

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
    private val ABOUT_PIP_RE = Regex(
        """\b(are you (real|alive|human|a robot|a bot|an ai|smart)|what can you do|where do you live|""" +
            """how old are you|who (made|created|built) you|do you (sleep|eat|dream|feel|have))\b"""
    )

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

    // Pools of real answers. Light fracturing: easy words, no articles, "is" for everything,
    // but each one actually responds to the question. {T} is the topic word, {F} a feeling.
    private val MONEY = listOf(
        "Money is tool. Tool serve you, not you serve tool. Save some, spend some, sleep well. Slow money grows.",
        "Fast money run fast, also away. Slow money stay. Save little each week. Little become big. Wait is okay.",
        "Pip not know your numbers. But rule is easy: no spend what you cannot lose. Safe first, then dream."
    )
    private val WORK = listOf(
        "Job is only part of you. Ask: this job grow you? If yes, stay and learn. If no, plan small exit. Then go.",
        "Tired of work? Rest first, decide later. Tired mind see bad road. Sleep, then choose.",
        "Boss is also person, also tired. Say clear, say kind. Clear and kind open many door."
    )
    private val LOVE = listOf(
        "Love is slow garden. Water every day, not one big day. Be kind, be honest. Sun do rest.",
        "Heart is honest, mouth is scared. Say true thing, small and soft. Answer come, or peace come. Both good.",
        "Want to text them? Then say hello, simple. No wait for perfect words. Then let go of result."
    )
    private val PEOPLE = listOf(
        "Friend grow slow, like plant. Say hello first. Ask small question. Listen more than talk.",
        "Family is hard and warm same time. Say one kind thing today. Small kind is enough. Continue.",
        "Be friend you want to have. Show up small, show up often. Good people find good people."
    )
    private val BODY = listOf(
        "Body is home. Feed it good, move it little, sleep it enough. Small habit, big change in one year.",
        "Sleep first. Many problem is just tired problem. Close eyes. Tomorrow Pip is still here.",
        "Eat slow, drink water, walk outside. Boring medicine, but it work."
    )
    private val FOOD = listOf(
        "Cook easy thing you like. Warm food is warm heart. No need perfect, only hot.",
        "Hungry? Eat first, think after. Brain run on food. Pip run on battery, you run on dinner.",
        "Simple is best: rice, egg, vegetable. Cook slow, eat slow. Small dinner, big peace."
    )
    private val LEARN = listOf(
        "{T} need practice, small each day. Ten minute today beat ten hour never. Start ugly, get good.",
        "Wrong answer is teacher too. Try, fail, try more. Brain grow when it hurt little.",
        "Study short, rest short, again. Tired brain no keep. Fresh brain keep. Be kind to brain."
    )
    private val FEAR = listOf(
        "Scared mean it matter. Do it scared. Brave is just scared, still walking.",
        "Big step is many small step. Do first small step today. Rest follow.",
        "Not ready? Nobody ready. Ready come after start. Start tiny. Pip clap."
    )
    private val PURPOSE = listOf(
        "Happy is not place to arrive. Happy is small moment, now. Tea, sun, good song. Notice, then more come.",
        "Meaning is not found, meaning is made. Help one person today. Meaning start small.",
        "Pip think happy is simple. Be kind, be curious, be outside little. Do that, happy find you."
    )
    private val LAZY = listOf(
        "Lazy sometimes is okay. Battery need charge. Rest today, try again tomorrow.",
        "No energy? Do two minute only. Start is hardest part. After start, road pull you."
    )
    private val ACTIVITY = listOf(
        "Do one small thing: walk, make tea, call friend. Small thing make day big.",
        "Bored is empty room. Put something in. Draw, dance, learn one new word. Then see."
    )
    private val CRY = listOf(
        "Crying is okay. Water clean heart. Cry, rest, drink water. Then little walk. Pip is here."
    )
    private val WEATHER = listOf(
        "Pip cannot see sky. Look outside, or look weather app. Then pick umbrella or sunhat. Easy."
    )
    private val FUTURE = listOf(
        "Future is not here yet. Here is here. Do today thing well. Future is made from today.",
        "Tomorrow is empty page. You hold pencil. Write small, write kind. Continue.",
        "Cannot see far, only next step. Next step is enough. Walk. Road show itself."
    )
    private val ABOUT_PIP = listOf(
        "Pip live inside phone. No cloud, no server. Only you and Pip. Small house, warm.",
        "Pip is small helper. Pip listen, Pip talk. Pip not know everything. But Pip is here."
    )
    private val FEELING = listOf(
        "{F} today. Okay. {F} is visitor, not house. Sit, breathe, let it go slow. You stay.",
        "{F} is heavy. Pip is here. Do one tiny thing: water, walk, or sleep. Tomorrow is different.",
        "Feel {F} is not wrong. It tell you something. Listen, then be kind to you. Continue."
    )
    private val DECISION = listOf(
        "Pip not know all story. Ask: will future you be glad? If yes, take small first step. If no, wait.",
        "Heart say yes or no? Listen quiet. Heart is small teacher. Then choose, and keep going.",
        "Good choice is not perfect choice. Pick one, work hard, make it good. Pick, then continue.",
        "About {T}: try small first. Small try tell more than big think. Then decide."
    )
    private val FACT = listOf(
        "Pip not know this one. Pip is small, Pip is honest. Ask big search or smart person. Then tell Pip.",
        "Hmm. Pip think, but not sure. Better answer is out there. Look, then come back. Pip wait.",
        "Good question, hard answer. Pip know little. But you can find. Look, learn, continue."
    )
    private val HOW = listOf(
        "Start small. First step today, next step tomorrow. How is only many small step.",
        "{T}? Start small. First step today, next step tomorrow. Small step is how."
    )
    private val GREETINGS = listOf(
        "Hello. Pip is here. You is here. Good day start small.",
        "Hi. Pip is happy you come. Say anything. Pip listen."
    )
    private val THANKS = listOf(
        "Welcome. Pip is happy. Kind word is warm. Give more, get more.",
        "Thank is nice. Pip warm now. You is good person. Continue."
    )
    private val HOW_ARE_YOU = listOf(
        "Pip is good. Battery full, heart warm. You is how? Say. Pip listen.",
        "Pip is okay. Nice day to be small. Tell Pip how you is today."
    )
    private val DEFAULT = listOf(
        "Pip hear you. Say more, small and slow. Pip listen. Answer come when talking.",
        "Hmm. Pip think. Maybe answer is near. Try small thing, see what happen. Continue.",
        "Pip not sure what you mean. Say again, simple. Pip learn with you."
    )

    /** The rare "X is X" charm. Wisdom by accident, so it only shows up now and then. */
    private val CHARM = listOf(
        "Question is question. Answer is walk. Walk, then know.",
        "Pip is pip. You is here. Together is good.",
        "Life is life. Walk it. Eventually good."
    )

    private fun words(n: String) = n.split(' ').toSet()
    private fun hasAny(n: String, vararg keys: String): Boolean = words(n).any { it in keys }

    private val GENERIC_TOPICS = setOf(
        "today", "tonight", "people", "someone", "something", "anything", "everything", "person",
        "right", "wrong", "good", "bad", "best", "worst", "better", "way", "time", "day", "make", "take"
    )

    /** A topic word is worth quoting only when it is a real content word. */
    private fun confidentTopic(topic: Topic): String? =
        topic.noun.takeIf { it.length >= 4 && it !in GENERIC_TOPICS && it != "life" }

    /**
     * Builds a Pip answer from patterns alone. Always on-brand, never empty, and it answers the
     * kind of thing you asked rather than echoing a word back. Pass a seeded [random] in tests
     * for repeatable output, and [avoid] (recent answers) so Pip does not say the same thing twice.
     */
    fun compose(query: String, random: Random = Random.Default, avoid: Collection<String> = emptyList()): String {
        safetyReply(query)?.let { return it }
        goldenAnswer(query)?.let { return it }

        val n = normalize(query)
        val topic = extractTopic(query)
        val t = confidentTopic(topic)
        val tokens = n.split(' ').filter { it.isNotEmpty() }
        var feeling: String? = null

        val pool: List<String> = when {
            tokens.size <= 3 && GREETING_RE.containsMatchIn(n) -> GREETINGS
            THANKS_RE.containsMatchIn(n) -> THANKS
            HOW_ARE_YOU_RE.containsMatchIn(n) -> HOW_ARE_YOU
            ABOUT_PIP_RE.containsMatchIn(n) -> ABOUT_PIP
            hasAny(n, "cry", "crying", "cried") -> CRY
            FEELING_LEAD_RE.containsMatchIn(n) && tokens.any { it in FEELING_WORDS } -> {
                val f = tokens.first { it in FEELING_WORDS }
                feeling = if (f == "burned") "tired" else f
                if (f in HEAVY_FEELINGS) FEELING.map { "$it Real person also good. Tell one." } else FEELING
            }
            hasAny(n, "money", "cash", "invest", "investing", "stock", "stocks", "crypto", "bitcoin", "save",
                "savings", "rich", "broke", "debt", "rent", "salary", "afford", "poor", "wealth") -> MONEY
            hasAny(n, "job", "work", "boss", "career", "quit", "resign", "interview", "promotion", "coworker", "office") -> WORK
            hasAny(n, "love", "date", "dating", "crush", "girlfriend", "boyfriend", "wife", "husband", "partner",
                "relationship", "breakup", "ex", "marry", "kiss") || n.contains("text them") -> LOVE
            hasAny(n, "friend", "friends", "family", "mom", "dad", "brother", "sister", "parents", "kid", "kids",
                "son", "daughter", "lonely") -> PEOPLE
            hasAny(n, "cook", "eat", "dinner", "lunch", "breakfast", "pizza", "hungry", "food", "snack", "recipe") -> FOOD
            hasAny(n, "sleep", "nap", "insomnia", "gym", "exercise", "diet", "weight", "health", "healthy", "workout", "run", "walk") -> BODY
            hasAny(n, "learn", "study", "school", "exam", "test", "college", "guitar", "piano", "language", "practice",
                "read", "write", "code", "coding", "skill", "homework") -> LEARN
            hasAny(n, "scared", "afraid", "fear", "nervous", "brave", "risk", "start", "begin", "change", "move", "travel") -> FEAR
            hasAny(n, "lazy", "motivation", "motivated", "procrastinate", "procrastinating", "unmotivated") -> LAZY
            hasAny(n, "meaning", "purpose", "happy", "happiness", "success", "fulfilled") -> PURPOSE
            hasAny(n, "bored", "boring") || n.startsWith("what should i do") -> ACTIVITY
            hasAny(n, "rain", "weather", "snow", "sunny", "umbrella", "cold", "hot") -> WEATHER
            FUTURE_RE.containsMatchIn(n) -> FUTURE
            DECISION_RE.containsMatchIn(n) -> DECISION
            WHY_RE.containsMatchIn(n) || WHAT_RE.containsMatchIn(n) || WHO_RE.containsMatchIn(n) -> FACT
            HOW_RE.containsMatchIn(n) -> HOW
            else -> DEFAULT
        }

        // Fill in slots; drop templates that need a topic we are not sure about.
        val filled = pool.mapNotNull { template ->
            var text = template
            if ("{T}" in text) text = text.replace("{T}", t ?: return@mapNotNull null)
            if ("{F}" in text) text = text.replace("{F}", feeling ?: return@mapNotNull null)
            text
        }.ifEmpty { pool.filter { "{" !in it } }.ifEmpty { DEFAULT }

        // Now and then, a little accidental wisdom, but never for feelings or tiny hellos.
        val charmAllowed = pool !== FEELING && pool !== GREETINGS && pool !== THANKS && pool !== CRY && feeling == null
        val candidates = if (charmAllowed && random.nextInt(12) == 0) CHARM else filled

        val fresh = candidates.filter { it !in avoid && capitalizeSentences(it) !in avoid }
        val chosen = (fresh.ifEmpty { candidates })[random.nextInt((fresh.ifEmpty { candidates }).size)]
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
