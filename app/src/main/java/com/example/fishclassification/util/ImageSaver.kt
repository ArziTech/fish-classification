package com.example.fishclassification.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.fishclassification.ml.InferenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageSaver {

    private const val TAG = "ImageSaver"
    private const val PREFS_NAME = "fish_saver_prefs"
    private const val KEY_COUNTER = "inference_counter"
    private const val FOLDER_NAME = "FishClassification"
    private const val JPEG_QUALITY = 95

    private fun nextSequence(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_COUNTER, 1)
        prefs.edit().putInt(KEY_COUNTER, current + 1).apply()
        return current
    }

    private fun buildTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun sequenceTag(seq: Int): String = seq.toString().padStart(5, '0')

    suspend fun saveInferencePair(
        context: Context,
        sourceUri: Uri,
        result: InferenceResult,
        modelName: String,
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val seq = nextSequence(context)
            val seqTag = sequenceTag(seq)
            val ts = buildTimestamp()

            val rawName = "fish_${seqTag}_${ts}_raw.jpg"
            val annotatedName = "fish_${seqTag}_${ts}_result.jpg"

            val rawSaved = saveRaw(context, sourceUri, rawName)
            val annotatedSaved = saveAnnotated(context, sourceUri, result, modelName, seqTag, annotatedName)

            if (rawSaved && annotatedSaved) {
                AppLogger.i(TAG, "Saved pair: $rawName | $annotatedName")
                Pair(rawName, annotatedName)
            } else {
                AppLogger.w(TAG, "Partial save failure: raw=$rawSaved annotated=$annotatedSaved")
                null
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "saveInferencePair failed", t)
            null
        }
    }

    private fun saveRaw(context: Context, sourceUri: Uri, fileName: String): Boolean {
        return try {
            openOutputStream(context, fileName)?.use { output ->
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    input.copyTo(output)
                } ?: return false
            } ?: return false
            true
        } catch (t: Throwable) {
            AppLogger.e(TAG, "saveRaw failed for $fileName", t)
            false
        }
    }

    private fun saveAnnotated(
        context: Context,
        sourceUri: Uri,
        result: InferenceResult,
        modelName: String,
        seqTag: String,
        fileName: String,
    ): Boolean {
        return try {
            val rawBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }?.copy(Bitmap.Config.ARGB_8888, false) ?: return false

            val srcWidth = rawBitmap.width
            val srcHeight = rawBitmap.height
            val panelHeight = (srcWidth * 0.28f).toInt()
            val totalHeight = srcHeight + panelHeight
            val annotated = Bitmap.createBitmap(srcWidth, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(annotated)

            // Draw original image at top
            canvas.drawBitmap(rawBitmap, 0f, 0f, null)
            rawBitmap.recycle()

            // Draw dark info panel
            val panelPaint = Paint().apply { color = Color.parseColor("#1A237E") }
            canvas.drawRect(
                0f,
                srcHeight.toFloat(),
                annotated.width.toFloat(),
                totalHeight.toFloat(),
                panelPaint,
            )

            val panelTop = srcHeight.toFloat()
            val paddingH = annotated.width * 0.04f
            val paddingV = panelHeight * 0.12f

            // className — large bold white
            val classNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textSize = panelHeight * 0.32f
            }
            canvas.drawText(
                result.className,
                paddingH,
                panelTop + paddingV + classNamePaint.textSize,
                classNamePaint,
            )

            // Confidence — medium white
            val confidencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT
                textSize = panelHeight * 0.20f
            }
            val confidenceText = "Confidence: ${"%.1f".format(result.confidence * 100)}%"
            canvas.drawText(
                confidenceText,
                paddingH,
                panelTop + paddingV + classNamePaint.textSize + confidencePaint.textSize * 1.4f,
                confidencePaint,
            )

            // Inference time + model name — small gray
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B0BEC5")
                typeface = Typeface.DEFAULT
                textSize = panelHeight * 0.15f
            }
            val metaText = "${result.inferenceTimeMs} ms  •  $modelName"
            canvas.drawText(
                metaText,
                paddingH,
                panelTop + paddingV + classNamePaint.textSize + confidencePaint.textSize * 1.4f + metaPaint.textSize * 1.6f,
                metaPaint,
            )

            // Sequence number — small gray, bottom-right
            val seqPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#78909C")
                typeface = Typeface.DEFAULT
                textSize = panelHeight * 0.14f
                textAlign = Paint.Align.RIGHT
            }
            val seqText = "#$seqTag"
            canvas.drawText(
                seqText,
                annotated.width - paddingH,
                totalHeight - paddingV,
                seqPaint,
            )

            openOutputStream(context, fileName)?.use { output ->
                annotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            } ?: run {
                annotated.recycle()
                return false
            }
            annotated.recycle()
            true
        } catch (t: Throwable) {
            AppLogger.e(TAG, "saveAnnotated failed for $fileName", t)
            false
        }
    }

    private fun openOutputStream(context: Context, fileName: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$FOLDER_NAME")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            val stream = resolver.openOutputStream(uri) ?: return null

            // Wrap to clear IS_PENDING on close
            object : OutputStream() {
                override fun write(b: Int) = stream.write(b)
                override fun write(b: ByteArray) = stream.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
                override fun flush() = stream.flush()
                override fun close() {
                    stream.close()
                    val update = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, update, null, null)
                }
            }
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                FOLDER_NAME,
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            val stream = FileOutputStream(file)
            object : OutputStream() {
                override fun write(b: Int) = stream.write(b)
                override fun write(b: ByteArray) = stream.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = stream.write(b, off, len)
                override fun flush() = stream.flush()
                override fun close() {
                    stream.close()
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null,
                    )
                }
            }
        }
    }
}
