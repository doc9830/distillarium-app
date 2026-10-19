package ru.distillarium.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.distillarium.R
import ru.distillarium.appupdate.AppUpdateViewModel
import ru.distillarium.calculators.Calculators

private enum class RootTab {
    Now,
    Batches,
    Knowledge,
    Calculators,
    Updates,
}

/** Нижняя навигация M3: текущие задачи, партии, знания, расчёты и обновления. */
@Composable
fun DistillariumRoot(
    initialArticleId: String? = null,
    initialArticleHeading: String? = null,
    initialTab: String? = null,
) {
    val knowledgeViewModel: KnowledgeViewModel = viewModel()
    val updatesViewModel: UpdatesViewModel = viewModel()
    val appUpdateViewModel: AppUpdateViewModel = viewModel()
    val batchesViewModel: BatchesViewModel = viewModel()
    val knowledge by knowledgeViewModel.state.collectAsStateWithLifecycle()
    val updates by updatesViewModel.state.collectAsStateWithLifecycle()
    val appUpdate by appUpdateViewModel.state.collectAsStateWithLifecycle()
    val batches by batchesViewModel.state.collectAsStateWithLifecycle()
    val knowledgeListState = rememberLazyListState()
    val context = LocalContext.current

    var tabName by rememberSaveable {
        mutableStateOf(
            RootTab.entries.firstOrNull { it.name == initialTab }?.name ?: RootTab.Now.name,
        )
    }
    var routeStack by rememberSaveable { mutableStateOf("") }
    var initialArticleApplied by rememberSaveable { mutableStateOf(false) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }

    val tab = RootTab.entries.firstOrNull { it.name == tabName } ?: RootTab.Now
    val library = knowledge.library
    val currentRoute = routeStack
        .split(STACK_SEPARATOR)
        .lastOrNull()
        ?.takeIf { it.isNotBlank() }

    LaunchedEffect(initialArticleId) {
        if (!initialArticleApplied && !initialArticleId.isNullOrBlank()) {
            tabName = RootTab.Knowledge.name
            routeStack = articleRoute(initialArticleId)
            initialArticleApplied = true
        }
    }

    fun pushRoute(route: String) {
        routeStack = if (routeStack.isBlank()) {
            route
        } else {
            routeStack + STACK_SEPARATOR + route
        }
    }

    fun closeRoute() {
        routeStack = if (STACK_SEPARATOR in routeStack) {
            routeStack.substringBeforeLast(STACK_SEPARATOR)
        } else {
            ""
        }
    }

    fun openBatch(batchId: Long) {
        batchesViewModel.openBatch(batchId)
        pushRoute(batchRoute(batchId))
    }

    fun openCalculator(
        calculatorId: String,
        batchId: Long? = null,
        prefill: Map<String, String> = emptyMap(),
    ) {
        pushRoute(calculatorRoute(calculatorId, batchId, prefill))
    }

    fun openNewBatch() {
        tabName = RootTab.Batches.name
        pushRoute(NEW_BATCH_ROUTE)
    }

    fun openKnowledge() {
        routeStack = ""
        tabName = RootTab.Knowledge.name
    }

    fun openCalculators() {
        routeStack = ""
        tabName = RootTab.Calculators.name
    }

    fun openUpdates() {
        routeStack = ""
        tabName = RootTab.Updates.name
    }

    fun deleteBatch(batchId: Long) {
        batchesViewModel.deleteBatch(batchId) {
            if (currentRoute?.startsWith(BATCH_ROUTE) == true) closeRoute()
        }
    }

    LaunchedEffect(tab, updates.installedVersion) {
        if (
            tab == RootTab.Knowledge &&
            library != null &&
            updates.installedVersion != null &&
            library.contentVersion != updates.installedVersion
        ) {
            knowledgeViewModel.refresh()
        }
    }

    // После публикации контента возвращение в приложение сразу проверяет обновления,
    // не дожидаясь повторного запуска процесса.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        updatesViewModel.checkUpdates(silent = true)
    }

    if (currentRoute != null) {
        val target = currentRoute.substringAfter(':').substringBefore('?')
        when {
            currentRoute.startsWith(NEW_BATCH_ROUTE) -> {
                NewBatchScreen(
                    state = batches,
                    onBack = ::closeRoute,
                    onCreate = { recipe, title, kind, volume ->
                        batchesViewModel.createBatch(recipe, title, kind, volume) { id ->
                            tabName = RootTab.Batches.name
                            routeStack = batchRoute(id)
                        }
                    },
                    onRetryRecipes = batchesViewModel::refreshRecipes,
                )
            }
            currentRoute.startsWith(BATCH_ROUTE) -> {
                val batchId = target.toLongOrNull()
                if (batchId == null) {
                    routeStack = ""
                } else {
                    LaunchedEffect(batchId) {
                        batchesViewModel.openBatch(batchId)
                    }
                    BatchDetailScreen(
                        state = batches,
                        batchId = batchId,
                        favorites = batches.calcFavorites,
                        onBack = {
                            batchesViewModel.closeBatch()
                            closeRoute()
                        },
                        onOpenArticle = { id -> pushRoute(articleRoute(id)) },
                        onOpenCalculator = { calcId, prefill ->
                            openCalculator(calcId, batchId, prefill)
                        },
                        onCompleteStep = { step, done ->
                            batchesViewModel.completeStep(step.id, batchId, done)
                        },
                        onAddStep = { title, days ->
                            batchesViewModel.addStep(batchId, title, days)
                        },
                        onAddMeasurement = { gravity, temp, note ->
                            batchesViewModel.addMeasurement(batchId, gravity, temp, note)
                        },
                        onAddJournal = { kind, text ->
                            batchesViewModel.addJournal(batchId, kind, text)
                        },
                        onSaveNotes = { notes -> batchesViewModel.updateNotes(batchId, notes) },
                        onSetStatus = { status -> batchesViewModel.setStatus(batchId, status) },
                        onRepeat = {
                            batchesViewModel.repeatBatch(batchId) { newId ->
                                routeStack = batchRoute(newId)
                            }
                        },
                        onDelete = { deleteBatch(batchId) },
                    )
                }
            }

            currentRoute.startsWith(ARTICLES_ROUTE) -> {
                if (library == null) {
                    if (knowledge.loading) {
                        ArticleLoadingScreen(onBack = ::closeRoute)
                    } else {
                        MissingArticleScreen(onBack = ::closeRoute)
                    }
                    return
                }
                val article = library.article(target)
                if (article == null) {
                    MissingArticleScreen(onBack = ::closeRoute)
                } else {
                    ArticleScreen(
                        article = article,
                        library = library,
                        selectedEquipment = equipment,
                        fontScale = fontScale,
                        initialHeading = initialArticleHeading,
                        onBack = ::closeRoute,
                        onOpenArticle = { id -> pushRoute(articleRoute(id)) },
                        onOpenReference = { id -> pushRoute(referenceRoute(id)) },
                        onOpenCalculator = { id -> pushRoute(calculatorRoute(id)) },
                        onEquipmentSelected = { equipment = it },
                        onFontScaleChange = { value -> fontScale = value.coerceIn(0.85f, 1.6f) },
                    )
                }
            }
            currentRoute.startsWith(REFERENCE_ROUTE) -> {
                if (library == null && knowledge.loading) {
                    ArticleLoadingScreen(onBack = ::closeRoute)
                } else {
                    ReferenceScreen(
                        tableId = target,
                        library = library,
                        onBack = ::closeRoute,
                        onOpenArticle = { id -> pushRoute(articleRoute(id)) },
                        onOpenCalculator = { id -> pushRoute(calculatorRoute(id)) },
                    )
                }
            }
            currentRoute.startsWith(CALCULATOR_ROUTE) -> {
                val args = calculatorRouteArgs(currentRoute)
                val calculator = args.id?.let(Calculators::byId)
                if (calculator == null) {
                    MissingCalculatorScreen(onBack = ::closeRoute)
                } else {
                    val batch = args.batchId?.let { id ->
                        batches.cards.firstOrNull { it.batch.id == id }
                            ?: batches.selected?.takeIf { it.batch.id == id }
                    }
                    CalculatorDetailScreen(
                        calculator = calculator,
                        initialValues = args.prefill,
                        batch = batch,
                        batches = batches.cards,
                        favorite = calculator.id in batches.calcFavorites,
                        onToggleFavorite = { batchesViewModel.toggleCalcFavorite(calculator) },
                        onBack = ::closeRoute,
                        onOpenCalculator = { id, prefill ->
                            openCalculator(id, args.batchId, prefill)
                        },
                        onSaveToBatch = { batchId, inputs, result, note ->
                            batchesViewModel.saveCalc(batchId, calculator, inputs, result, note)
                        },
                    )
                }
            }
            else -> routeStack = ""
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = tab == RootTab.Now,
                        onClick = { tabName = RootTab.Now.name },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_now)) },
                        colors = navigationBarColors(),
                    )
                    NavigationBarItem(
                        selected = tab == RootTab.Batches,
                        onClick = { tabName = RootTab.Batches.name },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_batches)) },
                        colors = navigationBarColors(),
                    )

                    NavigationBarItem(
                        selected = tab == RootTab.Updates,
                        onClick = { tabName = RootTab.Updates.name },
                        icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_updates)) },
                        colors = navigationBarColors(),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                RootTab.Now -> NowScreen(
                    state = batches,
                    appUpdate = appUpdate,
                    onOpenBatch = ::openBatch,
                    onNewBatch = ::openNewBatch,
                    onCompleteStep = { step ->
                        batchesViewModel.completeStep(step.id, step.batchId, true)
                    },
                    onRetry = batchesViewModel::refresh,
                    onOpenKnowledge = ::openKnowledge,
                    onOpenCalculators = ::openCalculators,
                    onOpenUpdates = ::openUpdates,
                )
                RootTab.Batches -> BatchesScreen(
                    state = batches,
                    onOpenBatch = ::openBatch,
                    onNewBatch = ::openNewBatch,
                    onDeleteBatch = ::deleteBatch,
                )
                RootTab.Knowledge -> KnowledgeScreen(
                    state = knowledge,
                    listState = knowledgeListState,
                    onQueryChange = knowledgeViewModel::onQueryChange,
                    onClearQuery = knowledgeViewModel::clearQuery,
                    onSelectTag = knowledgeViewModel::selectTag,
                    onOpenSection = knowledgeViewModel::openSection,
                    onNavigateBack = knowledgeViewModel::navigateBack,
                    onOpenArticle = { id -> pushRoute(articleRoute(id)) },
                    onOpenReference = { id -> pushRoute(referenceRoute(id)) },
                    onSelectMode = knowledgeViewModel::selectMode,
                    onRetry = knowledgeViewModel::retry,
                )
                RootTab.Calculators -> CalculatorsScreen(
                    onOpenCalculator = { id -> openCalculator(id) },
                    favorites = batches.calcFavorites,
                    onToggleFavorite = batchesViewModel::toggleCalcFavorite,
                )
                RootTab.Updates -> UpdatesScreen(
                    state = updates,
                    appUpdate = appUpdate,
                    onCheckUpdates = { updatesViewModel.checkUpdates() },
                    onRollback = updatesViewModel::rollback,
                    onCheckAppUpdate = { appUpdateViewModel.checkForUpdate() },
                    onDownloadAppUpdate = appUpdateViewModel::downloadAndInstall,
                    onOpenInstallPermission = {
                        runCatching {
                            appUpdateViewModel.openInstallPermissionSettings()
                        }.onSuccess { intent ->
                            runCatching {
                                context.startActivity(intent)
                            }.onFailure { error ->
                                android.util.Log.w("Updates", "Не удалось открыть настройки", error)
                            }
                        }
                    },
                    onAppUpdateResume = appUpdateViewModel::onResume,
                    onInstallDownloaded = appUpdateViewModel::installDownloaded,
                )
            }
        }
    }
}

