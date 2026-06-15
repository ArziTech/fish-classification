package com.example.fishclassification.ui.result

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fishclassification.ml.ModelCatalog
import com.example.fishclassification.ml.YoloDetector
import com.example.fishclassification.util.AppLogger
import com.example.fishclassification.util.ImageSaver
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
    private val modelAsset: String,
    private val useGpu: Boolean,
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
                    det.initialize(modelAsset = modelAsset, useGpu = useGpu)
                }

                val cpuUsage = PerformanceMonitor.sampleCpuUsage(200)

                val result = det.detect(Uri.parse(imageUri))

                val modelName = ModelCatalog.options
                    .find { it.assetFileName == modelAsset }?.displayName ?: modelAsset

                val savedFiles = withContext(Dispatchers.IO) {
                    ImageSaver.saveInferencePair(
                        context = getApplication(),
                        sourceUri = Uri.parse(imageUri),
                        result = result,
                        modelName = modelName,
                    )
                }

                val snapshot = PerformanceMonitor.captureMemory(
                    requestedGpu = useGpu,
                    gpuActive = det.usingGpu,
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
                    modelName = modelName,
                    savedFiles = savedFiles,
                )
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Inference pipeline failed for uri=$imageUri", t)
                val msg = "${t.javaClass.simpleName}: ${t.message ?: "(no message)"}"
                _uiState.value = ResultUiState.Error(msg)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
        detector = null
    }

    companion object {
        private const val TAG = "ResultViewModel"

        fun factory(imageUri: String, modelAsset: String, useGpu: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ResultViewModel(application, imageUri, modelAsset, useGpu)
            }
        }
    }
}
