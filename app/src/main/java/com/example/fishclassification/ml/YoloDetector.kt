package com.example.fishclassification.ml

import android.content.Context
import android.net.Uri
import com.example.fishclassification.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import kotlin.math.exp

/**
 * Orchestrates image preprocessing, TFLite inference, and output post-processing
 * for a YOLOv11 detection model.
 *
 * Usage:
 * ```
 * val detector = YoloDetector(context)
 * detector.initialize()
 * val result = detector.detect(imageUri)
 * detector.close()
 * ```
 */
class YoloDetector(private val context: Context) {

    companion object {
        private const val TAG = "YoloDetector"
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var labels: List<String> = emptyList()
    private var inputSize: Int = 640
    private var inputDataType: DataType = DataType.FLOAT32

    /** True when a [GpuDelegate] was successfully attached to the interpreter. */
    var usingGpu: Boolean = false
        private set

    /**
     * Loads the TFLite model and labels from assets. When [useGpu] is true the
     * detector attempts to attach a GPU delegate (falling back to CPU if the
     * device is not supported). When [useGpu] is false, inference runs on CPU
     * regardless of device capability.
     */
    suspend fun initialize(
        modelAsset: String = "yolov11_fp16.tflite",
        labelsAsset: String = "labels.txt",
        useGpu: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        labels = TFLiteHelper.loadLabels(context, labelsAsset)
        val modelBuffer = TFLiteHelper.loadModelFile(context, modelAsset)

        val gpu = if (useGpu) TFLiteHelper.tryCreateGpuDelegate() else null
        usingGpu = gpu != null
        gpuDelegate = gpu

        val options = Interpreter.Options().apply {
            numThreads = 4
            gpu?.let { addDelegate(it) }
        }

        val interp = Interpreter(modelBuffer, options)

        TFLiteHelper.logTensorInfo(interp)
        AppLogger.i(TAG, "Loaded model='$modelAsset' labels=${labels.size} requestedGpu=$useGpu gpuDelegateAttached=$usingGpu")

        val inputShape = interp.getInputTensor(0).shape()
        inputSize = if (inputShape.size >= 3) inputShape[1] else 640
        inputDataType = interp.getInputTensor(0).dataType()
        AppLogger.d(TAG, "Resolved inputSize=$inputSize dataType=$inputDataType from inputShape=${inputShape.toList()}")

        interpreter = interp
    }

    /**
     * Preprocesses the image at [uri], runs inference, and returns an [InferenceResult].
     * Must be called after [initialize].
     *
     * @throws IllegalStateException if [initialize] has not been called.
     */
    suspend fun detect(uri: Uri): InferenceResult {
        val interp = interpreter
            ?: throw IllegalStateException("YoloDetector not initialized — call initialize() first")

        val inputBuffer = withContext(Dispatchers.IO) {
            val preprocessor = ImagePreprocessor(inputSize)
            preprocessor.preprocess(context, uri, inputDataType)
        }

        return withContext(Dispatchers.Default) {
            val outputShape = interp.getOutputTensor(0).shape()
            AppLogger.d(TAG, "Output tensor shape=${outputShape.toList()} dtype=${interp.getOutputTensor(0).dataType()}")
            when (outputShape.size) {
                2 -> runClassification(interp, inputBuffer, outputShape)
                3 -> runDetection(interp, inputBuffer, outputShape)
                else -> error("Unsupported output rank ${outputShape.size}; shape=${outputShape.toList()}")
            }
        }
    }

    private fun runClassification(interp: Interpreter, input: ByteBuffer, outputShape: IntArray): InferenceResult {
        val numClasses = outputShape[1]
        val outputDataType = interp.getOutputTensor(0).dataType()

        val startNs = System.nanoTime()
        val probs: FloatArray
        if (outputDataType == DataType.UINT8) {
            val rawOutput = Array(1) { ByteArray(numClasses) }
            interp.run(input, rawOutput)
            val qp = interp.getOutputTensor(0).quantizationParams()
            probs = FloatArray(numClasses) { i ->
                qp.scale * ((rawOutput[0][i].toInt() and 0xFF) - qp.zeroPoint)
            }
        } else {
            val rawOutput = Array(1) { FloatArray(numClasses) }
            interp.run(input, rawOutput)
            val scores = rawOutput[0]
            probs = if (scores.any { it < 0f || it > 1f }) softmax(scores) else scores
        }
        val inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000L

        var bestIdx = 0
        var bestScore = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > bestScore) { bestScore = probs[i]; bestIdx = i }
        }

        val className = labels.getOrElse(bestIdx) { "class_$bestIdx" }
        AppLogger.d(TAG, "Classification: idx=$bestIdx name='$className' score=$bestScore time=${inferenceTimeMs}ms (gpu=$usingGpu)")
        if (labels.size != numClasses) {
            AppLogger.w(TAG, "labels.txt has ${labels.size} entries but model has $numClasses outputs — update labels.txt to match")
        }

        return InferenceResult(
            className = className,
            confidence = bestScore,
            inferenceTimeMs = inferenceTimeMs,
            allDetections = emptyList(),
        )
    }

    private fun runDetection(interp: Interpreter, input: ByteBuffer, outputShape: IntArray): InferenceResult {
        val dim1 = outputShape[1]
        val dim2 = outputShape[2]
        val rawOutput = Array(1) { Array(dim1) { FloatArray(dim2) } }

        val startNs = System.nanoTime()
        interp.run(input, rawOutput)
        val inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000L
        AppLogger.d(TAG, "Detection inference done in ${inferenceTimeMs}ms (gpu=$usingGpu)")

        val detections = PostProcessor.parseDetections(
            rawOutput = rawOutput,
            labels = labels,
            inputSize = inputSize,
        )
        AppLogger.d(TAG, "Parsed ${detections.size} detection(s) after NMS")

        val top = PostProcessor.pickTopResult(detections)
        return if (top != null) {
            InferenceResult(
                className = labels.getOrElse(top.first) { "class_${top.first}" },
                confidence = top.second,
                inferenceTimeMs = inferenceTimeMs,
                allDetections = detections,
            )
        } else {
            InferenceResult(
                className = "Unknown",
                confidence = 0f,
                inferenceTimeMs = inferenceTimeMs,
                allDetections = emptyList(),
            )
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    fun isInitialized(): Boolean = interpreter != null

    fun getInputShape(): IntArray? = interpreter?.getInputTensor(0)?.shape()

    fun getOutputShape(): IntArray? = interpreter?.getOutputTensor(0)?.shape()
}
