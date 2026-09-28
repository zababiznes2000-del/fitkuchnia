package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.MealHistoryEntry
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date

/**
 * Odpowiada za zarządzanie kolekcją historii zjedzonych posiłków w Firestore:
 * users/{userId}/mealHistory/{historyId}
 *
 * Zapewnia:
 * 1. Zapis nowego zjedzonego dania do Firestore i lokalnej pamięci podręcznej.
 * 2. Automatyczne usuwanie dań z poprzednich tygodni (w bazie znajdują się wyłącznie wpisy z bieżącego tygodnia).
 * 3. Nasłuchiwanie zmian w czasie rzeczywistym.
 */
object FirebaseMealHistorySync {
    private const val TAG = "FirebaseMealHistorySync"
    private const val PREFS_HISTORY_PREFIX = "fitkuchnia_meal_history_"
    private const val KEY_MEALS_JSON = "meals_history_json"

    fun normalizeUserId(email: String): String {
        return email.lowercase().trim().replace(".", "_").replace("@", "_at_")
    }

    /**
     * Zwraca czas w milisekundach początku bieżącego tygodnia (Poniedziałek, 00:00:00.000).
     */
    fun getStartOfCurrentWeekMillis(): Long {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        if (cal.timeInMillis > now) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        return cal.timeInMillis
    }

    /**
     * Zwraca czas w milisekundach końca bieżącego tygodnia (Niedziela, 23:59:59.999).
     */
    fun getEndOfCurrentWeekMillis(): Long {
        return getStartOfCurrentWeekMillis() + (7L * 24 * 60 * 60 * 1000) - 1
    }

    /**
     * Sprawdza, czy dany wpis należy do bieżącego tygodnia.
     */
    fun isEntryInCurrentWeek(entry: MealHistoryEntry): Boolean {
        val entryMillis = entry.timestamp.toDate().time
        val weekStart = getStartOfCurrentWeekMillis()
        val weekEnd = getEndOfCurrentWeekMillis()
        return entryMillis in weekStart..weekEnd
    }

