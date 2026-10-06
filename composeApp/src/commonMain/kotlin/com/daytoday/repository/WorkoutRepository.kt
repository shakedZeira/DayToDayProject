package com.daytoday.repository

import com.daytoday.model.DailyProgress
import com.daytoday.model.Exercise
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession

interface WorkoutRepository {
    suspend fun getSessions(limit: Int, offset: Int): Result<List<WorkoutSession>>
    suspend fun getSession(sessionId: String): Result<WorkoutSession>
    suspend fun saveSession(session: WorkoutSession): Result<WorkoutSession>
    suspend fun saveSessions(sessions: List<WorkoutSession>): Result<List<WorkoutSession>>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun getExercises(): Result<List<Exercise>>
    suspend fun getDailyProgress(date: String): Result<DailyProgress>
    suspend fun getPersonalBests(): Result<List<PersonalBests>>
    suspend fun getWeeklyProgress(weekStart: String): Result<WeeklyProgress>
}