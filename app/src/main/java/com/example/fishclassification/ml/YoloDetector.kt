package com.example.fishclassification.ml

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var usingGpu: Boolean = false
        private set

    /**
     * Loads the TFLite model and labels from assets, sets up the interpreter with
     * GPU delegate (falling back to CPU if GPU is unavailable). Must be called before [detect].
     */
    suspend fun initialize(
        modelAsset: String = "yolov11.tflite",
        labelsAsset: String = "labels.txt",
    ) = withContext(Dispatchers.IO) {
        // Load labels
        labels = TFLiteHelper.loadLabels(context, labelsAsset)

        // Load model — throws IOException if asset is missing
        val modelBuffer = TFLiteHelper.loadModelFile(context, modelAsset)

        // Try GPU delegate; fall back to CPU
        val gpu = TFLiteHelper.tryCreateGpuDelegate()
        usingGpu = gpu != null
        gpuDelegate = gpu

        val options = Interpreter.Options().apply {
            numThreads = 4
            gpu?.let { addDelegate(it) }
        }

        val interp = Interpreter(modelBuffer, options)

        // Surface every tensor's shape + dtype so YOLO output-shape mismatches are debuggable
        TFLiteHelper.logTensorInfo(interp)
        Log.d(TAG, "Loaded model='$modelAsset' labels=${labels.size} usingGpu=$usingGpu")

        // Read input size from the model's input tensor shape: [1, H, W, C]
        val inputShape = interp.getInputTensor(0).shape()
        // Shape is typically [1, 640, 640, 3]; use index 1 as height/width
        inputSize = if (inputShape.size >= 3) inputShape[1] else 640
        Log.d(TAG, "Resolved inputSize=$inputSize from inputShape=${inputShape.toList()}")

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

        // Preprocess on IO (file reading) then infer on Default
        val inputBuffer = withContext(Dispatchers.IO) {
            val preprocessor = ImagePreprocessor(inputSize)
            preprocessor.preprocess(context, uri)
        }

        return withContext(Dispatchers.Default) {
            val outputShape = interp.getOutputTensor(0).shape()
            Log.d(TAG, "Output tensor shape=${outputShape.toList()} dtype=${interp.getOutputTensor(0).dataType()}")
            when (outputShape.size) {
                2 -> runClassification(interp, inputBuffer, outputShape)
                3 -> runDetection(interp, inputBuffer, outputShape)
                else -> error("Unsupported output rank ${outputShape.size}; shape=${outputShape.toList()}")
            }
        }
    }

    /**
     * Classification path: output is `[1, num_classes]`. Picks argmax and applies
     * softmax if the raw values look like logits (any value <0 or >1).
     */
    private fun runClassification(interp: Interpreter, input: ByteBuffer, outputShape: IntArray): InferenceResult {
        val numClasses = outputShape[1]
        val rawOutput = Array(1) { FloatArray(numClasses) }

        val startNs = System.nanoTime()
        interp.run(input, rawOutput)
        val inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000L

        val scores = rawOutput[0]
        val probs = if (scores.any { it < 0f || it > 1f }) softmax(scores) else scores

        var bestIdx = 0
        var bestScore = probs[0]
        for (i in 1 until probs.size) {
            if (probs[i] > bestScore) { bestScore = probs[i]; bestIdx = i }
        }

        val className = labels.getOrElse(bestIdx) { "class_$bestIdx" }
        Log.d(TAG, "Classification: idx=$bestIdx name='$className' score=$bestScore time=${inferenceTimeMs}ms (gpu=$usingGpu)")
        if (labels.size != numClasses) {
            Log.w(TAG, "labels.txt has ${labels.size} entries but model has $numClasses outputs — update labels.txt to match")
        }

        return InferenceResult(
            className = className,
            confidence = bestScore,
            inferenceTimeMs = inferenceTimeMs,
            allDetections = emptyList(),
        )
    }

    /**
     * Detection path: output is `[1, 4+nc, anchors]` (or transposed). Runs full
     * YOLO post-processing with NMS.
     */
    private fun runDetection(interp: Interpreter, input: ByteBuffer, outputShape: IntArray): InferenceResult {
        val dim1 = outputShape[1]
        val dim2 = outputShape[2]
        val rawOutput = Array(1) { Array(dim1) { FloatArray(dim2) } }

        val startNs = System.nanoTime()
        interp.run(input, rawOutput)
        val inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000L
        Log.d(TAG, "Detection inference done in ${inferenceTimeMs}ms (gpu=$usingGpu)")

        val detections = PostProcessor.parseDetections(
            rawOutput = rawOutput,
            labels = labels,
            inputSize = inputSize,
        )
        Log.d(TAG, "Parsed ${detections.size} detection(s) after NMS")

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

    /**
     * Releases the interpreter and GPU delegate. Idempotent — safe to call multiple times.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    /** Returns true if [initialize] has been called successfully. */
    fun isInitialized(): Boolean = interpreter != null

    /** Returns the input tensor shape, or null if not initialized. */
    fun getInputShape(): IntArray? = interpreter?.getInputTensor(0)?.shape()

    /** Returns the output tensor shape, or null if not initialized. */
    fun getOutputShape(): IntArray? = interpreter?.getOutputTensor(0)?.shape()
}
