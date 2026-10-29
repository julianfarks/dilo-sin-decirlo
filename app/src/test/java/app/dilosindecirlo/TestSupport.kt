package app.dilosindecirlo

import app.dilosindecirlo.data.Card

/** Mazo sintético para pruebas del motor (no depende del catálogo real). */
fun fakeCards(count: Int = 60, difficulty: Int = 1): List<Card> =
    (1..count).map { i ->
        Card(
            id = "t:$i",
            word = "palabra$i",
            forbidden = listOf("a$i", "b$i", "c$i", "d$i", "e$i"),
            categoryId = "comida",
            difficulty = difficulty,
            custom = false,
        )
    }
