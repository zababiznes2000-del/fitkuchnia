package com.example.ai

import android.util.Log
import com.example.data.*
import com.example.data.model.MealHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SubstituteAdvice(
    val ingredientName: String,
    val recommendedSubstitute: String,
    val flavorImpact: String,
    val textureImpact: String,
    val fitPointsDelta: Int,
    val explanation: String
)

data class RecipeValidationResult(
    val isValid: Boolean,
    val feedbackMessage: String,
    val issues: List<String>,
    val awardedBadge: String? = null,
    val estimatedKcal: Int? = null,
    val estimatedFitPoints: Int? = null
)

data class EstimatedNutrition(
    val kcalPerPortion: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    val fitPoints: Int,
    val fitPointsReason: String,
    val isFromGemini: Boolean = false
)

object CulinaryAiEngine {

    private const val TAG = "CulinaryAiEngine"

    const val SYSTEM_INSTRUCTION = """
Jesteś głównym silnikiem AI dla nowoczesnej, precyzyjnej aplikacji kulinarnej FitKuchnia. Interfejs aplikacji jest zielono-czarny, co symbolizuje świeżość, zdrowie, ekologię i nowoczesną technologię. Twój ton jest profesjonalny, precyzyjny, nastawiony na zdrowie oraz idealny smak.
BEZWZGLĘDNA ZASADA BEZPIECZEŃSTWA:
- Wolno Ci polecać i proponować WYŁĄCZNIE przepisy znajdujące się w oficjalnej bazie danych aplikacji dostarczonej w kontekście.
- NIGDY nie proponuj ani nie zmyślaj przepisów spoza bazy danych aplikacji!
Twoje działanie opiera się na 5 modułach:
MODUŁ 1: Rekomendacje z Lodówki, Zero Waste i na Dolegliwości Zdrowotne (wyłącznie z bazy)
MODUŁ 2: Rygorystyczny Analityk Odżywczy (precyzyjne kalorie i makroskładniki) i "Fit Punkty" (1-100)
MODUŁ 3: Niewidzialny Kalkulator Porcji
MODUŁ 4: Bezlitosny Korektor Przepisów
MODUŁ 5: Ratunek Kulinarny (Zamienniki Składników)
"""

    private val commonSubstitutes = mapOf(
        "masło" to SubstituteAdvice(
            ingredientName = "Masło",
            recommendedSubstitute = "Oliwa z oliwek z pierwszego tłoczenia lub olej z awokado (w proporcji 3:4)",
            flavorImpact = "Lekko owocowa, ziołowa nuta, doskonała do dań wytrawnych.",
            textureImpact = "Wypieki będą nieco bardziej wilgotne, a sosy lżejsze i mniej zwarte.",
            fitPointsDelta = 7,
            explanation = "Zamiana nasyconych kwasów tłuszczowych na jednonienasycone kwasy tłuszczowe omega-9 i polifenole obniża ryzyko stanów zapalnych."
        ),
        "jajka" to SubstituteAdvice(
            ingredientName = "Jajka",
            recommendedSubstitute = "Siemię lniane mielone (1 łyżka + 3 łyżki ciepłej wody na 1 jajko) lub 1/2 dojrzałego banana",
            flavorImpact = "Delikatnie orzechowy posmak (len) lub owocowy (banan w daniach na słodko).",
            textureImpact = "Świetna wilgotność i spoiwo, choć ciasto rośnie nieco mniej puszyste.",
            fitPointsDelta = 5,
            explanation = "Zastępuje cholesterol cennymi kwasami alfa-linolenowymi (ALA) oraz błonnikiem rozpuszczalnym."
        ),
        "śmietana" to SubstituteAdvice(
            ingredientName = "Śmietana 18%",
            recommendedSubstitute = "Gęsty jogurt grecki 0% lub 2% z kroplą soku z cytryny",
            flavorImpact = "Przyjemna, świeża kwasowość z kremową głębią.",
            textureImpact = "Identyczna aksamitność przy redukcji tłuszczu o 70%.",
            fitPointsDelta = 12,
            explanation = "Aż 3x więcej białka serwatkowego i kazeiny, przy drastycznym obniżeniu kalorii z nasyconego tłuszczu."
        ),
        "cukier" to SubstituteAdvice(
            ingredientName = "Cukier biały",
            recommendedSubstitute = "Erytrytol lub ksylitol w proporcji 1:1.2",
            flavorImpact = "Czysta słodycz bez posmaku syntetycznych słodzików, lekki efekt chłodzący.",
            textureImpact = "Brak karmelizacji, idealny w naleśnikach i owsiankach.",
            fitPointsDelta = 15,
            explanation = "Zerowy indeks glikemiczny (IG = 0) nie wyrzuca insuliny do krwi i chroni przed hipoglikemią reaktywną."
        ),
        "sos sojowy" to SubstituteAdvice(
            ingredientName = "Sos sojowy",
            recommendedSubstitute = "Sos tamari bezglutenowy lub odrobina redukcji bulionu grzybowego z solą",
            flavorImpact = "Głębokie umami z mniejszą ostrością solną.",
            textureImpact = "Płynna, klarowna glazura.",
            fitPointsDelta = 4,
            explanation = "Redukcja sodu i eliminacja przetworzonego glutenu u osób z wrażliwością jelitową."
        ),
        "mąka pszenna" to SubstituteAdvice(
            ingredientName = "Mąka pszenna",
            recommendedSubstitute = "Mąka orkiszowa jasna lub zmielone płatki owsiane",
            flavorImpact = "Głębszy, lekko orzechowy i sycący aromat.",
            textureImpact = "Nieco bardziej gęste ciasto z wyższą zawartością błonnika.",
            fitPointsDelta = 8,
            explanation = "Dostarcza beta-glukanów stabilizujących poziom glukozy po posiłku."
        )
    )

    fun getSubstitute(ingredientName: String): SubstituteAdvice {
        val lower = ingredientName.lowercase().trim()
        for ((key, advice) in commonSubstitutes) {
            if (lower.contains(key) || key.contains(lower)) {
                return advice
            }
        }
        return SubstituteAdvice(
            ingredientName = ingredientName,
            recommendedSubstitute = "Oliwa extra virgin, bulion warzywny lub jogurt naturalny (w zależności od typu dania)",
            flavorImpact = "Neutralny, zachowujący naturalne aromaty dania.",
            textureImpact = "Płynna konsystencja ułatwiająca połączenie pozostałych składników.",
            fitPointsDelta = 3,
            explanation = "Substytucja oparta na bazie nieprzetworzonych surowców podnosi profil odżywczy posiłku."
        )
    }

    // MODUŁ 1: Rekomendacje z Lodówki, Zero Waste i na Dolegliwości (NIGDY POZA BAZĄ)
    suspend fun searchFridgeAndAilments(
        query: String,
        database: List<Recipe>,
        apiKey: String? = null
    ): Pair<Recipe?, String> = withContext(Dispatchers.IO) {
        val cleaned = query.lowercase().trim()
        if (cleaned.isEmpty()) {
            return@withContext null to "Wpisz składniki z lodówki (np. jajka, chleb, ryż) lub dolegliwość (np. boli mnie brzuch, ból gardła)."
        }

        // Baza przepisów użytkownika (z wykluczeniem wycofanych przepisów bota)
        val effectiveDb = database.filterNot { com.example.data.local.AppRecipeStorage.isDeprecatedBotRecipe(it) }

        if (effectiveDb.isEmpty()) {
            return@withContext null to "Twoja baza przepisów jest obecnie pusta. Dodaj przepisy w zakładce Profil lub Odkrywaj, aby asystent mógł z nich rekomendować."
        }

        // 1. Jeśli dostępny jest klucz Gemini API, wykonaj dogłębną analizę medyczno-dietetyczną online
        if (!apiKey.isNullOrBlank()) {
            try {
                val onlineResult = callGeminiFridgeSearch(query, effectiveDb, apiKey)
                if (onlineResult != null) {
                    return@withContext onlineResult
                }
            } catch (_: Exception) {
                // Przy braku sieci lub błędzie API, przejdź do zaawansowanego algorytmu lokalnego
            }
        }

        // 2. Precyzyjny silnik lokalny oparty na farmakopei, dietetyce i bazie aplikacji
        return@withContext smartLocalFridgeSearch(cleaned, effectiveDb)
    }

