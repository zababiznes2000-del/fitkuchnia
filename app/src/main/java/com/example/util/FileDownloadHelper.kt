package com.example.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast

object FileDownloadHelper {
    private const val TAG = "FileDownloadHelper"

    /**
     * Bezpieczne pobieranie pliku (np. aktualizacji APK lub dokumentu) za pomocą DownloadManager
     * z awaryjnym przejściem do domyślnej przeglądarki systemowej.
     */
    fun downloadFile(
        context: Context,
        url: String,
        suggestedFileName: String? = null,
        isApk: Boolean = false
    ) {
        val rawUrl = url.trim()
        if (rawUrl.isBlank()) {
            Toast.makeText(context, "Brak poprawnego adresu URL do pobrania.", Toast.LENGTH_SHORT).show()
            return
        }

        // Zabezpieczenie przed linkami .zip: aplikacja zawsze pobiera bezpośredni plik .apk
        val cleanUrl = if (rawUrl.endsWith(".zip", ignoreCase = true)) {
            rawUrl.substringBeforeLast(".zip") + ".apk"
        } else {
            rawUrl
        }

        try {
            val uri = Uri.parse(cleanUrl)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                Toast.makeText(context, "Nieprawidłowy protokół adresu: $cleanUrl", Toast.LENGTH_LONG).show()
                return
            }

            val rawFileName = suggestedFileName?.trim()?.ifBlank { null }
                ?: uri.lastPathSegment?.trim()?.ifBlank { null }
                ?: (if (isApk) "FitKuchnia-najnowsza.apk" else "zalacznik_fitkuchnia")

            val baseName = if (rawFileName.endsWith(".zip", ignoreCase = true)) {
                rawFileName.substringBeforeLast(".zip") + ".apk"
            } else {
                rawFileName
            }

            val finalFileName = if (isApk && !baseName.endsWith(".apk", ignoreCase = true)) {
                "$baseName.apk"
            } else baseName

            val request = DownloadManager.Request(uri).apply {
                setTitle(if (isApk) "Pobieranie aktualizacji FitKuchnia" else "Pobieranie: $finalFileName")
                setDescription("Pobieranie pliku z FitKuchnia...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)
                if (isApk || finalFileName.endsWith(".apk", ignoreCase = true)) {
                    setMimeType("application/vnd.android.package-archive")
                }
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                Toast.makeText(
                    context,
                    "Rozpoczęto pobieranie $finalFileName. Sprawdź pasek powiadomień!",
                    Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "Pomyślnie dodano do DownloadManager: $cleanUrl jako $finalFileName")
            } else {
                launchBrowserDownload(context, cleanUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "DownloadManager rzucił wyjątek, uruchamiam przeglądarkę systemową", e)
            launchBrowserDownload(context, cleanUrl)
        }
    }

    private fun launchBrowserDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Otwieram link do pobrania w przeglądarce...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas otwierania URL w przeglądarce", e)
            Toast.makeText(context, "Nie udało się otworzyć linku: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
