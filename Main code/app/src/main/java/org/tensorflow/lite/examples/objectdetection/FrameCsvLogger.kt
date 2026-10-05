package org.tensorflow.lite.examples.objectdetection

import java.io.Closeable
import java.io.File
import java.util.Locale

class FrameCsvLogger(file: File) : Closeable {
    private val writer = file.bufferedWriter()

    init {
        writer.appendLine(
            "runId,scenario,frameIndex,imageId,mode,modelId," +
                    "phase,selectionReason,switched," +
                    "targetDutyCycle,workerCount," +
                    "frameStartElapsedNs,selectionDurationNs," +
                    "inferenceNs,detectCallDurationNs,responseDurationNs," +
                    "deadlineNs,inferenceDeadlineMiss,responseDeadlineMiss," +
                    "confidence,processCpuPercentOneCore," +
                    "warmupDurationNs,emaLatencyMsBefore,windowDMRBefore," +
                    "observationsBefore,windowSamplesBefore," +
                    "batteryTemperatureC,thermalStatus,plugged,thermalSampleAgeMs"
        )
    }

    fun record(
        runId: String,
        scenario: String,
        frameIndex: Int,
        imageId: Int,
        mode: String,
        decision: SelectionDecision,
        targetDutyCycle: Double,
        workerCount: Int,
        frameStartElapsedNs: Long,
        selectionDurationNs: Long,
        inferenceNs: Long,
        detectCallDurationNs: Long,
        responseDurationNs: Long,
        deadlineNs: Long,
        confidence: Double,
        processCpuPercentOneCore: Double?,
        warmupDurationNs: Long = 0L,
        thermal: ThermalSample? = null
    ) {
        val values = listOf(
            runId,
            scenario,
            frameIndex.toString(),
            imageId.toString(),
            mode,
            decision.modelId.toString(),
            decision.phase.name.lowercase(Locale.ROOT),
            decision.reason,
            decision.switched.toString(),
            number(targetDutyCycle),
            workerCount.toString(),
            frameStartElapsedNs.toString(),
            selectionDurationNs.toString(),
            inferenceNs.toString(),
            detectCallDurationNs.toString(),
            responseDurationNs.toString(),
            deadlineNs.toString(),
            (inferenceNs > deadlineNs).toString(),
            (responseDurationNs > deadlineNs).toString(),
            number(confidence),
            processCpuPercentOneCore?.let(::number).orEmpty(),
            warmupDurationNs.toString(),
            decision.emaLatencyMsBefore?.let(::number).orEmpty(),
            decision.windowDMRBefore?.let(::number).orEmpty(),
            decision.observationsBefore.toString(),
            decision.windowSamplesBefore.toString(),
            thermal?.batteryTemperatureC?.let(::number).orEmpty(),
            thermal?.thermalStatus?.toString().orEmpty(),
            thermal?.plugged?.toString().orEmpty(),
            thermal?.let {
                number(
                    (frameStartElapsedNs - it.sampledAtElapsedNs)
                        .coerceAtLeast(0L) / 1_000_000.0
                )
            }.orEmpty()
        )

        writer.appendLine(values.joinToString(",") { escape(it) })
    }

    private fun number(value: Double): String =
        if (value.isFinite()) value.toString() else ""

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    override fun close() {
        writer.close()
    }
}