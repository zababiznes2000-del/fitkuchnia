package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.*
import com.example.ui.screens.FeaturedDailyDishCard
import com.example.ui.theme.FitKuchniaTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleRecipe = Recipe(
      id = "preview-dish",
      title = "Awokado Bowl z Jajkiem Poche",
      description = "Zbalansowany posiłek pełen zdrowych tłuszczów, błonnika i pełnowartościowego białka.",
      prepTimeMinutes = 15,
      kcalPerPortion = 380,
      fitPoints = 94,
      fitPointsReason = "Wysoka gęstość odżywcza, bogactwo kwasów omega-9 oraz antyoksydantów.",
      proteinG = 18,
      fatG = 22,
      carbsG = 28,
      basePortions = 1,
      categories = setOf(RecipeCategory.SNIADANIE, RecipeCategory.NA_CIEPLO),
      ingredients = listOf(
        RecipeIngredient(name = "Jajko", amount = 2.0, unit = "szt.", category = StoreCategory.NABIAL),
        RecipeIngredient(name = "Awokado", amount = 1.0, unit = "szt.", category = StoreCategory.WARZYWA_OWOCE)
      ),
      steps = listOf(
        RecipeStep(stepNumber = 1, instruction = "Ugotuj jajka w koszulce.", timerMinutes = 3)
      )
    )

    composeTestRule.setContent {
      FitKuchniaTheme {
        FeaturedDailyDishCard(
          recipe = sampleRecipe,
          onRecipeClick = {},
          isFavorite = true,
          onToggleFavorite = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
