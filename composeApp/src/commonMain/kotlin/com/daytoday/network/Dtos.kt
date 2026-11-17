package com.daytoday.network

import com.daytoday.model.User
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

internal val networkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    classDiscriminator = "kind"
}

inline fun <reified T> typeInfo(): KSerializer<T> = serializer()

internal fun absoluteUrl(endpoint: String): String =
    if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) endpoint
    else ApiBaseUrl.current().trimEnd('/') + "/" + endpoint.trimStart('/')

internal suspend fun <T> decodeResponse(response: HttpResponse, serializer: KSerializer<T>): T {
    if (serializer.descriptor.serialName == "kotlin.Unit") {
        @Suppress("UNCHECKED_CAST")
        return Unit as T
    }
    return networkJson.decodeFromString(serializer, response.bodyAsText())
}

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)

@Serializable
data class RegisterRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("name") val name: String
)

@Serializable
data class AuthResponse(
    @SerialName("user") val user: User,
    @SerialName("token") val token: String
)

@Serializable
data class RecipeIngredient(
    @SerialName("name") val name: String,
    @SerialName("amount") val amount: Double,
    @SerialName("unit") val unit: String
)

@Serializable
data class RecipeInstructionStep(
    @SerialName("step") val step: Int,
    @SerialName("text") val text: String,
    @SerialName("timerSeconds") val timerSeconds: Int? = null
)

@Serializable
data class RecipeView(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String?,
    @SerialName("meal") val meal: String,
    @SerialName("difficulty") val difficulty: String,
    @SerialName("prepTimeMin") val prepTimeMin: Int,
    @SerialName("cookTimeMin") val cookTimeMin: Int,
    @SerialName("servings") val servings: Int,
    @SerialName("calories") val calories: Int,
    @SerialName("proteinG") val proteinG: Double,
    @SerialName("carbsG") val carbsG: Double,
    @SerialName("fatG") val fatG: Double,
    @SerialName("ingredients") val ingredients: List<RecipeIngredient>,
    @SerialName("instructions") val instructions: List<RecipeInstructionStep>,
    @SerialName("imageUrl") val imageUrl: String?,
    @SerialName("source") val source: String?,
    @SerialName("tags") val tags: List<String>?,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("updatedAt") val updatedAt: String,
    @SerialName("favoriteCount") val favoriteCount: Int,
    @SerialName("isFavorited") val isFavorited: Boolean
)

@Serializable
data class FavoriteToggleResponse(
    @SerialName("favorited") val favorited: Boolean,
    @SerialName("recipeId") val recipeId: String
)

/** Row from `GET /api/tasks`. Dates are ISO-8601 strings straight from the server. */
@Serializable
data class TaskDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("dueAt") val dueAt: String? = null,
    @SerialName("recurrence") val recurrence: String? = "none",
    @SerialName("category") val category: String? = null,
    @SerialName("remindAt") val remindAt: String? = null,
)

/** Row from `GET /api/goals` (the server-side weekly goals, not the on-device goal list). */
@Serializable
data class WeeklyGoalDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("targetCount") val targetCount: Int = 1,
    @SerialName("unit") val unit: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("autoSource") val autoSource: String? = null,
    @SerialName("thisWeekCount") val thisWeekCount: Int = 0,
    @SerialName("lastEventAt") val lastEventAt: String? = null,
)

@Serializable
data class MealLogDto(
    @SerialName("id") val id: String,
    @SerialName("foodId") val foodId: String,
    @SerialName("foodName") val foodName: String,
    @SerialName("grams") val grams: Double,
    @SerialName("calories") val calories: Int = 0,
)

/** `GET /api/nutrition/detail?date=` */
@Serializable
data class MealsDetailDto(
    @SerialName("date") val date: String,
    @SerialName("meals") val meals: List<MealLogDto> = emptyList(),
)
