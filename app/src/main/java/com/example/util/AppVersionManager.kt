package com.example.util

import android.content.Context
import android.util.Log
import coil.Coil
import com.example.BuildConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

object AppVersionManager {
    private const val TAG = "AppVersionManager"
    private const val PREFS_VERSION = "fitkuchnia_version_tracking"
    private const val KEY_LAST_VERSION_CODE = "last_installed_version_code"
    private const val KEY_LAST_VERSION_NAME = "last_installed_version_name"
    private const val KEY_LAST_BUILD_TIME = "last_installed_build_time"

    const val COLLECTION_APP_CONFIG = "app_config"
    const val DOC_LATEST_VERSION = "latest_version"

    // Domyślny stabilny adres pobierania APK w środowisku produkcyjnym/deweloperskim
    const val DEFAULT_APK_DOWNLOAD_URL = "https://ais-dev-nwb3quwcyda62q7l65zm63-901459497383.europe-west2.run.app/FitKuchnia.apk"

    data class RemoteVersionInfo(
        val versionCode: Long = 0L,
        val versionName: String = "",
        val apkUrl: String = "",
        val releaseNotes: String = "",
        val isMandatory: Boolean = false,
        val updatedAtMillis: Long = 0L
    )

    sealed class AppUpdateStatus {
        object Checking : AppUpdateStatus()
        data class UpToDate(val versionName: String, val versionCode: Int) : AppUpdateStatus()
        data class UpdateAvailable(
            val currentVersionName: String,
            val currentVersionCode: Int = BuildConfig.VERSION_CODE,
            val newVersionName: String,
            val newVersionCode: Long,
            val apkUrl: String,
            val releaseNotes: String
        ) : AppUpdateStatus()
        data class Error(val error: String) : AppUpdateStatus()
    }

    data class VersionCheckResult(
        val isFirstLaunch: Boolean,
        val isNewVersion: Boolean,
        val oldVersionCode: Int,
        val newVersionCode: Int,
        val currentVersionName: String,
        val buildTimeString: String
    )

    /**
     * Sprawdza wersję przy każdym uruchomieniu aplikacji.
     * Jeśli wykryto nową instalację (wyższy lub inny versionCode):
     * - Czyści pamięć podręczną (cacheDir, Coil image cache)
     * - Wymusza inwalidację przestarzałych danych
     * - Aktualizuje zapisany numer wersji
     */
    fun checkAndHandleAppUpgrade(context: Context): VersionCheckResult {
        val prefs = context.getSharedPreferences(PREFS_VERSION, Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)
        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME
        val currentBuildTime = try { BuildConfig.BUILD_TIME_STRING } catch (_: Throwable) { "Brak danych" }

        val isFirstLaunch = lastVersionCode == -1
        val isNewVersion = !isFirstLaunch && (lastVersionCode != currentVersionCode)

        if (isFirstLaunch || isNewVersion) {
            Log.i(TAG, "Wykryto aktualizację aplikacji: stary versionCode=$lastVersionCode, nowy=$currentVersionCode ($currentVersionName)")
            try {
                // 1. Wyczyść tymczasową pamięć podręczną aplikacji (cacheDir)
                clearAppDiskCache(context)

                // 2. Wyczyść pamięć podręczną biblioteki Coil
                clearCoilImageCache(context)

                // 3. Trwale usuń wycofane przepisy bota i dodaj ich ID do listy zablokowanych
                com.example.data.local.AppRecipeStorage.DEPRECATED_BOT_RECIPE_IDS.forEach { id ->
                    com.example.data.local.AppRecipeStorage.markRecipeDeleted(context, id)
                }
                val cleanedRecipes = com.example.data.local.AppRecipeStorage.loadSavedRecipes(context)
                com.example.data.local.AppRecipeStorage.saveAllRecipes(context, cleanedRecipes)

                // 4. Zapisz nową wersję
                prefs.edit()
                    .putInt(KEY_LAST_VERSION_CODE, currentVersionCode)
                    .putString(KEY_LAST_VERSION_NAME, currentVersionName)
                    .putString(KEY_LAST_BUILD_TIME, currentBuildTime)
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Błąd podczas procedury aktualizacji wersji", e)
            }
        }

        return VersionCheckResult(
            isFirstLaunch = isFirstLaunch,
            isNewVersion = isNewVersion,
            oldVersionCode = lastVersionCode,
            newVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            buildTimeString = currentBuildTime
        )
    }

    /**
     * Całkowite czyszczenie pamięci podręcznej (Cache / Storage) na żądanie użytkownika.
     */
    fun clearAppDiskCache(context: Context): Boolean {
        return try {
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.isDirectory) {
                deleteDirContents(cacheDir)
            }
            val externalCache = context.externalCacheDir
            if (externalCache != null && externalCache.isDirectory) {
                deleteDirContents(externalCache)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Błąd czyszczenia cacheDir", e)
            false
        }
    }

    private fun deleteDirContents(dir: File): Boolean {
        val files = dir.listFiles() ?: return true
        var success = true
        for (file in files) {
            if (file.isDirectory) {
                success = success && deleteDirContents(file)
            }
            success = success && file.delete()
        }
        return success
    }

