package org.tensorflow.lite.examples.objectdetection

import kotlin.random.Random

data class ModelStats(
    var emaLatencyMs: Double = 0.0,
    var emaConf: Double = 0.0,
    var emaDMR: Double = 0.0,  // deadline miss ratio in [0,1]
    var seen: Int = 0,
    var lastMeasuredFrame: Long = -1
)

enum class SelectionPhase {
    INITIALIZATION,
    PROBE,
    NORMAL
}

data class SelectionDecision(
    val modelId: Int,
    val switched: Boolean,
    val phase: SelectionPhase,
    val reason: String
)

class ModelSelector(
    private val odh: ObjectDetectorHelper,
    val deadlineMs: Int = 50,
    seed: Int = 42,
    private val cooldownFrames: Int = 10,
    private val recheckIntervalFrames: Int = 20,
    private val initialSamplesPerModel: Int = 3,
    // Порядок качества: от менее точной к более точной.
    // TODO: уточнить его по mAP на отдельной калибровочной выборке.
    private val qualityOrder: List<Int> = listOf(0, 1, 2, 3)
) {
    private val stats = Array(4) { ModelStats() }
    private val random = Random(seed)

    private val slack = 0.9
    private val alphaLatency = 0.2
    private val alphaConf = 0.1
    private val alphaDMR = 0.2

    private val epsilon = 0.02

    private var completedFrames = 0L
    private var lastSwitchFrame = -cooldownFrames.toLong()
    private var lastProbeFrame = 0L
    private var probePending = false

    val timeStatsCollector = TimeStatsCollector(
        odh.context,
        deadlineMs * 1_000_000L
    )

    init {
        require(deadlineMs > 0)
        require(cooldownFrames >= 0)
        require(recheckIntervalFrames > 0)
        require(initialSamplesPerModel > 0)
        require(
            qualityOrder.size == stats.size &&
                    qualityOrder.toSet() == stats.indices.toSet()
        )
    }

    /**
     * Вызывается один раз перед инференсом.
     * После него обязательно вызвать updateMetrics().
     */
    fun chooseNextDecision(): SelectionDecision {
        val current = odh.currentModel.coerceIn(stats.indices)

        val unmeasured = qualityOrder.firstOrNull {
            stats[it].seen < initialSamplesPerModel
        }

        if (unmeasured != null) {
            return decide(
                unmeasured,
                SelectionPhase.INITIALIZATION,
                "initial_samples"
            )
        }

        if (probePending) {
            probePending = false
            return decide(
                bestFeasibleOrFastest(),
                SelectionPhase.NORMAL,
                "after_probe"
            )
        }

        if (!isFeasible(current)) {
            val next = bestFeasibleOrFastest()
            if (next != current) {
                return decide(
                    next,
                    SelectionPhase.NORMAL,
                    "current_not_feasible"
                )
            }
        }

        val cooldownPassed =
            completedFrames - lastSwitchFrame >= cooldownFrames

        if (!cooldownPassed) {
            return decide(
                current,
                SelectionPhase.NORMAL,
                "cooldown"
            )
        }

        if (completedFrames - lastProbeFrame >= recheckIntervalFrames) {
            val target = stats.indices
                .filter { it != current }
                .minByOrNull { stats[it].lastMeasuredFrame }

            if (target != null) {
                return startProbeDecision(target, "periodic_recheck")
            }
        }

        if (random.nextDouble() < epsilon) {
            val alternatives = stats.indices.filter { it != current }
            val target = alternatives[random.nextInt(alternatives.size)]
            return startProbeDecision(target, "epsilon_exploration")
        }

        return decide(
            bestFeasibleOrFastest(),
            SelectionPhase.NORMAL,
            "best_feasible_or_fastest"
        )
    }

    private fun startProbeDecision(
        modelId: Int,
        reason: String
    ): SelectionDecision {
        lastProbeFrame = completedFrames
        probePending = true

        return decide(modelId, SelectionPhase.PROBE, reason)
    }

    private fun decide(
        modelId: Int,
        phase: SelectionPhase,
        reason: String
    ): SelectionDecision {
        val (_, switched) = switchTo(modelId)

        return SelectionDecision(
            modelId = modelId,
            switched = switched,
            phase = phase,
            reason = reason
        )
    }

    // Совместимость с существующими вызовами из CameraFragment.
// Для каждого кадра вызывайте только один из двух методов выбора.
    fun chooseNextModel(): Pair<String, Boolean> {
        val decision = chooseNextDecision()
        return nameOf(decision.modelId) to decision.switched
    }

    fun updateMetrics(
        modelId: Int,
        latencyNs: Long,
        avgConfThisFrame: Double
    ) {
        require(modelId in stats.indices)
        require(latencyNs >= 0)

        val latencyMs = latencyNs / 1_000_000.0
        val miss = if (latencyNs > deadlineMs * 1_000_000L) {
            1.0
        } else {
            0.0
        }

        val s = stats[modelId]
        val first = s.seen == 0

        s.emaLatencyMs = if (first) {
            latencyMs
        } else {
            (1 - alphaLatency) * s.emaLatencyMs +
                    alphaLatency * latencyMs
        }

        if (avgConfThisFrame.isFinite()) {
            s.emaConf = if (first) {
                avgConfThisFrame
            } else {
                (1 - alphaConf) * s.emaConf +
                        alphaConf * avgConfThisFrame
            }
        }

        s.emaDMR = if (first) {
            miss
        } else {
            (1 - alphaDMR) * s.emaDMR + alphaDMR * miss
        }

        s.seen++
        completedFrames++
        s.lastMeasuredFrame = completedFrames

        timeStatsCollector.record(modelId, latencyNs)
    }

    private fun isFeasible(modelId: Int): Boolean {
        val s = stats[modelId]
        return s.seen > 0 &&
                s.emaLatencyMs <= deadlineMs * slack &&
                s.emaDMR <= 0.05
    }

    private fun bestFeasibleOrFastest(): Int {
        // Выбираем по заданному порядку качества, а не по задержке.
        return qualityOrder.lastOrNull { isFeasible(it) }
            ?: stats.indices
                .filter { stats[it].seen > 0 }
                .minByOrNull { stats[it].emaLatencyMs }
            ?: 0
    }

    private fun switchTo(modelId: Int): Pair<String, Boolean> {
        if (odh.currentModel == modelId) {
            return nameOf(modelId) to false
        }

        odh.currentModel = modelId
        odh.clearObjectDetector()
        lastSwitchFrame = completedFrames

        return nameOf(modelId) to true
    }

    private fun nameOf(modelId: Int): String = when (modelId) {
        0 -> "MobileNet V1"
        1 -> "EfficientDet Lite0"
        2 -> "EfficientDet Lite1"
        3 -> "EfficientDet Lite2"
        else -> error("Unknown model: $modelId")
    }
}