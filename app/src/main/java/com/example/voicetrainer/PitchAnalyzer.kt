package com.example.voicetrainer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * マイクからPCM音声を取得し、自己相関法（オートコリレーション）でピッチ（基本周波数）を
 * 推定するクラス。外部の音声解析ライブラリやネイティブコードは使わず、
 * Android標準の [AudioRecord] APIとKotlinの数値計算だけで実装している。
 *
 * 精度は市販のチューナーアプリほど高くはないが、
 * ・無音区間の除外（RMSしきい値）
 * ・オクターブ誤検出（相関のサブハーモニックへの誤ロック）を避けるための
 *   「グローバル最大相関の85%以上を満たす最初のローカルピーク」を採用する方式
 * ・相関値と信号エネルギーの比による信頼度チェック
 * など、実際のピッチ検出アルゴリズムとして妥当な工夫を入れている。
 */
class PitchAnalyzer {

    data class RecordingSession(
        val totalFrames: Int,
        val frequencies: List<Float>
    )

    /**
     * [durationMillis] ミリ秒間マイクから録音しながら、フレームごとにピッチを推定する。
     * 呼び出し側で RECORD_AUDIO 権限が許可されていることを確認してから呼び出すこと。
     *
     * [onFrame] はメインスレッド上で、フレーム解析のたびに呼び出される
     * （UIに「検出中の音」をリアルタイム表示するためのコールバック）。
     */
    @SuppressLint("MissingPermission")
    suspend fun record(
        durationMillis: Long,
        onFrame: suspend (Float?) -> Unit = {}
    ): RecordingSession = withContext(Dispatchers.IO) {
        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            return@withContext RecordingSession(0, emptyList())
        }
        val recorderBufferSize = maxOf(minBufferSize, FRAME_SIZE * 2 * 4)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            recorderBufferSize
        )

        val frequencies = mutableListOf<Float>()
        var totalFrames = 0
        val buffer = ShortArray(FRAME_SIZE)

        try {
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext RecordingSession(0, emptyList())
            }
            audioRecord.startRecording()
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < durationMillis) {
                val read = audioRecord.read(buffer, 0, FRAME_SIZE)
                if (read > 0) {
                    totalFrames++
                    val freq = detectPitch(buffer, sampleRate)
                    if (freq != null) frequencies.add(freq)
                    withContext(Dispatchers.Main) {
                        onFrame(freq)
                    }
                }
            }
        } finally {
            try {
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop()
                }
            } catch (_: IllegalStateException) {
                // 録音が開始されていない状態で stop() すると例外になることがあるため無視する
            }
            audioRecord.release()
        }

        RecordingSession(totalFrames, frequencies)
    }

    private fun detectPitch(samples: ShortArray, sampleRate: Int): Float? {
        val n = samples.size
        val buffer = FloatArray(n) { samples[it] / 32768f }

        var rms = 0f
        for (v in buffer) rms += v * v
        rms = sqrt(rms / n)
        if (rms < SILENCE_RMS_THRESHOLD) return null

        val minLag = (sampleRate / MAX_VOICE_FREQ).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / MIN_VOICE_FREQ).toInt().coerceAtMost(n - 1)
        if (maxLag <= minLag) return null

        val correlations = FloatArray(maxLag - minLag + 1)
        var globalMax = 0f
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until n - lag) {
                sum += buffer[i] * buffer[i + lag]
            }
            val idx = lag - minLag
            correlations[idx] = sum
            if (sum > globalMax) globalMax = sum
        }
        if (globalMax <= 0f) return null

        // オクターブ誤検出（副次ピークへの誤ロック）を避けるため、
        // グローバル最大相関の85%以上を満たす「最初のローカルピーク」を採用する。
        val threshold = globalMax * PEAK_RATIO_THRESHOLD
        var chosenLag = -1
        for (lag in minLag..maxLag) {
            val idx = lag - minLag
            val c = correlations[idx]
            if (c >= threshold) {
                val prev = if (idx > 0) correlations[idx - 1] else c
                val next = if (idx < correlations.size - 1) correlations[idx + 1] else c
                if (c >= prev && c >= next) {
                    chosenLag = lag
                    break
                }
            }
        }
        if (chosenLag == -1) return null

        var energy = 0f
        for (v in buffer) energy += v * v
        val confidence = correlations[chosenLag - minLag] / (energy + 1e-9f)
        if (confidence < CONFIDENCE_THRESHOLD) return null

        return sampleRate.toFloat() / chosenLag
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val FRAME_SIZE = 4096
        private const val MIN_VOICE_FREQ = 70f    // 低い男声の下限付近
        private const val MAX_VOICE_FREQ = 1000f  // 高いファルセット・裏声付近
        private const val SILENCE_RMS_THRESHOLD = 0.02f
        private const val CONFIDENCE_THRESHOLD = 0.35f
        private const val PEAK_RATIO_THRESHOLD = 0.85f

        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /** 周波数(Hz)から音名（例: "A4"）を求める。A4 = 440Hz を基準とする12平均律。 */
        fun frequencyToNoteName(freq: Float): String {
            if (freq <= 0f) return "-"
            val semitoneFromA4 = 12.0 * ln(freq / 440.0) / ln(2.0)
            val noteNumber = semitoneFromA4.roundToInt()
            val absoluteIndex = 57 + noteNumber // A4はC0から57半音上
            val octave = Math.floorDiv(absoluteIndex, 12)
            val noteIndex = Math.floorMod(absoluteIndex, 12)
            return "${NOTE_NAMES[noteIndex]}$octave"
        }
    }
}

