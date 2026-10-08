package com.daytoday.network

import com.daytoday.model.User
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

internal const val BASE_URL: String = "https://api.daytoday.app/"

internal val networkJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    classDiscriminator = "kind"
}

inline fun <reified T> typeInfo(): KSerializer<T> = serializer()

internal fun absoluteUrl(endpoint: String): String =
    if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) endpoint
    else BASE_URL.trimEnd('/') + "/" + endpoint.trimStart('/')

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