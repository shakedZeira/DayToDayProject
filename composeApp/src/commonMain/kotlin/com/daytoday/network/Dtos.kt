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