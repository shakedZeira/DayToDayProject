package com.daytoday.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercises",
    indices = [Index("exerciseId", unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val name: String,
    @ColumnInfo(name = "muscle_group") val muscleGroup: String,
    val equipment: String?,
    val instructions: String?,
    @ColumnInfo(name = "demo_video_url") val demoVideoUrl: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Exercise {
        return Exercise(
            id = exerciseId,
            name = name,
            muscleGroup = muscleGroup,
            equipment = equipment,
            instructions = instructions,
            demoVideoUrl = demoVideoUrl
        )
    }

    companion object {
        fun fromDomain(exercise: Exercise): ExerciseEntity {
            return ExerciseEntity(
                exerciseId = exercise.id,
                name = exercise.name,
                muscleGroup = exercise.muscleGroup,
                equipment = exercise.equipment,
                instructions = exercise.instructions,
                demoVideoUrl = exercise.demoVideoUrl
            )
        }
    }
}

data class Exercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String?,
    val instructions: String?,
    val demoVideoUrl: String?
)