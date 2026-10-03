package com.daytoday.usecase

import com.daytoday.model.DailyProgress
import com.daytoday.model.Exercise
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.Result
import com.daytoday.repository.WorkoutRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toLocalDate
import kotlin.random.Random
import javax.inject.Inject

class WorkoutUseCases @Inject constructor(
    private val repository: WorkoutRepository
) {
    suspend fun getSessions(limit: Int = 20, offset: Int = 0): Result<List<WorkoutSession>> =
        repository.getSessions(limit, offset)

    suspend fun getSession(sessionId: String): Result<WorkoutSession> =
        repository.getSession(sessionId)

    suspend fun saveSession(session: WorkoutSession): Result<WorkoutSession> =
        repository.saveSession(session)

    suspend fun saveSessions(sessions: List<WorkoutSession>): Result<List<WorkoutSession>> =
        repository.saveSessions(sessions)

    suspend fun deleteSession(sessionId: String): Result<Unit> =
        repository.deleteSession(sessionId)

    suspend fun getExercises(): Result<List<Exercise>> =
        repository.getExercises()

    suspend fun getDailyProgress(
        date: String = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
    ): Result<DailyProgress> =
        repository.getDailyProgress(date)

    suspend fun getPersonalBests(): Result<List<PersonalBests>> =
        repository.getPersonalBests()

    suspend fun getWeeklyProgress(weekStart: String): Result<WeeklyProgress> =
        repository.getWeeklyProgress(weekStart)

    suspend fun startWorkout(exerciseId: String): WorkoutSession = WorkoutSession(
        id = "wks_${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong()}",
        exerciseId = exerciseId,
        exerciseName = "",
        startTime = Clock.System.now().toEpochMilliseconds(),
        endTime = null,
        durationMinutes = 0,
        caloriesBurned = 0,
        sets = emptyList(),
        isCompleted = false
    )

    suspend fun completeWorkout(session: WorkoutSession): Result<WorkoutSession> = saveSession(
        session.copy(
            isCompleted = true,
            endTime = Clock.System.now().toEpochMilliseconds()
        )
    )

    suspend fun completeWorkouts(sessions: List<WorkoutSession>): Result<List<WorkoutSession>> = saveSessions(
        sessions.map { session ->
            session.copy(
                isCompleted = true,
                endTime = Clock.System.now().toEpochMilliseconds()
            )
        }
    )
}