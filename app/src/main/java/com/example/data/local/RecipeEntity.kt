package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.*
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val prepTimeMinutes: Int,
    val kcalPerPortion: Int,
    val fitPoints: Int,
    val fitPointsReason: String,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val basePortions: Int,
    val categoriesJson: String,
    val matchedAilmentsJson: String,
    val ingredientsJson: String,
    val stepsJson: String,
    val beverageTitle: String? = null,
    val beverageDescription: String? = null,
    val beverageWhyItMatches: String? = null,
    val isDailyDish: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val author: String = "FitKuchnia Chef",
    val authorEmail: String = "zababiznes2000@gmail.com",
    val moderationStatus: String = "APPROVED",
    val imagePath: String? = null,
    val imageBase64: String? = null
) {
    fun toDomainModel(): Recipe {
        val categories = mutableSetOf<RecipeCategory>()
        try {
            val catArray = JSONArray(categoriesJson)
            for (i in 0 until catArray.length()) {
                val catName = catArray.getString(i)
                RecipeCategory.values().find { it.name == catName }?.let { categories.add(it) }
            }
        } catch (_: Exception) {}

        val ailments = mutableListOf<String>()
        try {
            val ailArray = JSONArray(matchedAilmentsJson)
            for (i in 0 until ailArray.length()) {
                ailments.add(ailArray.getString(i))
            }
        } catch (_: Exception) {}

        val ingredients = mutableListOf<RecipeIngredient>()
        try {
            val ingArray = JSONArray(ingredientsJson)
            for (i in 0 until ingArray.length()) {
                val item = ingArray.opt(i)
                if (item is JSONObject) {
                    val catStr = item.optString("category", StoreCategory.SUCHE_PRZYPRAWY.name)
                    val cat = StoreCategory.values().find { it.name.equals(catStr, ignoreCase = true) } ?: StoreCategory.SUCHE_PRZYPRAWY
                    val name = item.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        ingredients.add(
                            RecipeIngredient(
                                name = name,
                                amount = item.optDouble("amount", item.optDouble("baseAmount", 1.0)),
                                unit = item.optString("unit", "szt."),
                                category = cat
                            )
                        )
                    }
                } else if (item is String && item.isNotBlank()) {
                    ingredients.add(RecipeIngredient(name = item, amount = 1.0, unit = "szt.", category = StoreCategory.WARZYWA_OWOCE))
                }
            }
        } catch (_: Exception) {}

        val steps = mutableListOf<RecipeStep>()
        try {
            val stepArray = JSONArray(stepsJson)
            for (i in 0 until stepArray.length()) {
                val item = stepArray.opt(i)
                if (item is JSONObject) {
                    val instruction = item.optString("instruction", "").trim()
                    if (instruction.isNotEmpty()) {
                        steps.add(
                            RecipeStep(
                                stepNumber = item.optInt("stepNumber", i + 1),
                                instruction = instruction,
                                timerMinutes = if (item.has("timerMinutes") && !item.isNull("timerMinutes")) item.optInt("timerMinutes") else null,
                                tip = if (item.has("tip") && !item.isNull("tip")) item.optString("tip") else null
                            )
                        )
                    }
                } else if (item is String && item.isNotBlank()) {
                    steps.add(RecipeStep(stepNumber = i + 1, instruction = item))
                }
            }
        } catch (_: Exception) {}

        val pairing = if (!beverageTitle.isNullOrBlank()) {
            BeveragePairing(
                title = beverageTitle,
                description = beverageDescription ?: "",
                whyItMatches = beverageWhyItMatches ?: ""
            )
        } else null

        val modStatus = try {
            RecipeModerationStatus.valueOf(moderationStatus)
        } catch (_: Exception) {
            RecipeModerationStatus.APPROVED
        }

        return Recipe(
            id = id,
            title = title,
            description = description,
            prepTimeMinutes = prepTimeMinutes,
            kcalPerPortion = kcalPerPortion,
            fitPoints = fitPoints,
            fitPointsReason = fitPointsReason,
            proteinG = proteinG,
            fatG = fatG,
            carbsG = carbsG,
            basePortions = basePortions,
            categories = categories,
            matchedAilments = ailments,
            ingredients = ingredients,
            steps = steps,
            beveragePairing = pairing,
            isVerified = modStatus == RecipeModerationStatus.APPROVED,
            author = author,
            authorEmail = authorEmail,
            moderationStatus = modStatus,
            imagePath = imagePath,
            imageBase64 = imageBase64
        )
    }

    companion object {
        fun fromDomainModel(recipe: Recipe, isDaily: Boolean = false): RecipeEntity {
            val catArray = JSONArray()
            recipe.categories.forEach { catArray.put(it.name) }

            val ailArray = JSONArray()
            recipe.matchedAilments.forEach { ailArray.put(it) }

            val ingArray = JSONArray()
            recipe.ingredients.forEach { ing ->
                val obj = JSONObject().apply {
                    put("name", ing.name)
                    put("amount", ing.amount)
                    put("unit", ing.unit)
                    put("category", ing.category.name)
                }
                ingArray.put(obj)
            }

            val stepArray = JSONArray()
            recipe.steps.forEach { step ->
                val obj = JSONObject().apply {
                    put("stepNumber", step.stepNumber)
                    put("instruction", step.instruction)
                    put("timerMinutes", step.timerMinutes ?: JSONObject.NULL)
                    put("tip", step.tip ?: JSONObject.NULL)
                }
                stepArray.put(obj)
            }

            return RecipeEntity(
                id = recipe.id,
                title = recipe.title,
                description = recipe.description,
                prepTimeMinutes = recipe.prepTimeMinutes,
                kcalPerPortion = recipe.kcalPerPortion,
                fitPoints = recipe.fitPoints,
                fitPointsReason = recipe.fitPointsReason,
                proteinG = recipe.proteinG,
                fatG = recipe.fatG,
                carbsG = recipe.carbsG,
                basePortions = recipe.basePortions,
                categoriesJson = catArray.toString(),
                matchedAilmentsJson = ailArray.toString(),
                ingredientsJson = ingArray.toString(),
                stepsJson = stepArray.toString(),
                beverageTitle = recipe.beveragePairing?.title,
                beverageDescription = recipe.beveragePairing?.description,
                beverageWhyItMatches = recipe.beveragePairing?.whyItMatches,
                isDailyDish = isDaily,
                author = recipe.author,
                authorEmail = recipe.authorEmail,
                moderationStatus = recipe.moderationStatus.name,
                imagePath = recipe.imagePath,
                imageBase64 = recipe.imageBase64
            )
        }
    }
}
