package com.superman.lianji.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

enum class WeightUnit { KG, LB }

enum class TrackingType { WEIGHT_REPS, BODYWEIGHT, ASSISTED, DURATION, DISTANCE }

enum class SetKind { NORMAL, WARMUP, DROP, FAILURE }

data class StatSet(
    val sessionId: String,
    val epochDay: Long,
    val exerciseId: String,
    val exerciseName: String,
    val muscleZh: String,
    val tracking: TrackingType,
    val kind: SetKind,
    val completed: Boolean,
    val reps: Int?,
    val weightGrams: Int?,
    val durationMs: Long?,
    val distanceMm: Int?,
)

data class SessionMarker(val id: String, val epochDay: Long, val durationMs: Long)

data class WeekPoint(val startEpochDay: Long, val sessions: Int, val volumeKg: Double)

data class MuscleShare(val muscleZh: String, val sets: Int)

data class ExerciseTrend(val epochDay: Long, val weightKg: Double?, val reps: Int?, val epleyKg: Double?)

data class RecordHit(val exerciseId: String, val exerciseName: String, val label: String, val epochDay: Long)

data class RangeSummary(val sessions: Int, val sets: Int, val volumeKg: Double, val minutes: Int)

private const val GRAMS_PER_POUND = 453.59237

fun trackingOf(value: String): TrackingType = when (value) {
    "bodyweight_reps" -> TrackingType.BODYWEIGHT
    "assisted_bodyweight" -> TrackingType.ASSISTED
    "duration" -> TrackingType.DURATION
    "distance_duration" -> TrackingType.DISTANCE
    else -> TrackingType.WEIGHT_REPS
}

fun kindOf(value: String): SetKind = when (value) {
    "warmup" -> SetKind.WARMUP
    "drop" -> SetKind.DROP
    "failure" -> SetKind.FAILURE
    else -> SetKind.NORMAL
}

fun displayToGrams(text: String, unit: WeightUnit): Int? {
    val cleaned = text.trim().replace('，', '.').replace(',', '.').replace("公斤", "").replace("磅", "")
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value < 0.0 || value > 2000.0) return null
    val grams = if (unit == WeightUnit.KG) value * 1000.0 else value * GRAMS_PER_POUND
    return grams.roundToInt()
}

fun gramsToInput(grams: Int?, unit: WeightUnit): String {
    if (grams == null || grams <= 0) return ""
    val value = if (unit == WeightUnit.KG) grams / 1000.0 else grams / GRAMS_PER_POUND
    return formatNumber(value)
}

fun formatWeight(grams: Int, unit: WeightUnit): String {
    val number = gramsToInput(grams, unit)
    return if (unit == WeightUnit.KG) "$number 公斤" else "$number 磅"
}

