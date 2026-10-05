package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ThermalSample(
    val sampledAtElapsedNs: Long,
    val batteryTemperatureC: Double?,
    val thermalStatus: Int?,
    val plugged: Boolean?
)

class ThermalSampler(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val executor = Executors.newSingleThreadScheduledExecutor()

    @Volatile
    var latest: ThermalSample? = null
        private set

    init {
        executor.scheduleWithFixedDelay(
            {
                try {
                    sample()
                } catch (e: Exception) {
                    Log.w("ThermalSampler", "Could not sample thermal data", e)
                }
            },
            0,
            1,
            TimeUnit.SECONDS
        )
    }

    private fun sample() {
        // Читаем sticky broadcast, не регистрируя receiver.
        val battery = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val temperature = battery
            ?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            ?.let { it / 10.0 }

        val plugged = battery
            ?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            ?.let { it != 0 }

        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            null
        }

        latest = ThermalSample(
            sampledAtElapsedNs = SystemClock.elapsedRealtimeNanos(),
            batteryTemperatureC = temperature,
            thermalStatus = status,
            plugged = plugged
        )
    }

    override fun close() {
        executor.shutdownNow()
    }
}