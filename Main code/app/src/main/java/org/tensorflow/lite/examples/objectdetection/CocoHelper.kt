package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.task.vision.detector.Detection
import kotlin.math.max

class CocoHelper(
    val context: Context
) {
    var objectDetectorHelper = ObjectDetectorHelper(
        context = context,
        objectDetectorListener = object : ObjectDetectorHelper.DetectorListener {
            override fun onError(error: String) {
                Log.e("CocoHelper", "Could not process image, error: $error")
            }

            override fun onResults(
                results: MutableList<Detection>?,
                inferenceTime: Long,
                imageHeight: Int,
                imageWidth: Int
            ) {
//                        assertNotNull(results)
                for (result in results!!) {
//                            assertTrue(result.boundingBox.top <= imageHeight)
//                            assertTrue(result.boundingBox.bottom <= imageHeight)
//                            assertTrue(result.boundingBox.left <= imageWidth)
//                            assertTrue(result.boundingBox.right <= imageWidth)
                }
            }
        })

    fun cocoImageIdFromFilename(name: String): Int {
        // "000000397133.jpg" -> 397133
        return name.substringBefore('.').trimStart('0').ifEmpty { "0" }.toInt()
    }

    // Convert Task Library detection to COCO bbox [x,y,w,h] in pixels
    fun toCocoBbox(box: RectF): List<Float> {
        val x = max(0f, box.left)
        val y = max(0f, box.top)
        val w = max(0f, box.width())
        val h = max(0f, box.height())
        return listOf(x, y, w, h)
    }

    fun scanImages() {
        context.assets.list("val2017_500")?.forEach {
            objectDetectorHelper.detect(loadImage("val2017_500/$it"), 0)
        }
    }


    @Throws(Exception::class)
    private fun loadImage(fileName: String): Bitmap {
        return BitmapFactory.decodeStream(context.assets.open(fileName))
    }
}


data class CocoDet(val image_id: Int, val category_id: Int, val bbox: List<Float>, val score: Float)

