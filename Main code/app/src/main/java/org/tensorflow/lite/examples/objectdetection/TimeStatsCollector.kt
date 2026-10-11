package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import org.HdrHistogram.Histogram
import java.io.File
import java.util.IdentityHashMap

/** Histogram percentiles are approximate; misses/mean/max use original nanoseconds. */
class TimeStatsCollector(private val context: Context, private val deadlineNs: Long) {
    val lowestDiscernibleNs = 1_000L
    val highestTrackableNs = 60_000_000_000L
    val sigDigits = 3
    private fun histogram() = Histogram(lowestDiscernibleNs, highestTrackableNs, sigDigits)
    val histHybrid = histogram()
    val histAll = histogram()
    val histMV = histogram()
    val histE0 = histogram()
    val histE1 = histogram()
    val histE2 = histogram()
    val modelIndexMapping =
        mapOf(999 to histHybrid, 0 to histMV, 1 to histE0, 2 to histE1, 3 to histE2)

    private data class Exact(
        var count: Long = 0, var misses: Long = 0,
        var sum: Double = 0.0, var maximum: Long = 0
    )

    private val exact = IdentityHashMap<Histogram, Exact>().apply {
        (modelIndexMapping.values + histAll).forEach { put(it, Exact()) }
    }

    fun record(modelIndex: Int, latencyNano: Long) {
        require(latencyNano in 0..highestTrackableNs)
        val model = requireNotNull(modelIndexMapping[modelIndex])
        listOf(model, histAll).forEach { h ->
            h.recordValue(latencyNano)
            val e = exact.getValue(h)
            e.count++; if (latencyNano > deadlineNs) e.misses++
            e.sum += latencyNano; e.maximum = maxOf(e.maximum, latencyNano)
        }
    }

    fun reset() {
        exact.forEach { (h, _) -> h.reset(); exact[h] = Exact() }
    }

    fun getStats(hist: Histogram): Stats {
        val e = exact.getValue(hist)
        return Stats(
            if (e.count == 0L) 0 else (e.sum / e.count).toLong(),
            hist.getValueAtPercentile(50.0), hist.getValueAtPercentile(95.0),
            hist.getValueAtPercentile(99.0), hist.getValueAtPercentile(99.9),
            e.maximum, if (e.count == 0L) Double.NaN else e.misses.toDouble() / e.count
        )
    }

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { w ->
            w.appendLine("modelId,count,averageNs,p50Ns,p95Ns,p99Ns,p999Ns,maxObservedNs,misses,dmr,deadlineNs")
            (modelIndexMapping.map { it.key.toString() to it.value } + ("all" to histAll)).forEach { (id, h) ->
                val e = exact.getValue(h);
                val s = getStats(h)
                w.appendLine(
                    listOf(
                        id, e.count, s.avgNs, s.median, s.p95, s.p99, s.p999, s.wcet,
                        e.misses, if (e.count == 0L) "" else s.dmr, deadlineNs
                    ).joinToString(",")
                )
            }
        }
    }

    fun logResults(fileName: String) =
        writeTo(File(context.getExternalFilesDir(null) ?: context.filesDir, fileName))

    data class Stats(
        val avgNs: Long, val median: Long, val p95: Long, val p99: Long,
        val p999: Long, val wcet: Long, val dmr: Double
    )
}
