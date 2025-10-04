/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.objectdetection.fragments

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.examples.objectdetection.ObjectDetectorHelper
import org.tensorflow.lite.examples.objectdetection.R
//import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.task.vision.detector.Detection
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
//import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import org.tensorflow.lite.examples.objectdetection.MetricLogger
import android.graphics.Color
import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.constraintlayout.motion.widget.Debug
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import android.os.Process
import kotlin.math.min
import kotlin.math.max
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.LineData
import kotlin.random.Random
//import org.tensorflow.lite.examples.objectdetection.fragments.PermissionsFragmentDirections
//

class MetricLogger(private val context: Context) {
    private var logFile: File? = null

    init {
        createLogFile()
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
    val logMessage = "$timestamp,$batteryLevel,$cpuUsage,$batteryConsumption,$selectedModel,$instantaneousConfidence,$averageConfidence,$currentTotalPredictions" // Updated log message
    writeToLogFile(logMessage)
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
        writeToLogFile("Timestamp,BatteryLevel,CPUUsage,BatteryConsumption,SelectedModel,InstantaneousConfidence,AverageConfidence,CurrentTotalPredictions")
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
}


class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private var CPU_Usage: Int = 0

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var bitmapBuffer: Bitmap
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var metricLogger: MetricLogger
    private lateinit var batteryLevelChart: LineChart
    private lateinit var cpuUsageChart: LineChart
    private lateinit var batteryConsumptionChart: LineChart
    private lateinit var instantaneousConfidenceChart: LineChart // New chart for instantaneous confidence
    private val instantaneousConfidenceEntries = ArrayList<Entry>() // New entries for instantaneous confidence
    private lateinit var averageConfidenceChart: LineChart // New chart for average confidence
    private val averageConfidenceEntries = ArrayList<Entry>() // New entries for average confidence

    private val batteryLevelEntries = ArrayList<Entry>()
    private val cpuUsageEntries = ArrayList<Entry>()
    private val batteryConsumptionEntries = ArrayList<Entry>()
    private var chartXValue = 0f

//    private var totalPredictions: Int = 0
//    private var correctPredictions: Int = 0
//    private val modelPredictions = mutableMapOf<Int, Int>() // Total predictions per model
//    private val modelCorrectPredictions = mutableMapOf<Int, Int>() // Correct predictions per model
//    private val modelConfidence = mutableMapOf<Int, MutableList<Float>>() // Confidence scores per model
//    private val defaultThreshold = 0.3f
//
//    private var instantaneousConfidence: Float = 0f
//
//    // Initialize the model-specific counters
//    init {
//        for (i in 0..3) { // Assuming you have 4 models (0 to 3)
//            modelPredictions[i] = 0
//            modelCorrectPredictions[i] = 0
//            modelConfidence[i] = mutableListOf()
//        }
//
//    }

    private var totalPredictions: Int = 0
    private var correctPredictions: Int = 0
    private val modelPredictions = mutableMapOf<Int, Int>() // Total predictions per model
    private val modelCorrectPredictions = mutableMapOf<Int, Int>() // Correct predictions per model
    private val modelConfidence = mutableMapOf<Int, MutableList<Float>>() // Confidence scores per model
    private val modelAverageConfidence = mutableMapOf<Int, Float>() // New map to store average confidence for each model
    private val modelConfidenceCount = mutableMapOf<Int, Int>() // New map to count the number of confidence entries for each model
    private val defaultThreshold = 0.3f

    private var instantaneousConfidence: Float = 0f

    // Initialize the model-specific counters
    init {
        for (i in 0..3) { // Assuming you have 4 models (0 to 3)
            modelPredictions[i] = 0
            modelCorrectPredictions[i] = 0
            modelConfidence[i] = mutableListOf()
            modelAverageConfidence[i] = 0f // Initialize average confidence for each model
            modelConfidenceCount[i] = 0 // Initialize count for each model
        }
    }

