package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.io.Closeable

enum class ExperimentState { READY, RUN, COOLDOWN, FINISHED, FAILED, CANCELLED }

/** Update only on phase transitions; no UI work per inference frame. */
class ExperimentStatusLamp(private val context: Context) : Closeable {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var closed = false
    private var state = ExperimentState.READY
    private val circle = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.GRAY)
    }
    private val lamp = View(context).apply { background = circle }
    private val label = TextView(context).apply {
        text = "Ready"
        setTextColor(Color.WHITE)
        textSize = 14f
    }
    private val tone = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (e: RuntimeException) {
        Log.w("CocoExperiment", "Cannot create status tone", e)
        null
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.argb(220, 35, 35, 35))
        }
        addView(lamp, LinearLayout.LayoutParams(dp(20), dp(20)))
        addView(
            label, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) })
        contentDescription = "Experiment: ready"
    }

    // Safe to call from the experiment's background thread.
    fun show(next: ExperimentState) {
        if (closed) return
        handler.post {
            if (closed || next == state) return@post
            val old = state
            state = next
            circle.setColor(
                when (next) {
                    ExperimentState.RUN -> Color.rgb(235, 65, 65)
                    ExperimentState.COOLDOWN -> Color.rgb(55, 130, 255)
                    ExperimentState.FAILED -> Color.rgb(255, 170, 30)
                    else -> Color.GRAY
                }
            )
            label.text = when (next) {
                ExperimentState.READY -> "ready"
                ExperimentState.RUN -> "RUN"
                ExperimentState.COOLDOWN -> "COOLDOWN"
                ExperimentState.FINISHED -> "Finished"
                ExperimentState.FAILED -> "Error"
                ExperimentState.CANCELLED -> "Stopped"
            }
            view.contentDescription = "Experiment: ${label.text}"
            if (next == ExperimentState.COOLDOWN && old != ExperimentState.COOLDOWN) {
                try {
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                } catch (e: RuntimeException) {
                    Log.w("CocoExperiment", "Status tone failed", e)
                }
            }
        }
    }

    override fun close() {
        // MainActivity calls this on the main thread in onDestroy().
        closed = true
        handler.removeCallbacksAndMessages(null)
        tone?.release()
    }
}
