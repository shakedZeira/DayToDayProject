package com.daytoday.ui.screen.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daytoday.ui.theme.ButtonType
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay

@Composable
fun PdfMergeScreen(viewModel: PdfMergeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { DayTodayTopAppBar(title = "PDF Merger") }) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                DropZone(onFilesSelected = { uris -> viewModel.addFiles(uris) })

                if (state.files.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.files) { index, file ->
                            FileRow(
                                file = file,
                                position = index + 1,
                                onMoveUp = { if (index > 0) viewModel.moveFile(index, index - 1) },
                                onMoveDown = {
                                    if (index < state.files.size - 1) {
                                        viewModel.moveFile(index, index + 1)
                                    }
                                },
                                onDelete = { viewModel.removeFile(index) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    DayTodayButton(
                        text = "Merge ${state.files.size} PDFs",
                        onClick = { viewModel.mergePdfs() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.files.size >= 2 && !state.isMerging
                    )
                }

                state.success?.let { response ->
                    Spacer(modifier = Modifier.height(16.dp))
                    DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Merge Complete", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Ready to download: ${response.fileName}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            DayTodayButton(
                                text = "Download",
                                onClick = { viewModel.downloadMergedPdf() },
                                buttonType = ButtonType.Outlined
                            )
                        }
                    }
                }
            }

            if (state.isMerging) {
                LoadingOverlay("Merging PDFs...")
            }

            state.error?.let { message ->
                ErrorState(message, viewModel::retry)
            }
        }
    }
}