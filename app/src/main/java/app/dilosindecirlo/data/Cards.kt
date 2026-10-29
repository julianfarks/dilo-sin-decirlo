package app.dilosindecirlo.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Categoría del catálogo (p. ej. "comida"). */
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val difficulty: Int,
)

/**
 * Carta del juego. [id] es estable: "b:palabra" para el catálogo integrado y
 * "c:uuid" para cartas personalizadas.
 */
data class Card(
    val id: String,
    val word: String,
    val forbidden: List<String>,
    val categoryId: String,
    val difficulty: Int,
    val custom: Boolean = false,
)

data class Catalog(val categories: List<Category>, val cards: List<Card>) {
    fun category(id: String): Category? = categories.firstOrNull { it.id == id }
}

/** Normalización de texto. Debe mantenerse idéntica a `normalize()` de tools/cards.py. */
object CardText {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val noAccents = combiningMarks.replace(decomposed, "")
        val clean = buildString(noAccents.length) {
            for (c in noAccents) append(if (c in 'a'..'z' || c in '0'..'9') c else ' ')
        }
        return spaces.replace(clean, " ").trim()
    }

    /** Quita una "s" final simple para comparar singular/plural. */
    fun singular(word: String): String =
        if (word.length > 3 && word.endsWith("s")) word.dropLast(1) else word

    fun commonPrefixLength(a: String, b: String): Int {
        var n = 0
        val max = minOf(a.length, b.length)
        while (n < max && a[n] == b[n]) n++
        return n
    }
}

/** Validación de cartas (personalizadas e importadas). Mensajes en español para mostrar al usuario. */
object CardValidator {
    const val MIN_FORBIDDEN = 5
    const val MAX_FORBIDDEN = 10
    const val MAX_WORD_LENGTH = 40

    /**
     * @param existingWords palabras secretas ya existentes (catálogo + personalizadas), sin incluir la que se edita.
     * @return lista de errores; vacía si la carta es válida.
     */
    fun validate(word: String, forbidden: List<String>, existingWords: Collection<String>): List<String> {
        val errors = mutableListOf<String>()
        val cleanWord = word.trim()
        val nWord = CardText.normalize(cleanWord)
        if (nWord.isEmpty()) {
            errors += "La palabra secreta no puede estar vacía."
            return errors
        }
        if (cleanWord.length > MAX_WORD_LENGTH) errors += "La palabra secreta es demasiado larga (máximo $MAX_WORD_LENGTH caracteres)."

        val existing = existingWords.map { CardText.singular(CardText.normalize(it)) }.toSet()
        if (CardText.singular(nWord) in existing) errors += "Ya existe una carta con la palabra «$cleanWord»."

        val cleaned = forbidden.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.size < MIN_FORBIDDEN) errors += "Necesitas al menos $MIN_FORBIDDEN palabras prohibidas (tienes ${cleaned.size})."
        if (cleaned.size > MAX_FORBIDDEN) errors += "Máximo $MAX_FORBIDDEN palabras prohibidas (tienes ${cleaned.size})."

        val seen = mutableSetOf<String>()
        for (f in cleaned) {
            val nf = CardText.normalize(f)
            if (nf.isEmpty()) {
                errors += "«$f» no es una palabra válida."
                continue
            }
            if (!seen.add(nf)) errors += "La palabra prohibida «$f» está repetida."
            if (nf == nWord || CardText.singular(nf) == CardText.singular(nWord)) {
                errors += "«$f» es la propia palabra secreta."
            } else if (nf.length >= 4 && (nf in nWord || nWord in nf)) {
                errors += "«$f» es demasiado parecida a la palabra secreta."
            } else if (CardText.commonPrefixLength(nf.replace(" ", ""), nWord.replace(" ", "")) >= 6) {
                errors += "«$f» comparte raíz con la palabra secreta."
            }
        }
        return errors
    }
}

/** Lectura y escritura del JSON del catálogo y de las cartas personalizadas. */
object CardCodec {
    fun parseCatalog(json: String): Catalog {
        val root = JSONObject(json)
        val cats = root.getJSONArray("categories")
        val categories = (0 until cats.length()).map { i ->
            val o = cats.getJSONObject(i)
            Category(o.getString("id"), o.getString("name"), o.optString("emoji", ""), o.optInt("difficulty", 2))
        }
        val cardArray = root.getJSONArray("cards")
        val cards = (0 until cardArray.length()).map { i ->
            val o = cardArray.getJSONObject(i)
            val word = o.getString("word")
            Card(
                id = "b:" + CardText.normalize(word).replace(' ', '_'),
                word = word,
                forbidden = o.getJSONArray("forbidden").toStringList(),
                categoryId = o.getString("category"),
                difficulty = o.optInt("difficulty", 2),
            )
        }
        return Catalog(categories, cards)
    }

    fun customToJson(cards: List<Card>): String {
        val arr = JSONArray()
        for (c in cards) arr.put(cardToJson(c, includeId = true))
        return JSONObject().put("version", 1).put("cards", arr).toString(1)
    }

    /** Formato de exportación/importación (sin ids internos). */
    fun exportJson(cards: List<Card>): String {
        val arr = JSONArray()
        for (c in cards) arr.put(cardToJson(c, includeId = false))
        return JSONObject().put("version", 1).put("cards", arr).toString(1)
    }

    /** Devuelve las cartas del JSON (con ids "c:" nuevos si no los traen). */
    fun parseCustom(json: String, idFactory: () -> String): List<Card> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("cards")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Card(
                id = o.optString("id", "").ifBlank { "c:" + idFactory() },
                word = o.getString("word").trim(),
                forbidden = o.getJSONArray("forbidden").toStringList().map { it.trim() },
                categoryId = o.optString("category", "cultura"),
                difficulty = o.optInt("difficulty", 2).coerceIn(1, 3),
                custom = true,
            )
        }
    }

    private fun cardToJson(c: Card, includeId: Boolean): JSONObject {
        val o = JSONObject()
        if (includeId) o.put("id", c.id)
        o.put("word", c.word)
        o.put("forbidden", JSONArray(c.forbidden))
        o.put("category", c.categoryId)
        o.put("difficulty", c.difficulty)
        return o
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
