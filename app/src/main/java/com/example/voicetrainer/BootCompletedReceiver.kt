package com.example.voicetrainer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 端末再起動時、AlarmManager にセットしていたアラームは消えてしまうため、
 * リマインダーが有効になっていれば再スケジュールする。
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repository = UserPreferencesRepository(appContext)
                val enabled = repository.reminderEnabledFlow.first()
                if (enabled) {
                    val hour = repository.reminderHourFlow.first()
                    val minute = repository.reminderMinuteFlow.first()
                    ReminderScheduler.schedule(appContext, hour, minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
