package com.daytoday.ui.screen.pdf

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.model.PdfMergeResponse
import com.daytoday.repository.Result.Failure
import com.daytoday.repository.Result.Success
import com.daytoday.usecase.PdfUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfMergeViewModel @Inject constructor(
    private val pdfUseCases: PdfUseCases,
    private val application: Application
) : ViewModel() {

    data class State(
        val files: List<UriFile> = emptyList(),
        val isMerging: Boolean = false,
        val success: PdfMergeResponse? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    fun addFiles(uris: List<Uri>) {
        val currentFiles = _uiState.value.files
        val newFiles = uris.mapIndexed { index, uri ->
            val name = getFileName(uri) ?: "PDF_${currentFiles.size + index + 1}.pdf"
            val size = getFileSize(uri)
            val fileId = "file_${System.currentTimeMillis()}_$index"
            UriFile(uri = uri, name = name, size = size, fileId = fileId)
        }
        _uiState.update { it.copy(files = currentFiles + newFiles, error = null) }
    }

    fun moveFile(fromIndex: Int, toIndex: Int) {
        val files = _uiState.value.files.toMutableList()
        if (fromIndex in files.indices && toIndex in files.indices) {
            val item = files.removeAt(fromIndex)
            files.add(toIndex, item)
            _uiState.update { it.copy(files = files) }
        }
    }

    fun removeFile(index: Int) {
        val files = _uiState.value.files.toMutableList()
        if (index in files.indices) {
            files.removeAt(index)
            _uiState.update { it.copy(files = files, success = null) }
        }
    }

    fun mergePdfs() {
        val state = _uiState.value
        if (state.files.size < 2 || state.isMerging) return

        _uiState.update { it.copy(isMerging = true, error = null, success = null) }

        viewModelScope.launch {
            val fileIds = state.files.map { it.fileId }
            val order = state.files.indices.map { it + 1 }.toList()

            val result = pdfUseCases.mergePdfs(fileIds, order)

            _uiState.update {
                when (result) {
                    is Success -> it.copy(isMerging = false, success = result.data)
                    is Failure -> it.copy(isMerging = false, error = result.error)
                }
            }
        }
    }

    fun downloadMergedPdf() {
        val success = _uiState.value.success
        if (success == null) return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(success.downloadUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(application.packageManager) != null) {
            application.startActivity(intent)
        }
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
    }

    private fun getFileName(uri: Uri): String? {
        try {
            val cursor = application.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) return it.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // Unable to resolve display name; caller falls back to a generated name.
        }
        return null
    }

    private fun getFileSize(uri: Uri): Long {
        try {
            val cursor = application.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) return it.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            // Unable to resolve size; report 0 bytes.
        }
        return 0
    }
}