package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.util.Log
import org.HdrHistogram.Histogram
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TimeStatsCollector(private val context: Context, private val deadlineNs: Long) {

    private var logFile: File? = null

    init {
        createLogFile()
    }

    val lowestDiscernibleNs = 1_000L // 1 µs in ns
    val highestTrackableNs = TimeUnit.MINUTES.toNanos(1)
    val sigDigits = 3

    val histHybrid = Histogram(lowestDiscernibleNs, highestTrackableNs, sigDigits)
    val histMV = Histogram(lowestDiscernibleNs, highestTrackableNs, sigDigits)
    val histE0 = Histogram(lowestDiscernibleNs, highestTrackableNs, sigDigits)
    val histE1 = Histogram(lowestDiscernibleNs, highestTrackableNs, sigDigits)
    val histE2 = Histogram(lowestDiscernibleNs, highestTrackableNs, sigDigits)

    val modelIndexMapping: Map<Int, Histogram> = mapOf(
        999 to histHybrid,
        0 to histMV,
        1 to histE0,
        2 to histE1,
        3 to histE2
    )

    fun record(modelIndex: Int, latencyNano: Long) {
        modelIndexMapping[modelIndex]!!.recordValue(latencyNano)
    }

    fun logResults() {
        val timestamp = getCurrentTimestamp()
        Log.d("StatsHelper", "Log timings")

        listOf(histMV, histE0, histE1, histE2).forEach {
            val s = getStats(it)

            var modelName: String
            if (it === histMV) {
                modelName = "MobileNet V1"
            } else if (it === histE0) {
                modelName = "EfficientDet Lite0"
            } else if (it === histE1) {
                modelName = "EfficientDet Lite1"
            } else {
                modelName = "EfficientDet Lite2"
            }
            val logMessage =
                "$timestamp,${modelName},${it.totalCount},${s.avgNs},${s.median},${s.p99},${s.p999},${s.dmr},${s.wcet}" // Updated log message
            writeToLogFile(logMessage)
        }
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun createLogFile() {
        val fileName = "time_log_${System.currentTimeMillis()}.csv"
        val directory = context.getExternalFilesDir(null)

        logFile = if (directory != null) {
            File(directory, fileName)
        } else {
            File(context.filesDir, fileName)
        }

        writeToLogFile("Timestamp,SelectedModel,TotalCount,AverageLatency,MedianLatency,p99Latency,p999Latency,DeadlineMissRatio,WorstCaseExecutionTime")
    }

    private fun writeToLogFile(message: String) {
        logFile?.let {
            try {
                FileWriter(it, true).use { writer ->
                    writer.append(message)
                    writer.appendLine()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun getStats(hist: Histogram): Stats {
        val avgNs = hist.mean.toLong()
        val median = hist.getValueAtPercentile(50.0)
        val p99 = hist.getValueAtPercentile(99.0)
        val p999 = hist.getValueAtPercentile(99.9)
        val wcet = hist.maxValue

        val misses = hist.getCountBetweenValues(deadlineNs + 1, Long.MAX_VALUE)
        val total = hist.totalCount
        val dmr = misses.toDouble() / total.toDouble()

        return Stats(avgNs, median, p99, p999, wcet, dmr)
    }

    data class Stats(
        val avgNs: Long,
        val median: Long,
        val p99: Long,
        val p999: Long,
        val wcet: Long,
        val dmr: Double
    )
}