package com.superman.lianji.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : IllegalArgumentException(message)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val catalogRevision: Int,
    val exportedAtEpochMs: Long,
)

@Serializable
data class FavoriteBackup(val exerciseId: String, val createdAt: Long)

@Serializable
data class RoutineBackup(val id: String, val name: String, val notes: String, val createdAt: Long, val updatedAt: Long)

@Serializable
data class RoutineExerciseBackup(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val exerciseName: String,
    val trackingType: String,
    val orderIndex: Int,
    val restSeconds: Int,
)

@Serializable
data class RoutineSetBackup(
    val id: String,
    val routineExerciseId: String,
    val orderIndex: Int,
    val setType: String,
    val targetReps: Int? = null,
    val targetWeightGrams: Int? = null,
    val targetDurationMs: Long? = null,
    val targetDistanceMm: Int? = null,
)

@Serializable
data class SessionBackup(
    val id: String,
    val title: String,
    val notes: String,
    val routineId: String? = null,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val localEpochDay: Long,
    val zoneId: String,
)

@Serializable
data class WorkoutExerciseBackup(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val muscleZh: String,
    val trackingType: String,
    val orderIndex: Int,
    val restSeconds: Int,
)

@Serializable
data class SetBackup(
    val id: String,
    val workoutExerciseId: String,
    val orderIndex: Int,
    val setType: String,
    val reps: Int? = null,
    val weightGrams: Int? = null,
    val durationMs: Long? = null,
    val distanceMm: Int? = null,
    val rpe: Int? = null,
    val note: String = "",
    val completed: Boolean = false,
    val completedAtEpochMs: Long? = null,
)

@Serializable
data class UserDataBackup(
    val favorites: List<FavoriteBackup> = emptyList(),
    val routines: List<RoutineBackup> = emptyList(),
    val routineExercises: List<RoutineExerciseBackup> = emptyList(),
    val routineSets: List<RoutineSetBackup> = emptyList(),
    val sessions: List<SessionBackup> = emptyList(),
    val workoutExercises: List<WorkoutExerciseBackup> = emptyList(),
    val sets: List<SetBackup> = emptyList(),
)

@Serializable
data class SettingsBackup(
    val weightUnit: String,
    val defaultRestSeconds: Int,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val themeMode: String,
    val keepScreenOn: Boolean,
)

@Serializable
data class BackupBundle(
    val manifest: BackupManifest,
    val userData: UserDataBackup,
    val settings: SettingsBackup,
)

@Serializable
private data class Checksums(val manifest: String, val userData: String, val settings: String)

