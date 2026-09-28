package com.example.data

/**
 * Baza danych domyślnych przepisów w kodzie aplikacji.
 *
 * Wszystkie wygenerowane wcześniej domyślne przepisy bota:
 * - "Deser owocowy ze śmietaną 18% i owocami leśnymi"
 * - "Złoty krem z dyni z imbirem i czosnkiem na odporność"
 * - "Jajecznica kremowa na maśle ze szczypiorkiem i pomidorami"
 * - "Siemię lniane (macerat leczniczy na ból brzucha i gardła)"
 * zostały TRWALE usunięte z kodu źródłowego.
 *
 * Lista domyślna jest teraz pusta. Wszystkie przepisy w aplikacji pochodzą wyłącznie
 * z bazy utworzonej przez administratora i użytkowników.
 */
object RecipeDatabase {

    val recipes: List<Recipe> = emptyList()

    fun findRecipeById(id: String, dynamicList: List<Recipe> = recipes): Recipe? {
        return dynamicList.find { it.id == id }
    }

    fun getDailyDish(dynamicList: List<Recipe> = recipes): Recipe? {
        return dynamicList.maxByOrNull { it.fitPoints }
    }
}
