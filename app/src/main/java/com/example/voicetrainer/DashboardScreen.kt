package com.example.voicetrainer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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
fun DashboardScreen(preferencesRepository: UserPreferencesRepository) {
    var voiceMode by remember { mutableStateOf(VoiceMode.CHEST) }
    val info = voiceModeInfoMap.getValue(voiceMode)

    val lastDiagnosis by preferencesRepository.lastDiagnosisFlow.collectAsState(
        initial = LastDiagnosis(null, null, null, null)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("マイボイス・ダッシュボード", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (lastDiagnosis.stabilityNote != null || lastDiagnosis.rangeLowNote != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("前回の診断結果", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (lastDiagnosis.stabilityNote != null) {
                        Text(
                            "ピッチ安定度: ${lastDiagnosis.stabilityPercent}%（検出音: ${lastDiagnosis.stabilityNote}）",
                            fontSize = 13.sp
                        )
                    }
                    if (lastDiagnosis.rangeLowNote != null && lastDiagnosis.rangeHighNote != null) {
                        Text(
                            "推定音域: ${lastDiagnosis.rangeLowNote} 〜 ${lastDiagnosis.rangeHighNote}",
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

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

        if (mode == VoiceMode.BREATH) {
            drawLine(
                color = Color(0xFF64B5F6),
                start = Offset(centerX, size.height - 8f),
                end = Offset(centerX, 8f),
                strokeWidth = 6f
            )
        }

        drawRoundRect(
            color = Color(0xFFE57373),
            topLeft = Offset(centerX - cordLength / 2f, upperCordY - cordThickness / 2f),
            size = Size(cordLength, cordThickness),
            cornerRadius = CornerRadius(cordThickness / 2f, cordThickness / 2f)
        )

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
