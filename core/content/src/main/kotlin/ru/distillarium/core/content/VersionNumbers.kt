package ru.distillarium.core.content

/**
 * Сравнение версий из числовых сегментов.
 *
 * contentVersion: 2026.10.1, minAppVersion: 0.1.0. Сравнение строкой дало бы
 * ошибку «2026.9.1 > 2026.10.0», поэтому каждый сегмент сравнивается как число.
 */
object VersionNumbers {
    private val VERSION_RE = Regex("^\\d+(\\.\\d+)*$")

    fun isValid(value: String): Boolean = VERSION_RE.matches(value)

    fun compare(left: String, right: String): Int {
        val a = segments(left)
        val b = segments(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isAtLeast(version: String, minimum: String): Boolean =
        compare(version, minimum) >= 0

    private fun segments(value: String): List<Long> {
        if (!isValid(value)) {
            throw ContentException("Некорректная версия «$value»: ожидается x.y.z")
        }
        return value.split('.').map { part ->
            part.toLongOrNull()
                ?: throw ContentException("Некорректная версия «$value»: сегмент слишком большой")
        }
    }
}
