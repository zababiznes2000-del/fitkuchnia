package com.example.data

import android.content.Context
import com.example.data.firebase.FirebaseRecipeSync
import com.example.data.local.AppRecipeStorage
import com.example.data.local.FitKuchniaDatabase
import com.example.data.local.RecipeEntity
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecipeRepository(
    private val context: Context,
    private val database: FitKuchniaDatabase
) {

    private val recipeDao = database.recipeDao()

    val allRecipes: Flow<List<Recipe>> = recipeDao.getAllRecipesFlow().map { entities ->
        entities.map { it.toDomainModel() }
    }

    /**
     * Sprawdza, czy synchronizacja w chmurze Firebase jest aktywna.
     */
    fun isCloudConfigured(): Boolean {
        return FirebaseRecipeSync.isConfigured(context)
    }

    /**
     * Rejestruje nasłuchiwanie na żywo zmian w Cloud Firestore.
     * Gdy jakikolwiek użytkownik doda lub zaktualizuje przepis w chmurze,
     * zostaje on natychmiast zapisany w lokalnej bazie Room i pamięci aplikacji.
     */
    fun startRealtimeCloudSync(
        onMerged: ((List<Recipe>) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): ListenerRegistration? {
        return FirebaseRecipeSync.startRealtimeSync(
            context = context,
            onRecipesReceived = { cloudRecipes ->
                try {
                    val deletedIds = AppRecipeStorage.getDeletedRecipeIds(context)
                    val validCloudRecipes = cloudRecipes.filterNot { it.id in deletedIds || AppRecipeStorage.isDeprecatedBotRecipe(it) }

                    // Zachowaj wyłącznie lokalne przepisy PENDING stworzone offline, których jeszcze nie ma w chmurze
                    val localPending = AppRecipeStorage.loadSavedRecipes(context)
                        .filter { it.moderationStatus == RecipeModerationStatus.PENDING && !cloudRecipes.any { c -> c.id == it.id } }
                        .filterNot { it.id in deletedIds || AppRecipeStorage.isDeprecatedBotRecipe(it) }

                    val mergedList = (validCloudRecipes + localPending).distinctBy { it.id }
                    AppRecipeStorage.saveAllRecipes(context, mergedList)

                    // Zapisz do Room w tle i usuń skasowane
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            recipeDao.deleteAllRecipes()
                            recipeDao.insertRecipes(mergedList.map { RecipeEntity.fromDomainModel(it) })
                        } catch (_: Exception) {}
                    }
                    onMerged?.invoke(mergedList)
                } catch (_: Exception) {}
            },
            onError = onError
        )
    }

    /**
     * Synchroniczne wczytanie przepisów z pamięci podręcznej aplikacji (SharedPreferences / plik)
     * przy starcie aplikacji, aby nie czekać na asynchroniczne zapytanie do Room.
     */
    fun loadInitialCachedRecipes(): List<Recipe> {
        val deletedIds = AppRecipeStorage.getDeletedRecipeIds(context)
        return AppRecipeStorage.loadSavedRecipes(context).filterNot { it.id in deletedIds || AppRecipeStorage.isDeprecatedBotRecipe(it) }
    }

    /**
     * Dwukierunkowa synchronizacja bazy Room z pamięcią aplikacji (SharedPreferences / plik).
     * Jeśli Room został wyczyszczony (np. restart/migracja), natychmiast przywraca przepisy z pamięci aplikacji.
     */
    suspend fun syncPersistentStorage(): List<Recipe> = withContext(Dispatchers.IO) {
        // Trwałe usunięcie wycofanych bot-przepisów z bazy danych Room
        AppRecipeStorage.DEPRECATED_BOT_RECIPE_IDS.forEach { id ->
            try {
                recipeDao.deleteRecipeById(id)
            } catch (_: Exception) {}
        }

        val deletedIds = AppRecipeStorage.getDeletedRecipeIds(context)
        val memoryRecipes = AppRecipeStorage.loadSavedRecipes(context).filterNot { it.id in deletedIds || AppRecipeStorage.isDeprecatedBotRecipe(it) }
        val roomEntities = try {
            recipeDao.getAllRecipes()
        } catch (_: Exception) {
            emptyList()
        }
        val roomRecipes = roomEntities.map { it.toDomainModel() }.filterNot { it.id in deletedIds || AppRecipeStorage.isDeprecatedBotRecipe(it) }

        val activeList = if (roomRecipes.isEmpty() && memoryRecipes.isNotEmpty()) {
            try {
                recipeDao.insertRecipes(memoryRecipes.map { RecipeEntity.fromDomainModel(it) })
            } catch (_: Exception) {}
            memoryRecipes
        } else if (roomRecipes.isNotEmpty()) {
            AppRecipeStorage.saveAllRecipes(context, roomRecipes)
            roomRecipes
        } else {
            emptyList()
        }

        // Jeśli Firebase jest skonfigurowany, wyślij lokalne przepisy do chmury (z pominięciem wycofanych)
        if (FirebaseRecipeSync.isConfigured(context) && activeList.isNotEmpty()) {
            activeList.forEach { recipe ->
                if (recipe.id !in deletedIds && !AppRecipeStorage.isDeprecatedBotRecipe(recipe)) {
                    FirebaseRecipeSync.uploadRecipe(context, recipe)
                }
            }
        }

        // Usunięcie wycofanych przepisów z Cloud Firestore, aby nie pojawiały się u nikogo
        if (FirebaseRecipeSync.isConfigured(context)) {
            AppRecipeStorage.DEPRECATED_BOT_RECIPE_IDS.forEach { id ->
                FirebaseRecipeSync.deleteRecipe(context, id)
            }
        }

        activeList
    }

    suspend fun getRecipeById(id: String): Recipe? = withContext(Dispatchers.IO) {
        recipeDao.getRecipeById(id)?.toDomainModel()
            ?: AppRecipeStorage.loadSavedRecipes(context).find { it.id == id }
    }

    suspend fun insertRecipe(recipe: Recipe) = withContext(Dispatchers.IO) {
        // 1. Zapis do pamięci aplikacji (SharedPreferences i plik)
        AppRecipeStorage.saveRecipe(context, recipe)
        // 2. Zapis do bazy danych Room
        try {
            recipeDao.insertRecipe(RecipeEntity.fromDomainModel(recipe))
        } catch (_: Exception) {}

        // 3. Zapis w chmurze Firebase (Firestore + Storage dla zdjęć)
        if (FirebaseRecipeSync.isConfigured(context)) {
            FirebaseRecipeSync.uploadRecipe(context, recipe)
        }
    }

    suspend fun deleteRecipe(id: String) = withContext(Dispatchers.IO) {
        AppRecipeStorage.removeRecipe(context, id)
        try {
            recipeDao.deleteRecipeById(id)
        } catch (_: Exception) {}

        // Usunięcie z chmury Firebase
        if (FirebaseRecipeSync.isConfigured(context)) {
            FirebaseRecipeSync.deleteRecipe(context, id)
        }
    }

    suspend fun updateModerationStatus(id: String, status: RecipeModerationStatus) = withContext(Dispatchers.IO) {
        AppRecipeStorage.updateModerationStatus(context, id, status)
        try {
            recipeDao.updateModerationStatus(id, status.name)
        } catch (_: Exception) {}

        // Aktualizacja w chmurze Firebase
        if (FirebaseRecipeSync.isConfigured(context)) {
            FirebaseRecipeSync.updateModerationStatus(context, id, status)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val all = AppRecipeStorage.loadSavedRecipes(context)
        AppRecipeStorage.clearAll(context)
        try {
            recipeDao.deleteAllRecipes()
        } catch (_: Exception) {}

        if (FirebaseRecipeSync.isConfigured(context)) {
            all.forEach {
                FirebaseRecipeSync.deleteRecipe(context, it.id)
            }
        }
    }
}
