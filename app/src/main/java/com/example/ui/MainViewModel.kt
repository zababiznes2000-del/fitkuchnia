package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ai.CulinaryAiEngine
import com.example.ai.RecipeValidationResult
import com.example.ai.SubstituteAdvice
import com.example.data.*
import com.example.data.firebase.FirebaseMealHistorySync
import com.example.data.firebase.FirebaseShoppingSync
import com.example.data.local.FitKuchniaDatabase
import com.example.data.model.AppMessage
import com.example.data.model.MealHistoryEntry
import com.example.notifications.NotificationRepository
import com.example.ui.components.ActiveTimer
import com.example.ui.components.triggerVibration
import com.example.util.AppVersionManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import android.util.Log

private const val TAG = "MainViewModel"

enum class AppNavTab(val title: String) {
    HOME("Odkrywaj"),
    SEARCH("Szukaj"),
    SHOPPING("Lista Zakupów"),
    DIETITIAN("Dietetyk AI"),
    PROFILE("Profil")
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderIsUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppUiState(
    val currentTab: AppNavTab = AppNavTab.HOME,
    val recipes: List<Recipe> = emptyList(),
    val selectedRecipeId: String? = null,
    val currentRecipePortions: Int = 4,
    val isPortionCalculating: Boolean = false,
    val activeTimer: ActiveTimer? = null,
    val plannedMeals: List<PlannedMeal> = emptyList(),
    val shoppingCheckedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedCategories: Set<RecipeCategory> = emptySet(),
    val fridgeQuery: String = "",
    val fridgeAiResponse: String? = null,
    val fridgeMatchedRecipe: Recipe? = null,
    val isFridgeSearching: Boolean = false,
    val favoriteRecipeIds: Set<String> = emptySet(),
    val creatorTitle: String = "",
    val creatorIngredients: String = "",
    val creatorSteps: String = "",
    val creatorPrepTime: String = "20",
    val creatorOvenTemp: String = "180°C",
    val creatorResult: RecipeValidationResult? = null,
    val creatorIsEvaluating: Boolean = false,
    val activeSubstituteAdvice: SubstituteAdvice? = null,
    val isCookingHandsFree: Boolean = false,
    val cookingStepIndex: Int = 0,
    val isRecipeEditorOpen: Boolean = false,
    val editingRecipe: Recipe? = null,
    val isUserLoggedIn: Boolean = false,
    val currentUser: UserAccount? = null,
    val feedbackMessage: String? = null,
    val chatMessages: List<ChatMessage> = listOf(
        ChatMessage(
            senderIsUser = false,
            text = "Witaj w FitKuchnia! Dodawaj własne przepisy z obowiązkowym zdjęciem potrawy. Zwykłe konta przesyłają przepisy do moderacji, a administrator (zababiznes2000@gmail.com) decyduje o publikacji."
        )
    ),
    val isChatLoading: Boolean = false,
    val isChatSheetOpen: Boolean = false,
    val isCloudSyncActive: Boolean = false,
    val cloudSyncStatusText: String = "Sprawdzanie stanu chmury...",
    val showNotificationPermissionDialog: Boolean = false,
    val shouldRequestNotificationPermission: Boolean = false,
    val messages: List<AppMessage> = emptyList(),
    val isMessagesScreenOpen: Boolean = false,
    val appUpdateStatus: AppVersionManager.AppUpdateStatus = AppVersionManager.AppUpdateStatus.Checking,
    val sessionTimeoutMinutes: Int = 8,
    val sessionRemainingSeconds: Long = 8 * 60L,
    val isSessionExpiringSoon: Boolean = false,
    val sessionExpiredMessage: String? = null,
    val keepScreenOn: Boolean = true,
    val mealHistory: List<MealHistoryEntry> = emptyList(),
    val dietitianAnalysisResult: String? = null,
    val isAnalyzingDiet: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository = RecipeRepository(
        context = application.applicationContext,
        database = FitKuchniaDatabase.getDatabase(application.applicationContext)
    )

    private val _uiState = MutableStateFlow(
        AppUiState(
            recipes = repository.loadInitialCachedRecipes()
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var sessionMonitorJob: Job? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var cloudListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var shoppingListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var mealHistoryListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        try {
            tts = TextToSpeech(application.applicationContext, this)
        } catch (_: Throwable) {
            tts = null
            isTtsReady = false
        }

        val isCloudConfigured = repository.isCloudConfigured()
        val savedUser = com.example.data.local.AppRecipeStorage.loadLoggedInUser(application.applicationContext)
        val keepScreenOn = com.example.util.AppSessionManager.isKeepScreenOnEnabled(application.applicationContext)

        _uiState.update {
            it.copy(
                isUserLoggedIn = savedUser != null,
                currentUser = savedUser,
                keepScreenOn = keepScreenOn,
                sessionExpiredMessage = null,
                isSessionExpiringSoon = false,
                isCloudSyncActive = isCloudConfigured,
                cloudSyncStatusText = if (isCloudConfigured) {
                    "Chmura Firebase: Aktywna (Automatyczna synchronizacja na żywo)"
                } else {
                    "Chmura Firebase: Oczekiwanie na plik google-services.json"
                }
            )
        }

        if (savedUser != null) {
            setupUserShoppingSync(savedUser.email)
            setupUserMealHistorySync(savedUser.email)
        }

        // Automatyczne odczytywanie i synchronizacja przepisów z pamięci aplikacji przy starcie
        viewModelScope.launch {
            // 1. Synchronizuj bazę danych z trwałą pamięcią aplikacji oraz chmurą Firebase
            val syncedRecipes = repository.syncPersistentStorage()
            if (syncedRecipes.isNotEmpty()) {
                _uiState.update { it.copy(recipes = syncedRecipes) }
            }

            // 2. Nasłuchuj zmian w Room DB reaktywnie
            repository.allRecipes.collectLatest { roomRecipes ->
                _uiState.update { it.copy(recipes = roomRecipes) }
            }
        }

        // 3. Rozpocznij nasłuchiwanie na żywo zmian w Cloud Firestore
        if (isCloudConfigured) {
            cloudListenerRegistration = repository.startRealtimeCloudSync(
                onMerged = { merged ->
                    _uiState.update {
                        it.copy(
                            recipes = merged,
                            isCloudSyncActive = true,
                            cloudSyncStatusText = "Chmura Firebase: Aktywna (Automatyczna synchronizacja na żywo)"
                        )
                    }
                },
                onError = { errorMsg ->
                    val isPermissionDenied = errorMsg.contains("PERMISSION_DENIED", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            isCloudSyncActive = false,
                            cloudSyncStatusText = if (isPermissionDenied) {
                                "Reguły Firebase blokują dostęp (PERMISSION_DENIED). Zezwól na odczyt i zapis w konsoli Firebase (Firestore -> Rules -> allow read, write: if true;)."
                            } else {
                                "Błąd Firestore: $errorMsg"
                            }
                        )
                    }
                }
            )
        }

        // 4. Inicjalizacja modułu powiadomień i wiadomości w czasie rzeczywistym
        NotificationRepository.initialize(application.applicationContext)
        viewModelScope.launch {
            NotificationRepository.messagesFlow.collectLatest { msgList ->
                _uiState.update { it.copy(messages = msgList) }
            }
        }
        NotificationRepository.startRealtimeListener(application.applicationContext, savedUser?.email)

        // 5. Automatyczne sprawdzenie statusu wersji aplikacji w Firebase
        checkAppVersionUpdates()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pl", "PL"))
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isTtsReady) {
                tts?.setLanguage(Locale.getDefault())
                isTtsReady = true
            }
        }
    }

    fun selectTab(tab: AppNavTab) {
        _uiState.update { it.copy(currentTab = tab, selectedRecipeId = null, isCookingHandsFree = false) }
    }

    fun toggleRecipeEditor(open: Boolean) {
        _uiState.update { it.copy(isRecipeEditorOpen = open, editingRecipe = if (!open) null else it.editingRecipe) }
    }

    fun openRecipeEditor(recipeToEdit: Recipe? = null) {
        _uiState.update { it.copy(isRecipeEditorOpen = true, editingRecipe = recipeToEdit) }
    }

    fun closeRecipeEditor() {
        _uiState.update { it.copy(isRecipeEditorOpen = false, editingRecipe = null) }
    }

    fun canEditRecipe(recipe: Recipe? = null): Boolean {
        val current = _uiState.value.currentUser ?: return false
        if (current.isAdmin || UserAccount.isAdministrator(current.email, current.name)) return true
        if (recipe != null) {
            if (recipe.authorEmail.isBlank()) return true
            if (recipe.authorEmail.equals(current.email, ignoreCase = true)) return true
        }
        return false
    }

    fun saveUserRecipe(recipe: Recipe) {
        val user = _uiState.value.currentUser
        val isExisting = _uiState.value.recipes.any { it.id == recipe.id }
        val recipeToSave = if (user != null) {
            val author = if (isExisting && recipe.author.isNotBlank()) recipe.author else user.name
            val authorEmail = if (isExisting && recipe.authorEmail.isNotBlank()) recipe.authorEmail else user.email
            recipe.copy(
                author = author,
                authorEmail = authorEmail,
                moderationStatus = if (user.isAdmin) RecipeModerationStatus.APPROVED else (if (isExisting) recipe.moderationStatus else RecipeModerationStatus.PENDING),
                isVerified = if (user.isAdmin) true else recipe.isVerified
            )
        } else recipe

        viewModelScope.launch {
            repository.insertRecipe(recipeToSave)
            val msg = if (recipeToSave.moderationStatus == RecipeModerationStatus.APPROVED) {
                if (isExisting) "Zaktualizowano pomyślnie przepis \"${recipeToSave.title}\" w chmurze!"
                else "Przepis \"${recipeToSave.title}\" został pomyślnie opublikowany w chmurze!"
            } else {
                "Przepis \"${recipeToSave.title}\" został przesłany do weryfikacji przez Administratora (zababiznes2000@gmail.com)."
            }
            _uiState.update { it.copy(feedbackMessage = msg, isRecipeEditorOpen = false, editingRecipe = null) }
        }
    }

    fun canDeleteRecipe(recipe: Recipe): Boolean {
        val current = _uiState.value.currentUser ?: return false
        return current.isAdmin || UserAccount.isAdministrator(current.email, current.name) || recipe.authorEmail.equals(current.email, ignoreCase = true)
    }

    fun deleteUserRecipe(recipeId: String): Boolean {
        val recipe = _uiState.value.recipes.find { it.id == recipeId } ?: return false
        if (!canDeleteRecipe(recipe)) {
            _uiState.update {
                it.copy(feedbackMessage = "Brak uprawnień! Zwykły użytkownik może usunąć wyłącznie własne przepisy.")
            }
            return false
        }

        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
            _uiState.update {
                it.copy(
                    selectedRecipeId = if (it.selectedRecipeId == recipeId) null else it.selectedRecipeId,
                    favoriteRecipeIds = it.favoriteRecipeIds - recipeId,
                    plannedMeals = it.plannedMeals.filterNot { m -> m.recipeId == recipeId },
                    feedbackMessage = "Przepis został pomyślnie usunięty."
                )
            }
        }
        return true
    }

    fun approveRecipe(recipeId: String) {
        val current = _uiState.value.currentUser
        if (current == null || !current.isAdmin) {
            _uiState.update {
                it.copy(feedbackMessage = "Tylko administrator (zababiznes2000@gmail.com) może akceptować przepisy!")
            }
            return
        }
        viewModelScope.launch {
            repository.updateModerationStatus(recipeId, RecipeModerationStatus.APPROVED)
            _uiState.update {
                it.copy(feedbackMessage = "Przepis został zatwierdzony i jest teraz widoczny publicznie w FitKuchnia!")
            }
        }
    }

    fun rejectRecipe(recipeId: String) {
        val current = _uiState.value.currentUser
        if (current == null || !current.isAdmin) {
            _uiState.update {
                it.copy(feedbackMessage = "Tylko administrator może odrzucać przepisy!")
            }
            return
        }
        viewModelScope.launch {
            repository.deleteRecipe(recipeId)
            _uiState.update {
                it.copy(
                    selectedRecipeId = if (it.selectedRecipeId == recipeId) null else it.selectedRecipeId,
                    feedbackMessage = "Przepis został odrzucony i usunięty z kolejki moderacji."
                )
            }
        }
    }

    private fun setupUserShoppingSync(userEmail: String) {
        shoppingListenerRegistration?.remove()
        shoppingListenerRegistration = null

        val (localMeals, localChecked) = FirebaseShoppingSync.loadLocalShoppingData(getApplication(), userEmail)
        _uiState.update { it.copy(plannedMeals = localMeals, shoppingCheckedIds = localChecked) }

        if (repository.isCloudConfigured()) {
            shoppingListenerRegistration = FirebaseShoppingSync.startRealtimeShoppingSync(
                context = getApplication(),
                userEmail = userEmail,
                onDataChanged = { cloudMeals, cloudChecked ->
                    _uiState.update { it.copy(plannedMeals = cloudMeals, shoppingCheckedIds = cloudChecked) }
                }
            )
        }
    }

    private fun setupUserMealHistorySync(userEmail: String) {
        mealHistoryListenerRegistration?.remove()
        mealHistoryListenerRegistration = null

        val localMeals = FirebaseMealHistorySync.loadLocalCurrentWeekMeals(getApplication(), userEmail)
        _uiState.update { it.copy(mealHistory = localMeals) }

        mealHistoryListenerRegistration = FirebaseMealHistorySync.listenToCurrentWeekMealHistory(
            context = getApplication(),
            userEmail = userEmail,
            onUpdate = { cloudMeals ->
                _uiState.update { it.copy(mealHistory = cloudMeals) }
            }
        )
    }

    fun markRecipeAsEaten(recipe: Recipe, portions: Int = 1) {
        val currentUser = _uiState.value.currentUser
        val userEmail = currentUser?.email ?: "guest"
        val entry = MealHistoryEntry(
            recipeId = recipe.id,
            title = recipe.title,
            ingredients = recipe.ingredients.map { it.name },
            timestamp = Timestamp.now(),
            dateString = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date()),
            kcal = recipe.kcalPerPortion,
            proteinG = recipe.proteinG,
            fatG = recipe.fatG,
            carbsG = recipe.carbsG,
            fitPoints = recipe.fitPoints,
            portions = portions
        )

        FirebaseMealHistorySync.addMealHistoryEntry(
            context = getApplication(),
            userEmail = userEmail,
            entry = entry,
            onSuccess = {
                val current = _uiState.value.mealHistory.toMutableList()
                current.removeAll { it.id == entry.id }
                current.add(0, entry)
                _uiState.update {
                    it.copy(
                        mealHistory = current,
                        feedbackMessage = "Danie „${recipe.title}” oznaczono jako zjedzone! Zapisano w historii tygodnia."
                    )
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(feedbackMessage = "Zapisano posiłek lokalnie w historii tygodnia.")
                }
            }
        )
    }

    fun removeMealHistoryEntry(entryId: String) {
        val currentUser = _uiState.value.currentUser
        val userEmail = currentUser?.email ?: "guest"

        FirebaseMealHistorySync.deleteMealHistoryEntry(
            context = getApplication(),
            userEmail = userEmail,
            entryId = entryId,
            onComplete = {
                _uiState.update {
                    it.copy(
                        mealHistory = it.mealHistory.filterNot { m -> m.id == entryId },
                        feedbackMessage = "Usunięto posiłek z historii tygodnia."
                    )
                }
            }
        )
    }

    fun runDietitianAnalysis() {
        val meals = _uiState.value.mealHistory
        if (meals.isEmpty()) {
            _uiState.update {
                it.copy(
                    dietitianAnalysisResult = "Nie oznaczono jeszcze posiłków w tym tygodniu. Otwórz dowolny przepis i naciśnij „🍽️ Oznacz jako zjedzone”, aby Asystent Dietetyk AI mógł przeanalizować Twoją dietę!",
                    feedbackMessage = "Najpierw oznacz co najmniej 1 zjedzone danie w tym tygodniu."
                )
            }
            return
        }

        _uiState.update { it.copy(isAnalyzingDiet = true) }
        viewModelScope.launch {
            val apiKey = try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            } catch (_: Exception) {
                null
            }

            val result = CulinaryAiEngine.analyzeWeeklyDietAndRecommendMeals(
                weeklyMeals = meals,
                availableRecipes = _uiState.value.recipes,
                apiKey = apiKey
            )

            _uiState.update {
                it.copy(
                    dietitianAnalysisResult = result,
                    isAnalyzingDiet = false,
                    feedbackMessage = "Ukończono cotygodniowy audyt Dietetyka AI!"
                )
            }
        }
    }

    fun clearDietitianAnalysis() {
        _uiState.update { it.copy(dietitianAnalysisResult = null) }
    }

    fun loginUser(email: String, password: String): String? {
        val (account, error) = com.example.data.local.AppRecipeStorage.loginUser(
            context = getApplication(),
            emailInput = email,
            passwordInput = password
        )
        if (error != null) {
            _uiState.update { it.copy(feedbackMessage = error) }
            return error
        }
        if (account != null) {
            setupUserShoppingSync(account.email)
            setupUserMealHistorySync(account.email)
            NotificationRepository.startRealtimeListener(getApplication(), account.email)
            _uiState.update {
                it.copy(
                    isUserLoggedIn = true,
                    currentUser = account,
                    isSessionExpiringSoon = false,
                    sessionExpiredMessage = null,
                    shouldRequestNotificationPermission = true,
                    showNotificationPermissionDialog = true,
                    feedbackMessage = if (account.isAdmin) {
                        "Zalogowano do panelu Administratora (${account.email}). Pełne uprawnienia aktywne."
                    } else {
                        "Witaj ${account.name}! Zalogowano pomyślnie."
                    }
                )
            }
        }
        return null
    }

    fun registerUser(email: String, name: String, password: String, passwordConfirm: String): String? {
        val (account, error) = com.example.data.local.AppRecipeStorage.registerUser(
            context = getApplication(),
            emailInput = email,
            nameInput = name,
            passwordInput = password,
            passwordConfirmInput = passwordConfirm
        )
        if (error != null) {
            _uiState.update { it.copy(feedbackMessage = error) }
            return error
        }
        if (account != null) {
            setupUserShoppingSync(account.email)
            setupUserMealHistorySync(account.email)
            NotificationRepository.startRealtimeListener(getApplication(), account.email)
            _uiState.update {
                it.copy(
                    isUserLoggedIn = true,
                    currentUser = account,
                    isSessionExpiringSoon = false,
                    sessionExpiredMessage = null,
                    shouldRequestNotificationPermission = true,
                    showNotificationPermissionDialog = true,
                    feedbackMessage = "Konto dla ${account.name} zostało pomyślnie utworzone!"
                )
            }
        }
        return null
    }

    fun dismissNotificationPermissionDialog() {
        _uiState.update { it.copy(showNotificationPermissionDialog = false, shouldRequestNotificationPermission = false) }
    }

    fun onNotificationPermissionHandled() {
        _uiState.update { it.copy(shouldRequestNotificationPermission = false) }
    }

    fun openMessagesScreen() {
        _uiState.update { it.copy(isMessagesScreenOpen = true) }
    }

    fun closeMessagesScreen() {
        _uiState.update { it.copy(isMessagesScreenOpen = false) }
    }

    fun markMessageAsRead(messageId: String) {
        NotificationRepository.markMessageAsRead(getApplication(), messageId)
    }

    fun markAllMessagesAsRead() {
        NotificationRepository.markAllAsRead(getApplication())
    }

    fun refreshMessages() {
        NotificationRepository.startRealtimeListener(getApplication(), _uiState.value.currentUser?.email)
    }

    fun checkAppVersionUpdates() {
        AppVersionManager.checkRemoteVersionStatus { status ->
            _uiState.update { it.copy(appUpdateStatus = status) }
        }
    }

    fun publishAppVersion(
        versionName: String,
        versionCode: Long,
        apkUrl: String,
        releaseNotes: String
    ) {
        AppVersionManager.publishVersionToFirebase(
            versionName = versionName,
            versionCode = versionCode,
            apkUrl = apkUrl,
            releaseNotes = releaseNotes,
            onSuccess = {
                checkAppVersionUpdates()
                _uiState.update {
                    it.copy(feedbackMessage = "Pomyślnie zaktualizowano wersję aplikacji w chmurze Firebase!")
                }
            },
            onError = { err ->
                _uiState.update {
                    it.copy(feedbackMessage = "Błąd zapisu wersji: $err")
                }
            }
        )
    }

    fun onAdminNotificationSent(message: String) {
        _uiState.update { it.copy(feedbackMessage = message) }
    }

    fun authenticateUser(email: String, password: String, name: String = ""): String? {
        return loginUser(email, password)
    }

    fun loginWithGoogle(email: String, name: String) {
        // Zabezpieczenie: logowanie wymaga autoryzacji hasłem
        loginUser(email, "")
    }

    fun logout() {
        sessionMonitorJob?.cancel()
        sessionMonitorJob = null
        shoppingListenerRegistration?.remove()
        shoppingListenerRegistration = null
        mealHistoryListenerRegistration?.remove()
        mealHistoryListenerRegistration = null
        com.example.util.AppSessionManager.clearSession(getApplication())
        com.example.data.local.AppRecipeStorage.saveLoggedInUser(getApplication(), null)
        _uiState.update {
            it.copy(
                isUserLoggedIn = false,
                currentUser = null,
                selectedRecipeId = null,
                plannedMeals = emptyList(),
                shoppingCheckedIds = emptySet(),
                mealHistory = emptyList(),
                dietitianAnalysisResult = null,
                isCookingHandsFree = false,
                sessionExpiredMessage = null,
                feedbackMessage = "Wylogowano pomyślnie z FitKuchnia."
            )
        }
    }

    /**
     * Włącza lub wyłącza blokadę wygaszania ekranu (tryb kuchenny).
     * Zapobiega wygaszaniu wyświetlacza telefonu podczas gotowania brudnymi rękami.
     */
    fun toggleKeepScreenOn(enabled: Boolean) {
        com.example.util.AppSessionManager.setKeepScreenOnEnabled(getApplication(), enabled)
        _uiState.update {
            it.copy(
                keepScreenOn = enabled,
                feedbackMessage = if (enabled) {
                    "Tryb kuchenny aktywny: Ekran telefonu nie wygasa podczas korzystania z aplikacji!"
                } else {
                    "Wyłączono stałe podświetlenie ekranu (standardowe wygaszanie systemu)."
                }
            )
        }
    }

    fun onUserActivity() {}
    fun extendSession() {}
    fun updateSessionTimeout(minutes: Int) {}
    fun checkSessionExpiration() {}
    fun testExpireSessionNow() {}
    fun dismissSessionExpiredMessage() {
        _uiState.update { it.copy(sessionExpiredMessage = null) }
    }

    fun switchUserAccount(user: UserAccount) {
        // Przełączenie konta wymaga ponownego zalogowania
        logout()
    }

    fun switchUser(user: UserAccount) = switchUserAccount(user)

    fun dismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun recalculateRecipeWithAi(recipeId: String) {
        viewModelScope.launch {
            val recipe = _uiState.value.recipes.find { it.id == recipeId } ?: return@launch
            val apiKey = BuildConfig.GEMINI_API_KEY.ifBlank { null }
            val estimated = CulinaryAiEngine.estimateNutrition(
                title = recipe.title,
                description = recipe.description,
                ingredients = recipe.ingredients,
                portions = recipe.basePortions,
                apiKey = apiKey
            )
            val updated = recipe.copy(
                kcalPerPortion = estimated.kcalPerPortion,
                proteinG = estimated.proteinG,
                fatG = estimated.fatG,
                carbsG = estimated.carbsG,
                fitPoints = estimated.fitPoints,
                fitPointsReason = estimated.fitPointsReason
            )
            repository.insertRecipe(updated)
            _uiState.update {
                it.copy(
                    feedbackMessage = "Przeliczono wartości odżywcze dla \"${recipe.title}\": ${estimated.kcalPerPortion} kcal/porcję (B: ${estimated.proteinG}g, T: ${estimated.fatG}g, W: ${estimated.carbsG}g, Fit: ${estimated.fitPoints}/100)."
                )
            }
        }
    }

    fun clearAllUserRecipes() {
        val current = _uiState.value.currentUser
        if (current == null || !current.isAdmin) {
            _uiState.update {
                it.copy(feedbackMessage = "Tylko administrator ma prawo do usuwania wszystkich przepisów!")
            }
            return
        }
        viewModelScope.launch {
            repository.clearAll()
            _uiState.update {
                it.copy(
                    selectedRecipeId = null,
                    favoriteRecipeIds = emptySet(),
                    plannedMeals = emptyList(),
                    feedbackMessage = "Wszystkie przepisy zostały usunięte z bazy danych przez Administratora."
                )
            }
        }
    }

    fun openRecipe(recipeId: String) {
        runCatching {
            val recipe = _uiState.value.recipes.find { it.id == recipeId } ?: RecipeDatabase.findRecipeById(recipeId)
            val defaultPortions = (recipe?.safeBasePortions ?: 2).coerceIn(1, 24)
            _uiState.update {
                it.copy(
                    selectedRecipeId = recipeId,
                    currentRecipePortions = defaultPortions,
                    isCookingHandsFree = false,
                    cookingStepIndex = 0,
                    activeSubstituteAdvice = null
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "Błąd podczas otwierania przepisu $recipeId", e)
            _uiState.update { it.copy(selectedRecipeId = null, feedbackMessage = "Nie udało się otworzyć szczegółów przepisu.") }
        }
    }

    fun closeRecipe() {
        _uiState.update { it.copy(selectedRecipeId = null, isCookingHandsFree = false) }
    }

    fun setPortions(portions: Int) {
        viewModelScope.launch {
            try {
                val safePortions = portions.coerceIn(1, 24)
                _uiState.update { it.copy(isPortionCalculating = true) }
                delay(100)
                _uiState.update { it.copy(currentRecipePortions = safePortions, isPortionCalculating = false) }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isPortionCalculating = false) }
            }
        }
    }

    fun toggleFavorite(recipeId: String) {
        _uiState.update {
            val current = it.favoriteRecipeIds
            val updated = if (current.contains(recipeId)) current - recipeId else current + recipeId
            it.copy(favoriteRecipeIds = updated)
        }
    }

    // Timer functions
    fun startTimer(minutes: Int, title: String) {
        val totalSecs = minutes * 60
        _uiState.update {
            it.copy(
                activeTimer = ActiveTimer(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    totalSeconds = totalSecs,
                    remainingSeconds = totalSecs,
                    isRunning = true
                )
            )
        }
        startTimerTicker()
    }

    private fun startTimerTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val timer = _uiState.value.activeTimer ?: break
                if (timer.isRunning) {
                    if (timer.remainingSeconds > 1) {
                        _uiState.update {
                            it.copy(activeTimer = timer.copy(remainingSeconds = timer.remainingSeconds - 1))
                        }
                    } else {
                        _uiState.update {
                            it.copy(activeTimer = timer.copy(remainingSeconds = 0, isRunning = false))
                        }
                        triggerVibration(getApplication())
                        speakText("Czas minął! Sprawdź potrawę.")
                        break
                    }
                }
            }
        }
    }

    fun toggleTimer() {
        val timer = _uiState.value.activeTimer ?: return
        val newRunning = !timer.isRunning
        _uiState.update { it.copy(activeTimer = timer.copy(isRunning = newRunning)) }
        if (newRunning) startTimerTicker()
    }

    fun resetTimer() {
        val timer = _uiState.value.activeTimer ?: return
        _uiState.update {
            it.copy(activeTimer = timer.copy(remainingSeconds = timer.totalSeconds, isRunning = false))
        }
    }

    fun addMinuteToTimer() {
        val timer = _uiState.value.activeTimer ?: return
        _uiState.update {
            it.copy(
                activeTimer = timer.copy(
                    totalSeconds = timer.totalSeconds + 60,
                    remainingSeconds = timer.remainingSeconds + 60
                )
            )
        }
    }

    fun dismissTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(activeTimer = null) }
    }

    // Hands-free cooking mode
    fun startHandsFreeCooking(recipeId: String) {
        _uiState.update {
            it.copy(
                selectedRecipeId = recipeId,
                isCookingHandsFree = true,
                cookingStepIndex = 0
            )
        }
        readCurrentStepAloud()
    }

    fun exitHandsFreeCooking() {
        tts?.stop()
        _uiState.update { it.copy(isCookingHandsFree = false) }
    }

    fun nextCookingStep(totalSteps: Int) {
        val current = _uiState.value.cookingStepIndex
        if (current < totalSteps - 1) {
            _uiState.update { it.copy(cookingStepIndex = current + 1) }
            readCurrentStepAloud()
        }
    }

    fun prevCookingStep() {
        val current = _uiState.value.cookingStepIndex
        if (current > 0) {
            _uiState.update { it.copy(cookingStepIndex = current - 1) }
            readCurrentStepAloud()
        }
    }

    fun repeatCookingStep() {
        readCurrentStepAloud()
    }

    private fun readCurrentStepAloud() {
        try {
            val recipe = _uiState.value.recipes.find { it.id == _uiState.value.selectedRecipeId } ?: return
            val scaled = PortionCalculator.scaleRecipe(recipe, _uiState.value.currentRecipePortions)
            val stepIdx = _uiState.value.cookingStepIndex
            if (stepIdx in scaled.steps.indices) {
                val step = scaled.steps[stepIdx]
                speakText("Krok ${step.stepNumber}: ${step.text}")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Błąd podczas odczytywania kroku na głos", e)
        }
    }

    private fun speakText(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FitKuchniaTTS")
        }
    }

    // Substitutes (Moduł 5)
    fun requestSubstitute(ingredientName: String) {
        val advice = CulinaryAiEngine.getSubstitute(ingredientName)
        _uiState.update { it.copy(activeSubstituteAdvice = advice) }
    }

    fun clearSubstituteAdvice() {
        _uiState.update { it.copy(activeSubstituteAdvice = null) }
    }

    // Fridge / Zero Waste & Ailments (Moduł 1)
    fun searchFridge(query: String) {
        _uiState.update { it.copy(fridgeQuery = query, isFridgeSearching = true) }
        viewModelScope.launch {
            val currentList = _uiState.value.recipes
            val apiKey = BuildConfig.GEMINI_API_KEY.ifBlank { null }
            val (matched, message) = CulinaryAiEngine.searchFridgeAndAilments(query, currentList, apiKey)
            _uiState.update {
                it.copy(
                    fridgeMatchedRecipe = matched,
                    fridgeAiResponse = message,
                    isFridgeSearching = false
                )
            }
        }
    }

    fun clearFridgeSearch() {
        _uiState.update {
            it.copy(
                fridgeQuery = "",
                fridgeAiResponse = null,
                fridgeMatchedRecipe = null,
                isFridgeSearching = false
            )
        }
    }

    // Search and category filters
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleCategoryFilter(category: RecipeCategory) {
        _uiState.update {
            val current = it.selectedCategories
            val updated = if (current.contains(category)) current - category else current + category
            it.copy(selectedCategories = updated)
        }
    }

    // Weekly Planner & Shopping (Moduł 4)
    fun addMealToPlan(day: DayOfWeekPolish, recipe: Recipe, portions: Int) {
        val newMeal = PlannedMeal(
            day = day,
            recipeId = recipe.id,
            recipeTitle = recipe.title,
            portions = portions
        )
        val updatedMeals = _uiState.value.plannedMeals + newMeal
        _uiState.update { it.copy(plannedMeals = updatedMeals) }

        val currentUser = _uiState.value.currentUser
        if (currentUser != null) {
            FirebaseShoppingSync.saveShoppingData(
                context = getApplication(),
                userEmail = currentUser.email,
                plannedMeals = updatedMeals,
                checkedIds = _uiState.value.shoppingCheckedIds
            )
        }
    }

    fun removeMealFromPlan(mealId: String) {
        val updatedMeals = _uiState.value.plannedMeals.filterNot { m -> m.id == mealId }
        _uiState.update { it.copy(plannedMeals = updatedMeals) }

        val currentUser = _uiState.value.currentUser
        if (currentUser != null) {
            FirebaseShoppingSync.saveShoppingData(
                context = getApplication(),
                userEmail = currentUser.email,
                plannedMeals = updatedMeals,
                checkedIds = _uiState.value.shoppingCheckedIds
            )
        }
    }

    fun toggleShoppingItemChecked(itemId: String) {
        val current = _uiState.value.shoppingCheckedIds
        val updated = if (current.contains(itemId)) current - itemId else current + itemId
        _uiState.update { it.copy(shoppingCheckedIds = updated) }

        val currentUser = _uiState.value.currentUser
        if (currentUser != null) {
            FirebaseShoppingSync.saveShoppingData(
                context = getApplication(),
                userEmail = currentUser.email,
                plannedMeals = _uiState.value.plannedMeals,
                checkedIds = updated
            )
        }
    }

    fun clearCheckedShoppingItems() {
        val emptyChecked = emptySet<String>()
        _uiState.update { it.copy(shoppingCheckedIds = emptyChecked) }

        val currentUser = _uiState.value.currentUser
        if (currentUser != null) {
            FirebaseShoppingSync.saveShoppingData(
                context = getApplication(),
                userEmail = currentUser.email,
                plannedMeals = _uiState.value.plannedMeals,
                checkedIds = emptyChecked
            )
        }
    }

    /**
     * Akcja "Zakupy zrobione" - czyści listę zakupów oraz zaplanowane posiłki
     * i trwale usuwa je z bazy Firebase użytkownika.
     */
    fun completeShopping() {
        _uiState.update {
            it.copy(
                plannedMeals = emptyList(),
                shoppingCheckedIds = emptySet(),
                feedbackMessage = "Zakupy zrobione! Lista zakupów i zaplanowane posiłki zostały pomyślnie wyczyszczone."
            )
        }
        val currentUser = _uiState.value.currentUser
        if (currentUser != null) {
            FirebaseShoppingSync.clearShoppingData(getApplication(), currentUser.email)
        }
    }

    // Moduł 4: Recipe Validator
    fun updateCreatorForm(
        title: String? = null,
        ingredients: String? = null,
        steps: String? = null,
        prepTime: String? = null,
        ovenTemp: String? = null
    ) {
        _uiState.update {
            it.copy(
                creatorTitle = title ?: it.creatorTitle,
                creatorIngredients = ingredients ?: it.creatorIngredients,
                creatorSteps = steps ?: it.creatorSteps,
                creatorPrepTime = prepTime ?: it.creatorPrepTime,
                creatorOvenTemp = ovenTemp ?: it.creatorOvenTemp
            )
        }
    }

    fun evaluateUserRecipe() {
        val s = _uiState.value
        _uiState.update { it.copy(creatorIsEvaluating = true) }
        viewModelScope.launch {
            delay(400)
            val res = CulinaryAiEngine.validateUserRecipe(
                title = s.creatorTitle,
                ingredientsText = s.creatorIngredients,
                stepsText = s.creatorSteps,
                prepTime = s.creatorPrepTime,
                ovenTemp = s.creatorOvenTemp
            )
            _uiState.update {
                it.copy(creatorResult = res, creatorIsEvaluating = false)
            }
        }
    }

    // AI Chat Assistant
    fun toggleChatSheet(open: Boolean) {
        _uiState.update { it.copy(isChatSheetOpen = open) }
    }

    fun sendChatMessage(messageText: String) {
        if (messageText.isBlank()) return
        val userMsg = ChatMessage(senderIsUser = true, text = messageText)
        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                isChatLoading = true
            )
        }

        viewModelScope.launch {
            val apiKey = try {
                BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            } catch (_: Exception) {
                null
            }

            val currentList = _uiState.value.recipes
            val reply = CulinaryAiEngine.queryAssistant(messageText, apiKey, currentList)
            val assistantMsg = ChatMessage(senderIsUser = false, text = reply)
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + assistantMsg,
                    isChatLoading = false
                )
            }
        }
    }

    fun forceCloudSync() {
        viewModelScope.launch {
            val isCloud = repository.isCloudConfigured()
            if (!isCloud) {
                _uiState.update {
                    it.copy(
                        feedbackMessage = "Firebase nie jest jeszcze połączony. Wgraj plik google-services.json do folderu app/, aby włączyć chmurę."
                    )
                }
                return@launch
            }

            val synced = repository.syncPersistentStorage()
            _uiState.update {
                it.copy(
                    recipes = synced,
                    isCloudSyncActive = true,
                    cloudSyncStatusText = "Chmura Firebase: Aktywna (Automatyczna synchronizacja na żywo)",
                    feedbackMessage = "Zsynchronizowano pomyślnie z chmurą Firebase!"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cloudListenerRegistration?.remove()
        shoppingListenerRegistration?.remove()
        mealHistoryListenerRegistration?.remove()
        tts?.stop()
        tts?.shutdown()
        timerJob?.cancel()
    }
}
