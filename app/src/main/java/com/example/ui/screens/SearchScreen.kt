package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Recipe
import com.example.data.RecipeCategory
import com.example.data.RecipeModerationStatus
import com.example.data.UserAccount
import com.example.ui.theme.*

@Composable
fun SearchScreen(
    recipes: List<Recipe>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategories: Set<RecipeCategory>,
    onToggleCategory: (RecipeCategory) -> Unit,
    fridgeQuery: String,
    onFridgeSearch: (String) -> Unit,
    fridgeResponse: String?,
    fridgeMatchedRecipe: Recipe?,
    isFridgeSearching: Boolean,
    onClearFridge: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onAddNewRecipe: () -> Unit,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    currentUser: UserAccount = UserAccount.DEFAULT_USER,
    canEditRecipe: (Recipe) -> Boolean = { false },
    onEditRecipe: ((Recipe) -> Unit)? = null,
    canDeleteRecipe: (Recipe) -> Boolean = { false },
    onDeleteRecipe: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchMode by remember { mutableStateOf(0) } // 0: Katalog & Filtry, 1: Moduł 1 (Lodówka & Dolegliwości)
    var fridgeInput by remember { mutableStateOf(fridgeQuery) }

    // Filtered recipes - only approved recipes are visible in search
    val filteredRecipes = remember(recipes, searchQuery, selectedCategories) {
        recipes.filter { recipe ->
            val isApproved = recipe.moderationStatus == RecipeModerationStatus.APPROVED
            val matchesQuery = searchQuery.isBlank() ||
                recipe.title.contains(searchQuery, ignoreCase = true) ||
                recipe.ingredients.any { it.name.contains(searchQuery, ignoreCase = true) }

            val matchesCategories = selectedCategories.isEmpty() ||
                selectedCategories.all { recipe.categories.contains(it) }

            isApproved && matchesQuery && matchesCategories
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen_list"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Search Header & Tabs
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "WYSZUKIWARKA & LODÓWKA",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonGreenBright,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Znajdź posiłek lub uratuj składniki (Zero Waste)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mode switch
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = searchMode == 0,
                        onClick = { searchMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = NeonGreenContainer,
                            activeContentColor = NeonGreenBright,
                            inactiveContainerColor = DarkSurface,
                            inactiveContentColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_standard_search")
                    ) {
                        Text("Katalog & Tagi", fontWeight = FontWeight.SemiBold)
                    }

                    SegmentedButton(
                        selected = searchMode == 1,
                        onClick = { searchMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = NeonGreenContainer,
                            activeContentColor = NeonGreenBright,
                            inactiveContainerColor = DarkSurface,
                            inactiveContentColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("tab_fridge_search")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Eco, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Moduł 1: Lodówka", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (searchMode == 0) {
            // STANDARD SEARCH + FILTER PILLS
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Big Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Szukaj potrawy, składnika...", color = TextMuted) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonGreen)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Wyczyść", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Filtruj wg kategorii (zaznaczaj wielokrotnie):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tag Chips / Pills (Śniadanie, Obiad, Kolacja, Na słodko, Na słono, Na ciepło, Na zimno)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecipeCategory.values().forEach { category ->
                            val isSelected = selectedCategories.contains(category)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleCategory(category) },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreenContainer,
                                    selectedLabelColor = NeonGreenBright,
                                    containerColor = DarkSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) NeonGreen else DarkCardBorder,
                                    selectedBorderColor = NeonGreen,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("filter_chip_${category.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wyniki wyszukiwania (${filteredRecipes.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedCategories.isNotEmpty() || searchQuery.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    onSearchQueryChange("")
                                    RecipeCategory.values().forEach {
                                        if (selectedCategories.contains(it)) onToggleCategory(it)
                                    }
                                }
                            ) {
                                Text("Resetuj filtry", color = NeonGreenBright, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (filteredRecipes.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (recipes.isEmpty()) "Brak przepisów w telefonie" else "Brak pasujących przepisów",
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (recipes.isEmpty()) "Twoja baza jest pusta. Dodaj swój pierwszy przepis poniżej!" else "Spróbuj odznaczyć niektóre filtry lub wpisać inne słowo.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddNewRecipe,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+ Dodaj nowy przepis", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(filteredRecipes, key = { it.id }) { recipe ->
                    StandardRecipeCard(
                        recipe = recipe,
                        onRecipeClick = { onRecipeClick(recipe.id) },
                        isFavorite = favoriteIds.contains(recipe.id),
                        onToggleFavorite = { onToggleFavorite(recipe.id) },
                        canEdit = canEditRecipe(recipe),
                        onEdit = if (canEditRecipe(recipe)) onEditRecipe?.let { ed -> { ed(recipe) } } else null,
                        canDelete = canDeleteRecipe(recipe),
                        onDelete = if (canDeleteRecipe(recipe)) onDeleteRecipe?.let { del -> { del(recipe.id) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            // MODUŁ 1: ZERO WASTE / CO MAM W LODÓWCE & DOLEGLIWOŚCI
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Kitchen, contentDescription = null, tint = NeonGreenBright)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Moduł 1: Rekomendacje z Lodówki & Dolegliwości",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Wpisz składniki, które masz w domu (np. 'mam jajka i chleb', 'ryż, groszek') lub dolegliwość (np. 'boli mnie brzuch'). Silnik AI przeszuka wyłącznie zweryfikowaną bazę!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = fridgeInput,
                            onValueChange = { fridgeInput = it },
                            placeholder = { Text("np. jajka, chleb lub boli mnie brzuch...", color = TextMuted) },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = NeonGreen,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("fridge_input_field")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick suggestion pills
                        Text("Szybkie przykłady:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "boli mnie brzuch",
                                "ból gardła",
                                "siemię lniane",
                                "mam jajka i chleb",
                                "ryż, jajka i groszek",
                                "pomidory, papryka i feta",
                                "spadek odporności"
                            ).forEach { example ->
                                AssistChip(
                                    onClick = {
                                        fridgeInput = example
                                        onFridgeSearch(example)
                                    },
                                    label = { Text(example, fontSize = 11.sp, color = TextSecondary) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkSurfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onFridgeSearch(fridgeInput) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("fridge_search_submit_btn")
                            ) {
                                if (isFridgeSearching) {
                                    CircularProgressIndicator(
                                        color = Color.Black,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dopasuj danie z bazy", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (fridgeResponse != null) {
                                OutlinedButton(
                                    onClick = {
                                        fridgeInput = ""
                                        onClearFridge()
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Resetuj", color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            // AI Fridge result display
            if (fridgeResponse != null) {
                item {
                    Surface(
                        color = if (fridgeMatchedRecipe != null) Color(0xFF0C2413) else Color(0xFF241512),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (fridgeMatchedRecipe != null) NeonGreen else Color(0xFFFF5252).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag("fridge_response_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (fridgeMatchedRecipe != null) Icons.Default.Eco else Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = if (fridgeMatchedRecipe != null) NeonGreenBright else Color(0xFFFF5252)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (fridgeMatchedRecipe != null) "Dopasowanie Modułu 1 (Sukces)" else "Brak w bazie",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (fridgeMatchedRecipe != null) NeonGreenBright else Color(0xFFFF5252)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = fridgeResponse,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Show matched recipe card directly
            if (fridgeMatchedRecipe != null) {
                item {
                    val isFridgeRecipeEditable = canEditRecipe(fridgeMatchedRecipe) || currentUser.isAdmin
                    StandardRecipeCard(
                        recipe = fridgeMatchedRecipe,
                        onRecipeClick = { onRecipeClick(fridgeMatchedRecipe.id) },
                        isFavorite = favoriteIds.contains(fridgeMatchedRecipe.id),
                        onToggleFavorite = { onToggleFavorite(fridgeMatchedRecipe.id) },
                        canEdit = isFridgeRecipeEditable,
                        onEdit = if (isFridgeRecipeEditable) onEditRecipe?.let { ed -> { ed(fridgeMatchedRecipe) } } else null,
                        canDelete = canDeleteRecipe(fridgeMatchedRecipe) || currentUser.isAdmin,
                        onDelete = if (canDeleteRecipe(fridgeMatchedRecipe) || currentUser.isAdmin) onDeleteRecipe?.let { del -> { del(fridgeMatchedRecipe.id) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
