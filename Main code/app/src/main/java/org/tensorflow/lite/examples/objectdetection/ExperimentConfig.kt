package org.tensorflow.lite.examples.objectdetection

import kotlin.random.Random

data class ExperimentConfig(
    val repeats: Int = 3,
    val expectedImageCount: Int = 5000,
    val assetDirectory: String = "val2017",
    val deadlineMs: Int = 50,
    val inferenceThreads: Int = 2,
    val loadWorkers: Int = 8,
    val loadPeriodMs: Long = 100,
    val warmupFrames: Int = 10,
    val loadSettleMs: Long = 5000,
    val dynamicStageMs: Long = 60_000,
    val seed: Int = 20261011,
    val maxStartBatteryC: Double = 30.0,
    val cooldownTimeoutMs: Long = 1800_000,
    // Explicit maps from Task API category index to COCO category_id, for each model.
    // Missing entries fail export rather than silently producing incorrect AP.
    val categoryMappingVerified: Boolean = false,
    val categoryMaps: Map<Int, Map<Int, Int>> = emptyMap()
) {
    init {
        require(repeats > 0 && expectedImageCount > 0 && deadlineMs > 0)
        require(inferenceThreads > 0 && loadWorkers > 0 && loadPeriodMs in 1..1000)
        require(warmupFrames > 0 && loadSettleMs >= 0 && dynamicStageMs > 0)
        require(maxStartBatteryC.isFinite() && cooldownTimeoutMs > 0)
        require(!categoryMappingVerified || (0..3).all { categoryMaps[it]?.isNotEmpty() == true })
    }

    val deadlineNs get() = deadlineMs * 1_000_000L
    fun jobs(): List<ExperimentJob> = (1..repeats).flatMap { repeat ->
        listOf(1, 2, 999).flatMap { model ->
            listOf(
                ExperimentJob(repeat, model, "idle"),
                ExperimentJob(repeat, model, "cpu75"), ExperimentJob(repeat, model, "dynamic")
            )
        }.shuffled(Random(seed + repeat))
    }
}

data class ExperimentJob(val repeatId: Int, val modelId: Int, val scenario: String)
