package com.daytoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val startTime: Long,
    val endTime: Long?,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val setsJson: String,
    val isCompleted: Boolean
) {
    fun toModel(): com.daytoday.model.WorkoutSession = com.daytoday.data.database.WorkoutSessionEntity.toModel(this)
    
    companion object {
        fun fromModel(session: com.daytoday.model.WorkoutSession): WorkoutSessionEntity {
            return WorkoutSessionEntity(
                id = session.id,
                exerciseId = session.exerciseId,
                exerciseName = session.exerciseName,
                startTime = session.startTime,
                endTime = session.endTime,
                durationMinutes = session.durationMinutes,
                caloriesBurned = session.caloriesBurned,
                setsJson = session.sets.toJson(),
                isCompleted = session.isCompleted
            )
        }
        
        fun toModel(entity: WorkoutSessionEntity): com.daytoday.model.WorkoutSession {
            return com.daytoday.model.WorkoutSession(
                id = entity.id,
                exerciseId = entity.exerciseId,
                exerciseName = entity.exerciseName,
                startTime = entity.startTime,
                endTime = entity.endTime,
                durationMinutes = entity.durationMinutes,
                caloriesBurned = entity.caloriesBurned,
                sets = entity.setsJson.fromJson(),
                isCompleted = entity.isCompleted
            )
        }
    }
}

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val instructions: String,
    val demoVideoUrl: String?
) {
    fun toModel(): com.daytoday.model.Exercise = com.daytoday.model.Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        instructions = instructions,
        demoVideoUrl = demoVideoUrl
    )
    
    companion object {
        fun fromModel(exercise: com.daytoday.model.Exercise): ExerciseEntity {
            return ExerciseEntity(
                id = exercise.id,
                name = exercise.name,
                muscleGroup = exercise.muscleGroup,
                equipment = exercise.equipment,
                instructions = exercise.instructions,
                demoVideoUrl = exercise.demoVideoUrl
            )
        }
    }
}

@Entity(tableName = "set_records")
data class SetRecordEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Double?,
    val isCompleted: Boolean,
    val completedAt: Long?
) {
    fun toModel(): com.daytoday.model.SetRecord = com.daytoday.model.SetRecord(
        id = id,
        sessionId = sessionId,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        rpe = rpe,
        isCompleted = isCompleted,
        completedAt = completedAt
    )
    
    companion object {
        fun fromModel(setRecord: com.daytoday.model.SetRecord): SetRecordEntity {
            return SetRecordEntity(
                id = setRecord.id,
                sessionId = setRecord.sessionId,
                setNumber = setRecord.setNumber,
                weight = setRecord.weight,
                reps = setRecord.reps,
                rpe = setRecord.rpe,
                isCompleted = setRecord.isCompleted,
                completedAt = setRecord.completedAt
            )
        }
    }
}

@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val date: Long,
    val steps: Int,
    val activeCalories: Int,
    val workoutsCompleted: Int,
    val totalDurationMinutes: Int,
    val caloriesBurned: Int,
    val heartRateAvg: Int?,
    val personalBestsJson: String
) {
    fun toModel(): com.daytoday.model.DailyProgress = com.daytoday.data.database.DailyProgressEntity.toModel(this)
    
    companion object {
        fun fromModel(progress: com.daytoday.model.DailyProgress): DailyProgressEntity {
            return DailyProgressEntity(
                date = progress.date,
                steps = progress.steps,
                activeCalories = progress.activeCalories,
                workoutsCompleted = progress.workoutsCompleted,
                totalDurationMinutes = progress.totalDurationMinutes,
                caloriesBurned = progress.caloriesBurned,
                heartRateAvg = progress.heartRateAvg,
                personalBestsJson = progress.personalBests.toJson()
            )
        }
        
        fun toModel(entity: DailyProgressEntity): com.daytoday.model.DailyProgress {
            return com.daytoday.model.DailyProgress(
                date = entity.date,
                steps = entity.steps,
                activeCalories = entity.activeCalories,
                workoutsCompleted = entity.workoutsCompleted,
                totalDurationMinutes = entity.totalDurationMinutes,
                caloriesBurned = entity.caloriesBurned,
                heartRateAvg = entity.heartRateAvg,
                personalBests = entity.personalBestsJson.fromJson()
            )
        }
    }
}

@Entity(tableName = "personal_bests")
data class PersonalBestsEntity(
    @PrimaryKey val exerciseId: String,
    val exerciseName: String,
    val bestWeight: Double,
    val bestReps: Int,
    val bestVolume: Double,
    val bestOneRM: Double,
    val achievedAt: Long
) {
    fun toModel(): com.daytoday.model.PersonalBests = com.daytoday.model.PersonalBests(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        bestWeight = bestWeight,
        bestReps = bestReps,
        bestVolume = bestVolume,
        bestOneRM = bestOneRM,
        achievedAt = achievedAt
    )
    
    companion object {
        fun fromModel(pb: com.daytoday.model.PersonalBests): PersonalBestsEntity {
            return PersonalBestsEntity(
                exerciseId = pb.exerciseId,
                exerciseName = pb.exerciseName,
                bestWeight = pb.bestWeight,
                bestReps = pb.bestReps,
                bestVolume = pb.bestVolume,
                bestOneRM = pb.bestOneRM,
                achievedAt = pb.achievedAt
            )
        }
    }
}

@Entity(tableName = "weekly_progress")
data class WeeklyProgressEntity(
    @PrimaryKey val weekStart: Long,
    val workouts: Int,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val volume: Double
) {
    fun toModel(): com.daytoday.model.WeeklyProgress = com.daytoday.model.WeeklyProgress(
        weekStart = weekStart,
        workouts = workouts,
        durationMinutes = durationMinutes,
        caloriesBurned = caloriesBurned,
        volume = volume
    )
    
    companion object {
        fun fromModel(progress: com.daytoday.model.WeeklyProgress): WeeklyProgressEntity {
            return WeeklyProgressEntity(
                weekStart = progress.weekStart,
                workouts = progress.workouts,
                durationMinutes = progress.durationMinutes,
                caloriesBurned = progress.caloriesBurned,
                volume = progress.volume
            )
        }
    }
}