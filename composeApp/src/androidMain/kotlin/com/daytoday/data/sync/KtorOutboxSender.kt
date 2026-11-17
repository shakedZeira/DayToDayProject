package com.daytoday.data.sync

import com.daytoday.network.ApiException
import com.daytoday.network.absoluteUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException

/**
 * Sends one queued request. The [client] must be configured with
 * `configureApiClient()`, which turns non-2xx replies into [ApiException].
 */
class KtorOutboxSender(private val client: HttpClient) : OutboxSender {

    override suspend fun send(method: String, path: String, body: String?, token: String): SendOutcome =
        try {
            val response = client.request(absoluteUrl(path)) {
                this.method = HttpMethod.parse(method)
                header(HttpHeaders.Authorization, "Bearer $token")
                if (body != null) setBody(TextContent(body, ContentType.Application.Json))
            }
            SendOutcome.Ok(response.bodyAsText().ifBlank { null })
        } catch (e: ApiException) {
            SendOutcome.Http(e.status, e.message ?: "Request failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SendOutcome.Network(e)
        }
}
