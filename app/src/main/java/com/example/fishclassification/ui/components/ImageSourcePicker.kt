package com.example.fishclassification.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.fishclassification.permission.CAMERA_PERMISSION
import com.example.fishclassification.permission.PermissionPermanentlyDeniedDialog
import com.example.fishclassification.permission.PermissionRationaleDialog
import com.example.fishclassification.permission.rememberPermissionState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourcePicker(
    onImageSelected: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Camera permission state
    val cameraPermission = rememberPermissionState(CAMERA_PERMISSION)

    // Track dialog visibility separately to handle rationale / permanently denied cases
    var showRationale by remember { mutableStateOf(false) }
    var showPermanentlyDenied by remember { mutableStateOf(false) }

    // Temp URI for camera capture — held in state so the launcher callback can reference it
    var cameraTargetUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery: PhotoPicker (PickVisualMedia) — no storage permission needed.
    // The modern Android PhotoPicker is a separate process with built-in access to media,
    // so READ_MEDIA_IMAGES is NOT required. This is the correct modern approach (API 21+
    // via Jetpack backport).
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImageSelected(uri)
    }

    // Camera: TakePicture writes to a FileProvider URI
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraTargetUri?.let { onImageSelected(it) }
        }
    }

    fun launchCamera() {
        val imageDir = File(context.cacheDir, "images").also { it.mkdirs() }
        val tempFile = File(imageDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile,
        )
        cameraTargetUri = uri
        cameraLauncher.launch(uri)
    }

    fun handleCameraClick() {
        when {
            cameraPermission.granted -> launchCamera()
            cameraPermission.shouldShowRationale -> showRationale = true
            else -> {
                // Not granted, rationale not needed — direct request.
                // If the user permanently denied, the launcher will return false and
                // next click will hit shouldShowRationale == false again; we show the
                // permanently-denied dialog then.
                cameraPermission.request()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(text = "Select Image Source")
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { handleCameraClick() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Camera")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Gallery")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showRationale) {
        PermissionRationaleDialog(
            title = "Camera Permission Required",
            message = "The camera permission is needed to capture a photo of the fish for classification.",
            onConfirm = {
                showRationale = false
                cameraPermission.request()
            },
            onDismiss = { showRationale = false },
        )
    }

    if (showPermanentlyDenied) {
        PermissionPermanentlyDeniedDialog(
            title = "Camera Permission Denied",
            message = "Camera permission was permanently denied. Please enable it in Settings to use the camera.",
            onDismiss = { showPermanentlyDenied = false },
        )
    }
}