    /**
     * Wczytuje lokalną kopię historii posiłków bieżącego tygodnia.
     * Automatycznie odfiltrowuje i usuwa wpisy z poprzednich tygodni.
     */
    fun loadLocalCurrentWeekMeals(context: Context, userEmail: String): List<MealHistoryEntry> {
        val userKey = normalizeUserId(userEmail)
        val prefs = context.getSharedPreferences(PREFS_HISTORY_PREFIX + userKey, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MEALS_JSON, null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            val list = mutableListOf<MealHistoryEntry>()
            val weekStart = getStartOfCurrentWeekMillis()
            val weekEnd = getEndOfCurrentWeekMillis()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timeMillis = obj.optLong("timeMillis", 0L)
                if (timeMillis in weekStart..weekEnd) {
                    val ingredientsArray = obj.optJSONArray("ingredients") ?: JSONArray()
                    val ingredientsList = mutableListOf<String>()
                    for (j in 0 until ingredientsArray.length()) {
                        ingredientsList.add(ingredientsArray.getString(j))
                    }

                    list.add(
                        MealHistoryEntry(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            recipeId = obj.optString("recipeId", ""),
                            title = obj.optString("title", "Posiłek"),
                            ingredients = ingredientsList,
                            timestamp = Timestamp(Date(timeMillis)),
                            dateString = obj.optString("dateString", ""),
                            kcal = obj.optInt("kcal", 0),
                            proteinG = obj.optInt("proteinG", 0),
                            fatG = obj.optInt("fatG", 0),
                            carbsG = obj.optInt("carbsG", 0),
                            fitPoints = obj.optInt("fitPoints", 0),
                            portions = obj.optInt("portions", 1)
                        )
                    )
                }
            }

            // Jeśli były jakieś stare wpisy, natychmiast zapisz oczyszczoną listę lokalnie
            if (list.size != array.length()) {
                saveLocalMeals(context, userEmail, list)
            }

            list.sortedByDescending { it.timestamp.toDate().time }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd parsowania lokalnej historii posiłków", e)
            emptyList()
        }
    }

    private fun saveLocalMeals(context: Context, userEmail: String, meals: List<MealHistoryEntry>) {
        try {
            val userKey = normalizeUserId(userEmail)
            val array = JSONArray()
            meals.filter { isEntryInCurrentWeek(it) }.forEach { meal ->
                val obj = JSONObject().apply {
                    put("id", meal.id)
                    put("recipeId", meal.recipeId)
                    put("title", meal.title)
                    put("timeMillis", meal.timestamp.toDate().time)
                    put("dateString", meal.dateString)
                    put("kcal", meal.kcal)
                    put("proteinG", meal.proteinG)
                    put("fatG", meal.fatG)
                    put("carbsG", meal.carbsG)
                    put("fitPoints", meal.fitPoints)
                    put("portions", meal.portions)
                    val ingArr = JSONArray()
                    meal.ingredients.forEach { ingArr.put(it) }
                    put("ingredients", ingArr)
                }
                array.put(obj)
            }
            val prefs = context.getSharedPreferences(PREFS_HISTORY_PREFIX + userKey, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_MEALS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu lokalnej historii posiłków", e)
        }
    }

    /**
     * Zapisuje nowy wpis do users/{userId}/mealHistory/{historyId}
     */
    fun addMealHistoryEntry(
        context: Context,
        userEmail: String,
        entry: MealHistoryEntry,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ) {
        if (userEmail.isBlank()) return
        val userKey = normalizeUserId(userEmail)

        // 1. Zapis lokalny (natychmiastowy)
        val currentLocal = loadLocalCurrentWeekMeals(context, userEmail).toMutableList()
        currentLocal.removeAll { it.id == entry.id }
        currentLocal.add(0, entry)
        saveLocalMeals(context, userEmail, currentLocal)
        onSuccess?.invoke()

        // 2. Zapis w chmurze Firestore
        if (FirebaseRecipeSync.isConfigured(context)) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users")
                    .document(userKey)
                    .collection("mealHistory")
                    .document(entry.id)
                    .set(entry.toFirestoreMap(), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Pomyślnie zapisano posiłek ${entry.id} w Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Błąd zapisu posiłku w Firestore", e)
                        onFailure?.invoke(e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Wyjątek zapisu w Firestore", e)
                onFailure?.invoke(e)
            }
        }
    }

    /**
     * Usuwa wpis z kolekcji mealHistory
     */
    fun deleteMealHistoryEntry(
        context: Context,
        userEmail: String,
        entryId: String,
        onComplete: (() -> Unit)? = null
    ) {
        if (userEmail.isBlank()) return
        val userKey = normalizeUserId(userEmail)

        // 1. Usunięcie lokalne
        val currentLocal = loadLocalCurrentWeekMeals(context, userEmail).filterNot { it.id == entryId }
        saveLocalMeals(context, userEmail, currentLocal)
        onComplete?.invoke()

        // 2. Usunięcie z Firestore
        if (FirebaseRecipeSync.isConfigured(context)) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userKey)
                    .collection("mealHistory")
                    .document(entryId)
                    .delete()
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Błąd usuwania posiłku z Firestore: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Wyjątek usuwania z Firestore", e)
            }
        }
    }

    /**
     * Zgodnie z Wymogiem 5:
     * Automatyczne czyszczenie bazy Firestore z wpisów z poprzednich tygodni.
     * Permanentnie usuwa z Firestore i pamięci lokalnej wszystkie posiłki sprzed poniedziałku bieżącego tygodnia.
     */
    fun cleanUpOldWeeks(context: Context, userEmail: String) {
        if (userEmail.isBlank()) return
        val userKey = normalizeUserId(userEmail)
        val weekStart = getStartOfCurrentWeekMillis()
        val weekStartTimestamp = Timestamp(Date(weekStart))

        // Czyszczenie lokalne
        val local = loadLocalCurrentWeekMeals(context, userEmail)
        saveLocalMeals(context, userEmail, local)

        // Czyszczenie Firestore
        if (FirebaseRecipeSync.isConfigured(context)) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users")
                    .document(userKey)
                    .collection("mealHistory")
                    .whereLessThan("timestamp", weekStartTimestamp)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val batch = firestore.batch()
                            for (doc in snapshot.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit().addOnSuccessListener {
                                Log.d(TAG, "Usunięto ${snapshot.size()} starych wpisów posiłków z poprzednich tygodni z Firestore.")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.d(TAG, "Informacja o czyszczeniu starych tygodni (brak wpisów lub brak indeksu): ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Wyjątek podczas czyszczenia starych posiłków w Firestore", e)
            }
        }
    }

    /**
     * Nasłuchiwanie na bieżący tydzień w Firestore w czasie rzeczywistym.
     */
    fun listenToCurrentWeekMealHistory(
        context: Context,
        userEmail: String,
        onUpdate: (List<MealHistoryEntry>) -> Unit
    ): ListenerRegistration? {
        if (userEmail.isBlank()) {
            onUpdate(emptyList())
            return null
        }
        val userKey = normalizeUserId(userEmail)

        // Zawsze najpierw od razu emituj lokalną pamięć
        val local = loadLocalCurrentWeekMeals(context, userEmail)
        onUpdate(local)

        // Uruchom automatyczne czyszczenie poprzednich tygodni
        cleanUpOldWeeks(context, userEmail)

        if (!FirebaseRecipeSync.isConfigured(context)) {
            return null
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .document(userKey)
                .collection("mealHistory")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Błąd nasłuchiwania mealHistory w Firestore", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val list = mutableListOf<MealHistoryEntry>()
                        val weekStart = getStartOfCurrentWeekMillis()
                        val weekEnd = getEndOfCurrentWeekMillis()

                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val entry = MealHistoryEntry.fromFirestoreMap(data)
                            if (entry != null) {
                                val time = entry.timestamp.toDate().time
                                if (time in weekStart..weekEnd) {
                                    list.add(entry)
                                } else if (time < weekStart) {
                                    // Stary wpis wykryty w snapshocie - usuń go
                                    doc.reference.delete()
                                }
                            }
                        }

                        val sorted = list.sortedByDescending { it.timestamp.toDate().time }
                        saveLocalMeals(context, userEmail, sorted)
                        onUpdate(sorted)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Wyjątek rejestracji nasłuchiwania mealHistory", e)
            null
        }
    }
}
