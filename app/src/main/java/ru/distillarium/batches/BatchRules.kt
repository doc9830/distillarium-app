package ru.distillarium.batches

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import ru.distillarium.recipes.RecipeStep
import kotlin.math.max

/**
 * Чистые правила партии: сроки, прогресс и крепость.
 * Здесь нет Android и базы — поэтому всё покрывается обычными unit-тестами.
 */
object BatchRules {
    /** Срок шага ставится на 9:00 локального дня: напоминание не будит ночью. */
    private const val DUE_HOUR = 9

    fun localDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun dueAt(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(DUE_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    /**
     * Сроки из шаблона: каждый шаг отсчитывается от предыдущего, как написано
     * в docs/06. День постановки — день первого шага.
     */
    fun templateDueDates(
        startedAt: Long,
        steps: List<RecipeStep>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        var date = localDate(startedAt, zone)
        return steps.map { step ->
            date = date.plusDays(step.afterDays.toLong())
            dueAt(date, zone)
        }
    }

    /**
     * Повтор партии: сохраняем интервалы между шагами исходной партии,
     * но привязываем их к сегодняшней дате.
     */
    fun repeatDueDates(
        sourceStartedAt: Long,
        sourceDueDates: List<Long>,
        targetStartedAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val sourceDay = localDate(sourceStartedAt, zone)
        val targetDay = localDate(targetStartedAt, zone)
        return sourceDueDates.map { due ->
            val offset = ChronoUnit.DAYS.between(sourceDay, localDate(due, zone))
            dueAt(targetDay.plusDays(offset), zone)
        }
    }

    fun abv(initialGravity: Double, finalGravity: Double): Double =
        max(0.0, (initialGravity - finalGravity) * 131.25)

    /**
     * Крепость по первому и последнему замеру. Одного замера недостаточно:
     * иначе приложение показывало бы 0 % как факт.
     */
    fun abvFrom(measurements: List<Measurement>): Double? {
        if (measurements.size < 2) return null
        val ordered = measurements.sortedBy { it.at }
        return abv(ordered.first().gravity, ordered.last().gravity)
    }

    fun isOverdue(step: BatchStep, now: Long): Boolean =
        !step.isDone && step.dueAt < now

    fun activeDay(startedAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = ChronoUnit.DAYS.between(localDate(startedAt, zone), localDate(now, zone))
        return (days + 1).toInt().coerceAtLeast(1)
    }

    fun progressPercent(details: BatchDetails): Int {
        if (details.totalSteps == 0) return 0
        return (details.doneSteps * 100 / details.totalSteps).coerceIn(0, 100)
    }
}
