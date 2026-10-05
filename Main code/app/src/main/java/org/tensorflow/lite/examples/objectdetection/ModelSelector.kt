package org.tensorflow.lite.examples.objectdetection

import java.util.ArrayDeque
import kotlin.random.Random

data class ModelStats(
    var emaLatencyMs: Double = 0.0,
    var emaConf: Double = 0.0,
    var seen: Int = 0,
    var lastMeasuredFrame: Long = -1,
    val recentMisses: ArrayDeque<Boolean> = ArrayDeque<Boolean>()
) {
    val windowDMR: Double
        get() = if (recentMisses.isEmpty()) {
            0.0
        } else {
            recentMisses.count { it }.toDouble() / recentMisses.size
        }
}

enum class SelectionPhase {
    INITIALIZATION,
    PROBE,
    NORMAL
}

data class SelectionDecision(
    val modelId: Int,
    val switched: Boolean,
    val phase: SelectionPhase,
    val reason: String,
    val emaLatencyMsBefore: Double? = null,
    val windowDMRBefore: Double? = null,
    val observationsBefore: Int = 0,
    val windowSamplesBefore: Int = 0
)

class ModelSelector(
    private val odh: ObjectDetectorHelper,
    val deadlineMs: Int = 50,
    private val cooldownFrames: Int = 10,
    private val recheckIntervalFrames: Int = 20,
    private val initialSamplesPerModel: Int = 3,
    // Порядок качества: от менее точной к более точной.
    // TODO: уточнить его по mAP на отдельной калибровочной выборке.
    private val qualityOrder: List<Int> = listOf(0, 1, 2, 3)
) {
    private val stats = Array(4) { ModelStats() }

    private val slack = 0.9
    private val alphaLatency = 0.2
    private val alphaConf = 0.1

    // Пять последних наблюдений каждой модели.
    // После пяти успешных измерений прежний пропуск выходит из окна.
    private val dmrWindowSize = 5
    private val maxWindowDMR = 0.05

    private var completedFrames = 0L
    private var lastSwitchFrame = -cooldownFrames.toLong()

    private data class RecoveryState(
        var intervalFrames: Long,
        var nextCheckFrame: Long = 0L
    )

    private val recovery = Array(stats.size) {
        RecoveryState(
            intervalFrames = recheckIntervalFrames.toLong()
        )
    }

    private val recoverySuccessFrames = 5
    private val maxRecoveryIntervalFrames =
        maxOf(160L, recheckIntervalFrames.toLong())

    private var activeProbeModel: Int? = null
    private var probeSuccessCount = 0
    private var returnAfterProbe = false
    private var initializationFinished = false

    // Каждая четвёртая серия проверяет наиболее давно измеренную
    // альтернативу, чтобы остальные модели тоже получали наблюдения.
    private var probeSeriesCount = 0


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

        // Начальные измерения всех моделей.
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

        if (!initializationFinished) {
            initializationFinished = true

            recovery.forEach {
                it.nextCheckFrame =
                    completedFrames + it.intervalFrames
            }
        }

        // Серия закончилась: повторяем обычный выбор.
        // cooldown здесь не мешает уйти с проверяемой модели.
        if (returnAfterProbe) {
            returnAfterProbe = false

            return decide(
                bestFeasibleOrFastest(),
                SelectionPhase.NORMAL,
                "after_recovery_series"
            )
        }

        // Продолжаем проверять ту же модель без переключений.
        activeProbeModel?.let { modelId ->
            return decide(
                modelId,
                SelectionPhase.PROBE,
                "recovery_series_continue"
            )
        }

        // При нарушении ограничений текущей моделью
        // сразу выбираем подходящую альтернативу.
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

        val target = chooseRecoveryTarget(current)

        if (target != null) {
            activeProbeModel = target
            probeSuccessCount = 0
            probeSeriesCount++

            return decide(
                target,
                SelectionPhase.PROBE,
                "recovery_series_start"
            )
        }

        return decide(
            bestFeasibleOrFastest(),
            SelectionPhase.NORMAL,
            "best_feasible_or_fastest"
        )
    }

    private fun chooseRecoveryTarget(current: Int): Int? {
        val due = qualityOrder.filter { modelId ->
            modelId != current &&
                    completedFrames >= recovery[modelId].nextCheckFrame
        }

        if (due.isEmpty()) {
            return null
        }

        // Периодическая проверка остальных моделей.
        if ((probeSeriesCount + 1) % 4 == 0) {
            return due.minByOrNull {
                stats[it].lastMeasuredFrame
            }
        }

        val currentRank = qualityOrder.indexOf(current)

        // Ближайшая более качественная модель,
        // для которой уже наступил срок проверки.
        val upgrade = due.firstOrNull {
            qualityOrder.indexOf(it) > currentRank
        }

        return upgrade ?: due.minByOrNull {
            stats[it].lastMeasuredFrame
        }
    }

    private fun finishRecoverySeries(
        modelId: Int,
        successful: Boolean
    ) {
        val state = recovery[modelId]

        state.intervalFrames = if (successful) {
            recheckIntervalFrames.toLong()
        } else {
            minOf(
                state.intervalFrames * 2,
                maxRecoveryIntervalFrames
            )
        }

        state.nextCheckFrame = completedFrames + state.intervalFrames

        activeProbeModel = null
        probeSuccessCount = 0
        returnAfterProbe = true
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

        activeProbeModel?.let {
            check(modelId == it) {
                "Expected probe model $it, received $modelId"
            }
        }

        val s = stats[modelId]
        val first = s.seen == 0
        val latencyMs = latencyNs / 1_000_000.0
        val deadlineMiss =
            latencyNs > deadlineMs * 1_000_000L

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

        s.recentMisses.addLast(deadlineMiss)

        while (s.recentMisses.size > dmrWindowSize) {
            s.recentMisses.removeFirst()
        }

        s.seen++
        completedFrames++
        s.lastMeasuredFrame = completedFrames

        timeStatsCollector.record(modelId, latencyNs)

        if (activeProbeModel == modelId) {
            val withinBudget =
                latencyMs <= deadlineMs * slack

            if (!withinBudget) {
                // Включая диапазон 45–50 мс:
                // дедлайн ещё не пропущен, но запаса недостаточно.
                finishRecoverySeries(
                    modelId = modelId,
                    successful = false
                )
            } else {
                probeSuccessCount++

                if (probeSuccessCount >= recoverySuccessFrames) {
                    // Пять успешных кадров очищают окно от старого
                    // пропуска. EMA задержки также должна подходить.
                    finishRecoverySeries(
                        modelId = modelId,
                        successful = isFeasible(modelId)
                    )
                }
            }

            return
        }

        // После пропуска обычного кадра даём модели время
        // до следующей серии восстановления.
        if (initializationFinished && deadlineMiss) {
            val state = recovery[modelId]
            state.nextCheckFrame = maxOf(
                state.nextCheckFrame,
                completedFrames + state.intervalFrames
            )
        }
    }

    private fun isFeasible(modelId: Int): Boolean {
        val s = stats[modelId]

        return s.seen >= initialSamplesPerModel &&
                s.emaLatencyMs <= deadlineMs * slack &&
                s.windowDMR <= maxWindowDMR
    }

    private fun decide(
        modelId: Int,
        phase: SelectionPhase,
        reason: String
    ): SelectionDecision {
        val s = stats[modelId]

        // Снимок статистики до инференса текущего кадра.
        val latencyBefore = s.emaLatencyMs.takeIf { s.seen > 0 }
        val dmrBefore = s.windowDMR.takeIf { s.seen > 0 }
        val observationsBefore = s.seen
        val windowSamplesBefore = s.recentMisses.size

        val (_, switched) = switchTo(modelId)

        return SelectionDecision(
            modelId = modelId,
            switched = switched,
            phase = phase,
            reason = reason,
            emaLatencyMsBefore = latencyBefore,
            windowDMRBefore = dmrBefore,
            observationsBefore = observationsBefore,
            windowSamplesBefore = windowSamplesBefore
        )
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