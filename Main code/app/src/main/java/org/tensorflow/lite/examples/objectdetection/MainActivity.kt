package org.tensorflow.lite.examples.objectdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
//import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
//import android.widget.TextView
//import java.io.File
//import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ImageView
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import kotlin.math.max

class BatteryMonitor(private val context: Context, private val listener: BatteryListener) {

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            listener.onBatteryLevelChanged(batteryLevel)
        }
    }

    private val cpuMonitorHandler = Handler(Looper.getMainLooper())
    private val cpuMonitorRunnable = object : Runnable {
        override fun run() {
            val cpuUsage = getCpuUsage()
            listener.onCpuUsageChanged(cpuUsage)
            listener.onBatteryConsumptionChanged(cpuUsage) // Notify about battery consumption
            cpuMonitorHandler.postDelayed(this, CPU_MONITOR_INTERVAL)
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
        cpuMonitorHandler.postDelayed(cpuMonitorRunnable, CPU_MONITOR_INTERVAL)
    }

    fun stopMonitoring() {
        context.unregisterReceiver(batteryReceiver)
        cpuMonitorHandler.removeCallbacks(cpuMonitorRunnable)
    }

//    private fun getCpuUsage(): Float {
//        val cpuTime = Debug.threadCpuTimeNanos()
//        val elapsedTime = System.nanoTime() - cpuTime
//        val cpuUsage = (cpuTime / elapsedTime.toFloat()) * 100
//
//        return if (cpuUsage.isNaN() || cpuUsage.isInfinite()) 0f else cpuUsage
//    }

    private fun getCpuUsage(): Float {
        try {
            val prevCpuInfo = readCpuInfo()
            Thread.sleep(500) // Wait for 500ms to get a more accurate reading
            val currentCpuInfo = readCpuInfo()

            var totalCpuUsage = 0f
            var totalCores = 0

            for ((core, prevInfo) in prevCpuInfo.withIndex()) {
                val currentInfo = currentCpuInfo[core]

                val prevIdle = prevInfo[3]
                val currentIdle = currentInfo[3]

                val prevTotal = prevInfo.sum()
                val currentTotal = currentInfo.sum()

                val deltaIdle = currentIdle - prevIdle
                val deltaTotal = currentTotal - prevTotal

                val cpuUsage = max(0f, (1 - deltaIdle.toFloat() / deltaTotal) * 100)

                totalCpuUsage += cpuUsage
                totalCores++
            }

            return totalCpuUsage / totalCores
        } catch (e: Exception) {
            e.printStackTrace()
            return 0f
        }
    }

    private fun readCpuInfo(): List<List<Long>> {
        val cpuInfoList = mutableListOf<List<Long>>()
        RandomAccessFile("/proc/stat", "r").use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("cpu")) {
                    val cpuInfo = line!!.split("\\s+".toRegex()).drop(1)
                        .map { it.toLongOrNull() ?: 0L }
                        .take(4) // user, nice, system, idle
                    cpuInfoList.add(cpuInfo)
                }
            }
        }
        return cpuInfoList
    }

    interface BatteryListener {
        fun onBatteryLevelChanged(batteryLevel: Int)
        fun onCpuUsageChanged(cpuUsage: Float)
        fun onBatteryConsumptionChanged(batteryConsumption: Float)
    }

    companion object {
        private const val CPU_MONITOR_INTERVAL = 1000L // 1 second
    }
}


class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        navController = navHostFragment.navController
    }
}