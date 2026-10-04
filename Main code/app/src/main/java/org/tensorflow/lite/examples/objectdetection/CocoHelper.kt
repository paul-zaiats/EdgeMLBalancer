package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.random.Random

/**
 * Runs repeatable COCO validation experiments for the four bundled detectors.
 *
 * The button in HomeFragment already calls [scanImages]. It runs every
 * configured load scenario, static model baselines, and the hybrid selector.
 * Call this method from a background executor: image decoding and inference
 * are intentionally synchronous so that one run has a deterministic order.
 */
class CocoHelper(context: Context) {

    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)

    @Volatile
    private var allDetections = JSONArray()

    private val deadlineNs = 50_000_000L
    private val warmupImageCount = 10
    private val loadStabilizationMs = 5_000L

    private data class LoadScenario(
        val name: String,
        val workers: Int,
        val dutyCycle: Double
    )

    private val loadScenarios = listOf(
        LoadScenario("idle", workers = 0, dutyCycle = 0.0),
        LoadScenario("cpu_w8_d75", workers = 8, dutyCycle = 0.75)
    )

    /** Starts the complete campaign away from the main/UI thread. */
    fun scanImages() {
        Thread({
            runCatching { runLoadExperiment() }
                .onFailure { Log.e(TAG, "COCO experiment failed", it) }
        }, "coco-experiment").apply {
            isDaemon = true
            start()
        }
    }

    fun runLoadExperiment() {
        check(running.compareAndSet(false, true)) { "A COCO experiment is already running" }
        val cpuSampler = CpuUsageSampler()

        try {
            val runId = UUID.randomUUID().toString()
            val images = listCocoImages()
            require(images.isNotEmpty()) { "No JPEG images found in $COCO_ASSET_DIR" }

            val outputDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val sampleFile = File(outputDir, "coco_samples_$runId.csv")
            sampleFile.bufferedWriter().use { writer ->
                writer.appendLine(
                    "runId,scenario,mode,imageId,modelId,switched,workers,dutyCycle," +
                            "cpuUsagePercent,inferenceNs,detectCallDurationNs,deadlineNs,deadlineMiss,confidence"
                )

                cpuSampler.start()
                for (scenario in loadScenarios) {
                    runScenario(
                        runId = runId,
                        scenario = scenario,
                        images = images,
                        writer = writer,
                        cpuSampler = cpuSampler
                    )
                    writer.flush()
                }
            }
            Log.i(TAG, "COCO experiment $runId completed; samples: ${sampleFile.absolutePath}")
        } finally {
            cpuSampler.stop()
            running.set(false)
        }
    }

    private fun runScenario(
        runId: String,
        scenario: LoadScenario,
        images: List<String>,
        writer: BufferedWriter,
        cpuSampler: CpuUsageSampler
    ) {
        val generator = CpuLoadGenerator()
        var activeScenario = scenario
        try {
            if (scenario.workers > 0) {
                val workers = minOf(scenario.workers, Runtime.getRuntime().availableProcessors())
                    .coerceAtLeast(1)
                activeScenario = scenario.copy(workers = workers)
                generator.start(
                    workers = workers,
                    dutyCycle = scenario.dutyCycle,
                    periodMs = 100
                )
                Log.i(TAG, "Started ${scenario.name}: workers=$workers, duty=${scenario.dutyCycle}")
            }
            Thread.sleep(loadStabilizationMs)

            val phases = listOf<() -> Unit>(
                { runStaticModels(runId, activeScenario, images, writer, cpuSampler) },
                { runHybrid(runId, activeScenario, images, writer, cpuSampler) }
            )
            phases.shuffled(Random("$runId:${scenario.name}".hashCode())).forEach { it() }
        } finally {
            generator.stop()
        }
    }

    private fun runStaticModels(
        runId: String,
        scenario: LoadScenario,
        images: List<String>,
        writer: BufferedWriter,
        cpuSampler: CpuUsageSampler
    ) {
        val outputDir = appContext.getExternalFilesDir(null)
            ?: appContext.filesDir

        // Одинаковый порядок моделей для всех сценариев одного запуска.
        val modelOrder = MODEL_IDS.shuffled(
            Random("$runId:static".hashCode())
        )

        for (modelId in modelOrder) {
            allDetections = JSONArray()
            val helper = createDetector(modelId)

            try {
                warmUp(helper, images)

                // Исключаем предсказания прогрева, если listener их сохранил.
                allDetections = JSONArray()

                val stats = TimeStatsCollector(appContext, deadlineNs)
                val samplesFile = File(
                    outputDir,
                    "coco_frames_${runId}_${scenario.name}_static_m${modelId}.csv"
                )

                FrameCsvLogger(samplesFile).use { csv ->
                    for ((frameIndex, assetName) in images.withIndex()) {
                        val imageId = cocoImageIdFromFilename(assetName)
                        val bitmap = loadImage(assetName)

                        try {
                            val cpuPercent =
                                cpuSampler.latestPercent?.toDouble()

                            val decision = SelectionDecision(
                                modelId = modelId,
                                switched = false,
                                phase = SelectionPhase.NORMAL,
                                reason = "fixed_model"
                            )

                            // Декодирование изображения в замер не входит.
                            val startedNs = SystemClock.elapsedRealtimeNanos()
                            val (inferenceNs, confidence) =
                                helper.detect(bitmap, 0, imageId)
                            val finishedNs = SystemClock.elapsedRealtimeNanos()

                            val callDurationNs = finishedNs - startedNs

                            stats.record(modelId, inferenceNs)

                            csv.record(
                                runId = runId,
                                scenario = scenario.name,
                                frameIndex = frameIndex,
                                imageId = imageId,
                                mode = "static",
                                decision = decision,
                                targetDutyCycle = scenario.dutyCycle,
                                workerCount = scenario.workers,
                                frameStartElapsedNs = startedNs,
                                selectionDurationNs = 0L,
                                inferenceNs = inferenceNs,
                                detectCallDurationNs = callDurationNs,
                                responseDurationNs = callDurationNs,
                                deadlineNs = deadlineNs,
                                confidence = confidence,
                                processCpuPercentOneCore = cpuPercent
                            )

                            writeSample(
                                writer,
                                runId,
                                scenario,
                                "static",
                                imageId,
                                modelId,
                                false,
                                inferenceNs,
                                callDurationNs,
                                confidence,
                                cpuPercent
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }

                stats.logResults(
                    "time_log_${runId}_${scenario.name}_static_m${modelId}.csv"
                )
                writePredictions(
                    runId,
                    scenario.name,
                    "static",
                    modelId,
                    allDetections
                )
            } finally {
                helper.clearObjectDetector()
                allDetections = JSONArray()
            }
        }
    }

    private fun runHybrid(
        runId: String,
        scenario: LoadScenario,
        images: List<String>,
        writer: BufferedWriter,
        cpuSampler: CpuUsageSampler
    ) {
        val outputDir = appContext.getExternalFilesDir(null)
            ?: appContext.filesDir

        val helper = createDetector(modelId = 0)

        try {
            allDetections = JSONArray()

            val selector = ModelSelector(
                odh = helper,
                deadlineMs = DEADLINE_MS,
                seed = 42,
                cooldownFrames = 10,
                recheckIntervalFrames = 20,
                initialSamplesPerModel = 3
            )

            val samplesFile = File(
                outputDir,
                "coco_frames_${runId}_${scenario.name}_hybrid.csv"
            )

            val initializedModels = mutableSetOf<Int>()

            // Одно изображение используется для прогрева всех моделей.
            val warmupAsset = images.firstOrNull()

            FrameCsvLogger(samplesFile).use { csv ->
                for ((frameIndex, assetName) in images.withIndex()) {
                    val imageId = cocoImageIdFromFilename(assetName)
                    val bitmap = loadImage(assetName)

                    try {
                        val cpuPercent =
                            cpuSampler.latestPercent?.toDouble()

                        val startedNs = SystemClock.elapsedRealtimeNanos()
                        val decision = selector.chooseNextDecision()
                        val selectionEndNs = SystemClock.elapsedRealtimeNanos()

                        var warmupDurationNs = 0L

                        if (
                            decision.phase == SelectionPhase.INITIALIZATION &&
                            decision.modelId !in initializedModels
                        ) {
                            val warmupStartedNs = SystemClock.elapsedRealtimeNanos()

                            if (warmupAsset != null) {
                                val warmupBitmap = loadImage(warmupAsset)

                                // Защищаем предсказания уже измеренных кадров от записей прогрева.
                                val measuredDetections = allDetections
                                allDetections = JSONArray()

                                try {
                                    repeat(10) {
                                        // imageId отсутствует: это прогрев, не оценочный кадр.
                                        helper.detect(warmupBitmap, 0)
                                    }
                                } finally {
                                    allDetections = measuredDetections
                                    warmupBitmap.recycle()
                                }
                            }

                            initializedModels.add(decision.modelId)
                            warmupDurationNs =
                                SystemClock.elapsedRealtimeNanos() - warmupStartedNs
                        }

                        // Отдельная граница: detectCallDurationNs не включает прогрев.
                        val detectStartedNs = SystemClock.elapsedRealtimeNanos()

                        val (inferenceNs, confidence) =
                            helper.detect(bitmap, 0, imageId)

                        val finishedNs = SystemClock.elapsedRealtimeNanos()

                        val selectionDurationNs = selectionEndNs - startedNs
                        val callDurationNs = finishedNs - detectStartedNs

                        // Время ответа включает начальный прогрев.
                        // Поэтому его стоимость остаётся видна в CSV.
                        val responseDurationNs = finishedNs - startedNs

                        selector.updateMetrics(
                            modelId = decision.modelId,
                            latencyNs = inferenceNs,
                            avgConfThisFrame = confidence
                        )

                        csv.record(
                            runId = runId,
                            scenario = scenario.name,
                            frameIndex = frameIndex,
                            imageId = imageId,
                            mode = "hybrid",
                            decision = decision,
                            targetDutyCycle = scenario.dutyCycle,
                            workerCount = scenario.workers,
                            frameStartElapsedNs = startedNs,
                            selectionDurationNs = selectionDurationNs,
                            inferenceNs = inferenceNs,
                            detectCallDurationNs = callDurationNs,
                            responseDurationNs = responseDurationNs,
                            deadlineNs = deadlineNs,
                            confidence = confidence,
                            processCpuPercentOneCore = cpuPercent,
                            warmupDurationNs = warmupDurationNs
                        )

                        writeSample(
                            writer,
                            runId,
                            scenario,
                            "hybrid",
                            imageId,
                            decision.modelId,
                            decision.switched,
                            inferenceNs,
                            callDurationNs,
                            confidence,
                            cpuPercent
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            selector.timeStatsCollector.logResults(
                "time_log_${runId}_${scenario.name}_hybrid_m${MODEL_HYBRID}.csv"
            )
            writePredictions(
                runId,
                scenario.name,
                "hybrid",
                MODEL_HYBRID,
                allDetections
            )
        } finally {
            helper.clearObjectDetector()
            allDetections = JSONArray()
        }
    }

    private fun warmUp(helper: ObjectDetectorHelper, images: List<String>) {
        images.take(minOf(warmupImageCount, images.size)).forEach { assetName ->
            val bitmap = loadImage(assetName)
            try {
                // Null imageId prevents warm-up outputs from entering the COCO prediction file.
                helper.detect(bitmap, 0)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun createDetector(modelId: Int): ObjectDetectorHelper = ObjectDetectorHelper(
        context = appContext,
        threshold = SCORE_THRESHOLD,
        maxResults = MAX_RESULTS,
        currentModel = modelId,
        deadlineNs = deadlineNs,
        objectDetectorListener = object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                Log.e(TAG, "Detector error: $error")
            }

            override fun onResults(
                results: MutableList<Detection>?,
                inferenceTime: Long,
                imageHeight: Int,
                imageWidth: Int,
                imageId: Int?
            ) {
                // Warm-up calls have no image id and must not be included in predictions.
                if (imageId == null) return

                results.orEmpty().forEach { detection ->
                    val category = detection.categories.maxByOrNull { it.score } ?: return@forEach
                    val detectionJson = JSONObject().apply {
                        put("image_id", imageId)
                        // Keep the existing model-index mapping. Verify it against the labels
                        // metadata before using these files for final COCOeval results.
                        put("category_id", category.index + 1)
                        put("bbox", toCocoBbox(detection.boundingBox))
                        put("score", category.score)
                    }
                    allDetections.put(detectionJson)
                }
            }
        }
    )

    private fun writeSample(
        writer: BufferedWriter,
        runId: String,
        scenario: LoadScenario,
        mode: String,
        imageId: Int,
        modelId: Int,
        switched: Boolean,
        inferenceNs: Long,
        callDurationNs: Long,
        confidence: Double,
        cpuPercent: Double?
    ) {
        val miss = inferenceNs > deadlineNs
        writer.appendLine(
            listOf(
                runId,
                scenario.name,
                mode,
                imageId,
                modelId,
                switched,
                scenario.workers,
                scenario.dutyCycle,
                cpuPercent?.toString() ?: "",
                inferenceNs,
                callDurationNs,
                deadlineNs,
                miss,
                confidence
            ).joinToString(",")
        )
    }

    private fun writePredictions(
        runId: String,
        scenarioName: String,
        mode: String,
        modelId: Int,
        detections: JSONArray
    ) {
        val directory = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val file = File(directory, "coco_${runId}_${scenarioName}_${mode}_m$modelId.json")
        FileWriter(file, false).buffered().use { it.write(detections.toString()) }
        Log.i(TAG, "Wrote ${detections.length()} detections to ${file.absolutePath}")
    }

    private fun listCocoImages(): List<String> =
        appContext.assets.list(COCO_ASSET_DIR)
            ?.filter { it.endsWith(".jpg", ignoreCase = true) }
            ?.sorted()
            ?.map { "$COCO_ASSET_DIR/$it" }
            .orEmpty()

    fun cocoImageIdFromFilename(assetPath: String): Int {
        val filename = assetPath.substringAfterLast('/')
        return filename.substringBefore('.').trimStart('0').ifEmpty { "0" }.toInt()
    }

    fun toCocoBbox(box: RectF): JSONArray {
        val x = max(0f, box.left)
        val y = max(0f, box.top)
        val width = max(0f, box.width())
        val height = max(0f, box.height())
        return JSONArray().put(x).put(y).put(width).put(height)
    }

    private fun loadImage(assetPath: String): Bitmap =
        appContext.assets.open(assetPath).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "Could not decode image asset: $assetPath"
            }
        }

    /** Process CPU usage: 100% means one fully occupied core; may exceed 100%. */
    private class CpuUsageSampler {
        @Volatile
        var latestPercent: Float? = null
            private set

        private var previousCpuMs = 0L
        private var previousWallNs = 0L
        private var scheduler: ScheduledExecutorService? = null

        @Synchronized
        fun start() {
            check(scheduler == null) { "CPU sampler is already running" }
            previousCpuMs = android.os.Process.getElapsedCpuTime()
            previousWallNs = SystemClock.elapsedRealtimeNanos()
            latestPercent = null
            val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "coco-cpu-sampler").apply { isDaemon = true }
            }
            scheduler = executor
            executor.scheduleWithFixedDelay({ sample() }, 1, 1, TimeUnit.SECONDS)
        }

        @Synchronized
        fun stop() {
            scheduler?.shutdownNow()
            scheduler = null
        }

        private fun sample() {
            val cpuMs = android.os.Process.getElapsedCpuTime()
            val wallNs = SystemClock.elapsedRealtimeNanos()
            val cpuDeltaMs = cpuMs - previousCpuMs
            val wallDeltaNs = wallNs - previousWallNs
            previousCpuMs = cpuMs
            previousWallNs = wallNs
            if (wallDeltaNs <= 0L || cpuDeltaMs < 0L) return

            latestPercent =
                (cpuDeltaMs.toDouble() * 1_000_000.0 / wallDeltaNs * 100.0).toFloat()
        }
    }

    private companion object {
        const val TAG = "CocoHelper"
        const val COCO_ASSET_DIR = "val2017_100"
        const val SCORE_THRESHOLD = 0.05f
        const val MAX_RESULTS = 100
        const val DEADLINE_MS = 50
        const val MODEL_HYBRID = 999
        val MODEL_IDS = 0..3
    }
}
