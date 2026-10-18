package ru.distillarium.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.distillarium.content.ContentRepository
import ru.distillarium.recipes.RecipeLoader
import ru.distillarium.recipes.RecipeTemplate

/** Мост между установленным пакетом и UI знаний. */
class WikiRepository(private val contentRepository: ContentRepository) {
    suspend fun load(): WikiLibrary = withContext(Dispatchers.IO) {
        val directory = contentRepository.currentDirectory()
        WikiLoader.load(directory)
    }

    suspend fun recipes(): List<RecipeTemplate> = withContext(Dispatchers.IO) {
        val directory = contentRepository.currentDirectory()
        RecipeLoader.load(directory)
    }

    suspend fun version(): String? = contentRepository.installed()?.state?.contentVersion
}
