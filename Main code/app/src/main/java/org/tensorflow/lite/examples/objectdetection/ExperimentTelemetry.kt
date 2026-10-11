package org.tensorflow.lite.examples.objectdetection

import android.os.SystemClock
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExperimentCsv(file: File) : Closeable {
    private val writer = file.bufferedWriter()

    init {
        writer.appendLine("runId,repeatId,scenario,runPhase,frameIndex,imageId,datasetPass,modelId,phase,selectionReason,switched,emaLatencyMsBefore,windowDMRBefore,observationsBefore,windowSamplesBefore,runElapsedNs,selectionNs,initializationNs,preprocessingNs,detectorCallNs,inferenceNs,responseNs,decodeNs,deadlineNs,inferenceMiss,responseMiss,confidence,loadStepStart,loadStepEnd,targetDuty,workers,crossedLoadTransition,processCpuPercentOneCore,cpuSampleAgeMs,batteryTemperatureC,thermalStatus,plugged,thermalSampleAgeMs")
    }

    fun row(vararg values: Any?) {
        writer.appendLine(values.joinToString(",") {
            val s = it?.toString().orEmpty()
            if (s.any { c -> c == ',' || c == '"' || c == '\n' || c == '\r' })
                "\"" + s.replace("\"", "\"\"") + "\"" else s
        })
    }

    override fun close() = writer.close()
}

data class CpuSample(val atNs: Long, val percent: Double)
class ExperimentCpuSampler : Closeable {
    @Volatile
    var latest: CpuSample? = null; private set
    private var lastCpu = android.os.Process.getElapsedCpuTime()
    private var lastWall = SystemClock.elapsedRealtimeNanos()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    init {
        executor.scheduleWithFixedDelay({
            val cpu = android.os.Process.getElapsedCpuTime()
            val wall = SystemClock.elapsedRealtimeNanos()
            if (wall > lastWall && cpu >= lastCpu)
                latest = CpuSample(wall, (cpu - lastCpu) * 1_000_000.0 / (wall - lastWall) * 100.0)
            lastCpu = cpu; lastWall = wall
        }, 1, 1, TimeUnit.SECONDS)
    }

    override fun close() {
        executor.shutdownNow()
    }
}
