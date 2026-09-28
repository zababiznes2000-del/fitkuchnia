package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ai.RecipeValidationResult
import com.example.data.Recipe
import com.example.data.RecipeModerationStatus
import com.example.data.UserAccount
import com.example.data.model.AppMessage
import com.example.ui.components.AdminPushNotificationDialog
import com.example.ui.components.DishImageView
import com.example.ui.theme.*
import com.example.util.AppVersionManager
import com.example.util.FileDownloadHelper

@Composable
fun ProfileScreen(
    recipes: List<Recipe>,
    currentUser: UserAccount = UserAccount.DEFAULT_USER,
    onSwitchUser: ((UserAccount) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onAddNewRecipe: () -> Unit,
    canDeleteRecipe: (Recipe) -> Boolean = { true },
    onDeleteRecipe: (String) -> Unit,
    onEditRecipe: ((Recipe) -> Unit)? = null,
    onClearAllRecipes: () -> Unit,
    onApproveRecipe: ((String) -> Unit)? = null,
    onRejectRecipe: ((String) -> Unit)? = null,
    favoriteRecipeIds: Set<String>,
    onRecipeClick: (String) -> Unit,
    creatorTitle: String,
    creatorIngredients: String,
    creatorSteps: String,
    creatorPrepTime: String,
    creatorOvenTemp: String,
    onUpdateCreatorForm: (title: String?, ings: String?, steps: String?, time: String?, temp: String?) -> Unit,
    onEvaluateRecipe: () -> Unit,
    creatorResult: RecipeValidationResult?,
    creatorIsEvaluating: Boolean,
    isCloudSyncActive: Boolean = false,
    cloudSyncStatusText: String = "",
    onForceCloudSync: (() -> Unit)? = null,
    onNotificationSent: ((String) -> Unit)? = null,
    messages: List<AppMessage> = emptyList(),
    onOpenMessages: () -> Unit = {},
    appUpdateStatus: AppVersionManager.AppUpdateStatus = AppVersionManager.AppUpdateStatus.Checking,
    onCheckForUpdates: () -> Unit = {},
    onPublishAppVersion: ((versionName: String, versionCode: Long, apkUrl: String, notes: String) -> Unit)? = null,
    sessionTimeoutMinutes: Int = 8,
    sessionRemainingSeconds: Long = 8 * 60L,
    onUpdateSessionTimeout: ((Int) -> Unit)? = null,
    onExtendSession: (() -> Unit)? = null,
    onTestExpireSession: (() -> Unit)? = null,
    keepScreenOn: Boolean = true,
    onToggleKeepScreenOn: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreatorSheet by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showAdminPushDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val pendingRecipes = remember(recipes) {
        recipes.filter { it.moderationStatus == RecipeModerationStatus.PENDING }
    }

    val favoriteRecipes = remember(recipes, favoriteRecipeIds) {
        recipes.filter { favoriteRecipeIds.contains(it.id) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // 0. Auto-check statusu wersji APK w chmurze Firebase (Zadanie 5)
        item {
            Surface(
                color = when (appUpdateStatus) {
                    is AppVersionManager.AppUpdateStatus.UpdateAvailable -> Color(0xFF2A190C)
                    is AppVersionManager.AppUpdateStatus.UpToDate -> DarkSurface
                    is AppVersionManager.AppUpdateStatus.Error -> Color(0xFF2A1515)
                    is AppVersionManager.AppUpdateStatus.Checking -> DarkSurface
                },
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (appUpdateStatus) {
                        is AppVersionManager.AppUpdateStatus.UpdateAvailable -> AmberEnergy
                        is AppVersionManager.AppUpdateStatus.UpToDate -> NeonGreen.copy(alpha = 0.4f)
                        is AppVersionManager.AppUpdateStatus.Error -> Color(0xFFFF5252).copy(alpha = 0.5f)
                        is AppVersionManager.AppUpdateStatus.Checking -> DarkCardBorder
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("app_version_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (appUpdateStatus) {
                        is AppVersionManager.AppUpdateStatus.UpdateAvailable -> {
                            val status = appUpdateStatus as AppVersionManager.AppUpdateStatus.UpdateAvailable
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(AmberEnergy.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            tint = AmberEnergy,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "NOWA WERSJA DOSTĘPNA!",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = AmberEnergy,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "Wersja v${status.newVersionName}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                Surface(
                                    color = AmberEnergy.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Build ${status.newVersionCode}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberEnergy,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Twoja wersja: v${status.currentVersionName} (Build ${status.currentVersionCode}). Pobierz najnowszy plik APK, aby otrzymać nowe funkcje i poprawki.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )

                            if (status.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Zmiany: ${status.releaseNotes}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        FileDownloadHelper.downloadFile(
                                            context = context,
                                            url = status.apkUrl,
                                            suggestedFileName = "FitKuchnia-v${status.newVersionName}.apk",
                                            isApk = true
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberEnergy,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("download_latest_apk_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pobierz APK v${status.newVersionName}", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onCheckForUpdates,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Odśwież",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        is AppVersionManager.AppUpdateStatus.UpToDate -> {
                            val status = appUpdateStatus as AppVersionManager.AppUpdateStatus.UpToDate
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonGreenBright,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Aplikacja jest aktualna (v${status.versionName})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Build ${status.versionCode} • Najnowsza wersja z chmury Firebase",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onCheckForUpdates,
                                    modifier = Modifier.size(36.dp).testTag("check_updates_refresh_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sprawdź ponownie",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        is AppVersionManager.AppUpdateStatus.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NeonGreenBright,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sprawdzanie wersji w chmurze Firebase...",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        is AppVersionManager.AppUpdateStatus.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Wersja aplikacji: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }

                                TextButton(
                                    onClick = onCheckForUpdates,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Sprawdź", color = NeonGreenBright, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Administrator: szybkie ustawienie bieżącej wersji jako oficjalna w chmurze
                    if (currentUser.isAdmin && onPublishAppVersion != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DarkCardBorder))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Admin: Ustaw v${BuildConfig.VERSION_NAME} w Firebase",
                                fontSize = 11.sp,
                                color = AmberEnergy,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = {
                                    onPublishAppVersion(
                                        BuildConfig.VERSION_NAME,
                                        BuildConfig.VERSION_CODE.toLong(),
                                        AppVersionManager.DEFAULT_APK_DOWNLOAD_URL,
                                        "Oficjalna aktualizacja FitKuchnia v${BuildConfig.VERSION_NAME}"
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Opublikuj", color = NeonGreenBright, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Moduł Wiadomości od Administratora (Zadanie 3 & Moduł 3)
        item {
            val unreadCount = remember(messages) { messages.count { !it.isRead } }

            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (unreadCount > 0) NeonGreen.copy(alpha = 0.6f) else DarkCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clickable { onOpenMessages() }
                    .testTag("messages_module_entry_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (unreadCount > 0) NeonGreenContainer else DarkSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (unreadCount > 0) NeonGreenBright else DarkCardBorder,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Email,
                                contentDescription = "Wiadomości",
                                tint = if (unreadCount > 0) NeonGreenBright else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Wiadomości i Komunikaty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = NeonGreen,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "$unreadCount NOWE",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (unreadCount > 0) {
                                    "Masz $unreadCount nowe wiadomości od administratora"
                                } else if (messages.isNotEmpty()) {
                                    "${messages.size} odebranych powiadomień • Kliknij, aby przejrzeć"
                                } else {
                                    "Brak nowych wiadomości • Sprawdź historię ogłoszeń"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (unreadCount > 0) NeonGreenBright else TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Otwórz wiadomości",
                        tint = if (unreadCount > 0) NeonGreenBright else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // User Profile Header Card
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (currentUser.isAdmin) NeonGreen.copy(alpha = 0.5f) else DarkCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(NeonGreenContainer)
                                .border(2.dp, if (currentUser.isAdmin) NeonGreen else DarkCardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentUser.isAdmin) Icons.Default.Shield else Icons.Default.Person,
                                contentDescription = "Awatar",
                                tint = NeonGreenBright,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                if (currentUser.isAdmin) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = NeonGreenContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "ADMIN",
                                            color = NeonGreenBright,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = currentUser.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentUser.isAdmin) NeonGreenBright else TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (currentUser.isAdmin)
                                    "Uprawnienia: Pełna moderacja i usuwanie dowolnych przepisów"
                                else
                                    "Uprawnienia: Dodawanie przepisów do moderacji, usuwanie tylko własnych",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Account Session Card with Logout
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = if (currentUser.isAdmin) AmberEnergy else NeonGreenBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (currentUser.isAdmin) "KONTO: SUPER ADMINISTRATOR" else "KONTO: UŻYTKOWNIK",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentUser.isAdmin) AmberEnergy else NeonGreenBright,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                if (onLogout != null) {
                                    OutlinedButton(
                                        onClick = onLogout,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFF5252)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                        modifier = Modifier.height(32.dp).testTag("logout_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Logout,
                                            contentDescription = "Wyloguj",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Wyloguj", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Zalogowano: ${currentUser.email}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tryb kuchenny: Ekran nie wygasa (stałe podświetlenie ekranu podczas gotowania)
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (keepScreenOn) NeonGreen.copy(alpha = 0.5f) else DarkCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("keep_screen_on_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (keepScreenOn) NeonGreenContainer else DarkSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = if (keepScreenOn) NeonGreenBright else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "TRYB KUCHENNY: EKRAN NIE WYGASA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (keepScreenOn) NeonGreenBright else TextPrimary,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = if (keepScreenOn) "Ekran telefonu pozostaje stale włączony" else "Wygaszanie ekranu wg ustawień telefonu",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                                Switch(
                                    checked = keepScreenOn,
                                    onCheckedChange = { onToggleKeepScreenOn?.invoke(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = NeonGreen,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = DarkSurface
                                    ),
                                    modifier = Modifier.testTag("toggle_keep_screen_on")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                color = if (keepScreenOn) NeonGreenContainer.copy(alpha = 0.3f) else DarkSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (keepScreenOn) NeonGreen.copy(alpha = 0.3f) else DarkCardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💡",
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Nie musisz ciągle dotykać ekranu brudnymi rękami podczas przygotowywania posiłków. Aplikacja utrzymuje ekran włączony i nigdy Cię nie wylogowuje.",
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gamification stats bar
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Średnia Fit Punktów", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("92 / 100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NeonGreenBright)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(DarkCardBorder))
                            Column {
                                Text("Wszystkie przepisy", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${recipes.size} dań", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(DarkCardBorder))
                            Column {
                                Text("Status", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(if (currentUser.isAdmin) "ADMIN" else "USER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = NeonGreen)
                            }
                        }
                    }
                }
            }
        }

        // Section: Synchronizacja w chmurze (Firebase Firestore)
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCloudSyncActive) NeonGreen.copy(alpha = 0.6f) else AmberEnergy.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .testTag("firebase_cloud_sync_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (isCloudSyncActive) NeonGreenBright else AmberEnergy,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "WSPÓLNA BAZA W CHMURZE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            color = if (isCloudSyncActive) NeonGreenContainer else AmberEnergy.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isCloudSyncActive) "AKTYWNA" else "WYMAGA KONFIGURACJI",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCloudSyncActive) NeonGreenBright else AmberEnergy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (cloudSyncStatusText.isNotBlank()) {
                        Text(
                            text = cloudSyncStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCloudSyncActive) NeonGreenBright else AmberEnergy,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = if (isCloudSyncActive) {
                            "Połączenie z Google Firebase jest aktywne! Wszystkie nowe przepisy i zdjęcia są automatycznie synchronizowane na żywo ze wszystkimi innymi telefonami."
                        } else {
                            "Jeśli widzisz komunikat o braku uprawnień (PERMISSION_DENIED), wejdź w konsolę Firebase (console.firebase.google.com) -> Firestore Database -> zakładka Rules (Reguły) i zmień regułę na 'allow read, write: if true;' oraz kliknij 'Publish'."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { onForceCloudSync?.invoke() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("force_cloud_sync_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isCloudSyncActive) NeonGreenBright else AmberEnergy
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCloudSyncActive) NeonGreenBright.copy(alpha = 0.6f) else AmberEnergy.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCloudSyncActive) "Wymuś synchronizację z Firebase" else "Sprawdź połączenie z Firebase",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Section: Panel Administratora (Kolejka Moderacji)
        if (currentUser.isAdmin && pendingRecipes.isNotEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberEnergy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, tint = AmberEnergy)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "PANEL ADMINISTRATORA",
                                        fontWeight = FontWeight.Black,
                                        color = AmberEnergy,
                                        fontSize = 11.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Przepisy oczekujące na akceptację (${pendingRecipes.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pendingRecipes.forEach { pendingRecipe ->
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onRecipeClick(pendingRecipe.id) }
                                        ) {
                                            DishImageView(
                                                recipe = pendingRecipe,
                                                contentDescription = pendingRecipe.title,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onRecipeClick(pendingRecipe.id) }
                                        ) {
                                            Text(
                                                text = pendingRecipe.title,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Autor: ${pendingRecipe.author} (${pendingRecipe.authorEmail})",
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${pendingRecipe.prepTimeMinutes} min • ${pendingRecipe.fitPoints}/100 FIT",
                                                color = NeonGreenBright,
                                                fontSize = 10.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (currentUser.isAdmin && onEditRecipe != null) {
                                                IconButton(
                                                    onClick = { onEditRecipe(pendingRecipe) },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(DarkSurfaceVariant)
                                                        .testTag("admin_profile_edit_pending_${pendingRecipe.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edytuj przepis (Admin)",
                                                        tint = NeonGreenBright,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { onApproveRecipe?.invoke(pendingRecipe.id) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(NeonGreenContainer)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Zatwierdź",
                                                    tint = NeonGreenBright,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { onRejectRecipe?.invoke(pendingRecipe.id) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF3B1E1E))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Odrzuć",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Panel Wysyłania Powiadomień Push (Tylko dla Administratora)
        if (currentUser.isAdmin) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberEnergy.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("admin_push_notification_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AmberEnergy.copy(alpha = 0.15f))
                                    .border(1.dp, AmberEnergy.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = AmberEnergy,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "POWIADOMIENIA PUSH (FCM)",
                                    fontWeight = FontWeight.Black,
                                    color = AmberEnergy,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Wyślij ogłoszenie do wszystkich",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Wyślij natychmiastowe powiadomienie push do wszystkich użytkowników FitKuchnia. Wymaga autoryzacji hasłem administratora.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { showAdminPushDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberEnergy,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("open_admin_push_dialog_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wyślij powiadomienie do wszystkich",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Section: System Prestiżu & Odznaki
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.MilitaryTech, contentDescription = null, tint = NeonGreenBright)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYSTEM PRESTIŻU I ODZNAKI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Odblokowywane za zatwierdzone przepisy i dbanie o Fit Punkty:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BadgeCard(
                        title = "Ekspert Zero Waste",
                        description = "Dania ratunkowe z resztek lodówki",
                        isUnlocked = true
                    )
                    BadgeCard(
                        title = "Mistrz Fit Punktów (100)",
                        description = "Przepisy o idealnym makro i gęstości",
                        isUnlocked = true
                    )
                    BadgeCard(
                        title = "Rygorystyczny Szef Kuchni",
                        description = "Przepis zaakceptowany przez Administrację",
                        isUnlocked = creatorResult?.isValid == true
                    )
                }
            }
        }

        // Section: Bezlitosny Korektor Przepisów (Moduł 4) Action Banner
        item {
            Surface(
                color = DarkSurface,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreenContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonGreenBright,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "MODUŁ 4: BEZLITOSNY KOREKTOR",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreenBright
                                )
                                Text(
                                    text = "Zgłoś swój autorski przepis",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Zanim Twój przepis trafi do testów Administracji, silnik AI sprawdzi luki logiczne, brakujące czasy, temperatury oraz absurdalne proporcje.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showCreatorSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("open_creator_btn")
                    ) {
                        Text("Otwórz formularz twórcy i weryfikuj", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Moja Baza Przepisów (Room Persistence)
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, tint = NeonGreenBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MOJA BAZA PRZEPISÓW (${recipes.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (recipes.isNotEmpty() && currentUser.isAdmin) {
                            TextButton(onClick = { showClearConfirmDialog = true }) {
                                Text("Wyczyść wszystko", color = Color(0xFFFF5252), fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = onAddNewRecipe,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dodaj", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (recipes.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Brak autorskich przepisów w pamięci telefonu.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onAddNewRecipe,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("+ Dodaj swój przepis", color = NeonGreenBright, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(recipes, key = { "stored_${it.id}" }) { recipe ->
                val isOwnRecipe = recipe.authorEmail.isNotBlank() && recipe.authorEmail.equals(currentUser.email, ignoreCase = true)
                val hasDeleteRights = canDeleteRecipe(recipe)

                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (recipe.moderationStatus == RecipeModerationStatus.PENDING) AmberEnergy.copy(alpha = 0.6f) else DarkCardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRecipeClick(recipe.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            DishImageView(
                                recipe = recipe,
                                contentDescription = recipe.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = recipe.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                if (recipe.moderationStatus == RecipeModerationStatus.PENDING) {
                                    Surface(
                                        color = AmberEnergy.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Oczekuje",
                                            color = AmberEnergy,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        color = NeonGreenContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Publiczny",
                                            color = NeonGreenBright,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (isOwnRecipe) "Twój przepis (${recipe.author})" else "Autor: ${recipe.author}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOwnRecipe) NeonGreenBright else TextMuted
                            )

                            Text(
                                text = "${recipe.prepTimeMinutes} min • ${recipe.kcalPerPortion} kcal • ${recipe.fitPoints}/100 FIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if ((currentUser.isAdmin || isOwnRecipe) && onEditRecipe != null) {
                            IconButton(
                                onClick = { onEditRecipe(recipe) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("profile_edit_recipe_${recipe.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edytuj przepis",
                                    tint = NeonGreenBright,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (hasDeleteRights) {
                            IconButton(
                                onClick = { onDeleteRecipe(recipe.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Usuń przepis",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Brak uprawnień do usunięcia przepisu innego autora",
                                    tint = TextMuted.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Section: Ulubione Przepisy
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = NeonGreenBright)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ULUBIONE PRZEPISY (${favoriteRecipes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        if (favoriteRecipes.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nie dodałeś jeszcze żadnych ulubionych dań. Kliknij serduszko przy przepisie!",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(favoriteRecipes, key = { it.id }) { recipe ->
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onRecipeClick(recipe.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipe.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${recipe.prepTimeMinutes} min • ${recipe.kcalPerPortion} kcal • ${recipe.fitPoints}/100 FIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreenBright
                            )
                        }
                        Text("Zobacz >", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = NeonGreenBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Wersja APK: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val buildTime = try { BuildConfig.BUILD_TIME_STRING } catch (_: Throwable) { "" }
                        if (buildTime.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kompilacja: $buildTime",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                AppVersionManager.clearAppDiskCache(context)
                                AppVersionManager.clearCoilImageCache(context)
                                onForceCloudSync?.invoke()
                                Toast.makeText(
                                    context,
                                    "Pamięć podręczna (Cache) wyczyszczona! Dane zsynchronizowane.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .testTag("clear_cache_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wyczyść pamięć podręczną (Cache)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "FitKuchnia • Gotuj zdrowo i smacznie",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }

    // Modal Sheet: Recipe Creator & Module 4 Strict Validator
    if (showCreatorSheet) {
        RecipeCreatorDialog(
            title = creatorTitle,
            ingredients = creatorIngredients,
            steps = creatorSteps,
            prepTime = creatorPrepTime,
            ovenTemp = creatorOvenTemp,
            onUpdate = onUpdateCreatorForm,
            onEvaluate = onEvaluateRecipe,
            result = creatorResult,
            isEvaluating = creatorIsEvaluating,
            onDismiss = { showCreatorSheet = false }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Wyczyścić wszystkie przepisy?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Czy na pewno chcesz usunąć wszystkie zapisane przepisy z bazy danych w telefonie? Ta operacja jest nieodwracalna.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllRecipes()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("Usuń wszystko", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAdminPushDialog && currentUser.isAdmin) {
        AdminPushNotificationDialog(
            adminEmail = currentUser.email,
            onDismiss = { showAdminPushDialog = false },
            onNotificationSent = { message ->
                onNotificationSent?.invoke(message)
            }
        )
    }
}

@Composable
fun BadgeCard(
    title: String,
    description: String,
    isUnlocked: Boolean
) {
    Surface(
        color = if (isUnlocked) DarkSurface else DarkSurface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isUnlocked) NeonGreen.copy(alpha = 0.6f) else DarkCardBorder
        ),
        modifier = Modifier.width(180.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isUnlocked) NeonGreenContainer else DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MilitaryTech,
                        contentDescription = null,
                        tint = if (isUnlocked) NeonGreenBright else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isUnlocked) "ODBLOKOWANE" else "ZABLOKOWANE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) NeonGreenBright else TextMuted
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) TextPrimary else TextSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
fun RecipeCreatorDialog(
    title: String,
    ingredients: String,
    steps: String,
    prepTime: String,
    ovenTemp: String,
    onUpdate: (title: String?, ings: String?, steps: String?, time: String?, temp: String?) -> Unit,
    onEvaluate: () -> Unit,
    result: RecipeValidationResult?,
    isEvaluating: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Moduł 4: Bezlitosny Korektor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreenBright
                )
                Text(
                    text = "Weryfikacja autorskiego przepisu przed wysyłką do Administracji",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { onUpdate(it, null, null, null, null) },
                    label = { Text("Nazwa dania") },
                    placeholder = { Text("np. Zupa krem z pieczonych pomidorów") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("creator_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prepTime,
                        onValueChange = { onUpdate(null, null, null, it, null) },
                        label = { Text("Czas (min)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ovenTemp,
                        onValueChange = { onUpdate(null, null, null, null, it) },
                        label = { Text("Temp. pieczenia") },
                        placeholder = { Text("np. 180°C") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { onUpdate(null, it, null, null, null) },
                    label = { Text("Składniki (z gramaturami, każdy w nowej linii)") },
                    placeholder = { Text("np.\n500g pomidorów\n1 cebula\n2 ząbki czosnku\n15ml oliwy") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("creator_ings_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = steps,
                    onValueChange = { onUpdate(null, null, it, null, null) },
                    label = { Text("Instrukcja przygotowania krok po kroku") },
                    placeholder = { Text("np. Rozgrzej piekarnik do 180°C. Piecz pomidory przez 25 minut. Zblenduj blenderem ręcznym.") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("creator_steps_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onEvaluate,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("evaluate_recipe_btn")
                ) {
                    if (isEvaluating) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Przetestuj z Bezlitosnym Korektorem", fontWeight = FontWeight.Bold)
                    }
                }

                if (result != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (result.isValid) Color(0xFF0D2514) else Color(0xFF2B1414),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (result.isValid) NeonGreen else Color(0xFFFF5252)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("validation_result_box")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (result.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (result.isValid) NeonGreenBright else Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (result.isValid) "ZATWIERDZONO PRZEPIS" else "WYMAGA POPRAWEK (MODUŁ 4)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (result.isValid) NeonGreenBright else Color(0xFFFF5252)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = result.feedbackMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = NeonGreenBright)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp)
    )
}
