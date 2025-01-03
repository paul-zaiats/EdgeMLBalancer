package org.tensorflow.lite.examples.objectdetection;

import java.lang.System;

@kotlin.Metadata(mv = {1, 6, 0}, k = 1, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\t\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u000e\u001a\u00020\u000fH\u0002J\b\u0010\u0010\u001a\u00020\u0011H\u0002J>\u0010\u0012\u001a\u00020\u000f2\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\r2\u0006\u0010\u0016\u001a\u00020\r2\u0006\u0010\u0017\u001a\u00020\u00112\u0006\u0010\u0018\u001a\u00020\r2\u0006\u0010\u0019\u001a\u00020\r2\u0006\u0010\u001a\u001a\u00020\u0014J.\u0010\u001b\u001a\u00020\u000f2\u0006\u0010\u001c\u001a\u00020\u001d2\f\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u00070\u001f2\u0006\u0010 \u001a\u00020\u00112\u0006\u0010!\u001a\u00020\u0014H\u0002J\u001e\u0010\"\u001a\u00020\u000f2\u0006\u0010#\u001a\u00020\u001d2\u0006\u0010$\u001a\u00020\u001d2\u0006\u0010%\u001a\u00020\u001dJ\u0010\u0010&\u001a\u00020\u000f2\u0006\u0010\'\u001a\u00020\u0011H\u0002R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\n\u001a\u0004\u0018\u00010\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006("}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/MetricLogger;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "batteryConsumptionEntries", "Ljava/util/ArrayList;", "Lcom/github/mikephil/charting/data/Entry;", "batteryLevelEntries", "cpuUsageEntries", "logFile", "Ljava/io/File;", "timeCounter", "", "createLogFile", "", "getCurrentTimestamp", "", "logMetrics", "batteryLevel", "", "cpuUsage", "batteryConsumption", "selectedModel", "instantaneousConfidence", "averageConfidence", "currentTotalPredictions", "updateChart", "chart", "Lcom/github/mikephil/charting/charts/LineChart;", "entries", "", "label", "color", "updateCharts", "batteryLevelChart", "cpuUsageChart", "batteryConsumptionChart", "writeToLogFile", "message", "app_debug"})
public final class MetricLogger {
    private final android.content.Context context = null;
    private final java.util.ArrayList<com.github.mikephil.charting.data.Entry> batteryLevelEntries = null;
    private final java.util.ArrayList<com.github.mikephil.charting.data.Entry> cpuUsageEntries = null;
    private final java.util.ArrayList<com.github.mikephil.charting.data.Entry> batteryConsumptionEntries = null;
    private float timeCounter = 0.0F;
    private java.io.File logFile;
    
    public MetricLogger(@org.jetbrains.annotations.NotNull
    android.content.Context context) {
        super();
    }
    
    private final void createLogFile() {
    }
    
    private final void writeToLogFile(java.lang.String message) {
    }
    
    private final java.lang.String getCurrentTimestamp() {
        return null;
    }
    
    public final void logMetrics(int batteryLevel, float cpuUsage, float batteryConsumption, @org.jetbrains.annotations.NotNull
    java.lang.String selectedModel, float instantaneousConfidence, float averageConfidence, int currentTotalPredictions) {
    }
    
    public final void updateCharts(@org.jetbrains.annotations.NotNull
    com.github.mikephil.charting.charts.LineChart batteryLevelChart, @org.jetbrains.annotations.NotNull
    com.github.mikephil.charting.charts.LineChart cpuUsageChart, @org.jetbrains.annotations.NotNull
    com.github.mikephil.charting.charts.LineChart batteryConsumptionChart) {
    }
    
    private final void updateChart(com.github.mikephil.charting.charts.LineChart chart, java.util.List<? extends com.github.mikephil.charting.data.Entry> entries, java.lang.String label, int color) {
    }
}