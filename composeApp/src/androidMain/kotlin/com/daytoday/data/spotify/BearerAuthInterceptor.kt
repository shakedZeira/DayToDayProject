package com.daytoday.data.spotify

import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val accessTokenProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = accessTokenProvider()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}