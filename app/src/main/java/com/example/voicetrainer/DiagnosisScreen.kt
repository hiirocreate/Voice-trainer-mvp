package com.example.voicetrainer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TestType { STABILITY, RANGE }

/**
 * ① 診断画面。
 * ・目的／環境のアンケート
 * ・実際にマイクで録音し、その場で自己相関法によりピッチを解析する
 *   「ピッチ安定度テスト」「音域テスト」（[PitchAnalyzer] を使用）
 * 解析結果は [UserPreferencesRepository] に保存し、ダッシュボード画面にも表示される。
 */
@Composable
fun DiagnosisScreen(preferencesRepository: UserPreferencesRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pitchAnalyzer = remember { PitchAnalyzer() }

    val purposes = listOf("音域を広げたい", "声量・響きを改善したい", "音痴を改善したい", "プロ・オーディション対策")
    val environments = listOf("自宅（防音なし）", "自宅（防音あり）", "スタジオ", "車内・屋外")
    var selectedPurpose by remember { mutableStateOf(purposes[0]) }
    var selectedEnvironment by remember { mutableStateOf(environments[0]) }

    var micPermissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionDenied = !granted
    }

    var isRecording by remember { mutableStateOf(false) }
    var activeTest by remember { mutableStateOf<TestType?>(null) }
    var countdown by remember { mutableStateOf(0) }
    var liveNote by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var stabilityResult by remember { mutableStateOf<StabilityResult?>(null) }
    var rangeResult by remember { mutableStateOf<RangeResult?>(null) }

    fun runTest(testType: TestType) {
        if (isRecording) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        micPermissionDenied = false
        errorMessage = null
        activeTest = testType
        val durationSeconds = if (testType == TestType.STABILITY) 5 else 8

        // カウントダウン表示用（見た目のためだけの、実録音とは独立したコルーチン）
        coroutineScope.launch {
            for (i in durationSeconds downTo 1) {
                countdown = i
                delay(1000)
            }
        }

        // 実際のマイク録音とピッチ解析
        coroutineScope.launch {
            isRecording = true
            liveNote = "…"
            val session = pitchAnalyzer.record(durationSeconds * 1000L) { freq ->
                liveNote = freq?.let { PitchAnalyzer.frequencyToNoteName(it) } ?: "…"
            }
            isRecording = false
            activeTest = null
            countdown = 0

            when (testType) {
                TestType.STABILITY -> {
                    val result = computeStabilityResult(session)
                    if (result == null) {
                        errorMessage = "声を十分に検出できませんでした。マイクに向かって、はっきりと発声してみてください。"
                    } else {
                        stabilityResult = result
                        preferencesRepository.saveStabilityResult(result.averageNote, result.stabilityPercent)
                    }
                }
                TestType.RANGE -> {
                    val result = computeRangeResult(session)
                    if (result == null) {
                        errorMessage = "声を十分に検出できませんでした。マイクに向かって、はっきりと発声してみてください。"
                    } else {
                        rangeResult = result
                        preferencesRepository.saveRangeResult(result.lowNote, result.highNote)
                    }
                }
            }
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

        Text("③ ピッチ安定度テスト（実測・5秒間）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "同じ高さの声で「あー」と5秒間伸ばしてください。マイクで実際に録音し、その場で自己相関法によりピッチを解析します。",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { runTest(TestType.STABILITY) },
            enabled = !isRecording
        ) {
            Text(if (isRecording && activeTest == TestType.STABILITY) "録音中... ${countdown}秒" else "安定度テストを開始")
        }

        if (isRecording && activeTest == TestType.STABILITY) {
            Spacer(Modifier.height(8.dp))
            Text("検出中の音: $liveNote", fontSize = 13.sp)
        }

        stabilityResult?.let { r ->
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ピッチ安定度テスト結果", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("安定度: ${r.stabilityPercent}%（中心の半音以内に収まったフレームの割合）")
                    LinearProgressIndicator(
                        progress = r.stabilityPercent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .padding(vertical = 4.dp)
                    )
                    Text("検出音: ${r.averageNote}（約${"%.1f".format(r.averageFrequencyHz)}Hz）", fontSize = 13.sp)
                    Text(
                        "音声を検出できたフレームの割合: ${r.voicedFrameRatioPercent}%",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("④ 音域テスト（実測・8秒間）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "「あー」と言いながら、低い声から高い声までゆっくりスライドさせてください。実際に録音した音声から、検出できた最低音〜最高音を算出します。",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { runTest(TestType.RANGE) },
            enabled = !isRecording
        ) {
            Text(if (isRecording && activeTest == TestType.RANGE) "録音中... ${countdown}秒" else "音域テストを開始")
        }

        if (isRecording && activeTest == TestType.RANGE) {
            Spacer(Modifier.height(8.dp))
            Text("検出中の音: $liveNote", fontSize = 13.sp)
        }

        rangeResult?.let { r ->
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("音域テスト結果", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("推定音域: ${r.lowNote} 〜 ${r.highNote}")
                    Text(
                        "（約${"%.1f".format(r.lowFrequencyHz)}Hz 〜 約${"%.1f".format(r.highFrequencyHz)}Hz）",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
        }

        if (micPermissionDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                "マイクへのアクセスが許可されていません。端末の設定からこのアプリのマイク権限を有効にしてください。",
                color = Color(0xFFB3261E),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
