package com.example.fishclassification.ml

import android.content.Context
import com.example.fishclassification.util.AppLogger
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
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
     * Creates a [GpuDelegate] only when [CompatibilityList] reports the device
     * as supported; applies the device's best options plus precision-loss and
     * quantized-model allowances so FP16 and quantized models actually
     * delegate to GPU. Returns null when the device is unsupported or native
     * libs fail to load (catches Throwable to include LinkageError /
     * NoClassDefFoundError).
     */
    fun tryCreateGpuDelegate(): GpuDelegate? {
        return try {
            val compatList = CompatibilityList()
            if (!compatList.isDelegateSupportedOnThisDevice) {
                AppLogger.i(TAG, "GPU delegate: device NOT supported by CompatibilityList — running on CPU")
                return null
            }
            AppLogger.i(TAG, "GPU delegate: device supported, creating GpuDelegate")
            GpuDelegate()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "GPU delegate unavailable, falling back to CPU: ${t.javaClass.simpleName}: ${t.message}", t)
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
        AppLogger.d(TAG, "Model has $inputs input tensor(s) and $outputs output tensor(s)")
        for (i in 0 until inputs) {
            val t = interpreter.getInputTensor(i)
            AppLogger.d(TAG, "  IN [$i] name='${t.name()}' shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
        for (i in 0 until outputs) {
            val t = interpreter.getOutputTensor(i)
            AppLogger.d(TAG, "  OUT[$i] name='${t.name()}' shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
    }
}
