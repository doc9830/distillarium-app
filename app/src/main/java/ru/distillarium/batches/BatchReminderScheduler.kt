package ru.distillarium.batches

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Планирует ежедневную проверку шагов на 9:00 по локальному времени. */
object BatchReminderScheduler {
    const val WORK_NAME = "batch-reminders"

    fun schedule(context: Context) {
        ReminderNotifications.createChannel(context)
        val request = PeriodicWorkRequestBuilder<BatchReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextRun(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun millisUntilNextRun(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val today = BatchRules.localDate(now, zone)
        var next = BatchRules.dueAt(today, zone)
        if (next <= now) next = BatchRules.dueAt(today.plusDays(1), zone)
        return next - now
    }
}
