package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.WindowManager

/**
 * Zarządza trybem kuchennym ("Ekran nie wygasa") oraz stałym podświetleniem ekranu.
 * Zapobiega wygaszaniu wyświetlacza telefonu podczas gotowania i korzystania z FitKuchnia,
 * dzięki czemu użytkownik nie musi dotykać telefonu brudnymi rękami!
 *
 * Zgodnie z wytycznymi użytkownika: aplikacja NIGDY nie wylogowuje automatycznie użytkownika.
 */
object AppSessionManager {
    private const val TAG = "ScreenWakeManager"
    private const val PREFS_NAME = "fitkuchnia_screen_prefs"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_kitchen_mode"

    /**
     * Sprawdza, czy funkcja "Ekran nie wygasa" jest włączona.
     * Domyślnie: PRAWDA (true) - ekran w kuchni nie powinien nigdy gasnąć!
     */
    fun isKeepScreenOnEnabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Włącza lub wyłącza blokadę wygaszania ekranu.
     */
    fun setKeepScreenOnEnabled(context: Context, enabled: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
            Log.i(TAG, "Ustawiono tryb 'Ekran nie wygasa': $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu preferencji ekranu", e)
        }
    }

    /**
     * Stosuje flagę FLAG_KEEP_SCREEN_ON do bieżącego okna Activity.
     */
    fun applyScreenWakeLock(activity: Activity, keepOn: Boolean) {
        try {
            if (keepOn) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd stosowania flagi FLAG_KEEP_SCREEN_ON", e)
        }
    }

    // Bezpieczne metody kompatybilności wstecznej - wygasanie sesji i wylogowywanie jest całkowicie wyłączone:
    fun isSessionExpired(context: Context): Boolean = false
    fun recordActivity(context: Context) {}
    fun markSessionExpired(context: Context, reason: String) {}
    fun consumeSessionExpiredReason(context: Context): String? = null
    fun clearSession(context: Context) {}
    fun startNewSession(context: Context) {}
    fun getRemainingSeconds(context: Context): Long = 86400L
    fun getTimeoutMinutes(context: Context): Int = 0
    fun setTimeoutMinutes(context: Context, minutes: Int): Int = minutes
    const val WARNING_THRESHOLD_SECONDS = 0L
}
