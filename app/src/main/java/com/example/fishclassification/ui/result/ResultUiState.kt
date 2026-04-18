package com.example.fishclassification.ui.result

import com.example.fishclassification.ml.InferenceResult
import com.example.fishclassification.util.ModelInfo
import com.example.fishclassification.util.PerformanceSnapshot

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data class Success(
        val result: InferenceResult,
        val metrics: PerformanceSnapshot,
        val modelInfo: ModelInfo,
    ) : ResultUiState
    data class Error(val message: String) : ResultUiState
}
