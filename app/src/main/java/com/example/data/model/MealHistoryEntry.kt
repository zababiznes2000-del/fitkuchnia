package com.example.data.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model wpisu historii zjedzonego posiłku przechowywanego w Firestore pod ścieżką:
 * users/{userId}/mealHistory/{historyId}
 */
data class MealHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val recipeId: String = "",
    val title: String = "",
    val ingredients: List<String> = emptyList(),
    val timestamp: Timestamp = Timestamp.now(),
    val dateString: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    val kcal: Int = 0,
    val proteinG: Int = 0,
    val fatG: Int = 0,
    val carbsG: Int = 0,
    val fitPoints: Int = 0,
    val portions: Int = 1
) {
    /**
     * Konwertuje model na mapę danych do zapisu w Firebase Firestore
     */
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "recipeId" to recipeId,
            "title" to title,
            "ingredients" to ingredients,
            "timestamp" to timestamp,
            "dateString" to dateString,
            "kcal" to kcal,
            "proteinG" to proteinG,
            "fatG" to fatG,
            "carbsG" to carbsG,
            "fitPoints" to fitPoints,
            "portions" to portions
        )
    }

    companion object {
        fun fromFirestoreMap(data: Map<String, Any?>): MealHistoryEntry? {
            val id = data["id"] as? String ?: return null
            val recipeId = data["recipeId"] as? String ?: ""
            val title = data["title"] as? String ?: "Posiłek"
            val ingredients = when (val ing = data["ingredients"]) {
                is List<*> -> ing.mapNotNull { it?.toString() }
                else -> emptyList()
            }
            val timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now()
            val dateString = data["dateString"] as? String ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(timestamp.toDate())
            val kcal = (data["kcal"] as? Number)?.toInt() ?: 0
            val proteinG = (data["proteinG"] as? Number)?.toInt() ?: 0
            val fatG = (data["fatG"] as? Number)?.toInt() ?: 0
            val carbsG = (data["carbsG"] as? Number)?.toInt() ?: 0
            val fitPoints = (data["fitPoints"] as? Number)?.toInt() ?: 0
            val portions = (data["portions"] as? Number)?.toInt() ?: 1

            return MealHistoryEntry(
                id = id,
                recipeId = recipeId,
                title = title,
                ingredients = ingredients,
                timestamp = timestamp,
                dateString = dateString,
                kcal = kcal,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
                fitPoints = fitPoints,
                portions = portions
            )
        }
    }
}
