package com.daytoday.network

import com.daytoday.repository.Result
import io.ktor.client.HttpClient
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.content.PartData
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ApiClient {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(networkJson)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        defaultRequest {
            header("Accept", "application/json")
        }
    }

    suspend fun <T> get(endpoint: String, serializer: KSerializer<T>): Result<T> = try {
        val response = client.get(absoluteUrl(endpoint))
        Result.success(decodeResponse(response, serializer))
    } catch (e: Exception) {
        Result.failure(e.message ?: "Network error", e)
    }

    suspend fun <T> post(endpoint: String, body: Any?, serializer: KSerializer<T>): Result<T> = try {
        val response = client.post(absoluteUrl(endpoint)) {
            setJsonBody(body)
        }
        Result.success(decodeResponse(response, serializer))
    } catch (e: Exception) {
        Result.failure(e.message ?: "Network error", e)
    }

    suspend fun <T> multipart(
        endpoint: String,
        formData: List<PartData>,
        serializer: KSerializer<T>
    ): Result<T> = try {
        val response = client.post(absoluteUrl(endpoint)) {
            setBody(MultiPartFormDataContent(formData))
        }
        Result.success(decodeResponse(response, serializer))
    } catch (e: Exception) {
        Result.failure(e.message ?: "Network error", e)
    }
}

private fun HttpRequestBuilder.setJsonBody(body: Any?) {
    when (body) {
        null -> Unit
        is Map<*, *> -> setBody(
            JsonObject(
                body.entries.associate { (key, value) ->
                    key.toString() to JsonPrimitive(value?.toString() ?: "null")
                }
            )
        )
        else -> setBody(body)
    }
}