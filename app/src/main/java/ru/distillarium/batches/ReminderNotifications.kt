package ru.distillarium.batches

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.distillarium.MainActivity
import ru.distillarium.R

/** Локальные напоминания: канал, разрешение и показ без сервера. */
object ReminderNotifications {
    const val CHANNEL_ID = "batch-reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // canPost() проверяет разрешение, notify() ещё и обёрнут.
    fun show(context: Context, reminders: List<DueReminder>) {
        if (reminders.isEmpty() || !canPost(context)) return
        createChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        reminders.take(MAX_NOTIFICATIONS).forEach { reminder ->
            val title = context.getString(R.string.reminder_title, reminder.batchTitle)
            val text = context.getString(
                R.string.reminder_text,
                reminder.step.title,
                DateText.shortDate(reminder.step.dueAt),
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_calc_bubble_chart)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching {
                manager.notify(reminder.step.id.toInt(), notification)
            }
        }
    }

    private const val MAX_NOTIFICATIONS = 5
}
