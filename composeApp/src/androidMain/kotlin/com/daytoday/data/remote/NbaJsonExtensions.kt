package com.daytoday.data.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun JsonElement?.jsonPrimitiveContent(): String? = (this as? JsonPrimitive)?.content

fun JsonElement?.contentOrNull(): String? = (this as? JsonPrimitive)?.content

fun JsonElement?.toLongOrNull(): Long? = (this as? JsonPrimitive)?.content?.toLongOrNull()

fun JsonObject.stringOrNull(vararg keys: String): String? {
    for (k in keys) {
        (this[k] as? JsonPrimitive)?.content?.let { return it }
    }
    return null
}

fun JsonObject.intOrNull(vararg keys: String): Int? {
    for (k in keys) {
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull()?.let { return it }
    }
    return null
}

fun JsonObject.getString(key: String): String? = (this[key] as? JsonPrimitive)?.content
