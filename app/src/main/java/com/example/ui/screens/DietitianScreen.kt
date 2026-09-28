package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Recipe
import com.example.data.firebase.FirebaseMealHistorySync
import com.example.data.model.MealHistoryEntry
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietitianScreen(
    weeklyMeals: List<MealHistoryEntry>,
    availableRecipes: List<Recipe>,
    analysisResult: String?,
    isAnalyzing: Boolean,
    onRunAnalysis: () -> Unit,
    onDeleteMeal: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val polishDayNames = listOf(
        "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela"
    )
    val polishDayShort = listOf("Pon", "Wt", "Śr", "Czw", "Pt", "Sob", "Ndz")

    val weekStartMillis = remember { FirebaseMealHistorySync.getStartOfCurrentWeekMillis() }
    val weekEndMillis = remember { FirebaseMealHistorySync.getEndOfCurrentWeekMillis() }

    val dateFormat = remember { SimpleDateFormat("dd.MM", Locale("pl", "PL")) }
    val dayFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("pl", "PL")) }

    // Generujemy dni bieżącego tygodnia (Poniedziałek - Niedziela)
    val weekDays = remember(weekStartMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = weekStartMillis }
        (0..6).map { idx ->
            val dateStr = dayFormat.format(cal.time)
            val displayDate = dateFormat.format(cal.time)
            val dayName = polishDayNames[idx]
            val shortName = polishDayShort[idx]
            cal.add(Calendar.DAY_OF_YEAR, 1)
            WeekDayInfo(dateStr, displayDate, dayName, shortName)
        }
    }

    val todayDateStr = remember { dayFormat.format(Date()) }
    var selectedDateFilter by remember { mutableStateOf<String?>(null) } // null = cały tydzień

    val filteredMeals = remember(weeklyMeals, selectedDateFilter) {
        if (selectedDateFilter == null) {
            weeklyMeals
        } else {
            weeklyMeals.filter { it.dateString == selectedDateFilter }
        }
    }

    val totalKcal = remember(weeklyMeals) { weeklyMeals.sumOf { it.kcal * it.portions } }
    val totalProt = remember(weeklyMeals) { weeklyMeals.sumOf { it.proteinG * it.portions } }
    val totalFat = remember(weeklyMeals) { weeklyMeals.sumOf { it.fatG * it.portions } }
    val totalCarbs = remember(weeklyMeals) { weeklyMeals.sumOf { it.carbsG * it.portions } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Nagłówek ekranu
        item {
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Dietetyk AI",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = NeonGreenContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "BIEŻĄCY TYDZIEŃ",
                                        color = NeonGreenBright,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Audyt składników, zbilansowanie makro i plan posiłków z bazy",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = NeonGreenBright,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pasek wyboru dni tygodnia (Poniedziałek – Niedziela)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isAllSelected = selectedDateFilter == null
                            Surface(
                                color = if (isAllSelected) NeonGreen else DarkCard,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isAllSelected) NeonGreen else DarkCardBorder
                                ),
                                modifier = Modifier
                                    .clickable { selectedDateFilter = null }
                                    .testTag("filter_all_days")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Cały tydzień",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllSelected) Color.Black else TextPrimary
                                    )
                                    Text(
                                        text = "${weeklyMeals.size} dań",
                                        fontSize = 10.sp,
                                        color = if (isAllSelected) Color.Black.copy(alpha = 0.8f) else TextSecondary
                                    )
                                }
                            }
                        }

                        items(weekDays) { dayInfo ->
                            val isSelected = selectedDateFilter == dayInfo.dateString
                            val isToday = dayInfo.dateString == todayDateStr
                            val countForDay = weeklyMeals.count { it.dateString == dayInfo.dateString }

                            Surface(
                                color = if (isSelected) NeonGreen else if (isToday) DarkSurfaceVariant else DarkCard,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonGreen else if (isToday) NeonGreen.copy(alpha = 0.5f) else DarkCardBorder
                                ),
                                modifier = Modifier
                                    .clickable {
                                        selectedDateFilter = if (isSelected) null else dayInfo.dateString
                                    }
                                    .testTag("day_filter_${dayInfo.shortName}")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dayInfo.shortName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else if (isToday) NeonGreenBright else TextPrimary
                                    )
                                    Text(
                                        text = dayInfo.displayDate,
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.Black.copy(alpha = 0.8f) else TextSecondary
                                    )
                                    if (countForDay > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.Black else NeonGreenBright)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Karta podsumowania energii i makroskładników w tygodniu
        item {
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🔥 Energia", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = "$totalKcal kcal",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreenBright
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(DarkCardBorder))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🥩 Białko", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = "${totalProt}g",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(DarkCardBorder))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🥑 Tłuszcze", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = "${totalFat}g",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(28.dp).background(DarkCardBorder))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "🌾 Węgle", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = "${totalCarbs}g",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Sekcja Analizy AI: Przycisk "Przeanalizuj mój tydzień i zaproponuj posiłki"
        item {
            Surface(
                color = NeonGreenContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonGreenBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audyt Kliniczny & Plan Żywieniowy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreenBright
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Asystent AI przeanalizuje składniki zjedzonych posiłków, wykryje niedobory i zaproponuje plan posiłków wyłącznie ze zweryfikowanej bazy dań FitKuchnia.",
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunAnalysis,
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("analyze_week_btn")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Dietetyk AI analizuje tydzień...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Przeanalizuj mój tydzień i zaproponuj posiłki",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Sekcja Wyniku AI (jeśli wygenerowano)
        if (!analysisResult.isNullOrBlank()) {
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("dietitian_analysis_result_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🩺", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rekomendacje Dietetyka AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreenBright
                                )
                            }

                            IconButton(
                                onClick = onRunAnalysis,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Odśwież analizę",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Wyświetlanie sformatowanego raportu Markdown
                        Text(
                            text = analysisResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            lineHeight = 20.sp
                        )

                        // Szybkie skróty do rekomendowanych dań z bazy
                        val mentionedRecipes = remember(analysisResult, availableRecipes) {
                            availableRecipes.filter { recipe ->
                                analysisResult.contains(recipe.title, ignoreCase = true) ||
                                        analysisResult.contains(recipe.id, ignoreCase = true)
                            }
                        }

                        if (mentionedRecipes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Szybki podgląd polecanych dań z Twojej bazy:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreenBright
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                mentionedRecipes.forEach { rec ->
                                    Surface(
                                        color = DarkCard,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .clickable { onOpenRecipe(rec.id) }
                                            .testTag("recommended_dish_chip_${rec.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestaurantMenu,
                                                contentDescription = null,
                                                tint = NeonGreenBright,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = rec.title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary
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

        // Sekcja: Historia tygodniowa zjedzonych posiłków
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Historia zjedzonych posiłków",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${filteredMeals.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreenBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (selectedDateFilter != null) {
                    TextButton(
                        onClick = { selectedDateFilter = null },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Pokaż wszystkie", fontSize = 11.sp, color = NeonGreenBright)
                    }
                }
            }
        }

        if (filteredMeals.isEmpty()) {
            item {
                Surface(
                    color = DarkCard,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (selectedDateFilter != null) "Brak posiłków zjedzonych w tym dniu" else "Brak zjedzonych dań w tym tygodniu",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Otwórz dowolny przepis w zakładce Odkrywaj lub Szukaj i dotknij przycisku „🍽️ Oznacz jako zjedzone”, aby zarejestrować posiłek.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(filteredMeals, key = { it.id }) { meal ->
                MealHistoryItemCard(
                    meal = meal,
                    timeDisplay = timeFormat.format(meal.timestamp.toDate()),
                    onDelete = { onDeleteMeal(meal.id) },
                    onOpenRecipe = {
                        if (meal.recipeId.isNotBlank()) {
                            onOpenRecipe(meal.recipeId)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private data class WeekDayInfo(
    val dateString: String,
    val displayDate: String,
    val dayName: String,
    val shortName: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealHistoryItemCard(
    meal: MealHistoryEntry,
    timeDisplay: String,
    onDelete: () -> Unit,
    onOpenRecipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkCard,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("meal_history_card_${meal.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(NeonGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = NeonGreenBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = meal.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${meal.dateString} o godz. $timeDisplay (${meal.portions} porcja)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meal.recipeId.isNotBlank()) {
                        IconButton(
                            onClick = onOpenRecipe,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Otwórz przepis",
                                tint = NeonGreenBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Usuń z historii",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Wskaźniki makroskładników
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (meal.kcal > 0) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "🔥 ${meal.kcal * meal.portions} kcal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonGreenBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (meal.proteinG > 0) {
                    Text(
                        text = "B: ${meal.proteinG * meal.portions}g",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                if (meal.fatG > 0) {
                    Text(
                        text = "T: ${meal.fatG * meal.portions}g",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                if (meal.carbsG > 0) {
                    Text(
                        text = "W: ${meal.carbsG * meal.portions}g",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            if (meal.ingredients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    meal.ingredients.take(6).forEach { ing ->
                        Surface(
                            color = DarkBg,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkCardBorder)
                        ) {
                            Text(
                                text = ing,
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (meal.ingredients.size > 6) {
                        Text(
                            text = "+${meal.ingredients.size - 6} więcej",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