    // Global variables for time tracking
    private var E0TimeLapsed: Long = 0
    private var E1TimeLapsed: Long = 0
    private var E2TimeLapsed: Long = 0
    private var MVTimeLapsed: Long = 0
    private var start_time: Long = 0
    private var abs_start_time: Long = 0
    private var E0avg: Float = 0f
    private var E1avg: Float = 0f
    private var E2avg: Float = 0f
    private var MVavg: Float = 0f

    private var E0last: Float = 0f
    private var E1last: Float = 0f
    private var E2last: Float = 0f
    private var MVlast: Float = 0f

    private var currentNoofBoxes: Int = 0

    private var time_slice = 2

    private fun updateSelectedModel() {
        val selectedModel = getSelectedModel()
        fragmentCameraBinding.bottomSheetLayout.textViewSelectedModel.text = "Selected Model: $selectedModel"
    }

    private fun getSelectedModel(): String {
        return getModelBasedOnCriteria()
    }

    private fun getModelBasedOnCriteria(): String {
        val p = Math.random()
        val epsilon = 0.1
        if(p<epsilon)
        {
            val num = Random.nextInt(0, 4)
            if(num == 1)
            {
                E0TimeLapsed++
                E0last = getCpuUsage()
                E0avg = (E0avg*(E0TimeLapsed-1) + E0last) / E0TimeLapsed
                if (objectDetectorHelper.currentModel != 1) {
                    objectDetectorHelper.currentModel = 1
                    Log.d("ModelUpdate", "Model updated to index: 1")
                    // Clear and reinitialize the detector
                    updateControlsUi()
                }
                return "EfficientDet Lite0"
            }
            if(num == 2)
            {
                E1TimeLapsed++
                E1last = getCpuUsage()
                E1avg = (E1avg*(E1TimeLapsed-1) + E1last) / E1TimeLapsed
                if (objectDetectorHelper.currentModel != 2) {
                    objectDetectorHelper.currentModel = 2
                    Log.d("ModelUpdate", "Model updated to index: 2")
                    // Clear and reinitialize the detector
                    updateControlsUi()
                }
                return "EfficientDet Lite1"
            }
            if(num == 3)
            {
                E2TimeLapsed++
                E2last = getCpuUsage()
                E2avg = (E2avg*(E2TimeLapsed-1) + E2last) / E2TimeLapsed
                if (objectDetectorHelper.currentModel != 3) {
                    objectDetectorHelper.currentModel = 3
                    Log.d("ModelUpdate", "Model updated to index: 3")
                    // Clear and reinitialize the detector
                    updateControlsUi()
                }
                return "EfficientDet Lite2"
            }

            MVTimeLapsed++
            MVlast = getCpuUsage()
            MVavg = (MVavg*(MVTimeLapsed-1) + MVlast) / MVTimeLapsed
            if (objectDetectorHelper.currentModel != 0) {
                objectDetectorHelper.currentModel = 0
                Log.d("ModelUpdate", "Model updated to index: 0")
                // Clear and reinitialize the detector
                updateControlsUi()
            }
            return "MobileNet V1"
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

        score[0] = min(MVavg.toFloat(), MVlast.toFloat()) *
                (1 - ((modelAverageConfidence[0] ?: defaultConfidence) /
                        (modelConfidence[0]?.getOrNull(0) ?: defaultConfidence)))

        score[1] = min(E0avg.toFloat(), E0last.toFloat()) *
                (1 - ((modelAverageConfidence[1] ?: defaultConfidence) /
                        (modelConfidence[1]?.getOrNull(0) ?: defaultConfidence)))

        score[2] = min(E1avg.toFloat(), E1last.toFloat()) *
                (1 - ((modelAverageConfidence[2] ?: defaultConfidence) /
                        (modelConfidence[2]?.getOrNull(0) ?: defaultConfidence)))

        score[3] = min(E2avg.toFloat(), E2last.toFloat()) *
                (1 - ((modelAverageConfidence[3] ?: defaultConfidence) /
                        (modelConfidence[3]?.getOrNull(0) ?: defaultConfidence)))

        val value = min(min(score[0], score[1]), min(score[2], score[3]))
        if(value == score[1])
        {
            E0TimeLapsed++
            E0last = getCpuUsage()
            E0avg = (E0avg*(E0TimeLapsed-1) + E0last) / E0TimeLapsed
            if (objectDetectorHelper.currentModel != 1) {
                objectDetectorHelper.currentModel = 1
                Log.d("ModelUpdate", "Model updated to index: 1")
                // Clear and reinitialize the detector
                updateControlsUi()
            }
            return "EfficientDet Lite0"
        }
        if(value == score[2])
        {
            E1TimeLapsed++
            E1last = getCpuUsage()
            E1avg = (E1avg*(E1TimeLapsed-1) + E1last) / E1TimeLapsed
            if (objectDetectorHelper.currentModel != 2) {
                objectDetectorHelper.currentModel = 2
                Log.d("ModelUpdate", "Model updated to index: 2")
                // Clear and reinitialize the detector
                updateControlsUi()
            }
            return "EfficientDet Lite1"
        }
        if(value == score[3])
        {
            E2TimeLapsed++
            E2last = getCpuUsage()
            E2avg = (E2avg*(E2TimeLapsed-1) + E2last) / E2TimeLapsed
            if (objectDetectorHelper.currentModel != 3) {
                objectDetectorHelper.currentModel = 3
                Log.d("ModelUpdate", "Model updated to index: 3")
                // Clear and reinitialize the detector
                updateControlsUi()
            }
            return "EfficientDet Lite2"
        }

        MVTimeLapsed++
        MVlast = getCpuUsage()
        MVavg = (MVavg*(MVTimeLapsed-1) + MVlast) / MVTimeLapsed
        if (objectDetectorHelper.currentModel != 0) {
            objectDetectorHelper.currentModel = 0
            Log.d("ModelUpdate", "Model updated to index: 0")
            // Clear and reinitialize the detector
            updateControlsUi()
        }
        return "MobileNet V1"
    }
    

    // private fun getIndexOfModel(): Int { // Specify return type as Int
    //     val str = getSelectedModel()
    //     return when (str) {
    //         "MobileNet V1" -> 0
    //         "EfficientDet Lite0" -> 1
    //         "EfficientDet Lite1" -> 2
    //         else -> 3
    //     }
    // }

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

    private fun getBatteryLevel(): Int {
        val batteryManager = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getBatteryConsumption(): Float {
        val batteryLevel = getBatteryLevel()
        val cpuUsage = getCpuUsage()

        // This is a simplified calculation. You might want to implement a more sophisticated method
        return batteryLevel * cpuUsage / 100.0f
    }

    private fun calculateInstantaneousConfidence(): Float {
        // Logic to calculate instantaneous confidence based on current results
        // This should return the latest instantaneous confidence value
         val currentModelIndex = objectDetectorHelper.currentModel
         val currentTotalPredictions = modelPredictions[currentModelIndex] ?: 0
         val currentCorrectPredictions = modelCorrectPredictions[currentModelIndex] ?: 0

         return if (currentTotalPredictions > 0) {
             (currentCorrectPredictions.toFloat() / currentTotalPredictions * 100)
         } else {
             0f
         }
    }

    private fun updateCharts(batteryLevel: Int, cpuUsage: Float, batteryConsumption: Float, currentTotalPredictions: Int, instantaneousConfidence: Float) {
    chartXValue += 1f

    // Update battery level chart
    updateChart(batteryLevelChart, batteryLevelEntries, batteryLevel.toFloat(), "Battery Level", Color.BLUE)
    updateChart(cpuUsageChart, cpuUsageEntries, cpuUsage, "CPU Usage", Color.RED)
    updateChart(batteryConsumptionChart, batteryConsumptionEntries, batteryConsumption, "Battery Consumption", Color.GREEN)

    val averageConfidence = averageConfidenceEntries.lastOrNull()?.y ?: 0f // Get last average confidence or 0 if empty

    // Log metrics including new values
    metricLogger.logMetrics(batteryLevel, cpuUsage, batteryConsumption, getSelectedModel(), instantaneousConfidence, averageConfidence, currentTotalPredictions) // Updated call
}
    
    
    private fun updateChart(chart: LineChart, entries: ArrayList<Entry>, newValue: Float, label: String, color: Int) {
        entries.add(Entry(chartXValue, newValue))

        // Limit the number of visible entries
        val visibleRange = 60f // Show last 60 seconds of data
        if (entries.size > visibleRange) {
            entries.removeAt(0)
        }

        val dataSet = LineDataSet(entries, label).apply {
            setDrawCircles(false)
            lineWidth = 2f
            setColor(color)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.data = LineData(dataSet)
        chart.apply {
            setVisibleXRangeMaximum(visibleRange)
            moveViewToX(chartXValue - visibleRange)
            description.isEnabled = false
            legend.isEnabled = true
            xAxis.setDrawLabels(false)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
        }

        chart.invalidate()
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService
    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions)
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    private fun setupChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)

            xAxis.apply {
                textColor = Color.WHITE
                setDrawGridLines(false)
                setDrawAxisLine(true)
                position = XAxis.XAxisPosition.BOTTOM
            }

            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                setDrawAxisLine(true)
            }

            axisRight.isEnabled = false

            legend.apply {
                textColor = Color.WHITE
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false)
            }

