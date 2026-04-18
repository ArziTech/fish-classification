package com.example.fishclassification.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The camera permission constant.
 */
val CAMERA_PERMISSION: String = Manifest.permission.CAMERA

/**
 * Returns the correct storage read permission based on the API level.
 * On API 33 (Android 13) and above, uses READ_MEDIA_IMAGES.
 * On older versions, uses READ_EXTERNAL_STORAGE.
 */
fun storagePermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

/**
 * Returns true if the given permission has been granted.
 */
fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * Opens the app's settings page so the user can manually grant permissions
 * that were permanently denied.
 */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
