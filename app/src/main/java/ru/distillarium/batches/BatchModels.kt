package ru.distillarium.batches

import ru.distillarium.calculators.CalcResult

/** Тип продукта. Хранится строкой, чтобы старые версии не ломали чтение. */
enum class BatchKind(val storage: String) {
    Distillate("distillate"),
    Wine("wine"),
    Beer("beer"),
    Other("other");

    companion object {
        fun fromStorage(value: String?): BatchKind =
            entries.firstOrNull { it.storage == value } ?: Other
    }
}

enum class BatchStatus(val storage: String) {
    Active("active"),
    Completed("completed"),
    Archived("archived");

    companion object {
        fun fromStorage(value: String?): BatchStatus =
            entries.firstOrNull { it.storage == value } ?: Active
    }
}

enum class JournalKind(val storage: String) {
    Note("note"),
    Addition("addition"),
    Racking("racking"),
    Tasting("tasting"),
    Calculation("calculation");

    companion object {
        fun fromStorage(value: String?): JournalKind =
            entries.firstOrNull { it.storage == value } ?: Note
    }
}

data class Batch(
    val id: Long,
    val title: String,
    val kind: BatchKind,
    val volume: Double,
    val startedAt: Long,
    val stage: String = "",
    val notes: String? = null,
    val recipeId: String? = null,
    val status: BatchStatus = BatchStatus.Active,
    val createdAt: Long = startedAt,
    val updatedAt: Long = startedAt,
)

data class BatchStep(
    val id: Long,
    val batchId: Long,
    val title: String,
    val dueAt: Long,
    val doneAt: Long? = null,
    val wikiId: String? = null,
    val position: Int = 0,
) {
    val isDone: Boolean get() = doneAt != null
}

data class Measurement(
    val id: Long,
    val batchId: Long,
    val at: Long,
    val gravity: Double,
    val temp: Double? = null,
    val abvCalc: Double,
    val note: String? = null,
)

data class JournalEntry(
    val id: Long,
    val batchId: Long,
    val at: Long,
    val kind: JournalKind,
    val text: String,
    val photoUri: String? = null,
)

/**
 * Расчёт, сохранённый пользователем. Поля калькулятора хранятся как введённые
 * строки, результат — уже посчитанные значения: формула может измениться,
 * а история останется прежней.
 */
data class SavedCalc(
    val id: Long,
    val batchId: Long?,
    val calcId: String,
    val inputs: Map<String, String>,
    val result: CalcResult?,
    val note: String? = null,
    val at: Long,
)

/** Полная карточка партии: всё, что нужно списку и экрану деталей. */
data class BatchDetails(
    val batch: Batch,
    val steps: List<BatchStep>,
    val measurements: List<Measurement>,
    val journal: List<JournalEntry>,
    val savedCalcs: List<SavedCalc> = emptyList(),
) {
    val doneSteps: Int get() = steps.count { it.isDone }
    val totalSteps: Int get() = steps.size
    val latestMeasurement: Measurement? get() = measurements.maxByOrNull { it.at }
    val calculatedAbv: Double? get() = BatchRules.abvFrom(measurements)

    fun nextStep(): BatchStep? = steps
        .filterNot { it.isDone }
        .minByOrNull { it.dueAt }
}
