package com.example.voicetrainer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * ③ スケジュール管理画面。
 * ・週間練習スケジュール、リマインダー時刻、習慣化カレンダーはすべて
 *   [UserPreferencesRepository]（DataStore）に永続化され、アプリ再起動後も保持される。
 * ・リマインダーをONにすると、実際に [ReminderScheduler] 経由で AlarmManager に
 *   毎日の通知アラームがセットされる（Android 13+では通知権限も要求する）。
 */
@Composable
fun ScheduleScreen(preferencesRepository: UserPreferencesRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val days = listOf("月", "火", "水", "木", "金", "土", "日")
    val weeklyToggles by preferencesRepository.weeklyTogglesFlow.collectAsState(
        initial = UserPreferencesRepository.DEFAULT_WEEKLY_TOGGLES
    )
    val reminderHour by preferencesRepository.reminderHourFlow.collectAsState(
        initial = UserPreferencesRepository.DEFAULT_REMINDER_HOUR
    )
    val reminderMinute by preferencesRepository.reminderMinuteFlow.collectAsState(
        initial = UserPreferencesRepository.DEFAULT_REMINDER_MINUTE
    )
    val reminderEnabled by preferencesRepository.reminderEnabledFlow.collectAsState(initial = false)
    val practiceStamps by preferencesRepository.practiceStampsFlow.collectAsState(initial = emptySet())

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                preferencesRepository.setReminderEnabled(true)
                ReminderScheduler.schedule(context, reminderHour, reminderMinute)
            }
        }
    }

    fun enableReminder() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            coroutineScope.launch {
                preferencesRepository.setReminderEnabled(true)
                ReminderScheduler.schedule(context, reminderHour, reminderMinute)
            }
        }
    }

    fun disableReminder() {
        coroutineScope.launch {
            preferencesRepository.setReminderEnabled(false)
            ReminderScheduler.cancel(context)
        }
    }

    val yearMonth = remember { YearMonth.now() }
    val today = remember { LocalDate.now() }
    val daysInMonth = yearMonth.lengthOfMonth()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("スケジュール管理", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text("週間練習スケジュール", fontWeight = FontWeight.Bold)
        Text("オンにした曜日だけ、下のリマインダー通知が届きます。", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { index, day ->
                val isOn = weeklyToggles.getOrElse(index) { true }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isOn) Color(0xFF6750A4) else Color(0xFFE0E0E0))
                            .clickable {
                                coroutineScope.launch {
                                    preferencesRepository.setWeeklyToggle(index, !isOn)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOn) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "$day 曜日は練習日",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("毎日のリマインダー通知", fontWeight = FontWeight.Bold)
                Text(
                    if (reminderEnabled) {
                        "設定した時刻に通知します（端末の省電力設定により数分前後する場合があります）"
                    } else {
                        "オフになっています"
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = reminderEnabled,
                onCheckedChange = { checked -> if (checked) enableReminder() else disableReminder() }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = reminderEnabled,
                onClick = {
                    var newMinute = reminderMinute - 10
                    var newHour = reminderHour
                    if (newMinute < 0) {
                        newMinute += 60
                        newHour = (newHour + 23) % 24
                    }
                    coroutineScope.launch {
                        preferencesRepository.setReminderTime(newHour, newMinute)
                        if (reminderEnabled) ReminderScheduler.schedule(context, newHour, newMinute)
                    }
                }
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "時間を早める")
            }
            Text(
                text = String.format("%02d:%02d", reminderHour, reminderMinute),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            IconButton(
                enabled = reminderEnabled,
                onClick = {
                    var newMinute = reminderMinute + 10
                    var newHour = reminderHour
                    if (newMinute >= 60) {
                        newMinute -= 60
                        newHour = (newHour + 1) % 24
                    }
                    coroutineScope.launch {
                        preferencesRepository.setReminderTime(newHour, newMinute)
                        if (reminderEnabled) ReminderScheduler.schedule(context, newHour, newMinute)
                    }
                }
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "時間を遅らせる")
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("習慣化カレンダー（タップで練習実績を記録）", fontWeight = FontWeight.Bold)
        Text("記録は端末に保存され、アプリを再起動しても消えません。", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            items(daysInMonth) { index ->
                val date = yearMonth.atDay(index + 1)
                val dateKey = date.toString()
                val stamped = practiceStamps.contains(dateKey)
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (date == today) Color(0xFFEDE7F6) else Color(0xFFF5F5F5))
                        .border(
                            width = if (date == today) 2.dp else 0.dp,
                            color = Color(0xFF6750A4),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            coroutineScope.launch {
                                preferencesRepository.togglePracticeStamp(dateKey)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${date.dayOfMonth}", fontSize = 11.sp)
                        if (stamped) {
                            Text("♪", fontSize = 14.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
