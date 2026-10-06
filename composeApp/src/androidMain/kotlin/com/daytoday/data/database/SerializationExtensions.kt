package com.daytoday.data.database

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@PublishedApi internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

inline fun <reified T : Any> String.fromJson(): T = json.decodeFromString<T>(this)

inline fun <reified T> T.toJson(): String = json.encodeToString(this)