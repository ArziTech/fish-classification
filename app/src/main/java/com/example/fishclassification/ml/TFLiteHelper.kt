package com.example.fishclassification.ml

import android.content.Context
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TFLiteHelper {

    /**
     * Loads a TFLite model from the app's assets as a memory-mapped [MappedByteBuffer].
     * Throws [IOException] with a descriptive message if the asset is not found.
     */
    @Throws(IOException::class)
    fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        return try {
            context.assets.openFd(assetPath).use { fd ->
                fd.createInputStream().channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        } catch (e: IOException) {
            throw IOException("Failed to load TFLite model from assets: '$assetPath'. " +
                    "Make sure the file exists in the assets directory.", e)
        }
    }

    /**
     * Reads a labels file from assets, one label per line.
     * Lines starting with '#' and blank lines are ignored.
     */
    fun loadLabels(context: Context, assetPath: String): List<String> {
        return context.assets.open(assetPath).bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .toList()
        }
    }

    /**
     * Attempts to create a [GpuDelegate]. Returns null if the GPU delegate is not
     * supported on this device or if native lib loading fails.
     */
    fun tryCreateGpuDelegate(): GpuDelegate? {
        return try {
            // Use the no-arg constructor; GpuDelegate.Options extends GpuDelegateFactory.Options
            // which requires the tensorflow-lite-support library (excluded due to AGP 9 namespace
            // conflict). The no-arg constructor applies sensible defaults on most devices.
            GpuDelegate()
        } catch (e: Exception) {
            null
        }
    }
}
