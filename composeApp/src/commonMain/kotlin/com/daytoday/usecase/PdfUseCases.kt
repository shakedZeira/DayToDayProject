package com.daytoday.usecase

import com.daytoday.repository.PdfRepository
import com.daytoday.network.ApiClient
import com.daytoday.model.PdfMergeRequest
import com.daytoday.model.PdfMergeResponse
import com.daytoday.repository.Result
import com.daytoday.network.typeInfo
import javax.inject.Inject

class PdfUseCases @Inject constructor(
    private val repository: PdfRepository,
    private val apiClient: ApiClient
) {
    suspend fun mergePdfs(fileIds: List<String>, order: List<Int>): Result<PdfMergeResponse> {
        val request = PdfMergeRequest(fileIds, order)
        return apiClient.post("api/pdfs/merge", request, typeInfo<PdfMergeResponse>())
    }
}