fun formatNumber(value: Double): String {
    val text = String.format(java.util.Locale.US, "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
}

fun countsTowardTraining(set: StatSet): Boolean = set.completed && set.kind != SetKind.WARMUP

fun volumeKg(set: StatSet): Double {
    if (!countsTowardTraining(set) || set.tracking == TrackingType.ASSISTED) return 0.0
    val reps = set.reps ?: return 0.0
    val grams = set.weightGrams ?: return 0.0
    if (reps <= 0 || grams <= 0) return 0.0
    return grams / 1000.0 * reps
}

fun estimatedOneRepMaxKg(set: StatSet): Double? {
    if (!countsTowardTraining(set)) return null
    if (set.tracking != TrackingType.WEIGHT_REPS && set.tracking != TrackingType.BODYWEIGHT) return null
    val grams = set.weightGrams ?: return null
    val reps = set.reps ?: return null
    if (grams <= 0 || reps !in 1..10) return null
    val kg = grams / 1000.0
    return if (reps == 1) kg else kg * (1.0 + reps / 30.0)
}

fun trainingStreak(trainingDays: Set<Long>, today: Long): Int {
    var cursor = if (today in trainingDays) today else today - 1
    if (cursor !in trainingDays) return 0
    var count = 0
    while (cursor in trainingDays) {
        count += 1
        cursor -= 1
    }
    return count
}

fun weekStart(epochDay: Long): Long {
    val date = LocalDate.ofEpochDay(epochDay)
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
}

fun weeklyPoints(
    sessions: List<SessionMarker>,
    sets: List<StatSet>,
    today: Long,
    weeks: Int,
): List<WeekPoint> {
    val end = weekStart(today)
    val starts = (weeks - 1 downTo 0).map { end - it * 7L }
    return starts.map { start ->
        val sessionCount = sessions.count { weekStart(it.epochDay) == start }
        val volume = sets.filter { weekStart(it.epochDay) == start }.sumOf { volumeKg(it) }
        WeekPoint(start, sessionCount, volume)
    }
}

fun muscleShares(sets: List<StatSet>): List<MuscleShare> =
    sets.filter { countsTowardTraining(it) }
        .groupingBy { it.muscleZh }
        .eachCount()
        .map { MuscleShare(it.key, it.value) }
        .sortedByDescending { it.sets }

fun rangeSummary(sessions: List<SessionMarker>, sets: List<StatSet>): RangeSummary {
    val working = sets.filter { countsTowardTraining(it) }
    return RangeSummary(
        sessions = sessions.size,
        sets = working.size,
        volumeKg = working.sumOf { volumeKg(it) },
        minutes = (sessions.sumOf { it.durationMs } / 60000L).toInt(),
    )
}

fun exerciseTrend(sets: List<StatSet>, exerciseId: String): List<ExerciseTrend> =
    sets.filter { it.exerciseId == exerciseId && countsTowardTraining(it) }
        .groupBy { it.epochDay }
        .toSortedMap()
        .map { (day, daySets) ->
            val weight = daySets.mapNotNull { it.weightGrams?.takeIf { grams -> grams > 0 } }.maxOrNull()
            ExerciseTrend(
                epochDay = day,
                weightKg = weight?.div(1000.0),
                reps = daySets.mapNotNull { it.reps }.maxOrNull(),
                epleyKg = daySets.mapNotNull { estimatedOneRepMaxKg(it) }.maxOrNull(),
            )
        }

fun recentRecords(sets: List<StatSet>, today: Long, unit: WeightUnit, withinDays: Int = 28): List<RecordHit> {
    val hits = mutableListOf<RecordHit>()
    sets.sortedWith(compareBy({ it.epochDay }, { it.sessionId })).groupBy { it.exerciseId }.forEach { (_, exerciseSets) ->
        var bestWeight = 0
        var bestReps = 0
        var bestEpley = 0.0
        var bestVolume = 0.0
        var bestDuration = 0L
        var bestDistance = 0
        var bestAssist = Int.MAX_VALUE
        exerciseSets.groupBy { it.epochDay }.toSortedMap().forEach { (day, daySets) ->
            daySets.filter { countsTowardTraining(it) }.forEach { set ->
                val name = set.exerciseName
                fun add(label: String) {
                    if (day >= today - withinDays) hits += RecordHit(set.exerciseId, name, label, day)
                }
                val weight = set.weightGrams ?: 0
                if (set.tracking != TrackingType.ASSISTED && weight > bestWeight) {
                    bestWeight = weight
                    add("最大重量 ${formatWeight(weight, unit)}")
                }
                val reps = set.reps ?: 0
                if (reps > bestReps && set.tracking != TrackingType.DURATION && set.tracking != TrackingType.DISTANCE) {
                    bestReps = reps
                    add("最多次数 $reps 次")
                }
                val epley = estimatedOneRepMaxKg(set)
                if (epley != null && epley > bestEpley + 0.05) {
                    bestEpley = epley
                    add("估算 1RM ${formatNumber(if (unit == WeightUnit.KG) epley else epley * 1000 / GRAMS_PER_POUND)} ${if (unit == WeightUnit.KG) "公斤" else "磅"}")
                }
                val volume = volumeKg(set)
                if (volume > bestVolume + 0.05) {
                    bestVolume = volume
                    add("单组容量 ${formatNumber(if (unit == WeightUnit.KG) volume else volume * 1000 / GRAMS_PER_POUND)} ${if (unit == WeightUnit.KG) "公斤" else "磅"}")
                }
                val duration = set.durationMs ?: 0L
                if (set.tracking == TrackingType.DURATION && duration > bestDuration) {
                    bestDuration = duration
                    add("最长时长 ${duration / 1000} 秒")
                }
                val distance = set.distanceMm ?: 0
                if (set.tracking == TrackingType.DISTANCE && distance > bestDistance) {
                    bestDistance = distance
                    add("最长距离 ${formatNumber(distance / 1_000_000.0)} 公里")
                }
                if (set.tracking == TrackingType.ASSISTED && weight in 1 until bestAssist) {
                    bestAssist = weight
                    add("最小辅助 ${formatWeight(weight, unit)}")
                }
            }
        }
    }
    return hits.sortedByDescending { it.epochDay }
}

/** 单个动作最近一次训练里的代表数据，用来在详情页和训练页提示「上次练了多少」。 */
data class ExercisePerformance(
    val sessionId: String,
    val epochDay: Long,
    val topWeightGrams: Int?,
    val topReps: Int?,
    val sets: Int,
    val durationMs: Long?,
    val distanceMm: Int?,
)

fun recentPerformances(sets: List<StatSet>, exerciseId: String, limit: Int = 3): List<ExercisePerformance> =
    sets.filter { it.exerciseId == exerciseId && countsTowardTraining(it) }
        .groupBy { it.sessionId }
        .map { (sessionId, sessionSets) ->
            val day = sessionSets.maxOf { it.epochDay }
            val top = sessionSets
                .filter { it.reps != null || it.durationMs != null || it.distanceMm != null }
                .maxWithOrNull(compareBy({ it.weightGrams ?: 0 }, { it.reps ?: 0 }))
            ExercisePerformance(
                sessionId = sessionId,
                epochDay = day,
                topWeightGrams = top?.weightGrams?.takeIf { it > 0 },
                topReps = top?.reps,
                sets = sessionSets.size,
                durationMs = sessionSets.mapNotNull { it.durationMs }.maxOrNull(),
                distanceMm = sessionSets.mapNotNull { it.distanceMm }.maxOrNull(),
            )
        }
        .sortedWith(compareByDescending<ExercisePerformance> { it.epochDay }.thenByDescending { it.sessionId })
        .take(limit)

fun performanceSummary(performance: ExercisePerformance, unit: WeightUnit): String {
    val day = java.time.LocalDate.ofEpochDay(performance.epochDay)
        .format(java.time.format.DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA))
    return "$day · ${performanceDetail(performance, unit)} · ${performance.sets} 组"
}

