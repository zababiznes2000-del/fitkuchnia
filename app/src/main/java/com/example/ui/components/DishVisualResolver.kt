package com.example.ui.components

import com.example.R
import com.example.data.Recipe
import com.example.data.RecipeCategory

/**
 * Zapewnia neutralny, spójny fallback graficzny w przypadku braku zdjęcia.
 * Całkowicie wyłączono jakiekolwiek losowe lub generowane przez AI grafiki.
 */
object DishVisualResolver {

    fun getDrawableForRecipe(recipe: Recipe): Int {
        return R.drawable.dish_of_day_hero
    }

    fun getDrawableFor(
        title: String,
        categories: Set<RecipeCategory> = emptySet(),
        ingredientNames: List<String> = emptyList()
    ): Int {
        return R.drawable.dish_of_day_hero
    }
}
