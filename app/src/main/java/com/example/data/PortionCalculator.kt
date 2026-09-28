package com.example.data

import kotlin.math.roundToInt

data class ScaledRecipeData(
    val portions: Int,
    val multiplier: Double,
    val ingredients: List<ScaledIngredient>,
    val steps: List<ScaledStep>,
    val totalKcal: Int,
    val totalProteinG: Int,
    val totalFatG: Int,
    val totalCarbsG: Int
)

data class ScaledIngredient(
    val original: RecipeIngredient,
    val scaledAmount: Double,
    val formattedAmountWithUnit: String,
    val smartNote: String? = null
)

data class ScaledStep(
    val stepNumber: Int,
    val text: String,
    val timerMinutes: Int?,
    val tip: String?
)

object PortionCalculator {

    fun scaleIngredients(recipe: Recipe, targetPortions: Int): List<ScaledIngredient> {
        return try {
            val safeTarget = targetPortions.coerceIn(1, 24)
            val base = recipe.safeBasePortions.coerceIn(1, 24)
            val multiplier = (safeTarget.toDouble() / base.toDouble()).coerceIn(0.01, 100.0)

            val rawList = recipe.safeIngredients
            if (rawList.isEmpty()) return emptyList()

            rawList.map { ing ->
                try {
                    val rawAmount = if (ing.amount.isFinite() && ing.amount > 0.0) ing.amount else 1.0
                    val rawScaled = (rawAmount * multiplier).coerceIn(0.01, 100000.0)
                    val (roundedAmount, formatted, smartNote) = formatSmartAmount(rawScaled, ing.unit ?: "", ing.name ?: "")
                    ScaledIngredient(
                        original = ing,
                        scaledAmount = roundedAmount,
                        formattedAmountWithUnit = formatted,
                        smartNote = smartNote
                    )
                } catch (_: Throwable) {
                    ScaledIngredient(
                        original = ing,
                        scaledAmount = ing.amount,
                        formattedAmountWithUnit = "${ing.amount} ${ing.unit ?: ""}".trim(),
                        smartNote = null
                    )
                }
            }
        } catch (_: Throwable) {
            recipe.safeIngredients.map { ing ->
                ScaledIngredient(
                    original = ing,
                    scaledAmount = ing.amount,
                    formattedAmountWithUnit = "${ing.amount} ${ing.unit ?: ""}".trim(),
                    smartNote = null
                )
            }
        }
    }