/** 训练中空间有限，去掉日期，只留重量和次数。 */
fun performanceCompact(performance: ExercisePerformance, unit: WeightUnit): String =
    "上次 ${performanceDetail(performance, unit)} · ${performance.sets} 组"

private fun performanceDetail(performance: ExercisePerformance, unit: WeightUnit): String {
    return when {
        performance.topWeightGrams != null && performance.topReps != null ->
            "${gramsToInput(performance.topWeightGrams, unit)}${if (unit == WeightUnit.KG) "公斤" else "磅"} × ${performance.topReps}"
        performance.topReps != null -> "${performance.topReps} 次"
        performance.durationMs != null -> "${performance.durationMs / 1000} 秒"
        performance.distanceMm != null -> "${formatNumber(performance.distanceMm / 1_000_000.0)} 公里"
        else -> "${performance.sets} 组"
    }
}

// 中文动作库的排序：肌群、器械、类型按训练时常见的顺序排，名称按拼音。
private val MUSCLE_ORDER = listOf(
    "胸", "背", "背阔肌", "上背", "肩", "肩后束", "肱二头", "肱三头", "前臂",
    "腿", "股四头", "腘绳肌", "臀", "小腿", "核心", "下背", "后链", "髋", "髋内收", "腹股沟",
    "活动度", "握力", "心肺", "其他",
)

private val EQUIPMENT_ORDER = listOf(
    "自重", "哑铃", "杠铃", "固定器械", "绳索", "弹力带", "引体杆", "墙", "毛巾", "杠铃片", "有氧器械", "其他",
)

private val TYPE_ORDER = listOf("负重次数", "自重次数", "辅助次数", "时长", "距离时长")

fun muscleRank(name: String): Int = MUSCLE_ORDER.indexOf(name).let { if (it < 0) MUSCLE_ORDER.size else it }

fun equipmentRank(name: String): Int = EQUIPMENT_ORDER.indexOf(name).let { if (it < 0) EQUIPMENT_ORDER.size else it }

fun typeRank(name: String): Int = TYPE_ORDER.indexOf(name).let { if (it < 0) TYPE_ORDER.size else it }

fun orderedMuscles(values: Collection<String>): List<String> =
    values.distinct().sortedWith(compareBy({ muscleRank(it) }, { it }))

fun orderedEquipment(values: Collection<String>): List<String> =
    values.distinct().sortedWith(compareBy({ equipmentRank(it) }, { it }))

fun orderedTypes(values: Collection<String>): List<String> =
    values.distinct().sortedWith(compareBy({ typeRank(it) }, { it }))
