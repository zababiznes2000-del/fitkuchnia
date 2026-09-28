package com.example

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.ai.CulinaryAiEngine
import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private val testToastRecipe = Recipe(
        id = "tosty-francuskie",
        title = "Tosty francuskie z cynamonem",
        description = "Chrupiące tosty",
        prepTimeMinutes = 10,
        kcalPerPortion = 250,
        fitPoints = 88,
        fitPointsReason = "Pyszne i proste",
        proteinG = 12,
        fatG = 8,
        carbsG = 30,
        basePortions = 2,
        categories = setOf(RecipeCategory.SNIADANIE),
        ingredients = listOf(
            RecipeIngredient("Chleb", 2.0, "kromki", StoreCategory.SUCHE_PRZYPRAWY),
            RecipeIngredient("Jajka", 1.0, "szt.", StoreCategory.NABIAL)
        ),
        steps = listOf(
            RecipeStep(1, "Roztrzep {amount_jajka} jajka w misce z cynamonem.", 2)
        )
    )

    private val testFlaxseedRecipe = Recipe(
        id = "napar-siemie-imbir",
        title = "Napar z siemienia lnianego",
        description = "Osłona żołądka",
        prepTimeMinutes = 15,
        kcalPerPortion = 70,
        fitPoints = 99,
        fitPointsReason = "Śluzy osłonowe",
        proteinG = 2,
        fatG = 3,
        carbsG = 5,
        basePortions = 1,
        categories = setOf(RecipeCategory.NA_CIEPLO),
        matchedAilments = listOf("brzuch", "zgaga", "żołądek"),
        ingredients = listOf(
            RecipeIngredient("Siemię lniane", 1.0, "łyżka", StoreCategory.SUCHE_PRZYPRAWY)
        ),
        steps = listOf(
            RecipeStep(1, "Zalej wrzątkiem i odstaw na 10 minut.")
        )
    )

    private val fixtureRecipes = listOf(testToastRecipe, testFlaxseedRecipe)

    @Test
    fun `read string from context matches FitKuchnia`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FitKuchnia", appName)
    }

    @Test
    fun `module 1 - fridge search returns verified recipe`() {
        val (recipe, msg) = CulinaryAiEngine.searchFridgeAndAilments("mam jajka i chleb", fixtureRecipes)
        assertNotNull(recipe)
        assertEquals("tosty-francuskie", recipe?.id)
        assertTrue(msg.contains("Tosty francuskie z cynamonem"))
    }

    @Test
    fun `module 1 - ailment stomach pain returns soothing flaxseed recipe`() {
        val (recipe, msg) = CulinaryAiEngine.searchFridgeAndAilments("boli mnie brzuch i mam zgagę", fixtureRecipes)
        assertNotNull(recipe)
        assertEquals("napar-siemie-imbir", recipe?.id)
        assertTrue(msg.contains("Napar z siemienia lnianego"))
    }

    @Test
    fun `module 1 - unknown recipe returns exact strict fallback phrase`() {
        val (recipe, msg) = CulinaryAiEngine.searchFridgeAndAilments("kawior z łososia i trufle z alaski", fixtureRecipes)
        assertNull(recipe)
        val expected = "Niestety, w naszej zweryfikowanej bazie nie mamy obecnie przepisu pasującego do tych wytycznych. Spróbuj dodać inny składnik lub sprawdź inne kategorie."
        assertEquals(expected, msg)
    }

    @Test
    fun `module 3 - portion calculator scales amounts and interpolates instructions`() {
        val recipe = testToastRecipe
        val scaled6 = PortionCalculator.scaleRecipe(recipe, 6) // base is 2, multiplier is 3.0

        assertEquals(6, scaled6.portions)
        assertEquals(recipe.kcalPerPortion * 6, scaled6.totalKcal)

        // Bread was 2 slices for 2 portions -> should be 6 slices for 6 portions
        val bread = scaled6.ingredients.find { it.original.name.contains("Chleb") }
        assertNotNull(bread)
        assertEquals(6.0, bread!!.scaledAmount, 0.01)

        // Eggs were 1 egg for 2 portions -> 3.0 for 6 portions
        val eggs = scaled6.ingredients.find { it.original.name.contains("Jajka") }
        assertNotNull(eggs)
        assertEquals(3.0, eggs!!.scaledAmount, 0.01)

        // Check instruction string interpolation {egg_amount}
        val step1 = scaled6.steps.find { it.stepNumber == 1 }
        assertNotNull(step1)
        assertTrue(step1!!.text.contains("3 szt. jajka") || step1.text.contains("3"))
    }

    @Test
    fun `module 4 - shopping aggregator combines ingredients across multiple meals`() {
        val meals = listOf(
            PlannedMeal(day = DayOfWeekPolish.MON, recipeId = "tosty-francuskie", recipeTitle = "Tosty", portions = 2), // 1 egg
            PlannedMeal(day = DayOfWeekPolish.WED, recipeId = "tosty-francuskie", recipeTitle = "Tosty", portions = 4)  // 2 eggs
        )
        val shoppingList = ShoppingAggregator.aggregate(meals, fixtureRecipes)
        val eggsItem = shoppingList.find { it.name == "Jajka" }
        assertNotNull(eggsItem)
        assertEquals(3.0, eggsItem!!.totalAmount, 0.01)
        assertEquals("Jajka - 3 szt.", eggsItem.formattedDisplay)
    }

    @Test
    fun `module 4 - recipe validator flags missing temperature and absurd salt`() {
        val result = CulinaryAiEngine.validateUserRecipe(
            title = "Moje Ciasto",
            ingredientsText = "1 kg soli\n2 jajka",
            stepsText = "Piecz ciasto w piekarniku aż będzie złote.",
            prepTime = "30",
            ovenTemp = "" // Missing oven temp!
        )
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("temperatury") })
        assertTrue(result.issues.any { it.contains("sól w kilogramach") })
    }

    @Test
    fun `module 5 - substitute advice returns high nutritional alternative`() {
        val butterSub = CulinaryAiEngine.getSubstitute("masło")
        assertEquals("Masło", butterSub.ingredientName)
        assertTrue(butterSub.recommendedSubstitute.contains("Oliwa"))
        assertEquals(7, butterSub.fitPointsDelta)
    }

    @Test
    fun `auth - administrator login requires exact password sonymhc-ex5`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (adminWrong, errWrong) = com.example.data.local.AppRecipeStorage.loginUser(
            context = context,
            emailInput = UserAccount.SUPERADMIN_EMAIL,
            passwordInput = "random_password"
        )
        assertNull(adminWrong)
        assertNotNull(errWrong)
        assertTrue(errWrong!!.contains("Nieprawidłowe hasło"))

        val (adminRight, errRight) = com.example.data.local.AppRecipeStorage.loginUser(
            context = context,
            emailInput = UserAccount.SUPERADMIN_EMAIL,
            passwordInput = "sonymhc-ex5"
        )
        assertNotNull(adminRight)
        assertNull(errRight)
        assertEquals(UserAccount.SUPERADMIN_EMAIL, adminRight!!.email)
        assertTrue(adminRight.isAdmin)
    }

    @Test
    fun `auth - user registration and login verifies email nick and matching password`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testEmail = "testuser@gmail.com"
        val testNick = "Tester Jan"
        val testPass = "mojeBezpieczneHaslo123"

        // Registration with mismatched password fails
        val (failedUser, mismatchErr) = com.example.data.local.AppRecipeStorage.registerUser(
            context, testEmail, testNick, testPass, "inneHaslo123"
        )
        assertNull(failedUser)
        assertNotNull(mismatchErr)

        // Registration with valid data succeeds
        val (registeredUser, regErr) = com.example.data.local.AppRecipeStorage.registerUser(
            context, testEmail, testNick, testPass, testPass
        )
        assertNotNull(registeredUser)
        assertNull(regErr)
        assertEquals(testEmail, registeredUser!!.email)
        assertEquals(testNick, registeredUser.name)
        assertFalse(registeredUser.isAdmin)

        // Login with wrong password fails
        val (loginWrong, errPass) = com.example.data.local.AppRecipeStorage.loginUser(
            context, testEmail, "zleHaslo"
        )
        assertNull(loginWrong)
        assertNotNull(errPass)
        assertTrue(errPass!!.contains("Niepoprawne hasło"))

        // Login with correct password succeeds
        val (loginOk, errOk) = com.example.data.local.AppRecipeStorage.loginUser(
            context, testEmail, testPass
        )
        assertNotNull(loginOk)
        assertNull(errOk)
        assertEquals(testEmail, loginOk!!.email)
    }

    @Test
    fun `main activity launches without crashing`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotNull(scenario)
        }
    }
}
