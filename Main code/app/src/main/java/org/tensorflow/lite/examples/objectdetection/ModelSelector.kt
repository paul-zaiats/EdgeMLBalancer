package org.tensorflow.lite.examples.objectdetection

import android.os.Process
import android.util.Log
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class ModelSelector(var objectDetectorHelper: ObjectDetectorHelper) {

    private var E0TimeLapsed: Long = 0
    private var E1TimeLapsed: Long = 0
    private var E2TimeLapsed: Long = 0
    private var MVTimeLapsed: Long = 0

    private var E0avg: Float = 0f
    private var E1avg: Float = 0f
    private var E2avg: Float = 0f
    private var MVavg: Float = 0f

    private var E0last: Float = 0f
    private var E1last: Float = 0f
    private var E2last: Float = 0f
    private var MVlast: Float = 0f

    private val modelConfidence =
        mutableMapOf<Int, MutableList<Float>>() // Confidence scores per model
    private val modelAverageConfidence =
        mutableMapOf<Int, Float>() // New map to store average confidence for each model


    fun getModelBasedOnCriteria(): Pair<String, Boolean> {
        val p = Math.random()
        val epsilon = 0.1
        var modelChanged = false
        if (p < epsilon) {
            val num = Random.nextInt(0, 4)
            if (num == 1) {
                E0TimeLapsed++
                E0last = getCpuUsage()
                E0avg = (E0avg * (E0TimeLapsed - 1) + E0last) / E0TimeLapsed
                if (objectDetectorHelper.currentModel != 1) {
                    objectDetectorHelper.currentModel = 1
                    Log.d("ModelUpdate", "Model updated to index: 1")
                    // Clear and reinitialize the detector
                    objectDetectorHelper.clearObjectDetector()
                    modelChanged = true
                }
                return Pair("EfficientDet Lite0", modelChanged)
            }
            if (num == 2) {
                E1TimeLapsed++
                E1last = getCpuUsage()
                E1avg = (E1avg * (E1TimeLapsed - 1) + E1last) / E1TimeLapsed
                if (objectDetectorHelper.currentModel != 2) {
                    objectDetectorHelper.currentModel = 2
                    Log.d("ModelUpdate", "Model updated to index: 2")
                    // Clear and reinitialize the detector
                    objectDetectorHelper.clearObjectDetector()
                    modelChanged = true
                }
                return Pair("EfficientDet Lite1", modelChanged)
            }
            if (num == 3) {
                E2TimeLapsed++
                E2last = getCpuUsage()
                E2avg = (E2avg * (E2TimeLapsed - 1) + E2last) / E2TimeLapsed
                if (objectDetectorHelper.currentModel != 3) {
                    objectDetectorHelper.currentModel = 3
                    Log.d("ModelUpdate", "Model updated to index: 3")
                    // Clear and reinitialize the detector
                    objectDetectorHelper.clearObjectDetector()
                    modelChanged = true
                }
                return Pair("EfficientDet Lite2", modelChanged)
            }

            MVTimeLapsed++
            MVlast = getCpuUsage()
            MVavg = (MVavg * (MVTimeLapsed - 1) + MVlast) / MVTimeLapsed
            if (objectDetectorHelper.currentModel != 0) {
                objectDetectorHelper.currentModel = 0
                Log.d("ModelUpdate", "Model updated to index: 0")
                // Clear and reinitialize the detector
                objectDetectorHelper.clearObjectDetector()
                modelChanged = true
            }
            return Pair("MobileNet V1", modelChanged)
        }

//        val score = Array(4) { 0.0f } // Array of size 4, initialized to 0.0f
//        val defaultConfidence = 1f // Default value if confidence is null
//
//        score[0] = min(MVavg.toFloat(), MVlast.toFloat()) *
//                (1 - ((modelAverageConfidence[0] ?: defaultConfidence) / (modelConfidence[0].toFloat() ?: defaultConfidence)))
//
//
//        score[1] = min(E0avg.toFloat(), E0last.toFloat()) *
//                (1 - ((modelAverageConfidence[1] ?: defaultConfidence) / (modelConfidence[1].toFloat() ?: defaultConfidence)))
//
//        score[2] = min(E1avg.toFloat(), E1last.toFloat()) *
//                (1 - ((modelAverageConfidence[2] ?: defaultConfidence) / (modelConfidence[2]?.toFloat() ?: defaultConfidence)))
//
//        score[3] = min(E2avg.toFloat(), E2last.toFloat()) *
//                (1 - ((modelAverageConfidence[3] ?: defaultConfidence) / (modelConfidence[3]?.toFloat() ?: defaultConfidence)))

        val score = Array(4) { 0.0f } // Array of size 4, initialized to 0.0f
        val defaultConfidence = 1f // Default value if confidence is null

        score[0] = min(MVavg.toFloat(), MVlast.toFloat()) * (1 - ((modelAverageConfidence[0]
            ?: defaultConfidence) / (modelConfidence[0]?.getOrNull(0) ?: defaultConfidence)))

        score[1] = min(E0avg.toFloat(), E0last.toFloat()) * (1 - ((modelAverageConfidence[1]
            ?: defaultConfidence) / (modelConfidence[1]?.getOrNull(0) ?: defaultConfidence)))

        score[2] = min(E1avg.toFloat(), E1last.toFloat()) * (1 - ((modelAverageConfidence[2]
            ?: defaultConfidence) / (modelConfidence[2]?.getOrNull(0) ?: defaultConfidence)))

        score[3] = min(E2avg.toFloat(), E2last.toFloat()) * (1 - ((modelAverageConfidence[3]
            ?: defaultConfidence) / (modelConfidence[3]?.getOrNull(0) ?: defaultConfidence)))

        val value = min(min(score[0], score[1]), min(score[2], score[3]))
        if (value == score[1]) {
            E0TimeLapsed++
            E0last = getCpuUsage()
            E0avg = (E0avg * (E0TimeLapsed - 1) + E0last) / E0TimeLapsed
            if (objectDetectorHelper.currentModel != 1) {
                objectDetectorHelper.currentModel = 1
                Log.d("ModelUpdate", "Model updated to index: 1")
                // Clear and reinitialize the detector
                objectDetectorHelper.clearObjectDetector()
                modelChanged = true
            }
            return Pair("EfficientDet Lite0", modelChanged)
        }
        if (value == score[2]) {
            E1TimeLapsed++
            E1last = getCpuUsage()
            E1avg = (E1avg * (E1TimeLapsed - 1) + E1last) / E1TimeLapsed
            if (objectDetectorHelper.currentModel != 2) {
                objectDetectorHelper.currentModel = 2
                Log.d("ModelUpdate", "Model updated to index: 2")
                // Clear and reinitialize the detector
                objectDetectorHelper.clearObjectDetector()
                modelChanged = true
            }
            return Pair("EfficientDet Lite1", modelChanged)
        }
        if (value == score[3]) {
            E2TimeLapsed++
            E2last = getCpuUsage()
            E2avg = (E2avg * (E2TimeLapsed - 1) + E2last) / E2TimeLapsed
            if (objectDetectorHelper.currentModel != 3) {
                objectDetectorHelper.currentModel = 3
                Log.d("ModelUpdate", "Model updated to index: 3")
                // Clear and reinitialize the detector
                objectDetectorHelper.clearObjectDetector()
                modelChanged = true
            }
            return Pair("EfficientDet Lite2", modelChanged)
        }

        MVTimeLapsed++
        MVlast = getCpuUsage()
        MVavg = (MVavg * (MVTimeLapsed - 1) + MVlast) / MVTimeLapsed
        if (objectDetectorHelper.currentModel != 0) {
            objectDetectorHelper.currentModel = 0
            Log.d("ModelUpdate", "Model updated to index: 0")
            // Clear and reinitialize the detector
            objectDetectorHelper.clearObjectDetector()
            modelChanged = true
        }
        return Pair("MobileNet V1", modelChanged)
    }

    private fun getCpuUsage(): Float {
        val pid = Process.myPid()
        val path = "/proc/$pid/stat"
        try {
            val statContent = File(path).readText()
            val parts = statContent.split(" ")
            val utime = parts[13].toLong()
            val stime = parts[14].toLong()
            val totalTime = utime + stime

            Thread.sleep(100) // Wait for 100ms

            val newStatContent = File(path).readText()
            val newParts = newStatContent.split(" ")
            val newUtime = newParts[13].toLong()
            val newStime = newParts[14].toLong()
            val newTotalTime = newUtime + newStime

            val cpuUsage = (newTotalTime - totalTime) / 1f
            return max(0f, min(cpuUsage, 100f))
        } catch (e: Exception) {
            e.printStackTrace()
            return 0f
        }
    }
}