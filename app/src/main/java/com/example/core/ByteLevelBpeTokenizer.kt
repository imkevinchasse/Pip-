package com.example.core

/**
 * Byte-level BPE tokenizer (GPT-2 style) loaded from a Hugging Face `tokenizer.json`.
 *
 * SmolLM2 uses exactly this scheme: optional "split digits" step, GPT-2 regex pre-tokenizer,
 * byte-to-unicode mapping, then BPE merges.
 *
 * Security note: [encode] treats its input as plain text and never turns text such as
 * "<|im_end|>" into control tokens. Control tokens are added by the caller through [tokenId].
 */
class ByteLevelBpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val idToToken: Array<String?>,
    private val mergeRanks: Map<String, Int>,
    private val specialIds: Set<Int>,
    private val splitDigits: Boolean
) {

    private val bpeCache = HashMap<String, List<String>>()

    val vocabSize: Int get() = idToToken.size

    fun tokenId(token: String): Int? = vocab[token]

    fun isSpecial(id: Int): Boolean = id in specialIds

    /** Encodes plain text (control-token look-alikes are ordinary text). */
    fun encode(text: String): IntArray {
        if (text.isEmpty()) return IntArray(0)
        val ids = ArrayList<Int>(text.length / 3 + 4)
        for (piece in splitOnDigits(text)) {
            for (match in GPT2_PATTERN.findAll(piece)) {
                val word = toByteLevel(match.value)
                for (token in bpe(word)) {
                    val id = vocab[token]
                    if (id != null) {
                        ids.add(id)
                    } else {
                        // Should not happen with a byte-level vocab, but never lose text silently:
                        for (ch in token) vocab[ch.toString()]?.let { ids.add(it) }
                    }
                }
            }
        }
        return ids.toIntArray()
    }

    fun decode(ids: IntArray, skipSpecial: Boolean = true): String {
        val bytes = java.io.ByteArrayOutputStream(ids.size * 3)
        for (id in ids) {
            if (id < 0 || id >= idToToken.size) continue
            if (skipSpecial && id in specialIds) continue
            val token = idToToken[id] ?: continue
            for (ch in token) {
                val b = UNICODE_TO_BYTE[ch]
                if (b != null) bytes.write(b) else bytes.write(ch.toString().toByteArray(Charsets.UTF_8))
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun splitOnDigits(text: String): List<String> {
        if (!splitDigits) return listOf(text)
        val parts = ArrayList<String>()
        val run = StringBuilder()
        for (ch in text) {
            if (Character.isDigit(ch)) {
                if (run.isNotEmpty()) {
                    parts.add(run.toString())
                    run.setLength(0)
                }
                parts.add(ch.toString())
            } else {
                run.append(ch)
            }
        }
        if (run.isNotEmpty()) parts.add(run.toString())
        return parts
    }

    private fun toByteLevel(word: String): String {
        val sb = StringBuilder()
        for (b in word.toByteArray(Charsets.UTF_8)) sb.append(BYTE_TO_UNICODE[b.toInt() and 0xFF])
        return sb.toString()
    }

    private fun bpe(word: String): List<String> {
        bpeCache[word]?.let { return it }
        var parts = ArrayList<String>(word.length)
        for (ch in word) parts.add(ch.toString())

        while (parts.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1
            for (i in 0 until parts.size - 1) {
                val rank = mergeRanks[parts[i] + " " + parts[i + 1]]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break
            val first = parts[bestIndex]
            val second = parts[bestIndex + 1]
            val merged = ArrayList<String>(parts.size)
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == first && parts[i + 1] == second) {
                    merged.add(first + second)
                    i += 2
                } else {
                    merged.add(parts[i])
                    i++
                }
            }
            parts = merged
        }
        if (bpeCache.size > 20_000) bpeCache.clear()
        bpeCache[word] = parts
        return parts
    }

    companion object {
        // The GPT-2 / ByteLevel pre-tokenizer pattern used by Hugging Face tokenizers.
        private val GPT2_PATTERN =
            Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")

        private val BYTE_TO_UNICODE: CharArray = CharArray(256)
        private val UNICODE_TO_BYTE: Map<Char, Int>

        init {
            val printable = ArrayList<Int>()
            for (b in '!'.code..'~'.code) printable.add(b)
            for (b in '¡'.code..'¬'.code) printable.add(b)
            for (b in '®'.code..'ÿ'.code) printable.add(b)
            val printableSet = printable.toHashSet()
            var extra = 0
            val reverse = HashMap<Char, Int>()
            for (b in 0 until 256) {
                val ch = if (b in printableSet) b.toChar() else (256 + extra++).toChar()
                BYTE_TO_UNICODE[b] = ch
                reverse[ch] = b
            }
            UNICODE_TO_BYTE = reverse
        }

        /** The 256 printable stand-ins for raw bytes (exposed for tests that build a tokenizer). */
        internal fun byteLevelAlphabet(): List<String> = BYTE_TO_UNICODE.map { it.toString() }

        fun fromJson(json: String): ByteLevelBpeTokenizer {
            val root = MiniJson.asObject(MiniJson.parse(json))
                ?: throw IllegalArgumentException("tokenizer.json is not a JSON object")
            val model = MiniJson.asObject(root["model"])
                ?: throw IllegalArgumentException("tokenizer.json has no model section")
            val type = model["type"] as? String
            if (type != null && type != "BPE") {
                throw IllegalArgumentException("Unsupported tokenizer type: $type")
            }

            val vocab = LinkedHashMap<String, Int>()
            val rawVocab = MiniJson.asObject(model["vocab"])
                ?: throw IllegalArgumentException("tokenizer.json has no vocab")
            for ((token, id) in rawVocab) vocab[token] = (id as Number).toInt()

            val specialIds = HashSet<Int>()
            val added = MiniJson.asArray(root["added_tokens"]).orEmpty()
            for (entry in added) {
                val obj = MiniJson.asObject(entry) ?: continue
                val content = obj["content"] as? String ?: continue
                val id = (obj["id"] as? Number)?.toInt() ?: continue
                vocab[content] = id
                if (obj["special"] == true) specialIds.add(id)
            }

            var maxId = -1
            for (id in vocab.values) if (id > maxId) maxId = id
            val idToToken = arrayOfNulls<String>(maxId + 1)
            for ((token, id) in vocab) if (id >= 0) idToToken[id] = token

            val merges = HashMap<String, Int>()
            val rawMerges = MiniJson.asArray(model["merges"]).orEmpty()
            for ((rank, merge) in rawMerges.withIndex()) {
                val key = when (merge) {
                    is String -> merge
                    is List<*> -> if (merge.size == 2) "${merge[0]} ${merge[1]}" else continue
                    else -> continue
                }
                merges.putIfAbsent(key, rank)
            }
            if (merges.isEmpty()) throw IllegalArgumentException("tokenizer.json has no merges")

            val splitDigits = detectIndividualDigits(root["pre_tokenizer"])
            return ByteLevelBpeTokenizer(vocab, idToToken, merges, specialIds, splitDigits)
        }

        private fun detectIndividualDigits(node: Any?): Boolean {
            val obj = MiniJson.asObject(node) ?: return false
            if (obj["type"] == "Digits") return obj["individual_digits"] == true
            val children = MiniJson.asArray(obj["pretokenizers"]).orEmpty()
            return children.any { detectIndividualDigits(it) }
        }
    }
}
