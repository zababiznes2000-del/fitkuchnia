package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*

@Composable
fun ShoppingPlannerScreen(
    recipes: List<Recipe>,
    plannedMeals: List<PlannedMeal>,
    onAddMeal: (DayOfWeekPolish, Recipe, Int) -> Unit,
    onRemoveMeal: (String) -> Unit,
    checkedShoppingItemIds: Set<String>,
    onToggleShoppingItem: (String) -> Unit,
    onClearCheckedItems: () -> Unit,
    onCompleteShopping: () -> Unit = {},
    onRecipeClick: (String) -> Unit,
    onAddNewRecipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDay by remember { mutableStateOf(DayOfWeekPolish.MON) }
    var showAddMealDialog by remember { mutableStateOf(false) }
    var showCompleteShoppingDialog by remember { mutableStateOf(false) }

    // Dynamic aggregation with Module 4
    val aggregatedItems = remember(plannedMeals, recipes) {
        try {
            ShoppingAggregator.aggregate(plannedMeals, recipes)
        } catch (e: Throwable) {
            android.util.Log.e("ShoppingPlannerScreen", "Błąd agregacji składników", e)
            emptyList()
        }
    }

    val itemsByCategory = remember(aggregatedItems) {
        try {
            aggregatedItems.groupBy { it.category ?: StoreCategory.SUCHE_PRZYPRAWY }
        } catch (e: Throwable) {
            emptyMap()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("shopping_planner_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonGreenBright)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PLANER & INTELIGENTNY AGREGATOR ZAKUPÓW",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreenBright,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Planer Tygodniowy (Pn-Nd)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                Text(
                    text = "Moduł 4 matematycznie łączy powtarzające się składniki i przelicza jednostki.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Section 1: Weekly Days Selector (Pn - Nd)
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayOfWeekPolish.values().forEach { day ->
                        val isSelected = selectedDay == day
                        val mealsForDay = plannedMeals.filter { it.day == day }

                        Surface(
                            color = if (isSelected) NeonGreenContainer else DarkSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) NeonGreen else DarkCardBorder
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedDay = day }
                                .testTag("day_tab_${day.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day.shortName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) NeonGreenBright else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                if (mealsForDay.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(NeonGreen)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 1.5: Meals Planned for Selected Day
        item {
            val currentDayMeals = plannedMeals.filter { (it.day ?: DayOfWeekPolish.MON) == selectedDay }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Posiłki: ${selectedDay.fullName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    FilledTonalButton(
                        onClick = { showAddMealDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonGreenContainer,
                            contentColor = NeonGreenBright
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("add_meal_to_day_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dodaj danie (+)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (currentDayMeals.isEmpty()) {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Brak zaplanowanych dań na ${selectedDay.fullName}",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Kliknij 'Dodaj danie (+)', aby wybrać z bazy",
                                color = NeonGreenBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        currentDayMeals.forEach { meal ->
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onRecipeClick(meal.recipeId) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(NeonGreenContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Restaurant,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = meal.recipeTitle.ifBlank { "Danie" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "${meal.portions} porcje • zobacz przepis >",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeonGreenBright
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onRemoveMeal(meal.id) },
                                        modifier = Modifier.size(32.dp).testTag("delete_meal_${meal.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Usuń danie",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: AGGREGATED SHOPPING LIST (MODUŁ 4)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = NeonGreenBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AGREGACJA SKŁADNIKÓW (MODUŁ 4)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = NeonGreenBright,
                            letterSpacing = 1.sp
                        )
                    }

                    if (checkedShoppingItemIds.isNotEmpty()) {
                        TextButton(
                            onClick = onClearCheckedItems,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Odznacz wszystko", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Text(
                    text = "Zsumowane zapotrzebowanie na cały tydzień z podziałem na działy marketu:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        if (aggregatedItems.isEmpty()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Lista zakupów jest pusta", color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dodaj dania do poszczególnych dni tygodnia powyżej, a sztuczna inteligencja zsumuje Twoje składniki!",
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            // Render category groups
            StoreCategory.values().forEach { category ->
                val itemsInCat = itemsByCategory[category] ?: emptyList()
                if (itemsInCat.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(category.icon, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(DarkCardBorder)
                                )
                            }
                        }
                    }

                    val uniqueItemsInCat = itemsInCat.distinctBy { it.id }
                    items(uniqueItemsInCat, key = { it.id }) { item ->
                        val isChecked = checkedShoppingItemIds.contains(item.id)
                        Surface(
                            color = if (isChecked) DarkSurface.copy(alpha = 0.5f) else DarkSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChecked) DarkCardBorder.copy(alpha = 0.4f) else DarkCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleShoppingItem(item.id) }
                                .testTag("shopping_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleShoppingItem(item.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonGreen,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = DarkCardBorder
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.formattedDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Bold,
                                        color = if (isChecked) TextMuted else TextPrimary,
                                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (item.sources.isNotEmpty()) {
                                        Text(
                                            text = "Z dań: ${item.sources.joinToString(", ")}",
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
            }
        }

        // PRZYCISK: ZAKUPY ZROBIONE (Trwałe usunięcie listy zakupów z konta użytkownika)
        if (plannedMeals.isNotEmpty() || aggregatedItems.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = { showCompleteShoppingDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("complete_shopping_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zakupy zrobione – Wyczyść listę",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Trwale usuwa listę zakupów i zaplanowane posiłki z Twojego prywatnego konta.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Dialog potwierdzenia zakończenia zakupów
    if (showCompleteShoppingDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteShoppingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreenBright)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zakupy zrobione!", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Czy na pewno chcesz zakończyć zakupy? Spowoduje to trwałe wyczyszczenie bieżącej listy zakupów oraz zaplanowanych posiłków z Twojego prywatnego konta w chmurze.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompleteShoppingDialog = false
                        onCompleteShopping()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tak, wyczyść listę", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteShoppingDialog = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Add Meal to Day Dialog
    if (showAddMealDialog) {
        if (recipes.isEmpty()) {
            AlertDialog(
                onDismissRequest = { showAddMealDialog = false },
                title = { Text("Brak przepisów", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Twoja baza przepisów jest pusta. Dodaj najpierw swój przepis, aby móc zaplanować posiłki na ten tydzień i wygenerować listę zakupów.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAddMealDialog = false
                            onAddNewRecipe()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                    ) {
                        Text("+ Dodaj przepis", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddMealDialog = false }) {
                        Text("Zamknij", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp)
            )
        } else {
            var selectedRecipe by remember { mutableStateOf(recipes.first()) }
            var portionCount by remember { mutableStateOf(selectedRecipe.basePortions) }

            AlertDialog(
                onDismissRequest = { showAddMealDialog = false },
                title = {
                    Text(
                        text = "Dodaj do: ${selectedDay.fullName}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Wybierz przepis z bazy:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            recipes.forEach { r ->
                                val isSelected = r.id == selectedRecipe.id
                                Surface(
                                    color = if (isSelected) NeonGreenContainer else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRecipe = r
                                            portionCount = r.basePortions
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = r.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) NeonGreenBright else TextPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${r.kcalPerPortion} kcal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Liczba porcji:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (portionCount > 1) portionCount-- },
                                    enabled = portionCount > 1
                                ) {
                                    Text("-", fontSize = 20.sp, color = NeonGreenBright)
                                }
                                Text(
                                    text = portionCount.toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = { if (portionCount < 20) portionCount++ }
                                ) {
                                    Text("+", fontSize = 20.sp, color = NeonGreenBright)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onAddMeal(selectedDay, selectedRecipe, portionCount)
                            showAddMealDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                    ) {
                        Text("Dodaj do planu", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddMealDialog = false }) {
                        Text("Anuluj", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
