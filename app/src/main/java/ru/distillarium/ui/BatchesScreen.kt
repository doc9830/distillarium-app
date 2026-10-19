package ru.distillarium.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.distillarium.R
import ru.distillarium.batches.BatchStatus

private enum class BatchFilter {
    All,
    Active,
    Completed,
    Archived,
}

@Composable
fun BatchesScreen(
    state: BatchesUiState,
    onOpenBatch: (Long) -> Unit,
    onNewBatch: () -> Unit,
    onDeleteBatch: (Long) -> Unit,
) {
    var filterName by rememberSaveable { mutableStateOf(BatchFilter.All.name) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = pendingDeleteId?.let { id -> state.cards.firstOrNull { it.batch.id == id } }
    val filter = BatchFilter.entries.firstOrNull { it.name == filterName } ?: BatchFilter.All
    val visible = state.cards.filter { details ->
        when (filter) {
            BatchFilter.All -> true
            BatchFilter.Active -> details.batch.status == BatchStatus.Active
            BatchFilter.Completed -> details.batch.status == BatchStatus.Completed
            BatchFilter.Archived -> details.batch.status == BatchStatus.Archived
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.batches_title),
                subtitle = stringResource(R.string.batches_count, state.cards.size),
                actions = {
                    IconButton(onClick = onNewBatch) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.batches_new),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BatchFilter.entries.forEach { option ->
                    item(key = option.name) {
                        FilterChip(
                            selected = filter == option,
                            onClick = { filterName = option.name },
                            label = { Text(filterLabel(option)) },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { error ->
                    item(key = "error") {
                        ErrorBlock(message = error)
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        if (state.cards.isEmpty()) {
                            EmptyBlock(
                                title = stringResource(R.string.batches_empty_title),
                                text = stringResource(R.string.batches_empty_text),
                                actionLabel = stringResource(R.string.batches_new),
                                onAction = onNewBatch,
                            )
                        } else {
                            EmptyBlock(
                                title = stringResource(R.string.batches_filter_empty),
                                text = stringResource(R.string.batches_empty_text),
                            )
                        }
                    }
                } else {
                    val now = System.currentTimeMillis()
                    items(visible, key = { it.batch.id }) { details ->
                        BatchCard(
                            details = details,
                            now = now,
                            onClick = { onOpenBatch(details.batch.id) },
                            onDelete = { pendingDeleteId = details.batch.id },
                        )
                    }
                }
            }
        }
    }
    if (pendingDelete != null) {
        DeleteBatchDialog(
            title = pendingDelete.batch.title,
            onDismiss = { pendingDeleteId = null },
            onConfirm = {
                val id = pendingDelete.batch.id
                pendingDeleteId = null
                onDeleteBatch(id)
            },
        )
    }
}

@Composable
private fun filterLabel(filter: BatchFilter): String = stringResource(
    when (filter) {
        BatchFilter.All -> R.string.batches_filter_all
        BatchFilter.Active -> R.string.batches_filter_active
        BatchFilter.Completed -> R.string.batches_filter_completed
        BatchFilter.Archived -> R.string.batches_filter_archived
    },
)
