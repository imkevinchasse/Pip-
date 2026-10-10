package com.example.engine

import android.os.Debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Measures the REAL memory Pip uses (proportional set size of the whole process, including
 * the native memory of the speech and language models). The old meter was an invented number.
 */
class RamMeter(private val scope: CoroutineScope) {

    private val _usedMb = MutableStateFlow(read())
    val usedMb: StateFlow<Float> = _usedMb.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                _usedMb.value = read()
                delay(2_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun read(): Float {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        return info.totalPss / 1024f
    }

    companion object {
        /** Pip's design limit. The models together stay far below this. */
        const val BUDGET_MB = 1500f
    }
}
