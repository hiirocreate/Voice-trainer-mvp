package com.example.voicetrainer

import kotlin.math.roundToInt

// =========================================================================
// 自由練習メニュー（診断結果に応じて並び替えるだけの、簡易版おすすめ）
// =========================================================================
data class ExerciseMenuItem(
    val id: String,
    val title: String,
    val description: String,
    val totalSeconds: Int,
    val isRecommended: Boolean,
    val recommendReason: String?
)

data class ExerciseTemplate(
    val id: String,
    val title: String,
    val description: String,
    val totalSeconds: Int
)

val ALL_EXERCISES = listOf(
    ExerciseTemplate("lip_roll", "リップロール", "唇を軽く閉じ「ブルルル」と振動させながら音階を上下させます。", 60),
    ExerciseTemplate("edge_voice", "エッジボイス", "「ぎー」という軽く掠れた声を出し、声帯の閉鎖を意識します。", 45),
    ExerciseTemplate("humming", "ハミング", "口を閉じたまま「んー」と鼻腔に響かせるように発声します。", 60),
    ExerciseTemplate("straw_phonation", "ストロー発声", "ストロー（あれば）をくわえ、細く一定の息で「うー」と発声します。声帯への負担を抑えつつ響きを育てます。", 60),
    ExerciseTemplate("siren", "サイレン発声", "「ウー」と言いながら、低い声から高い声へ、またゆっくり戻る、を滑らかに繰り返します。", 45)
)

/**
 * 診断結果（目的・ピッチ安定度）をもとに、自由練習メニューの並び順を判定する。
 * メニュー自体は常にすべて表示するが、おすすめのものを先頭に並べ替え、理由を添える。
 *
 * ルール（一般的なボイストレーニングの考え方に基づく簡易ルールベース。
 * 医学的・専門的な厳密さを保証するものではなく、あくまでMVPとしての一次的な目安）:
 * ・ピッチ安定度が60%未満、または目的が「音痴を改善したい」→ エッジボイス／ハミングを推奨
 * ・目的が「音域を広げたい」→ リップロールを推奨
 * ・目的が「声量・響きを改善したい」→ ハミングを推奨
 */
fun recommendExercises(diagnosis: LastDiagnosis): List<ExerciseMenuItem> {
    val reasons = mutableMapOf<String, String>()

    val lowStability = diagnosis.stabilityPercent != null && diagnosis.stabilityPercent < 60
    if (lowStability || diagnosis.purpose == "音痴を改善したい") {
        reasons["edge_voice"] = "ピッチのコントロール強化におすすめ"
        reasons["humming"] = "音程を合わせる感覚づくりにおすすめ"
    }
    if (diagnosis.purpose == "音域を広げたい") {
        reasons["lip_roll"] = "音域の拡張ウォームアップにおすすめ"
    }
    if (diagnosis.purpose == "声量・響きを改善したい") {
        reasons["humming"] = "響き（共鳴）づくりにおすすめ"
    }

    val items = ALL_EXERCISES.map { template ->
        ExerciseMenuItem(
            id = template.id,
            title = template.title,
            description = template.description,
            totalSeconds = template.totalSeconds,
            isRecommended = reasons.containsKey(template.id),
            recommendReason = reasons[template.id]
        )
    }

    return items.sortedByDescending { it.isRecommended }
}

// =========================================================================
// パーソナルトレーニングプログラム
// （測定 → 目標選択 → 1〜2週間のメニュー計画・スケジューリング →
//   トレーニング完了ボタン → 再測定 → 前回との比較 → 再度目標選択 → …）
// =========================================================================

/** ユーザーが選ぶ「どうなりたいか」という目標。 */
enum class TrainingGoal(val label: String, val description: String) {
    HIGHER_VOICE("高い声を出したい", "地声のまま出せる高さを広げ、裏声への移行もなめらかにしていきます。"),
    RICH_RESONANCE("豊かな声を出したい", "声に響き（共鳴）を加え、通る・厚みのある声を目指します。"),
    STABLE_PITCH("音程を安定させたい", "ピッチのブレを減らし、音程を正確にコントロールする力を鍛えます。"),
    LOWER_VOICE("低い声を出したい", "声帯をリラックスさせ、力まず低い声を長く保てるようにします。"),
    GENERAL("総合的に鍛えたい", "特定の弱点に絞らず、バランスよく声全体を鍛えます。")
}

/**
 * 目標ごとに重点的に行う種目（優先度順）。
 * ここも診断メニューと同様、外部AI/APIは使わない決定的なルールベースのマッピングにしている。
 * 医学的な専門知識に基づく厳密な処方ではなく、一般的なボイストレーニングの考え方を
 * もとにした簡易的な目安であり、目標と種目の組み合わせが常に一定で説明できることを優先している。
 */