@Composable
private fun navigationBarColors(): NavigationBarItemColors = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private const val STACK_SEPARATOR = "\u001F"
private const val ARTICLES_ROUTE = "a:"
private const val REFERENCE_ROUTE = "r:"
private const val CALCULATOR_ROUTE = "c:"
private const val BATCH_ROUTE = "b:"
private const val NEW_BATCH_ROUTE = "n:"

private data class CalculatorRouteArgs(
    val id: String?,
    val batchId: Long?,
    val prefill: Map<String, String>,
)

private fun articleRoute(id: String): String = ARTICLES_ROUTE + id
private fun referenceRoute(id: String): String = REFERENCE_ROUTE + id
private fun calculatorRoute(
    id: String,
    batchId: Long? = null,
    prefill: Map<String, String> = emptyMap(),
): String {
    val params = buildList {
        if (batchId != null) add("b=$batchId")
        if (prefill.isNotEmpty()) {
            val encoded = prefill.entries.joinToString("\n") { (key, value) -> "$key=$value" }
            add("v=${Uri.encode(encoded)}")
        }
    }
    val suffix = if (params.isEmpty()) "" else "?" + params.joinToString("&")
    return CALCULATOR_ROUTE + Uri.encode(id) + suffix
}

private fun calculatorRouteArgs(route: String): CalculatorRouteArgs {
    val id = route.substringAfter(':').substringBefore('?').let(Uri::decode)
    val query = route.substringAfter('?', "")
    val params = query
        .split('&')
        .filter { '=' in it }
        .associate { part ->
            Uri.decode(part.substringBefore('=')) to Uri.decode(part.substringAfter('='))
        }
    val prefill = params["v"]?.lineSequence()
        ?.mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                line.take(separator) to line.drop(separator + 1)
            }
        }
        ?.toMap()
        .orEmpty()
    return CalculatorRouteArgs(
        id = id.takeIf { it.isNotBlank() },
        batchId = params["b"]?.toLongOrNull(),
        prefill = prefill,
    )
}

private fun batchRoute(id: Long): String = BATCH_ROUTE + id

