package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.*
import com.example.util.ImageStorageHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Odpowiada za integrację z chmurą Google Cloud Firestore.
 * Przesyła wyłącznie dane tekstowe przepisów (100% darmowy plan Spark bez konieczności karty czy Storage).
 * Umożliwia synchronizację przepisów w czasie rzeczywistym między wszystkimi
 * użytkownikami aplikacji.
 */
object FirebaseRecipeSync {
    private const val TAG = "FirebaseRecipeSync"
    private const val COLLECTION_RECIPES = "recipes"

    /**
     * Sprawdza, czy Firebase został poprawnie zainicjalizowany (np. przez plik google-services.json lub opcje projektu).
     */
    fun isConfigured(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                val app = try {
                    FirebaseApp.initializeApp(context)
                } catch (_: Throwable) {
                    null
                }
                if (app != null) {
                    true
                } else {
                    // Rezerwowa bezpośrednia konfiguracja na wypadek braku wygenerowanych zasobów
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:59070493984:android:a877ee672784a18ff0d345")
                        .setApiKey("AIzaSyC5HPIxGDU6tZ9mFWlKn300MQX25JZdNBo")
                        .setProjectId("foodx-48322")
                        .setStorageBucket("foodx-48322.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(context, options) != null
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Firebase nie jest jeszcze skonfigurowany: ${e.message}")
            false
        }
    }

    /**
     * Uruchamia nasłuchiwanie w czasie rzeczywistym na nowe, zmodyfikowane lub usunięte przepisy w chmurze Firestore.
     */
    fun startRealtimeSync(
        context: Context,
        onRecipesReceived: (List<Recipe>) -> Unit,
        onError: ((String) -> Unit)? = null
    ): ListenerRegistration? {
        if (!isConfigured(context)) {
            Log.i(TAG, "Oczekiwanie na plik google-services.json – tryb lokalny.")
            return null
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(COLLECTION_RECIPES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Błąd nasłuchiwania Firestore", error)
                        onError?.invoke(error.localizedMessage ?: "Błąd połączenia z Firestore")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val recipes = mutableListOf<Recipe>()
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val recipe = mapToRecipe(context, doc.id, data)
                                if (recipe != null) {
                                    recipes.add(recipe)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Błąd parsowania dokumentu ${doc.id}", e)
                            }
                        }
                        onRecipesReceived(recipes)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Nie udało się uruchomić nasłuchiwania Firestore", e)
            null
        }
    }

    /**
     * Wysyła przepis do chmury Cloud Firestore (wyłącznie dane tekstowe).
     */
    suspend fun uploadRecipe(context: Context, recipe: Recipe): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured(context)) {
            return@withContext false
        }

        try {
            // Przygotuj mapę danych dla Firestore
            val docData = recipeToMap(context, recipe)

            val firestore = FirebaseFirestore.getInstance()
            suspendCancellableCoroutine<Boolean> { continuation ->
                firestore.collection(COLLECTION_RECIPES)
                    .document(recipe.id)
                    .set(docData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Przepis tekstowy '${recipe.title}' pomyślnie wysłany do Firestore.")
                        continuation.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Błąd wysyłania przepisu '${recipe.title}' do Firestore", e)
                        continuation.resume(false)
                    }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Wyjątek podczas wysyłania do Firestore", e)
            false
        }
    }