val GOAL_FOCUS_EXERCISES: Map<TrainingGoal, List<String>> = mapOf(
    TrainingGoal.HIGHER_VOICE to listOf("siren", "lip_roll", "humming"),
    TrainingGoal.RICH_RESONANCE to listOf("humming", "straw_phonation", "lip_roll"),
    TrainingGoal.STABLE_PITCH to listOf("edge_voice", "siren", "humming"),
    TrainingGoal.LOWER_VOICE to listOf("humming", "straw_phonation", "edge_voice"),
    TrainingGoal.GENERAL to ALL_EXERCISES.map { it.id }
)

data class TrainingPlanItem(
    val exerciseId: String,
    val title: String,
    val description: String,
    val seconds: Int,
    val reason: String
)

/** プログラム内の1日分のメニュー。dayIndex は 0 始まり。 */
data class DayPlan(
    val dayIndex: Int,
    val items: List<TrainingPlanItem>
)

/**
 * 進行中のパーソナルトレーニングプログラム。
 * currentDayIndex / currentExerciseIndex は DataStore に永続化されるため、
 * タブを切り替えたりアプリを再起動したりしても進行状況が失われない。
 */
data class TrainingProgram(
    val goal: TrainingGoal,
    val durationDays: Int,
    val startTimestampMillis: Long,
    val days: List<DayPlan>,
    val currentDayIndex: Int,
    val currentExerciseIndex: Int,
    val baselineStabilityPercent: Int,
    val baselineRangeLowNote: String?,
    val baselineRangeHighNote: String?
)

/** パーソナルトレーニングの進行状態。 */
enum class ProgramPhase { NO_PROGRAM, MEASURING, GOAL_SELECTION, TRAINING, RE_MEASURING, COMPARISON }

/**
 * 目標・現在のピッチ安定度・希望日数から、1〜2週間分のトレーニング計画を組み立てる。
 *
 * ルール（すべて説明可能な決定的ロジックで、外部AI/APIは使用していない。
 * 数値目標に対する加減算・条件分岐だけで完結するこの種の判断は、
 * ネットワーク依存や利用上限のあるAI APIより、こうしたルールベースの方が
 * オフラインで確実に動き、判断根拠も明確にできるため適していると考えている）:
 * ・目標ごとに重点種目（[GOAL_FOCUS_EXERCISES]）を毎日行う
 * ・ピッチ安定度が低いほど（<40%, <60%）、1種目あたりの時間を長めにする
 * ・日が進むにつれて少しずつ負荷（秒数）を上げる（漸進性過負荷の考え方）
 * ・3日に1回、重点種目以外からバランス強化用の種目を1つ追加する
 */
fun buildProgramPlan(
    goal: TrainingGoal,
    stabilityPercent: Int,
    durationDays: Int
): List<DayPlan> {
    val focusIds = GOAL_FOCUS_EXERCISES[goal] ?: ALL_EXERCISES.map { it.id }
    val focusTemplates = focusIds.mapNotNull { id -> ALL_EXERCISES.find { it.id == id } }
    val otherTemplates = ALL_EXERCISES.filter { template -> focusIds.none { it == template.id } }

    val levelMultiplier = when {
        stabilityPercent < 40 -> 1.4
        stabilityPercent < 60 -> 1.15
        else -> 1.0
    }

    return (1..durationDays).map { day ->
        val progressMultiplier = 1.0 + (day - 1) * 0.03
        val items = mutableListOf<TrainingPlanItem>()

        focusTemplates.forEach { template ->
            val seconds = (template.totalSeconds * levelMultiplier * progressMultiplier)
                .roundToInt()
                .coerceIn(20, 150)
            items.add(
                TrainingPlanItem(
                    exerciseId = template.id,
                    title = template.title,
                    description = template.description,
                    seconds = seconds,
                    reason = "「${goal.label}」の重点メニューです"
                )
            )
        }

        if (day % 3 == 0 && otherTemplates.isNotEmpty()) {
            val template = otherTemplates[(day / 3 - 1) % otherTemplates.size]
            items.add(
                TrainingPlanItem(
                    exerciseId = template.id,
                    title = template.title,
                    description = template.description,
                    seconds = template.totalSeconds,
                    reason = "バランス強化のための総合メニューです"
                )
            )
        }

        DayPlan(dayIndex = day - 1, items = items)
    }
}

/** トレーニング前後のピッチ安定度を比較し、コーチング用の一言コメントを生成する。 */
fun buildTrendMessage(beforeStabilityPercent: Int, afterStabilityPercent: Int): String {
    val delta = afterStabilityPercent - beforeStabilityPercent
    return when {
        delta >= 10 -> "安定度が${delta}ポイントも向上しました！素晴らしい変化です。"
        delta in 1..9 -> "安定度が${delta}ポイント向上しました。着実に前進しています。"
        delta == 0 -> "安定度は前回と同じでした。焦らず継続していきましょう。"
        else -> "今回は${-delta}ポイント下がりましたが、声の調子は日によって変わるものです。次のプログラムで整えていきましょう。"
    }
}
