package com.example.manager

import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections

/**
 * Opens a bundled file that may have been split into pieces so each piece fits under GitHub's
 * 100 MB file limit. Pieces are named `name.001`, `name.002`, ... and are joined in order.
 * A whole file named exactly `name` wins if both exist.
 */
object ChunkedAssets {
    private const val MAX_PARTS = 999

    fun open(opener: (String) -> InputStream?, name: String): InputStream? {
        opener(name)?.let { return it }
        val parts = ArrayList<InputStream>()
        try {
            for (i in 1..MAX_PARTS) {
                val part = opener("$name.%03d".format(i)) ?: break
                parts += part
            }
        } catch (e: IOException) {
            parts.forEach { runCatching { it.close() } }
            throw e
        }
        if (parts.isEmpty()) return null
        return SequenceInputStream(Collections.enumeration(parts))
    }
}
