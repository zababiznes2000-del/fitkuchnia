package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SubstituteAdvice
import com.example.data.*
import com.example.ui.components.DishImageView
import com.example.ui.components.DishVisualResolver
import com.example.ui.components.PortionSelectorCard
import com.example.ui.components.RecipeStatBadgeOverlay
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipe: Recipe,
    currentUser: UserAccount = UserAccount.DEFAULT_USER,
    currentPortions: Int,
    onPortionsChange: (Int) -> Unit,
    isCalculating: Boolean,
    onStartTimer: (minutes: Int, title: String) -> Unit,
    onStartHandsFreeCook: () -> Unit,
    onRequestSubstitute: (String) -> Unit,
    activeSubstituteAdvice: SubstituteAdvice?,
    onClearSubstituteAdvice: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    canEdit: Boolean = false,
    onEditRecipe: (() -> Unit)? = null,
    canDelete: Boolean = true,
    onDeleteRecipe: (() -> Unit)? = null,
    onApproveRecipe: (() -> Unit)? = null,
    onRejectRecipe: (() -> Unit)? = null,
    onRecalculateAi: (() -> Unit)? = null,
    onMarkAsEaten: ((Recipe, Int) -> Unit)? = null,
    isEatenThisWeek: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaledData = remember(recipe, currentPortions) {
        try {
            PortionCalculator.scaleRecipe(recipe, currentPortions)
        } catch (_: Throwable) {
            PortionCalculator.createSafeFallback(recipe, currentPortions)
        }
    }

    var showSubstituteDialog by remember { mutableStateOf(false) }
    var substituteCustomInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recipe.safeTitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("recipe_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz",
                            tint = NeonGreen
                        )
                    }
                },
                actions = {
                    if ((currentUser.isAdmin || canEdit) && onEditRecipe != null) {
                        IconButton(
                            onClick = onEditRecipe,
                            modifier = Modifier.testTag("recipe_detail_edit_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edytuj przepis",
                                tint = NeonGreenBright
                            )
                        }
                    }
                    if (onDeleteRecipe != null && (canDelete || currentUser.isAdmin)) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("recipe_detail_delete_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Usuń przepis",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("recipe_detail_favorite_btn")) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Ulubione",
                            tint = if (isFavorite) NeonGreenBright else TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("recipe_detail_scroll"),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Moderation Status Notification Banner
            if (recipe.moderationStatus == RecipeModerationStatus.PENDING) {
                item {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberEnergy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = AmberEnergy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentUser.isAdmin) "OCZEKUJE NA MODERACJĘ" else "STATUS: WERYFIKACJA",
                                    color = AmberEnergy,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (currentUser.isAdmin)
                                    "Przepis zgłoszony przez: ${recipe.author} (${recipe.authorEmail}). Jako Administrator możesz go przetestować, zaakceptować do widoku publicznego lub odrzucić."
                                else
                                    "Przepis został przesłany do weryfikacji przez Administratora. Nie jest jeszcze widoczny dla innych użytkowników.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            if (currentUser.isAdmin) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onApproveRecipe?.invoke() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonGreen,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Zatwierdź i opublikuj", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { onRejectRecipe?.invoke() },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Odrzuć", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hero Dish Image (User Uploaded or Fallback)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF121212))
                ) {
                    DishImageView(
                        recipe = recipe,
                        contentDescription = recipe.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        backgroundColor = Color(0xFF121212)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (!recipe.imagePath.isNullOrBlank()) Icons.Default.CameraAlt else Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = NeonGreenBright,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!recipe.imagePath.isNullOrBlank()) "Autorskie zdjęcie potrawy" else "Zdjęcie potrawy",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (recipe.moderationStatus == RecipeModerationStatus.APPROVED) {
                        Surface(
                            color = NeonGreenContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonGreenBright,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Zatwierdzony",
                                    color = NeonGreenBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Przycisk edycji dla Administratora lub Autora
            if ((currentUser.isAdmin || canEdit) && onEditRecipe != null) {
                item {
                    FilledTonalButton(
                        onClick = onEditRecipe,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonGreenContainer,
                            contentColor = NeonGreenBright
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("recipe_detail_edit_action_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentUser.isAdmin) "Edytuj ten przepis (Administrator)" else "Edytuj ten przepis",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Header Pictograms & Portion Box
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Pictograms row (oven/temp, stopwatch, calories)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time pictogram
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${recipe.prepTimeMinutes} min",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text("Czas", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }

                            // Kcal pictogram
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = NeonGreenBright,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val safePortions = if (scaledData.portions > 0) scaledData.portions else 1
                                val perPortionKcal = (scaledData.totalKcal / safePortions).coerceAtLeast(0)
                                Text(
                                    text = "$perPortionKcal kcal",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text("Porcja", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }

                            // Fit Points pictogram
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${recipe.fitPoints}/100",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreenBright
                                )
                                Text("Fit Punkty", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive portion selector (Kwadracik z cyfrą i steppery)
                        PortionSelectorCard(
                            currentPortions = currentPortions,
                            onPortionsChange = onPortionsChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // CTA Buttons Row: Hands-free "Brudne Ręce" mode & "Ratunek Kulinarny"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartHandsFreeCook,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("hands_free_start_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tryb Brudne Ręce", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showSubstituteDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonGreenBright
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("substitute_rescue_btn")
                    ) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Brak składnika?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Oznaczenie przepisu jako "Zjedzone" (Zadanie 2)
            if (onMarkAsEaten != null) {
                item {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isEatenThisWeek) NeonGreen.copy(alpha = 0.7f) else DarkCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("eaten_status_surface")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isEatenThisWeek) NeonGreenContainer else DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = if (isEatenThisWeek) NeonGreenBright else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Historia posiłków tygodnia",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = if (isEatenThisWeek) "✓ Zjedzone w tym tygodniu" else "Zjadłeś to danie?",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEatenThisWeek) NeonGreenBright else TextPrimary
                                    )
                                }
                            }

                            Button(
                                onClick = { onMarkAsEaten(recipe, currentPortions) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEatenThisWeek) NeonGreenContainer else NeonGreen,
                                    contentColor = if (isEatenThisWeek) NeonGreenBright else Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("mark_recipe_as_eaten_btn")
                            ) {
                                Icon(
                                    imageVector = if (isEatenThisWeek) Icons.Default.CheckCircle else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEatenThisWeek) "Zjedz ponownie" else "Zjedzone",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Fit Points explanation
            item {
                Surface(
                    color = NeonGreenContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💚",
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ocena Odżywcza: ${recipe.fitPoints}/100 Fit Punktów",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreenBright
                            )
                            Text(
                                text = recipe.fitPointsReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // AI Recalculation card
            if (onRecalculateAi != null) {
                item {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🤖 AI Analityk Makro",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreenBright,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Przelicz to danie ponownie przez Asystenta AI (makroskładniki i Fit Punkty).",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = onRecalculateAi,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("recalculate_ai_btn")
                            ) {
                                Text("Przelicz AI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section: Składniki (Przeliczone automatycznie w Moduł 3)
            item {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Składniki (${scaledData.portions} porcj.)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Dotknij, aby sprawdzić zamiennik",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // Ingredients List
            items(scaledData.ingredients) { ing ->
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRequestSubstitute(ing.original.name) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ing.original.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (ing.smartNote != null) {
                                Text(
                                    text = ing.smartNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonGreenBright,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = ing.formattedAmountWithUnit,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreenBright
                        )
                    }
                }
            }

            // Section: Krok po kroku z aktywnymi minutnikami (Smart Timers)
            item {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)) {
                    Text(
                        text = "Przygotowanie (Wartości przeliczone)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Zielone przyciski uruchamiają aktywny minutnik w aplikacji:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Steps list
            items(scaledData.steps) { step ->
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreenContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = step.stepNumber.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreenBright
                                )
                            }

                            // Smart Timer Button (Blueprint Feature 2)
                            if (step.timerMinutes != null) {
                                FilledTonalButton(
                                    onClick = {
                                        onStartTimer(step.timerMinutes, "Krok ${step.stepNumber}: ${recipe.title}")
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = NeonGreenContainer,
                                        contentColor = NeonGreenBright
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("step_timer_btn_${step.stepNumber}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = NeonGreenBright
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${step.timerMinutes} min (Uruchom)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = step.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )

                        if (step.tip != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Wskazówka szefa: ${step.tip}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet / Dialog: Missing ingredient substitute (Moduł 5)
    if (showSubstituteDialog || activeSubstituteAdvice != null) {
        AlertDialog(
            onDismissRequest = {
                showSubstituteDialog = false
                onClearSubstituteAdvice()
            },
            title = {
                Text(
                    text = "Moduł 5: Ratunek Kulinarny",
                    color = NeonGreenBright,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Wpisz brakujący składnik lub sprawdź poradę kulinarną:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = substituteCustomInput,
                        onValueChange = { substituteCustomInput = it },
                        label = { Text("Czego Ci brakuje? (np. masło, jajka, śmietana)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("substitute_custom_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onRequestSubstitute(substituteCustomInput) },
                        enabled = substituteCustomInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Znajdź zamiennik z AI", fontWeight = FontWeight.Bold)
                    }

                    if (activeSubstituteAdvice != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("substitute_advice_box")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Rekomendacja dla: ${activeSubstituteAdvice.ingredientName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreenBright
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeSubstituteAdvice.recommendedSubstitute,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "• Wpływ na smak: ${activeSubstituteAdvice.flavorImpact}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "• Konsystencja: ${activeSubstituteAdvice.textureImpact}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "• Zmiana Fit Punktów: +${activeSubstituteAdvice.fitPointsDelta} pkt",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreenBright
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeSubstituteAdvice.explanation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSubstituteDialog = false
                    onClearSubstituteAdvice()
                }) {
                    Text("Zamknij", color = NeonGreenBright)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Usunąć przepis?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Czy na pewno chcesz usunąć przepis \"${recipe.title}\"? Zostanie on trwale usunięty z bazy danych w telefonie.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteRecipe?.invoke()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("Usuń", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
