package ru.distillarium.core.content

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Распаковка ZIP с защитой от zip-slip и «бомб».
 *
 * Пути проверяются до создания файла, символические ссылки не создаются,
 * распакованный объём ограничен. При любой ошибке каталог назначения удаляется.
 */
object SafeZip {
    private const val MAX_ENTRIES = 10_000
    private const val MIN_UNCOMPRESSED_BYTES = 8L * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 128L * 1024 * 1024

    fun maxUncompressed(packSize: Long): Long {
        val ratio = if (packSize > Long.MAX_VALUE / 3) Long.MAX_VALUE else packSize * 3
        return minOf(MAX_UNCOMPRESSED_BYTES, maxOf(ratio, MIN_UNCOMPRESSED_BYTES))
    }

    fun extract(zipFile: File, destination: File, packSize: Long): Int {
        if (!zipFile.isFile) throw ContentException("Архив контента не найден")
        destination.deleteRecursively()
        if (!destination.mkdirs() && !destination.isDirectory) {
            throw ContentException("Не удалось создать каталог для распаковки")
        }
        val root = destination.canonicalFile.toPath()
        val limit = maxUncompressed(packSize)
        val seen = HashSet<String>()
        var total = 0L
        var entries = 0

        try {
            ZipInputStream(zipFile.inputStream().buffered(64 * 1024)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES) {
                        throw ContentException("В архиве слишком много файлов")
                    }
                    val rawName = entry.name
                    if (rawName.isBlank() || rawName.any { it.isISOControl() }) {
                        throw ContentException("Архив содержит недопустимое имя файла")
                    }
                    val isDirectory = entry.isDirectory || rawName.endsWith('/')
                    val name = rawName.trimEnd('/')
                    SafePath.requireSafe(name, "архив")
                    if (!seen.add(name)) {
                        throw ContentException("В архиве дважды встречается $name")
                    }
                    val target = File(destination, name).canonicalFile
                    if (target.toPath() != root && !target.toPath().startsWith(root)) {
                        throw ContentException("Архив пытается выйти за пределы пакета: $rawName")
                    }
                    if (isDirectory) {
                        if (!target.mkdirs() && !target.isDirectory) {
                            throw ContentException("Не удалось создать каталог $name")
                        }
                    } else {
                        target.parentFile?.let { parent ->
                            if (!parent.mkdirs() && !parent.isDirectory) {
                                throw ContentException("Не удалось создать каталог для $name")
                            }
                        }
                        if (target.exists()) {
                            throw ContentException("В архиве дважды встречается $name")
                        }
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > limit) {
                                    throw ContentException(
                                        "Распакованный пакет больше допустимого размера",
                                    )
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: ContentException) {
            destination.deleteRecursively()
            throw error
        } catch (error: ZipException) {
            destination.deleteRecursively()
            throw ContentException("Архив повреждён: ${error.message}", error)
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw ContentException("Не удалось распаковать архив: ${error.message}", error)
        }
        if (entries == 0) {
            destination.deleteRecursively()
            throw ContentException("Архив пуст")
        }
        return entries
    }
}
