package com.superman.lianji.core.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.superman.lianji.BuildConfig
import com.superman.lianji.core.database.ExerciseEntity
import com.superman.lianji.core.database.ExerciseFrameEntity
import com.superman.lianji.core.database.FavoriteEntity
import com.superman.lianji.core.database.CatalogMetaEntity
import com.superman.lianji.core.database.LianjiDatabase
import com.superman.lianji.core.database.RoutineEntity
import com.superman.lianji.core.database.RoutineExerciseEntity
import com.superman.lianji.core.database.RoutineSetEntity
import com.superman.lianji.core.database.SessionEntity
import com.superman.lianji.core.database.SetEntity
import com.superman.lianji.core.database.WorkoutExerciseEntity
import com.superman.lianji.core.domain.BackupArchive
import com.superman.lianji.core.domain.BackupBundle
import com.superman.lianji.core.domain.BackupManifest
import com.superman.lianji.core.domain.FavoriteBackup
import com.superman.lianji.core.domain.RestSnapshot
import com.superman.lianji.core.domain.RoutineBackup
import com.superman.lianji.core.domain.RoutineExerciseBackup
import com.superman.lianji.core.domain.RoutineSetBackup
import com.superman.lianji.core.domain.SessionBackup
import com.superman.lianji.core.domain.SessionMarker
import com.superman.lianji.core.domain.SetBackup
import com.superman.lianji.core.domain.SettingsBackup
import com.superman.lianji.core.domain.StatSet
import com.superman.lianji.core.domain.UserDataBackup
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.WorkoutExerciseBackup
import com.superman.lianji.core.domain.kindOf
import com.superman.lianji.core.domain.trackingOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "lianji_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val rest: RestSnapshot? = null,
)

data class ExerciseSummary(
    val id: String,
    val nameZh: String,
    val muscleZh: String,
    val equipmentZh: String,
    val typeZh: String,
    val frame: String,
    val favorite: Boolean,
    val hasDetailedSteps: Boolean,
)

data class ExerciseDetail(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val muscleZh: String,
    val secondaryZh: String,
    val equipmentZh: String,
    val typeZh: String,
    val tracking: String,
    val stretch: Boolean,
    val frames: List<String>,
    val steps: List<String>,
    val sourceId: String?,
    val favorite: Boolean,
)

data class RoutineDetails(val routine: RoutineEntity, val exercises: List<Pair<RoutineExerciseEntity, List<RoutineSetEntity>>>)

data class WorkoutDetails(val session: SessionEntity, val exercises: List<Pair<WorkoutExerciseEntity, List<SetEntity>>>)

data class SetEdit(
    val id: String,
    val setType: String,
    val reps: Int?,
    val weightGrams: Int?,
    val durationMs: Long?,
    val distanceMm: Int?,
    val rpe: Int?,
    val note: String,
    val completed: Boolean,
)

data class PickTarget(val kind: String, val ownerId: String, val replaceId: String? = null)

class SettingsRepository(private val context: Context) {
    private val unitKey = stringPreferencesKey("unit")
    private val restKey = intPreferencesKey("rest")
    private val soundKey = booleanPreferencesKey("sound")
    private val vibrationKey = booleanPreferencesKey("vibration")
    private val themeKey = stringPreferencesKey("theme")
    private val screenKey = booleanPreferencesKey("screen")
    private val restDeadlineKey = longPreferencesKey("rest_deadline")
    private val restDurationKey = intPreferencesKey("rest_duration")
    private val restWallKey = longPreferencesKey("rest_wall")
    private val restBootKey = intPreferencesKey("rest_boot")
    private val restSessionKey = stringPreferencesKey("rest_session")
    private val restNameKey = stringPreferencesKey("rest_name")

