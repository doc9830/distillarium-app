package ru.distillarium.knowledge

import java.util.Locale

/** Нормализация русских словоформ без морфологического словаря. */
object SearchText {
    val tokenRegex = Regex("[\\p{L}\\p{N}]+")

    private val suffixes = listOf(
        "иями", "ями", "ами", "ыми", "ими", "ения", "ению", "ении", "ение",
        "ости", "ость", "ого", "его", "ему", "ому", "ая", "ое", "ые", "ий",
        "ый", "ой", "ем", "ом", "ах", "ях", "ов", "ев", "ам", "ям", "ую",
        "юю", "ия", "ие", "ии", "ей", "ок", "а", "я", "ы", "и", "у", "ю",
        "е", "о", "ь",
    )

    fun stem(token: String): String {
        if (token.length <= 4) return token
        val suffix = suffixes.firstOrNull { ending ->
            token.endsWith(ending) && token.length - ending.length >= 3
        }
        return if (suffix == null) token else token.dropLast(suffix.length)
    }

    fun tokens(text: String): List<String> = tokenRegex
        .findAll(text.lowercase(Locale.ROOT).replace('ё', 'е'))
        .map { stem(it.value) }
        .filter { it.isNotEmpty() }
        .toList()

    fun normalize(text: String): String = tokens(text).joinToString(" ")

    /** Основы, которые нужно искать для запроса: слово + бытовые синонимы. */
    fun variants(token: String): Set<String> = buildSet {
        add(token)
        SYNONYMS[token]?.let(::addAll)
    }

    private val SYNONYMS = mapOf(
        "сивушн" to setOf("сивух", "хвост"),
        "сивух" to setOf("сивушн", "хвост"),
        "хвост" to setOf("сивушн"),
        "сахаромер" to setOf("ареометр", "гидрометр"),
        "ареометр" to setOf("сахаромер", "гидрометр"),
        "гидрометр" to setOf("ареометр", "сахаромер"),
        "браг" to setOf("затор"),
        "затор" to setOf("браг"),
    )
}