    /**
     * Wymusza wyczyszczenie cache obrazków (Coil)
     */
    fun clearCoilImageCache(context: Context) {
        try {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Throwable) {
            Log.w(TAG, "Nie udało się zresetować cache Coil: ${e.message}")
        }
    }

    fun getAppVersionInfo(): String {
        val buildTime = try { BuildConfig.BUILD_TIME_STRING } catch (_: Throwable) { "" }
        return "FitKuchnia v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})" +
                if (buildTime.isNotBlank()) " • $buildTime" else ""
    }

    /**
     * Sprawdza status wersji aplikacji w Firebase Firestore w węźle app_config/latest_version.
     * Porównuje numer wersji aplikacji z kodem (BuildConfig.VERSION_CODE / VERSION_NAME)
     * z najnowszą wersją zapisaną w chmurze.
     */
    fun checkRemoteVersionStatus(onStatus: (AppUpdateStatus) -> Unit) {
        onStatus(AppUpdateStatus.Checking)
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection(COLLECTION_APP_CONFIG)
                .document(DOC_LATEST_VERSION)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        // Dokument jeszcze nie został zainicjalizowany w Firebase - uznajemy bieżącą wersję za aktualną
                        Log.d(TAG, "Dokument app_config/latest_version nie istnieje w Firebase. Uznaję v${BuildConfig.VERSION_NAME} za aktualną.")
                        onStatus(AppUpdateStatus.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                        return@addOnSuccessListener
                    }

                    val remoteVersionCode = doc.getLong("versionCode") ?: 0L
                    val remoteVersionName = doc.getString("versionName")?.trim().orEmpty()
                    val remoteApkUrl = doc.getString("apkUrl")?.trim().let {
                        if (it.isNullOrBlank()) DEFAULT_APK_DOWNLOAD_URL else it
                    }
                    val releaseNotes = doc.getString("releaseNotes")?.trim().orEmpty()

                    val currentCode = BuildConfig.VERSION_CODE.toLong()
                    val currentName = BuildConfig.VERSION_NAME.trim()

                    val isNewerCode = remoteVersionCode > currentCode
                    val isNewerSemver = compareSemver(remoteVersionName, currentName) > 0

                    if (isNewerCode || isNewerSemver) {
                        Log.i(TAG, "Dostępna nowa wersja: $remoteVersionName ($remoteVersionCode) > lokalna: $currentName ($currentCode)")
                        onStatus(
                            AppUpdateStatus.UpdateAvailable(
                                currentVersionName = currentName,
                                newVersionName = if (remoteVersionName.isNotBlank()) remoteVersionName else "Nowsza",
                                newVersionCode = remoteVersionCode,
                                apkUrl = remoteApkUrl,
                                releaseNotes = releaseNotes
                            )
                        )
                    } else {
                        Log.d(TAG, "Aplikacja jest aktualna: v$currentName ($currentCode)")
                        onStatus(AppUpdateStatus.UpToDate(currentName, BuildConfig.VERSION_CODE))
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Błąd pobierania app_config/latest_version: ${e.message}")
                    // W razie braku połączenia internetowego, uznajemy bieżącą wersję za stabilną
                    onStatus(AppUpdateStatus.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Wyjątek w checkRemoteVersionStatus", e)
            onStatus(AppUpdateStatus.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
        }
    }

    /**
     * Publikacja nowej wersji przez Administratora do Firebase Firestore
     */
    fun publishVersionToFirebase(
        versionName: String = BuildConfig.VERSION_NAME,
        versionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        apkUrl: String = DEFAULT_APK_DOWNLOAD_URL,
        releaseNotes: String = "Najnowsze poprawki stabilności i wydajności",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val cleanApkUrl = if (apkUrl.trim().endsWith(".zip", ignoreCase = true)) {
                apkUrl.trim().substringBeforeLast(".zip") + ".apk"
            } else {
                apkUrl.trim()
            }
            val data = hashMapOf(
                "versionName" to versionName.trim(),
                "versionCode" to versionCode,
                "apkUrl" to cleanApkUrl,
                "releaseNotes" to releaseNotes.trim(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedAtMillis" to System.currentTimeMillis()
            )

            db.collection(COLLECTION_APP_CONFIG)
                .document(DOC_LATEST_VERSION)
                .set(data)
                .addOnSuccessListener {
                    Log.d(TAG, "Pomyślnie zaktualizowano wersję aplikacji w Firebase: v$versionName ($versionCode)")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Błąd zapisu wersji w Firebase", e)
                    onError("Błąd zapisu wersji: ${e.localizedMessage ?: "brak połączenia"}")
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Wyjątek w publishVersionToFirebase", e)
            onError("Błąd: ${e.localizedMessage}")
        }
    }

    /**
     * Pomocnicze porównywanie wersji semantycznych (np. "1.3.2" vs "1.3.0")
     * Zwraca > 0 jeśli v1 > v2, 0 jeśli równe, < 0 jeśli v1 < v2
     */
    fun compareSemver(v1: String, v2: String): Int {
        try {
            val parts1 = v1.replace("[^0-9.]".toRegex(), "").split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.replace("[^0-9.]".toRegex(), "").split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) {
                    return p1.compareTo(p2)
                }
            }
            return 0
        } catch (_: Exception) {
            return 0
        }
    }
}