    val settings: Flow<UserSettings> = context.settingsStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val deadline = prefs[restDeadlineKey]
            UserSettings(
                weightUnit = if (prefs[unitKey] == "LB") WeightUnit.LB else WeightUnit.KG,
                defaultRestSeconds = prefs[restKey] ?: 90,
                soundEnabled = prefs[soundKey] ?: true,
                vibrationEnabled = prefs[vibrationKey] ?: true,
                themeMode = when (prefs[themeKey]) {
                    "LIGHT" -> ThemeMode.LIGHT
                    "DARK" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
                keepScreenOn = prefs[screenKey] ?: true,
                rest = if (deadline == null) null else RestSnapshot(
                    deadline, prefs[restDurationKey] ?: 90, prefs[restWallKey] ?: 0L,
                    prefs[restBootKey] ?: -1, prefs[restSessionKey].orEmpty(), prefs[restNameKey].orEmpty(),
                ),
            )
        }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val current = settings.first()
        val next = transform(current)
        context.settingsStore.edit { prefs ->
            prefs[unitKey] = next.weightUnit.name
            prefs[restKey] = next.defaultRestSeconds
            prefs[soundKey] = next.soundEnabled
            prefs[vibrationKey] = next.vibrationEnabled
            prefs[themeKey] = next.themeMode.name
            prefs[screenKey] = next.keepScreenOn
        }
    }

    suspend fun setRest(snapshot: RestSnapshot?) {
        context.settingsStore.edit { prefs ->
            if (snapshot == null) {
                prefs.remove(restDeadlineKey)
                prefs.remove(restDurationKey)
                prefs.remove(restWallKey)
                prefs.remove(restBootKey)
                prefs.remove(restSessionKey)
                prefs.remove(restNameKey)
            } else {
                prefs[restDeadlineKey] = snapshot.deadlineElapsedRealtime
                prefs[restDurationKey] = snapshot.durationSeconds
                prefs[restWallKey] = snapshot.wallDeadlineEpochMs
                prefs[restBootKey] = snapshot.bootCount
                prefs[restSessionKey] = snapshot.sessionId
                prefs[restNameKey] = snapshot.exerciseName
            }
        }
    }
}

class ExerciseRepository(private val dao: com.superman.lianji.core.database.CatalogDao) {
    fun observe(query: String, muscle: String, equipment: String, type: String, favoritesOnly: Boolean): Flow<List<ExerciseSummary>> =
        combine(dao.observeExercises(), dao.observeFavorites()) { exercises, favorites ->
            val favoriteIds = favorites.toSet()
            val needle = query.trim()
            exercises.filter { item ->
                (!favoritesOnly || item.id in favoriteIds) &&
                    (muscle.isBlank() || item.primaryMuscleZh == muscle) &&
                    (equipment.isBlank() || item.equipmentZh == equipment) &&
                    (type.isBlank() || item.exerciseTypeZh == type) &&
                    (needle.isBlank() || listOf(item.nameZh, item.nameEn, item.aliasesZh, item.primaryMuscleZh, item.equipmentZh)
                        .any { it.contains(needle, ignoreCase = true) })
            }.map { it.toSummary(it.id in favoriteIds) }
                .sortedWith(compareByDescending<ExerciseSummary> { it.favorite }.thenBy { it.nameZh })
        }

    fun observeDetail(id: String): Flow<ExerciseDetail?> =
        combine(dao.observeExercise(id), dao.observeFavorites()) { exercise, favorites ->
            exercise?.toDetail(exercise.id in favorites)
        }

