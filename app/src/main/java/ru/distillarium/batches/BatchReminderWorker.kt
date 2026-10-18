package ru.distillarium.batches

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.distillarium.DistillariumApp

/** Раз в сутки собирает просроченные и сегодняшние шаги и показывает уведомления. */
class BatchReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DistillariumApp ?: return Result.success()
        return try {
            val reminders = app.container.batchRepository.dueReminders()
            ReminderNotifications.show(applicationContext, reminders)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
