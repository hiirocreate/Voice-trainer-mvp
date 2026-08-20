package com.example.voicetrainer

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "voice_trainer_prefs")

/** 直近の診断結果（ダッシュボード画面に表示するために永続化する） */
data class LastDiagnosis(
    val stabilityNote: String?,
    val stabilityPercent: Int?,
    val rangeLowNote: String?,
    val rangeHighNote: String?
)

/**
 * アプリの設定・記録をすべて端末内（DataStore）に永続化するリポジトリ。
 * 週間スケジュールのON/OFF、リマインダー時刻、習慣化カレンダーの練習記録、
 * 直近の診断結果は、すべてアプリを再起動しても消えずに残る。
 */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val WEEKLY_TOGGLES = stringPreferencesKey("weekly_toggles")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val PRACTICE_STAMPS = stringSetPreferencesKey("practice_stamps")
        val LAST_STABILITY_NOTE = stringPreferencesKey("last_stability_note")
        val LAST_STABILITY_PERCENT = intPreferencesKey("last_stability_percent")
        val LAST_RANGE_LOW = stringPreferencesKey("last_range_low")
        val LAST_RANGE_HIGH = stringPreferencesKey("last_range_high")
    }

    companion object {
        val DEFAULT_WEEKLY_TOGGLES = listOf(true, true, false, true, true, false, false)
        const val DEFAULT_REMINDER_HOUR = 20
        const val DEFAULT_REMINDER_MINUTE = 0

        private fun encodeToggles(toggles: List<Boolean>): String =
            toggles.joinToString(",") { if (it) "1" else "0" }

        private fun decodeToggles(raw: String?): List<Boolean> =
            if (raw.isNullOrEmpty()) DEFAULT_WEEKLY_TOGGLES else raw.split(",").map { it == "1" }
    }

    // --- 週間練習スケジュール -------------------------------------------------
    val weeklyTogglesFlow: Flow<List<Boolean>> = context.dataStore.data.map { prefs ->
        decodeToggles(prefs[Keys.WEEKLY_TOGGLES])
    }

    suspend fun setWeeklyToggle(index: Int, value: Boolean) {
        context.dataStore.edit { prefs ->
            val current = decodeToggles(prefs[Keys.WEEKLY_TOGGLES]).toMutableList()
            if (index in current.indices) {
                current[index] = value
                prefs[Keys.WEEKLY_TOGGLES] = encodeToggles(current)
            }
        }
    }

    // --- リマインダー -----------------------------------------------------
    val reminderHourFlow: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR }
    val reminderMinuteFlow: Flow<Int> = context.dataStore.data.map { it[Keys.REMINDER_MINUTE] ?: DEFAULT_REMINDER_MINUTE }
    val reminderEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.REMINDER_ENABLED] ?: false }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_HOUR] = hour
            prefs[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.REMINDER_ENABLED] = enabled }
    }

    // --- 習慣化カレンダー ---------------------------------------------------
    val practiceStampsFlow: Flow<Set<String>> = context.dataStore.data.map { it[Keys.PRACTICE_STAMPS] ?: emptySet() }

    /** [dateKey] は "yyyy-MM-dd" 形式。記録があれば削除、なければ記録する（トグル）。 */
    suspend fun togglePracticeStamp(dateKey: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.PRACTICE_STAMPS] ?: emptySet()).toMutableSet()
            if (!current.add(dateKey)) {
                current.remove(dateKey)
            }
            prefs[Keys.PRACTICE_STAMPS] = current
        }
    }

    // --- 直近の診断結果 -----------------------------------------------------
    val lastDiagnosisFlow: Flow<LastDiagnosis> = context.dataStore.data.map { prefs ->
        LastDiagnosis(
            stabilityNote = prefs[Keys.LAST_STABILITY_NOTE],
            stabilityPercent = prefs[Keys.LAST_STABILITY_PERCENT],
            rangeLowNote = prefs[Keys.LAST_RANGE_LOW],
            rangeHighNote = prefs[Keys.LAST_RANGE_HIGH]
        )
    }

    suspend fun saveStabilityResult(note: String, stabilityPercent: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_STABILITY_NOTE] = note
            prefs[Keys.LAST_STABILITY_PERCENT] = stabilityPercent
        }
    }

    suspend fun saveRangeResult(lowNote: String, highNote: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_RANGE_LOW] = lowNote
            prefs[Keys.LAST_RANGE_HIGH] = highNote
        }
    }
}
