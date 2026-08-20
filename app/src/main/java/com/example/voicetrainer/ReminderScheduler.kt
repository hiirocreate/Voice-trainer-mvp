package com.example.voicetrainer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 毎日決まった時刻に練習リマインダー通知を発火させるための、実際の [AlarmManager] スケジューラ。
 *
 * `setExactAndAllowWhileIdle` ではなく `setAndAllowWhileIdle`（非厳密アラーム）を使用している。
 * これにより、Android 12以降で必要になる「アラームとリマインダー」特別許可
 * （SCHEDULE_EXACT_ALARM）をユーザーに求めることなく動作する。
 * 端末の省電力機能により、設定時刻から数分程度ずれて発火する場合がある。
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = nextTriggerTimeMillis(hour, minute)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, buildPendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerTimeMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }
}
