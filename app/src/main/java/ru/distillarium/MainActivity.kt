package ru.distillarium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.distillarium.ui.UpdatesScreen
import ru.distillarium.ui.UpdatesViewModel
import ru.distillarium.ui.theme.DistillariumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DistillariumTheme {
                val viewModel: UpdatesViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                UpdatesScreen(
                    state = state,
                    onCheckUpdates = viewModel::checkUpdates,
                    onRollback = viewModel::rollback,
                )
            }
        }
    }
}
