package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageStorageHelper {

    fun copyUriToAppStorage(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "recipe_photos").apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, "recipe_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToAppStorage(context: Context, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "recipe_photos").apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, "recipe_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveDrawableResourceToAppStorage(context: Context, drawableResId: Int): String? {
        return try {
            val bitmap = BitmapFactory.decodeResource(context.resources, drawableResId)
            if (bitmap != null) {
                saveBitmapToAppStorage(context, bitmap)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Koduje plik ze wskazanej ścieżki do skompresowanego ciągu Base64 (JPEG, max 800px),
     * dzięki czemu zdjęcie może być bezpośrednio zsynchronizowane przez Firestore bez utraty oryginału.
     */
    fun encodeFileToBase64(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null

            // Odczytaj wymiary
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val maxDim = maxOf(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 900) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            // Ewentualne dodatkowe przeskalowanie
            val scaledBitmap = if (bitmap.width > 800 || bitmap.height > 800) {
                val ratio = minOf(800f / bitmap.width, 800f / bitmap.height)
                val newW = (bitmap.width * ratio).toInt()
                val newH = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Zapisuje zakodowany ciąg Base64 do trwałego pliku na urządzeniu i zwraca jego ścieżkę absolutną.
     */
    fun decodeBase64ToFile(context: Context, base64: String?, recipeId: String): String? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val dir = File(context.filesDir, "recipe_photos").apply {
                if (!exists()) mkdirs()
            }
            val sanitizedId = recipeId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "recipe_${sanitizedId}.jpg")
            FileOutputStream(file).use { fos ->
                fos.write(bytes)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
