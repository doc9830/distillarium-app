package ru.distillarium.batches

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.distillarium.recipes.RecipeStep

class BatchRulesTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun templateDueDatesCountEachStepFromPreviousDay() {
        val startedAt = date("2026-10-18")
        val steps = listOf(
            RecipeStep(title = "Подготовить", afterDays = 0),
            RecipeStep(title = "Проверить", afterDays = 1),
            RecipeStep(title = "Снять с осадка", afterDays = 7),
        )

        assertEquals(
            listOf(
                due("2026-10-18"),
                due("2026-10-19"),
                due("2026-10-26"),
            ),
            BatchRules.templateDueDates(startedAt, steps, zone),
        )
    }

    @Test
    fun repeatDueDatesKeepIntervalsButMoveToNewStart() {
        val sourceStartedAt = date("2026-09-01")
        val sourceDueDates = listOf(
            due("2026-09-01"),
            due("2026-09-03"),
            due("2026-09-10"),
        )

        assertEquals(
            listOf(
                due("2026-10-18"),
                due("2026-10-20"),
                due("2026-10-27"),
            ),
            BatchRules.repeatDueDates(
                sourceStartedAt = sourceStartedAt,
                sourceDueDates = sourceDueDates,
                targetStartedAt = date("2026-10-18"),
                zone = zone,
            ),
        )
    }

    @Test
    fun abvNeedsTwoMeasurementsAndIgnoresOrder() {
        assertNull(BatchRules.abvFrom(listOf(measurement("2026-10-18", 1.050))))

        val abv = BatchRules.abvFrom(
            listOf(
                measurement("2026-10-25", 1.010),
                measurement("2026-10-18", 1.050),
            ),
        )

        assertEquals(5.25, abv!!, 1e-9)
        assertEquals(5.25, BatchRules.abv(1.050, 1.010), 1e-9)
        assertEquals(0.0, BatchRules.abv(1.000, 1.010), 1e-9)
    }

    @Test
    fun activeDayAndProgressAreCalculatedFromDatesAndSteps() {
        val startedAt = date("2026-10-18")
        val details = BatchDetails(
            batch = Batch(
                id = 1,
                title = "Брага",
                kind = BatchKind.Distillate,
                volume = 20.0,
                startedAt = startedAt,
            ),
            steps = listOf(
                BatchStep(1, 1, "Первый", due("2026-10-18"), doneAt = startedAt, position = 0),
                BatchStep(2, 1, "Второй", due("2026-10-19"), doneAt = startedAt, position = 1),
                BatchStep(3, 1, "Третий", due("2026-10-25"), position = 2),
            ),
            measurements = emptyList(),
            journal = emptyList(),
        )

        assertEquals(3, BatchRules.activeDay(startedAt, date("2026-10-20"), zone))
        assertEquals(2, details.doneSteps)
        assertEquals(3, details.totalSteps)
        assertEquals(66, BatchRules.progressPercent(details))
        assertEquals("Второй", details.steps[1].title)
        assertEquals("Третий", details.nextStep()?.title)
    }

    @Test
    fun reminderDelayTargetsNextNineOClock() {
        val beforeNine = LocalDate.parse("2026-10-18").atTime(8, 59).atZone(zone).toInstant().toEpochMilli()
        val afterNine = LocalDate.parse("2026-10-18").atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            due("2026-10-18") - beforeNine,
            BatchReminderScheduler.millisUntilNextRun(beforeNine, zone),
        )
        assertEquals(
            due("2026-10-19") - afterNine,
            BatchReminderScheduler.millisUntilNextRun(afterNine, zone),
        )
    }

    private fun date(value: String): Long =
        LocalDate.parse(value).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun due(value: String): Long =
        LocalDate.parse(value).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun measurement(date: String, gravity: Double): Measurement = Measurement(
        id = 0,
        batchId = 1,
        at = date(date),
        gravity = gravity,
        abvCalc = 0.0,
    )
}