    suspend fun toggleFavorite(id: String) {
        if (dao.isFavorite(id) > 0) dao.unfavorite(id) else dao.favorite(FavoriteEntity(id, System.currentTimeMillis()))
    }

    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean) {
        if (ids.isEmpty()) return
        if (favorite) dao.favorites(ids.map { FavoriteEntity(it, System.currentTimeMillis()) })
        else dao.unfavoriteMany(ids.toList())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingRepository(
    private val dao: com.superman.lianji.core.database.TrainingDao,
    private val catalog: com.superman.lianji.core.database.CatalogDao,
) {
    fun observeActive(): Flow<SessionEntity?> = dao.observeActive()
    fun observeRoutines(): Flow<List<RoutineEntity>> = dao.observeRoutines()
    fun observeHistory(): Flow<List<SessionEntity>> = dao.observeCompleted()
    fun observeFlat(): Flow<List<StatSet>> = dao.observeFlatSets().map { rows ->
        rows.map {
            StatSet(it.sessionId, it.epochDay, it.exerciseId, it.exerciseName, it.muscleZh, trackingOf(it.trackingType), kindOf(it.setType), it.completed, it.reps, it.weightGrams, it.setDurationMs, it.distanceMm)
        }
    }

    fun observeSessionMarkers(): Flow<List<SessionMarker>> = dao.observeCompleted().map { sessions ->
        sessions.map { SessionMarker(it.id, it.localEpochDay, ((it.endedAtEpochMs ?: it.startedAtEpochMs) - it.startedAtEpochMs).coerceAtLeast(0)) }
    }

    fun observeRoutine(id: String): Flow<RoutineDetails?> = dao.observeRoutine(id).flatMapLatest { routine ->
        if (routine == null) flowOf(null) else dao.observeRoutineExercises(id).flatMapLatest { exercises ->
            if (exercises.isEmpty()) flowOf(RoutineDetails(routine, emptyList()))
            else dao.observeRoutineSets(exercises.map { it.id }).map { sets ->
                RoutineDetails(routine, exercises.map { exercise -> exercise to sets.filter { it.routineExerciseId == exercise.id } })
            }
        }
    }

    fun observeWorkout(id: String): Flow<WorkoutDetails?> = dao.observeSession(id).flatMapLatest { session ->
        if (session == null) flowOf(null) else dao.observeWorkoutExercises(id).flatMapLatest { exercises ->
            if (exercises.isEmpty()) flowOf(WorkoutDetails(session, emptyList()))
            else dao.observeSets(exercises.map { it.id }).map { sets ->
                WorkoutDetails(session, exercises.map { exercise -> exercise to sets.filter { it.workoutExerciseId == exercise.id } })
            }
        }
    }

    suspend fun createRoutine(name: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertRoutine(RoutineEntity(id, name.ifBlank { "未命名计划" }, "", now, now))
        return id
    }

    suspend fun updateRoutine(id: String, name: String, notes: String) {
        val current = dao.observeRoutine(id).first() ?: return
        // 这里必须用 UPDATE：@Insert(REPLACE) 会先删父行再插入，级联删掉计划里的动作。
        dao.updateRoutine(current.copy(name = name.ifBlank { current.name }, notes = notes, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicateRoutine(id: String): String? {
        val details = observeRoutine(id).first() ?: return null
        val copyId = createRoutine(details.routine.name + " 副本")
        details.exercises.forEach { (exercise, sets) ->
            val newExercise = exercise.copy(id = UUID.randomUUID().toString(), routineId = copyId)
            dao.insertRoutineExercises(listOf(newExercise))
            dao.insertRoutineSets(sets.map { it.copy(id = UUID.randomUUID().toString(), routineExerciseId = newExercise.id) })
        }
        return copyId
    }

    suspend fun deleteRoutine(id: String) = dao.deleteRoutine(id)

    suspend fun addExerciseToRoutine(routineId: String, exerciseId: String, restSeconds: Int) {
        val exercise = catalog.observeExercise(exerciseId).first() ?: return
        val existing = dao.observeRoutineExercises(routineId).first()
        val id = UUID.randomUUID().toString()
        dao.insertRoutineExercises(listOf(exercise.toRoutineExercise(id, routineId, existing.size, restSeconds)))
        dao.insertRoutineSets(listOf(emptyRoutineSet(id, 0)))
        touchRoutine(routineId)
    }

    suspend fun updateRoutineSet(exerciseId: String, setId: String, reps: Int?, weightGrams: Int?) {
        val current = dao.observeRoutineSets(listOf(exerciseId)).first().firstOrNull { it.id == setId } ?: return
        dao.updateRoutineSet(current.copy(targetReps = reps, targetWeightGrams = weightGrams))
    }

    suspend fun updateRoutineRest(exerciseId: String, restSeconds: Int) {
        val current = dao.routineExercise(exerciseId) ?: return
        dao.updateRoutineExercise(current.copy(restSeconds = restSeconds.coerceIn(0, 3600)))
    }

    suspend fun deleteRoutineSet(id: String) = dao.deleteRoutineSet(id)

    suspend fun addRoutineSet(exerciseId: String) {
        val sets = dao.observeRoutineSets(listOf(exerciseId)).first()
        val last = sets.maxByOrNull { it.orderIndex }
        dao.insertRoutineSets(listOf((last ?: emptyRoutineSet(exerciseId, -1)).copy(id = UUID.randomUUID().toString(), orderIndex = (last?.orderIndex ?: -1) + 1)))
    }

    suspend fun deleteRoutineExercise(id: String) = dao.deleteRoutineExercise(id)

    suspend fun moveRoutineExercise(routineId: String, exerciseId: String, delta: Int) {
        val items = dao.observeRoutineExercises(routineId).first()
        val index = items.indexOfFirst { it.id == exerciseId }
        val target = index + delta
        if (index < 0 || target !in items.indices) return
        val left = items[index]
        val right = items[target]
        dao.updateRoutineExercise(left.copy(orderIndex = right.orderIndex))
        dao.updateRoutineExercise(right.copy(orderIndex = left.orderIndex))
    }

    suspend fun ensureSession(title: String): String {
        dao.observeActive().first()?.let { return it.id }
        return createSession(title, null)
    }

    suspend fun startFromRoutine(routineId: String, restSeconds: Int): String {
        dao.observeActive().first()?.let { return it.id }
        val details = observeRoutine(routineId).first() ?: return ensureSession("自由训练")
        val sessionId = createSession(details.routine.name, routineId)
        details.exercises.forEach { (exercise, targets) ->
            val source = catalog.observeExercise(exercise.exerciseId).first()
            val workoutExerciseId = UUID.randomUUID().toString()
            dao.insertWorkoutExercises(listOf(WorkoutExerciseEntity(workoutExerciseId, sessionId, exercise.exerciseId, source?.nameZh ?: exercise.exerciseName, source?.primaryMuscleZh ?: "其他", source?.exerciseType ?: exercise.trackingType, source?.frameName ?: exercise.frameName, exercise.orderIndex, exercise.restSeconds)))
            val sets = targets.ifEmpty { listOf(emptyRoutineSet(exercise.id, 0)) }
            dao.insertSets(sets.mapIndexed { index, target -> target.toWorkoutSet(workoutExerciseId, index) })
        }
        return sessionId
    }

    suspend fun addExerciseToSession(sessionId: String, exerciseId: String, restSeconds: Int) {
        val exercise = catalog.observeExercise(exerciseId).first() ?: return
        val existing = dao.observeWorkoutExercises(sessionId).first()
        val id = UUID.randomUUID().toString()
        dao.insertWorkoutExercises(listOf(exercise.toWorkout(id, sessionId, restSeconds).copy(orderIndex = existing.size)))
        dao.insertSets(listOf(emptySet(id, 0)))
    }

    suspend fun replaceExercise(workoutExerciseId: String, exerciseId: String) {
        val current = dao.workoutExercise(workoutExerciseId) ?: return
        val exercise = catalog.observeExercise(exerciseId).first() ?: return
        dao.updateWorkoutExercise(current.copy(exerciseId = exercise.id, exerciseName = exercise.nameZh, muscleZh = exercise.primaryMuscleZh, trackingType = exercise.exerciseType, frameName = exercise.frameName))
    }

    suspend fun addSet(workoutExerciseId: String) {
        val sets = dao.observeSets(listOf(workoutExerciseId)).first()
        val last = sets.maxByOrNull { it.orderIndex }
        // 新组沿用上一组的重量、次数和类型，少输一次数字。
        dao.insertSets(listOf((last ?: emptySet(workoutExerciseId, -1)).copy(id = UUID.randomUUID().toString(), orderIndex = (last?.orderIndex ?: -1) + 1, completed = false, completedAtEpochMs = null, note = "")))
    }

    suspend fun updateSet(edit: SetEdit) {
        val current = dao.set(edit.id) ?: return
        dao.updateSet(current.copy(setType = edit.setType, reps = edit.reps, weightGrams = edit.weightGrams, durationMs = edit.durationMs, distanceMm = edit.distanceMm, rpe = edit.rpe, note = edit.note, completed = edit.completed, completedAtEpochMs = if (edit.completed) current.completedAtEpochMs ?: System.currentTimeMillis() else null))
    }

    suspend fun deleteSet(id: String) = dao.deleteSet(id)
    suspend fun deleteWorkoutExercise(id: String) = dao.deleteWorkoutExercise(id)

    suspend fun moveWorkoutExercise(sessionId: String, exerciseId: String, delta: Int) {
        val items = dao.observeWorkoutExercises(sessionId).first()
        val index = items.indexOfFirst { it.id == exerciseId }
        val target = index + delta
        if (index < 0 || target !in items.indices) return
        val left = items[index]
        val right = items[target]
        dao.updateWorkoutExercise(left.copy(orderIndex = right.orderIndex))
        dao.updateWorkoutExercise(right.copy(orderIndex = left.orderIndex))
    }

    suspend fun updateSessionText(id: String, title: String, notes: String) {
        val current = dao.session(id) ?: return
        dao.updateSession(current.copy(title = title.ifBlank { current.title }, notes = notes))
    }

    suspend fun finish(id: String) {
        val current = dao.session(id) ?: return
        if (current.status == "completed") return
        dao.updateSession(current.copy(status = "completed", endedAtEpochMs = System.currentTimeMillis()))
    }

    suspend fun deleteSession(id: String) = dao.deleteSession(id)

    suspend fun deleteSessions(ids: Collection<String>) {
        if (ids.isNotEmpty()) dao.deleteSessions(ids.toList())
    }

    suspend fun addExercisesToSession(sessionId: String, exerciseIds: List<String>, restSeconds: Int) {
        var order = dao.observeWorkoutExercises(sessionId).first().size
        exerciseIds.forEach { exerciseId ->
            val exercise = catalog.observeExercise(exerciseId).first() ?: return@forEach
            val id = UUID.randomUUID().toString()
            dao.insertWorkoutExercises(listOf(exercise.toWorkout(id, sessionId, restSeconds).copy(orderIndex = order++)))
            dao.insertSets(listOf(emptySet(id, 0)))
        }
    }

    suspend fun addExercisesToRoutine(routineId: String, exerciseIds: List<String>, restSeconds: Int) {
        var order = dao.observeRoutineExercises(routineId).first().size
        exerciseIds.forEach { exerciseId ->
            val exercise = catalog.observeExercise(exerciseId).first() ?: return@forEach
            val id = UUID.randomUUID().toString()
            dao.insertRoutineExercises(listOf(exercise.toRoutineExercise(id, routineId, order++, restSeconds)))
            dao.insertRoutineSets(listOf(emptyRoutineSet(id, 0)))
        }
        if (exerciseIds.isNotEmpty()) touchRoutine(routineId)
    }

    suspend fun replaceRoutineExercise(routineExerciseId: String, exerciseId: String) {
        val current = dao.routineExercise(routineExerciseId) ?: return
        val exercise = catalog.observeExercise(exerciseId).first() ?: return
        dao.updateRoutineExercise(
            current.copy(
                exerciseId = exercise.id,
                exerciseName = exercise.nameZh,
                trackingType = exercise.exerciseType,
                frameName = exercise.frameName,
            ),
        )
    }

    /** 撤销删除一组的入口，把原来的实体按原 id 写回。 */
    suspend fun restoreSet(set: SetEntity) = dao.insertSets(listOf(set))

    suspend fun restoreSets(sets: List<SetEntity>) {
        if (sets.isNotEmpty()) dao.insertSets(sets)
    }

    suspend fun restoreWorkoutExercise(exercise: WorkoutExerciseEntity, sets: List<SetEntity>) {
        dao.insertWorkoutExercises(listOf(exercise))
        if (sets.isNotEmpty()) dao.insertSets(sets)
    }

    suspend fun restoreRoutineExercise(exercise: RoutineExerciseEntity, sets: List<RoutineSetEntity>) {
        dao.insertRoutineExercises(listOf(exercise))
        if (sets.isNotEmpty()) dao.insertRoutineSets(sets)
    }

    suspend fun restoreRoutine(details: RoutineDetails) {
        dao.insertRoutine(details.routine)
        val exercises = details.exercises.map { it.first }
        if (exercises.isNotEmpty()) dao.insertRoutineExercises(exercises)
        val sets = details.exercises.flatMap { it.second }
        if (sets.isNotEmpty()) dao.insertRoutineSets(sets)
    }

    suspend fun restoreRoutineSet(set: RoutineSetEntity) = dao.insertRoutineSets(listOf(set))

    suspend fun restoreSession(details: WorkoutDetails) {
        dao.insertSession(details.session)
        val exercises = details.exercises.map { it.first }
        if (exercises.isNotEmpty()) dao.insertWorkoutExercises(exercises)
        val sets = details.exercises.flatMap { it.second }
        if (sets.isNotEmpty()) dao.insertSets(sets)
    }

    suspend fun completeAllSets(workoutExerciseId: String) {
        val now = System.currentTimeMillis()
        val sets = dao.observeSets(listOf(workoutExerciseId)).first()
        sets.filterNot { it.completed }.forEach { set ->
            dao.updateSet(set.copy(completed = true, completedAtEpochMs = now))
        }
    }

    /** 从一场已完成的训练「再来一次」：沿用动作和每组数据，状态重置为未完成。 */
    suspend fun startFromSession(sourceSessionId: String): String? {
        val details = observeWorkout(sourceSessionId).first() ?: return null
        val active = dao.observeActive().first()
        val sessionId = active?.id ?: createSession(details.session.title, null)
        var order = dao.observeWorkoutExercises(sessionId).first().size
        details.exercises.forEach { (exercise, sets) ->
            val copy = exercise.copy(id = UUID.randomUUID().toString(), sessionId = sessionId, orderIndex = order++)
            dao.insertWorkoutExercises(listOf(copy))
            val copiedSets = sets.map { set ->
                set.copy(
                    id = UUID.randomUUID().toString(),
                    workoutExerciseId = copy.id,
                    completed = false,
                    completedAtEpochMs = null,
                )
            }
            if (copiedSets.isNotEmpty()) dao.insertSets(copiedSets)
        }
        return sessionId
    }

    suspend fun insertTemplate(name: String, slugs: List<String>, restSeconds: Int) {
        val id = createRoutine(name)
        slugs.forEach { slug -> catalog.bySlug(slug)?.let { addExerciseToRoutine(id, it.id, restSeconds) } }
    }

    private suspend fun createSession(title: String, routineId: String?): String {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val id = UUID.randomUUID().toString()
        dao.insertSession(SessionEntity(id, title.ifBlank { "自由训练" }, "", routineId, "active", now, null, day, zone.id))
        return id
    }

    private suspend fun touchRoutine(id: String) {
        val current = dao.observeRoutine(id).first() ?: return
        dao.updateRoutine(current.copy(updatedAt = System.currentTimeMillis()))
    }
}

class BackupRepository(
    private val context: Context,
    private val database: LianjiDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun exportTo(uri: Uri) {
        val dao = database.training()
        val routines = dao.observeRoutines().first()
        val routineExercises = routines.flatMap { dao.observeRoutineExercises(it.id).first() }
        val routineSets = if (routineExercises.isEmpty()) emptyList() else dao.observeRoutineSets(routineExercises.map { it.id }).first()
        val sessions = dao.observeCompleted().first() + listOfNotNull(dao.observeActive().first())
        val workoutExercises = sessions.flatMap { dao.observeWorkoutExercises(it.id).first() }
        val sets = if (workoutExercises.isEmpty()) emptyList() else dao.observeSets(workoutExercises.map { it.id }).first()
        val favorites = database.catalog().favoriteRows()
        val current = settings.settings.first()
        val bundle = BackupBundle(
            BackupManifest(BackupArchive.FORMAT_VERSION, BuildConfig.VERSION_NAME, 1, System.currentTimeMillis()),
            UserDataBackup(
                favorites.map { FavoriteBackup(it.exerciseId, it.createdAt) },
                routines.map { RoutineBackup(it.id, it.name, it.notes, it.createdAt, it.updatedAt) },
                routineExercises.map { RoutineExerciseBackup(it.id, it.routineId, it.exerciseId, it.exerciseName, it.trackingType, it.orderIndex, it.restSeconds) },
                routineSets.map { RoutineSetBackup(it.id, it.routineExerciseId, it.orderIndex, it.setType, it.targetReps, it.targetWeightGrams, it.targetDurationMs, it.targetDistanceMm) },
                sessions.map { SessionBackup(it.id, it.title, it.notes, it.routineId, it.status, it.startedAtEpochMs, it.endedAtEpochMs, it.localEpochDay, it.zoneId) },
                workoutExercises.map { WorkoutExerciseBackup(it.id, it.sessionId, it.exerciseId, it.exerciseName, it.muscleZh, it.trackingType, it.orderIndex, it.restSeconds) },
                sets.map { SetBackup(it.id, it.workoutExerciseId, it.orderIndex, it.setType, it.reps, it.weightGrams, it.durationMs, it.distanceMm, it.rpe, it.note, it.completed, it.completedAtEpochMs) },
            ),
            SettingsBackup(current.weightUnit.name, current.defaultRestSeconds, current.soundEnabled, current.vibrationEnabled, current.themeMode.name, current.keepScreenOn),
        )
        context.contentResolver.openOutputStream(uri)?.use { BackupArchive.write(it, bundle) } ?: error("无法写入备份")
    }

    suspend fun importFrom(uri: Uri) {
        val bundle = context.contentResolver.openInputStream(uri)?.use { BackupArchive.read(it) } ?: error("无法读取备份")
        val data = bundle.userData
        val frames = database.catalog().allExercises().associate { it.id to it.frameName }
        database.training().replaceUserContent(
            data.favorites.map { FavoriteEntity(it.exerciseId, it.createdAt) },
            data.routines.map { RoutineEntity(it.id, it.name, it.notes, it.createdAt, it.updatedAt) },
            data.routineExercises.map { RoutineExerciseEntity(it.id, it.routineId, it.exerciseId, it.exerciseName, it.trackingType, frames[it.exerciseId].orEmpty(), it.orderIndex, it.restSeconds) },
            data.routineSets.map { RoutineSetEntity(it.id, it.routineExerciseId, it.orderIndex, it.setType, it.targetReps, it.targetWeightGrams, it.targetDurationMs, it.targetDistanceMm) },
            data.sessions.map { SessionEntity(it.id, it.title, it.notes, it.routineId, it.status, it.startedAtEpochMs, it.endedAtEpochMs, it.localEpochDay, it.zoneId) },
            data.workoutExercises.map { WorkoutExerciseEntity(it.id, it.sessionId, it.exerciseId, it.exerciseName, it.muscleZh, it.trackingType, frames[it.exerciseId].orEmpty(), it.orderIndex, it.restSeconds) },
            data.sets.map { SetEntity(it.id, it.workoutExerciseId, it.orderIndex, it.setType, it.reps, it.weightGrams, it.durationMs, it.distanceMm, it.rpe, it.note, it.completed, it.completedAtEpochMs) },
        )
        settings.update {
            it.copy(
                weightUnit = if (bundle.settings.weightUnit == "LB") WeightUnit.LB else WeightUnit.KG,
                defaultRestSeconds = bundle.settings.defaultRestSeconds,
                soundEnabled = bundle.settings.soundEnabled,
                vibrationEnabled = bundle.settings.vibrationEnabled,
                themeMode = when (bundle.settings.themeMode) {
                    "LIGHT" -> ThemeMode.LIGHT
                    "DARK" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
                keepScreenOn = bundle.settings.keepScreenOn,
            )
        }
    }
}

class Feedback(private val context: Context) {
    fun bootCount(): Int = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        tone.release()
    }

    fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(160)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database = LianjiDatabase.create(appContext)
    val settings = SettingsRepository(appContext)
    val exercises = ExerciseRepository(database.catalog())
    val training = TrainingRepository(database.training(), database.catalog())
    val backup = BackupRepository(appContext, database, settings)
    val feedback = Feedback(appContext)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { CatalogSeeder(appContext, database).seed() }
    }
}

@Serializable
private data class CatalogFile(val revision: Int, val exercises: List<CatalogExercise>)

@Serializable
private data class CatalogExercise(
    val id: String,
    val slug: String,
    val nameEn: String,
    val nameZh: String,
    val aliasesZh: List<String> = emptyList(),
    val equipmentZh: String,
    val primaryMuscleZh: String,
    val secondaryMuscles: List<Labeled> = emptyList(),
    val exerciseType: String,
    val exerciseTypeZh: String,
    val isStretch: Boolean,
    val frames: List<String>,
    val instructionsZh: List<String> = emptyList(),
    val instructionSourceId: String? = null,
)

@Serializable
private data class Labeled(val en: String, val zh: String)

private class CatalogSeeder(private val context: Context, private val database: LianjiDatabase) {
    suspend fun seed() {
        val text = context.assets.open("catalog/exercises_zh.json").bufferedReader().use { it.readText() }
        val catalog = Json { ignoreUnknownKeys = true }.decodeFromString<CatalogFile>(text)
        val instructions = catalog.exercises.count { it.instructionsZh.isNotEmpty() }
        val meta = database.catalog().meta()
        if (meta?.revision == catalog.revision && meta.exerciseCount == catalog.exercises.size && meta.instructionCount == instructions) return
        val json = Json
        database.catalog().upsertExercises(catalog.exercises.map { item ->
            ExerciseEntity(item.id, item.slug, item.nameEn, item.nameZh, item.aliasesZh.joinToString(" "), item.equipmentZh, item.primaryMuscleZh, item.secondaryMuscles.joinToString("、") { it.zh }, item.exerciseType, item.exerciseTypeZh, item.isStretch, json.encodeToString(item.instructionsZh), item.instructionSourceId, item.frames.first(), item.frames.joinToString(" "))
        })
        database.catalog().clearFrames()
        database.catalog().insertFrames(catalog.exercises.flatMap { item -> item.frames.mapIndexed { index, name -> ExerciseFrameEntity(item.id, index + 1, name) } })
        database.catalog().upsertMeta(CatalogMetaEntity(revision = catalog.revision, exerciseCount = catalog.exercises.size, instructionCount = instructions))
    }
}

private fun ExerciseEntity.toSummary(favorite: Boolean) = ExerciseSummary(
    id = id,
    nameZh = nameZh,
    muscleZh = primaryMuscleZh,
    equipmentZh = equipmentZh,
    typeZh = exerciseTypeZh,
    frame = frameName,
    favorite = favorite,
    hasDetailedSteps = instructionSourceId != null,
)

private fun ExerciseEntity.toDetail(favorite: Boolean) = ExerciseDetail(id, nameZh, nameEn, primaryMuscleZh, secondaryZh, equipmentZh, exerciseTypeZh, exerciseType, isStretch, allFrames.split(" ").filter { it.isNotBlank() }, runCatching { Json.decodeFromString<List<String>>(instructionsZh) }.getOrDefault(emptyList()), instructionSourceId, favorite)

private fun ExerciseEntity.toRoutineExercise(id: String, routineId: String, order: Int, restSeconds: Int) =
    RoutineExerciseEntity(id, routineId, this.id, nameZh, exerciseType, frameName, order, restSeconds)

private fun ExerciseEntity.toWorkout(id: String, sessionId: String, restSeconds: Int) =
    WorkoutExerciseEntity(id, sessionId, this.id, nameZh, primaryMuscleZh, exerciseType, frameName, 0, restSeconds)

private fun emptyRoutineSet(exerciseId: String, order: Int) = RoutineSetEntity(UUID.randomUUID().toString(), exerciseId, order, "normal", 8, null, null, null)

private fun emptySet(exerciseId: String, order: Int) = SetEntity(UUID.randomUUID().toString(), exerciseId, order, "normal", null, null, null, null, null, "", false, null)

private fun RoutineSetEntity.toWorkoutSet(workoutExerciseId: String, order: Int) =
    SetEntity(UUID.randomUUID().toString(), workoutExerciseId, order, setType, targetReps, targetWeightGrams, targetDurationMs, targetDistanceMm, null, "", false, null)

