package org.tensorflow.lite.examples.objectdetection

import android.util.Log

data class ModelStats(
    var emaLatencyMs: Double = 0.0,
    var emaConf: Double = 0.0,
    var emaDMR: Double = 0.0,       // deadline miss ratio in [0,1]
    var seen: Int = 0
)

class ModelSelector(private val odh: ObjectDetectorHelper) {

    private val N_MODELS = 4 // 0: MV1, 1: E0, 2: E1, 3: E2
    private val stats = Array(N_MODELS) { ModelStats() }

    // RT params
    var deadlineMs = 35
    private val slack = 0.85                  // require 15% headroom
    private val alphaLatency = 0.2            // EMA smoothing
    private val alphaConf = 0.1
    private val alphaDMR = 0.2

    // switching guards
    private var lastSwitchAtMs = 0L
    private val switchCooldownMs = 1500L

    // exploration
    private var epsilon = 0.12
    private val minEpsilon = 0.02
    private val epsilonDecay = 0.995

    val timeStatsCollector = TimeStatsCollector(odh.context, deadlineMs * 1_000_000L)

    fun updateMetrics(modelId: Int, latencyNs: Long, avgConfThisFrame: Double) {
        val latencyMs = latencyNs / 1_000_000
        val s = stats[modelId]
        s.emaLatencyMs = if (s.seen == 0) latencyMs.toDouble() else (1 - alphaLatency) * s.emaLatencyMs + alphaLatency * latencyMs
        s.emaConf     = if (s.seen == 0) avgConfThisFrame else (1 - alphaConf) * s.emaConf + alphaConf * avgConfThisFrame
        val miss = if (latencyMs > deadlineMs) 1.0 else 0.0
        s.emaDMR      = if (s.seen == 0) miss else (1 - alphaDMR) * s.emaDMR + alphaDMR * miss
        s.seen++

        timeStatsCollector.record(modelId, latencyNs)
    }

    fun chooseNextModel(): Pair<String, Boolean> {
        val nowMs = System.currentTimeMillis()

        // Respect cooldown unless the current model violates the deadline badly
        val cur = odh.currentModel.coerceIn(0, N_MODELS - 1)
        val curOk = stats[cur].emaLatencyMs > 0 &&
                stats[cur].emaLatencyMs <= slack * deadlineMs &&
                stats[cur].emaDMR <= 0.05

        if ((nowMs - lastSwitchAtMs) < switchCooldownMs && curOk) {
            return nameOf(cur) to false
        }

        // ε-greedy exploration only if we have healthy slack
        val healthy = stats.any { it.emaLatencyMs > 0 && it.emaLatencyMs <= slack * deadlineMs }
        if (healthy && Math.random() < epsilon) {
            epsilon = maxOf(minEpsilon, epsilon * epsilonDecay)
            val pick = (0 until N_MODELS).random()
            return switchTo(pick, nowMs)
        }

        // Pick the cheapest model that meets deadline+slack; tie-break by higher confidence
        val candidates = (0 until N_MODELS).filter {
            val s = stats[it]
            s.emaLatencyMs > 0 && s.emaLatencyMs <= slack * deadlineMs
        }.sortedWith(
            compareBy<Int> { stats[it].emaLatencyMs }
                .thenByDescending { stats[it].emaConf }
        )

        val next = when {
            candidates.isNotEmpty() -> candidates.last()
            else -> fastestSeenModel() ?: cur // fallback
        }

        if (next == cur) {
            return nameOf(cur) to false
        }
        return switchTo(next, nowMs)
    }

    private fun fastestSeenModel(): Int? =
        (0 until N_MODELS)
            .filter { stats[it].emaLatencyMs > 0 }
            .minByOrNull { stats[it].emaLatencyMs }

    private fun switchTo(modelId: Int, nowMs: Long): Pair<String, Boolean> {
        if (odh.currentModel != modelId) {
            odh.currentModel = modelId
            odh.clearObjectDetector() // consider doing this off the main thread
            lastSwitchAtMs = nowMs
            Log.d("ModelUpdate", "Switch -> $modelId (${nameOf(modelId)})")
            return nameOf(modelId) to true
        }
        return nameOf(modelId) to false
    }

    private fun nameOf(id: Int): String = when (id) {
        1 -> "EfficientDet Lite0"; 2 -> "EfficientDet Lite1"; 3 -> "EfficientDet Lite2"; else -> "MobileNet V1"
    }
}
