package com.example.voicetrainer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [ReminderScheduler] がセットしたアラームが発火したときに呼ばれる BroadcastReceiver。
 * ・今日が週間スケジュールでON（練習日）になっている場合のみ通知を表示する
 * ・発火のたびに、翌日分のアラームを再スケジュールする（繰り返し通知の実現）
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = UserPreferencesRepository(appContext)
                val enabled = repository.reminderEnabledFlow.first()
                if (enabled) {
                    val weeklyToggles = repository.weeklyTogglesFlow.first()
                    val todayIndex = LocalDate.now().dayOfWeek.value - 1 // 月=0 ... 日=6
                    val isTodayPracticeDay = weeklyToggles.getOrElse(todayIndex) { true }

                    if (isTodayPracticeDay) {
                        showNotification(appContext)
                    }

                    val hour = repository.reminderHourFlow.first()
                    val minute = repository.reminderMinuteFlow.first()
                    ReminderScheduler.schedule(appContext, hour, minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context) {
        createNotificationChannel(context)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Android 13以降で通知権限が許可されていない場合は何もしない
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ボイストレーニングの時間です")
            .setContentText("今日の練習メニューをチェックしましょう ♪")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "voice_training_reminder"
        const val NOTIFICATION_ID = 2001

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "練習リマインダー",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "毎日の練習時間をお知らせします"
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }
    }
}
