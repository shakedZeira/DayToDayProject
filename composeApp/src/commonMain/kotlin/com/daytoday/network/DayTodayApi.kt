package com.daytoday.network

import com.daytoday.model.DailyProgress
import com.daytoday.model.Exercise
import com.daytoday.model.Injury
import com.daytoday.model.NbaGame
import com.daytoday.model.NbaNews
import com.daytoday.model.PersonalBests
import com.daytoday.model.WeeklyProgress
import com.daytoday.model.WorkoutSession

interface DayTodayApi {
    suspend fun getScoreboard(date: String): List<NbaGame>

    suspend fun getGameDetail(gameId: String): NbaGame

    suspend fun getNews(limit: Int = 20): List<NbaNews>

    suspend fun getInjuries(teamId: String?): List<Injury>

    suspend fun getSessions(limit: Int = 20, offset: Int = 0): List<WorkoutSession>

    suspend fun getSession(sessionId: String): WorkoutSession

    suspend fun saveSession(session: WorkoutSession): WorkoutSession

    suspend fun saveSessions(sessions: List<WorkoutSession>): List<WorkoutSession>

    suspend fun deleteSession(sessionId: String): Unit

    suspend fun getExercises(): List<Exercise>

    suspend fun getDailyProgress(date: String): DailyProgress

    suspend fun getPersonalBests(): List<PersonalBests>

    suspend fun getWeeklyProgress(weekStart: String): WeeklyProgress

    suspend fun login(request: LoginRequest): AuthResponse

    suspend fun register(request: RegisterRequest): AuthResponse

    suspend fun getItalianCourse(authToken: String): ItalianCourseDto

    suspend fun getItalianProgress(authToken: String): ItalianPathProgressDto

    suspend fun submitItalianLesson(authToken: String, lessonId: String, answers: List<ItalianAnswerDto>): ItalianSubmitResultDto

    suspend fun sendItalianChat(authToken: String, messages: List<ChatMessageDto>): ItalianChatResponse

    suspend fun streamItalianChat(
        authToken: String,
        messages: List<ChatMessageDto>,
        onDelta: (text: String, lang: String) -> Unit,
    )

    suspend fun getRecipes(authToken: String, meal: String?, favorites: Boolean): List<RecipeView>

    suspend fun getRecipe(authToken: String, recipeId: String): RecipeView

    suspend fun toggleFavorite(authToken: String, recipeId: String): Boolean
}