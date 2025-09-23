package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MetricLogger(private val context: Context) {
    private val batteryLevelEntries = ArrayList<Entry>()
    private val cpuUsageEntries = ArrayList<Entry>()
    private val batteryConsumptionEntries = ArrayList<Entry>()

    private var timeCounter = 0f
    private var logFile: File? = null

    init {
        createLogFile()
    }

    private fun createLogFile() {
        val fileName = "metrics_log_${System.currentTimeMillis()}.csv"
        val directory = context.getExternalFilesDir(null)

        logFile = if (directory != null) {
            File(directory, fileName)
        } else {
            File(context.filesDir, fileName)
        }

        // Write header to the CSV file

        writeToLogFile("Timestamp,BatteryLevel,CPUUsage,BatteryConsumption,SelectedModel,InstantaneousConfidence,AverageConfidence,CurrentTotalPredictions") // Updated header
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

    private fun getCurrentTimestamp(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    fun logMetrics(
        batteryLevel: Int,
        cpuUsage: Float,
        batteryConsumption: Float,
        selectedModel: String,
        instantaneousConfidence: Float,
        averageConfidence: Float,
        currentTotalPredictions: Int // New parameter
    ) {
        val timestamp = getCurrentTimestamp()
        Log.d("MetricsLogger", "Instantaneous Confidence: $instantaneousConfidence")
        val logMessage =
            "$timestamp,$batteryLevel,$cpuUsage,$batteryConsumption,$selectedModel,$instantaneousConfidence,$averageConfidence,$currentTotalPredictions" // Updated log message
        writeToLogFile(logMessage)
    }

    fun updateCharts(
        batteryLevelChart: LineChart,
        cpuUsageChart: LineChart,
        batteryConsumptionChart: LineChart
    ) {
        updateChart(batteryLevelChart, batteryLevelEntries, "Battery Level", Color.BLUE)
        updateChart(cpuUsageChart, cpuUsageEntries, "CPU Usage", Color.RED)
        updateChart(
            batteryConsumptionChart,
            batteryConsumptionEntries,
            "Battery Consumption",
            Color.GREEN
        )
    }

    private fun updateChart(chart: LineChart, entries: List<Entry>, label: String, color: Int) {
        val dataSet = LineDataSet(entries, label)
        dataSet.color = color
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2f

        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)
        chart.xAxis.isEnabled = false
        chart.axisLeft.isEnabled = true
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true

        chart.invalidate()
    }


}
