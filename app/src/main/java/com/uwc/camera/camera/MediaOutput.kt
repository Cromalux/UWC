package com.uwc.camera.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tout part dans DCIM/UWC via MediaStore ; RAW et JPEG d'une même prise partagent le nom de base. */
object MediaOutput {
    const val RELATIVE_DIR = "DCIM/UWC"

    fun baseName(): String =
        "UWC_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private fun values(name: String, mime: String) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
    }

    fun jpeg(resolver: ContentResolver, base: String): ImageCapture.OutputFileOptions =
        ImageCapture.OutputFileOptions.Builder(
            resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values("$base.jpg", "image/jpeg"),
        ).build()

    fun dng(resolver: ContentResolver, base: String): ImageCapture.OutputFileOptions =
        ImageCapture.OutputFileOptions.Builder(
            resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values("$base.dng", "image/x-adobe-dng"),
        ).build()

    fun video(resolver: ContentResolver, base: String): MediaStoreOutputOptions =
        MediaStoreOutputOptions.Builder(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values("$base.mp4", "video/mp4"))
            .build()
}
