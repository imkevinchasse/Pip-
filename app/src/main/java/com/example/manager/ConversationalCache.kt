package com.example.manager

import com.example.personality.FracturedEnglish

/**
 * Remembers Pip's recent answers so asking the same thing twice is instant and consistent.
 * Holds only short text (never audio), so it costs a few kilobytes of RAM.
 */
class ConversationalCache(private val maxCapacity: Int = 64) {

    private val lock = Any()
    private val lru = object : LinkedHashMap<String, String>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxCapacity
    }

    var hitCount: Int = 0
        private set
    var missCount: Int = 0
        private set

    fun get(query: String): String? {
        val key = FracturedEnglish.normalize(query)
        synchronized(lock) {
            val value = lru[key]
            if (value != null) hitCount++ else missCount++
            return value
        }
    }

    fun put(query: String, response: String) {
        val key = FracturedEnglish.normalize(query)
        if (key.isBlank() || response.isBlank()) return
        synchronized(lock) { lru[key] = response }
    }

    fun clear() {
        synchronized(lock) {
            lru.clear()
            hitCount = 0
            missCount = 0
        }
    }

    fun size(): Int = synchronized(lock) { lru.size }
}
