package com.superman.lianji.core.database

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val nameEn: String,
    val nameZh: String,
    val aliasesZh: String,
    val equipmentZh: String,
    val primaryMuscleZh: String,
    val secondaryZh: String,
    val exerciseType: String,
    val exerciseTypeZh: String,
    val isStretch: Boolean,
    val instructionsZh: String,
    val instructionSourceId: String?,
    val frameName: String,
    val allFrames: String,
)

@Entity(
    tableName = "exercise_frames",
    primaryKeys = ["exerciseId", "frameIndex"],
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId")],
)
data class ExerciseFrameEntity(val exerciseId: String, val frameIndex: Int, val drawableName: String)

@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val id: Int = 1,
    val revision: Int,
    val exerciseCount: Int,
    val instructionCount: Int,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE)],
)
data class FavoriteEntity(@PrimaryKey val exerciseId: String, val createdAt: Long)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val exerciseName: String,
    val trackingType: String,
    val frameName: String,
    val orderIndex: Int,
    val restSeconds: Int,
)

@Entity(
    tableName = "routine_sets",
    foreignKeys = [ForeignKey(RoutineExerciseEntity::class, ["id"], ["routineExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineExerciseId")],
)
data class RoutineSetEntity(
    @PrimaryKey val id: String,
    val routineExerciseId: String,
    val orderIndex: Int,
    val setType: String,
    val targetReps: Int?,
    val targetWeightGrams: Int?,
    val targetDurationMs: Long?,
    val targetDistanceMm: Int?,
)

@Entity(tableName = "workout_sessions", indices = [Index("localEpochDay"), Index("status")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val routineId: String?,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val localEpochDay: Long,
    val zoneId: String,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val muscleZh: String,
    val trackingType: String,
    val frameName: String,
    val orderIndex: Int,
    val restSeconds: Int,
)

@Entity(
    tableName = "set_records",
    foreignKeys = [ForeignKey(WorkoutExerciseEntity::class, ["id"], ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")],
)
data class SetEntity(
    @PrimaryKey val id: String,
    val workoutExerciseId: String,
    val orderIndex: Int,
    val setType: String,
    val reps: Int?,
    val weightGrams: Int?,
    val durationMs: Long?,
    val distanceMm: Int?,
    val rpe: Int?,
    val note: String,
    val completed: Boolean,
    val completedAtEpochMs: Long?,
)

data class FlatSetRow(
    val sessionId: String,
    val epochDay: Long,
    val durationMs: Long,
    val exerciseId: String,
    val exerciseName: String,
    val muscleZh: String,
    val trackingType: String,
    val setType: String,
    val completed: Boolean,
    val reps: Int?,
    val weightGrams: Int?,
    val setDurationMs: Long?,
    val distanceMm: Int?,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_meta WHERE id = 1")
    suspend fun meta(): CatalogMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: CatalogMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(items: List<ExerciseEntity>)

    @Query("DELETE FROM exercise_frames")
    suspend fun clearFrames()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(items: List<ExerciseFrameEntity>)

    @Query("SELECT * FROM exercises ORDER BY nameZh")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeExercise(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE slug = :slug")
    suspend fun bySlug(slug: String): ExerciseEntity?

    @Query("SELECT * FROM exercise_frames WHERE exerciseId = :id ORDER BY frameIndex")
    suspend fun frames(id: String): List<ExerciseFrameEntity>

    @Query("SELECT exerciseId FROM favorites")
    fun observeFavorites(): Flow<List<String>>

    @Query("SELECT * FROM favorites")
    suspend fun favoriteRows(): List<FavoriteEntity>

    @Query("SELECT * FROM exercises")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE exerciseId = :id")
    suspend fun isFavorite(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun favorite(item: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun favorites(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE exerciseId = :id")
    suspend fun unfavorite(id: String)

    @Query("DELETE FROM favorites WHERE exerciseId IN (:ids)")
    suspend fun unfavoriteMany(ids: List<String>)
}

@Dao
abstract class TrainingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoutine(item: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoutineSets(items: List<RoutineSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSession(item: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSets(items: List<SetEntity>)

    @Update
    abstract suspend fun updateSession(item: SessionEntity)

    @Update
    abstract suspend fun updateWorkoutExercise(item: WorkoutExerciseEntity)

    @Update
    abstract suspend fun updateSet(item: SetEntity)

    @Update
    abstract suspend fun updateRoutine(item: RoutineEntity)

    @Update
    abstract suspend fun updateRoutineExercise(item: RoutineExerciseEntity)

    @Update
    abstract suspend fun updateRoutineSet(item: RoutineSetEntity)

    @Query("SELECT * FROM routines ORDER BY updatedAt DESC")
    abstract fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    abstract fun observeRoutine(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routine_exercises WHERE routineId = :id ORDER BY orderIndex")
    abstract fun observeRoutineExercises(id: String): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_sets WHERE routineExerciseId IN (:ids) ORDER BY orderIndex")
    abstract fun observeRoutineSets(ids: List<String>): Flow<List<RoutineSetEntity>>

    @Query("SELECT * FROM routine_exercises WHERE id = :id")
    abstract suspend fun routineExercise(id: String): RoutineExerciseEntity?

    @Query("SELECT * FROM workout_sessions WHERE status = 'active' ORDER BY startedAtEpochMs DESC LIMIT 1")
    abstract fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun session(id: String): SessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract fun observeSession(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'completed' ORDER BY startedAtEpochMs DESC")
    abstract fun observeCompleted(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM workout_exercises WHERE sessionId = :id ORDER BY orderIndex")
    abstract fun observeWorkoutExercises(id: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises WHERE id = :id")
    abstract suspend fun workoutExercise(id: String): WorkoutExerciseEntity?

    @Query("SELECT * FROM set_records WHERE workoutExerciseId IN (:ids) ORDER BY orderIndex")
    abstract fun observeSets(ids: List<String>): Flow<List<SetEntity>>

    @Query("SELECT * FROM set_records WHERE id = :id")
    abstract suspend fun set(id: String): SetEntity?

    @Query("DELETE FROM set_records WHERE id = :id")
    abstract suspend fun deleteSet(id: String)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    abstract suspend fun deleteWorkoutExercise(id: String)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    abstract suspend fun deleteSession(id: String)

    @Query("DELETE FROM workout_sessions WHERE id IN (:ids)")
    abstract suspend fun deleteSessions(ids: List<String>)

    @Query("DELETE FROM set_records WHERE id IN (:ids)")
    abstract suspend fun deleteSets(ids: List<String>)

    @Query("DELETE FROM routines WHERE id = :id")
    abstract suspend fun deleteRoutine(id: String)

    @Query("DELETE FROM routine_exercises WHERE id = :id")
    abstract suspend fun deleteRoutineExercise(id: String)

    @Query("DELETE FROM routine_sets WHERE id = :id")
    abstract suspend fun deleteRoutineSet(id: String)

    @Query(
        """
        SELECT s.id AS sessionId, s.localEpochDay AS epochDay,
               COALESCE(s.endedAtEpochMs, s.startedAtEpochMs) - s.startedAtEpochMs AS durationMs,
               we.exerciseId AS exerciseId, we.exerciseName AS exerciseName, we.muscleZh AS muscleZh,
               we.trackingType AS trackingType, sr.setType AS setType, sr.completed AS completed,
               sr.reps AS reps, sr.weightGrams AS weightGrams, sr.durationMs AS setDurationMs,
               sr.distanceMm AS distanceMm
        FROM set_records sr
        JOIN workout_exercises we ON we.id = sr.workoutExerciseId
        JOIN workout_sessions s ON s.id = we.sessionId
        WHERE s.status = 'completed'
        """,
    )
    abstract fun observeFlatSets(): Flow<List<FlatSetRow>>

    @Query("DELETE FROM set_records")
    abstract suspend fun clearSets()

    @Query("DELETE FROM workout_exercises")
    abstract suspend fun clearWorkoutExercises()

    @Query("DELETE FROM workout_sessions")
    abstract suspend fun clearSessions()

    @Query("DELETE FROM routine_sets")
    abstract suspend fun clearRoutineSets()

    @Query("DELETE FROM routine_exercises")
    abstract suspend fun clearRoutineExercises()

    @Query("DELETE FROM routines")
    abstract suspend fun clearRoutines()

    @Query("DELETE FROM favorites")
    abstract suspend fun clearFavorites()

    @Transaction
    open suspend fun replaceUserContent(
        favorites: List<FavoriteEntity>,
        routines: List<RoutineEntity>,
        routineExercises: List<RoutineExerciseEntity>,
        routineSets: List<RoutineSetEntity>,
        sessions: List<SessionEntity>,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<SetEntity>,
    ) {
        clearSets()
        clearWorkoutExercises()
        clearSessions()
        clearRoutineSets()
        clearRoutineExercises()
        clearRoutines()
        clearFavorites()
        catalogFavorites(favorites)
        insertRoutines(routines)
        insertRoutineExercises(routineExercises)
        insertRoutineSets(routineSets)
        insertSessions(sessions)
        insertWorkoutExercises(exercises)
        insertSets(sets)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun catalogFavorites(items: List<FavoriteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoutines(items: List<RoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSessions(items: List<SessionEntity>)
}

@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseFrameEntity::class,
        CatalogMetaEntity::class,
        FavoriteEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        RoutineSetEntity::class,
        SessionEntity::class,
        WorkoutExerciseEntity::class,
        SetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LianjiDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun training(): TrainingDao

    companion object {
        fun create(context: Context): LianjiDatabase =
            Room.databaseBuilder(context, LianjiDatabase::class.java, "lianji.db")
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
