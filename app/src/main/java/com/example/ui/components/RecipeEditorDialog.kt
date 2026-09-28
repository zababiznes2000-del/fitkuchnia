package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.R
import com.example.ai.CulinaryAiEngine
import com.example.ai.EstimatedNutrition
import com.example.data.*
import com.example.ui.theme.*
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class EditableIngredient(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val amount: String = "1",
    val unit: String = "szt.",
    val category: StoreCategory = StoreCategory.WARZYWA_OWOCE
)

data class EditableStep(
    val id: String = UUID.randomUUID().toString(),
    val instruction: String = "",
    val timerMinutes: String = "",
    val tip: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorDialog(
    currentUser: UserAccount = UserAccount.DEFAULT_ADMIN,
    recipeToEdit: Recipe? = null,
    onDismiss: () -> Unit,
    onSaveRecipe: (Recipe) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isEditMode = recipeToEdit != null

    var title by remember { mutableStateOf(recipeToEdit?.title ?: "") }
    var description by remember { mutableStateOf(recipeToEdit?.description ?: "") }
    var prepTime by remember { mutableStateOf(recipeToEdit?.prepTimeMinutes?.toString() ?: "20") }
    var basePortions by remember { mutableStateOf(recipeToEdit?.basePortions?.toString() ?: "2") }
    var selectedCategories by remember {
        mutableStateOf(recipeToEdit?.categories ?: setOf(RecipeCategory.OBIAD, RecipeCategory.NA_CIEPLO))
    }

    // Obowiązkowe zdjęcie potrawy dodane przez użytkownika (całkowicie wyłączone podmiany AI)
    var selectedPhotoPath by remember { mutableStateOf<String?>(recipeToEdit?.imagePath) }
    var selectedPhotoName by remember {
        mutableStateOf<String?>(if (recipeToEdit?.imagePath != null) "Zdjęcie z przepisu" else null)
    }
    var photoError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = ImageStorageHelper.copyUriToAppStorage(context, uri)
            if (path != null) {
                selectedPhotoPath = path
                selectedPhotoName = "Moje_zdjęcie_${System.currentTimeMillis().toString().takeLast(4)}.jpg"
                photoError = null
            } else {
                photoError = "Nie udało się zapisać zdjęcia. Spróbuj ponownie."
            }
        }
    }

    var ingredients by remember {
        mutableStateOf(
            recipeToEdit?.ingredients?.map {
                EditableIngredient(
                    name = it.name,
                    amount = if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount.toString(),
                    unit = it.unit,
                    category = it.category
                )
            } ?: listOf(
                EditableIngredient(name = "", amount = "100", unit = "g", category = StoreCategory.WARZYWA_OWOCE)
            )
        )
    }

    var steps by remember {
        mutableStateOf(
            recipeToEdit?.steps?.map {
                EditableStep(
                    instruction = it.instruction,
                    timerMinutes = it.timerMinutes?.toString() ?: "",
                    tip = it.tip ?: ""
                )
            } ?: listOf(
                EditableStep(instruction = "", timerMinutes = "", tip = "")
            )
        )
    }

    var beverageTitle by remember { mutableStateOf(recipeToEdit?.beveragePairing?.title ?: "") }
    var beverageWhy by remember { mutableStateOf(recipeToEdit?.beveragePairing?.whyItMatches ?: "") }

    // AI Nutrition & Fit Points State
    var estimatedNutrition by remember {
        mutableStateOf<EstimatedNutrition?>(
            if (recipeToEdit != null) {
                EstimatedNutrition(
                    kcalPerPortion = recipeToEdit.kcalPerPortion,
                    proteinG = recipeToEdit.proteinG,
                    fatG = recipeToEdit.fatG,
                    carbsG = recipeToEdit.carbsG,
                    fitPoints = recipeToEdit.fitPoints,
                    fitPointsReason = recipeToEdit.fitPointsReason
                )
            } else null
        )
    }
    var isAiCalculating by remember { mutableStateOf(false) }
    var showManualOverride by remember { mutableStateOf(false) }

    var manualKcal by remember { mutableStateOf(recipeToEdit?.kcalPerPortion?.toString() ?: "") }
    var manualProtein by remember { mutableStateOf(recipeToEdit?.proteinG?.toString() ?: "") }
    var manualFat by remember { mutableStateOf(recipeToEdit?.fatG?.toString() ?: "") }
    var manualCarbs by remember { mutableStateOf(recipeToEdit?.carbsG?.toString() ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Helper to run AI evaluation
    fun runAiEvaluation(onComplete: ((EstimatedNutrition) -> Unit)? = null) {
        val validIngs = ingredients.filter { it.name.isNotBlank() }
        if (validIngs.isEmpty()) {
            errorMessage = "Wpisz przynajmniej jeden składnik, aby AI mogło wyliczyć wartości."
            return
        }
        errorMessage = null
        isAiCalculating = true

        coroutineScope.launch {
            val domainIngredients = validIngs.map { ing ->
                RecipeIngredient(
                    name = ing.name.trim(),
                    amount = ing.amount.toDoubleOrNull() ?: 1.0,
                    unit = ing.unit.trim().ifEmpty { "szt." },
                    category = ing.category
                )
            }
            val pCount = (basePortions.toIntOrNull() ?: 2).coerceAtLeast(1)
            val apiKey = BuildConfig.GEMINI_API_KEY.ifBlank { null }
            val result = CulinaryAiEngine.estimateNutrition(
                title = title.ifBlank { "Danie kulinarne" },
                description = description,
                ingredients = domainIngredients,
                portions = pCount,
                apiKey = apiKey
            )
            estimatedNutrition = result
            manualKcal = result.kcalPerPortion.toString()
            manualProtein = result.proteinG.toString()
            manualFat = result.fatG.toString()
            manualCarbs = result.carbsG.toString()
            isAiCalculating = false
            onComplete?.invoke(result)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.RestaurantMenu, contentDescription = null, tint = NeonGreenBright)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isEditMode) "Edycja przepisu (Administrator)" else "Nowy przepis w FitKuchnia",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                    if (currentUser.isAdmin) {
                        Text(
                            text = if (isEditMode) "Super Admin: Zapis zmian w bazie Firestore" else "Konto Administratora: bezpośrednia publikacja",
                            color = NeonGreenBright,
                            fontSize = 11.sp
                        )
                    } else {
                        Text("Wymaga zatwierdzenia przez Administratora", color = AmberEnergy, fontSize = 11.sp)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = Color(0xFF331515),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF8080),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Nazwa i opis
                Text("1. Podstawowe informacje", fontWeight = FontWeight.Bold, color = NeonGreenBright, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nazwa dania (np. Sałatka z awokado i jajkiem)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("editor_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Krótki opis potrawy") },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("editor_desc_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Czas i porcje
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prepTime,
                        onValueChange = { prepTime = it.filter { ch -> ch.isDigit() } },
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
                        value = basePortions,
                        onValueChange = { basePortions = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Liczba porcji") },
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

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Składniki
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("2. Składniki potrawy", fontWeight = FontWeight.Bold, color = NeonGreenBright, fontSize = 13.sp)
                        Text("Na ich podstawie AI wyliczy makro i Fit Punkty", color = TextSecondary, fontSize = 10.sp)
                    }
                    TextButton(
                        onClick = {
                            ingredients = ingredients + EditableIngredient(name = "", amount = "1", unit = "szt.")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+ Składnik", color = NeonGreenBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                ingredients.forEachIndexed { index, ing ->
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = ing.name,
                                onValueChange = { newName ->
                                    ingredients = ingredients.toMutableList().also {
                                        it[index] = it[index].copy(name = newName)
                                    }
                                },
                                label = { Text("Składnik") },
                                placeholder = { Text("np. jajka, awokado") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1.5f)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedTextField(
                                value = ing.amount,
                                onValueChange = { newAmt ->
                                    ingredients = ingredients.toMutableList().also {
                                        it[index] = it[index].copy(amount = newAmt)
                                    }
                                },
                                label = { Text("Ilość") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(0.9f)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedTextField(
                                value = ing.unit,
                                onValueChange = { newUnit ->
                                    ingredients = ingredients.toMutableList().also {
                                        it[index] = it[index].copy(unit = newUnit)
                                    }
                                },
                                label = { Text("Jedn.") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(0.9f)
                            )

                            if (ingredients.size > 1) {
                                IconButton(
                                    onClick = {
                                        ingredients = ingredients.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Usuń", tint = TextMuted)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. AI NUTRITION & FIT POINTS SECTION (CORE DIRECTIVE)
                Surface(
                    color = Color(0xFF0F1A12),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().testTag("ai_nutrition_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = NeonGreenBright,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Asystent Dietetyczny",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreenBright,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { runAiEvaluation() },
                                enabled = !isAiCalculating,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreenContainer,
                                    contentColor = NeonGreenBright
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("ai_estimate_button")
                            ) {
                                if (isAiCalculating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = NeonGreenBright,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Liczenie...", fontSize = 11.sp)
                                } else {
                                    Text("🤖 Przelicz przez AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (estimatedNutrition != null) {
                            val nut = estimatedNutrition!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${nut.kcalPerPortion} kcal / porcję",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Białko: ${nut.proteinG}g • Tłuszcz: ${nut.fatG}g • Węgle: ${nut.carbsG}g",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                FitPointScorePill(score = nut.fitPoints)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = nut.fitPointsReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Asystent AI automatycznie obliczy kalorie, makroskładniki i obiektywne Fit Punkty (1-100) na podstawie podanych składników.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }

                        // Manual override toggle
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showManualOverride = !showManualOverride }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showManualOverride) "Ukryj ręczną korektę" else "Chcesz ręcznie skorygować wartości? (opcjonalnie)",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Icon(
                                imageVector = if (showManualOverride) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = showManualOverride) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = manualKcal,
                                        onValueChange = { manualKcal = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Kcal", fontSize = 10.sp) },
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
                                        value = manualProtein,
                                        onValueChange = { manualProtein = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Białko (g)", fontSize = 10.sp) },
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
                                        value = manualFat,
                                        onValueChange = { manualFat = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Tłuszcz (g)", fontSize = 10.sp) },
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
                                        value = manualCarbs,
                                        onValueChange = { manualCarbs = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Węgle (g)", fontSize = 10.sp) },
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
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. OBOWIĄZKOWE WŁASNE ZDJĘCIE POTRAWY (USUNIĘTO GENEROWANIE AI)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipe_photo_upload_section")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = NeonGreenBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "3. Własne zdjęcie dania",
                                fontWeight = FontWeight.Bold,
                                color = NeonGreenBright,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "* Obowiązkowe",
                            color = if (selectedPhotoPath == null) Color(0xFFFF5252) else NeonGreenBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Automatyczne generowanie zdjęć przez AI zostało wyłączone. Wgraj własne zdjęcie przygotowanej potrawy, aby opublikować przepis.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedPhotoPath != null) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen),
                            modifier = Modifier.fillMaxWidth().testTag("uploaded_dish_preview")
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                ) {
                                    val file = remember(selectedPhotoPath) { File(selectedPhotoPath!!) }
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(if (file.exists()) file else selectedPhotoPath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Wgrane zdjęcie dania",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    Surface(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
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
                                                text = "Własne zdjęcie wgrane pomyślnie",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedPhotoPath = null
                                            selectedPhotoName = null
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.75f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Usuń zdjęcie",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedPhotoName ?: "Zdjęcie potrawy gotowe",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(12.dp), tint = NeonGreenBright)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Zmień plik", fontSize = 11.sp, color = NeonGreenBright)
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (errorMessage != null && selectedPhotoPath == null) Color(0xFFFF5252) else DarkCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_photo_dropzone")
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreenContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Dodaj zdjęcie potrawy",
                                        tint = NeonGreenBright,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Wgraj własne zdjęcie potrawy (Wymagane)",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "Kliknij, aby wybrać plik ze swojego urządzenia",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Wybierz zdjęcie z galerii", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Wgrane zdjęcie potrawy jest obowiązkowe i zostanie trwale zachowane w oryginalnej postaci w chmurze Firebase.",
                                    color = NeonGreenBright,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (photoError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = photoError!!, color = Color(0xFFFF5252), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Kategorie
                Text("4. Kategorie dania", fontWeight = FontWeight.Bold, color = NeonGreenBright, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecipeCategory.values().forEach { cat ->
                        val isSelected = selectedCategories.contains(cat)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCategories = if (isSelected) selectedCategories - cat else selectedCategories + cat
                            },
                            label = { Text(cat.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreenContainer,
                                selectedLabelColor = NeonGreenBright,
                                containerColor = DarkSurfaceVariant,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) NeonGreen else DarkCardBorder,
                                selectedBorderColor = NeonGreen,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Kroki przygotowania
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("5. Kroki przygotowania", fontWeight = FontWeight.Bold, color = NeonGreenBright, fontSize = 13.sp)
                    TextButton(
                        onClick = {
                            steps = steps + EditableStep(instruction = "")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+ Krok", color = NeonGreenBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                steps.forEachIndexed { index, step ->
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "KROK ${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreenBright,
                                    fontSize = 11.sp
                                )
                                if (steps.size > 1) {
                                    IconButton(
                                        onClick = {
                                            steps = steps.toMutableList().also { it.removeAt(index) }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Usuń krok", tint = TextMuted)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = step.instruction,
                                onValueChange = { newInst ->
                                    steps = steps.toMutableList().also {
                                        it[index] = it[index].copy(instruction = newInst)
                                    }
                                },
                                label = { Text("Instrukcja (np. podsmaż na oliwie...)") },
                                minLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = step.timerMinutes,
                                    onValueChange = { newTime ->
                                        steps = steps.toMutableList().also {
                                            it[index] = it[index].copy(timerMinutes = newTime.filter { ch -> ch.isDigit() })
                                        }
                                    },
                                    label = { Text("Minutnik (min)") },
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
                                    value = step.tip,
                                    onValueChange = { newTip ->
                                        steps = steps.toMutableList().also {
                                            it[index] = it[index].copy(tip = newTip)
                                        }
                                    },
                                    label = { Text("Wskazówka (opcjonalnie)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonGreen,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    modifier = Modifier.weight(1.3f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 8. Opcjonalny napój
                Text("6. Dedykowany napój (opcjonalnie)", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = beverageTitle,
                    onValueChange = { beverageTitle = it },
                    label = { Text("Nazwa napoju (np. Zielona Herbata z miętą)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Wpisz nazwę przepisu."
                        return@Button
                    }
                    if (selectedPhotoPath.isNullOrBlank()) {
                        errorMessage = "Wgranie własnego zdjęcia dania jest obowiązkowe! (Zdjęcia AI zostały wyłączone)."
                        return@Button
                    }
                    val validIngs = ingredients.filter { it.name.isNotBlank() }
                    if (validIngs.isEmpty()) {
                        errorMessage = "Dodaj przynajmniej jeden składnik."
                        return@Button
                    }
                    val validSteps = steps.filter { it.instruction.isNotBlank() }
                    if (validSteps.isEmpty()) {
                        errorMessage = "Dodaj przynajmniej jeden krok przygotowania."
                        return@Button
                    }

                    // If nutrition not yet estimated by AI, do it before final save
                    if (estimatedNutrition == null) {
                        runAiEvaluation { finalNutrition ->
                            proceedSave(
                                title = title,
                                description = description,
                                prepTime = prepTime,
                                basePortions = basePortions,
                                ingredients = validIngs,
                                steps = validSteps,
                                selectedCategories = selectedCategories,
                                beverageTitle = beverageTitle,
                                beverageWhy = beverageWhy,
                                nutrition = finalNutrition,
                                photoPath = selectedPhotoPath,
                                currentUser = currentUser,
                                recipeToEdit = recipeToEdit,
                                onSaveRecipe = onSaveRecipe,
                                onDismiss = onDismiss
                            )
                        }
                    } else {
                        // Nutrition already estimated
                        val nut = estimatedNutrition!!
                        val finalNut = if (manualKcal.isNotBlank()) {
                            nut.copy(
                                kcalPerPortion = manualKcal.toIntOrNull() ?: nut.kcalPerPortion,
                                proteinG = manualProtein.toIntOrNull() ?: nut.proteinG,
                                fatG = manualFat.toIntOrNull() ?: nut.fatG,
                                carbsG = manualCarbs.toIntOrNull() ?: nut.carbsG
                            )
                        } else nut

                        proceedSave(
                            title = title,
                            description = description,
                            prepTime = prepTime,
                            basePortions = basePortions,
                            ingredients = validIngs,
                            steps = validSteps,
                            selectedCategories = selectedCategories,
                            beverageTitle = beverageTitle,
                            beverageWhy = beverageWhy,
                            nutrition = finalNut,
                            photoPath = selectedPhotoPath,
                            currentUser = currentUser,
                            recipeToEdit = recipeToEdit,
                            onSaveRecipe = onSaveRecipe,
                            onDismiss = onDismiss
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                modifier = Modifier.testTag("save_user_recipe_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEditMode) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zapisz zmiany w przepisie", fontWeight = FontWeight.Black)
                    } else if (currentUser.isAdmin) {
                        Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Opublikuj przepis (Administrator)", fontWeight = FontWeight.Black)
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prześlij do weryfikacji", fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun proceedSave(
    title: String,
    description: String,
    prepTime: String,
    basePortions: String,
    ingredients: List<EditableIngredient>,
    steps: List<EditableStep>,
    selectedCategories: Set<RecipeCategory>,
    beverageTitle: String,
    beverageWhy: String,
    nutrition: EstimatedNutrition,
    photoPath: String?,
    currentUser: UserAccount,
    recipeToEdit: Recipe?,
    onSaveRecipe: (Recipe) -> Unit,
    onDismiss: () -> Unit
) {
    val recipeIngredients = ingredients.map { ing ->
        RecipeIngredient(
            name = ing.name.trim(),
            amount = ing.amount.toDoubleOrNull() ?: 1.0,
            unit = ing.unit.trim().ifEmpty { "szt." },
            category = ing.category
        )
    }

    val recipeSteps = steps.mapIndexed { idx, st ->
        RecipeStep(
            stepNumber = idx + 1,
            instruction = st.instruction.trim(),
            timerMinutes = st.timerMinutes.toIntOrNull(),
            tip = st.tip.trim().ifEmpty { null }
        )
    }

    val time = prepTime.toIntOrNull() ?: 20
    val portions = (basePortions.toIntOrNull() ?: 2).coerceAtLeast(1)

    val finalId = recipeToEdit?.id ?: ("user-" + UUID.randomUUID().toString().take(8))
    val finalAuthor = recipeToEdit?.author ?: currentUser.name
    val finalAuthorEmail = recipeToEdit?.authorEmail ?: currentUser.email
    val finalModeration = if (currentUser.isAdmin) RecipeModerationStatus.APPROVED else (recipeToEdit?.moderationStatus ?: RecipeModerationStatus.PENDING)
    val finalVerified = if (currentUser.isAdmin) true else (recipeToEdit?.isVerified ?: false)
    val finalPhoto = photoPath ?: recipeToEdit?.imagePath
    val finalBase64 = if (!photoPath.isNullOrBlank()) ImageStorageHelper.encodeFileToBase64(photoPath) ?: recipeToEdit?.imageBase64 else recipeToEdit?.imageBase64

    val finalRecipe = Recipe(
        id = finalId,
        title = title.trim(),
        description = description.trim().ifEmpty { "Autorski przepis kulinarny." },
        prepTimeMinutes = time,
        kcalPerPortion = nutrition.kcalPerPortion,
        fitPoints = nutrition.fitPoints,
        fitPointsReason = nutrition.fitPointsReason,
        proteinG = nutrition.proteinG,
        fatG = nutrition.fatG,
        carbsG = nutrition.carbsG,
        basePortions = portions,
        categories = if (selectedCategories.isEmpty()) setOf(RecipeCategory.OBIAD) else selectedCategories,
        matchedAilments = recipeToEdit?.matchedAilments ?: emptyList(),
        ingredients = recipeIngredients,
        steps = recipeSteps,
        beveragePairing = if (beverageTitle.isNotBlank()) {
            BeveragePairing(
                title = beverageTitle.trim(),
                description = "Dedykowany napój dodany przez autora.",
                whyItMatches = beverageWhy.trim().ifEmpty { "Świetnie uzupełnia posiłek i nawadnia organizm." }
            )
        } else null,
        isVerified = finalVerified,
        author = finalAuthor,
        authorEmail = finalAuthorEmail,
        moderationStatus = finalModeration,
        imagePath = finalPhoto,
        imageBase64 = finalBase64
    )

    onSaveRecipe(finalRecipe)
    onDismiss()
}
