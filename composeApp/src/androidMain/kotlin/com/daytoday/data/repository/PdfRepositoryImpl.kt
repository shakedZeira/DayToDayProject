package com.daytoday.data.repository

import com.daytoday.model.PdfMergeRequest
import com.daytoday.model.PdfMergeResponse
import com.daytoday.network.DayTodayApi
import com.daytoday.repository.PdfRepository
import com.daytoday.repository.Result
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Singleton
class PdfRepositoryImpl @Inject constructor(
    private val api: DayTodayApi
) : PdfRepository {
    override suspend fun mergePdfs(request: PdfMergeRequest): Result<PdfMergeResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(api.mergePdfs(request))
        } catch (e: Exception) {
            Result.failure(e.message ?: "Failed to merge PDFs", e)
        }
    }
}