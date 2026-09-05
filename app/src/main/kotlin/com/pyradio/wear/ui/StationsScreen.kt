package com.pyradio.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pyradio.wear.R
import com.pyradio.wear.model.PlaybackState
import com.pyradio.wear.model.Station

/**
 * Список станций.
 *
 * Плоский, без вложенных групп: на круглом экране в 466 точек каждый уровень
 * вложенности — это лишнее касание вслепую, а жанр вынесен в отдельный фильтр,
 * который открывается сверху и закрывается выбором.
 */
@Composable
fun StationsScreen(
    state: UiState,
    onSelect: (Station) -> Unit,
    onOpenFilter: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val playing = state.playback.stationOrNull()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
        ) {
            item {
                ListHeader {
                    Text(
                        text = state.filter.label(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            item {
                CompactChip(
                    onClick = onOpenFilter,
                    label = { Text(stringResource(R.string.filter_change)) },
                )
            }

            // Строка «сейчас играет» ведёт обратно к плееру. Она есть только когда
            // есть что играть, — пустой заголовок съедал бы экран ни за чем.
            if (playing != null) {
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenNowPlaying,
                        colors = ChipDefaults.primaryChipColors(),
                        label = {
                            Text(
                                text = playing.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        secondaryLabel = { Text(state.playback.shortLabel()) },
                    )
                }
            }

            items(state.visible, key = { it.id }) { station ->
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(station) },
                    // Играющая станция помечена цветом, а не значком. Значок
                    // пробовали: «▶» (U+25B6) в шрифте часов отсутствует и
                    // занимает место, ничего не рисуя, — строка выглядит просто
                    // криво съехавшей. Цвет виден при прокрутке и не зависит
                    // от того, что нашлось в системном шрифте.
                    colors = if (station.id == playing?.id) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    label = {
                        Text(
                            text = station.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = if (station.id in state.favorites) {
                                "★ ${station.genre}"
                            } else {
                                station.genre
                            },
                            maxLines = 1,
                        )
                    },
                )
            }

            if (state.visible.isEmpty() && state.stations.isNotEmpty()) {
                item {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        text = stringResource(R.string.filter_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2,
                    )
                }
            }
        }
    }
}

/** Станция, к которой относится состояние, — или `null`, когда состояние ни к чему не относится. */
fun PlaybackState.stationOrNull(): Station? = when (this) {
    PlaybackState.Idle -> null
    is PlaybackState.Connecting -> station
    is PlaybackState.Buffering -> station
    is PlaybackState.Playing -> station
    is PlaybackState.Failed -> station
}
