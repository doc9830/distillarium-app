package ru.distillarium.content

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import ru.distillarium.DistillariumApp
import ru.distillarium.core.content.ContentException

/** Фоновая проверка раз в сутки, только по Wi-Fi (unmetered). */
object ContentUpdateScheduler {
    private const val WORK_NAME = "content-update"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<ContentUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class ContentUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DistillariumApp ?: return Result.success()
        return try {
            app.container.contentRepository.ensureInstalled()
            app.container.contentRepository.checkForUpdates()
            Result.success()
        } catch (error: IOException) {
            Log.w(TAG, "Фоновое обновление: сеть недоступна", error)
            Result.retry()
        } catch (error: ContentException) {
            Log.w(TAG, "Фоновое обновление отклонено: ${error.message}")
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Фоновое обновление не удалось", error)
            Result.success()
        }
    }

    private companion object {
        const val TAG = "ContentUpdateWorker"
    }
}
