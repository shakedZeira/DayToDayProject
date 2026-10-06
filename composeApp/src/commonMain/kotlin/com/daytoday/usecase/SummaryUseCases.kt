package com.daytoday.usecase

import com.daytoday.model.FitnessSummary
import com.daytoday.model.MonthlyProgress
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession
import com.daytoday.repository.NbaRepository
import com.daytoday.repository.Result
import com.daytoday.repository.WorkoutRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

private const val SESSION_LIMIT = 100

class SummaryUseCases @Inject constructor(
    @Suppress("unused") private val nbaRepository: NbaRepository,
    private val workoutRepository: WorkoutRepository
) {
    suspend fun generateDailySummary(
        date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    ): Result<FitnessSummary> {
        val dailyResult = workoutRepository.getDailyProgress(date.toString())
        val daily = dailyResult.getOrNull()
            ?: return Result.failure(
                "Workout progress unavailable for $date: ${dailyResult.errorOrNull() ?: "unknown error"}"
            )

        val sessions = workoutRepository.getSessions(SESSION_LIMIT, 0).getOrNull().orEmpty()
        val personalBests = workoutRepository.getPersonalBests().getOrNull().orEmpty()
            .ifEmpty { daily.personalBests }

        val todaySessions = sessions.filter { it.isCompleted && localDateOf(it.startTime) == date }
        val completedSets = todaySessions.flatMap { session -> session.sets }.filter { it.isCompleted }
        val ratedSets = completedSets.mapNotNull { it.rpe }

        val favoriteExercise = todaySessions
            .groupingBy { it.exerciseName }
            .eachCount()
            .filterKeys { it.isNotBlank() }
            .maxByOrNull { it.value }
            ?.key
            ?: personalBests.firstOrNull()?.exerciseName

        val strongestExercise = personalBests.maxByOrNull { it.bestOneRM }?.exerciseName

        val activeDates = sessions
            .filter { it.isCompleted }
            .map { localDateOf(it.startTime) }
            .toSortedSet()

        val currentStreak = generateSequence(date) {
            LocalDate.fromEpochDays(it.toEpochDays() - 1)
        }
            .takeWhile { activeDates.contains(it) }
            .count()

        var longestStreak = 0
        var runningStreak = 0
        var previousDate: LocalDate? = null
        for (day in activeDates) {
            val previous = previousDate
            runningStreak = if (previous != null && day == LocalDate.fromEpochDays(previous.toEpochDays() + 1)) {
                runningStreak + 1
            } else {
                1
            }
            if (runningStreak > longestStreak) longestStreak = runningStreak
            previousDate = day
        }

        val completedSessions = sessions.filter { it.isCompleted }

        val weeklyProgress = completedSessions
            .groupBy { weekStartOf(localDateOf(it.startTime)) }
            .map { (weekStart, weekSessions) ->
                WeeklyProgress(
                    weekStart = weekStart
                        .atStartOfDayIn(TimeZone.currentSystemDefault())
                        .toEpochMilliseconds(),
                    workouts = weekSessions.size,
                    durationMinutes = weekSessions.sumOf { it.durationMinutes },
                    caloriesBurned = weekSessions.sumOf { it.caloriesBurned },
                    volume = volumeOf(weekSessions)
                )
            }
            .sortedByDescending { it.weekStart }

        val monthlyProgress = completedSessions
            .groupBy { day -> localDateOf(day.startTime).let { it.year to it.monthNumber } }
            .map { (key, monthSessions) ->
                MonthlyProgress(
                    month = key.second,
                    year = key.first,
                    workouts = monthSessions.size,
                    durationMinutes = monthSessions.sumOf { it.durationMinutes },
                    caloriesBurned = monthSessions.sumOf { it.caloriesBurned },
                    volume = volumeOf(monthSessions)
                )
            }
            .sortedWith(compareByDescending<MonthlyProgress> { it.year }.thenByDescending { it.month })

        return Result.success(
            FitnessSummary(
                totalWorkouts = maxOf(daily.workoutsCompleted, todaySessions.size),
                totalDurationMinutes = if (daily.totalDurationMinutes > 0) {
                    daily.totalDurationMinutes
                } else {
                    todaySessions.sumOf { it.durationMinutes }
                },
                totalCaloriesBurned = if (daily.caloriesBurned > 0) {
                    daily.caloriesBurned
                } else {
                    todaySessions.sumOf { it.caloriesBurned }
                },
                totalSets = completedSets.size,
                totalVolume = completedSets.sumOf { it.weight * it.reps },
                averageRpe = if (ratedSets.isEmpty()) 0.0 else ratedSets.average(),
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                favoriteExercise = favoriteExercise,
                strongestExercise = strongestExercise,
                weeklyProgress = weeklyProgress,
                monthlyProgress = monthlyProgress
            )
        )
    }
}

private fun localDateOf(epochMilliseconds: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMilliseconds)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date

private fun weekStartOf(date: LocalDate): LocalDate =
    LocalDate.fromEpochDays(date.toEpochDays() - date.dayOfWeek.ordinal)

private fun volumeOf(sessions: List<WorkoutSession>): Double =
    sessions.flatMap { it.sets }
        .filter { it.isCompleted }
        .sumOf { it.weight * it.reps }
