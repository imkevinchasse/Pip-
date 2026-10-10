package com.example.core

import java.util.PriorityQueue
import kotlin.math.exp
import kotlin.random.Random

/** Temperature / top-k / top-p sampling with a repetition penalty. Pure Kotlin. */
class Sampler(
    private val temperature: Float = 0.7f,
    private val topK: Int = 40,
    private val topP: Float = 0.92f,
    private val repetitionPenalty: Float = 1.15f,
    private val random: Random = Random.Default
) {

    /**
     * @param logits raw model scores for the next token (not modified)
     * @param recent token ids to discourage repeating
     * @param banned token ids that must never be chosen
     */
    fun sample(logits: FloatArray, recent: IntArray = IntArray(0), banned: Set<Int> = emptySet()): Int {
        val scores = logits.copyOf()

        if (repetitionPenalty != 1.0f) {
            for (id in recent) {
                if (id < 0 || id >= scores.size) continue
                val s = scores[id]
                scores[id] = if (s > 0) s / repetitionPenalty else s * repetitionPenalty
            }
        }
        for (id in banned) if (id >= 0 && id < scores.size) scores[id] = Float.NEGATIVE_INFINITY

        if (temperature <= 0f) return argmax(scores)

        // Keep the best topK candidates.
        val k = topK.coerceIn(1, scores.size)
        val heap = PriorityQueue<Int>(k + 1) { a, b -> scores[a].compareTo(scores[b]) } // min-heap
        for (i in scores.indices) {
            if (scores[i] == Float.NEGATIVE_INFINITY) continue
            if (heap.size < k) {
                heap.add(i)
            } else if (scores[i] > scores[heap.peek()!!]) {
                heap.poll()
                heap.add(i)
            }
        }
        if (heap.isEmpty()) return argmax(logits)

        val candidates = heap.toMutableList().sortedByDescending { scores[it] }
        val maxScore = scores[candidates[0]]
        val probs = FloatArray(candidates.size)
        var sum = 0.0f
        for (i in candidates.indices) {
            probs[i] = exp((scores[candidates[i]] - maxScore) / temperature)
            sum += probs[i]
        }
        for (i in probs.indices) probs[i] /= sum

        // Nucleus (top-p) cut.
        var cumulative = 0.0f
        var cut = probs.size
        for (i in probs.indices) {
            cumulative += probs[i]
            if (cumulative >= topP) {
                cut = i + 1
                break
            }
        }
        var norm = 0.0f
        for (i in 0 until cut) norm += probs[i]
        var r = random.nextFloat() * norm
        for (i in 0 until cut) {
            r -= probs[i]
            if (r <= 0f) return candidates[i]
        }
        return candidates[cut - 1]
    }

    private fun argmax(values: FloatArray): Int {
        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in values.indices) {
            if (values[i] > bestValue) {
                bestValue = values[i]
                best = i
            }
        }
        return best
    }
}
