package com.bionicmusic.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a dominant colour from album art for the dynamic mini-player tint.
 */
object ColorExtractor {

    /** Returns a dark-ish dominant colour suitable for a Vantablack UI. */
    suspend fun fromUri(context: Context, uri: Uri?): Int = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext FALLBACK
        try {
            val bmp = loadBitmap(context, uri) ?: return@withContext FALLBACK
            val palette = Palette.from(bmp).generate()
            val swatch = palette.darkVibrantSwatch
                ?: palette.darkMutedSwatch
                ?: palette.dominantSwatch
            darken(swatch?.rgb ?: FALLBACK)
        } catch (_: Exception) {
            FALLBACK
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Blend the colour heavily toward black so it stays in theme. */
    private fun darken(color: Int): Int {
        val factor = 0.45f
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }

    private val FALLBACK = Color.parseColor("#141414")
}
