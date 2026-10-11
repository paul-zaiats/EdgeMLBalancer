package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class CocoHelper(
    context: Context,
    private val config: ExperimentConfig = ExperimentConfig(),
    private val onStateChanged: ((ExperimentState) -> Unit)? = null
) {
    private val app = context.applicationContext
    @Volatile
    private var worker: Thread? = null
    fun scanImages() {
        if (!busy.compareAndSet(false, true)) return
        worker = Thread({
            try {
                executeWithStatus()
            } catch (e: Exception) {
                Log.e("CocoExperiment", "Campaign failed", e)
            } finally {
                busy.set(false); worker = null
            }
        }, "coco-experiment").also { it.start() }
    }

    fun cancel() {
        worker?.interrupt()
    }

    fun runLoadExperiment() {
        check(busy.compareAndSet(false, true)) { "Another campaign is running" }
        worker = Thread.currentThread()
        try {
            executeWithStatus()
        } finally {
            worker = null; busy.set(false)
        }
    }

    private fun emitState(state: ExperimentState) {
        try {
            onStateChanged?.invoke(state)
        } catch (e: Exception) {
            Log.w("CocoExperiment", "Status callback failed", e)
        }
    }

    private fun executeWithStatus() {
        try {
            execute()
            emitState(ExperimentState.FINISHED)
        } catch (e: Exception) {
            emitState(if (e is InterruptedException) ExperimentState.CANCELLED else ExperimentState.FAILED)
            throw e
        }
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Campaign cancelled")
    }

    private fun execute() {
        val images = app.assets.list(config.assetDirectory).orEmpty()
            .filter { it.endsWith(".jpg", true) }.sorted().map { "${config.assetDirectory}/$it" }
        require(images.size == config.expectedImageCount) {
            "Expected ${config.expectedImageCount} images, found ${images.size}"
        }
        val ids = images.map(::cocoImageIdFromFilename)
        require(ids.toSet().size == ids.size) { "Duplicate image IDs" }
        val campaign = UUID.randomUUID().toString()
        val root = File(app.getExternalFilesDir(null) ?: app.filesDir, "experiments/$campaign")
        check(root.mkdirs())
        val manifest = JSONObject().put("campaignId", campaign).put("status", "running")
            .put("seed", config.seed).put("repeats", config.repeats)
            .put("deadlineNs", config.deadlineNs).put("numThreads", config.inferenceThreads)
            .put("delegate", "CPU").put("scoreThreshold", 0.05).put("maxResults", 100)
            .put("imageCount", images.size).put("modelQualityOrder", JSONArray(listOf(0, 1, 2, 3)))
            .put("warmupFramesPerModel", config.warmupFrames)
            .put("loadSettleMs", config.loadSettleMs)
            .put("dynamicStageMs", config.dynamicStageMs).put("loadPeriodMs", config.loadPeriodMs)
            .put("requestedWorkers", config.loadWorkers)
            .put("availableProcessors", Runtime.getRuntime().availableProcessors())
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("sdk", Build.VERSION.SDK_INT)
            .put("fingerprint", Build.FINGERPRINT).put("maxStartBatteryC", config.maxStartBatteryC)
            .put("protocolVersion", "1.0")
            .put("categoryMappingVerified", config.categoryMappingVerified)
            .put(
                "categoryMapping",
                if (config.categoryMaps.isEmpty()) "unverified_index_plus_one" else "explicit"
            )
        val modelFiles = listOf(
            "mobilenetv1.tflite", "efficientdet-lite0.tflite",
            "efficientdet-lite1.tflite", "efficientdet-lite2.tflite"
        )
        val hashes = JSONObject()
        modelFiles.forEach { name ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            app.assets.open(name).use { stream ->
                val buffer = ByteArray(65536)
                while (true) {
                    val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n)
                }
            }
            hashes.put(name, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        }
        manifest.put("modelSha256", hashes)
        val jobs = config.jobs()
        manifest.put("jobOrder", JSONArray(jobs.map {
            JSONObject().put("repeat", it.repeatId)
                .put("modelId", it.modelId).put("scenario", it.scenario)
        }))
        val completed = JSONArray(); manifest.put("runs", completed)
        fun saveManifest() {
            File(root, "manifest.json").writeText(manifest.toString(2))
        }
        saveManifest()
        try {
            ThermalSampler(app).use { thermal ->
                for (job in jobs) {
                    checkCancellation(); coolDown(thermal)
                    emitState(ExperimentState.RUN)
                    val id = "r${job.repeatId}_${job.scenario}_m${job.modelId}_${UUID.randomUUID()}"
                    val dir = File(root, id); check(dir.mkdirs())
                    val run = JSONObject().put("runId", id).put("status", "running")
                        .put("repeat", job.repeatId).put("scenario", job.scenario)
                        .put("modelId", job.modelId)
                    completed.put(run); saveManifest()
                    try {
                        executeRun(job, id, dir, images, thermal, run); run.put(
                            "status",
                            "completed"
                        )
                    } catch (e: Exception) {
                        run.put("status", if (e is InterruptedException) "cancelled" else "failed")
                            .put("error", e.toString()); throw e
                    } finally {
                        File(dir, "run.json").writeText(run.toString(2)); saveManifest()
                    }
                }
            }
            manifest.put("status", "completed")
        } catch (e: Exception) {
            manifest.put("status", if (e is InterruptedException) "cancelled" else "failed")
                .put("error", e.toString()); throw e
        } finally {
            saveManifest(); Log.i("CocoExperiment", root.absolutePath)
        }
    }

    private fun coolDown(thermal: ThermalSampler) {
        emitState(ExperimentState.COOLDOWN)
        val start = SystemClock.elapsedRealtime();
        var stableSince: Long? = null
        var lastProgressMs = 0L

        while (SystemClock.elapsedRealtime() - start < config.cooldownTimeoutMs) {
            checkCancellation()
            val now = SystemClock.elapsedRealtimeNanos();
            val s = thermal.latest
            val valid = s != null && now - s.sampledAtElapsedNs <= 2_500_000_000L &&
                    s.batteryTemperatureC != null && s.batteryTemperatureC <= config.maxStartBatteryC &&
                    s.thermalStatus == 0 //&& s.plugged == false
            val nowMs = SystemClock.elapsedRealtime()

            if (nowMs - lastProgressMs >= 5_000L) {
                lastProgressMs = nowMs

                val stableSeconds = stableSince
                    ?.let { (now - it) / 1_000_000_000L }
                    ?: 0L

                Log.i(
                    "CocoExperiment",
                    "COOLDOWN: waited=${(nowMs - start) / 1000}s, " +
                            "battery=${s?.batteryTemperatureC}°C " +
                            "(target<=${config.maxStartBatteryC}°C), " +
                            "thermal=${s?.thermalStatus}, plugged=${s?.plugged}, " +
                            "conditionsMet=$valid, stable=${stableSeconds}/5s"
                )
            }

            if (valid) {
                if (stableSince == null) stableSince = now
                if (now - requireNotNull(stableSince) >= 5_000_000_000L) {
                    Log.i("CocoExperiment", "COOLDOWN complete: starting preparation")
                    return
                }
            } else stableSince = null
            Thread.sleep(500)
        }
        error("Cooling timeout: require fresh battery temperature <= ${config.maxStartBatteryC} C, thermal NONE, unplugged")
    }

    private fun executeRun(
        job: ExperimentJob, id: String, dir: File, images: List<String>,
        thermal: ThermalSampler, metadata: JSONObject
    ) {
        val inference = TimeStatsCollector(app, config.deadlineNs)
        val response = TimeStatsCollector(app, config.deadlineNs)
        val creationStart = SystemClock.elapsedRealtimeNanos()
        val helper = ObjectDetectorHelper(
            threshold = 0.05f, numThreads = config.inferenceThreads,
            maxResults = 100, currentModel = if (job.modelId == 999) 0 else job.modelId,
            context = app, objectDetectorListener = null, deadlineNs = config.deadlineNs
        )
        metadata.put("coldModelCreationNs", SystemClock.elapsedRealtimeNanos() - creationStart)
        val selector =
            if (job.modelId == 999) ModelSelector(helper, deadlineMs = config.deadlineMs) else null
        try {
            ExperimentLoadController(config, File(dir, "load_events.csv")).use { load ->
                load.prepare(job.scenario); Thread.sleep(config.loadSettleMs)
                ExperimentCpuSampler().use { cpu ->
                    val prepared = SystemClock.elapsedRealtimeNanos()
                    ExperimentCsv(File(dir, "startup.csv")).use { csv ->
                        val warmed = mutableSetOf<Int>()
                        if (selector == null) {
                            repeat(config.warmupFrames) { n ->
                                measure(
                                    images[n % images.size], n, 0, job, id, helper,
                                    SelectionDecision(
                                        helper.currentModel,
                                        false,
                                        SelectionPhase.INITIALIZATION,
                                        "warmup"
                                    ),
                                    0, prepared, "warmup", load, cpu, thermal, csv, null, null, null
                                )
                            }
                        } else repeat(12) { n ->
                            val choose = SystemClock.elapsedRealtimeNanos()
                            val decision = selector.chooseNextDecision()
                            val selectionNs = SystemClock.elapsedRealtimeNanos() - choose
                            if (warmed.add(decision.modelId)) repeat(config.warmupFrames) { w ->
                                measure(
                                    images[w % images.size], w, 0, job, id, helper, decision, 0,
                                    prepared, "warmup", load, cpu, thermal, csv, null, null, null
                                )
                            }
                            val result = measure(
                                images[n % images.size],
                                n,
                                0,
                                job,
                                id,
                                helper,
                                decision,
                                selectionNs,
                                prepared,
                                "calibration",
                                load,
                                cpu,
                                thermal,
                                csv,
                                null,
                                null,
                                null
                            )
                            selector.updateMetrics(
                                decision.modelId,
                                result.inferenceNs,
                                result.confidence
                            )
                        }
                    }
                    selector?.timeStatsCollector?.reset()
                    metadata.put("startupDurationNs", SystemClock.elapsedRealtimeNanos() - prepared)
                    metadata.put("startBatteryTemperatureC", thermal.latest?.batteryTemperatureC)
                    metadata.put("startThermalStatus", thermal.latest?.thermalStatus)
                    val start = SystemClock.elapsedRealtimeNanos()
                    if (job.scenario == "dynamic") load.startDynamic(start)
                    val legacyId = id.substringAfterLast('_')
                    val mode = if (job.modelId == 999) "hybrid" else "static"
                    val cocoName = "coco_${legacyId}_${job.scenario}_${mode}_m${job.modelId}.json"
                    val timelyName =
                        "coco_${legacyId}_${job.scenario}_${mode}_m${job.modelId}_timely.json"
                    metadata.put("cocoFile", cocoName).put("timelyCocoFile", timelyName)
                    Predictions(
                        File(dir, cocoName),
                        File(dir, timelyName),
                        config
                    ).use { predictions ->
                        ExperimentCsv(File(dir, "frames.csv")).use { csv ->
                            var index = 0
                            while (if (job.scenario == "dynamic") SystemClock.elapsedRealtimeNanos() - start <
                                        config.dynamicStageMs * 4 * 1_000_000L else index < images.size
                            ) {
                                checkCancellation(); load.failure?.let {
                                    throw IllegalStateException(
                                        "Load scheduler failed",
                                        it
                                    )
                                }
                                measure(
                                    images[index % images.size],
                                    index,
                                    index / images.size,
                                    job,
                                    id,
                                    helper,
                                    SelectionDecision(
                                        helper.currentModel,
                                        false,
                                        SelectionPhase.NORMAL,
                                        "static"
                                    ),
                                    0L,
                                    start,
                                    "measurement",
                                    load,
                                    cpu,
                                    thermal,
                                    csv,
                                    inference,
                                    response,
                                    predictions,
                                    selector
                                )
                                index++
                            }
                            load.failure?.let {
                                throw IllegalStateException(
                                    "Load scheduler failed",
                                    it
                                )
                            }
                            check(job.scenario != "dynamic" || load.state.step == 3) {
                                "Dynamic load did not reach its final stage"
                            }
                            metadata.put("frames", index).put(
                                "measurementDurationNs",
                                SystemClock.elapsedRealtimeNanos() - start
                            )
                        }
                        metadata.put("unverifiedCategoryCount", predictions.unverified)
                        metadata.put("cocoEvaluationPolicy", "first_dataset_pass")
                        metadata.put("cocoEvaluationImages", predictions.evaluatedImageIds.size)
                        File(dir, "evaluated_image_ids.json").writeText(
                            JSONArray(predictions.evaluatedImageIds).toString()
                        )
                    }
                    inference.writeTo(File(dir, "inference_stats.csv")); response.writeTo(
                    File(
                        dir,
                        "response_stats.csv"
                    )
                )
                }
            }
        } finally {
            helper.clearObjectDetector()
        }
    }

    private fun measure(
        asset: String, index: Int, pass: Int, job: ExperimentJob, id: String,
        helper: ObjectDetectorHelper, decision: SelectionDecision, selectionNs: Long,
        runStart: Long, phase: String, load: ExperimentLoadController, cpu: ExperimentCpuSampler,
        thermal: ThermalSampler, csv: ExperimentCsv, inference: TimeStatsCollector?,
        response: TimeStatsCollector?, predictions: Predictions?, selector: ModelSelector? = null
    ): ObjectDetectorHelper.BenchmarkResult {
        checkCancellation()
        val decodeStart = SystemClock.elapsedRealtimeNanos()
        val bitmap = app.assets.open(asset).use { requireNotNull(BitmapFactory.decodeStream(it)) }
        val decodeNs = SystemClock.elapsedRealtimeNanos() - decodeStart
        try {
            val start = SystemClock.elapsedRealtimeNanos();
            val before = load.state
            val c = cpu.latest;
            val t = thermal.latest
            val activeDecision = selector?.chooseNextDecision() ?: decision
            val activeSelectionNs =
                if (selector == null) selectionNs else SystemClock.elapsedRealtimeNanos() - start
            val result = helper.detectForBenchmark(bitmap)
            val end = SystemClock.elapsedRealtimeNanos();
            val after = load.state
            val responseNs = if (selector == null) selectionNs + end - start else end - start
            inference?.record(activeDecision.modelId, result.inferenceNs)
            response?.record(activeDecision.modelId, responseNs)
            csv.row(
                id,
                job.repeatId,
                job.scenario,
                phase,
                index,
                cocoImageIdFromFilename(asset),
                pass,
                activeDecision.modelId,
                activeDecision.phase.name,
                activeDecision.reason,
                activeDecision.switched,
                activeDecision.emaLatencyMsBefore,
                activeDecision.windowDMRBefore,
                activeDecision.observationsBefore,
                activeDecision.windowSamplesBefore,
                start - runStart,
                activeSelectionNs,
                result.initializationNs,
                result.preprocessingNs,
                result.detectorCallNs,
                result.inferenceNs,
                responseNs,
                decodeNs,
                config.deadlineNs,
                result.inferenceNs > config.deadlineNs,
                responseNs > config.deadlineNs,
                result.confidence,
                before.step,
                after.step,
                before.duty,
                before.workers,
                before.step != after.step,
                c?.percent,
                c?.let { (start - it.atNs) / 1_000_000.0 },
                t?.batteryTemperatureC,
                t?.thermalStatus,
                t?.plugged,
                t?.let { (start - it.sampledAtElapsedNs) / 1_000_000.0 })
            predictions?.write(
                cocoImageIdFromFilename(asset),
                index,
                pass,
                activeDecision.modelId,
                result,
                responseNs <= config.deadlineNs
            )
            selector?.updateMetrics(activeDecision.modelId, result.inferenceNs, result.confidence)
            return result
        } finally {
            bitmap.recycle()
        }
    }

    fun cocoImageIdFromFilename(path: String): Int =
        path.substringAfterLast('/').substringBefore('.').toInt()

    fun toCocoBbox(box: RectF): JSONArray = JSONArray().put(box.left.coerceAtLeast(0f))
        .put(box.top.coerceAtLeast(0f)).put(box.width().coerceAtLeast(0f))
        .put(box.height().coerceAtLeast(0f))

    private companion object {
        val busy = AtomicBoolean(false)
    }
}

