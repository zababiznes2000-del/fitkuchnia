package com.example.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.R
import com.example.data.Recipe
import com.example.data.RecipeModerationStatus
import com.example.data.UserAccount
import com.example.ui.components.DishImageView
import com.example.ui.components.DishVisualResolver
import com.example.ui.components.FitPointScorePill
import com.example.ui.components.RecipeStatBadgeOverlay
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    recipes: List<Recipe>,
    currentUser: UserAccount = UserAccount.DEFAULT_USER,
    onRecipeClick: (String) -> Unit,
    onAddNewRecipe: () -> Unit,
    onOpenAiChat: () -> Unit,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    canEditRecipe: (Recipe) -> Boolean = { false },
    onEditRecipe: ((Recipe) -> Unit)? = null,
    canDeleteRecipe: (Recipe) -> Boolean = { true },
    onDeleteRecipe: ((String) -> Unit)? = null,
    onApproveRecipe: ((String) -> Unit)? = null,
    onRejectRecipe: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val approvedRecipes = remember(recipes) {
        recipes.filter { it.moderationStatus == RecipeModerationStatus.APPROVED }
    }
    val pendingRecipes = remember(recipes) {
        recipes.filter { it.moderationStatus == RecipeModerationStatus.PENDING }
    }
    val myPendingRecipes = remember(recipes, currentUser) {
        recipes.filter { it.moderationStatus == RecipeModerationStatus.PENDING && it.authorEmail.equals(currentUser.email, ignoreCase = true) }
    }

    val dailyDish = approvedRecipes.maxByOrNull { it.fitPoints } ?: approvedRecipes.firstOrNull()
    val feedRecipes = if (dailyDish != null) approvedRecipes.filterNot { it.id == dailyDish.id } else emptyList()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_feed"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FIT",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = NeonGreenBright,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "KUCHNIA",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = if (recipes.isEmpty()) "Twoja autorska książka kucharska" else "${recipes.size} przepisów w telefonie",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddNewRecipe,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("home_add_recipe_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dodaj", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onOpenAiChat,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                            .border(1.dp, NeonGreen.copy(alpha = 0.3f), CircleShape)
                            .testTag("open_ai_assistant_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Asystent Kulinarny AI",
                            tint = NeonGreenBright
                        )
                    }
                }
            }
        }

        // Current User Status Indicator
        item {
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (currentUser.isAdmin) NeonGreen.copy(alpha = 0.5f) else DarkCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (currentUser.isAdmin) Icons.Default.Shield else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (currentUser.isAdmin) NeonGreenBright else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (currentUser.isAdmin) "Konto: Administrator (${currentUser.email})" else "Konto: ${currentUser.name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    if (currentUser.isAdmin) {
                        Surface(
                            color = NeonGreenContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ADMIN",
                                color = NeonGreenBright,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1. ADMIN MODERATION QUEUE (Blueprint Requirement 2 & 4)
        if (currentUser.isAdmin && pendingRecipes.isNotEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberEnergy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("admin_moderation_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(AmberEnergy.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = AmberEnergy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                                        text = "Kolejka moderacji: ${pendingRecipes.size} do weryfikacji",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Surface(
                                color = AmberEnergy.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${pendingRecipes.size} oczekuje",
                                    color = AmberEnergy,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Nowe przepisy zwykłych użytkowników wymagają Twojej weryfikacji i zatwierdzenia przed publikacją w widoku publicznym.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pendingRecipes.forEach { pendingRecipe ->
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onRecipeClick(pendingRecipe.id) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        ) {
                                            DishImageView(
                                                recipe = pendingRecipe,
                                                contentDescription = pendingRecipe.title,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pendingRecipe.title,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
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
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
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
                                                        .testTag("admin_edit_pending_${pendingRecipe.id}")
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
                                                    contentDescription = "Zatwierdź i opublikuj",
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
                                                    contentDescription = "Odrzuć przepis",
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

        // 2. REGULAR USER PENDING NOTIFICATION (Blueprint Requirement 2)
        if (!currentUser.isAdmin && myPendingRecipes.isNotEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberEnergy.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = AmberEnergy,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Twoje przepisy w weryfikacji (${myPendingRecipes.size})",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Przepis przesłany do Administratora (${UserAccount.ADMIN_EMAIL}). Zostanie opublikowany dla wszystkich po pomyślnej weryfikacji.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // When Database has no approved public recipes
        if (approvedRecipes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("empty_recipes_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(NeonGreen.copy(alpha = 0.5f), DarkCardBorder)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(NeonGreenContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = NeonGreenBright,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Brak opublikowanych przepisów",
                            color = NeonGreenBright,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Baza jest gotowa na Twoje autorskie dania. Dodaj przepis z własnym zdjęciem potrawy.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onAddNewRecipe,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("empty_state_add_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dodaj przepis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            // Blueprint Feature 1: Featured Daily Dish Combo Card
            dailyDish?.let { dish ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "POLECANE NA DZIŚ",
                                color = NeonGreenBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreenBright)
                            )
                        }

                        val isDishEditable = canEditRecipe(dish) || currentUser.isAdmin
                        FeaturedDailyDishCard(
                            recipe = dish,
                            onRecipeClick = { onRecipeClick(dish.id) },
                            isFavorite = favoriteIds.contains(dish.id),
                            onToggleFavorite = { onToggleFavorite(dish.id) },
                            canEdit = isDishEditable,
                            onEdit = if (isDishEditable) onEditRecipe?.let { ed -> { ed(dish) } } else null,
                            canDelete = canDeleteRecipe(dish) || currentUser.isAdmin,
                            onDelete = if (canDeleteRecipe(dish) || currentUser.isAdmin) onDeleteRecipe?.let { del -> { del(dish.id) } } else null
                        )
                    }
                }
            }

            // Regular Feed Section
            if (feedRecipes.isNotEmpty()) {
                item {
                    Text(
                        text = "OPUBLIKOWANE PRZEPISY (${approvedRecipes.size})",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
                    )
                }

                items(feedRecipes, key = { it.id }) { recipe ->
                    val isRecipeEditable = canEditRecipe(recipe) || currentUser.isAdmin
                    StandardRecipeCard(
                        recipe = recipe,
                        onRecipeClick = { onRecipeClick(recipe.id) },
                        isFavorite = favoriteIds.contains(recipe.id),
                        onToggleFavorite = { onToggleFavorite(recipe.id) },
                        canEdit = isRecipeEditable,
                        onEdit = if (isRecipeEditable) onEditRecipe?.let { ed -> { ed(recipe) } } else null,
                        canDelete = canDeleteRecipe(recipe) || currentUser.isAdmin,
                        onDelete = if (canDeleteRecipe(recipe) || currentUser.isAdmin) onDeleteRecipe?.let { del -> { del(recipe.id) } } else null,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedRecipeCard(
    recipe: Recipe,
    onRecipeClick: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    canEdit: Boolean = false,
    onEdit: (() -> Unit)? = null,
    canDelete: Boolean = true,
    onDelete: (() -> Unit)? = null,
    isFeatured: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onRecipeClick)
            .testTag(if (isFeatured) "featured_daily_dish_card" else "recipe_card_${recipe.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isFeatured) {
                Brush.verticalGradient(listOf(NeonGreen.copy(alpha = 0.6f), DarkCardBorder))
            } else {
                Brush.verticalGradient(listOf(DarkCardBorder.copy(alpha = 0.8f), DarkCardBorder.copy(alpha = 0.4f)))
            }
        )
    ) {
        Column {
            // 1. Zdjęcie użytkownika na górze (100% widoczne, bez przycinania, object-fit: contain / ContentScale.Fit, tło #121212)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF121212))
            ) {
                DishImageView(
                    recipe = recipe,
                    contentDescription = recipe.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    backgroundColor = Color(0xFF121212)
                )

                // Subtelny gradient przy dolnej krawędzi kadru dla zapewnienia czytelności nakładek
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, DarkSurface.copy(alpha = 0.85f))
                            )
                        )
                )

                // Nakładka: Czas przygotowania i kcal (Góra - Lewa)
                RecipeStatBadgeOverlay(
                    prepTimeMinutes = recipe.prepTimeMinutes,
                    kcal = recipe.kcalPerPortion,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )

                // Nakładka: Ikony akcji (Edytuj / Usuń / Polub) (Góra - Prawa)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canEdit && onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .testTag("recipe_card_edit_btn_${recipe.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edytuj przepis (Admin)",
                                tint = NeonGreenBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (onDelete != null) {
                        if (canDelete) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
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
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Brak uprawnień do usunięcia cudzego przepisu",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Polub",
                            tint = if (isFavorite) NeonGreenBright else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Nakładka: Fit score / ocena (Dół - Prawa)
                FitPointScorePill(
                    score = recipe.fitPoints,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                )

                // Etykieta autora (Dół - Lewa)
                if (recipe.author.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Autor: ${recipe.author}",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Treść karty: Tytuł i opis przepisu
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = recipe.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Trzy plakietki z makroskładnikami na dole: "Białko: Xg", "Tłuszcze: Yg", "Węglowodany: Zg"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MacroChip(label = "Białko", value = "${recipe.proteinG}g", color = NeonGreenBright)
                    MacroChip(label = "Tłuszcze", value = "${recipe.fatG}g", color = TimeBlue)
                    MacroChip(label = "Węglowodany", value = "${recipe.carbsG}g", color = FitGold)
                }

                // Opcjonalne combo napoju
                if (recipe.beveragePairing != null && isFeatured) {
                    recipe.beveragePairing?.let { beverage ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonGreenContainer, DarkCardBorder))),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreenContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalBar,
                                        contentDescription = null,
                                        tint = NeonGreenBright,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "COMBO DNIA: ${beverage.title}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreenBright
                                    )
                                    Text(
                                        text = beverage.whyItMatches,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
                // Przycisk edycji dla Administratora pod kartą
                if (canEdit && onEdit != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipe_card_edit_action_${recipe.id}"),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreenBright),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Edytuj przepis (Admin)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedDailyDishCard(
    recipe: Recipe,
    onRecipeClick: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    canEdit: Boolean = false,
    onEdit: (() -> Unit)? = null,
    canDelete: Boolean = true,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    UnifiedRecipeCard(
        recipe = recipe,
        onRecipeClick = onRecipeClick,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        canEdit = canEdit,
        onEdit = onEdit,
        canDelete = canDelete,
        onDelete = onDelete,
        isFeatured = true,
        modifier = modifier
    )
}

@Composable
fun StandardRecipeCard(
    recipe: Recipe,
    onRecipeClick: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    canEdit: Boolean = false,
    onEdit: (() -> Unit)? = null,
    canDelete: Boolean = true,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    UnifiedRecipeCard(
        recipe = recipe,
        onRecipeClick = onRecipeClick,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        canEdit = canEdit,
        onEdit = onEdit,
        canDelete = canDelete,
        onDelete = onDelete,
        isFeatured = false,
        modifier = modifier
    )
}

@Composable
fun MacroChip(label: String, value: String, color: Color) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "$label: ", fontSize = 11.sp, color = TextSecondary)
            Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
