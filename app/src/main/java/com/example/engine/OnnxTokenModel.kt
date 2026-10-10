package com.example.engine

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.example.core.TokenModel
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Runs a decoder-only transformer exported to ONNX (the layout used by onnx-community/SmolLM2)
 * one step at a time with a key/value cache, so each new word costs one tiny forward pass.
 *
 * It reads the model's own input list instead of assuming one, so it works whether or not the
 * export asks for `position_ids`, and for any layer count.
 *
 * RAM: weights (about 100-200 MB for the 4-bit file) + a KV cache of ~24 MB at 512 tokens.
 * The CPU memory arena is switched off so big temporary buffers (the logits of a long prompt)
 * go back to the system immediately instead of staying reserved.
 */
class OnnxTokenModel private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val kvInputNames: List<String>,
    private val kvHeads: Long,
    private val kvHeadDim: Long,
    private val hasPositionIds: Boolean
) : TokenModel, AutoCloseable {

    private var lastResult: OrtSession.Result? = null
    private var pastLength = 0

    override fun reset() {
        lastResult?.close()
        lastResult = null
        pastLength = 0
    }

    override fun step(tokens: IntArray): FloatArray {
        require(tokens.isNotEmpty()) { "no tokens" }
        val n = tokens.size
        val total = pastLength + n
        val temporary = ArrayList<OnnxTensor>()
        val inputs = LinkedHashMap<String, OnnxTensor>()
        try {
            inputs["input_ids"] = longTensor(LongArray(n) { tokens[it].toLong() }, longArrayOf(1, n.toLong()), temporary)
            inputs["attention_mask"] = longTensor(LongArray(total) { 1L }, longArrayOf(1, total.toLong()), temporary)
            if (hasPositionIds) {
                inputs["position_ids"] =
                    longTensor(LongArray(n) { (pastLength + it).toLong() }, longArrayOf(1, n.toLong()), temporary)
            }
            for (name in kvInputNames) {
                val previous = lastResult
                inputs[name] = if (previous == null) {
                    val empty = OnnxTensor.createTensor(
                        env, FloatBuffer.allocate(0), longArrayOf(1, kvHeads, 0, kvHeadDim)
                    )
                    temporary.add(empty)
                    empty
                } else {
                    val outputName = name.replaceFirst("past_key_values", "present")
                    val value = previous.get(outputName).orElseThrow {
                        IllegalStateException("Model output $outputName is missing")
                    }
                    value as OnnxTensor
                }
            }

            val result = session.run(inputs)
            // The previous cache tensors were only inputs; now that run() finished, free them.
            lastResult?.close()
            lastResult = result
            pastLength = total

            val logits = result.get("logits").orElseThrow { IllegalStateException("Model has no logits output") } as OnnxTensor
            val shape = (logits.info as TensorInfo).shape
            val vocab = shape[shape.size - 1].toInt()
            val rows = shape[shape.size - 2].toInt()
            val buffer = logits.floatBuffer.duplicate()
            buffer.position((rows - 1) * vocab)
            val last = FloatArray(vocab)
            buffer.get(last)
            return last
        } finally {
            for (t in temporary) t.close()
        }
    }

    override fun close() {
        reset()
        try {
            session.close()
        } catch (_: Throwable) {
        }
    }

    private fun longTensor(data: LongArray, shape: LongArray, track: MutableList<OnnxTensor>): OnnxTensor {
        val t = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
        track.add(t)
        return t
    }

    companion object {
        /** Throws if the model cannot be used (wrong format, not enough memory, unsupported type). */
        fun load(modelPath: String, threads: Int): OnnxTokenModel {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads.coerceIn(1, 4))
                setCPUArenaAllocator(false)
                setMemoryPatternOptimization(false)
            }
            val session = env.createSession(modelPath, options)
            try {
                val kvNames = session.inputNames.filter { it.startsWith("past_key_values") }.sorted()
                if (kvNames.isEmpty()) throw IllegalStateException("This model has no key/value cache inputs")

                val info = session.inputInfo[kvNames.first()]!!.info as TensorInfo
                if (info.type != OnnxJavaType.FLOAT) {
                    throw IllegalStateException("Unsupported model precision: ${info.type}")
                }
                val shape = info.shape // [batch, heads, past_seq, head_dim]
                if (shape.size != 4 || shape[1] <= 0 || shape[3] <= 0) {
                    throw IllegalStateException("Unexpected key/value cache shape")
                }
                return OnnxTokenModel(
                    env, session, kvNames, shape[1], shape[3],
                    hasPositionIds = session.inputNames.contains("position_ids")
                )
            } catch (t: Throwable) {
                session.close()
                throw t
            }
        }
    }
}
