package com.example.videodownloader.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videodownloader.R
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard
import com.example.videodownloader.ui.downloadTaskStatusText

@Composable
fun DownloadDetailScreen(
    viewModel: DownloadDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    AppGradientBackdrop {
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(hostState = snackbarHostState)

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                return@Box
            }

            val task = state.task
            if (task == null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    AppSectionCard {
                        Text(stringResource(R.string.detail_missing_task), color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Box
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AppSectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                            Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.detail_field_title, task.title))
                            Text(stringResource(R.string.detail_field_status, context.downloadTaskStatusText(task.status)))
                            Text(stringResource(R.string.detail_field_progress, task.progress))
                            Text(stringResource(R.string.detail_field_format, task.selectedResolution, task.selectedExt))
                            Text(stringResource(R.string.detail_field_source, task.sourceUrl))
                            Text(stringResource(R.string.detail_field_download_url, task.downloadUrl))
                            task.saveUri?.let { Text(stringResource(R.string.detail_field_local_path, it)) }
                            task.errorMessage?.let { Text(stringResource(R.string.detail_field_error, it), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }

                item {
                    AppSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (task.status == DownloadTaskStatus.DOWNLOADING || task.status == DownloadTaskStatus.QUEUED) {
                                Button(onClick = viewModel::pauseTask) { Text(stringResource(R.string.detail_pause)) }
                            }
                            if (task.status == DownloadTaskStatus.CANCELED) {
                                FilledTonalButton(onClick = viewModel::resumeTask) { Text(stringResource(R.string.detail_resume)) }
                            }
                            if (task.status == DownloadTaskStatus.FAILED) {
                                Button(onClick = viewModel::retryTask) { Text(stringResource(R.string.common_retry)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
