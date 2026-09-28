package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Zarządza trwałym zapisem przepisów w pamięci aplikacji:
 * 1. SharedPreferences ("fitkuchnia_app_memory")
 * 2. Plik kopii zapasowej w pamięci wewnętrznej ("fitkuchnia_recipes_backup.json")
 * Zapewnia, że żaden dodany przepis nie zniknie po ponownym wczytaniu podglądu,
 * odświeżeniu strony czy restarcie aplikacji.
 */
object AppRecipeStorage {
    private const val TAG = "AppRecipeStorage"
    private const val PREFS_NAME = "fitkuchnia_app_memory"
    private const val KEY_RECIPES_JSON = "saved_recipes_json_v1"
    private const val KEY_DELETED_RECIPES = "deleted_recipe_ids_v1"
    private const val BACKUP_FILE_NAME = "fitkuchnia_recipes_backup.json"

    val DEPRECATED_BOT_RECIPE_IDS = setOf(
        "fit_siemie_lniane_macerat",
        "fit_deser_owocowy_ze_smietana",
        "fit_zupa_krem_dynia_imbir",
        "fit_jajecznica_ze_szczypiorkiem"
    )

    /**
     * Weryfikuje, czy dany przepis jest jednym z 4 wygenerowanych wcześniej przepisów bota.
     * BARDZO WAŻNY WARUNEK: Przepisy autorstwa "super administrator" oraz użytkowników
     * są bezwzględnie chronione i nigdy nie zostaną usunięte.
     */
    fun isDeprecatedBotRecipe(recipe: Recipe): Boolean {
        if (recipe.id in DEPRECATED_BOT_RECIPE_IDS) return true
        val authorLower = (recipe.author ?: "").trim().lowercase()
        val isBotAuthor = authorLower.contains("dietetyk fit") ||
                authorLower.contains("fit kuchnia szef") ||
                authorLower.contains("fitkuchnia chef") ||
                authorLower.contains("fitkuchnia szef")
        if (isBotAuthor) {
            val titleLower = recipe.title.lowercase().trim()
            if (titleLower.contains("deser owocowy ze śmietaną") ||
                titleLower.contains("złoty krem z dyni") ||
                titleLower.contains("jajecznica kremowa na maśle") ||
                titleLower.contains("siemię lniane") ||
                titleLower.contains("siemielniane") ||
                titleLower.contains("siemie lniane")) {
                return true
            }
        }
        return false
    }

