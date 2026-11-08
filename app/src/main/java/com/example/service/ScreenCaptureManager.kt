package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference

/**
 * Manages user-consented Screen Share capture via MediaProjection or current Activity Window snapshot,
 * encoding captured frames to JPEG Base64 for Gemini Vision multimodal analysis.
 */
object ScreenCaptureManager {

    private var activityRef: WeakReference<Activity>? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    @Volatile
    private var lastCapturedBase64Jpeg: String = ""

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    fun hasMediaProjectionConsent(): Boolean = mediaProjection != null

    /**
     * Stores the user-approved MediaProjection result from MediaProjectionManager.createScreenCaptureIntent().
     */
    fun onMediaProjectionPermissionResult(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) return
        try {
            stopMediaProjection()
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                ?: return
            val projection = mgr.getMediaProjection(resultCode, data) ?: return
            mediaProjection = projection

            val metrics = context.resources.displayMetrics
            val width = (metrics.widthPixels / 2).coerceIn(360, 720)
            val height = (metrics.heightPixels / 2).coerceIn(640, 1280)
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            virtualDisplay = projection.createVirtualDisplay(
                "MokoScreenShareDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )
        } catch (_: Exception) {
        }
    }

    fun stopMediaProjection() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
    }

    /**
     * Captures the latest screen frame as a compressed Base64 JPEG string.
     * Uses MediaProjection frame if active, or falls back to capturing the visible Activity window.
     */
    fun captureScreenBase64Jpeg(): String {
        // 1. Try MediaProjection ImageReader frame first
        val reader = imageReader
        if (reader != null) {
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * image.width
                        val bmpWidth = image.width + rowPadding / pixelStride
                        val bmp = Bitmap.createBitmap(bmpWidth, image.height, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
                        val encoded = encodeBitmapToBase64Jpeg(cropped)
                        if (encoded.isNotEmpty()) {
                            lastCapturedBase64Jpeg = encoded
                            return encoded
                        }
                    } finally {
                        image.close()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Fallback: Capture current Activity root view if in foreground
        val act = activityRef?.get()
        if (act != null && !act.isFinishing) {
            try {
                val rootView = act.window?.decorView?.rootView
                if (rootView != null && rootView.width > 0 && rootView.height > 0) {
                    val scale = 0.5f
                    val w = (rootView.width * scale).toInt().coerceAtLeast(240)
                    val h = (rootView.height * scale).toInt().coerceAtLeast(320)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.scale(scale, scale)
                    rootView.draw(canvas)
                    val encoded = encodeBitmapToBase64Jpeg(bitmap)
                    if (encoded.isNotEmpty()) {
                        lastCapturedBase64Jpeg = encoded
                        return encoded
                    }
                }
            } catch (_: Exception) {
            }
        }

        return lastCapturedBase64Jpeg
    }

    private fun encodeBitmapToBase64Jpeg(bitmap: Bitmap): String {
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }
}
