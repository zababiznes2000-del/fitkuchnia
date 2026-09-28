package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.RecipeDatabase
import com.example.data.UserAccount
import com.example.notifications.FitKuchniaMessagingService
import com.example.notifications.NotificationRepository
import com.example.ui.AppNavTab
import com.example.ui.MainViewModel
import com.example.ui.components.ActiveTimerBanner
import com.example.ui.components.AiChefAssistantSheet
import com.example.ui.components.NotificationPermissionDialog
import com.example.ui.components.RecipeEditorDialog
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        com.example.util.AppVersionManager.checkAndHandleAppUpgrade(applicationContext)
        enableEdgeToEdge()
        setContent {
            FitKuchniaTheme {
                FitKuchniaApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FitKuchniaApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Uprawnienia do powiadomień przy logowaniu/rejestracji (Android 13+ / API 33+) - Zadanie 2
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onNotificationPermissionHandled()
        if (isGranted) {
            FitKuchniaMessagingService.subscribeToAllUsersTopic(context)
            NotificationRepository.registerDeviceToken(context, uiState.currentUser?.email)
        }
    }

    LaunchedEffect(uiState.shouldRequestNotificationPermission) {
        if (uiState.shouldRequestNotificationPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!isGranted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onNotificationPermissionHandled()
                    FitKuchniaMessagingService.subscribeToAllUsersTopic(context)
                    NotificationRepository.registerDeviceToken(context, uiState.currentUser?.email)
                }
            } else {
                viewModel.onNotificationPermissionHandled()
                FitKuchniaMessagingService.subscribeToAllUsersTopic(context)
                NotificationRepository.registerDeviceToken(context, uiState.currentUser?.email)
            }
        }
    }

    // Wyświetlanie powiadomień Snackbar / Toast (m.in. po oznaczeniu posiłku jako zjedzone)
    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage
        if (!msg.isNullOrBlank()) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.dismissFeedback()
        }
    }

    // Tryb kuchenny: Zarządzanie stałym podświetleniem ekranu (FLAG_KEEP_SCREEN_ON)
    val activity = context as? android.app.Activity
    LaunchedEffect(uiState.keepScreenOn) {
        activity?.let { act ->
            if (uiState.keepScreenOn) {
                act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Top-level container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        val currentRecipe = uiState.selectedRecipeId?.let { id ->
            uiState.recipes.find { it.id == id } ?: RecipeDatabase.findRecipeById(id)
        }

        // Auto-cleanup if selected recipe was deleted or cannot be found
        androidx.compose.runtime.LaunchedEffect(uiState.selectedRecipeId, currentRecipe) {
            if (uiState.selectedRecipeId != null && currentRecipe == null) {
                viewModel.closeRecipe()
            }
        }

        if (!uiState.isUserLoggedIn || uiState.currentUser == null) {
            // Wymagany ekran logowania / rejestracji z hasłem i weryfikacją konta
            AuthScreen(
                onLogin = { email, password ->
                    viewModel.loginUser(email, password)
                },
                onRegister = { email, name, password, confirmPassword ->
                    viewModel.registerUser(email, name, password, confirmPassword)
                },
                sessionExpiredMessage = uiState.sessionExpiredMessage,
                onDismissExpiredMessage = { viewModel.dismissSessionExpiredMessage() }
            )
        } else if (uiState.isMessagesScreenOpen) {
            // Ekran wiadomości od administratora i ogłoszeń (Zadanie 3 & Moduł 3)
            MessagesScreen(
                messages = uiState.messages,
                onBack = { viewModel.closeMessagesScreen() },
                onMarkAsRead = { viewModel.markMessageAsRead(it) },
                onMarkAllAsRead = { viewModel.markAllMessagesAsRead() },
                onRefresh = { viewModel.refreshMessages() }
            )
        } else if (uiState.isCookingHandsFree && currentRecipe != null) {
            // Fullscreen Hands-free cooking mode
            HandsFreeCookingScreen(
                recipe = currentRecipe,
                portions = uiState.currentRecipePortions,
                currentStepIndex = uiState.cookingStepIndex,
                onNextStep = { total -> viewModel.nextCookingStep(total) },
                onPrevStep = { viewModel.prevCookingStep() },
                onRepeatStep = { viewModel.repeatCookingStep() },
                onStartTimer = { mins, title -> viewModel.startTimer(mins, title) },
                onExit = { viewModel.exitHandsFreeCooking() }
            )
        } else if (currentRecipe != null) {
            // Recipe detail view
            RecipeDetailScreen(
                recipe = currentRecipe,
                currentUser = uiState.currentUser ?: UserAccount.DEFAULT_USER,
                currentPortions = uiState.currentRecipePortions,
                onPortionsChange = { viewModel.setPortions(it) },
                isCalculating = uiState.isPortionCalculating,
                onStartTimer = { mins, title -> viewModel.startTimer(mins, title) },
                onStartHandsFreeCook = { viewModel.startHandsFreeCooking(currentRecipe.id) },
                onRequestSubstitute = { viewModel.requestSubstitute(it) },
                activeSubstituteAdvice = uiState.activeSubstituteAdvice,
                onClearSubstituteAdvice = { viewModel.clearSubstituteAdvice() },
                isFavorite = uiState.favoriteRecipeIds.contains(currentRecipe.id),
                onToggleFavorite = { viewModel.toggleFavorite(currentRecipe.id) },
                canEdit = viewModel.canEditRecipe(currentRecipe),
                onEditRecipe = { viewModel.openRecipeEditor(currentRecipe) },
                canDelete = viewModel.canDeleteRecipe(currentRecipe),
                onDeleteRecipe = { viewModel.deleteUserRecipe(currentRecipe.id) },
                onApproveRecipe = { viewModel.approveRecipe(currentRecipe.id) },
                onRejectRecipe = { viewModel.rejectRecipe(currentRecipe.id) },
                onRecalculateAi = { viewModel.recalculateRecipeWithAi(currentRecipe.id) },
                onMarkAsEaten = { recipe, portions -> viewModel.markRecipeAsEaten(recipe, portions) },
                isEatenThisWeek = uiState.mealHistory.any { it.recipeId == currentRecipe.id },
                onBack = { viewModel.closeRecipe() }
            )
        } else {
            // Main tabs layout with bottom navigation bar
            Scaffold(
                containerColor = DarkBg,
                contentWindowInsets = WindowInsets.systemBars,
                bottomBar = {
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = TextPrimary,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .testTag("main_bottom_nav")
                    ) {
                        NavigationBarItem(
                            selected = uiState.currentTab == AppNavTab.HOME,
                            onClick = { viewModel.selectTab(AppNavTab.HOME) },
                            icon = { Icon(imageVector = Icons.Default.Explore, contentDescription = "Odkrywaj") },
                            label = { Text("Odkrywaj", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreenBright,
                                selectedTextColor = NeonGreenBright,
                                indicatorColor = NeonGreenContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_home")
                        )

                        NavigationBarItem(
                            selected = uiState.currentTab == AppNavTab.SEARCH,
                            onClick = { viewModel.selectTab(AppNavTab.SEARCH) },
                            icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Szukaj") },
                            label = { Text("Szukaj", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreenBright,
                                selectedTextColor = NeonGreenBright,
                                indicatorColor = NeonGreenContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_search")
                        )

                        NavigationBarItem(
                            selected = uiState.currentTab == AppNavTab.SHOPPING,
                            onClick = { viewModel.selectTab(AppNavTab.SHOPPING) },
                            icon = {
                                BadgedBox(badge = {
                                    if (uiState.plannedMeals.isNotEmpty()) {
                                        Badge(containerColor = NeonGreen, contentColor = Color.Black) {
                                            Text(uiState.plannedMeals.size.toString())
                                        }
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Lista Zakupów")
                                }
                            },
                            label = { Text("Zakupy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreenBright,
                                selectedTextColor = NeonGreenBright,
                                indicatorColor = NeonGreenContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_shopping")
                        )

                        NavigationBarItem(
                            selected = uiState.currentTab == AppNavTab.DIETITIAN,
                            onClick = { viewModel.selectTab(AppNavTab.DIETITIAN) },
                            icon = {
                                BadgedBox(badge = {
                                    if (uiState.mealHistory.isNotEmpty()) {
                                        Badge(containerColor = NeonGreen, contentColor = Color.Black) {
                                            Text(uiState.mealHistory.size.toString())
                                        }
                                    }
                                }) {
                                    Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = "Dietetyk AI")
                                }
                            },
                            label = { Text("Dietetyk", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreenBright,
                                selectedTextColor = NeonGreenBright,
                                indicatorColor = NeonGreenContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_dietitian")
                        )

                        val unreadMessagesCount = uiState.messages.count { !it.isRead }

                        NavigationBarItem(
                            selected = uiState.currentTab == AppNavTab.PROFILE,
                            onClick = { viewModel.selectTab(AppNavTab.PROFILE) },
                            icon = {
                                BadgedBox(badge = {
                                    if (unreadMessagesCount > 0) {
                                        Badge(containerColor = NeonGreen, contentColor = Color.Black) {
                                            Text(unreadMessagesCount.toString())
                                        }
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = "Profil")
                                }
                            },
                            label = { Text("Profil", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreenBright,
                                selectedTextColor = NeonGreenBright,
                                indicatorColor = NeonGreenContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_profile")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (uiState.currentTab) {
                        AppNavTab.HOME -> {
                            HomeScreen(
                                recipes = uiState.recipes,
                                currentUser = uiState.currentUser ?: UserAccount.DEFAULT_USER,
                                onRecipeClick = { viewModel.openRecipe(it) },
                                onAddNewRecipe = { viewModel.toggleRecipeEditor(true) },
                                onOpenAiChat = { viewModel.toggleChatSheet(true) },
                                favoriteIds = uiState.favoriteRecipeIds,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                canEditRecipe = { viewModel.canEditRecipe(it) },
                                onEditRecipe = { viewModel.openRecipeEditor(it) },
                                canDeleteRecipe = { viewModel.canDeleteRecipe(it) },
                                onDeleteRecipe = { viewModel.deleteUserRecipe(it) },
                                onApproveRecipe = { viewModel.approveRecipe(it) },
                                onRejectRecipe = { viewModel.rejectRecipe(it) }
                            )
                        }

                        AppNavTab.SEARCH -> {
                            SearchScreen(
                                recipes = uiState.recipes,
                                searchQuery = uiState.searchQuery,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                selectedCategories = uiState.selectedCategories,
                                onToggleCategory = { viewModel.toggleCategoryFilter(it) },
                                fridgeQuery = uiState.fridgeQuery,
                                onFridgeSearch = { viewModel.searchFridge(it) },
                                fridgeResponse = uiState.fridgeAiResponse,
                                fridgeMatchedRecipe = uiState.fridgeMatchedRecipe,
                                isFridgeSearching = uiState.isFridgeSearching,
                                onClearFridge = { viewModel.clearFridgeSearch() },
                                onRecipeClick = { viewModel.openRecipe(it) },
                                onAddNewRecipe = { viewModel.toggleRecipeEditor(true) },
                                favoriteIds = uiState.favoriteRecipeIds,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                currentUser = uiState.currentUser ?: UserAccount.DEFAULT_USER,
                                canEditRecipe = { viewModel.canEditRecipe(it) },
                                onEditRecipe = { viewModel.openRecipeEditor(it) },
                                canDeleteRecipe = { viewModel.canDeleteRecipe(it) },
                                onDeleteRecipe = { viewModel.deleteUserRecipe(it) }
                            )
                        }

                        AppNavTab.SHOPPING -> {
                            ShoppingPlannerScreen(
                                recipes = uiState.recipes,
                                plannedMeals = uiState.plannedMeals,
                                onAddMeal = { day, recipe, count -> viewModel.addMealToPlan(day, recipe, count) },
                                onRemoveMeal = { viewModel.removeMealFromPlan(it) },
                                checkedShoppingItemIds = uiState.shoppingCheckedIds,
                                onToggleShoppingItem = { viewModel.toggleShoppingItemChecked(it) },
                                onClearCheckedItems = { viewModel.clearCheckedShoppingItems() },
                                onCompleteShopping = { viewModel.completeShopping() },
                                onRecipeClick = { viewModel.openRecipe(it) },
                                onAddNewRecipe = { viewModel.toggleRecipeEditor(true) }
                            )
                        }

                        AppNavTab.DIETITIAN -> {
                            DietitianScreen(
                                weeklyMeals = uiState.mealHistory,
                                availableRecipes = uiState.recipes,
                                analysisResult = uiState.dietitianAnalysisResult,
                                isAnalyzing = uiState.isAnalyzingDiet,
                                onRunAnalysis = { viewModel.runDietitianAnalysis() },
                                onDeleteMeal = { viewModel.removeMealHistoryEntry(it) },
                                onOpenRecipe = { recipeId -> viewModel.openRecipe(recipeId) }
                            )
                        }

                        AppNavTab.PROFILE -> {
                            ProfileScreen(
                                recipes = uiState.recipes,
                                currentUser = uiState.currentUser ?: UserAccount.DEFAULT_USER,
                                onSwitchUser = { viewModel.switchUser(it) },
                                onLogout = { viewModel.logout() },
                                onAddNewRecipe = { viewModel.toggleRecipeEditor(true) },
                                canDeleteRecipe = { viewModel.canDeleteRecipe(it) },
                                onDeleteRecipe = { viewModel.deleteUserRecipe(it) },
                                onEditRecipe = { viewModel.openRecipeEditor(it) },
                                onClearAllRecipes = { viewModel.clearAllUserRecipes() },
                                onApproveRecipe = { viewModel.approveRecipe(it) },
                                onRejectRecipe = { viewModel.rejectRecipe(it) },
                                favoriteRecipeIds = uiState.favoriteRecipeIds,
                                onRecipeClick = { viewModel.openRecipe(it) },
                                creatorTitle = uiState.creatorTitle,
                                creatorIngredients = uiState.creatorIngredients,
                                creatorSteps = uiState.creatorSteps,
                                creatorPrepTime = uiState.creatorPrepTime,
                                creatorOvenTemp = uiState.creatorOvenTemp,
                                onUpdateCreatorForm = { t, i, s, time, temp ->
                                    viewModel.updateCreatorForm(t, i, s, time, temp)
                                },
                                onEvaluateRecipe = { viewModel.evaluateUserRecipe() },
                                creatorResult = uiState.creatorResult,
                                creatorIsEvaluating = uiState.creatorIsEvaluating,
                                isCloudSyncActive = uiState.isCloudSyncActive,
                                cloudSyncStatusText = uiState.cloudSyncStatusText,
                                onForceCloudSync = { viewModel.forceCloudSync() },
                                onNotificationSent = { viewModel.onAdminNotificationSent(it) },
                                messages = uiState.messages,
                                onOpenMessages = { viewModel.openMessagesScreen() },
                                appUpdateStatus = uiState.appUpdateStatus,
                                onCheckForUpdates = { viewModel.checkAppVersionUpdates() },
                                onPublishAppVersion = { name, code, url, notes ->
                                    viewModel.publishAppVersion(name, code, url, notes)
                                },
                                keepScreenOn = uiState.keepScreenOn,
                                onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn(it) }
                            )
                        }
                    }
                }
            }
        }

        // Post-Registration Notification Permission Dialog
        if (uiState.showNotificationPermissionDialog) {
            NotificationPermissionDialog(
                onDismiss = { viewModel.dismissNotificationPermissionDialog() },
                onPermissionGranted = { viewModel.dismissNotificationPermissionDialog() }
            )
        }

        // Floating Active Timer Banner (Blueprint Feature 2)
        ActiveTimerBanner(
            timer = uiState.activeTimer,
            onToggle = { viewModel.toggleTimer() },
            onReset = { viewModel.resetTimer() },
            onAddMinute = { viewModel.addMinuteToTimer() },
            onDismiss = { viewModel.dismissTimer() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp)
        )

        // Recipe Creator / Editor Dialog for adding or editing recipes
        if (uiState.isRecipeEditorOpen) {
            RecipeEditorDialog(
                currentUser = uiState.currentUser ?: UserAccount.DEFAULT_USER,
                recipeToEdit = uiState.editingRecipe,
                onDismiss = { viewModel.closeRecipeEditor() },
                onSaveRecipe = { savedRecipe ->
                    viewModel.saveUserRecipe(savedRecipe)
                }
            )
        }

        // AI Chef Assistant Bottom Sheet Modal
        AiChefAssistantSheet(
            isOpen = uiState.isChatSheetOpen,
            messages = uiState.chatMessages,
            isLoading = uiState.isChatLoading,
            onSendMessage = { viewModel.sendChatMessage(it) },
            onClose = { viewModel.toggleChatSheet(false) }
        )
    }
}
