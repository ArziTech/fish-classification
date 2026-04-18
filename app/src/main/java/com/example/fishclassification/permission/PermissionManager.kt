package com.example.fishclassification.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Holds the current state of a single permission.
 *
 * @param granted Whether the permission is currently granted.
 * @param shouldShowRationale Whether the system recommends showing a rationale to the user.
 * @param request Lambda to trigger the permission request.
 */
data class PermissionState(
    val granted: Boolean,
    val shouldShowRationale: Boolean,
    val request: () -> Unit,
)

/**
 * Holds the combined state of multiple permissions.
 *
 * @param allGranted True when every permission in the list is granted.
 * @param permissions Map of each permission string to its individual [PermissionState].
 * @param requestAll Lambda to request all permissions at once.
 */
data class MultiplePermissionsState(
    val allGranted: Boolean,
    val permissions: Map<String, PermissionState>,
    val requestAll: () -> Unit,
)

/**
 * Walks the [ContextWrapper] chain to find the underlying [Activity].
 * Returns null if no Activity is found (e.g. inside a Service or test).
 */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * A composable that tracks and requests a single runtime permission.
 *
 * The granted state is re-evaluated every time the app returns to the foreground
 * (ON_RESUME), so changes made in the system Settings screen are reflected
 * automatically.
 *
 * @param permission The permission string (e.g. [CAMERA_PERMISSION]).
 * @return A [PermissionState] reflecting the current status.
 */
@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    val activity = context.findActivity()

    var granted by remember {
        mutableStateOf(isPermissionGranted(context, permission))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
    }

    // Re-check on every ON_RESUME so returning from Settings updates the state.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = isPermissionGranted(context, permission)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val shouldShowRationale = remember(granted) {
        activity?.shouldShowRequestPermissionRationale(permission) ?: false
    }

    return PermissionState(
        granted = granted,
        shouldShowRationale = shouldShowRationale,
        request = { launcher.launch(permission) },
    )
}

/**
 * A composable that tracks and requests multiple runtime permissions at once.
 *
 * @param permissions List of permission strings to manage.
 * @return A [MultiplePermissionsState] reflecting the combined status.
 */
@Composable
fun rememberMultiplePermissionsState(permissions: List<String>): MultiplePermissionsState {
    val context = LocalContext.current
    val activity = context.findActivity()

    var grantedMap by remember {
        mutableStateOf(
            permissions.associateWith { isPermissionGranted(context, it) }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        grantedMap = grantedMap + result
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                grantedMap = permissions.associateWith { isPermissionGranted(context, it) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val permissionStates = permissions.associateWith { permission ->
        val isGranted = grantedMap[permission] ?: false
        PermissionState(
            granted = isGranted,
            shouldShowRationale = activity?.shouldShowRequestPermissionRationale(permission) ?: false,
            request = { launcher.launch(arrayOf(permission)) },
        )
    }

    return MultiplePermissionsState(
        allGranted = grantedMap.values.all { it },
        permissions = permissionStates,
        requestAll = { launcher.launch(permissions.toTypedArray()) },
    )
}

/**
 * A Material3 dialog that explains why a permission is needed and offers
 * the user a chance to grant it or dismiss.
 *
 * @param title Short title shown in the dialog header.
 * @param message Rationale message explaining why the permission is required.
 * @param onConfirm Called when the user taps the confirm / grant button.
 * @param onDismiss Called when the user taps the dismiss / cancel button.
 */
@Composable
fun PermissionRationaleDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Dismiss")
            }
        },
    )
}

/**
 * Dialog shown when a permission has been permanently denied (user checked
 * "Don't ask again"). Offers a button that opens the app's system settings
 * page so the user can re-enable the permission manually.
 */
@Composable
fun PermissionPermanentlyDeniedDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = {
                openAppSettings(context)
                onDismiss()
            }) {
                Text(text = "Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
