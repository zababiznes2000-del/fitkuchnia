package com.example

import com.example.data.PortionCalculator
import com.example.data.Recipe
import com.example.data.RecipeIngredient
import com.example.data.RecipeStep
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testStepScalingVariousUnits() {
    val inputs = listOf(
        "Wlej 800g wody do garnka i wsyp 61g siemia lnianego" to "Wlej 1600g wody do garnka i wsyp 122g siemia lnianego",
        "Wlej 800 g wody do garnka i wsyp 61 g siemienia" to "Wlej 1600 g wody do garnka i wsyp 122 g siemienia",
        "Dodaj 2 łyżki oliwy i 1 łyżeczkę soli" to "Dodaj 4 łyżki oliwy i 2 łyżeczki soli",
        "Wsyp 1/2 szklanki mąki oraz 250ml mleka" to "Wsyp 1 szklanka mąki oraz 500ml mleka",
        "Piecz przez 15 minut w 180°C, krok 1 z 3, na poziomie 2" to "Piecz przez 15 minut w 180°C, krok 1 z 3, na poziomie 2",
        "Gotuj 2 godziny na małym ogniu, dodaj 2 ząbki czosnku" to "Gotuj 2 godziny na małym ogniu, dodaj 4 ząbki czosnku",
        "Użyj 1 puszka pomidorów, 100 g fety i 1 plaster sera" to "Użyj 2 puszki pomidorów, 200 g fety i 2 plastry sera",
        "Wlej 1.5 litra bulionu i wrzuć 2 garści szpinaku" to "Wlej 3 litry bulionu i wrzuć 4 garście szpinaku",
        "Odstaw na 30 sek, użyj 1-2 łyżki miodu i 50-100g orzechów" to "Odstaw na 30 sek, użyj 2-4 łyżki miodu i 100-200g orzechów"
    )

    for ((input, expected) in inputs) {
      val scaled = PortionCalculator.scaleStepInstructionText(input, 2.0)
      println("IN:  $input")
      println("OUT: $scaled")
      assertEquals(expected, scaled)
    }
  }

  @Test
  fun testPortionCalculatorRealRecipe() {
    val recipe = Recipe(
        id = "test_recipe_1",
        title = "Siemię lniane z wodą",
        description = "Przepis testowy",
        prepTimeMinutes = 15,
        kcalPerPortion = 250,
        fitPoints = 95,
        fitPointsReason = "Wysoka zawartość błonnika",
        proteinG = 10,
        fatG = 12,
        carbsG = 15,
        basePortions = 2,
        categories = setOf(com.example.data.RecipeCategory.SNIADANIE),
        ingredients = listOf(
            RecipeIngredient(name = "woda", amount = 800.0, unit = "g", category = com.example.data.StoreCategory.WARZYWA_OWOCE),
            RecipeIngredient(name = "siemię lniane", amount = 61.0, unit = "g", category = com.example.data.StoreCategory.SUCHE_PRZYPRAWY)
        ),
        steps = listOf(
            RecipeStep(
                stepNumber = 1,
                instruction = "Wlej 800g wody do garnka i wsyp 61g siemia lnianego, gotuj przez 15 minut w 100°C.",
                timerMinutes = 15
            )
        )
    )

    // Skalowanie na 4 porcje (mnożnik x2)
    val scaled4 = PortionCalculator.scaleRecipe(recipe, 4)
    val step4Text = scaled4.steps[0].text
    println("SCALED 4 PORTIONS: $step4Text")
    assertTrue(step4Text.contains("1600g wody"))
    assertTrue(step4Text.contains("122g siemia lnianego"))
    assertTrue(step4Text.contains("15 minut")) // czas NIE zmieniony!
    assertTrue(step4Text.contains("100°C")) // temperatura NIE zmieniona!

    // Skalowanie na 1 porcję (mnożnik x0.5)
    val scaled1 = PortionCalculator.scaleRecipe(recipe, 1)
    val step1Text = scaled1.steps[0].text
    println("SCALED 1 PORTION: $step1Text")
    assertTrue(step1Text.contains("400g wody"))
    assertTrue(step1Text.contains("30.5g siemia lnianego"))
    assertTrue(step1Text.contains("15 minut"))
    assertTrue(step1Text.contains("100°C"))
  }
}
