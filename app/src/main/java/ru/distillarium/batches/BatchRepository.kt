package ru.distillarium.batches

import android.content.Context
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.distillarium.recipes.RecipeTemplate

/**
 * Единственная точка доступа к пользовательским партиям.
 * Контент сюда не пишет: шаблон копируется в обычные строки user.db.
 */
class BatchRepository(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val database = UserDatabase(context)
    private val mutex = Mutex()

    suspend fun all(): List<BatchDetails> = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.batches().map { batch -> loadDetails(batch) }
        }
    }

    suspend fun details(batchId: Long): BatchDetails? = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.batch(batchId)?.let(::loadDetails)
        }
    }

    suspend fun createFromRecipe(
        recipe: RecipeTemplate,
        title: String,
        kind: BatchKind,
        volume: Double,
    ): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val startedAt = clock()
            val dueDates = BatchRules.templateDueDates(startedAt, recipe.steps, zone)
            val steps = recipe.steps.mapIndexed { index, step ->
                BatchStep(
                    id = 0,
                    batchId = 0,
                    title = step.title,
                    dueAt = dueDates[index],
                    wikiId = step.wikiId,
                    position = index,
                )
            }
            insertBatch(
                title = title,
                kind = kind,
                volume = volume,
                startedAt = startedAt,
                recipeId = recipe.id,
                steps = steps,
            )
        }
    }

    suspend fun createBlank(
        title: String,
        kind: BatchKind,
        volume: Double,
    ): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            insertBatch(
                title = title,
                kind = kind,
                volume = volume,
                startedAt = clock(),
                recipeId = null,
                steps = emptyList(),
            )
        }
    }

    /** Копия партии с новыми датами. Замеры и журнал остаются только в исходной. */
    suspend fun repeat(batchId: Long): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = requireNotNull(database.batch(batchId)) { "Партия не найдена" }
            val sourceSteps = database.steps(batchId)
            val startedAt = clock()
            val dueDates = BatchRules.repeatDueDates(
                sourceStartedAt = source.startedAt,
                sourceDueDates = sourceSteps.map { it.dueAt },
                targetStartedAt = startedAt,
                zone = zone,
            )
            val steps = sourceSteps.mapIndexed { index, step ->
                BatchStep(
                    id = 0,
                    batchId = 0,
                    title = step.title,
                    dueAt = dueDates[index],
                    wikiId = step.wikiId,
                    position = index,
                )
            }
            insertBatch(
                title = source.title,
                kind = source.kind,
                volume = source.volume,
                startedAt = startedAt,
                recipeId = source.recipeId,
                steps = steps,
            )
        }
    }

    suspend fun setStepDone(stepId: Long, batchId: Long, done: Boolean) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val now = clock()
                database.setStepDone(
                    stepId = stepId,
                    batchId = batchId,
                    doneAt = if (done) now else null,
                    now = now,
                )
            }
        }

    suspend fun addStep(batchId: Long, title: String, dueAt: Long) =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim()
            require(cleanTitle.isNotEmpty()) { "Название шага не может быть пустым" }
            require(dueAt > 0) { "Некорректный срок шага" }
            mutex.withLock {
                val now = clock()
                database.addStep(
                    BatchStep(
                        id = 0,
                        batchId = batchId,
                        title = cleanTitle,
                        dueAt = dueAt,
                    ),
                    now,
                )
            }
        }

    suspend fun addMeasurement(
        batchId: Long,
        gravity: Double,
        temp: Double?,
        note: String?,
    ) = withContext(Dispatchers.IO) {
        require(gravity in 0.800..1.300) { "Плотность должна быть от 0,800 до 1,300" }
        require(temp == null || temp in -10.0..60.0) { "Температура должна быть от −10 до 60 °C" }
        mutex.withLock {
            val now = clock()
            val existing = database.measurements(batchId)
            val first = existing.minByOrNull { it.at }
            database.addMeasurement(
                Measurement(
                    id = 0,
                    batchId = batchId,
                    at = now,
                    gravity = gravity,
                    temp = temp,
                    abvCalc = first?.let { BatchRules.abv(it.gravity, gravity) } ?: 0.0,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
    }

    suspend fun addJournal(
        batchId: Long,
        kind: JournalKind,
        text: String,
    ) = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Текст записи не может быть пустым" }
        mutex.withLock {
            database.addJournal(
                JournalEntry(
                    id = 0,
                    batchId = batchId,
                    at = clock(),
                    kind = kind,
                    text = cleanText,
                ),
            )
        }
    }

    suspend fun setStatus(batchId: Long, status: BatchStatus) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                database.updateBatchStatus(batchId, status, clock())
            }
        }

    suspend fun updateNotes(batchId: Long, notes: String?) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                database.updateBatchNotes(
                    batchId,
                    notes?.trim()?.takeIf { it.isNotEmpty() },
                    clock(),
                )
            }
        }

    suspend fun dueReminders(): List<DueReminder> = withContext(Dispatchers.IO) {
        mutex.withLock { database.dueReminders(clock()) }
    }

    private fun insertBatch(
        title: String,
        kind: BatchKind,
        volume: Double,
        startedAt: Long,
        recipeId: String?,
        steps: List<BatchStep>,
    ): Long {
        require(title.isNotBlank()) { "Название партии не может быть пустым" }
        require(volume > 0) { "Объём должен быть больше нуля" }
        val now = clock()
        val stage = steps.minByOrNull { it.dueAt }?.title.orEmpty()
        return database.insertBatch(
            Batch(
                id = 0,
                title = title.trim(),
                kind = kind,
                volume = volume,
                startedAt = startedAt,
                stage = stage,
                recipeId = recipeId,
                status = BatchStatus.Active,
                createdAt = now,
                updatedAt = now,
            ),
            steps,
        )
    }

    private fun loadDetails(batch: Batch): BatchDetails = BatchDetails(
        batch = batch,
        steps = database.steps(batch.id),
        measurements = database.measurements(batch.id),
        journal = database.journal(batch.id),
    )
}
