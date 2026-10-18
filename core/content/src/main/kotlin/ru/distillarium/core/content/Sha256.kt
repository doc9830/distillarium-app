package ru.distillarium.core.content

import java.io.File
import java.security.MessageDigest

/** SHA-256 для файлов и массивов байт. */
object Sha256 {
    private val HEX_RE = Regex("^[0-9a-fA-F]{64}$")

    fun ofBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun ofFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun isValidHex(value: String): Boolean = HEX_RE.matches(value)

    fun normalizeHex(value: String): String = value.lowercase()

    fun matches(file: File, expected: String): Boolean =
        isValidHex(expected) &&
            MessageDigest.isEqual(
                ofFile(file).toByteArray(Charsets.US_ASCII),
                normalizeHex(expected).toByteArray(Charsets.US_ASCII),
            )

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        val digits = "0123456789abcdef"
        for (index in indices) {
            val value = this[index].toInt() and 0xff
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0f]
        }
        return String(chars)
    }
}
