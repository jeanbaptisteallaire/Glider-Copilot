package com.neutronstar.glidy.myflights

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * V21 (comme iOS) : photo du pilote, fichier local de l'app, jamais envoyée en ligne. Recadrée à 512 px au plus.
 */
internal object PilotPhoto {
    private fun file(context: Context) = File(context.filesDir, "pilot-photo.jpg")

    fun load(context: Context): Bitmap? = runCatching {
        file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }.getOrNull()

    /** Copie l'image choisie (réduite) dans le fichier local ; null si elle est illisible. */
    fun save(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp
    }.getOrNull()
}
