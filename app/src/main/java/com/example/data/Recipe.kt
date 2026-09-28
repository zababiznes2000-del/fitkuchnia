package com.example.data

enum class RecipeCategory(val label: String) {
    SNIADANIE("Śniadanie"),
    OBIAD("Obiad"),
    KOLACJA("Kolacja"),
    NA_SLODKO("Na słodko"),
    NA_SLONO("Na słono"),
    NA_CIEPLO("Na ciepło"),
    NA_ZIMNO("Na zimno")
}

enum class StoreCategory(val label: String, val icon: String) {
    WARZYWA_OWOCE("Warzywa i Owoce", "🥦"),
    NABIAL("Nabiał i Jaja", "🥛"),
    MIESO_RYBY("Mięso i Ryby", "🥩"),
    SUCHE_PRZYPRAWY("Produkty Suche i Przyprawy", "🌾")
}

data class RecipeIngredient(
    val name: String = "",
    val amount: Double = 1.0,
    val unit: String = "szt.",
    val category: StoreCategory = StoreCategory.WARZYWA_OWOCE,
    val optional: Boolean = false
) {
    fun formatAmount(multiplier: Double = 1.0): String {
        return try {
            val safeAmount = if (amount.isFinite() && amount > 0) amount else 1.0
            val safeMultiplier = if (multiplier.isFinite() && multiplier > 0) multiplier else 1.0
            val scaled = safeAmount * safeMultiplier
            val safeUnit = unit ?: ""
            when {
                safeUnit.isEmpty() -> if (scaled % 1.0 == 0.0) "${scaled.toInt()}" else String.format(java.util.Locale.US, "%.1f", scaled)
                scaled % 1.0 == 0.0 -> "${scaled.toInt()} $safeUnit"
                else -> "${String.format(java.util.Locale.US, "%.1f", scaled)} $safeUnit"
            }
        } catch (_: Throwable) {
            "${amount} ${unit ?: ""}".trim()
        }
    }
}

typealias Ingredient = RecipeIngredient

data class RecipeStep(
    val stepNumber: Int = 1,
    val instruction: String = "",
    val timerMinutes: Int? = null,
    val tip: String? = null
)

typealias Step = RecipeStep

data class BeveragePairing(
    val title: String = "",
    val description: String = "",
    val whyItMatches: String = "",
    val fitPoints: Int = 90
)

enum class RecipeModerationStatus(val label: String) {
    PENDING("Oczekujący na weryfikację"),
    APPROVED("Zaakceptowany"),
    REJECTED("Odrzucony")
}

data class Recipe(
    val id: String = "",
    val title: String = "Nowy przepis",
    val description: String = "",
    val prepTimeMinutes: Int = 20,
    val prepTime: Int = 20,
    val kcalPerPortion: Int = 400,
    val fitPoints: Int = 80, // 1 to 100
    val fitPointsReason: String = "Zbilansowany posiłek",
    val proteinG: Int = 20,
    val fatG: Int = 15,
    val carbsG: Int = 40,
    val basePortions: Int = 2,
    val servings: Int = 2,
    val categories: Set<RecipeCategory> = setOf(RecipeCategory.OBIAD),
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val beveragePairing: BeveragePairing? = null,
    val matchedAilments: List<String> = emptyList(),
    val isVerified: Boolean = true,
    val author: String = "FitKuchnia Chef",
    val authorEmail: String = "zababiznes2000@gmail.com",
    val moderationStatus: RecipeModerationStatus = RecipeModerationStatus.APPROVED,
    val imagePath: String? = null,
    val imageUrl: String = "",
    val imageDrawableName: String? = null,
    val imageBase64: String? = null
) {
    val safeTitle: String get() = (title ?: "Przepis").ifBlank { "Przepis" }
    val safeDescription: String get() = description ?: ""
    val safeIngredients: List<RecipeIngredient> get() = ingredients ?: emptyList()
    val safeSteps: List<RecipeStep> get() = steps ?: emptyList()
    val safeCategories: Set<RecipeCategory> get() = categories ?: setOf(RecipeCategory.OBIAD)
    val safeMatchedAilments: List<String> get() = matchedAilments ?: emptyList()
    val safeAuthor: String get() = (author ?: "FitKuchnia Chef").ifBlank { "FitKuchnia Chef" }
    val safeAuthorEmail: String get() = authorEmail ?: ""
    val safeFitPointsReason: String get() = (fitPointsReason ?: "Zbilansowany posiłek").ifBlank { "Zbilansowany posiłek" }
    val safePrepTimeMinutes: Int get() = when {
        prepTimeMinutes > 0 -> prepTimeMinutes
        prepTime > 0 -> prepTime
        else -> 20
    }
    val safeBasePortions: Int get() = when {
        basePortions > 0 -> basePortions
        servings > 0 -> servings
        else -> 2
    }
    val safeImageUrl: String get() = (imageUrl.takeIf { it.isNotBlank() } ?: imagePath ?: "").trim()
}
