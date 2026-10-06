package com.daytoday.repository

import com.daytoday.model.PdfMergeRequest
import com.daytoday.model.PdfMergeResponse

interface PdfRepository {
    suspend fun mergePdfs(request: PdfMergeRequest): Result<PdfMergeResponse>
}