    fun getDeletedRecipeIds(context: Context): Set<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getStringSet(KEY_DELETED_RECIPES, emptySet()) ?: emptySet()
            stored + DEPRECATED_BOT_RECIPE_IDS
        } catch (_: Exception) {
            DEPRECATED_BOT_RECIPE_IDS
        }
    }

    fun markRecipeDeleted(context: Context, id: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = (prefs.getStringSet(KEY_DELETED_RECIPES, emptySet()) ?: emptySet()).toMutableSet()
            current.add(id)
            prefs.edit().putStringSet(KEY_DELETED_RECIPES, current).apply()
        } catch (_: Exception) {}
    }

    fun unmarkRecipeDeleted(context: Context, id: String) {
        if (id in DEPRECATED_BOT_RECIPE_IDS) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = (prefs.getStringSet(KEY_DELETED_RECIPES, emptySet()) ?: emptySet()).toMutableSet()
            if (current.remove(id)) {
                prefs.edit().putStringSet(KEY_DELETED_RECIPES, current).apply()
            }
        } catch (_: Exception) {}
    }

    /**
     * Odczytuje zapisane wcześniej przepisy z pamięci aplikacji (SharedPreferences / plik wewnętrzny).
     * Całkowicie wyklucza 4 usunięte przepisy bota i czyści je przy pierwszym uruchomieniu,
     * zachowując nienaruszone wszystkie przepisy administratora i użytkowników.
     */
    fun loadSavedRecipes(context: Context): List<Recipe> {
        val deletedIds = getDeletedRecipeIds(context)

        // 1. Sprawdź pamięć SharedPreferences
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonFromPrefs = prefs.getString(KEY_RECIPES_JSON, null)
            if (!jsonFromPrefs.isNullOrBlank()) {
                val list = parseRecipesJson(jsonFromPrefs)
                if (list.isNotEmpty()) {
                    val filtered = list.filterNot { it.id in deletedIds || isDeprecatedBotRecipe(it) }
                    if (filtered.size != list.size) {
                        saveAllRecipes(context, filtered)
                    }
                    return filtered
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd odczytu z SharedPreferences", e)
        }

        // 2. Kopia zapasowa w pliku wewnętrznym aplikacji
        try {
            val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
            if (backupFile.exists()) {
                val jsonFromFile = backupFile.readText()
                if (jsonFromFile.isNotBlank()) {
                    val list = parseRecipesJson(jsonFromFile)
                    if (list.isNotEmpty()) {
                        val filtered = list.filterNot { it.id in deletedIds || isDeprecatedBotRecipe(it) }
                        if (filtered.size != list.size) {
                            saveAllRecipes(context, filtered)
                        }
                        return filtered
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd odczytu z pliku kopii zapasowej", e)
        }

        return emptyList()
    }

    /**
     * Zapisuje wszystkie przepisy atomowo do SharedPreferences oraz trwałego pliku wewnętrznego.
     */
    fun saveAllRecipes(context: Context, recipes: List<Recipe>) {
        try {
            val cleanRecipes = recipes.filterNot { isDeprecatedBotRecipe(it) }
            val jsonArray = JSONArray()
            cleanRecipes.forEach { recipe ->
                jsonArray.put(recipeToJson(recipe))
            }
            val jsonString = jsonArray.toString()

            // 1. Zapis do SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_RECIPES_JSON, jsonString).apply()

            // 2. Trwały zapis do pliku w context.filesDir
            val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
            backupFile.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu przepisów do pamięci aplikacji", e)
        }
    }

    /**
     * Dodaje lub aktualizuje pojedynczy przepis w pamięci aplikacji.
     */
    fun saveRecipe(context: Context, recipe: Recipe) {
        if (isDeprecatedBotRecipe(recipe)) {
            Log.w(TAG, "Odrzucono próbę zapisu usuniętego przepisu bota: ${recipe.title}")
            return
        }
        unmarkRecipeDeleted(context, recipe.id)
        val current = loadSavedRecipes(context).toMutableList()
        val index = current.indexOfFirst { it.id == recipe.id }
        if (index >= 0) {
            current[index] = recipe
        } else {
            current.add(0, recipe)
        }
        saveAllRecipes(context, current)
    }

    /**
     * Usuwa przepis o wskazanym ID z pamięci aplikacji.
     */
    fun removeRecipe(context: Context, id: String) {
        markRecipeDeleted(context, id)
        val current = loadSavedRecipes(context).filterNot { it.id == id }
        saveAllRecipes(context, current)
    }

    /**
     * Aktualizuje status moderacji w pamięci aplikacji.
     */
    fun updateModerationStatus(context: Context, id: String, status: RecipeModerationStatus) {
        val current = loadSavedRecipes(context).map {
            if (it.id == id) it.copy(moderationStatus = status, isVerified = status == RecipeModerationStatus.APPROVED) else it
        }
        saveAllRecipes(context, current)
    }

    /**
     * Czyści pamięć przepisów w aplikacji.
     */
    fun clearAll(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_RECIPES_JSON).apply()
            val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
            if (backupFile.exists()) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd czyszczenia pamięci", e)
        }
    }

    private fun parseRecipesJson(jsonString: String): List<Recipe> {
        val list = mutableListOf<Recipe>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                recipeFromJson(obj)?.let { list.add(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd parsowania tablicy JSON", e)
        }
        return list
    }

    private fun recipeToJson(recipe: Recipe): JSONObject {
        return JSONObject().apply {
            put("id", recipe.id)
            put("title", recipe.title)
            put("description", recipe.description)
            put("prepTimeMinutes", recipe.prepTimeMinutes)
            put("kcalPerPortion", recipe.kcalPerPortion)
            put("fitPoints", recipe.fitPoints)
            put("fitPointsReason", recipe.fitPointsReason)
            put("proteinG", recipe.proteinG)
            put("fatG", recipe.fatG)
            put("carbsG", recipe.carbsG)
            put("basePortions", recipe.basePortions)

            val catArray = JSONArray()
            recipe.categories.forEach { catArray.put(it.name) }
            put("categories", catArray)

            val ailArray = JSONArray()
            recipe.matchedAilments.forEach { ailArray.put(it) }
            put("matchedAilments", ailArray)

            val ingArray = JSONArray()
            recipe.ingredients.forEach { ing ->
                val ingObj = JSONObject().apply {
                    put("name", ing.name)
                    put("amount", ing.amount)
                    put("unit", ing.unit)
                    put("category", ing.category.name)
                    put("optional", ing.optional)
                }
                ingArray.put(ingObj)
            }
            put("ingredients", ingArray)

            val stepArray = JSONArray()
            recipe.steps.forEach { st ->
                val stObj = JSONObject().apply {
                    put("stepNumber", st.stepNumber)
                    put("instruction", st.instruction)
                    put("timerMinutes", st.timerMinutes ?: JSONObject.NULL)
                    put("tip", st.tip ?: JSONObject.NULL)
                }
                stepArray.put(stObj)
            }
            put("steps", stepArray)

            recipe.beveragePairing?.let { bev ->
                val bevObj = JSONObject().apply {
                    put("title", bev.title)
                    put("description", bev.description)
                    put("whyItMatches", bev.whyItMatches)
                    put("fitPoints", bev.fitPoints)
                }
                put("beveragePairing", bevObj)
            }

            put("isVerified", recipe.isVerified)
            put("author", recipe.author)
            put("authorEmail", recipe.authorEmail)
            put("moderationStatus", recipe.moderationStatus.name)
            put("imagePath", recipe.imagePath ?: JSONObject.NULL)
            put("imageDrawableName", recipe.imageDrawableName ?: JSONObject.NULL)
            put("imageBase64", recipe.imageBase64 ?: JSONObject.NULL)
        }
    }

    private fun recipeFromJson(obj: JSONObject): Recipe? {
        return try {
            val id = obj.getString("id")
            val title = obj.getString("title")
            val description = obj.optString("description", "")
            val prepTimeMinutes = obj.optInt("prepTimeMinutes", 20)
            val kcalPerPortion = obj.optInt("kcalPerPortion", 400)
            val fitPoints = obj.optInt("fitPoints", 80)
            val fitPointsReason = obj.optString("fitPointsReason", "")
            val proteinG = obj.optInt("proteinG", 20)
            val fatG = obj.optInt("fatG", 15)
            val carbsG = obj.optInt("carbsG", 40)
            val basePortions = obj.optInt("basePortions", 2)

            val categories = mutableSetOf<RecipeCategory>()
            if (obj.has("categories")) {
                val arr = obj.getJSONArray("categories")
                for (i in 0 until arr.length()) {
                    val name = arr.getString(i)
                    RecipeCategory.values().find { it.name == name }?.let { categories.add(it) }
                }
            }
            if (categories.isEmpty()) {
                categories.add(RecipeCategory.OBIAD)
            }

            val matchedAilments = mutableListOf<String>()
            if (obj.has("matchedAilments")) {
                val arr = obj.getJSONArray("matchedAilments")
                for (i in 0 until arr.length()) {
                    matchedAilments.add(arr.getString(i))
                }
            }

            val ingredients = mutableListOf<RecipeIngredient>()
            if (obj.has("ingredients")) {
                val arr = obj.optJSONArray("ingredients")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        if (item is JSONObject) {
                            val name = item.optString("name", "").trim()
                            if (name.isNotEmpty()) {
                                val catName = item.optString("category", StoreCategory.WARZYWA_OWOCE.name)
                                val cat = StoreCategory.values().find { it.name.equals(catName, ignoreCase = true) } ?: StoreCategory.WARZYWA_OWOCE
                                ingredients.add(
                                    RecipeIngredient(
                                        name = name,
                                        amount = item.optDouble("amount", 1.0),
                                        unit = item.optString("unit", "szt."),
                                        category = cat,
                                        optional = item.optBoolean("optional", false)
                                    )
                                )
                            }
                        } else if (item is String && item.isNotBlank()) {
                            ingredients.add(RecipeIngredient(name = item, amount = 1.0, unit = "szt.", category = StoreCategory.WARZYWA_OWOCE))
                        }
                    }
                }
            }

            val steps = mutableListOf<RecipeStep>()
            if (obj.has("steps")) {
                val arr = obj.optJSONArray("steps")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
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
                }
            }

            val beveragePairing = if (obj.has("beveragePairing") && !obj.isNull("beveragePairing")) {
                val bev = obj.getJSONObject("beveragePairing")
                BeveragePairing(
                    title = bev.optString("title", ""),
                    description = bev.optString("description", ""),
                    whyItMatches = bev.optString("whyItMatches", ""),
                    fitPoints = bev.optInt("fitPoints", 90)
                )
            } else null

            val modStatus = try {
                RecipeModerationStatus.valueOf(obj.optString("moderationStatus", RecipeModerationStatus.APPROVED.name))
            } catch (_: Exception) {
                RecipeModerationStatus.APPROVED
            }

            Recipe(
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
                ingredients = ingredients,
                steps = steps,
                beveragePairing = beveragePairing,
                matchedAilments = matchedAilments,
                isVerified = obj.optBoolean("isVerified", modStatus == RecipeModerationStatus.APPROVED),
                author = obj.optString("author", "FitKuchnia Chef"),
                authorEmail = obj.optString("authorEmail", "zababiznes2000@gmail.com"),
                moderationStatus = modStatus,
                imagePath = if (obj.has("imagePath") && !obj.isNull("imagePath")) obj.getString("imagePath") else null,
                imageDrawableName = if (obj.has("imageDrawableName") && !obj.isNull("imageDrawableName")) obj.getString("imageDrawableName") else null,
                imageBase64 = if (obj.has("imageBase64") && !obj.isNull("imageBase64")) obj.getString("imageBase64") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Błąd parsowania obiektu przepisu", e)
            null
        }
    }

    private const val KEY_USER_EMAIL = "logged_user_email"
    private const val KEY_USER_NAME = "logged_user_name"
    private const val KEY_USER_IS_ADMIN = "logged_user_is_admin"
    private const val KEY_USERS_DB_JSON = "registered_users_db_v4"
    private const val KEY_AUTH_VERSION = "auth_system_version_v4"
    private const val CURRENT_AUTH_VERSION = 4

    private fun ensureAuthMigration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVer = prefs.getInt(KEY_AUTH_VERSION, 0)
        if (currentVer < CURRENT_AUTH_VERSION) {
            // Wymuś utworzenie kont na nowo, wyczyść stare niezabezpieczone sesje i stare bazy
            prefs.edit()
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_IS_ADMIN)
                .remove("registered_users_db")
                .remove(KEY_USERS_DB_JSON)
                .remove("superadmin_password_hash")
                .putInt(KEY_AUTH_VERSION, CURRENT_AUTH_VERSION)
                .apply()
        }
    }

    fun isValidEmail(email: String): Boolean {
        val clean = email.trim()
        if (clean.isBlank() || clean.length < 5 || !clean.contains("@") || !clean.contains(".")) {
            return false
        }
        return try {
            android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches()
        } catch (_: Throwable) {
            clean.count { it == '@' } == 1 && clean.substringAfter('@').contains('.')
        }
    }

    private fun hashPassword(password: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val salted = "fitkuchnia_salt_secure_2026_${password.trim()}"
            val bytes = md.digest(salted.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            password.trim().hashCode().toString()
        }
    }

    fun isEmailRegistered(context: Context, email: String): Boolean {
        ensureAuthMigration(context)
        val clean = email.trim().lowercase()
        if (clean == UserAccount.SUPERADMIN_EMAIL.lowercase()) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_USERS_DB_JSON, "{}") ?: "{}"
        return try {
            val json = org.json.JSONObject(jsonStr)
            json.has(clean)
        } catch (e: Exception) {
            false
        }
    }

    fun getRegisteredUserName(context: Context, email: String): String? {
        ensureAuthMigration(context)
        val clean = email.trim().lowercase()
        if (clean == UserAccount.SUPERADMIN_EMAIL.lowercase()) return "Super Administrator"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_USERS_DB_JSON, "{}") ?: "{}"
        return try {
            val json = org.json.JSONObject(jsonStr)
            if (json.has(clean)) {
                json.getJSONObject(clean).optString("name", null)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Bezpieczne logowanie użytkownika.
     * Dla Administratora (zababiznes2000@gmail.com) wymagane hasło: sonymhc-ex5
     * Dla zwykłych użytkowników sprawdzane jest hasło podane podczas rejestracji konta.
     */
    fun loginUser(
        context: Context,
        emailInput: String,
        passwordInput: String
    ): Pair<UserAccount?, String?> {
        ensureAuthMigration(context)
        val cleanEmail = emailInput.trim().lowercase()
        val password = passwordInput.trim()

        if (!isValidEmail(cleanEmail)) {
            return Pair(null, "Wprowadź prawidłowy adres e-mail (np. twoj.email@gmail.com).")
        }
        if (password.isBlank()) {
            return Pair(null, "Wprowadź hasło do konta.")
        }

        // Sprawdzenie, czy to konto Super Administratora
        if (cleanEmail == UserAccount.SUPERADMIN_EMAIL.lowercase()) {
            if (password != UserAccount.ADMIN_PASSWORD_EXACT) {
                return Pair(null, "Nieprawidłowe hasło Administratora! Spróbuj ponownie.")
            }
            val adminAccount = UserAccount(
                email = UserAccount.SUPERADMIN_EMAIL,
                name = "Super Administrator",
                isAdmin = true
            )
            saveLoggedInUser(context, adminAccount)
            return Pair(adminAccount, null)
        }

        // Zwykły zarejestrowany użytkownik
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_USERS_DB_JSON, "{}") ?: "{}"
        val usersJson = try {
            org.json.JSONObject(jsonStr)
        } catch (e: Exception) {
            org.json.JSONObject()
        }

        if (!usersJson.has(cleanEmail)) {
            return Pair(null, "Nie znaleziono konta dla adresu $cleanEmail. Przejdź do zakładki 'Utwórz konto', aby się zarejestrować.")
        }

        val userObj = usersJson.getJSONObject(cleanEmail)
        val storedHash = userObj.optString("passwordHash", "")
        val enteredHash = hashPassword(password)

        if (storedHash != enteredHash) {
            return Pair(null, "Niepoprawne hasło do konta $cleanEmail! Spróbuj ponownie.")
        }

        val savedName = userObj.optString("name", "").ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }

        val account = UserAccount(
            email = cleanEmail,
            name = savedName,
            isAdmin = false
        )
        saveLoggedInUser(context, account)
        return Pair(account, null)
    }

    /**
     * Bezpieczna rejestracja nowego konta.
     * Wymaga unikalnego, poprawnego e-maila, nicku (min. 3 znaki) oraz potwierdzonego hasła (min. 6 znaków).
     */
    fun registerUser(
        context: Context,
        emailInput: String,
        nameInput: String,
        passwordInput: String,
        passwordConfirmInput: String
    ): Pair<UserAccount?, String?> {
        ensureAuthMigration(context)
        val cleanEmail = emailInput.trim().lowercase()
        val cleanName = nameInput.trim()
        val cleanPassword = passwordInput.trim()
        val cleanConfirm = passwordConfirmInput.trim()

        if (!isValidEmail(cleanEmail)) {
            return Pair(null, "Wprowadź prawidłowy adres e-mail (np. twoj.email@gmail.com).")
        }
        if (cleanName.length < 3) {
            return Pair(null, "Twój Nick musi mieć co najmniej 3 znaki.")
        }
        if (cleanPassword.length < 6) {
            return Pair(null, "Hasło musi mieć co najmniej 6 znaków.")
        }
        if (cleanPassword != cleanConfirm) {
            return Pair(null, "Podane hasła nie są identyczne! Wpisz dokładnie to samo hasło w obu polach.")
        }

        if (cleanEmail == UserAccount.SUPERADMIN_EMAIL.lowercase()) {
            return Pair(null, "Adres $cleanEmail jest zarezerwowany dla konta Administratora. Użyj zakładki Logowanie.")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_USERS_DB_JSON, "{}") ?: "{}"
        val usersJson = try {
            org.json.JSONObject(jsonStr)
        } catch (e: Exception) {
            org.json.JSONObject()
        }

        if (usersJson.has(cleanEmail)) {
            return Pair(null, "Konto z adresem $cleanEmail już istnieje! Przejdź do zakładki Logowanie, aby się zalogować.")
        }

        val enteredHash = hashPassword(cleanPassword)
        val newUserObj = org.json.JSONObject().apply {
            put("name", cleanName)
            put("passwordHash", enteredHash)
            put("created_at", System.currentTimeMillis())
        }
        usersJson.put(cleanEmail, newUserObj)
        prefs.edit().putString(KEY_USERS_DB_JSON, usersJson.toString()).apply()

        val account = UserAccount(
            email = cleanEmail,
            name = cleanName,
            isAdmin = false
        )
        saveLoggedInUser(context, account)
        return Pair(account, null)
    }

    /**
     * Logowanie pomocnicze zachowujące kompatybilność.
     */
    fun authenticateUser(
        context: Context,
        email: String,
        passwordInput: String,
        nameInput: String = ""
    ): Pair<UserAccount?, String?> {
        return loginUser(context, email, passwordInput)
    }

    fun saveLoggedInUser(context: Context, user: UserAccount?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (user == null) {
            prefs.edit()
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_IS_ADMIN)
                .apply()
            com.example.util.AppSessionManager.clearSession(context)
        } else {
            val isAdmin = UserAccount.isAdministrator(user.email, user.name)
            prefs.edit()
                .putString(KEY_USER_EMAIL, user.email.trim().lowercase())
                .putString(KEY_USER_NAME, user.name)
                .putBoolean(KEY_USER_IS_ADMIN, isAdmin)
                .apply()
            com.example.util.AppSessionManager.startNewSession(context)
        }
    }

    fun loadLoggedInUser(context: Context): UserAccount? {
        ensureAuthMigration(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null

        val name = prefs.getString(KEY_USER_NAME, "Użytkownik") ?: "Użytkownik"
        val isAdmin = UserAccount.isAdministrator(email, name)
        return UserAccount(
            email = email.trim().lowercase(),
            name = name,
            isAdmin = isAdmin
        )
    }
}
