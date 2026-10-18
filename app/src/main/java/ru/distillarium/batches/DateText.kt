package ru.distillarium.batches

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Даты партии в коротком русском формате, одинаковом в UI и уведомлениях. */
object DateText {
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
    private val dateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

    fun shortDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate())

    fun dateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(zone))
}
