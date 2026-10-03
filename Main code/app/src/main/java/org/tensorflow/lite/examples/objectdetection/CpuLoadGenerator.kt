package org.tensorflow.lite.examples.objectdetection

import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

class CpuLoadGenerator {
    @Volatile
    private var executor: ExecutorService? = null

    // Не даёт оптимизатору удалить вычисления как бесполезные.
    @Volatile
    private var resultSink = 0L

    @Synchronized
    fun start(
        workers: Int,
        dutyCycle: Double,
        periodMs: Long = 100
    ) {
        require(workers in 1..Runtime.getRuntime().availableProcessors())
        require(dutyCycle in 0.0..1.0)
        require(periodMs in 1..1_000)
        check(executor == null) { "CPU load is already running" }

        val pool = Executors.newFixedThreadPool(workers)
        executor = pool

        val periodNs = periodMs * 1_000_000L
        val busyNs = (periodNs * dutyCycle).toLong()

        repeat(workers) { workerId ->
            pool.execute {
                val thread = Thread.currentThread()
                var value = workerId.toLong() + 1

                while (!thread.isInterrupted) {
                    val cycleStart = SystemClock.elapsedRealtimeNanos()
                    val busyUntil = cycleStart + busyNs

                    while (!thread.isInterrupted &&
                        SystemClock.elapsedRealtimeNanos() < busyUntil
                    ) {
                        value = value xor (value shl 13)
                        value = value xor (value ushr 7)
                        value = value xor (value shl 17)
                    }

                    resultSink = value

                    val remaining =
                        cycleStart + periodNs - SystemClock.elapsedRealtimeNanos()
                    if (remaining > 0) {
                        LockSupport.parkNanos(remaining)
                    }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        val pool = executor ?: return
        executor = null
        pool.shutdownNow()
        pool.awaitTermination(2, TimeUnit.SECONDS)
    }
}