object BackupArchive {
    const val FORMAT_VERSION = 1
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; prettyPrint = true }
    private val names = setOf("manifest.json", "user_data.json", "settings.json", "checksums.json")

    fun write(output: OutputStream, bundle: BackupBundle) {
        validate(bundle)
        val manifest = json.encodeToString(bundle.manifest).toByteArray()
        val user = json.encodeToString(bundle.userData).toByteArray()
        val settings = json.encodeToString(bundle.settings).toByteArray()
        val checksums = json.encodeToString(
            Checksums(sha256(manifest), sha256(user), sha256(settings)),
        ).toByteArray()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "manifest.json", manifest)
            writeEntry(zip, "user_data.json", user)
            writeEntry(zip, "settings.json", settings)
            writeEntry(zip, "checksums.json", checksums)
        }
    }

    fun read(input: InputStream): BackupBundle {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entries.size >= 8) throw BackupException("备份条目过多")
                if (entry.isDirectory || entry.name !in names) throw BackupException("备份结构无效")
                val bytes = readLimited(zip, 8 * 1024 * 1024)
                total += bytes.size
                if (total > 20 * 1024 * 1024) throw BackupException("备份体积过大")
                entries[entry.name] = bytes
            }
        }
        if (entries.keys != names) throw BackupException("备份文件不完整")
        val checksums = decode(entries.getValue("checksums.json"), "校验信息") { json.decodeFromString<Checksums>(it) }
        if (checksums.manifest != sha256(entries.getValue("manifest.json")) ||
            checksums.userData != sha256(entries.getValue("user_data.json")) ||
            checksums.settings != sha256(entries.getValue("settings.json"))
        ) {
            throw BackupException("备份校验失败")
        }
        val bundle = BackupBundle(
            manifest = decode(entries.getValue("manifest.json"), "清单") { json.decodeFromString(it) },
            userData = decode(entries.getValue("user_data.json"), "训练数据") { json.decodeFromString(it) },
            settings = decode(entries.getValue("settings.json"), "设置") { json.decodeFromString(it) },
        )
        validate(bundle)
        return bundle
    }

    fun validate(bundle: BackupBundle) {
        val manifest = bundle.manifest
        if (manifest.formatVersion != FORMAT_VERSION) throw BackupException("不支持的备份版本")
        if (manifest.catalogRevision < 1 || manifest.exportedAtEpochMs <= 0L) throw BackupException("备份清单无效")
        val settings = bundle.settings
        if (settings.weightUnit !in setOf("KG", "LB")) throw BackupException("重量单位无效")
        if (settings.themeMode !in setOf("SYSTEM", "LIGHT", "DARK")) throw BackupException("主题设置无效")
        if (settings.defaultRestSeconds !in 5..3600) throw BackupException("休息时间超出范围")
        val data = bundle.userData
        requireIds(data.favorites.map { it.exerciseId }, "收藏")
        requireIds(data.routines.map { it.id }, "计划")
        requireIds(data.routineExercises.map { it.id }, "计划动作")
        requireIds(data.routineSets.map { it.id }, "计划组")
        requireIds(data.sessions.map { it.id }, "训练")
        requireIds(data.workoutExercises.map { it.id }, "训练动作")
        requireIds(data.sets.map { it.id }, "训练组")
        val routines = data.routines.associateBy { it.id }
        val routineExercises = data.routineExercises.associateBy { it.id }
        data.routines.forEach {
            requireText(it.name, 1, 40, "计划名称")
            requireText(it.notes, 0, 2000, "计划备注")
        }
        data.routineExercises.forEach {
            if (it.routineId !in routines) throw BackupException("计划动作缺少所属计划")
            requireText(it.exerciseId, 1, 80, "动作编号")
            requireText(it.exerciseName, 1, 40, "动作名称")
            if (it.orderIndex < 0 || it.restSeconds !in 0..3600) throw BackupException("计划动作数据超出范围")
        }
        data.routineSets.forEach { set ->
            if (set.routineExerciseId !in routineExercises) throw BackupException("计划组缺少所属动作")
            validateNumbers(set.targetReps, set.targetWeightGrams, set.targetDurationMs, set.targetDistanceMm, null)
        }
        val sessions = data.sessions.associateBy { it.id }
        val workoutExercises = data.workoutExercises.associateBy { it.id }
        data.sessions.forEach {
            if (it.status !in setOf("active", "completed")) throw BackupException("训练状态无效")
            requireText(it.title, 1, 40, "训练标题")
            requireText(it.notes, 0, 4000, "训练备注")
            if (it.localEpochDay < 0 || it.startedAtEpochMs <= 0L) throw BackupException("训练时间无效")
            runCatching { ZoneId.of(it.zoneId) }.getOrElse { throw BackupException("时区无效") }
        }
        data.workoutExercises.forEach {
            if (it.sessionId !in sessions) throw BackupException("训练动作缺少所属训练")
            requireText(it.exerciseName, 1, 40, "动作名称")
            requireText(it.muscleZh, 1, 20, "肌群")
            if (it.orderIndex < 0 || it.restSeconds !in 0..3600) throw BackupException("训练动作数据超出范围")
        }
        data.sets.forEach { set ->
            if (set.workoutExerciseId !in workoutExercises) throw BackupException("训练组缺少所属动作")
            if (set.setType !in setOf("normal", "warmup", "drop", "failure")) throw BackupException("组类型无效")
            validateNumbers(set.reps, set.weightGrams, set.durationMs, set.distanceMm, set.rpe)
            requireText(set.note, 0, 500, "组备注")
        }
    }

    private fun validateNumbers(reps: Int?, weight: Int?, duration: Long?, distance: Int?, rpe: Int?) {
        if (reps != null && reps !in 0..2000) throw BackupException("次数超出范围")
        if (weight != null && weight !in 0..500_000) throw BackupException("重量超出范围")
        if (duration != null && duration !in 0L..(24 * 60 * 60 * 1000L)) throw BackupException("时长超出范围")
        if (distance != null && distance !in 0..1_000_000_000) throw BackupException("距离超出范围")
        if (rpe != null && rpe !in 1..10) throw BackupException("主观用力程度超出范围")
    }

    private fun requireIds(ids: List<String>, label: String) {
        if (ids.any { it.isBlank() || it.length > 80 }) throw BackupException("${label}编号无效")
        if (ids.size != ids.toSet().size) throw BackupException("${label}编号重复")
    }

    private fun requireText(value: String, min: Int, max: Int, label: String) {
        if (value.length !in min..max) throw BackupException("${label}长度无效")
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw BackupException("单个备份条目过大")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private inline fun <T> decode(bytes: ByteArray, label: String, parse: (String) -> T): T =
        runCatching { parse(bytes.toString(Charsets.UTF_8)) }.getOrElse { throw BackupException("${label}无法读取") }
}
