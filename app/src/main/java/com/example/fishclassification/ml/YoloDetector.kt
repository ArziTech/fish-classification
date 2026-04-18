package com.example.fishclassification.ml

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

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

        // Read input size from the model's input tensor shape: [1, H, W, C]
        val inputShape = interp.getInputTensor(0).shape()
        // Shape is typically [1, 640, 640, 3]; use index 1 as height/width
        inputSize = if (inputShape.size >= 3) inputShape[1] else 640

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
            // Allocate output buffer based on model's output tensor shape
            val outputShape = interp.getOutputTensor(0).shape()
            // Typical shape: [1, 4+nc, num_anchors] — allocate a nested FloatArray
            val dim1 = outputShape[1]
            val dim2 = outputShape[2]
            val rawOutput = Array(1) { Array(dim1) { FloatArray(dim2) } }

            // Time only the inference call
            val startNs = System.nanoTime()
            interp.run(inputBuffer, rawOutput)
            val inferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000L

            // Parse detections
            val detections = PostProcessor.parseDetections(
                rawOutput = rawOutput,
                labels = labels,
                inputSize = inputSize,
            )

            val top = PostProcessor.pickTopResult(detections)

            if (top != null) {
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
