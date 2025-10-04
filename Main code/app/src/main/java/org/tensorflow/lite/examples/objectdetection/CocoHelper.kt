package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileWriter
import java.io.IOException
import kotlin.math.max

class CocoHelper(
    val context: Context
) {

    val allDetections = JSONArray()
    private var logFile: File? = null

    init {
        createLogFile()
    }

    val objectDetectorHelper = ObjectDetectorHelper(
        context = context, threshold = 0.05F, maxResults = 100, objectDetectorListener = object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                Log.e("CocoHelper", "Could not process image, error: $error")
            }

            override fun onResults(
                results: MutableList<Detection>?,
                inferenceTime: Long,
                imageHeight: Int,
                imageWidth: Int,
                imageId: Int?
            ) {
                for (result in results!!) {
                    val maxCategory = result.categories.maxByOrNull { it.score }
                    if (maxCategory != null) {
                        val cocoBbox = toCocoBbox(result.boundingBox)
                        val categoryId = maxCategory.index + 1
                        val detectionJson = JSONObject().apply {
                            put("image_id", imageId)
                            put("category_id", categoryId)
                            put("bbox", cocoBbox)
                            put("score", maxCategory.score)
                        }
                        allDetections.put(detectionJson)
                    }
                }
            }
        })

    fun cocoImageIdFromFilename(name: String): Int {
        // "000000397133.jpg" -> 397133
        return name.substringBefore('.').trimStart('0').ifEmpty { "0" }.toInt()
    }

    // Convert Task Library detection to COCO bbox [x,y,w,h] in pixels
    fun toCocoBbox(box: RectF): JSONArray {
        val x = max(0f, box.left)
        val y = max(0f, box.top)
        val w = max(0f, box.width())
        val h = max(0f, box.height())
        return JSONArray().put(x).put(y).put(w).put(h)
    }

    fun scanImages() {
        context.assets.list("val2017_500")?.forEach {
            val imageId = cocoImageIdFromFilename(it)
            objectDetectorHelper.detect(loadImage("val2017_500/$it"), 0, imageId)
        }
        writeToLogFile(allDetections.toString(4))
    }

    private fun createLogFile() {
        val fileName = "all_detections_${System.currentTimeMillis()}.json"
        val directory = context.getExternalFilesDir(null)

        logFile = if (directory != null) {
            File(directory, fileName)
        } else {
            File(context.filesDir, fileName)
        }
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

    @Throws(Exception::class)
    private fun loadImage(fileName: String): Bitmap {
        return BitmapFactory.decodeStream(context.assets.open(fileName))
    }
}