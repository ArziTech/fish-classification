package com.example.fishclassification.ui.result

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fishclassification.ml.YoloDetector
import com.example.fishclassification.util.ModelInfo
import com.example.fishclassification.util.PerformanceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultViewModel(
    application: Application,
    private val imageUri: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState

    private var detector: YoloDetector? = null

    init {
        viewModelScope.launch {
            try {
                val det = YoloDetector(getApplication())
                detector = det

                withContext(Dispatchers.IO) {
                    det.initialize()
                }

                // Sample CPU before inference
                val cpuUsage = PerformanceMonitor.sampleCpuUsage(200)

                val result = det.detect(Uri.parse(imageUri))

                // Capture memory snapshot after inference
                val snapshot = PerformanceMonitor.captureMemory(
                    gpuUsed = det.usingGpu,
                    cpuUsagePercent = cpuUsage,
                )

                val modelInfo = ModelInfo(
                    inputShape = det.getInputShape()?.toList() ?: emptyList(),
                    outputShape = det.getOutputShape()?.toList() ?: emptyList(),
                )

                _uiState.value = ResultUiState.Success(
                    result = result,
                    metrics = snapshot,
                    modelInfo = modelInfo,
                )
            } catch (e: Exception) {
                _uiState.value = ResultUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
        detector = null
    }

    companion object {
        fun factory(imageUri: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ResultViewModel(application, imageUri)
            }
        }
    }
}