private class Predictions(
    all: File, timely: File,
    private val config: ExperimentConfig
) : Closeable {
    private val a = all.bufferedWriter();
    private val t = timely.bufferedWriter()
    private var firstA = true;
    private var firstT = true
    val evaluatedImageIds = mutableListOf<Int>()
    private val exportedImageIds = mutableSetOf<Int>()
    var unverified = 0L; private set

    init {
        a.write("["); t.write("[")
    }

    fun write(
        id: Int,
        index: Int,
        pass: Int,
        model: Int,
        r: ObjectDetectorHelper.BenchmarkResult,
        onTime: Boolean
    ) {
        // Export one observation per image, including empty/late frames in the GT ID list.
        // Later dataset passes are timed in CSV but not exported as duplicate COCO detections.
        val exportCoco = pass == 0
        if (exportCoco) {
            check(exportedImageIds.add(id)) { "Duplicate image in first dataset pass: $id" }
            evaluatedImageIds.add(id)
        }
        if (!exportCoco) return
        r.results.forEach { d ->
            val c = d.categories.maxByOrNull { it.score } ?: return@forEach
            val map = config.categoryMaps[model]
            val category = if (map == null) {
                unverified++; c.index + 1
            } else
                requireNotNull(map[c.index]) { "Missing category mapping: model=$model index=${c.index}" }
            val b = d.boundingBox
            val left = b.left.coerceIn(0f, r.imageWidth.toFloat());
            val top = b.top.coerceIn(0f, r.imageHeight.toFloat())
            val right = b.right.coerceIn(left, r.imageWidth.toFloat());
            val bottom = b.bottom.coerceIn(top, r.imageHeight.toFloat())
            if (right <= left || bottom <= top) return@forEach
            val item =
                JSONObject().put("image_id", id).put("category_id", category).put("score", c.score)
                    .put("bbox", JSONArray().put(left).put(top).put(right - left).put(bottom - top))
            if (exportCoco) {
                if (!firstA) a.write(","); firstA = false; a.write(item.toString())
                if (onTime) {
                    if (!firstT) t.write(","); firstT = false; t.write(item.toString())
                }
            }
        }
    }

    override fun close() {
        a.write("]"); t.write("]"); a.close(); t.close()
    }
}
