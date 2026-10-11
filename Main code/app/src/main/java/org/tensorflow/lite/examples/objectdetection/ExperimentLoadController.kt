package org.tensorflow.lite.examples.objectdetection

import android.os.SystemClock
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class LoadState(val step: Int, val duty: Double, val workers: Int, val appliedNs: Long)

/** Transitions run independently of inference. A transition is published AFTER application. */
class ExperimentLoadController(private val config: ExperimentConfig, file: File) : Closeable {
    private val generator = CpuLoadGenerator()
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val writer = file.bufferedWriter()
    private var runningWorkers = false
    private var closed = false

    @Volatile
    var failure: Throwable? = null; private set

    @Volatile
    var state = LoadState(-1, 0.0, 0, 0); private set
    private val workers = minOf(config.loadWorkers, Runtime.getRuntime().availableProcessors())

    init {
        writer.appendLine("step,duty,workers,requestedElapsedNs,appliedElapsedNs,periodMs")
    }

    @Synchronized
    private fun apply(step: Int, duty: Double, requestedNs: Long) {
        if (closed) return
        if (duty == 0.0) {
            if (runningWorkers) generator.stop()
            runningWorkers = false
        } else {
            if (runningWorkers) generator.setDutyCycle(duty)
            else {
                generator.start(workers, duty, config.loadPeriodMs); runningWorkers = true
            }
        }
        state = LoadState(
            step, duty, if (duty == 0.0) 0 else workers,
            SystemClock.elapsedRealtimeNanos()
        )
        writer.appendLine("$step,$duty,${state.workers},$requestedNs,${state.appliedNs},${config.loadPeriodMs}")
        writer.flush()
    }

    fun prepare(scenario: String) = apply(
        0, if (scenario == "cpu75") 0.75 else 0.0,
        SystemClock.elapsedRealtimeNanos()
    )

    fun startDynamic(startNs: Long) {
        listOf(0.50, 0.75, 0.0).forEachIndexed { index, duty ->
            val requested = startNs + (index + 1) * config.dynamicStageMs * 1_000_000L
            val delay = (requested - SystemClock.elapsedRealtimeNanos()).coerceAtLeast(0L)
            executor.schedule({
                try {
                    apply(index + 1, duty, requested)
                } catch (t: Throwable) {
                    failure = t
                }
            }, delay, TimeUnit.NANOSECONDS)
        }
    }

    override fun close() {
        executor.shutdownNow()
        synchronized(this) {
            if (closed) return
            closed = true
            try {
                if (runningWorkers) generator.stop()
            } finally {
                writer.close()
            }
        }
    }
}
