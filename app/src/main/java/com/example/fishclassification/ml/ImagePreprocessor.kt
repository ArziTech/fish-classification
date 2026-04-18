package com.example.fishclassification.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImagePreprocessor(private val inputSize: Int) {

    companion object {
        init {
            val loaded = OpenCVLoader.initLocal()
            if (!loaded) {
                throw IllegalStateException("Failed to initialize OpenCV")
            }
        }
    }

    /**
     * Decodes the given [uri] to a Bitmap, runs OpenCV preprocessing
     * (RGBA→RGB, resize to [inputSize]x[inputSize], normalize to [0,1]),
     * and packs the result into a direct [ByteBuffer] in NHWC layout (Float32).
     */
    fun preprocess(context: Context, uri: Uri): ByteBuffer {
        // Step 1: Decode URI to ARGB_8888 software bitmap
        val rawBitmap = decodeBitmap(context, uri)
        val bitmap = ensureArgb8888Software(rawBitmap)

        // Step 2: Bitmap → Mat (RGBA)
        val rgbaMat = Mat()
        Utils.bitmapToMat(bitmap, rgbaMat)

        // Step 3: RGBA → RGB
        val rgbMat = Mat()
        Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
        rgbaMat.release()

        // Step 4: Resize to inputSize × inputSize
        val resizedMat = Mat()
        Imgproc.resize(rgbMat, resizedMat, Size(inputSize.toDouble(), inputSize.toDouble()))
        rgbMat.release()

        // Step 5: Convert to CV_32FC3 + normalize pixel values to [0, 1]
        val floatMat = Mat()
        resizedMat.convertTo(floatMat, CvType.CV_32FC3, 1.0 / 255.0)
        resizedMat.release()

        // Step 6: Write to direct ByteBuffer (NHWC: H=inputSize, W=inputSize, C=3, FLOAT32)
        val bufferSize = inputSize * inputSize * 3 * 4 // 4 bytes per float
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }

        val floatArray = FloatArray(inputSize * inputSize * 3)
        floatMat.get(0, 0, floatArray)
        floatMat.release()

        for (v in floatArray) {
            byteBuffer.putFloat(v)
        }

        // Return with position at start (caller may rewind if reusing)
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun ensureArgb8888Software(bitmap: Bitmap): Bitmap {
        // If already ARGB_8888 and mutable, just return it
        if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.isHardwareAccelerated) {
            return bitmap
        }
        // Create a software-backed ARGB_8888 copy
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (copy !== bitmap) {
            bitmap.recycle()
        }
        return copy
    }

    // Bitmap.isHardwareAccelerated doesn't exist; use config check instead
    private val Bitmap.isHardwareAccelerated: Boolean
        get() = config == Bitmap.Config.HARDWARE
}
