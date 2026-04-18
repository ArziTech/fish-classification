package com.example.fishclassification.ml

import android.graphics.RectF

data class InferenceResult(
    val className: String,
    val confidence: Float,
    val inferenceTimeMs: Long,
    val allDetections: List<Detection> = emptyList(),
)

data class Detection(
    val classIndex: Int,
    val className: String,
    val confidence: Float,
    val boundingBox: RectF,
)
