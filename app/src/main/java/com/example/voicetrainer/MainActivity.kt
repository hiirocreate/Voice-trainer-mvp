package com.example.voicetrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// =========================================================================
// MainActivity
// =========================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通知チャンネルはアプリ起動時に一度だけ作成しておく（Android 8.0以降で必須）。
        ReminderReceiver.createNotificationChannel(this)

        val preferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            MaterialTheme {
                VoiceTrainingApp(preferencesRepository = preferencesRepository)
            }
        }
    }
}

// =========================================================================
// 画面遷移（状態管理）
// =========================================================================
enum class Screen(val label: String) {
    DIAGNOSIS("診断"),
    DASHBOARD("ホーム"),
    SCHEDULE("スケジュール")
}

@Composable
fun VoiceTrainingApp(preferencesRepository: UserPreferencesRepository) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.DIAGNOSIS,
                    onClick = { currentScreen = Screen.DIAGNOSIS },
                    icon = { Icon(Icons.Default.Mic, contentDescription = Screen.DIAGNOSIS.label) },
                    label = { Text(Screen.DIAGNOSIS.label) }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.DASHBOARD,
                    onClick = { currentScreen = Screen.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, contentDescription = Screen.DASHBOARD.label) },
                    label = { Text(Screen.DASHBOARD.label) }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.SCHEDULE,
                    onClick = { currentScreen = Screen.SCHEDULE },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = Screen.SCHEDULE.label) },
                    label = { Text(Screen.SCHEDULE.label) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                Screen.DIAGNOSIS -> DiagnosisScreen(preferencesRepository)
                Screen.DASHBOARD -> DashboardScreen(preferencesRepository)
                Screen.SCHEDULE -> ScheduleScreen(preferencesRepository)
            }
        }
    }
}
