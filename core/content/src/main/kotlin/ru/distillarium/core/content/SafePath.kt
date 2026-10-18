package ru.distillarium.core.content

/**
 * Относительные пути внутри пакета.
 *
 * Пакет приходит из сети, поэтому путь проверяется до любого обращения к файлу:
 * никаких абсолютных путей, `..`, обратных слэшей и управляющих символов.
 */
object SafePath {
    private const val MAX_LENGTH = 512
    private val FORBIDDEN = charArrayOf('\\', '\u0000', ':', '*', '?', '"', '<', '>', '|')

    fun isSafe(relative: String): Boolean {
        if (relative.isBlank() || relative.length > MAX_LENGTH) return false
        if (relative.startsWith('/') || relative.endsWith('/')) return false
        if (relative.any { it.isISOControl() || it in FORBIDDEN }) return false
        val parts = relative.split('/')
        return parts.isNotEmpty() && parts.none { it.isEmpty() || it == "." || it == ".." }
    }

    fun requireSafe(relative: String, field: String): String {
        if (!isSafe(relative)) {
            throw ContentException("$field: недопустимый путь «$relative»")
        }
        return relative
    }
}
