package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.Json

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index("sessionId", unique = true),
        Index("startTime")
    ]
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Int,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @ColumnInfo(name = "sets_json") val setsJson: String,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): WorkoutSession {
        return WorkoutSession(
            id = sessionId,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            caloriesBurned = caloriesBurned,
            isCompleted = isCompleted,
            sets = Json.decodeFromString<List<SetRecord>>(setsJson)
        )
    }

    companion object {
        fun fromDomain(session: WorkoutSession): WorkoutSessionEntity {
            return WorkoutSessionEntity(
                sessionId = session.id,
                exerciseId = session.exerciseId,
                exerciseName = session.exerciseName,
                startTime = session.startTime,
                endTime = session.endTime,
                durationMinutes = session.durationMinutes,
                caloriesBurned = session.caloriesBurned,
                isCompleted = session.isCompleted,
                setsJson = Json.encodeToString(session.sets)
            )
        }
    }
}

@kotlinx.serialization.Serializable
data class WorkoutSession(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val isCompleted: Boolean,
    val sets: List<SetRecord>
)

@kotlinx.serialization.Serializable
data class SetRecord(
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Double?,
    val isCompleted: Boolean,
    val completedAt: Long?
)