    private fun callGeminiFridgeSearch(
        query: String,
        database: List<Recipe>,
        apiKey: String
    ): Pair<Recipe?, String>? {
        val model = "gemini-2.5-flash"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 12000

        val dbRecipesList = database.joinToString("\n") { r ->
            "- ID: \"${r.id}\", TYTUŁ: \"${r.title}\", SKŁADNIKI: [${r.ingredients.joinToString { "${it.name} ${it.amount} ${it.unit}" }}], OPIS: \"${r.description.take(120)}\", KCAL: ${r.kcalPerPortion}, FIT: ${r.fitPoints}"
        }

        val prompt = """
Jesteś profesjonalnym dietetykiem klinicznym i kulinarnym asystentem aplikacji FitKuchnia.
Użytkownik wprowadził zapytanie w module lodówki / ratunku zdrowotnego:
"$query"

DOSTĘPNA ZWERYFIKOWANA BAZA PRZEPISÓW W APLIKACJI (ŁĄCZNIE: ${database.size} pozycji):
$dbRecipesList

BEZWZGLĘDNA I NAJWAŻNIEJSZA ZASADA:
1. Wolno Ci wybrać i polecić WYŁĄCZNIE przepis znajdujący się na powyższej liście w bazie aplikacji. NIGDY nie proponuj, nie sugeruj ani nie zmyślaj żadnego dania spoza tej bazy!
2. Jeśli zapytanie dotyczy dolegliwości zdrowotnych (np. ból brzucha, ból żołądka, zgaga, refluks, niestrawność, zaparcia ORAZ ból gardła, chrypka, podrażnienie krtani) lub słów typu siemię / siemielniane:
   - Dokonaj analizy farmakologiczno-dietetycznej składników z bazy.
   - Jeśli w bazie znajduje się przepis łagodzący podrażnienia żołądka lub gardła, wybierz ten przepis z bazy!
   - Na przeziębienie / odporność wybierz dania z imbirem, czosnkiem, cytryną, buliony lub zupy z bazy.
3. Jeśli zapytanie dotyczy deserów, owoców lub dań na słodko ze śmietaną, wybierz odpowiedni deser z bazy.
4. Jeśli zapytanie dotyczy składników z lodówki: dopasuj przepis z bazy, który pozwala je maksymalnie zużyć.
5. Jeśli żaden przepis z bazy w żaden sposób nie pasuje, ustaw "matched_id" na null.

Zwróć TYLKO czysty obiekt JSON bez znaczników markdown:
{
  "matched_id": "dokładne ID wybranego przepisu z bazy lub null",
  "explanation": "rzetelne, przekonujące uzasadnienie po polsku, dlaczego ten przepis z bazy aplikacji jest idealny na te wytyczne"
}
""".trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val parts = candidate.getJSONObject("content").getJSONArray("parts")
                if (parts.length() > 0) {
                    val rawText = parts.getJSONObject(0).getString("text")
                    val cleanJson = rawText.substringAfter("{").substringBeforeLast("}")
                    val parsed = JSONObject("{$cleanJson}")
                    val matchedId = parsed.optString("matched_id", "").trim()
                    val explanation = parsed.optString("explanation", "").trim()

                    if (matchedId.isNotEmpty() && matchedId != "null") {
                        val found = database.find { it.id == matchedId || it.title.equals(matchedId, ignoreCase = true) }
                        if (found != null) {
                            return found to explanation
                        }
                    }
                }
            }
        }
        return null
    }

    private fun smartLocalFridgeSearch(query: String, database: List<Recipe>): Pair<Recipe?, String> {
        val q = query.lowercase().trim()
        val effectiveDb = database.filterNot { com.example.data.local.AppRecipeStorage.isDeprecatedBotRecipe(it) }

        // 1. Wykrywanie dolegliwości układu pokarmowego i gardła oraz zapytań o siemię
        val isStomachIssue = q.contains("brzuch") || q.contains("żołądek") || q.contains("zoladek") ||
                q.contains("zgag") || q.contains("refluks") || q.contains("niestrawno") ||
                q.contains("jelit") || q.contains("trawien") || q.contains("mdłośc") || q.contains("nudnośc") ||
                q.contains("zaparci") || q.contains("wzdęci") || q.contains("wrzod") || q.contains("boli")

        val isThroatIssue = q.contains("gardł") || q.contains("gardel") || q.contains("chrypk") ||
                q.contains("krtań") || q.contains("krtan") || q.contains("suchość w gardle") ||
                q.contains("pieczenie w gardle") || q.contains("przełyk") || q.contains("drapanie")

        val isFlaxDirect = q.contains("siemi") || q.contains("lnian") || q.contains("len") ||
                q.contains("siemielniane") || q.contains("siemielnian")

        // Sprawdzenie siemienia lnianego (pierwsza linia obrony na ból brzucha i ból gardła z bazy użytkownika)
        if (isStomachIssue || isThroatIssue || isFlaxDirect) {
            val flaxRecipe = effectiveDb.find { r ->
                val text = (r.title + " " + r.ingredients.joinToString(" ") { it.name } + " " + r.description).lowercase()
                text.contains("siemię") || text.contains("siemie") || text.contains("lnian") || text.contains("len") || text.contains("macerat")
            }

            if (flaxRecipe != null) {
                val reasonDetail = if (isStomachIssue && isThroatIssue) {
                    "na ból brzucha i ból gardła. Związki śluzowe siemienia lnianego powlekają podrażnioną błonę śluzową żołądka oraz nawilżają i osłaniają śluzówkę gardła."
                } else if (isStomachIssue) {
                    "na ból brzucha, nadkwasotę i podrażnienia żołądka. Maceraty śluzowe lnu powlekają ściany przewodu pokarmowego, tworząc tarczę ochronną przed kwasem solnym i redukując stan zapalny."
                } else if (isThroatIssue) {
                    "na ból gardła, chrypkę i podrażnienia krtani. Śluz z siemienia lnianego głęboko nawilża, regeneruje i koi podrażnione tkanki gardła."
                } else {
                    "jako sprawdzony, medyczny superfood powlekający i regenerujący przewód pokarmowy oraz błony śluzowe."
                }
                val explanation = "Polecamy z Twojej bazy przepis: **${flaxRecipe.title}** ([CZAS: **${flaxRecipe.prepTimeMinutes} min** | KCAL: **${flaxRecipe.kcalPerPortion}** | FIT: **${flaxRecipe.fitPoints}/100**]). To sprawdzony, naturalny ratunek $reasonDetail"
                return flaxRecipe to explanation
            }
        }

        // Sprawdzenie deserów owocowych ze śmietaną z bazy użytkownika
        val isDessertFruit = q.contains("deser") || q.contains("owoc") || (q.contains("śmietan") && q.contains("owoc"))
        if (isDessertFruit) {
            val dessertRecipe = effectiveDb.find { r ->
                r.title.contains("deser", ignoreCase = true) || r.title.contains("owoc", ignoreCase = true) || r.categories.contains(RecipeCategory.NA_SLODKO)
            }
            if (dessertRecipe != null) {
                val explanation = "Polecamy z Twojej bazy przepis: **${dessertRecipe.title}** ([CZAS: **${dessertRecipe.prepTimeMinutes} min** | KCAL: **${dessertRecipe.kcalPerPortion}** | FIT: **${dessertRecipe.fitPoints}/100**]). Danie idealnie odpowiada profilowi słodkiego posiłku owocowego."
                return dessertRecipe to explanation
            }
        }

        // Pozostałe dania łagodzące żołądek (kleiki, imbir, mięta, rumianek, lekkostrawne)
        if (isStomachIssue) {
            val soothingRecipe = effectiveDb.find { r ->
                val text = (r.title + " " + r.ingredients.joinToString(" ") { it.name } + " " + r.description).lowercase()
                text.contains("imbir") || text.contains("rumian") || text.contains("mięt") || text.contains("kleik") || text.contains("owsian") || text.contains("ryż") || text.contains("zupa")
            }
            if (soothingRecipe != null) {
                return soothingRecipe to "Na ból brzucha polecamy z Twojej bazy **${soothingRecipe.title}** ([CZAS: **${soothingRecipe.prepTimeMinutes} min** | KCAL: **${soothingRecipe.kcalPerPortion}** | FIT: **${soothingRecipe.fitPoints}/100**]). Składniki działają lekko, kojąco i przeciwzapalnie na żołądek."
            }
        }

        // Pozostałe dania na gardło (miód, cytryna, imbir, napary)
        if (isThroatIssue) {
            val throatRecipe = effectiveDb.find { r ->
                val text = (r.title + " " + r.ingredients.joinToString(" ") { it.name } + " " + r.description).lowercase()
                text.contains("miód") || text.contains("miod") || text.contains("cytryn") || text.contains("imbir") || text.contains("herbata") || text.contains("napar")
            }
            if (throatRecipe != null) {
                return throatRecipe to "Na ból gardła polecamy z Twojej bazy **${throatRecipe.title}** ([CZAS: **${throatRecipe.prepTimeMinutes} min** | KCAL: **${throatRecipe.kcalPerPortion}** | FIT: **${throatRecipe.fitPoints}/100**]). Działa rozgrzewająco, łagodzi drapanie w gardle i wspomaga regenerację śluzówki."
            }
        }

        // Przeziębienie / odporność
        val isColdIssue = q.contains("przeziębi") || q.contains("przeziebi") || q.contains("gryp") ||
                q.contains("odporno") || q.contains("gorączk") || q.contains("goraczk") ||
                q.contains("chłód") || q.contains("katar") || q.contains("kaszel") || q.contains("osłabien")

        if (isColdIssue) {
            val coldRecipe = effectiveDb.find { r ->
                val text = (r.title + " " + r.ingredients.joinToString(" ") { it.name } + " " + r.description).lowercase()
                text.contains("imbir") || text.contains("czosnek") || text.contains("cebul") || text.contains("dynia") || text.contains("rosół") || text.contains("zupa")
            }
            if (coldRecipe != null) {
                return coldRecipe to "Na przeziębienie i wzmocnienie odporności polecamy z Twojej bazy **${coldRecipe.title}** ([CZAS: **${coldRecipe.prepTimeMinutes} min** | KCAL: **${coldRecipe.kcalPerPortion}** | FIT: **${coldRecipe.fitPoints}/100**]). Działa rozgrzewająco i stymuluje układ immunologiczny."
            }
        }

        // 2. Dopasowanie składników z lodówki (Zero Waste)
        val stopWords = setOf("mam", "i", "oraz", "a", "w", "z", "ze", "na", "do", "zostało", "zostalo", "lodówce", "lodowce", "trochę", "troche", "dużo", "duzo", "posiłek", "danie", "obiad", "kolacja", "śniadanie")
        val tokens = q.split(Regex("[,\\s+]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && !stopWords.contains(it) }

        var bestRecipe: Recipe? = null
        var bestScore = 0

        for (recipe in effectiveDb) {
            var score = 0
            val rText = (recipe.title + " " + recipe.ingredients.joinToString(" ") { it.name } + " " + recipe.description).lowercase()
            for (token in tokens) {
                if (rText.contains(token)) {
                    score += 2
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestRecipe = recipe
            }
        }

        if (bestRecipe != null && bestScore >= 2) {
            return bestRecipe to "Na podstawie Twoich składników polecamy z Twojej bazy: **${bestRecipe.title}** ([CZAS: **${bestRecipe.prepTimeMinutes} min** | KCAL: **${bestRecipe.kcalPerPortion}** | FIT: **${bestRecipe.fitPoints}/100**]). Wykorzystasz swoje składniki bez marnowania żywności!"
        }

        // Jeśli żaden przepis z bazy nie pasuje, informujemy o tym zgodnie z zasadą "żadnych dań spoza bazy"
        return null to "W Twojej zweryfikowanej bazie FitKuchnia (${effectiveDb.size} przepisów) żaden przepis nie pasuje bezpośrednio do zapytania '$query'. Zgodnie z zasadami aplikacji nie rekomendujemy dań spoza bazy. Dodaj nowy przepis na tę okazję w zakładce Profil!"
    }

    // MODUŁ 4: Bezlitosny Korektor Przepisów
    fun validateUserRecipe(
        title: String,
        ingredientsText: String,
        stepsText: String,
        prepTime: String,
        ovenTemp: String
    ): RecipeValidationResult {
        val issues = mutableListOf<String>()

        if (title.isBlank() || title.length < 5) {
            issues.add("Zbyt krótka lub brakująca nazwa potrawy (minimum 5 znaków).")
        }

        val lowerSteps = stepsText.lowercase()
        val lowerIngs = ingredientsText.lowercase()

        // Missing time or temperature checks
        val mentionsBaking = lowerSteps.contains("piecz") || lowerSteps.contains("piekarnik") || lowerSteps.contains("pieczenia")
        if (mentionsBaking) {
            val hasTemp = ovenTemp.isNotBlank() || lowerSteps.contains("°c") || lowerSteps.contains("stopni") || lowerSteps.contains("temperat")
            if (!hasTemp) {
                issues.add("Brak temperatury pieczenia! Określ temperaturę (np. 180°C).")
            }
            val hasTime = prepTime.isNotBlank() || Regex("\\d+\\s*(min|godz)").containsMatchIn(lowerSteps)
            if (!hasTime) {
                issues.add("Brak sprecyzowanego czasu pieczenia w minutach.")
            }
        }

        // Absurd proportions check
        val saltMatch = Regex("(\\d+)\\s*(kg|kilo|litr)").find(lowerIngs)
        if (saltMatch != null && (lowerIngs.contains("sol") || lowerIngs.contains("sól"))) {
            issues.add("Wykryto absurdalną proporcję: sól w kilogramach/litrach! Popraw ilość.")
        }

        val waterLitersMatch = Regex("(\\d+)\\s*(l|litr)").find(lowerIngs)
        if (waterLitersMatch != null) {
            val count = waterLitersMatch.groupValues[1].toIntOrNull() ?: 0
            if (count >= 8) {
                issues.add("Wykryto absurdalną ilość płynu ($count litrów) dla posiłku domowego.")
            }
        }

        // Check ingredients list
        val ingLines = ingredientsText.lines().filter { it.isNotBlank() }
        if (ingLines.size < 2) {
            issues.add("Przepis musi zawierać przynajmniej 2 składniki z gramaturami.")
        }

        // Missing tools check
        if (lowerSteps.contains("zblenduj") || lowerSteps.contains("blenduj") || lowerSteps.contains("zmiksuj")) {
            if (!lowerSteps.contains("blender") && !lowerSteps.contains("mikser")) {
                issues.add("Wskazano blendowanie, ale brak wzmianki o narzędziu kuchennym (np. blender ręczny lub kielichowy).")
            }
        }

        if (issues.isNotEmpty()) {
            val msg = buildString {
                append("Korektor AI odrzucił przepis z powodu nieprecyzyjnych danych:\n")
                issues.forEach { append("• $it\n") }
                append("Popraw błędy, aby przepis mógł zostać opublikowany.")
            }
            return RecipeValidationResult(
                isValid = false,
                feedbackMessage = msg,
                issues = issues
            )
        }

        val parsedIngs = ingLines.mapNotNull { line ->
            val match = Regex("([\\d,.]+)\\s*([a-zA-Ząćęłńóśźż]+)?\\s*(.*)").find(line)
            if (match != null) {
                val amount = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
                val unit = match.groupValues[2].ifEmpty { "szt." }
                val name = match.groupValues[3].ifEmpty { line }
                RecipeIngredient(name, amount, unit, StoreCategory.WARZYWA_OWOCE)
            } else {
                RecipeIngredient(line, 1.0, "szt.", StoreCategory.WARZYWA_OWOCE)
            }
        }

        val portions = 2
        val est = calculateNutritionOffline(title, parsedIngs, portions)
        val kcal = est.kcalPerPortion
        val fitScore = est.fitPoints

        val successMsg = buildString {
            append("Przepis przeszedł bezlitosną weryfikację AI pozytywnie!\n")
            append("• Szacowane kalorie: ~${kcal} kcal / porcję\n")
            append("• Fit Punkty: ${fitScore}/100 (Wysoka wartość biologiczna)\n")
            append("• Brak luk w procesie obróbki termicznej.")
        }

        return RecipeValidationResult(
            isValid = true,
            feedbackMessage = successMsg,
            issues = emptyList(),
            awardedBadge = if (fitScore >= 95) "Mistrz Fit Punktów (100)" else "Ekspert Zero Waste",
            estimatedKcal = kcal,
            estimatedFitPoints = fitScore
        )
    }

    // Assistant response generator
    suspend fun queryAssistant(
        userMessage: String,
        apiKey: String?,
        database: List<Recipe>
    ): String = withContext(Dispatchers.IO) {
        if (!apiKey.isNullOrBlank()) {
            try {
                return@withContext callGeminiRestApi(userMessage, apiKey, database)
            } catch (_: Exception) {
                // Fallback do silnika lokalnego
            }
        }
        return@withContext localModuleResponse(userMessage, database)
    }

    private fun localModuleResponse(input: String, database: List<Recipe>): String {
        val lower = input.lowercase().trim()

        // Check if question is about Danie Dnia
        if (lower.contains("danie dnia") || lower.contains("zestaw dnia")) {
            val hero = database.firstOrNull()
            if (hero == null) {
                return "Baza przepisów jest pusta. Dodaj swój pierwszy przepis w aplikacji!"
            }
            val pairing = hero.beveragePairing
            return """
[METADANE: DANIE_DNIA]
* Danie Główne: **${hero.title}**
* Dedykowany Napój: **${pairing?.title ?: "Zielona Matcha Latte"}**
* Czas przygotowania: **${hero.prepTimeMinutes} min**
* Kalorie: **${hero.kcalPerPortion} kcal**
* Fit Punkty: **${hero.fitPoints}/100**

Dlaczego ten napój?
${pairing?.whyItMatches ?: "Zapewnia idealną synergię smaków i stabilny poziom energii."}
""".trimIndent()
        }

        // Check if question is about substitute / brak składnika
        if (lower.contains("nie mam") || lower.contains("brak") || lower.contains("zamiennik") || lower.contains("zastąpić")) {
            val ingQuery = lower.replace("nie mam", "").replace("brak", "").replace("czym zastąpić", "").trim()
            val sub = getSubstitute(ingQuery)
            return """
[MODUŁ 5: RATUNEK KULINARNY]
* Składnik: **${sub.ingredientName}**
* Rekomendowany zamiennik: **${sub.recommendedSubstitute}**
* Wpływ na smak: ${sub.flavorImpact}
* Wpływ na konsystencję: ${sub.textureImpact}
* Zmiana Fit Punktów: **+${sub.fitPointsDelta} Fit Punktów**
* Uzasadnienie: ${sub.explanation}
""".trimIndent()
        }

        // Check if question is fridge / zero waste / ailments
        val (matchedRecipe, msg) = smartLocalFridgeSearch(lower, database)
        if (matchedRecipe != null) {
            return """
[MODUŁ 1: ZERO WASTE & LODÓWKA]
$msg

* Przepis: **${matchedRecipe.title}**
* Czas: **${matchedRecipe.prepTimeMinutes} min**
* Kalorie: **${matchedRecipe.kcalPerPortion} kcal**
* Fit Punkty: **${matchedRecipe.fitPoints}/100** - ${matchedRecipe.fitPointsReason}
* Makroskładniki: Białko **${matchedRecipe.proteinG}g**, Tłuszcze **${matchedRecipe.fatG}g**, Węglowodany **${matchedRecipe.carbsG}g**
""".trimIndent()
        }

        return msg
    }

    private fun callGeminiRestApi(prompt: String, apiKey: String, database: List<Recipe>): String {
        val model = "gemini-2.5-flash"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val dbContext = database.joinToString("\n") { r ->
            "- [ID: ${r.id}] ${r.title} (Czas: ${r.prepTimeMinutes} min, Kcal: ${r.kcalPerPortion}, Fit: ${r.fitPoints}/100, Tagi: ${r.categories.joinToString { it.label }}, Składniki: ${r.ingredients.joinToString { "${it.name} ${it.amount} ${it.unit}" }})"
        }

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "$SYSTEM_INSTRUCTION\n\nŚCIŚLE DOZWOLONA BAZA DANYCH APLIKACJI (ZAKAZ PROPONOWANIA INNYCH PRZEPISÓW):\n$dbContext")
                    })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
        }
        throw RuntimeException("Gemini API error code ${conn.responseCode}")
    }

    private data class IngredientMacroProfile(
        val kcal100: Double,
        val prot100: Double,
        val fat100: Double,
        val carb100: Double,
        val defaultPieceGrams: Double = 60.0,
        val healthScoreDelta: Int = 0
    )

    private fun resolveMacroProfile(rawName: String): IngredientMacroProfile {
        val name = rawName.lowercase().trim().replace("smietan", "śmietan")
        return when {
            // Śmietany i kremówki (precyzyjne wartości w zależności od zawartości tłuszczu)
            "śmietana 36%" in name || "śmietanka 36%" in name || "kremówka 36%" in name || "36%" in name ->
                IngredientMacroProfile(340.0, 2.1, 36.0, 3.0, defaultPieceGrams = 200.0, healthScoreDelta = -2)
            "śmietana 30%" in name || "śmietanka 30%" in name || "kremówka" in name || "śmietana kremowa" in name || "30%" in name ->
                IngredientMacroProfile(292.0, 2.3, 30.0, 3.3, defaultPieceGrams = 200.0, healthScoreDelta = -1)
            "śmietana 18%" in name || "śmietana kwaśna" in name || "kwaśna śmietana" in name ||
            "18%" in name || "od 12 do 18" in name || "12-18" in name || "12 do 18" in name ->
                IngredientMacroProfile(195.0, 2.6, 18.5, 3.8, defaultPieceGrams = 200.0, healthScoreDelta = 0)
            "śmietana 12%" in name || "12%" in name ->
                IngredientMacroProfile(136.0, 2.8, 12.0, 4.0, defaultPieceGrams = 200.0, healthScoreDelta = 1)
            "śmietan" in name -> {
                when {
                    "36" in name -> IngredientMacroProfile(340.0, 2.1, 36.0, 3.0, defaultPieceGrams = 200.0, healthScoreDelta = -2)
                    "30" in name -> IngredientMacroProfile(292.0, 2.3, 30.0, 3.3, defaultPieceGrams = 200.0, healthScoreDelta = -1)
                    "18" in name || "od 12 do 18" in name || "12-18" in name || "12 do 18" in name ->
                        IngredientMacroProfile(195.0, 2.6, 18.5, 3.8, defaultPieceGrams = 200.0, healthScoreDelta = 0)
                    "12" in name -> IngredientMacroProfile(136.0, 2.8, 12.0, 4.0, defaultPieceGrams = 200.0, healthScoreDelta = 1)
                    else -> IngredientMacroProfile(220.0, 2.4, 20.0, 3.6, defaultPieceGrams = 200.0, healthScoreDelta = 0)
                }
            }
            "mascarpone" in name -> IngredientMacroProfile(400.0, 3.5, 42.0, 3.0, defaultPieceGrams = 250.0, healthScoreDelta = -2)
            "kajmak" in name || "karmel" in name || "dulce de leche" in name ->
                IngredientMacroProfile(335.0, 6.0, 8.0, 60.0, defaultPieceGrams = 200.0, healthScoreDelta = -3)

            // Tłuszcze, masła, oleje
            "masło" in name || "maslo" in name -> IngredientMacroProfile(735.0, 0.7, 82.5, 0.7, defaultPieceGrams = 15.0, healthScoreDelta = -1)
            "margaryna" in name -> IngredientMacroProfile(710.0, 0.2, 80.0, 0.5, defaultPieceGrams = 15.0, healthScoreDelta = -2)
            "oliwa" in name || "olej" in name || "smalec" in name -> IngredientMacroProfile(884.0, 0.0, 100.0, 0.0, defaultPieceGrams = 10.0, healthScoreDelta = 5)
            "majonez" in name -> IngredientMacroProfile(680.0, 1.0, 75.0, 2.5, defaultPieceGrams = 20.0, healthScoreDelta = -1)

            // Cukry, słodziki, czekolady
            "cukier puder" in name || "cukier" in name || "sacharoza" in name ->
                IngredientMacroProfile(395.0, 0.0, 0.0, 100.0, defaultPieceGrams = 15.0, healthScoreDelta = -4)
            "miód" in name || "miod" in name -> IngredientMacroProfile(325.0, 0.3, 0.0, 81.0, defaultPieceGrams = 20.0, healthScoreDelta = 1)
            "syrop klonowy" in name || "syrop z agawy" in name -> IngredientMacroProfile(260.0, 0.0, 0.0, 67.0, defaultPieceGrams = 20.0, healthScoreDelta = 0)
            "czekolada" in name -> IngredientMacroProfile(545.0, 5.5, 33.0, 56.0, defaultPieceGrams = 100.0, healthScoreDelta = 1)
            "kakao" in name -> IngredientMacroProfile(310.0, 18.0, 12.0, 14.0, defaultPieceGrams = 10.0, healthScoreDelta = 4)

            // Nasiona, siemię lniane, orzechy (bomba odżywcza i kaloryczna)
            "siemię" in name || "siemie" in name || "len" in name || "nasiona lnu" in name ->
                IngredientMacroProfile(534.0, 18.3, 42.2, 28.9, defaultPieceGrams = 15.0, healthScoreDelta = 6)
            "chia" in name -> IngredientMacroProfile(486.0, 16.5, 30.7, 42.1, defaultPieceGrams = 15.0, healthScoreDelta = 6)
            "słonecznik" in name || "dynia" in name || "pestki" in name ->
                IngredientMacroProfile(580.0, 20.0, 50.0, 15.0, defaultPieceGrams = 15.0, healthScoreDelta = 5)
            "masło orzechowe" in name -> IngredientMacroProfile(595.0, 25.0, 50.0, 20.0, defaultPieceGrams = 25.0, healthScoreDelta = 4)
            "orzech" in name || "migdał" in name || "migdal" in name || "nerkowc" in name || "pistacj" in name ->
                IngredientMacroProfile(630.0, 18.0, 58.0, 14.0, defaultPieceGrams = 25.0, healthScoreDelta = 5)

            // Nabiał i jaja
            "jaj" in name -> IngredientMacroProfile(143.0, 12.6, 9.5, 0.7, defaultPieceGrams = 55.0, healthScoreDelta = 3)
            "twaróg" in name || "twarog" in name -> IngredientMacroProfile(100.0, 18.0, 1.5, 3.5, defaultPieceGrams = 100.0, healthScoreDelta = 5)
            "jogurt grecki" in name -> IngredientMacroProfile(95.0, 9.0, 5.0, 4.0, defaultPieceGrams = 150.0, healthScoreDelta = 4)
            "jogurt" in name -> IngredientMacroProfile(60.0, 4.0, 2.0, 5.0, defaultPieceGrams = 150.0, healthScoreDelta = 3)
            "mozzarella" in name -> IngredientMacroProfile(280.0, 22.0, 20.0, 2.0, defaultPieceGrams = 125.0, healthScoreDelta = 2)
            "feta" in name -> IngredientMacroProfile(260.0, 14.0, 21.0, 4.0, defaultPieceGrams = 50.0, healthScoreDelta = 2)
            "ser" in name || "parmezan" in name -> IngredientMacroProfile(380.0, 28.0, 28.0, 1.0, defaultPieceGrams = 30.0, healthScoreDelta = 1)
            "mleko" in name -> IngredientMacroProfile(48.0, 3.2, 1.8, 4.8, defaultPieceGrams = 250.0, healthScoreDelta = 2)

            // Mięso i ryby
            "kurczak" in name || "pierś" in name || "filet" in name -> IngredientMacroProfile(120.0, 22.5, 2.6, 0.0, defaultPieceGrams = 180.0, healthScoreDelta = 5)
            "indyk" in name -> IngredientMacroProfile(112.0, 24.0, 1.6, 0.0, defaultPieceGrams = 160.0, healthScoreDelta = 6)
            "wołowin" in name || "mielone" in name || "stek" in name -> IngredientMacroProfile(215.0, 20.0, 15.0, 0.0, defaultPieceGrams = 150.0, healthScoreDelta = 2)
            "wieprzowin" in name || "schab" in name || "karkówk" in name -> IngredientMacroProfile(240.0, 19.0, 18.0, 0.0, defaultPieceGrams = 150.0, healthScoreDelta = 1)
            "łosoś" in name || "losos" in name -> IngredientMacroProfile(208.0, 20.0, 13.0, 0.0, defaultPieceGrams = 150.0, healthScoreDelta = 6)
            "tuńczyk" in name || "tunczyk" in name -> IngredientMacroProfile(130.0, 28.0, 1.0, 0.0, defaultPieceGrams = 120.0, healthScoreDelta = 5)
            "dorsz" in name || "ryba" in name || "mintaj" in name -> IngredientMacroProfile(82.0, 18.0, 0.7, 0.0, defaultPieceGrams = 150.0, healthScoreDelta = 5)
            "krewetk" in name -> IngredientMacroProfile(95.0, 20.0, 1.0, 1.0, defaultPieceGrams = 15.0, healthScoreDelta = 4)

            // Owoce i warzywa
            "awokado" in name -> IngredientMacroProfile(160.0, 2.0, 15.0, 9.0, defaultPieceGrams = 140.0, healthScoreDelta = 6)
            "banan" in name -> IngredientMacroProfile(89.0, 1.1, 0.3, 23.0, defaultPieceGrams = 120.0, healthScoreDelta = 3)
            "jabłk" in name -> IngredientMacroProfile(52.0, 0.3, 0.2, 14.0, defaultPieceGrams = 150.0, healthScoreDelta = 3)
            "borówk" in name || "malin" in name || "jagod" in name || "truskawk" in name ->
                IngredientMacroProfile(45.0, 0.8, 0.4, 10.0, defaultPieceGrams = 70.0, healthScoreDelta = 5)
            "owoce" in name -> IngredientMacroProfile(55.0, 0.8, 0.3, 13.0, defaultPieceGrams = 120.0, healthScoreDelta = 4)
            "pomidor" in name || "passata" in name -> IngredientMacroProfile(20.0, 1.0, 0.2, 4.0, defaultPieceGrams = 120.0, healthScoreDelta = 4)
            "ogórek" in name || "ogorek" in name -> IngredientMacroProfile(14.0, 0.7, 0.1, 3.0, defaultPieceGrams = 100.0, healthScoreDelta = 3)
            "papryk" in name -> IngredientMacroProfile(28.0, 1.3, 0.3, 5.5, defaultPieceGrams = 140.0, healthScoreDelta = 4)
            "cebul" in name -> IngredientMacroProfile(40.0, 1.1, 0.1, 9.0, defaultPieceGrams = 80.0, healthScoreDelta = 3)
            "czosnek" in name -> IngredientMacroProfile(145.0, 6.0, 0.5, 33.0, defaultPieceGrams = 4.0, healthScoreDelta = 3)
            "szpinak" in name || "rukola" in name || "sałat" in name || "jarmuż" in name ->
                IngredientMacroProfile(23.0, 2.9, 0.4, 3.6, defaultPieceGrams = 40.0, healthScoreDelta = 5)
            "brokuł" in name || "cukini" in name || "kalafior" in name ->
                IngredientMacroProfile(30.0, 2.6, 0.3, 5.0, defaultPieceGrams = 150.0, healthScoreDelta = 4)
            "marchew" in name -> IngredientMacroProfile(41.0, 0.9, 0.2, 9.6, defaultPieceGrams = 80.0, healthScoreDelta = 3)
            "pieczark" in name || "grzyb" in name -> IngredientMacroProfile(22.0, 3.0, 0.3, 3.3, defaultPieceGrams = 40.0, healthScoreDelta = 3)

            // Zboża, mąki, makarony, ziemniaki
            "chleb" in name || "pieczywo" in name || "bułk" in name || "tost" in name ->
                IngredientMacroProfile(250.0, 8.0, 1.5, 49.0, defaultPieceGrams = 38.0, healthScoreDelta = 2)
            "mąka" in name || "maka" in name -> IngredientMacroProfile(350.0, 10.0, 1.2, 73.0, defaultPieceGrams = 100.0, healthScoreDelta = 2)
            "makaron" in name || "spaghetti" in name || "penne" in name ->
                IngredientMacroProfile(355.0, 12.0, 1.5, 72.0, defaultPieceGrams = 80.0, healthScoreDelta = 2)
            "ryż" in name || "ryz" in name -> IngredientMacroProfile(350.0, 7.0, 0.7, 78.0, defaultPieceGrams = 75.0, healthScoreDelta = 2)
            "kasza" in name || "quinoa" in name || "komos" in name ->
                IngredientMacroProfile(350.0, 13.5, 5.5, 64.0, defaultPieceGrams = 70.0, healthScoreDelta = 5)
            "płatki" in name || "platki" in name || "owsian" in name ->
                IngredientMacroProfile(370.0, 13.0, 7.0, 60.0, defaultPieceGrams = 50.0, healthScoreDelta = 5)
            "ziemniak" in name -> IngredientMacroProfile(77.0, 2.0, 0.1, 17.0, defaultPieceGrams = 90.0, healthScoreDelta = 3)
            "batat" in name -> IngredientMacroProfile(86.0, 1.6, 0.1, 20.0, defaultPieceGrams = 130.0, healthScoreDelta = 4)
            "odżywka" in name || "białko serwatkowe" in name ->
                IngredientMacroProfile(380.0, 78.0, 3.0, 6.0, defaultPieceGrams = 30.0, healthScoreDelta = 6)
            "ciecierzyc" in name || "soczewic" in name || "fasol" in name ->
                IngredientMacroProfile(135.0, 9.0, 1.5, 23.0, defaultPieceGrams = 100.0, healthScoreDelta = 5)
            "deser" in name || "ciastk" in name || "biszkopt" in name ->
                IngredientMacroProfile(380.0, 6.0, 14.0, 58.0, defaultPieceGrams = 100.0, healthScoreDelta = -2)
            else -> IngredientMacroProfile(120.0, 4.0, 3.0, 15.0, defaultPieceGrams = 50.0, healthScoreDelta = 1)
        }
    }

    private fun convertToGrams(amount: Double, unit: String, defaultPieceGrams: Double): Double {
        val u = unit.lowercase().trim()
        val safeAmount = if (amount > 0 && amount.isFinite()) amount else 1.0
        return when {
            u in listOf("g", "gram", "gramy", "gramów") -> safeAmount
            u in listOf("dag", "dkg") -> safeAmount * 10.0
            u in listOf("kg", "kilogram", "kilogramy") -> safeAmount * 1000.0
            u in listOf("ml", "mililitr", "mililitry") -> safeAmount * 1.0
            u in listOf("l", "litr", "litry") -> safeAmount * 1000.0
            u in listOf("łyżka", "lyzka", "łyżki", "łyżek") -> safeAmount * 15.0
            u in listOf("łyżeczka", "lyzeczka", "łyżeczki") -> safeAmount * 5.0
            u in listOf("szklanka", "szklanki", "szklanek") -> safeAmount * 240.0
            u in listOf("kubek", "kubka", "kubki") -> safeAmount * 200.0
            u in listOf("opakowanie", "opak.", "opakowania", "paczka") -> safeAmount * 200.0
            u in listOf("kostka", "kostki") -> safeAmount * 200.0
            u in listOf("garść", "garsc", "garści") -> safeAmount * 30.0
            u in listOf("kromka", "kromki", "kromek") -> safeAmount * 38.0
            else -> safeAmount * defaultPieceGrams
        }
    }

    fun calculateNutritionOffline(
        title: String,
        ingredients: List<RecipeIngredient>,
        portions: Int
    ): EstimatedNutrition {
        val safePortions = portions.coerceAtLeast(1)
        var totalKcal = 0.0
        var totalProtein = 0.0
        var totalFat = 0.0
        var totalCarbs = 0.0
        var healthBonusSum = 0

        val itemBreakdowns = mutableListOf<Pair<String, Int>>()

        for (ing in ingredients) {
            val profile = resolveMacroProfile(ing.name)
            val grams = convertToGrams(ing.amount, ing.unit, profile.defaultPieceGrams)
            val factor = grams / 100.0
            val itemKcal = (profile.kcal100 * factor).toInt()
            itemBreakdowns.add(ing.name to itemKcal)

            totalKcal += profile.kcal100 * factor
            totalProtein += profile.prot100 * factor
            totalFat += profile.fat100 * factor
            totalCarbs += profile.carb100 * factor
            healthBonusSum += profile.healthScoreDelta
        }

        val kcalPerPortion = (totalKcal / safePortions).toInt().coerceAtLeast(30)
        val proteinPerPortion = (totalProtein / safePortions).toInt().coerceAtLeast(1)
        val fatPerPortion = (totalFat / safePortions).toInt().coerceAtLeast(0)
        val carbsPerPortion = (totalCarbs / safePortions).toInt().coerceAtLeast(1)

        // Scientific Fit Points
        var fitScore = 74
        if (proteinPerPortion >= 25) fitScore += 10
        else if (proteinPerPortion >= 15) fitScore += 6

        if (kcalPerPortion in 250..650) fitScore += 6
        else if (kcalPerPortion > 850) fitScore -= 10

        fitScore += (healthBonusSum / ingredients.size.coerceAtLeast(1)).coerceIn(-5, 12)
        if (fatPerPortion > 40) fitScore -= 8

        val finalScore = fitScore.coerceIn(60, 99)

        val topCaloric = itemBreakdowns.sortedByDescending { it.second }.take(3)
        val breakdownStr = topCaloric.joinToString(", ") { "${it.first}: ~${(it.second / safePortions)} kcal" }

        val reason = buildString {
            append("Analityk Dietetyczny AI (Rygorystyczny audyt makro): ")
            if (breakdownStr.isNotBlank()) {
                append("Główne źródła kalorii na porcję: $breakdownStr. ")
            }
            if (fatPerPortion >= 20) {
                append("Wysoka gęstość energetyczna ze składników tłuszczowych (tłuszcz: ${fatPerPortion}g). ")
            }
            if (proteinPerPortion >= 20) {
                append("Wysoka zawartość pełnowartościowego białka (${proteinPerPortion}g). ")
            }
            append("Łącznie: $kcalPerPortion kcal / porcję. Fit Punkty: $finalScore/100.")
        }

        return EstimatedNutrition(
            kcalPerPortion = kcalPerPortion,
            proteinG = proteinPerPortion,
            fatG = fatPerPortion,
            carbsG = carbsPerPortion,
            fitPoints = finalScore,
            fitPointsReason = reason,
            isFromGemini = false
        )
    }

    suspend fun estimateNutrition(
        title: String,
        description: String,
        ingredients: List<RecipeIngredient>,
        portions: Int,
        apiKey: String?
    ): EstimatedNutrition = withContext(Dispatchers.IO) {
        val safePortions = portions.coerceAtLeast(1)
        if (!apiKey.isNullOrBlank()) {
            try {
                return@withContext callGeminiNutritionApi(title, description, ingredients, safePortions, apiKey)
            } catch (_: Exception) {
                // Przy braku sieci lub błędzie API, użyj zaktualizowanego kalkulatora offline
            }
        }
        return@withContext calculateNutritionOffline(title, ingredients, safePortions)
    }

    private fun callGeminiNutritionApi(
        title: String,
        description: String,
        ingredients: List<RecipeIngredient>,
        portions: Int,
        apiKey: String
    ): EstimatedNutrition {
        val model = "gemini-2.5-flash"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 15000

        val ingsText = ingredients.joinToString("\n") { "- ${it.name}: ${it.amount} ${it.unit}" }
        val prompt = """
Jesteś certyfikowanym dietetykiem klinicznym, precyzyjnym analitykiem laboratoryjnym żywności i bezkompromisowym audytorem kalorii dla aplikacji FitKuchnia.
Przeanalizuj poniższe danie oraz DOKŁADNĄ listę składników z ich gramaturami i proporcjami.

Danie: $title
Opis: $description
Liczba porcji bazowych: $portions
Składniki:
$ingsText

KRYTYCZNE ZASADY I CZUJNOŚĆ OBLICZEŃ DIETETYCZNYCH:
1. Nigdy nie zaniżaj kaloryczności! Zwróć szczególną uwagę na składniki wysokotłuszczowe, nabiałowe i cukry:
   - Śmietana: śmietana 18% ma ok. 186-200 kcal/100g, śmietana 30% ma ok. 292 kcal/100g, śmietana 36%/kremówka ok. 340-350 kcal/100g, mascarpone ok. 400 kcal/100g. Jeśli w nazwie jest zakres np. "śmietana od 12 do 18%" lub ogólna "śmietana", zwłaszcza w deserach, przyjmij górny realistyczny próg (min. 18% tłuszczu lub wyższy dla deseru).
   - Masło ma ok. 735 kcal/100g, oleje 884-900 kcal/100g, majonez ok. 680 kcal/100g.
   - Cukier ma ok. 395 kcal/100g, miód ok. 325 kcal/100g, czekolada ok. 545 kcal/100g, orzechy/migdały ok. 630 kcal/100g, siemię lniane ok. 534 kcal/100g.
2. Zsumuj dokładnie kalorie i makroskładniki ze WSZYSTKICH składników całego dania, a następnie PODZIEL DOKŁADNIE przez liczbę porcji ($portions).
3. Podaj FitPoints (1-100) odzwierciedlający gęstość odżywczą, witaminy i profil kwasów tłuszczowych.
4. W polu "reason" podaj po polsku zwięzłe, wysoce analityczne rozbicie kaloryczności, wprost wskazując, które składniki dostarczyły najwięcej kalorii i tłuszczu (np. "Śmietana 18%: ~280 kcal, Migdały: ~126 kcal, Owoce: ~110 kcal. Łącznie: 540 kcal / porcję.").

Zwróć TYLKO czysty obiekt JSON bez znaczników markdown:
{"kcal": int, "protein": int, "fat": int, "carbs": int, "fitPoints": int, "reason": "dokładne wyjaśnienie rozbicia kalorii i oceny"}
""".trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    val rawText = parts.getJSONObject(0).getString("text")
                    val cleanJson = rawText.substringAfter("{").substringBeforeLast("}")
                    val parsed = JSONObject("{$cleanJson}")
                    return EstimatedNutrition(
                        kcalPerPortion = parsed.getInt("kcal"),
                        proteinG = parsed.getInt("protein"),
                        fatG = parsed.getInt("fat"),
                        carbsG = parsed.getInt("carbs"),
                        fitPoints = parsed.getInt("fitPoints").coerceIn(60, 99),
                        fitPointsReason = parsed.getString("reason"),
                        isFromGemini = true
                    )
                }
            }
        }
        throw RuntimeException("Gemini nutrition API returned code ${conn.responseCode}")
    }

    /**
     * Zgodnie z Zadaniem 4:
     * Dogłębna analiza zjedzonych dań z bieżącego tygodnia oraz dobór planu posiłkowego
     * z BEZWZGLĘDNYM ZAKAZEM proponowania dań spoza bazy danych aplikacji.
     */
    suspend fun analyzeWeeklyDietAndRecommendMeals(
        weeklyMeals: List<MealHistoryEntry>,
        availableRecipes: List<Recipe>,
        apiKey: String?
    ): String = withContext(Dispatchers.IO) {
        if (weeklyMeals.isEmpty()) {
            return@withContext """
# 🥗 Asystent Dietetyk AI FitKuchnia

Nie odnotowano jeszcze zjedzonych posiłków w bieżącym tygodniu!

### 💡 Jak rozpocząć analizę?
1. Przejdź do zakładki **Odkrywaj** lub **Szukaj**.
2. Wybierz przepis, który zjadłeś.
3. Kliknij przycisk **„🍽️ Oznacz jako zjedzone”**.
4. Wróć tutaj i kliknij przycisk analizy – Asystent Dietetyk AI przeprowadzi rzetelny audyt Twojej diety oraz zaproponuje spersonalizowany plan posiłków wyłącznie ze zweryfikowanej bazy aplikacji!
""".trimIndent()
        }

        val effectiveDb = availableRecipes.filterNot { com.example.data.local.AppRecipeStorage.isDeprecatedBotRecipe(it) }

        if (!apiKey.isNullOrBlank()) {
            try {
                val geminiResponse = callGeminiWeeklyDietAnalysis(weeklyMeals, effectiveDb, apiKey)
                if (!geminiResponse.isNullOrBlank()) {
                    return@withContext geminiResponse
                }
            } catch (e: Exception) {
                Log.w(TAG, "Błąd podczas zapytania do Gemini dla analizy dietetyka: ${e.message}")
            }
        }

        // Rzetelny silnik dietetyczny offline działający wg tych samych ścisłych reguł medycznych
        return@withContext generateLocalWeeklyDietAnalysis(weeklyMeals, effectiveDb)
    }

    private fun callGeminiWeeklyDietAnalysis(
        weeklyMeals: List<MealHistoryEntry>,
        availableRecipes: List<Recipe>,
        apiKey: String
    ): String? {
        val model = "gemini-2.5-flash"
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 12000
        conn.readTimeout = 25000

        val zjedzonePosilkiText = weeklyMeals.mapIndexed { idx, m ->
            "${idx + 1}. [Data: ${m.dateString}] Danie: \"${m.title}\" | Porcje: ${m.portions} | Kcal: ${m.kcal} | Makro: B: ${m.proteinG}g, T: ${m.fatG}g, W: ${m.carbsG}g | Składniki: ${m.ingredients.joinToString(", ")}"
        }.joinToString("\n")

        val dostepnePrzepisyText = availableRecipes.map { r ->
            "- ID: \"${r.id}\" | Tytuł: \"${r.title}\" | Czas: ${r.prepTimeMinutes} min | Kcal: ${r.kcalPerPortion} kcal | Makro: B: ${r.proteinG}g, T: ${r.fatG}g, W: ${r.carbsG}g | Składniki: ${r.ingredients.joinToString(", ") { it.name }}"
        }.joinToString("\n")

        val prompt = """
Jesteś certyfikowanym lekarzem dietetykiem klinicznym oraz głównym audytorem żywieniowym aplikacji FitKuchnia.
Przeprowadź dogłębną analizę żywieniową posiłków zjedzonych w tym tygodniu przez użytkownika i zaproponuj mu zbilansowany plan posiłków.

zjedzone_posilki:
$zjedzonePosilkiText

dostepne_przepisy_w_aplikacji:
$dostepnePrzepisyText

BARDZO WAŻNE I BEZWZGLĘDNE ZASADY DIETETYCZNE I SYSTEMOWE:
1. WIEDZA MEDYCZNA I DIETETYCZNA:
   - Użyj wiedzy medycznej/dietetycznej WYŁĄCZNIE do analizy składników zjedzonych posiłków.
   - Przeanalizuj czy w zjedzonych daniach było wystarczająco pełnowartościowego białka, błonnika, warzyw, owoców, antyoksydantów, zdrowych kwasów tłuszczowych (omega-3) oraz czy nie było nadmiaru tłuszczów nasyconych lub cukrów prostych.
   - Wskaż zidentyfikowane niedobory i obszary wymagające wzmocnienia (np. odporność, trawienie, regeneracja mięśniowa).
2. BEZWZGLĘDNY ZAKAZ PROPONOWANIA PRZEPISÓW SPOZA BAZY:
   - REKOMENDACJE I PLAN POSIŁKOWY MUSZĄ POCHODZIĆ TYLKO I WYŁĄCZNIE Z BAZY DANYCH APLIKACJI ("dostepne_przepisy_w_aplikacji").
   - MASZ CAŁKOWITY ZAKAZ proponowania lub wymyślania jakichkolwiek przepisów czy dań spoza powyższej listy bazy aplikacji!
   - W sekcjach rekomendacji używaj DOKŁADNYCH tytułów dań z bazy danych aplikacji.
   - Jeśli w bazie brak idealnego dania na dany niedobór, wybierz najbardziej zbliżone dostępne w aplikacji i podaj praktyczną wskazówkę, jakich składników brakuje lub co warto do niego dodać.

Format odpowiedzi w języku polskim w czystym Markdown:
# 🥗 Raport Tygodniowy Dietetyka AI FitKuchnia

## 1. 📊 Audyt Odżywczy Zjedzonych Posiłków
- **Bilans makroskładników:** (dokładna ocena białka, tłuszczów i węglowodanów)
- **Gęstość odżywcza:** (błonnik, witaminy, warzywa, owoce, nawodnienie)
- **Zidentyfikowane niedobory:** (konkretne braki odżywcze w tym tygodniu)

## 2. 🩺 Kliniczne Wnioski Dietetyczne
(Krótka, motywująca i profesjonalna ocena lekarsko-dietetyczna)

## 3. 🍲 Rekomendacje z Bazy FitKuchnia
(Konkretne dania wybrane WYŁĄCZNIE z bazy aplikacji, które idealnie wyrównują niedobory, z wyjaśnieniem dlaczego i ewentualną modyfikacją składników)

## 4. 📅 Spersonalizowany Plan Posiłków (Tylko Baza Aplikacji)
(Przejrzysty jadłospis na kolejne posiłki, w 100% oparty na pozycjach z bazy danych FitKuchnia)
""".trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    val rawText = parts.getJSONObject(0).getString("text")
                    return rawText.trim()
                }
            }
        }
        return null
    }

    private fun generateLocalWeeklyDietAnalysis(
        weeklyMeals: List<MealHistoryEntry>,
        availableRecipes: List<Recipe>
    ): String {
        val totalMeals = weeklyMeals.size
        val totalKcal = weeklyMeals.sumOf { it.kcal * it.portions }
        val totalProt = weeklyMeals.sumOf { it.proteinG * it.portions }
        val totalFat = weeklyMeals.sumOf { it.fatG * it.portions }
        val totalCarbs = weeklyMeals.sumOf { it.carbsG * it.portions }

        val avgKcal = if (totalMeals > 0) totalKcal / totalMeals else 0
        val avgProt = if (totalMeals > 0) totalProt / totalMeals else 0
        val avgFat = if (totalMeals > 0) totalFat / totalMeals else 0
        val avgCarbs = if (totalMeals > 0) totalCarbs / totalMeals else 0

        // Analiza obecności warzyw, nasion, błonnika
        val allIngredients = weeklyMeals.flatMap { it.ingredients }.map { it.lowercase() }
        val hasVegetables = allIngredients.any { ing ->
            ing.contains("pomidor") || ing.contains("ogórek") || ing.contains("ogorek") ||
                    ing.contains("dynia") || ing.contains("szpinak") || ing.contains("brokuł") ||
                    ing.contains("czosnek") || ing.contains("cebula") || ing.contains("marchew") ||
                    ing.contains("cukinia") || ing.contains("papryka") || ing.contains("szczypiorek")
        }
        val hasFiberAndSeeds = allIngredients.any { ing ->
            ing.contains("siemię") || ing.contains("siemie") || ing.contains("len") ||
                    ing.contains("chia") || ing.contains("płatki") || ing.contains("owsian") ||
                    ing.contains("otręby") || ing.contains("kasza")
        }
        val hasFruits = allIngredients.any { ing ->
            ing.contains("borów") || ing.contains("truskawk") || ing.contains("malin") ||
                    ing.contains("jabłko") || ing.contains("banan") || ing.contains("owoc")
        }

        val deficits = mutableListOf<String>()
        if (avgProt < 20) {
            deficits.add("Niska podaż pełnowartościowego białka (średnio ${avgProt}g na posiłek – optymalnie min. 25-30g dla ochrony masy mięśniowej)")
        }
        if (!hasVegetables) {
            deficits.add("Niedobór świeżych warzyw i witamin rozpuszczalnych w wodzie (wit. C, foliany, potas)")
        }
        if (!hasFiberAndSeeds) {
            deficits.add("Brak nasion bogatych w kwasy omega-3 i śluzy osłaniające układ pokarmowy (np. siemię lniane)")
        }
        if (!hasFruits) {
            deficits.add("Ograniczona podaż polifenoli i antyoksydantów ze świeżych owoców jagodowych")
        }
        if (deficits.isEmpty()) {
            deficits.add("Dieta jest dobrze zrównoważona. Warto urozmaicać źródła mikroelementów i dbać o stałe nawodnienie.")
        }

        // Dobór przepisów WYŁĄCZNIE z rzeczywistej bazy przepisów
        val cleanRecipes = availableRecipes.filterNot { com.example.data.local.AppRecipeStorage.isDeprecatedBotRecipe(it) }
        val recommended = mutableListOf<Recipe>()

        val highProtein = cleanRecipes.find { it.proteinG >= 20 }
        if (highProtein != null) recommended.add(highProtein)

        val soupOrWarm = cleanRecipes.find { it.categories.contains(RecipeCategory.NA_CIEPLO) || it.title.contains("zupa", ignoreCase = true) }
        if (soupOrWarm != null && !recommended.contains(soupOrWarm)) recommended.add(soupOrWarm)

        val balanced = cleanRecipes.find { it.fitPoints >= 85 }
        if (balanced != null && !recommended.contains(balanced)) recommended.add(balanced)

        val sweetOrBreakfast = cleanRecipes.find { it.categories.contains(RecipeCategory.NA_SLODKO) || it.categories.contains(RecipeCategory.SNIADANIE) }
        if (sweetOrBreakfast != null && !recommended.contains(sweetOrBreakfast)) recommended.add(sweetOrBreakfast)

        // Uzupełnij do 4 dań z bazy, jeśli brakuje
        for (r in cleanRecipes) {
            if (recommended.size >= 4) break
            if (!recommended.contains(r)) recommended.add(r)
        }

        return buildString {
            append("# 🥗 Raport Tygodniowy Dietetyka AI FitKuchnia\n\n")
            append("## 1. 📊 Audyt Odżywczy Zjedzonych Posiłków\n")
            append("- **Zarejestrowane posiłki w tym tygodniu:** $totalMeals\n")
            append("- **Łączna energia:** $totalKcal kcal (średnio **$avgKcal kcal** / posiłek)\n")
            append("- **Średni profil makro na posiłek:** Białko: **${avgProt}g**, Tłuszcze: **${avgFat}g**, Węglowodany: **${avgCarbs}g**\n")
            append("\n### ⚠️ Zidentyfikowane obszary i niedobory:\n")
            deficits.forEach { def ->
                append("• $def\n")
            }

            append("\n## 2. 🩺 Kliniczne Wnioski Dietetyczne\n")
            append("W zjedzonych posiłkach kluczem jest wyrównanie proporcji białkowo-tłuszczowych oraz wzmocnienie osłony przewodu pokarmowego. Pamiętaj o regularnym spożywaniu posiłków o niskim stopniu przetworzenia.\n")

            append("\n## 3. 🍲 Dopasowane Dania z Twojej Bazy FitKuchnia\n")
            append("Wszystkie poniższe propozycje zostały dobrane **WYŁĄCZNIE z Twojej zweryfikowanej bazy przepisów w aplikacji**, aby natychmiast uzupełnić zdiagnozowane braki:\n\n")

            recommended.forEach { r ->
                append("### 🔹 **${r.title}**\n")
                append("- **Wartości w 1 porcji:** ${r.kcalPerPortion} kcal | Białko: ${r.proteinG}g | Tłuszcz: ${r.fatG}g | Węgle: ${r.carbsG}g | Fit: **${r.fitPoints}/100**\n")
                append("- **Dlaczego to danie:** ${r.description.take(160)}...\n")
                if (r.proteinG < 15 && avgProt < 20) {
                    append("- **Wskazówka modyfikacji:** Aby podbić białko w tym posiłku, dodaj łyżkę jogurtu wysokobiałkowego lub nasion.\n")
                }
                append("\n")
            }

            append("## 4. 📅 Spersonalizowany Plan Posiłków na Kolejne Dni\n")
            append("Poniższy jadłospis opiera się w 100% na dostępnych przepisach w Twojej bazie:\n")
            val mealLabels = listOf("Śniadanie", "II Śniadanie / Obiad", "Podwieczorek / Kolacja", "Danie uzupełniające")
            recommended.forEachIndexed { idx, rec ->
                val label = mealLabels.getOrElse(idx) { "Posiłek ${idx + 1}" }
                append("• **$label:** ${rec.title} ([${rec.kcalPerPortion} kcal | B: ${rec.proteinG}g | T: ${rec.fatG}g | W: ${rec.carbsG}g])\n")
            }
            if (recommended.isEmpty()) {
                append("• Dodaj przepisy do swojej bazy, aby asystent ułożył z nich pełny jadłospis na cały dzień.\n")
            }
            append("\n_Wszystkie powyższe dania możesz znaleźć i przygotować w aplikacji FitKuchnia._")
        }
    }
}
