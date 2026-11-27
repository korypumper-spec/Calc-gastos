package com.mibolsillointeligente.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mibolsillointeligente.app.R
import com.mibolsillointeligente.app.data.local.AppDatabase
import com.mibolsillointeligente.app.domain.model.PaymentStatus
import com.mibolsillointeligente.app.settings.AppPreferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object ReminderNotificationHelper {
    const val CHANNEL_ID = "mi_bolsillo_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferencesDataStore(applicationContext).preferencesFlow.first()
        if (!prefs.remindersEnabled || !ReminderNotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        val db = AppDatabase.getInstance(applicationContext)
        val payments = db.scheduledPaymentDao().observeAll().first()
        val today = LocalDate.now()
        val upcomingOrOverdue = payments.count { payment ->
            payment.status != PaymentStatus.PAID.name &&
                payment.dueDateEpochDay <= today.plusDays(3).toEpochDay()
        }

        if (upcomingOrOverdue > 0) {
            // Privacidad: nunca mostrar importes ni descripciones financieras en notificaciones
            val notification = NotificationCompat.Builder(applicationContext, ReminderNotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Recordatorio de pagos")
                .setContentText("Tienes $upcomingOrOverdue compromiso(s) próximos o vencidos por revisar.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            if (ReminderNotificationHelper.hasNotificationPermission(applicationContext)) {
                NotificationManagerCompat.from(applicationContext).notify(1001, notification)
            }
        }
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "mi_bolsillo_daily_reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