    fun scaleRecipe(recipe: Recipe, targetPortions: Int): ScaledRecipeData {
        return try {
            val safeTarget = targetPortions.coerceIn(1, 24)
            val base = recipe.safeBasePortions.coerceIn(1, 24)
            val multiplier = (safeTarget.toDouble() / base.toDouble()).coerceIn(0.01, 100.0)

            val scaledIngredients = scaleIngredients(recipe, safeTarget)

            val scaledSteps = recipe.safeSteps.mapIndexed { idx, step ->
                val safeText = try {
                    var text = step.instruction ?: ""

                    // Interpolate placeholder tokens {amount_xxx} with matching scaled ingredients
                    for (scaledIng in scaledIngredients) {
                        try {
                            val origName = scaledIng.original.name ?: ""
                            val ingKey = findKeyForIngredient(origName)
                            if (ingKey.isNotEmpty()) {
                                val token = "{amount_$ingKey}"
                                if (text.contains(token)) {
                                    text = text.replace(token, formatNumberNicely(scaledIng.scaledAmount))
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    // Regex-based dynamic scaling of ingredient quantities in step preparation text
                    text = scaleStepInstructionText(text, scaledIngredients, multiplier)
                    text = replaceRemainingTokens(text, scaledIngredients, multiplier)
                    text
                } catch (_: Throwable) {
                    // W razie jakiegokolwiek błędu w tekście wyświetlamy oryginalny tekst instrukcji
                    step.instruction ?: ""
                }

                ScaledStep(
                    stepNumber = if (step.stepNumber > 0) step.stepNumber else idx + 1,
                    text = if (safeText.isNotBlank()) safeText else (step.instruction ?: ""),
                    timerMinutes = step.timerMinutes,
                    tip = step.tip
                )
            }

            ScaledRecipeData(
                portions = safeTarget,
                multiplier = multiplier,
                ingredients = scaledIngredients,
                steps = scaledSteps,
                totalKcal = (recipe.kcalPerPortion * safeTarget).coerceAtLeast(0),
                totalProteinG = (recipe.proteinG * safeTarget).coerceAtLeast(0),
                totalFatG = (recipe.fatG * safeTarget).coerceAtLeast(0),
                totalCarbsG = (recipe.carbsG * safeTarget).coerceAtLeast(0)
            )
        } catch (_: Throwable) {
            createSafeFallback(recipe, targetPortions)
        }
    }

    fun createSafeFallback(recipe: Recipe, targetPortions: Int): ScaledRecipeData {
        return try {
            val safeTarget = targetPortions.coerceIn(1, 24)
            ScaledRecipeData(
                portions = safeTarget,
                multiplier = 1.0,
                ingredients = recipe.safeIngredients.map {
                    ScaledIngredient(
                        original = it,
                        scaledAmount = it.amount,
                        formattedAmountWithUnit = "${it.amount} ${it.unit ?: ""}".trim(),
                        smartNote = null
                    )
                },
                steps = recipe.safeSteps.mapIndexed { idx, it ->
                    ScaledStep(
                        stepNumber = if (it.stepNumber > 0) it.stepNumber else idx + 1,
                        text = it.instruction ?: "",
                        timerMinutes = it.timerMinutes,
                        tip = it.tip
                    )
                },
                totalKcal = (recipe.kcalPerPortion * safeTarget).coerceAtLeast(0),
                totalProteinG = (recipe.proteinG * safeTarget).coerceAtLeast(0),
                totalFatG = (recipe.fatG * safeTarget).coerceAtLeast(0),
                totalCarbsG = (recipe.carbsG * safeTarget).coerceAtLeast(0)
            )
        } catch (_: Throwable) {
            ScaledRecipeData(
                portions = targetPortions.coerceIn(1, 24),
                multiplier = 1.0,
                ingredients = emptyList(),
                steps = emptyList(),
                totalKcal = 400,
                totalProteinG = 20,
                totalFatG = 15,
                totalCarbsG = 40
            )
        }
    }

    private fun findKeyForIngredient(name: String): String {
        val lower = name.lowercase()
        return when {
            "chleb" in lower || "chałka" in lower -> "chleb"
            "jajk" in lower -> "jajka"
            "mleko" in lower -> "mleko"
            "cynamon" in lower -> "cynamon"
            "oliwa" in lower || "masło" in lower || "olej" in lower -> "oliwa"
            "borówk" in lower || "malin" in lower -> "borowki"
            "siemię" in lower -> "siemie"
            "imbir" in lower -> "imbir"
            "woda" in lower -> "woda"
            "miód" in lower -> "miod"
            "makaron" in lower -> "makaron"
            "mielone" in lower || "indyk" in lower -> "mieso"
            "passata" in lower -> "passata"
            "cebula" in lower -> "cebula"
            "czosnek" in lower -> "czosnek"
            "marchew" in lower -> "marchewka"
            "ryż" in lower -> "ryz"
            "groszek" in lower -> "groszek"
            "sos sojowy" in lower -> "sos"
            "papryka" in lower -> "papryka"
            "pomidor" in lower -> "pomidory"
            "feta" in lower -> "feta"
            "komosa" in lower || "quinoa" in lower -> "quinoa"
            "awokado" in lower -> "awokado"
            "ogórek" in lower -> "ogorek"
            "dynia" in lower -> "dynia"
            "bulion" in lower -> "bulion"
            "płatki owsiane" in lower -> "platki"
            "odżywka" in lower -> "bialko"
            "migdał" in lower -> "migdaly"
            else -> ""
        }
    }

    private fun formatSmartAmount(raw: Double, unit: String, name: String): Triple<Double, String, String?> {
        val safeRaw = if (raw.isFinite() && raw > 0.0) raw else 1.0
        val lowerName = (name ?: "").lowercase()
        val safeUnit = unit ?: ""
        return when {
            "jaj" in lowerName -> {
                // Logical egg rounding: avoid fractions like 0.33
                val rounded = when {
                    safeRaw <= 0.6 -> 1.0
                    safeRaw in 1.1..1.4 -> 1.0 // or 1 + 1 żółtko
                    safeRaw in 1.4..1.8 -> 2.0
                    else -> try {
                        safeRaw.roundToInt().toDouble()
                    } catch (_: Throwable) {
                        Math.round(safeRaw).toDouble()
                    }
                }
                val note = if (safeRaw % 1.0 != 0.0 && safeRaw in 0.2..0.8) "(lub 1 żółtko)" else null
                Triple(rounded, "${rounded.toInt()} szt.", note)
            }
            safeUnit in listOf("g", "ml") -> {
                // Round to nearest 5 or 10 for clean kitchen measures
                val rounded = when {
                    safeRaw >= 50 -> (Math.round(safeRaw / 5.0) * 5).toDouble()
                    safeRaw >= 10 -> (Math.round(safeRaw)).toDouble()
                    else -> (Math.round(safeRaw * 2.0) / 2.0)
                }
                Triple(rounded, "${formatNumberNicely(rounded)} $safeUnit", null)
            }
            safeUnit in listOf("szt.", "kromki", "ząbki") -> {
                val rounded = if (safeRaw < 1.0) 1.0 else (Math.round(safeRaw * 2.0) / 2.0)
                Triple(rounded, "${formatNumberNicely(rounded)} $safeUnit", null)
            }
            else -> {
                val rounded = (Math.round(safeRaw * 2.0) / 2.0)
                Triple(rounded, "${formatNumberNicely(rounded)} $safeUnit", null)
            }
        }
    }

    private fun formatNumberNicely(v: Double): String {
        val safeV = if (v.isFinite()) v else 0.0
        return if (safeV % 1.0 == 0.0) {
            safeV.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", safeV)
        }
    }

    private val culinaryUnitsRegex = Regex(
        "(?<![a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ0-9])((?:\\d+/\\d+)|\\d+(?:[.,]\\d+)?)(?:(\\s*[-–]\\s*)((?:\\d+/\\d+)|\\d+(?:[.,]\\d+)?))?(\\s*)(g|gr|gram(?:y|ów|a)?|kg|kilogram(?:y|ów|a)?|dag|dkg|dekagram(?:y|ów|a)?|ml|mililitr(?:y|ów|a)?|l|litr(?:a|ów|y)?|cl|dl|łyż(?:ka|ki|ek|kę|kami)|łyżeczk(?:a|i|ek|ę|ami)|szklan(?:ka|ki|ek|kę|kami|ce)|kieliszk(?:a|i|ów)|kieliszek|kub(?:ek|ka|ki|ków)|miar(?:ka|ki|ek|kę)|szt(?:uka|uki|uk|ukę|\\.)?|ząb(?:ek|ka|ki|ków)|plaster(?:ek|ka|ki|ków)?|plastr(?:y|ów|a|ze)?|krom(?:ka|ki|ek|kę)|kropl(?:a|i|e|el|ę)|puszk(?:a|i|ek|ę)|opakowan(?:ie|ia|ień|iu)|op\\.|kost(?:ka|ki|ek|kę)|garś(?:ć|ci|cie)|garst(?:ka|ki|ek)|szczypt(?:a|y|ę)?|porcj(?:a|i|e|ę)|kawał(?:ek|ka|ki|ków)|pęcz(?:ek|ka|ki|ków)|liś(?:ć|cia|cie|ci)|list(?:ek|ka|ki|ków)|ziar(?:no|na|en|nko|nka|enko|enka)|filet(?:y|a|ów)?|gałąz(?:ka|ki|ek)|łody(?:ga|gi|g))(?![a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ])",
        setOf(RegexOption.IGNORE_CASE)
    )

    private fun parseStepNumber(numStr: String): Double? {
        return try {
            val clean = numStr.trim()
            if (clean.contains("/")) {
                val parts = clean.split("/")
                if (parts.size == 2) {
                    val num = parts[0].trim().replace(",", ".").toDoubleOrNull()
                    val den = parts[1].trim().replace(",", ".").toDoubleOrNull()
                    if (num != null && den != null && den != 0.0 && num.isFinite() && den.isFinite()) {
                        return num / den
                    }
                }
            }
            clean.replace(",", ".").toDoubleOrNull()?.takeIf { it.isFinite() }
        } catch (_: Throwable) {
            null
        }
    }

    private fun formatStepScaledNumber(value: Double): String {
        return try {
            val safe = if (value.isFinite()) value else 1.0
            if (safe % 1.0 == 0.0) {
                return safe.toLong().toString()
            }
            if ((safe * 2) % 1.0 == 0.0) {
                return String.format(java.util.Locale.US, "%.1f", safe)
            }
            if ((safe * 4) % 1.0 == 0.0) {
                return String.format(java.util.Locale.US, "%.2f", safe).trimEnd('0')
            }
            val rounded = Math.round(safe).toDouble()
            if (Math.abs(safe - rounded) < 0.05) {
                return rounded.toLong().toString()
            }
            String.format(java.util.Locale.US, "%.1f", safe).trimEnd('0').trimEnd('.')
        } catch (_: Throwable) {
            value.toString()
        }
    }

    private fun declineStepUnit(baseUnit: String, count: Double): String {
        return try {
            val lower = baseUnit.lowercase()
            val isFraction = count % 1.0 != 0.0
            val roundedInt = try { count.toInt() } catch (_: Throwable) { 1 }

            fun form(one: String, few: String, many: String, fraction: String = few): String {
                return try {
                    when {
                        isFraction -> fraction
                        count == 1.0 -> if (baseUnit.endsWith("ę", ignoreCase = true) && one.endsWith("a") && one.length > 1) one.dropLast(1) + "ę" else one
                        roundedInt % 10 in 2..4 && roundedInt % 100 !in 12..14 -> few
                        else -> many
                    }
                } catch (_: Throwable) {
                    baseUnit
                }
            }

            when {
                lower.startsWith("łyżeczk") -> form("łyżeczka", "łyżeczki", "łyżeczek")
                lower.startsWith("łyż") -> form("łyżka", "łyżki", "łyżek")
                lower.startsWith("szklan") -> form("szklanka", "szklanki", "szklanek")
                lower.startsWith("ząb") -> form("ząbek", "ząbki", "ząbków", "ząbka")
                lower.startsWith("plastr") || lower.startsWith("plaster") -> form("plaster", "plastry", "plastrów", "plastra")
                lower.startsWith("krom") -> form("kromka", "kromki", "kromek")
                lower.startsWith("puszk") -> form("puszka", "puszki", "puszek")
                lower.startsWith("opakowan") -> form("opakowanie", "opakowania", "opakowań")
                lower.startsWith("kost") -> form("kostka", "kostki", "kostek")
                lower.startsWith("garś") || lower.startsWith("garst") -> form("garść", "garście", "garści", "garści")
                lower.startsWith("szczypt") -> form("szczypta", "szczypty", "szczypt")
                lower.startsWith("kieliszk") || lower.startsWith("kieliszek") -> form("kieliszek", "kieliszki", "kieliszków", "kieliszka")
                lower.startsWith("kub") -> form("kubek", "kubki", "kubków", "kubka")
                lower.startsWith("litr") -> form("litr", "litry", "litrów", "litra")
                lower.startsWith("pęcz") -> form("pęczek", "pęczki", "pęczków", "pęczka")
                lower.startsWith("kawał") -> form("kawałek", "kawałki", "kawałków", "kawałka")
                lower.startsWith("sztuk") -> form("sztuka", "sztuki", "sztuk")
                else -> baseUnit
            }
        } catch (_: Throwable) {
            baseUnit
        }
    }

    /**
     * Skaluje ilości składników w tekście instrukcji przygotowania za pomocą Regex.
     * Wykrywa wyłącznie kulinarne jednostki miary (g, kg, ml, l, szklanka, łyżka itp.),
     * inteligentnie ignorując czasy gotowania (minuty, godziny, sekundy), temperatury (°C, stopni),
     * numery kroków oraz inne cyfry niebędące ilościami składników.
     */
    fun scaleStepInstructionText(
        text: String,
        ingredients: List<ScaledIngredient> = emptyList(),
        multiplier: Double
    ): String {
        if (multiplier == 1.0 || text.isBlank()) return text

        return try {
            culinaryUnitsRegex.replace(text) { match ->
                try {
                    val num1Str = match.groups[1]?.value ?: return@replace match.value
                    val rangeDash = match.groups[2]?.value
                    val num2Str = match.groups[3]?.value
                    val space = match.groups[4]?.value ?: ""
                    val unitStr = match.groups[5]?.value ?: return@replace match.value

                    val val1 = parseStepNumber(num1Str)
                    if (val1 != null && val1 > 0) {
                        val scaledVal1 = val1 * multiplier
                        val formatted1 = formatStepScaledNumber(scaledVal1)

                        if (rangeDash != null && num2Str != null) {
                            val val2 = parseStepNumber(num2Str)
                            if (val2 != null && val2 > 0) {
                                val scaledVal2 = val2 * multiplier
                                val formatted2 = formatStepScaledNumber(scaledVal2)
                                val declinedUnit = declineStepUnit(unitStr, scaledVal2)
                                "$formatted1$rangeDash$formatted2$space$declinedUnit"
                            } else {
                                val declinedUnit = declineStepUnit(unitStr, scaledVal1)
                                "$formatted1$space$declinedUnit"
                            }
                        } else {
                            val declinedUnit = declineStepUnit(unitStr, scaledVal1)
                            "$formatted1$space$declinedUnit"
                        }
                    } else {
                        match.value
                    }
                } catch (_: Throwable) {
                    match.value
                }
            }
        } catch (_: Throwable) {
            text
        }
    }

    fun scaleStepInstructionText(text: String, multiplier: Double): String {
        return scaleStepInstructionText(text, emptyList(), multiplier)
    }

    private fun replaceRemainingTokens(
        text: String,
        ingredients: List<ScaledIngredient>,
        multiplier: Double
    ): String {
        return try {
            var result = text
            // Clean any leftover curly brackets if any token wasn't mapped
            val regex = Regex("\\{amount_[a-zA-Z0-9_]+\\}")
            result = regex.replace(result) { match ->
                try {
                    val num = (multiplier).coerceAtLeast(1.0)
                    formatNumberNicely(num)
                } catch (_: Throwable) {
                    match.value
                }
            }
            result
        } catch (_: Throwable) {
            text
        }
    }
}
