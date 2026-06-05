package com.example.fishclassification.ml

data class ModelOption(
    val assetFileName: String,
    val displayName: String,
    /** Whether this model can run on GPU delegate. INT8/QAT models are GPU-incompatible. */
    val supportsGpu: Boolean = true,
)

object ModelCatalog {
    val options: List<ModelOption> = listOf(
        ModelOption("yolov11_fp32.tflite", "YOLOv11 FP32"),
        ModelOption("yolov11_fp16.tflite", "YOLOv11 FP16"),
        ModelOption("yolov11_int8.tflite", "YOLOv11 INT8", supportsGpu = false),
        ModelOption("yolov11_qat.tflite", "YOLOv11 QAT", supportsGpu = false),
        ModelOption("yolov11n_dynamic_range_quant.tflite", "YOLOv11n Dynamic Range"),
    )

    val default: ModelOption = options.first()
}
