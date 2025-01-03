package org.tensorflow.lite.examples.objectdetection;

import java.lang.System;

@kotlin.Metadata(mv = {1, 6, 0}, k = 1, d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0007\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J&\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u000b2\u0006\u0010\u0011\u001a\u00020\u000b2\u0006\u0010\u0012\u001a\u00020\u0013J.\u0010\u0014\u001a\u00020\r2\u0006\u0010\u0015\u001a\u00020\u00162\f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00070\u00182\u0006\u0010\u0019\u001a\u00020\u00132\u0006\u0010\u001a\u001a\u00020\u000fH\u0002J\u001e\u0010\u001b\u001a\u00020\r2\u0006\u0010\u001c\u001a\u00020\u00162\u0006\u0010\u001d\u001a\u00020\u00162\u0006\u0010\u001e\u001a\u00020\u0016R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001f"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/MetricLogger;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "batteryConsumptionEntries", "Ljava/util/ArrayList;", "Lcom/github/mikephil/charting/data/Entry;", "batteryLevelEntries", "cpuUsageEntries", "timeCounter", "", "logMetrics", "", "batteryLevel", "", "cpuUsage", "batteryConsumption", "selectedModel", "", "updateChart", "chart", "Lcom/github/mikephil/charting/charts/LineChart;", "entries", "", "label", "color", "updateCharts", "batteryLevelChart", "cpuUsageChart", "batteryConsumptionChart", "app_release"})
public final class MetricLogger {
    private final android.content.Context context = null;
    private final java.util.ArrayList<com.github.mikephil.charting.data.Entry> batteryLevelEntries = null;
    private final java.util.ArrayList<com.github.mikephil.charting.data.Entry> cpuUsageEntries = null;
    private final java.util.ArrayList<com.github.mikephil.charting.data.Entry> batteryConsumptionEntries = null;
    private float timeCounter = 0.0F;
    
    public MetricLogger(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    public final void logMetrics(int batteryLevel, float cpuUsage, float batteryConsumption, @org.jetbrains.annotations.NotNull()
    java.lang.String selectedModel) {
    }
    
    public final void updateCharts(@org.jetbrains.annotations.NotNull()
    com.github.mikephil.charting.charts.LineChart batteryLevelChart, @org.jetbrains.annotations.NotNull()
    com.github.mikephil.charting.charts.LineChart cpuUsageChart, @org.jetbrains.annotations.NotNull()
    com.github.mikephil.charting.charts.LineChart batteryConsumptionChart) {
    }
    
    private final void updateChart(com.github.mikephil.charting.charts.LineChart chart, java.util.List<? extends com.github.mikephil.charting.data.Entry> entries, java.lang.String label, int color) {
    }
}