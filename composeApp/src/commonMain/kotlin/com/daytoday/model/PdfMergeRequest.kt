package com.daytoday.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PdfMergeRequest(
    @SerialName("fileIds") val fileIds: List<String>,
    @SerialName("order") val order: List<Int>
)

@Serializable
data class PdfMergeResponse(
    @SerialName("downloadUrl") val downloadUrl: String,
    @SerialName("fileName") val fileName: String,
    @SerialName("fileSize") val fileSize: Long,
    @SerialName("mergedAt") val mergedAt: Long
)