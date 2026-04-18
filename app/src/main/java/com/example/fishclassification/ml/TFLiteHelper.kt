package com.example.fishclassification.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TFLiteHelper {

    const val TAG = "TFLiteHelper"

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
     * supported on this device, native libs fail to load, or
     * `GpuDelegateFactory.Options` is missing at runtime (catches Throwable to
     * include LinkageError/NoClassDefFoundError, not just Exception).
     */
    fun tryCreateGpuDelegate(): GpuDelegate? {
        return try {
            GpuDelegate()
        } catch (t: Throwable) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /**
     * Logs every input/output tensor's index, name, shape, and dtype.
     * Call right after creating an [Interpreter] to confirm what the model actually expects.
     */
    fun logTensorInfo(interpreter: Interpreter) {
        val inputs = interpreter.inputTensorCount
        val outputs = interpreter.outputTensorCount
        Log.d(TAG, "Model has $inputs input tensor(s) and $outputs output tensor(s)")
        for (i in 0 until inputs) {
            val t = interpreter.getInputTensor(i)
            Log.d(TAG, "  IN [$i] name='${t.name()}' shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
        for (i in 0 until outputs) {
            val t = interpreter.getOutputTensor(i)
            Log.d(TAG, "  OUT[$i] name='${t.name()}' shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
    }
}
