package org.tensorflow.lite.examples.objectdetection.fragments;

import java.lang.System;

@kotlin.Metadata(mv = {1, 6, 0}, k = 1, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0002\b\b\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u0007\u001a\u00020\bH\u0002J\b\u0010\t\u001a\u00020\nH\u0002J>\u0010\u000b\u001a\u00020\b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u000f2\u0006\u0010\u0011\u001a\u00020\n2\u0006\u0010\u0012\u001a\u00020\u000f2\u0006\u0010\u0013\u001a\u00020\u000f2\u0006\u0010\u0014\u001a\u00020\rJ\u0010\u0010\u0015\u001a\u00020\b2\u0006\u0010\u0016\u001a\u00020\nH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0017"}, d2 = {"Lorg/tensorflow/lite/examples/objectdetection/fragments/MetricLogger;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "logFile", "Ljava/io/File;", "createLogFile", "", "getCurrentTimestamp", "", "logMetrics", "batteryLevel", "", "cpuUsage", "", "batteryConsumption", "selectedModel", "instantaneousConfidence", "averageConfidence", "currentTotalPredictions", "writeToLogFile", "message", "app_debug"})
public final class MetricLogger {
    private final android.content.Context context = null;
    private java.io.File logFile;
    
    public MetricLogger(@org.jetbrains.annotations.NotNull
    android.content.Context context) {
        super();
    }
    
    public final void logMetrics(int batteryLevel, float cpuUsage, float batteryConsumption, @org.jetbrains.annotations.NotNull
    java.lang.String selectedModel, float instantaneousConfidence, float averageConfidence, int currentTotalPredictions) {
    }
    
    private final void createLogFile() {
    }
    
    private final void writeToLogFile(java.lang.String message) {
    }
    
    private final java.lang.String getCurrentTimestamp() {
        return null;
    }
}