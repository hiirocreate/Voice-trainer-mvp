package com.example.voicetrainer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** 測定の目的（初回の計画用か、プログラム完了後の再測定か）。数秒で終わるためDataStoreには永続化しない。 */
private enum class MeasuringPurpose { INITIAL, REMEASURE }

/** 1回の測定の中での段階（安定度→音域の順に測定する）。 */
private enum class MeasuringStage { NONE, STABILITY, RANGE }

@Composable
fun DashboardScreen(preferencesRepository: UserPreferencesRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pitchAnalyzer = remember { PitchAnalyzer() }

    var voiceMode by remember { mutableStateOf(VoiceMode.CHEST) }
    val info = voiceModeInfoMap.getValue(voiceMode)

    val lastDiagnosis by preferencesRepository.lastDiagnosisFlow.collectAsState(
        initial = LastDiagnosis(null, null, null, null, null, null)
    )
    val recommendedMenu = remember(lastDiagnosis) { recommendExercises(lastDiagnosis) }

    // --- パーソナルトレーニングプログラムの永続状態 -------------------------------
    // ここは DataStore（永続化ストレージ）から取得した状態を「そのまま」使う。
    // タブを切り替えて画面が破棄・再構築されても、進行中のプログラムが消えないようにするため
    // （remember だけに頼らず、真実はすべて DataStore 側に持たせている）。
    val activeProgram by preferencesRepository.activeProgramFlow.collectAsState(initial = null)
    val awaitingGoalSelection by preferencesRepository.awaitingGoalSelectionFlow.collectAsState(initial = false)
    val awaitingComparison by preferencesRepository.awaitingComparisonFlow.collectAsState(initial = false)

    val persistedPhase = when {
        activeProgram != null && awaitingComparison -> ProgramPhase.COMPARISON
        activeProgram != null -> ProgramPhase.TRAINING
        awaitingGoalSelection -> ProgramPhase.GOAL_SELECTION
        else -> ProgramPhase.NO_PROGRAM
    }

    // --- 測定中だけの一時的なUI状態（数秒で終わるため、永続化はしない） -------------------
    var measuringPurpose by remember { mutableStateOf<MeasuringPurpose?>(null) }
    var measuringStage by remember { mutableStateOf(MeasuringStage.NONE) }
    var measureCountdown by remember { mutableStateOf(0) }
    var liveNote by remember { mutableStateOf("") }
    var measureError by remember { mutableStateOf<String?>(null) }

    val programPhase = when (measuringPurpose) {
        MeasuringPurpose.INITIAL -> ProgramPhase.MEASURING
        MeasuringPurpose.REMEASURE -> ProgramPhase.RE_MEASURING
        null -> persistedPhase
    }

    var micPermissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micPermissionDenied = !granted }

    /** ピッチ安定度→音域の順に連続で測定し、結果を保存する。目的（初回計画用／再測定用）は [purpose] で区別する。 */
    fun runCombinedMeasurement(purpose: MeasuringPurpose, onDone: (StabilityResult?) -> Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        micPermissionDenied = false
        measureError = null
        measuringPurpose = purpose
        measuringStage = MeasuringStage.STABILITY
        val stabilityDurationSeconds = 5
        val rangeDurationSeconds = 8
        measureCountdown = stabilityDurationSeconds
        liveNote = "…"

        coroutineScope.launch {
            var stabilityResult: StabilityResult? = null
            var rangeResult: RangeResult? = null
            try {
                val countdownJob1 = launch {
                    for (i in stabilityDurationSeconds downTo 1) {
                        measureCountdown = i
                        delay(1000)
                    }
                }
                val stabilitySession = pitchAnalyzer.record(stabilityDurationSeconds * 1000L) { freq ->
                    liveNote = freq?.let { PitchAnalyzer.frequencyToNoteName(it) } ?: "…"
                }
                countdownJob1.cancel()
                stabilityResult = computeStabilityResult(stabilitySession)

                if (stabilityResult != null) {
                    measuringStage = MeasuringStage.RANGE
                    measureCountdown = rangeDurationSeconds
                    val countdownJob2 = launch {
                        for (i in rangeDurationSeconds downTo 1) {
                            measureCountdown = i
                            delay(1000)
                        }
                    }
                    val rangeSession = pitchAnalyzer.record(rangeDurationSeconds * 1000L) { freq ->
                        liveNote = freq?.let { PitchAnalyzer.frequencyToNoteName(it) } ?: "…"
                    }
                    countdownJob2.cancel()
                    rangeResult = computeRangeResult(rangeSession)
                }

                if (stabilityResult == null) {
                    measureError = "声を十分に検出できませんでした。マイクに向かって、はっきりと発声してみてください。"
                } else {
                    preferencesRepository.saveStabilityResult(stabilityResult.averageNote, stabilityResult.stabilityPercent)
                    preferencesRepository.appendStabilitySnapshot(System.currentTimeMillis(), stabilityResult.stabilityPercent)
                    if (rangeResult != null) {
                        preferencesRepository.saveRangeResult(rangeResult.lowNote, rangeResult.highNote)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                measureError = "測定中に問題が発生しました。マイクが他のアプリで使用されていないか確認して、もう一度お試しください。"
            } finally {
                measureCountdown = 0
                measuringStage = MeasuringStage.NONE
                measuringPurpose = null
                onDone(stabilityResult)
            }
        }
    }

    fun startInitialMeasurement() {
        runCombinedMeasurement(MeasuringPurpose.INITIAL) { result ->
            if (result != null) {
                coroutineScope.launch { preferencesRepository.setAwaitingGoalSelection(true) }
            }
        }
    }

    fun startProgram(goal: TrainingGoal, durationDays: Int) {
        val stabilityPercent = lastDiagnosis.stabilityPercent ?: 50
        val plan = buildProgramPlan(goal, stabilityPercent, durationDays)
        val program = TrainingProgram(
            goal = goal,
            durationDays = durationDays,
            startTimestampMillis = System.currentTimeMillis(),
            days = plan,
            currentDayIndex = 0,
            currentExerciseIndex = 0,
            baselineStabilityPercent = stabilityPercent,
            baselineRangeLowNote = lastDiagnosis.rangeLowNote,
            baselineRangeHighNote = lastDiagnosis.rangeHighNote
        )
        coroutineScope.launch {
            preferencesRepository.saveActiveProgram(program)
            preferencesRepository.setAwaitingGoalSelection(false)
        }
    }

    fun advanceExercise(program: TrainingProgram) {
        val today = program.days.getOrNull(program.currentDayIndex) ?: return
        if (program.currentExerciseIndex < today.items.size - 1) {
            coroutineScope.launch {
                preferencesRepository.saveActiveProgram(
                    program.copy(currentExerciseIndex = program.currentExerciseIndex + 1)
                )
            }
        } else {
            val dateKey = java.time.LocalDate.now().toString()
            coroutineScope.launch {
                preferencesRepository.markPracticeStampDone(dateKey)
                preferencesRepository.saveActiveProgram(
                    program.copy(currentDayIndex = program.currentDayIndex + 1, currentExerciseIndex = 0)
                )
            }
        }
    }

    fun finishProgramAndRemeasure() {
        runCombinedMeasurement(MeasuringPurpose.REMEASURE) { result ->
            if (result != null) {
                coroutineScope.launch { preferencesRepository.setAwaitingComparison(true) }
            }
        }
    }

    fun startNextGoalSelection() {
        coroutineScope.launch {
            preferencesRepository.clearActiveProgram()
            preferencesRepository.setAwaitingComparison(false)
            preferencesRepository.setAwaitingGoalSelection(true)
        }
    }

    fun endProgramCompletely() {
        coroutineScope.launch {
            preferencesRepository.clearActiveProgram()
            preferencesRepository.setAwaitingComparison(false)
            preferencesRepository.setAwaitingGoalSelection(false)
        }
    }

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
                    Text("前回の測定結果", fontWeight = FontWeight.Bold)
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

        // --- パーソナルトレーニングプログラム -----------------------------------
        Text("パーソナルトレーニング", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "測定 → 目標選択 → 1〜2週間のトレーニング計画 → 完了ボタンで再測定・比較 の流れで、\nあなたに合ったメニューを更新し続けます。",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (programPhase) {
                    ProgramPhase.NO_PROGRAM -> {
                        Button(onClick = { startInitialMeasurement() }) {
                            Text("測定してトレーニングを計画する")
                        }
                    }

                    ProgramPhase.MEASURING, ProgramPhase.RE_MEASURING -> {
                        val stageLabel = when (measuringStage) {
                            MeasuringStage.STABILITY -> "ピッチ安定度を測定中（一定の高さで「あー」と伸ばしてください）"
                            MeasuringStage.RANGE -> "音域を測定中（低い声から高い声へ、ゆっくり滑らかに）"
                            MeasuringStage.NONE -> "測定中"
                        }
                        Text(stageLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("残り ${measureCountdown}秒 / 検出中の音: $liveNote", fontSize = 13.sp)
                    }

                    ProgramPhase.GOAL_SELECTION -> {
                        GoalSelectionCard(
                            baselineStabilityPercent = lastDiagnosis.stabilityPercent,
                            onConfirm = { goal, durationDays -> startProgram(goal, durationDays) }
                        )
                    }

                    ProgramPhase.TRAINING -> {
                        val program = activeProgram
                        if (program == null) {
                            Text("プログラムを読み込めませんでした。もう一度測定からお試しください。", fontSize = 13.sp)
                        } else if (program.currentDayIndex >= program.durationDays) {
                            Text(
                                "${program.durationDays}日間のメニューをすべて完了しました！お疲れ様でした。",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { finishProgramAndRemeasure() }) { Text("再測定して変化を確認する") }
                        } else {
                            TrainingProgramCard(
                                program = program,
                                onExerciseFinished = { advanceExercise(program) },
                                onFinishEarly = { finishProgramAndRemeasure() }
                            )
                        }
                    }

                    ProgramPhase.COMPARISON -> {
                        val program = activeProgram
                        val afterStability = lastDiagnosis.stabilityPercent
                        if (program != null && afterStability != null) {
                            ComparisonCard(
                                goal = program.goal,
                                beforeStabilityPercent = program.baselineStabilityPercent,
                                afterStabilityPercent = afterStability,
                                beforeRangeLowNote = program.baselineRangeLowNote,
                                beforeRangeHighNote = program.baselineRangeHighNote,
                                afterRangeLowNote = lastDiagnosis.rangeLowNote,
                                afterRangeHighNote = lastDiagnosis.rangeHighNote,
                                onContinue = { startNextGoalSelection() },
                                onEnd = { endProgramCompletely() }
                            )
                        }
                    }
                }

                measureError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFB3261E), fontSize = 12.sp)
                }
                if (micPermissionDenied) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "マイクへのアクセスが許可されていません。端末の設定でこのアプリのマイク権限を有効にしてください。",
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
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
        Text("自宅用省エネメニュー（自由練習）", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (recommendedMenu.any { it.isRecommended }) {
            Text(
                "（前回の診断結果をもとに、おすすめのメニューを先頭に並べています）",
                fontSize = 12.sp,
                color = Color.Gray
            )
        } else {
            Text(
                "（診断画面でテストを受けると、あなたに合ったメニューをおすすめします）",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Spacer(Modifier.height(8.dp))
        recommendedMenu.forEach { item ->
            ExerciseTimerCard(
                title = item.title,
                description = item.description,
                totalSeconds = item.totalSeconds,
                isRecommended = item.isRecommended,
                recommendReason = item.recommendReason
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 目標（どうなりたいか）と期間を選ぶカード。GOAL_SELECTION フェーズで表示。 */
@Composable
private fun GoalSelectionCard(
    baselineStabilityPercent: Int?,
    onConfirm: (TrainingGoal, Int) -> Unit
) {
    var selectedGoal by remember { mutableStateOf<TrainingGoal?>(null) }
    var selectedDuration by remember { mutableStateOf(7) }

    Column {
        if (baselineStabilityPercent != null) {
            Text(
                "今回の測定結果（ピッチ安定度: ${baselineStabilityPercent}%）をもとに計画します。",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
        }
        Text("どうなりたいですか？", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        TrainingGoal.values().forEach { goal ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedGoal = goal }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedGoal == goal, onClick = { selectedGoal = goal })
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(goal.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(goal.description, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("期間", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            listOf(7, 14).forEach { days ->
                val selected = selectedDuration == days
                Button(
                    onClick = { selectedDuration = days },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("${days}日間")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { selectedGoal?.let { onConfirm(it, selectedDuration) } },
            enabled = selectedGoal != null
        ) {
            Text("この内容でトレーニングを計画する")
        }
    }
}

/** 進行中プログラムの「本日のメニュー」を表示・実行するカード。TRAINING フェーズで表示。 */
@Composable
private fun TrainingProgramCard(
    program: TrainingProgram,
    onExerciseFinished: () -> Unit,
    onFinishEarly: () -> Unit
) {
    val today = program.days.getOrNull(program.currentDayIndex)

    Column {
        Text("目標: ${program.goal.label}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Day ${program.currentDayIndex + 1} / ${program.durationDays}",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = program.currentDayIndex.toFloat() / program.durationDays.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (today == null || today.items.isEmpty()) {
            Text("本日のメニューを準備できませんでした。", fontSize = 13.sp)
        } else {
            val item = today.items.getOrNull(program.currentExerciseIndex) ?: today.items.first()
            Text(
                "本日のメニュー ${(program.currentExerciseIndex + 1).coerceAtMost(today.items.size)} / ${today.items.size}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            PersonalizedExerciseCard(item = item, onFinished = onExerciseFinished)
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onFinishEarly) {
            Text("ここまでにして再測定する")
        }
    }
}

/** 再測定後、プログラム開始前（baseline）との比較結果を表示するカード。COMPARISON フェーズで表示。 */
@Composable
private fun ComparisonCard(
    goal: TrainingGoal,
    beforeStabilityPercent: Int,
    afterStabilityPercent: Int,
    beforeRangeLowNote: String?,
    beforeRangeHighNote: String?,
    afterRangeLowNote: String?,
    afterRangeHighNote: String?,
    onContinue: () -> Unit,
    onEnd: () -> Unit
) {
    Column {
        Text("測定結果の比較（目標: ${goal.label}）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("ピッチ安定度: ${beforeStabilityPercent}% → ${afterStabilityPercent}%", fontSize = 13.sp)
        if (beforeRangeLowNote != null && beforeRangeHighNote != null &&
            afterRangeLowNote != null && afterRangeHighNote != null
        ) {
            Text(
                "推定音域: ${beforeRangeLowNote}〜${beforeRangeHighNote} → ${afterRangeLowNote}〜${afterRangeHighNote}",
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            buildTrendMessage(beforeStabilityPercent, afterStabilityPercent),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = onContinue) { Text("次の目標を決めて続ける") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onEnd) { Text("ここで終える") }
        }
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

/** プログラムの「本日のメニュー」を1種目ずつ表示するカード。タイマー終了 or スキップで次に進む。 */
@Composable
fun PersonalizedExerciseCard(item: TrainingPlanItem, onFinished: () -> Unit) {
    var remaining by remember(item.exerciseId, item.seconds) { mutableStateOf(item.seconds) }
    var isRunning by remember(item.exerciseId) { mutableStateOf(false) }
    var completed by remember(item.exerciseId) { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
            }
            isRunning = false
            completed = true
        }
    }

    Column {
        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(item.description, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Text(item.reason, fontSize = 11.sp, color = Color(0xFF6750A4))
        Spacer(Modifier.height(12.dp))
        Text("${remaining}s", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (!isRunning && !completed) isRunning = true },
                enabled = !isRunning && !completed
            ) {
                Text(if (completed) "完了" else if (isRunning) "実行中" else "開始")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onFinished) {
                Text(if (completed) "次へ" else "スキップ")
            }
        }
    }
}

@Composable
fun ExerciseTimerCard(
    title: String,
    description: String,
    totalSeconds: Int,
    isRecommended: Boolean = false,
    recommendReason: String? = null
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold)
                    if (isRecommended) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF6750A4))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("おすすめ", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = Color.Gray)
                if (recommendReason != null) {
                    Text(recommendReason, fontSize = 11.sp, color = Color(0xFF6750A4))
                }
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