            data = LineData().apply {
                setValueTextColor(Color.WHITE)
            }
        }
    }

    private fun setupCharts() {
        setupChart(batteryLevelChart)
        setupChart(cpuUsageChart)
        setupChart(batteryConsumptionChart)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        instantaneousConfidenceChart = fragmentCameraBinding.bottomSheetLayout.instantaneousConfidenceChart // Initialize the new chart
        setupChart(instantaneousConfidenceChart) // Setup the new chart
        averageConfidenceChart = fragmentCameraBinding.bottomSheetLayout.averageConfidenceChart // Initialize the new chart
        setupChart(averageConfidenceChart) // Setup the new chart

        objectDetectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this
        )

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        updateSelectedModel()
//        // Call startModelUpdates() in onViewCreated() after initializing objectDetectorHelper
//        startModelUpdates()

        // Attach listeners to UI control widgets
        initBottomSheetControls()
        metricLogger = MetricLogger(requireContext())
        batteryLevelChart = fragmentCameraBinding.bottomSheetLayout.batteryLevelChart
        cpuUsageChart = fragmentCameraBinding.bottomSheetLayout.cpuUsageChart
        batteryConsumptionChart = fragmentCameraBinding.bottomSheetLayout.batteryConsumptionChart

        setupCharts()
        startMetricUpdates()

        // Set up section switcher
        fragmentCameraBinding.bottomSheetLayout.statsIcon.setOnClickListener {
            showStatsSection()
        }
        fragmentCameraBinding.bottomSheetLayout.graphsIcon.setOnClickListener {
            showGraphsSection()
        }

        // Show stats section by default
        showStatsSection()
    }

    private fun showStatsSection() {
        fragmentCameraBinding.bottomSheetLayout.statsSection.visibility = View.VISIBLE
        fragmentCameraBinding.bottomSheetLayout.graphsSection.visibility = View.GONE
        fragmentCameraBinding.bottomSheetLayout.statsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
        fragmentCameraBinding.bottomSheetLayout.graphsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.grey))
    }

    private fun showGraphsSection() {
        fragmentCameraBinding.bottomSheetLayout.statsSection.visibility = View.GONE
        fragmentCameraBinding.bottomSheetLayout.graphsSection.visibility = View.VISIBLE
        fragmentCameraBinding.bottomSheetLayout.statsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.grey))
        fragmentCameraBinding.bottomSheetLayout.graphsIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun startMetricUpdates() {
    val handler = Handler(Looper.getMainLooper())
    handler.post(object : Runnable {
        override fun run() {
            val batteryLevel = getBatteryLevel()
            val cpuUsage = getCpuUsage()
            val batteryConsumption = getBatteryConsumption()
            val selectedModel = getSelectedModel()

            CPU_Usage = cpuUsage.toInt()

            // Calculate instantaneous and average confidence
            val averageConfidence = averageConfidenceEntries.lastOrNull()?.y ?: 0f // Get last average confidence or 0 if empty

            // Retrieve or calculate currentTotalPredictions
            // val currentTotalPredictions = modelPredictions[objectDetectorHelper.currentModel] ?: 0 // Assuming you have a way to get this
            val currentTotalPredictions = currentNoofBoxes

            fragmentCameraBinding.bottomSheetLayout.textViewBatteryLevel.text = "Battery Level: $batteryLevel%"
            fragmentCameraBinding.bottomSheetLayout.textViewCpuUsage.text = "CPU Usage: ${String.format("%.2f", cpuUsage)}%"
            fragmentCameraBinding.bottomSheetLayout.textViewBatteryConsumption.text = "Battery Consumption: ${String.format("%.2f", batteryConsumption)}%"
            fragmentCameraBinding.bottomSheetLayout.textViewSelectedModel.text = "Selected Model: $selectedModel"

            updateCharts(batteryLevel, cpuUsage, batteryConsumption, currentTotalPredictions, instantaneousConfidence) // Pass currentTotalPredictions to updateCharts
            metricLogger.logMetrics(batteryLevel, cpuUsage, batteryConsumption, selectedModel, instantaneousConfidence, averageConfidence, currentTotalPredictions) // Updated call
            
            updateSelectedModel()

            handler.postDelayed(this, 1000) // Update every second
        }
    })
}

    private fun initBottomSheetControls() {
        // When clicked, lower detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be detected at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (objectDetectorHelper.maxResults > 1) {
                objectDetectorHelper.maxResults--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of objects that can be detected at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (objectDetectorHelper.maxResults < 5) {
                objectDetectorHelper.maxResults++
                updateControlsUi()
            }
        }

        // When clicked, decrease the number of threads used for detection
        fragmentCameraBinding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (objectDetectorHelper.numThreads > 1) {
                objectDetectorHelper.numThreads--
                updateControlsUi()
            }
        }

        // When clicked, increase the number of threads used for detection
        fragmentCameraBinding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (objectDetectorHelper.numThreads < 4) {
                objectDetectorHelper.numThreads++
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    objectDetectorHelper.currentDelegate = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // // When clicked, change the underlying model used for object detection
        // fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        // fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
        //     object : AdapterView.OnItemSelectedListener {
        //         override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        //             objectDetectorHelper.currentModel = p2
        //             Log.d("ModelUpdate", "Model updated to index: $p2")
        //             updateControlsUi()
        //         }

        //         override fun onNothingSelected(p0: AdapterView<*>?) {
        //             /* no op */
        //         }
        //     }
    }

    // // Add a handler to update the model every 500ms
    // private val modelUpdateHandler = Handler(Looper.getMainLooper())
    // private var modelUpdateRunnable: Runnable? = null
    // private var str: String = "" // Declare and initialize `str`

    // private fun getIndexOfModel(): Int { // Specify return type as Int
    //     str = getSelectedModel()
    //     return when (str) {
    //         "MobileNet V1" -> 0
    //         "EfficientDet Lite0" -> 1
    //         "EfficientDet Lite1" -> 2
    //         else -> 3
    //     }
    // }

    // private fun startModelUpdates() {
    //     modelUpdateRunnable = object : Runnable {
    //         override fun run() {
    //             // Update the model here
    //             val newModelIndex = getIndexOfModel()
    //             objectDetectorHelper.currentModel = newModelIndex
    //             Log.d("ModelUpdate", "Model updated to index: $newModelIndex")
    //             updateControlsUi()

    //             // Schedule the next update
    //             modelUpdateHandler.postDelayed(this, 500)
    //         }
    //     }
    //     modelUpdateHandler.post(modelUpdateRunnable!!)
    // }

    // override fun onDestroyView() {
    //     modelUpdateHandler.removeCallbacks(modelUpdateRunnable) // Stop updates when the view is destroyed
    //     modelUpdateRunnable = null // Avoid memory leaks
    //     _fragmentCameraBinding = null
    //     super.onDestroyView() // Ensure this is called at the end
    // }




    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            objectDetectorHelper.maxResults.toString()
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", objectDetectorHelper.threshold)
        fragmentCameraBinding.bottomSheetLayout.threadsValue.text =
            objectDetectorHelper.numThreads.toString()

        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        objectDetectorHelper.clearObjectDetector()
        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            // The image rotation and RGB image buffer are initialized only once
                            // the analyzer has started running
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }

                        detectObjects(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectObjects(image: ImageProxy) {
        // Copy out RGB bits to the shared bitmap buffer
        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }

        val imageRotation = image.imageInfo.rotationDegrees
        // Pass Bitmap and rotation to the object detector helper for processing and detection
        objectDetectorHelper.detect(bitmapBuffer, imageRotation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
        results: MutableList<Detection>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int,
        imageId: Int?
    ) {
        activity?.runOnUiThread {
            fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", inferenceTime)

            val currentModelIndex = objectDetectorHelper.currentModel

            val currentTotalPredictions = results?.size ?: 0
            currentNoofBoxes = currentTotalPredictions
            modelPredictions[currentModelIndex] =
                modelPredictions[currentModelIndex]!! + currentTotalPredictions

            // Calculate instantaneous confidence
            instantaneousConfidence = if (currentTotalPredictions > 0) {
                results?.sumOf { detection ->
                    detection.categories.sumOf { it.score.toDouble() }
                }?.toFloat()?.div(currentTotalPredictions)?.times(100) ?: 0f
            } else {
                0f
            }

            // Add the instantaneous confidence to the list for the current model
            modelConfidence[currentModelIndex]?.add(instantaneousConfidence)

            // Update chart entries for instantaneous confidence
            chartXValue += 1f
            instantaneousConfidenceEntries.add(Entry(chartXValue, instantaneousConfidence))
            updateChart(
                instantaneousConfidenceChart,
                instantaneousConfidenceEntries,
                instantaneousConfidence,
                "Instantaneous Confidence",
                Color.YELLOW
            )

            // Calculate average confidence for the current model
            val averageConfidence = modelConfidence[currentModelIndex]?.average()?.toFloat() ?: 0f
            averageConfidenceEntries.add(Entry(chartXValue, averageConfidence))
            updateChart(
                averageConfidenceChart,
                averageConfidenceEntries,
                averageConfidence,
                "Average Confidence",
                Color.MAGENTA
            )

            // Update accuracy in the UI
            val accuracy = if (modelPredictions[currentModelIndex]!! > 0) {
                (modelCorrectPredictions[currentModelIndex]!!.toFloat() / modelPredictions[currentModelIndex]!! * 100).toInt()
            } else {
                0
            }

//            fragmentCameraBinding.bottomSheetLayout.textViewAccuracy.text = "Accuracy: $accuracy%"
            fragmentCameraBinding.bottomSheetLayout.textViewInstantaneousConfidence.text =
                "Instantaneous Confidence: ${String.format("%.2f", instantaneousConfidence)}%"
            fragmentCameraBinding.bottomSheetLayout.textViewAverageConfidence.text =
                "Average Confidence: ${String.format("%.2f", averageConfidence)}%"


            

            // Pass necessary information to OverlayView for drawing on the canvas
            fragmentCameraBinding.overlay.setResults(
                results ?: LinkedList<Detection>(),
                imageHeight,
                imageWidth
            )

            // Force a redraw
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}