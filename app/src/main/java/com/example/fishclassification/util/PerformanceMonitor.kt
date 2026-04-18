package com.example.fishclassification.util

import android.os.Debug
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class PerformanceSnapshot(
    val javaHeapUsedMb: Long,
    val javaHeapMaxMb: Long,
    val nativeHeapUsedMb: Long,
    val dalvikHeapUsedMb: Long,
    val cpuUsagePercent: Float?,
    val gpuUsed: Boolean,
)

data class ModelInfo(
    val inputShape: List<Int>,
    val outputShape: List<Int>,
)

object PerformanceMonitor {

    fun captureMemory(gpuUsed: Boolean, cpuUsagePercent: Float?): PerformanceSnapshot {
        val runtime = Runtime.getRuntime()
        val javaHeapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val javaHeapMax = runtime.maxMemory() / (1024L * 1024L)

        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)

        // nativePss and dalvikPss are in KB on all API levels >= 24
        val nativeHeapUsedMb = memInfo.nativePss / 1024L
        val dalvikHeapUsedMb = memInfo.dalvikPss / 1024L

        return PerformanceSnapshot(
            javaHeapUsedMb = javaHeapUsed,
            javaHeapMaxMb = javaHeapMax,
            nativeHeapUsedMb = nativeHeapUsedMb,
            dalvikHeapUsedMb = dalvikHeapUsedMb,
            cpuUsagePercent = cpuUsagePercent,
            gpuUsed = gpuUsed,
        )
    }

    @Suppress("ReturnCount")
    suspend fun sampleCpuUsage(durationMs: Long = 200): Float? = withContext(Dispatchers.IO) {
        try {
            val clkTck = Os.sysconf(OsConstants._SC_CLK_TCK)
            val numCores = Runtime.getRuntime().availableProcessors()

            val statFile = File("/proc/self/stat")

            fun readStatTicks(): Long {
                val parts = statFile.readText().trim().split(" ")
                // utime is field 14 (0-indexed 13), stime is 15 (0-indexed 14)
                val utime = parts.getOrNull(13)?.toLongOrNull() ?: return -1L
                val stime = parts.getOrNull(14)?.toLongOrNull() ?: return -1L
                return utime + stime
            }

            val ticksBefore = readStatTicks()
            val wallBefore = System.nanoTime()
            delay(durationMs)
            val ticksAfter = readStatTicks()
            val wallAfter = System.nanoTime()

            if (ticksBefore < 0 || ticksAfter < 0 || clkTck <= 0) return@withContext null

            val elapsedSec = (wallAfter - wallBefore) / 1_000_000_000.0
            val deltaTicks = ticksAfter - ticksBefore

            val cpuPercent = ((deltaTicks.toDouble() / (clkTck * elapsedSec * numCores)) * 100.0)
                .coerceIn(0.0, 100.0 * numCores)
                .toFloat()

            cpuPercent
        } catch (_: Exception) {
            null
        }
    }
}
