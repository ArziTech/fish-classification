package com.example.fishclassification.ml

data class ModelOption(
    val assetFileName: String,
    val displayName: String,
)

object ModelCatalog {
    val options: List<ModelOption> = listOf(
        ModelOption("model_11 fp32.tflite", "Model 11 FP32"),
        ModelOption("yolov11_fp16.tflite", "YOLOv11 FP16"),
        ModelOption("model_11 int8.tflite", "Model 11 INT8"),
        ModelOption("model_11 qat.tflite", "Model 11 QAT"),
        ModelOption("yolov11n_dynamic_range_quant.tflite", "YOLOv11n Dynamic Range"),
    )

    val default: ModelOption = options.first()
}