    /**
     * Aktualizuje status moderacji przepisu w Cloud Firestore.
     */
    suspend fun updateModerationStatus(context: Context, recipeId: String, status: RecipeModerationStatus): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured(context)) return@withContext false
        try {
            val firestore = FirebaseFirestore.getInstance()
            suspendCancellableCoroutine { continuation ->
                firestore.collection(COLLECTION_RECIPES)
                    .document(recipeId)
                    .update("moderationStatus", status.name)
                    .addOnSuccessListener { continuation.resume(true) }
                    .addOnFailureListener { continuation.resume(false) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Błąd aktualizacji statusu moderacji w Firestore", e)
            false
        }
    }

    /**
     * Usuwa przepis z Cloud Firestore.
     */
    suspend fun deleteRecipe(context: Context, recipeId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured(context)) return@withContext false
        try {
            val firestore = FirebaseFirestore.getInstance()
            suspendCancellableCoroutine { continuation ->
                firestore.collection(COLLECTION_RECIPES)
                    .document(recipeId)
                    .delete()
                    .addOnSuccessListener {
                        continuation.resume(true)
                    }
                    .addOnFailureListener {
                        continuation.resume(false)
                    }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Błąd usuwania przepisu z Firestore", e)
            false
        }
    }

    /**
     * Mapuje obiekt Recipe na mapę gotową do zapisu w dokumencie Firestore.
     */
    private fun recipeToMap(context: Context, recipe: Recipe): Map<String, Any?> {
        val base64Image = recipe.imageBase64 ?: ImageStorageHelper.encodeFileToBase64(recipe.imagePath)

        return mapOf(
            "id" to recipe.id,
            "title" to recipe.title,
            "description" to recipe.description,
            "prepTimeMinutes" to recipe.prepTimeMinutes,
            "kcalPerPortion" to recipe.kcalPerPortion,
            "fitPoints" to recipe.fitPoints,
            "fitPointsReason" to recipe.fitPointsReason,
            "proteinG" to recipe.proteinG,
            "fatG" to recipe.fatG,
            "carbsG" to recipe.carbsG,
            "basePortions" to recipe.basePortions,
            "categories" to recipe.categories.map { it.name },
            "matchedAilments" to recipe.matchedAilments,
            "isVerified" to recipe.isVerified,
            "author" to recipe.author,
            "authorEmail" to recipe.authorEmail,
            "moderationStatus" to recipe.moderationStatus.name,
            "imagePath" to recipe.imagePath,
            "imageBase64" to base64Image,
            "imageDrawableName" to recipe.imageDrawableName,
            "ingredients" to recipe.ingredients.map { ing ->
                mapOf(
                    "name" to ing.name,
                    "amount" to ing.amount,
                    "unit" to ing.unit,
                    "category" to ing.category.name,
                    "optional" to ing.optional
                )
            },
            "steps" to recipe.steps.map { step ->
                mapOf(
                    "stepNumber" to step.stepNumber,
                    "instruction" to step.instruction,
                    "timerMinutes" to step.timerMinutes,
                    "tip" to step.tip
                )
            },
            "beveragePairing" to recipe.beveragePairing?.let { bev ->
                mapOf(
                    "title" to bev.title,
                    "description" to bev.description,
                    "whyItMatches" to bev.whyItMatches,
                    "fitPoints" to bev.fitPoints
                )
            },
            "updatedAt" to System.currentTimeMillis()
        )
    }

    /**
     * Konwertuje mapę danych pobraną z Firestore na domenowy model Recipe.
     */
    @Suppress("UNCHECKED_CAST")
    private fun mapToRecipe(context: Context, docId: String, data: Map<String, Any?>): Recipe? {
        return try {
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val title = (data["title"] as? String)?.takeIf { it.isNotBlank() } ?: "Przepis ($id)"
            val description = (data["description"] as? String) ?: ""
            val prepTimeMinutes = ((data["prepTimeMinutes"] as? Number)?.toInt()
                ?: (data["prepTime"] as? Number)?.toInt()
                ?: (data["time"] as? Number)?.toInt() ?: 20).coerceAtLeast(1)
            val kcalPerPortion = ((data["kcalPerPortion"] as? Number)?.toInt()
                ?: (data["calories"] as? Number)?.toInt() ?: 400).coerceAtLeast(0)
            val fitPoints = ((data["fitPoints"] as? Number)?.toInt() ?: 80).coerceIn(1, 100)
            val fitPointsReason = (data["fitPointsReason"] as? String) ?: "Zbilansowane wartości odżywcze"
            val proteinG = ((data["proteinG"] as? Number)?.toInt()
                ?: (data["protein"] as? Number)?.toInt() ?: 20).coerceAtLeast(0)
            val fatG = ((data["fatG"] as? Number)?.toInt()
                ?: (data["fat"] as? Number)?.toInt() ?: 15).coerceAtLeast(0)
            val carbsG = ((data["carbsG"] as? Number)?.toInt()
                ?: (data["carbs"] as? Number)?.toInt() ?: 40).coerceAtLeast(0)
            val basePortions = ((data["basePortions"] as? Number)?.toInt()
                ?: (data["servings"] as? Number)?.toInt()
                ?: (data["portions"] as? Number)?.toInt() ?: 2).coerceIn(1, 24)

            val rawCategories = data["categories"]
            val categories = when (rawCategories) {
                is List<*> -> rawCategories.mapNotNull { it?.toString() }
                is String -> rawCategories.split(",").map { it.trim() }
                else -> emptyList()
            }.mapNotNull { catStr ->
                RecipeCategory.values().find { it.name.equals(catStr, ignoreCase = true) }
            }.toSet().ifEmpty { setOf(RecipeCategory.OBIAD) }

            val rawAilments = data["matchedAilments"]
            val matchedAilments = when (rawAilments) {
                is List<*> -> rawAilments.mapNotNull { it?.toString() }
                is String -> rawAilments.split(",").map { it.trim() }
                else -> emptyList()
            }

            val isVerified = (data["isVerified"] as? Boolean) ?: true
            val author = (data["author"] as? String)?.takeIf { it.isNotBlank() } ?: "FitKuchnia Chef"
            val authorEmail = (data["authorEmail"] as? String) ?: ""

            val modStatusStr = (data["moderationStatus"] as? String) ?: RecipeModerationStatus.APPROVED.name
            val moderationStatus = try {
                RecipeModerationStatus.valueOf(modStatusStr)
            } catch (_: Exception) {
                RecipeModerationStatus.APPROVED
            }

            val imageDrawableName = data["imageDrawableName"] as? String
            val imageBase64 = data["imageBase64"] as? String
            val rawImageUrl = (data["imageUrl"] ?: data["photoUrl"]) as? String ?: ""
            var imagePath = (data["imagePath"] ?: data["path"]) as? String
            if (imagePath.isNullOrBlank() && rawImageUrl.isNotBlank()) {
                imagePath = rawImageUrl
            }

            // Jeśli plik lokalny ze ścieżki nie istnieje (np. na urządzeniu innego użytkownika lub po restarcie),
            // a posiadamy zsynchronizowany ciąg Base64 zdjęcia, odtwórzmy oryginalny plik użytkownika:
            if (!imageBase64.isNullOrBlank()) {
                val fileExists = !imagePath.isNullOrBlank() && File(imagePath).exists()
                if (!fileExists) {
                    val restored = ImageStorageHelper.decodeBase64ToFile(context, imageBase64, id)
                    if (!restored.isNullOrBlank()) {
                        imagePath = restored
                    }
                }
            }

            val rawIngredients = when (val raw = data["ingredients"]) {
                is List<*> -> raw
                is Map<*, *> -> raw.values.toList()
                else -> emptyList<Any?>()
            }
            val ingredients = mutableListOf<RecipeIngredient>()
            for (rawItem in rawIngredients) {
                if (rawItem is Map<*, *>) {
                    val name = (rawItem["name"] ?: rawItem["title"] ?: rawItem["ingredient"])?.toString() ?: continue
                    if (name.isBlank()) continue
                    val amount = when (val a = rawItem["amount"] ?: rawItem["quantity"] ?: rawItem["qty"]) {
                        is Number -> a.toDouble()
                        is String -> a.replace(",", ".").toDoubleOrNull() ?: 1.0
                        else -> 1.0
                    }
                    val unit = (rawItem["unit"] ?: rawItem["measure"])?.toString() ?: "szt."
                    val catStr = rawItem["category"]?.toString() ?: StoreCategory.WARZYWA_OWOCE.name
                    val category = StoreCategory.values().find { it.name.equals(catStr, ignoreCase = true) }
                        ?: StoreCategory.WARZYWA_OWOCE
                    val optional = (rawItem["optional"] as? Boolean) ?: false
                    ingredients.add(RecipeIngredient(name, amount, unit, category, optional))
                } else if (rawItem is String && rawItem.isNotBlank()) {
                    ingredients.add(RecipeIngredient(name = rawItem, amount = 1.0, unit = "szt.", category = StoreCategory.WARZYWA_OWOCE))
                }
            }

            val rawSteps = when (val raw = data["steps"] ?: data["instructions"]) {
                is List<*> -> raw
                is Map<*, *> -> raw.values.toList()
                is String -> raw.split("\n").filter { it.isNotBlank() }
                else -> emptyList<Any?>()
            }
            val steps = mutableListOf<RecipeStep>()
            for ((index, rawItem) in rawSteps.withIndex()) {
                if (rawItem is Map<*, *>) {
                    val instruction = (rawItem["instruction"] ?: rawItem["text"] ?: rawItem["step"] ?: rawItem["description"])?.toString() ?: ""
                    if (instruction.isBlank()) continue
                    val stepNumber = (rawItem["stepNumber"] as? Number)?.toInt()
                        ?: (rawItem["number"] as? Number)?.toInt() ?: (index + 1)
                    val timerMinutes = (rawItem["timerMinutes"] as? Number)?.toInt()
                        ?: (rawItem["timer"] as? Number)?.toInt()
                    val tip = rawItem["tip"]?.toString()
                    steps.add(RecipeStep(stepNumber, instruction, timerMinutes, tip))
                } else if (rawItem is String && rawItem.isNotBlank()) {
                    steps.add(RecipeStep(stepNumber = index + 1, instruction = rawItem))
                }
            }

            val rawBev = data["beveragePairing"] as? Map<*, *>
            val beveragePairing = if (rawBev != null) {
                BeveragePairing(
                    title = rawBev["title"]?.toString() ?: "",
                    description = rawBev["description"]?.toString() ?: "",
                    whyItMatches = rawBev["whyItMatches"]?.toString() ?: "",
                    fitPoints = (rawBev["fitPoints"] as? Number)?.toInt() ?: 90
                )
            } else null

            Recipe(
                id = id,
                title = title,
                description = description,
                prepTimeMinutes = prepTimeMinutes,
                prepTime = prepTimeMinutes,
                kcalPerPortion = kcalPerPortion,
                fitPoints = fitPoints,
                fitPointsReason = fitPointsReason,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
                basePortions = basePortions,
                servings = basePortions,
                categories = categories,
                ingredients = ingredients,
                steps = steps,
                beveragePairing = beveragePairing,
                matchedAilments = matchedAilments,
                isVerified = isVerified,
                author = author,
                authorEmail = authorEmail,
                moderationStatus = moderationStatus,
                imagePath = imagePath,
                imageUrl = rawImageUrl.ifBlank { imagePath ?: "" },
                imageDrawableName = imageDrawableName,
                imageBase64 = imageBase64
            )
        } catch (e: Exception) {
            Log.e(TAG, "Błąd konwersji mapy Firestore na Recipe", e)
            null
        }
    }
}
