package ru.distillarium.appupdate

/** Некорректный или несовместимый манифест обновления приложения. */
class AppUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Семантическая версия приложения и вычисляемый Android versionCode. */
object AppVersion {
    private val NAME_RE = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

    fun isValidName(value: String): Boolean = NAME_RE.matches(value)

    fun codeFor(name: String): Int {
        val match = NAME_RE.matchEntire(name)
            ?: throw AppUpdateException("Некорректная версия приложения «$name»: ожидается x.y.z")
        val (major, minor, patch) = match.destructured
        val minorValue = minor.toIntOrNull()
            ?: throw AppUpdateException("Некорректная версия приложения «$name»")
        val patchValue = patch.toIntOrNull()
            ?: throw AppUpdateException("Некорректная версия приложения «$name»")
        if (minorValue !in 0..999 || patchValue !in 0..999) {
            throw AppUpdateException("Версия приложения «$name»: minor и patch должны быть 0..999")
        }
        val code = major.toLong() * 1_000_000 + minorValue * 1_000L + patchValue
        if (code <= 0 || code > Int.MAX_VALUE) {
            throw AppUpdateException("Версия приложения «$name» выходит за пределы versionCode")
        }
        return code.toInt()
    }
}

/**
 * Манифест обновления APK.
 *
 * versionCode — целое число, по которому Android и приложение сравнивают версии.
 * versionName — только для показа пользователю. Оба поля обязаны совпадать.
 */
data class AppUpdateManifest(
    val schemaVersion: Int,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val released: String,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String,
    val notes: String?,
) {
    companion object {
        const val SUPPORTED_SCHEMA = 1
        const val MAX_APK_SIZE = 200L * 1024 * 1024
    }
}
