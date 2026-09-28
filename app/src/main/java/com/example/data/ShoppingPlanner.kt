package com.example.data

import android.util.Log
import java.util.UUID

enum class DayOfWeekPolish(val shortName: String, val fullName: String) {
    MON("Pn", "Poniedziałek"),
    TUE("Wt", "Wtorek"),
    WED("Śr", "Środa"),
    THU("Czw", "Czwartek"),
    FRI("Pt", "Piątek"),
    SAT("Sob", "Sobota"),
    SUN("Nd", "Niedziela")
}

data class PlannedMeal(
    val id: String = UUID.randomUUID().toString(),
    val day: DayOfWeekPolish = DayOfWeekPolish.MON,
    val recipeId: String,
    val recipeTitle: String = "Danie",
    val portions: Int = 2
)

data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalAmount: Double,
    val unit: String,
    val category: StoreCategory = StoreCategory.SUCHE_PRZYPRAWY,
    val formattedDisplay: String,
    val isChecked: Boolean = false,
    val sources: List<String> = emptyList()
)

object ShoppingAggregator {

    fun aggregate(plannedMeals: List<PlannedMeal>?, database: List<Recipe>?): List<ShoppingItem> {
        if (plannedMeals.isNullOrEmpty() || database.isNullOrEmpty()) return emptyList()

        return try {
            // Group ingredients by normalized name and unit
            val rawItems = mutableListOf<Pair<RecipeIngredient, Pair<Double, String>>>()

            for (meal in plannedMeals) {
                if (meal.recipeId.isBlank()) continue
                val recipe = database.find { it.id == meal.recipeId } ?: continue
                val safePortions = meal.portions.coerceIn(1, 24)
                val scaledIngredients = try {
                    PortionCalculator.scaleIngredients(recipe, safePortions)
                } catch (e: Throwable) {
                    Log.w("ShoppingAggregator", "Nie udało się przeskalować składników dla ${recipe.title}", e)
                    emptyList()
                }

                val safeMealTitle = meal.recipeTitle.ifBlank { recipe.title.ifBlank { "Danie" } }
                for (ing in scaledIngredients) {
                    val amount = if (ing.scaledAmount.isFinite() && ing.scaledAmount > 0.0) ing.scaledAmount else 1.0
                    rawItems.add(
                        ing.original to (amount to "$safeMealTitle ($safePortions porcj.)")
                    )
                }
            }

            if (rawItems.isEmpty()) return emptyList()

            // Key by canonical name & unit
            val grouped = rawItems.groupBy { (ing, _) ->
                canonicalIngredientKey(ing.name) to (ing.unit ?: "").lowercase().trim()
            }

            val result = mutableListOf<ShoppingItem>()

            for ((key, entries) in grouped) {
                if (entries.isEmpty()) continue
                val (canonName, unit) = key
                val sampleIng = entries.first().first
                val totalAmount = entries.sumOf { it.second.first }
                val sources = entries.map { it.second.second }.filter { it.isNotBlank() }.distinct()
                val safeCategory = sampleIng.category ?: StoreCategory.SUCHE_PRZYPRAWY

                val safeTotal = if (totalAmount.isFinite() && totalAmount > 0.0) totalAmount else 0.0
                val display = when {
                    unit.isEmpty() -> canonName
                    unit in listOf("g", "ml") && safeTotal >= 1000 -> {
                        val kUnit = if (unit == "g") "kg" else "l"
                        val inKilo = safeTotal / 1000.0
                        val formatted = if (inKilo % 1.0 == 0.0) {
                            "${inKilo.toLong()} $kUnit"
                        } else {
                            "${String.format(java.util.Locale.US, "%.2f", inKilo)} $kUnit"
                        }
                        "$canonName - $formatted"
                    }
                    safeTotal % 1.0 == 0.0 -> "$canonName - ${safeTotal.toLong()} $unit"
                    else -> "$canonName - ${String.format(java.util.Locale.US, "%.1f", safeTotal)} $unit"
                }

                val stableId = "shop_${safeCategory.name}_${canonName.lowercase().trim()}_${unit.lowercase().trim()}"
                    .replace(Regex("[^a-zA-Z0-9_]"), "_")

                result.add(
                    ShoppingItem(
                        id = stableId,
                        name = canonName,
                        totalAmount = safeTotal,
                        unit = unit,
                        category = safeCategory,
                        formattedDisplay = display,
                        isChecked = false,
                        sources = sources
                    )
                )
            }

            // Sort by Category order, then name
            result.sortedWith(compareBy({ it.category.ordinal }, { it.name }))
        } catch (e: Throwable) {
            Log.e("ShoppingAggregator", "Krytyczny błąd podczas agregacji listy zakupów", e)
            emptyList()
        }
    }

    private fun canonicalIngredientKey(raw: String?): String {
        if (raw.isNullOrBlank()) return "Inne składniki"
        val lower = raw.lowercase().trim()
        return when {
            "jajk" in lower -> "Jajka"
            "chleb" in lower || "chałka" in lower -> "Chleb pełnoziarnisty / Pieczywo"
            "mleko 2%" in lower || "mleko krowie" in lower || "mleko migdałowe" in lower -> "Mleko"
            "cynamon" in lower -> "Cynamon mielony"
            "oliwa" in lower -> "Oliwa z oliwek"
            "borówk" in lower -> "Borówki amerykańskie"
            "malin" in lower -> "Maliny świeże"
            "siemię" in lower -> "Siemię lniane całe"
            "imbir" in lower -> "Świeży korzeń imbiru"
            "makaron" in lower -> "Makaron spaghetti pełnoziarnisty"
            "indyk" in lower || "mieso" in lower || "mięso mielone" in lower -> "Mięso mielone z indyka"
            "passata" in lower -> "Passata pomidorowa"
            "cebula" in lower -> "Cebula"
            "czosnek" in lower -> "Czosnek"
            "marchew" in lower -> "Marchew"
            "ryż" in lower -> "Ryż jaśminowy / basmati"
            "groszek" in lower -> "Zielony groszek"
            "sos sojowy" in lower -> "Sos sojowy ciemny"
            "papryka" in lower -> "Papryka czerwona"
            "pomidor" in lower -> "Pomidory krojone"
            "feta" in lower -> "Ser feta"
            "komosa" in lower || "quinoa" in lower -> "Komosa ryżowa (quinoa)"
            "awokado" in lower -> "Awokado hass"
            "szpinak" in lower -> "Szpinak baby świeży"
            "ogórek" in lower -> "Ogórek zielony gruntowy"
            "pestki dyni" in lower -> "Pestki dyni"
            "dynia" in lower -> "Dynia Hokkaido"
            "mleczko kokosowe" in lower -> "Mleczko kokosowe"
            "płatki owsiane" in lower -> "Płatki owsiane górskie"
            "odżywka białkowa" in lower -> "Odżywka białkowa"
            else -> raw.replaceFirstChar { it.uppercase() }
        }
    }
}
