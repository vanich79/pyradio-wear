package com.pyradio.wear.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

class MainActivity : ComponentActivity() {

    /**
     * Уведомление медиасессии — это то, чем служба держится на переднем плане.
     * Без разрешения система не даст его показать, и воспроизведение оборвётся
     * при первом же уходе с экрана. Спрашиваем сразу, не дожидаясь первой станции:
     * объяснять отказ постфактум («звук пропал, потому что вы нажали „нет“»)
     * заметно хуже, чем спросить до того, как что-то заиграло.
     */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { PyRadioApp() }
    }
}

@Composable
fun PyRadioApp(viewModel: RadioViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    MaterialTheme {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Route.STATIONS,
        ) {
            composable(Route.STATIONS) {
                StationsScreen(
                    state = state,
                    onSelect = { station ->
                        viewModel.play(station)
                        navController.navigate(Route.NOW_PLAYING)
                    },
                    onOpenFilter = { navController.navigate(Route.FILTER) },
                    onOpenNowPlaying = { navController.navigate(Route.NOW_PLAYING) },
                )
            }

            composable(Route.FILTER) {
                FilterScreen(
                    state = state,
                    onPick = { filter ->
                        viewModel.setFilter(filter)
                        navController.popBackStack()
                    },
                )
            }

            composable(Route.NOW_PLAYING) {
                NowPlayingScreen(
                    state = state,
                    onStop = viewModel::stop,
                    onRetry = viewModel::retry,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onVolumeUp = viewModel::volumeUp,
                    onVolumeDown = viewModel::volumeDown,
                    onScreenShown = viewModel::readVolume,
                )
            }
        }
    }
}

object Route {
    const val STATIONS = "stations"
    const val FILTER = "filter"
    const val NOW_PLAYING = "now_playing"
}
