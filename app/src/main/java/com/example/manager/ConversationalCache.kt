package com.example.manager

import java.util.LinkedHashMap

data class CachedInteraction(
    val queryKey: String,
    val fracturedResponse: String,
    val synthesizedPcm: ByteArray? = null,
    val sampleRate: Int = 22050,
    val accessCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

class ConversationalCache(private val maxCapacity: Int = 64) {

    private val lock = Any()
    private val lruMap = object : LinkedHashMap<String, CachedInteraction>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedInteraction>?): Boolean {
            return size > maxCapacity
        }
    }

    var hitCount: Int = 0
        private set
    var missCount: Int = 0
        private set

    init {
        // Pre-seed core prompt wisdom queries for instant <10ms lookup
        seed("is life difficult", "i can say yes, but can say yes, but sometimes if hard, is be difficult, because it hard.")
        seed("should i put my savings into a memecoin", "Bad is lose, you will bad keep instead bank in better.")
        seed("who am i", "You is you.")
        seed("what is happiness", "Smile inside, sun shine but inside heart, when do good, you is happy.")
        seed("will i succeed", "Try make big walk, small feet step step, mountain become little ground.")
        seed("why do we sleep", "Eyes close, soul go visit stars, body rest soft so tomorrow be tomorrow.")
        seed("should i text them", "No send message if tummy feel ouch. Eat bread instead.")
        seed("what is love", "Heart go bump bump like eat cheese, warm like sunshine nap on floor.")
        seed("what should i eat", "Yum thing! Warm soup or potato, belly happy then heart quiet.")
        seed("who are you", "Pip is Pip! Tiny cosmic pet live inside phone, speak little words big heart.")
        seed("what is time", "Tick tock is invention of clocks so eat lunch on clock. Time is now.")
    }

    private fun seed(query: String, response: String) {
        val key = normalize(query)
        lruMap[key] = CachedInteraction(
            queryKey = key,
            fracturedResponse = response,
            synthesizedPcm = null,
            accessCount = 0
        )
    }

    fun normalize(text: String): String {
        return text.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
    }

    fun get(query: String): CachedInteraction? {
        val key = normalize(query)
        synchronized(lock) {
            val item = lruMap[key]
            if (item != null) {
                hitCount++
                val updated = item.copy(accessCount = item.accessCount + 1)
                lruMap[key] = updated
                return updated
            } else {
                missCount++
                return null
            }
        }
    }

    fun put(query: String, response: String, pcm: ByteArray? = null, sampleRate: Int = 22050) {
        val key = normalize(query)
        if (key.isBlank()) return
        synchronized(lock) {
            lruMap[key] = CachedInteraction(
                queryKey = key,
                fracturedResponse = response,
                synthesizedPcm = pcm,
                sampleRate = sampleRate
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            lruMap.clear()
            hitCount = 0
            missCount = 0
        }
    }

    fun getEstimatedMemorySizeBytes(): Long {
        synchronized(lock) {
            var total = 0L
            for ((k, v) in lruMap) {
                total += k.length * 2
                total += v.fracturedResponse.length * 2
                total += v.synthesizedPcm?.size ?: 0
            }
            return total
        }
    }

    fun size(): Int = synchronized(lock) { lruMap.size }
}
