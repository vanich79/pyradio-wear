package com.pyradio.wear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pyradio.wear.R

/**
 * Выбор фильтра: «все», «избранное» и жанры.
 *
 * Отдельным экраном, а не рядом чипов над списком: горизонтальная лента на
 * круглом экране обрезается по краям, и половина жанров остаётся за границей.
 */
@Composable
fun FilterScreen(
    state: UiState,
    onPick: (StationFilter) -> Unit,
) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
        ) {
            item { ListHeader { Text(stringResource(R.string.filter_title)) } }

            item {
                FilterChip(
                    text = stringResource(R.string.all_genres),
                    count = state.stations.size,
                    selected = state.filter == StationFilter.All,
                    onClick = { onPick(StationFilter.All) },
                )
            }

            item {
                FilterChip(
                    text = stringResource(R.string.favorites),
                    count = state.favorites.size,
                    selected = state.filter == StationFilter.Favorites,
                    onClick = { onPick(StationFilter.Favorites) },
                )
            }

            items(state.genres, key = { it }) { genre ->
                FilterChip(
                    text = genre,
                    count = state.stations.count { it.genre == genre },
                    selected = (state.filter as? StationFilter.Genre)?.name == genre,
                    onClick = { onPick(StationFilter.Genre(genre)) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        label = {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        // Число рядом с жанром избавляет от захода в пустой список.
        secondaryLabel = { Text(text = count.toString()) },
    )
}
