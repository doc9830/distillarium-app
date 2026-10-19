package ru.distillarium.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ru.distillarium.R
import ru.distillarium.appupdate.AppUpdateStatus
import ru.distillarium.appupdate.AppUpdateUiState
import ru.distillarium.batches.BatchDetails
import ru.distillarium.batches.BatchRules
import ru.distillarium.batches.BatchStatus
import ru.distillarium.batches.BatchStep
import ru.distillarium.batches.ReminderNotifications

@Composable
fun NowScreen(
    state: BatchesUiState,
    appUpdate: AppUpdateUiState,
    onOpenBatch: (Long) -> Unit,
    onNewBatch: () -> Unit,
    onCompleteStep: (BatchStep) -> Unit,
    onRetry: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenCalculators: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val active = state.cards.filter { it.batch.status == BatchStatus.Active }
    val overdue = active.sumOf { details ->
        details.steps.count { BatchRules.isOverdue(it, now) }
    }
    val endOfToday = BatchRules.dueAt(BatchRules.localDate(now).plusDays(1)) - 1
    val todayTasks = active
        .flatMap { details -> details.steps.map { details to it } }
        .filter { (_, step) -> !step.isDone && step.dueAt <= endOfToday }
        .sortedBy { (_, step) -> step.dueAt }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DistillariumTopBar(
                title = stringResource(R.string.now_title),
                subtitle = stringResource(R.string.now_subtitle, active.size, overdue),
                actions = {
                    IconButton(onClick = onNewBatch) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.now_new_batch),
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.now_loading))
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.error?.let { error ->
                item(key = "error") {
                    ErrorBlock(message = error, onRetry = onRetry)
                }
            }
            item(key = "notifications") {
                NotificationPermissionCard()
            }
            if (
                appUpdate.status == AppUpdateStatus.Available ||
                appUpdate.status == AppUpdateStatus.Downloading ||
                appUpdate.status == AppUpdateStatus.ReadyToInstall ||
                appUpdate.status == AppUpdateStatus.NeedsPermission
            ) {
                item(key = "app-update") {
                    AppUpdateBanner(state = appUpdate, onOpen = onOpenUpdates)
                }
            }
            item(key = "sections") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeTile(
                        title = stringResource(R.string.now_knowledge),
                        text = stringResource(R.string.now_knowledge_text),
                        actionLabel = stringResource(R.string.now_open_knowledge),
                        iconRes = R.drawable.ic_calc_menu_book,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenKnowledge,
                    )
                    HomeTile(
                        title = stringResource(R.string.now_calculators),
                        text = stringResource(R.string.now_calculators_text),
                        actionLabel = stringResource(R.string.now_open_calculators),
                        iconRes = R.drawable.ic_calc_calculate,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCalculators,
                    )
                }
            }
            item(key = "tasks-title") {
                SectionTitle(stringResource(R.string.now_tasks_title))
            }
            if (todayTasks.isEmpty()) {
                item(key = "no-tasks") {
                    EmptyBlock(
                        title = stringResource(R.string.now_no_tasks_title),
                        text = stringResource(R.string.now_no_tasks_text),
                    )
                }
            } else {
                items(todayTasks, key = { (_, step) -> "task-${step.id}" }) { (details, step) ->
                    TaskCard(
                        details = details,
                        step = step,
                        now = now,
                        onOpen = { onOpenBatch(details.batch.id) },
                        onComplete = { onCompleteStep(step) },
                    )
                }
            }

            item(key = "active-title") {
                SectionTitle(stringResource(R.string.now_active_title))
            }
            if (active.isEmpty()) {
                item(key = "empty") {
                    EmptyBlock(
                        title = stringResource(R.string.now_empty_title),
                        text = stringResource(R.string.now_empty_text),
                        actionLabel = stringResource(R.string.now_new_batch),
                        onAction = onNewBatch,
                    )
                }
            } else {
                items(active, key = { "batch-${it.batch.id}" }) { details ->
                    BatchCard(
                        details = details,
                        now = now,
                        onClick = { onOpenBatch(details.batch.id) },
                    )
                }
            }
            item(key = "new") {
                OutlinedButton(
                    onClick = onNewBatch,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.now_new_batch),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUpdateBanner(state: AppUpdateUiState, onOpen: () -> Unit) {
    val version = state.availableVersion ?: return
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.now_app_update_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.now_app_update_text, version),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.now_app_update_action))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HomeTile(
    title: String,
    text: String,
    actionLabel: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = actionLabel,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TaskCard(
    details: BatchDetails,
    step: BatchStep,
    now: Long,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = details.batch.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = step.title, style = MaterialTheme.typography.titleSmall)
                StepDueText(step = step, now = now)
            }
            TextButton(onClick = onComplete) {
                Text(stringResource(R.string.now_mark_done))
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReminderNotifications.canPost(context)) }
    var deniedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        enabled = granted
        if (!granted) deniedOnce = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = ReminderNotifications.canPost(context)
    }
    if (enabled) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.now_notifications_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.now_notifications_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(
                onClick = {
                    val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    if (canAsk && !deniedOnce) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                },
                            )
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.now_notifications_enable))
            }
        }
    }
}
