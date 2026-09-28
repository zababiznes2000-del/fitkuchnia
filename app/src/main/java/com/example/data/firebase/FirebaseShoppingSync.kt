package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.DayOfWeekPolish
import com.example.data.PlannedMeal
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject

/**
 * Odpowiada za prywatną i trwałą listę zakupów / zaplanowanych posiłków dla każdego użytkownika.
 * Zapisuje i odczytuje dane z Firebase Firestore pod ścieżką:
 * users/{userId}/shoppingList/current
 * Zapewnia całkowitą prywatność: użytkownik A nie widzi listy użytkownika B.
 */
object FirebaseShoppingSync {
    private const val TAG = "FirebaseShoppingSync"
    private const val PREFS_SHOPPING_PREFIX = "fitkuchnia_shopping_user_"

    private fun normalizeUserId(email: String): String {
        return email.lowercase().trim().replace(".", "_").replace("@", "_at_")
    }

    /**
     * Wczytuje lokalnie zapisaną prywatną listę zakupów danego użytkownika z pamięci aplikacji.
     */
    fun loadLocalShoppingData(context: Context, userEmail: String): Pair<List<PlannedMeal>, Set<String>> {
        val userKey = normalizeUserId(userEmail)
        val prefs = context.getSharedPreferences(PREFS_SHOPPING_PREFIX + userKey, Context.MODE_PRIVATE)
        val mealsJson = prefs.getString("planned_meals", null)
        val checkedJson = prefs.getString("checked_ids", null)

        val meals = mutableListOf<PlannedMeal>()
        if (!mealsJson.isNullOrBlank()) {
            try {
                val array = JSONArray(mealsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val dayStr = obj.optString("day", "MON")
                    val day = try { DayOfWeekPolish.valueOf(dayStr) } catch (_: Exception) { DayOfWeekPolish.MON }
                    val recipeId = obj.optString("recipeId", "")
                    val recipeTitle = obj.optString("recipeTitle", "")
                    val portions = obj.optInt("portions", 2).coerceIn(1, 24)
                    if (recipeId.isNotBlank()) {
                        meals.add(
                            PlannedMeal(
                                id = id.ifEmpty { java.util.UUID.randomUUID().toString() },
                                day = day,
                                recipeId = recipeId,
                                recipeTitle = recipeTitle.ifBlank { "Danie" },
                                portions = portions
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Błąd parsowania lokalnych planned_meals", e)
            }
        }

        val checked = mutableSetOf<String>()
        if (!checkedJson.isNullOrBlank()) {
            try {
                val array = JSONArray(checkedJson)
                for (i in 0 until array.length()) {
                    checked.add(array.getString(i))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Błąd parsowania lokalnych checked_ids", e)
            }
        }

        return Pair(meals, checked)
    }

    /**
     * Zapisuje prywatną listę zakupów lokalnie oraz asynchronicznie w Firestore.
     */
    fun saveShoppingData(
        context: Context,
        userEmail: String,
        plannedMeals: List<PlannedMeal>,
        checkedIds: Set<String>
    ) {
        if (userEmail.isBlank()) return
        val userKey = normalizeUserId(userEmail)

        // 1. Zapis lokalny (SharedPreferences)
        try {
            val mealsArray = JSONArray()
            plannedMeals.forEach { meal ->
                val obj = JSONObject().apply {
                    put("id", meal.id)
                    put("day", meal.day.name)
                    put("recipeId", meal.recipeId)
                    put("recipeTitle", meal.recipeTitle)
                    put("portions", meal.portions)
                }
                mealsArray.put(obj)
            }

            val checkedArray = JSONArray()
            checkedIds.forEach { checkedArray.put(it) }

            val prefs = context.getSharedPreferences(PREFS_SHOPPING_PREFIX + userKey, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("planned_meals", mealsArray.toString())
                .putString("checked_ids", checkedArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd lokalnego zapisu listy zakupów", e)
        }

        // 2. Zapis w chmurze Firebase Firestore
        if (FirebaseRecipeSync.isConfigured(context)) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val mealsListMaps = plannedMeals.map { meal ->
                    mapOf(
                        "id" to meal.id,
                        "day" to meal.day.name,
                        "recipeId" to meal.recipeId,
                        "recipeTitle" to meal.recipeTitle,
                        "portions" to meal.portions
                    )
                }

                val docData = mapOf(
                    "userEmail" to userEmail,
                    "plannedMeals" to mealsListMaps,
                    "checkedIds" to checkedIds.toList(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users")
                    .document(userKey)
                    .collection("shoppingList")
                    .document("current")
                    .set(docData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Lista zakupów dla $userEmail pomyślnie zsynchronizowana z Firebase.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Błąd wysyłania listy zakupów do Firebase", e)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Wyjątek podczas zapisu listy zakupów do Firestore", e)
            }
        }
    }

    /**
     * Rejestruje nasłuchiwacz na żywo zmian w prywatnej liście zakupów w Firebase.
     */
    fun startRealtimeShoppingSync(
        context: Context,
        userEmail: String,
        onDataChanged: (List<PlannedMeal>, Set<String>) -> Unit
    ): ListenerRegistration? {
        if (!FirebaseRecipeSync.isConfigured(context) || userEmail.isBlank()) return null
        val userKey = normalizeUserId(userEmail)

        return try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .document(userKey)
                .collection("shoppingList")
                .document("current")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Błąd nasłuchiwania listy zakupów w Firebase: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val data = snapshot.data ?: return@addSnapshotListener
                        val mealsRaw = data["plannedMeals"] as? List<*>
                        val checkedRaw = data["checkedIds"] as? List<*>

                        val meals = mutableListOf<PlannedMeal>()
                        mealsRaw?.forEach { item ->
                            if (item is Map<*, *>) {
                                val id = (item["id"] as? String) ?: java.util.UUID.randomUUID().toString()
                                val dayStr = (item["day"] as? String) ?: "MON"
                                val day = try { DayOfWeekPolish.valueOf(dayStr) } catch (_: Exception) { DayOfWeekPolish.MON }
                                val recipeId = (item["recipeId"] as? String) ?: ""
                                val recipeTitle = (item["recipeTitle"] as? String) ?: ""
                                val portions = (item["portions"] as? Number)?.toInt()?.coerceIn(1, 24) ?: 2
                                if (recipeId.isNotBlank()) {
                                    meals.add(
                                        PlannedMeal(
                                            id = id,
                                            day = day,
                                            recipeId = recipeId,
                                            recipeTitle = recipeTitle.ifBlank { "Danie" },
                                            portions = portions
                                        )
                                    )
                                }
                            }
                        }

                        val checked = mutableSetOf<String>()
                        checkedRaw?.forEach { item ->
                            if (item is String) checked.add(item)
                        }

                        // Aktualizuj lokalny cache
                        val prefs = context.getSharedPreferences(PREFS_SHOPPING_PREFIX + userKey, Context.MODE_PRIVATE)
                        val mealsArray = JSONArray()
                        meals.forEach { meal ->
                            val obj = JSONObject().apply {
                                put("id", meal.id)
                                put("day", meal.day.name)
                                put("recipeId", meal.recipeId)
                                put("recipeTitle", meal.recipeTitle)
                                put("portions", meal.portions)
                            }
                            mealsArray.put(obj)
                        }
                        val checkedArray = JSONArray()
                        checked.forEach { checkedArray.put(it) }
                        prefs.edit()
                            .putString("planned_meals", mealsArray.toString())
                            .putString("checked_ids", checkedArray.toString())
                            .apply()

                        onDataChanged(meals, checked)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Błąd uruchamiania realtime shopping sync", e)
            null
        }
    }

    /**
     * Trwale usuwa prywatną listę zakupów i zaplanowane posiłki użytkownika z Firestore oraz pamięci lokalnej.
     * Wywoływana po kliknięciu "Zakupy zrobione".
     */
    fun clearShoppingData(context: Context, userEmail: String) {
        if (userEmail.isBlank()) return
        val userKey = normalizeUserId(userEmail)

        // 1. Wyczyść lokalne SharedPreferences
        try {
            val prefs = context.getSharedPreferences(PREFS_SHOPPING_PREFIX + userKey, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("planned_meals", "[]")
                .putString("checked_ids", "[]")
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd czyszczenia lokalnej listy zakupów", e)
        }

        // 2. Trwałe usunięcie dokumentu z Firestore
        if (FirebaseRecipeSync.isConfigured(context)) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users")
                    .document(userKey)
                    .collection("shoppingList")
                    .document("current")
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Lista zakupów dla $userEmail została trwale usunięta z Firebase po zakończeniu zakupów.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Błąd trwałego usuwania listy zakupów z Firebase", e)
                    }
            } catch (e: Throwable) {
                Log.e(TAG, "Wyjątek podczas usuwania listy zakupów z Firestore", e)
            }
        }
    }
}