// =========================================================================
// 診断結果
// =========================================================================
data class StabilityResult(
    val averageNote: String,
    val averageFrequencyHz: Float,
    val stabilityPercent: Int,
    val voicedFrameRatioPercent: Int
)

data class RangeResult(
    val lowNote: String,
    val lowFrequencyHz: Float,
    val highNote: String,
    val highFrequencyHz: Float
)

private const val MIN_VALID_FRAMES = 5

/**
 * 録音セッションから、ピッチ安定度を計算する。
 * 各フレームの周波数を半音（セミトーン）単位に変換し、
 * 中央値から±0.5半音以内に収まっているフレームの割合を安定度スコアとする。
 */
fun computeStabilityResult(session: PitchAnalyzer.RecordingSession): StabilityResult? {
    val freqs = session.frequencies
    if (freqs.size < MIN_VALID_FRAMES) return null

    val semitones = freqs.map { 12.0 * ln(it / 440.0) / ln(2.0) }
    val sortedSemitones = semitones.sorted()
    val median = sortedSemitones[sortedSemitones.size / 2]
    val withinHalfSemitone = semitones.count { abs(it - median) <= 0.5 }
    val stabilityPercent = ((withinHalfSemitone.toDouble() / semitones.size) * 100).roundToInt()

    val avgFreq = freqs.average().toFloat()
    val voicedRatio = if (session.totalFrames > 0) {
        ((freqs.size.toDouble() / session.totalFrames) * 100).roundToInt()
    } else {
        0
    }

    return StabilityResult(
        averageNote = PitchAnalyzer.frequencyToNoteName(avgFreq),
        averageFrequencyHz = avgFreq,
        stabilityPercent = stabilityPercent,
        voicedFrameRatioPercent = voicedRatio
    )
}

/**
 * 録音セッションから、検出できた最低音・最高音を算出する。
 * オクターブ誤検出などの外れ値の影響を減らすため、上下5%をトリムしてから最小・最大を取る。
 */
fun computeRangeResult(session: PitchAnalyzer.RecordingSession): RangeResult? {
    val freqs = session.frequencies
    if (freqs.size < MIN_VALID_FRAMES) return null

    val sorted = freqs.sorted()
    val trimCount = (sorted.size * 0.05).roundToInt()
    val trimmed = if (trimCount * 2 < sorted.size) {
        sorted.subList(trimCount, sorted.size - trimCount)
    } else {
        sorted
    }

    val low = trimmed.first()
    val high = trimmed.last()

    return RangeResult(
        lowNote = PitchAnalyzer.frequencyToNoteName(low),
        lowFrequencyHz = low,
        highNote = PitchAnalyzer.frequencyToNoteName(high),
        highFrequencyHz = high
    )
}
