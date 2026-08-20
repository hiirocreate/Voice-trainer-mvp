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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "voice_trainer_prefs")

/** 直近の診断・測定結果（ダッシュボード画面の表示や、練習メニューのおすすめ判定に使うため永続化する） */
data class LastDiagnosis(
    val purpose: String?,
    val environment: String?,
    val stabilityNote: String?,
    val stabilityPercent: Int?,
    val rangeLowNote: String?,
    val rangeHighNote: String?
)

/** ピッチ安定度測定の履歴1件分（トレンド確認に使う） */
data class DiagnosisSnapshot(
    val timestampMillis: Long,
    val stabilityPercent: Int
)

/**
 * アプリの設定・記録をすべて端末内（DataStore）に永続化するリポジトリ。
 * 週間スケジュールのON/OFF、リマインダー時刻、習慣化カレンダーの練習記録、
 * 直近の診断結果、そして進行中のパーソナルトレーニングプログラム（1〜2週間分の計画・
 * 現在何日目のどの種目まで終えたか）は、すべてアプリを再起動しても、
 * 画面（タブ）を行き来しても消えずに残る。
 */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val WEEKLY_TOGGLES = stringPreferencesKey("weekly_toggles")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val PRACTICE_STAMPS = stringSetPreferencesKey("practice_stamps")
        val LAST_PURPOSE = stringPreferencesKey("last_purpose")
        val LAST_ENVIRONMENT = stringPreferencesKey("last_environment")
        val LAST_STABILITY_NOTE = stringPreferencesKey("last_stability_note")
        val LAST_STABILITY_PERCENT = intPreferencesKey("last_stability_percent")
        val LAST_RANGE_LOW = stringPreferencesKey("last_range_low")
        val LAST_RANGE_HIGH = stringPreferencesKey("last_range_high")
        val STABILITY_HISTORY = stringPreferencesKey("stability_history")
        val ACTIVE_PROGRAM = stringPreferencesKey("active_program")
        val AWAITING_GOAL_SELECTION = booleanPreferencesKey("awaiting_goal_selection")
        val AWAITING_COMPARISON = booleanPreferencesKey("awaiting_comparison")
    }

    companion object {
        val DEFAULT_WEEKLY_TOGGLES = listOf(true, true, false, true, true, false, false)
        const val DEFAULT_REMINDER_HOUR = 20
        const val DEFAULT_REMINDER_MINUTE = 0
        private const val MAX_HISTORY_SIZE = 30

        private fun encodeToggles(toggles: List<Boolean>): String =
            toggles.joinToString(",") { if (it) "1" else "0" }

        private fun decodeToggles(raw: String?): List<Boolean> =
            if (raw.isNullOrEmpty()) DEFAULT_WEEKLY_TOGGLES else raw.split(",").map { it == "1" }

        private fun encodeHistory(history: List<DiagnosisSnapshot>): String {
            val array = JSONArray()
            history.forEach { snapshot ->
                val obj = JSONObject()
                obj.put("t", snapshot.timestampMillis)
                obj.put("s", snapshot.stabilityPercent)
                array.put(obj)
            }
            return array.toString()
        }

        private fun decodeHistory(raw: String?): List<DiagnosisSnapshot> {
            if (raw.isNullOrEmpty()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    DiagnosisSnapshot(obj.getLong("t"), obj.getInt("s"))
                }
            } catch (e: JSONException) {
                // 破損したデータが保存されていた場合は履歴なしとして扱う
                emptyList()
            }
        }

        private fun encodeProgram(program: TrainingProgram): String {
            val obj = JSONObject()
            obj.put("goal", program.goal.name)
            obj.put("durationDays", program.durationDays)
            obj.put("startTimestampMillis", program.startTimestampMillis)
            obj.put("currentDayIndex", program.currentDayIndex)
            obj.put("currentExerciseIndex", program.currentExerciseIndex)
            obj.put("baselineStabilityPercent", program.baselineStabilityPercent)
            obj.put("baselineRangeLowNote", program.baselineRangeLowNote ?: JSONObject.NULL)
            obj.put("baselineRangeHighNote", program.baselineRangeHighNote ?: JSONObject.NULL)

            val daysArray = JSONArray()
            program.days.forEach { day ->
                val dayObj = JSONObject()
                dayObj.put("dayIndex", day.dayIndex)
                val itemsArray = JSONArray()
                day.items.forEach { item ->
                    val itemObj = JSONObject()
                    itemObj.put("exerciseId", item.exerciseId)
                    itemObj.put("title", item.title)
                    itemObj.put("description", item.description)
                    itemObj.put("seconds", item.seconds)
                    itemObj.put("reason", item.reason)
                    itemsArray.put(itemObj)
                }
                dayObj.put("items", itemsArray)
                daysArray.put(dayObj)
            }
            obj.put("days", daysArray)

            return obj.toString()
        }

        /** 破損データ・未知の enum 値などが保存されていた場合は null を返し、プログラムなし状態として扱う。 */
        private fun decodeProgram(raw: String?): TrainingProgram? {
            if (raw.isNullOrEmpty()) return null
            return try {
                val obj = JSONObject(raw)
                val goal = TrainingGoal.valueOf(obj.getString("goal"))

                val daysArray = obj.getJSONArray("days")
                val days = (0 until daysArray.length()).map { i ->
                    val dayObj = daysArray.getJSONObject(i)
                    val itemsArray = dayObj.getJSONArray("items")
                    val items = (0 until itemsArray.length()).map { j ->
                        val itemObj = itemsArray.getJSONObject(j)
                        TrainingPlanItem(
                            exerciseId = itemObj.getString("exerciseId"),
                            title = itemObj.getString("title"),
                            description = itemObj.getString("description"),
                            seconds = itemObj.getInt("seconds"),
                            reason = itemObj.getString("reason")
                        )
                    }
                    DayPlan(dayIndex = dayObj.getInt("dayIndex"), items = items)
                }

                TrainingProgram(
                    goal = goal,
                    durationDays = obj.getInt("durationDays"),
                    startTimestampMillis = obj.getLong("startTimestampMillis"),
                    days = days,
                    currentDayIndex = obj.getInt("currentDayIndex"),
                    currentExerciseIndex = obj.getInt("currentExerciseIndex"),
                    baselineStabilityPercent = obj.getInt("baselineStabilityPercent"),
                    baselineRangeLowNote = if (obj.isNull("baselineRangeLowNote")) null else obj.getString("baselineRangeLowNote"),
                    baselineRangeHighNote = if (obj.isNull("baselineRangeHighNote")) null else obj.getString("baselineRangeHighNote")
                )
            } catch (e: JSONException) {
                null
            } catch (e: IllegalArgumentException) {
                // TrainingGoal.valueOf に未知の値が渡された場合など
                null
            }
        }
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

    /** [dateKey] は "yyyy-MM-dd" 形式。記録があれば削除、なければ記録する（トグル）。カレンダー画面のタップ用。 */
    suspend fun togglePracticeStamp(dateKey: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.PRACTICE_STAMPS] ?: emptySet()).toMutableSet()
            if (!current.add(dateKey)) {
                current.remove(dateKey)
            }
            prefs[Keys.PRACTICE_STAMPS] = current
        }
    }

    /** [dateKey] は "yyyy-MM-dd" 形式。トグルせず、無条件に「練習した日」として記録する。プログラムの1日完了時に使用。 */
    suspend fun markPracticeStampDone(dateKey: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.PRACTICE_STAMPS] ?: emptySet()).toMutableSet()
            current.add(dateKey)
            prefs[Keys.PRACTICE_STAMPS] = current
        }
    }

    // --- 直近の診断・測定結果（目的・環境・ピッチ安定度・推定音域） -------------------
    val lastDiagnosisFlow: Flow<LastDiagnosis> = context.dataStore.data.map { prefs ->
        LastDiagnosis(
            purpose = prefs[Keys.LAST_PURPOSE],
            environment = prefs[Keys.LAST_ENVIRONMENT],
            stabilityNote = prefs[Keys.LAST_STABILITY_NOTE],
            stabilityPercent = prefs[Keys.LAST_STABILITY_PERCENT],
            rangeLowNote = prefs[Keys.LAST_RANGE_LOW],
            rangeHighNote = prefs[Keys.LAST_RANGE_HIGH]
        )
    }

    suspend fun savePurpose(purpose: String) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_PURPOSE] = purpose }
    }

    suspend fun saveEnvironment(environment: String) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_ENVIRONMENT] = environment }
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

    // --- ピッチ安定度の測定履歴（トレンド確認に使用） -------------
    val stabilityHistoryFlow: Flow<List<DiagnosisSnapshot>> = context.dataStore.data.map { prefs ->
        decodeHistory(prefs[Keys.STABILITY_HISTORY])
    }

    /** 測定のたびに履歴へ1件追記する。直近 [MAX_HISTORY_SIZE] 件だけを保持する。 */
    suspend fun appendStabilitySnapshot(timestampMillis: Long, stabilityPercent: Int) {
        context.dataStore.edit { prefs ->
            val current = decodeHistory(prefs[Keys.STABILITY_HISTORY]).toMutableList()
            current.add(DiagnosisSnapshot(timestampMillis, stabilityPercent))
            val trimmed = if (current.size > MAX_HISTORY_SIZE) current.takeLast(MAX_HISTORY_SIZE) else current
            prefs[Keys.STABILITY_HISTORY] = encodeHistory(trimmed)
        }
    }

    // --- パーソナルトレーニングプログラム -------------------------------------
    // 測定 → 目標選択 → 1〜2週間のメニュー計画・スケジューリング → トレーニング完了ボタン →
    // 再測定 → 前回との比較 → 再度目標選択 → … という多日プログラムの進行状態を永続化する。
    // これにより、ダッシュボード画面がタブ切り替えで一度破棄・再構築されても、
    // また端末を再起動しても、進行中のプログラムが失われない。

    /** 進行中のトレーニングプログラム。存在しない場合は null。 */
    val activeProgramFlow: Flow<TrainingProgram?> = context.dataStore.data.map { prefs ->
        decodeProgram(prefs[Keys.ACTIVE_PROGRAM])
    }

    /** 測定は完了したが、まだ目標（どうなりたいか）を選んでいない状態かどうか。 */
    val awaitingGoalSelectionFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.AWAITING_GOAL_SELECTION] ?: false }

    /** 再測定は完了したが、まだ比較結果を確認していない状態かどうか。 */
    val awaitingComparisonFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.AWAITING_COMPARISON] ?: false }

    suspend fun saveActiveProgram(program: TrainingProgram) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_PROGRAM] = encodeProgram(program) }
    }

    suspend fun clearActiveProgram() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.ACTIVE_PROGRAM) }
    }

    suspend fun setAwaitingGoalSelection(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AWAITING_GOAL_SELECTION] = value }
    }

    suspend fun setAwaitingComparison(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AWAITING_COMPARISON] = value }
    }
}
