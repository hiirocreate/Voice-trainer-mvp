package com.example.voicetrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

// =========================================================================
// MainActivity
// =========================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VoiceTrainingApp()
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
fun VoiceTrainingApp() {
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
                Screen.DIAGNOSIS -> DiagnosisScreen()
                Screen.DASHBOARD -> DashboardScreen()
                Screen.SCHEDULE -> ScheduleScreen()
            }
        }
    }
}

// =========================================================================
// ① 診断画面（アンケート＋擬似録音分析デモ）
// =========================================================================
@Composable
fun DiagnosisScreen() {
    val purposes = listOf("音域を広げたい", "声量・響きを改善したい", "音痴を改善したい", "プロ・オーディション対策")
    val environments = listOf("自宅（防音なし）", "自宅（防音あり）", "スタジオ", "車内・屋外")

    var selectedPurpose by remember { mutableStateOf(purposes[0]) }
    var selectedEnvironment by remember { mutableStateOf(environments[0]) }

    var isRecording by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(5) }
    var showResult by remember { mutableStateOf(false) }
    var pitchStability by remember { mutableStateOf(0) }
    var estimatedRangeLow by remember { mutableStateOf("") }
    var estimatedRangeHigh by remember { mutableStateOf("") }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            showResult = false
            for (i in 5 downTo 1) {
                countdown = i
                delay(1000)
            }
            // --- 擬似的な音声分析結果を生成（デモ） ---
            pitchStability = (70..98).random()
            val ranges = listOf("A2" to "E4", "C3" to "G4", "G2" to "C4", "D3" to "A4")
            val result = ranges.random()
            estimatedRangeLow = result.first
            estimatedRangeHigh = result.second
            isRecording = false
            showResult = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("ボイス診断", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("① 目的を選んでください", fontWeight = FontWeight.Bold)
        purposes.forEach { purpose ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedPurpose = purpose }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedPurpose == purpose, onClick = { selectedPurpose = purpose })
                Text(purpose)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("② 練習環境を選んでください", fontWeight = FontWeight.Bold)
        environments.forEach { environment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedEnvironment = environment }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedEnvironment == environment, onClick = { selectedEnvironment = environment })
                Text(environment)
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE0E0E0))
        )
        Spacer(Modifier.height(24.dp))

        Text("③ 音声分析デモ（5秒間）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "「あー」と5秒間発声してみましょう（デモ：実際の録音・音声解析は行われません）",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { if (!isRecording) isRecording = true },
                    enabled = !isRecording,
                    shape = CircleShape,
                    modifier = Modifier.size(96.dp)
                ) {
                    if (isRecording) {
                        Text("$countdown", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Mic, contentDescription = "録音開始", modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (isRecording) "分析中..." else "タップで開始", fontSize = 13.sp)
            }
        }

        if (showResult) {
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("分析結果", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("ピッチ安定度: $pitchStability%")
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = pitchStability / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("推定音域: $estimatedRangeLow 〜 $estimatedRangeHigh")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "あなたの目的（$selectedPurpose）に合わせて、次のホーム画面でおすすめメニューを確認しましょう。",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// =========================================================================
// ② メインダッシュボード
// =========================================================================
enum class VoiceMode(val label: String) {
    BREATH("呼吸"),
    CHEST("地声"),
    HEAD("裏声")
}

data class VoiceModeInfo(
    val title: String,
    val anatomy: String,
    val onomatopoeia: String
)

val voiceModeInfoMap = mapOf(
    VoiceMode.BREATH to VoiceModeInfo(
        title = "呼吸",
        anatomy = "声帯（左右のひだ）が開き、声門から空気が自由に通過している状態です。この呼吸筋（横隔膜）のコントロールが、安定した発声の土台になります。",
        onomatopoeia = "「スーーッ」と、風がそのまま通り抜けていくようなイメージ"
    ),
    VoiceMode.CHEST to VoiceModeInfo(
        title = "地声（チェストボイス）",
        anatomy = "声帯全体（筋肉部分を含む）がしっかり閉じ、厚みを持った状態で規則的に振動します。振動数が少なく振幅が大きいため、太く力強い響きになります。",
        onomatopoeia = "「ガッ」「ワーン」と、地面から突き上げるような芯のある響き"
    ),
    VoiceMode.HEAD to VoiceModeInfo(
        title = "裏声（ヘッドボイス／ファルセット）",
        anatomy = "声帯の縁（靭帯部分）のみが薄く伸展し、部分的に振動します。振動数が多く振幅が小さいため、息が混じった軽く高い響きになります。",
        onomatopoeia = "「ヒュー」「フワッ」と、頭のてっぺんに抜けていくような軽い響き"
    )
)

@Composable
fun DashboardScreen() {
    var voiceMode by remember { mutableStateOf(VoiceMode.CHEST) }
    val info = voiceModeInfoMap.getValue(voiceMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("マイボイス・ダッシュボード", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            VoiceMode.values().forEach { mode ->
                val selected = voiceMode == mode
                Button(
                    onClick = { voiceMode = mode },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(mode.label)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            VocalCordCanvas(mode = voiceMode, modifier = Modifier.padding(12.dp))
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(info.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Text("【解剖学的な解説】", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(info.anatomy, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("【感覚的なイメージ（オノマトペ）】", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(info.onomatopoeia, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("自宅用省エネメニュー", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        ExerciseTimerCard("リップロール", "唇を軽く閉じ「ブルルル」と振動させながら音階を上下させます。", 60)
        ExerciseTimerCard("エッジボイス", "「ぎー」という軽く掠れた声を出し、声帯の閉鎖を意識します。", 45)
        ExerciseTimerCard("ハミング", "口を閉じたまま「んー」と鼻腔に響かせるように発声します。", 60)

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 声帯の2D図解アニメーション。
 * mode によって声帯間の隙間（呼吸時は広い／地声・裏声は閉じる）と、
 * 振動の速さ・振幅を変化させる。
 */
@Composable
fun VocalCordCanvas(mode: VoiceMode, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "vibration")
    val vibration by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mode == VoiceMode.HEAD) 90 else 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vibrationValue"
    )

    val gap by animateFloatAsState(
        targetValue = when (mode) {
            VoiceMode.BREATH -> 70f
            VoiceMode.CHEST -> 6f
            VoiceMode.HEAD -> 16f
        },
        animationSpec = tween(500),
        label = "glottalGap"
    )

    val amplitude = when (mode) {
        VoiceMode.BREATH -> 0f
        VoiceMode.CHEST -> 10f
        VoiceMode.HEAD -> 4f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val centerY = size.height / 2f
        val centerX = size.width / 2f
        val cordLength = size.width * 0.7f
        val cordThickness = 22.dp.toPx()
        val wiggle = (vibration - 0.5f) * 2f * amplitude

        val upperCordY = centerY - gap / 2f - wiggle
        val lowerCordY = centerY + gap / 2f + wiggle

        // 気道の中心線（呼吸時のみ、空気の流れを表現）
        if (mode == VoiceMode.BREATH) {
            drawLine(
                color = Color(0xFF64B5F6),
                start = Offset(centerX, size.height - 8f),
                end = Offset(centerX, 8f),
                strokeWidth = 6f
            )
        }

        // 上側の声帯
        drawRoundRect(
            color = Color(0xFFE57373),
            topLeft = Offset(centerX - cordLength / 2f, upperCordY - cordThickness / 2f),
            size = Size(cordLength, cordThickness),
            cornerRadius = CornerRadius(cordThickness / 2f, cordThickness / 2f)
        )

        // 下側の声帯
        drawRoundRect(
            color = Color(0xFFEF9A9A),
            topLeft = Offset(centerX - cordLength / 2f, lowerCordY - cordThickness / 2f),
            size = Size(cordLength, cordThickness),
            cornerRadius = CornerRadius(cordThickness / 2f, cordThickness / 2f)
        )
    }
}

@Composable
fun ExerciseTimerCard(title: String, description: String, totalSeconds: Int) {
    var remaining by remember { mutableStateOf(totalSeconds) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
            }
            isRunning = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${remaining}s", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Button(onClick = {
                    if (!isRunning) {
                        if (remaining == 0) remaining = totalSeconds
                        isRunning = true
                    } else {
                        isRunning = false
                    }
                }) {
                    Text(
                        when {
                            isRunning -> "一時停止"
                            remaining == totalSeconds -> "開始"
                            else -> "再開"
                        }
                    )
                }
            }
        }
    }
}

// =========================================================================
// ③ スケジュール管理画面
// =========================================================================
@Composable
fun ScheduleScreen() {
    val days = listOf("月", "火", "水", "木", "金", "土", "日")
    val weeklyToggles = remember { mutableStateListOf(true, true, false, true, true, false, false) }

    var reminderHour by remember { mutableStateOf(20) }
    var reminderMinute by remember { mutableStateOf(0) }

    val calendar = remember { Calendar.getInstance() }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = calendar.get(Calendar.DAY_OF_MONTH)

    val practiceStamps = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("スケジュール管理", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        Text("週間練習スケジュール", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { index, day ->
                val isOn = weeklyToggles[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isOn) Color(0xFF6750A4) else Color(0xFFE0E0E0))
                            .clickable { weeklyToggles[index] = !weeklyToggles[index] },
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
        Text("毎日のリマインダー時間（モック）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                reminderMinute -= 10
                if (reminderMinute < 0) {
                    reminderMinute += 60
                    reminderHour = (reminderHour + 23) % 24
                }
            }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "時間を早める")
            }
            Text(
                text = String.format("%02d:%02d", reminderHour, reminderMinute),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            IconButton(onClick = {
                reminderMinute += 10
                if (reminderMinute >= 60) {
                    reminderMinute -= 60
                    reminderHour = (reminderHour + 1) % 24
                }
            }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "時間を遅らせる")
            }
            Spacer(Modifier.width(8.dp))
            Text("に通知（デモ設定）", fontSize = 13.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(28.dp))
        Text("習慣化カレンダー（タップで練習実績スタンプ）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            items(daysInMonth) { index ->
                val day = index + 1
                val stamped = practiceStamps[day] == true
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (day == today) Color(0xFFEDE7F6) else Color(0xFFF5F5F5))
                        .border(
                            width = if (day == today) 2.dp else 0.dp,
                            color = Color(0xFF6750A4),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { practiceStamps[day] = !stamped },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$day", fontSize = 11.sp)
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
