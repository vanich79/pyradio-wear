package com.pyradio.wear.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.pyradio.wear.R
import com.pyradio.wear.model.PlaybackState
import kotlin.math.roundToInt

/**
 * Что играет, как это остановить и как сделать тише.
 *
 * Кнопки крупные: экран включается на несколько секунд, палец попадает в него не
 * глядя, и промахнуться мимо «стоп» здесь дороже, чем сэкономить место.
 */
@OptIn(ExperimentalWearFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun NowPlayingScreen(
    state: UiState,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onScreenShown: () -> Unit,
) {
    val playback = state.playback
    val station = playback.stationOrNull()

    // Громкость могли изменить снаружи, пока экрана не было на виду.
    LaunchedEffect(Unit) { onScreenShown() }

    val focusRequester = rememberActiveFocusRequester()
    // Колесо отдаёт непрерывные пиксели, а громкость дискретна. Копим ход и
    // тратим его ступенями: без этого один щелчок укручивал бы звук до нуля.
    val turned = remember { RotaryAccumulator() }

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    turned.add(event.verticalScrollPixels) { up ->
                        if (up) onVolumeUp() else onVolumeDown()
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (station == null) {
                Text(
                    text = stringResource(R.string.state_idle),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            Text(
                text = station.name,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = playback.shortLabel(),
                style = MaterialTheme.typography.caption2,
                color = if (playback is PlaybackState.Failed) {
                    MaterialTheme.colors.error
                } else {
                    MaterialTheme.colors.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val favorite = station.id in state.favorites
                Button(
                    onClick = { onToggleFavorite(station.id) },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    // Звёздочка вместо иконки: набор material-icons-core её не
                    // содержит, а тянуть ради одного символа extended-набор —
                    // это мегабайт в APK на часах.
                    Text(if (favorite) "★" else "☆", style = MaterialTheme.typography.title2)
                }

                Spacer(Modifier.width(12.dp))

                if (playback is PlaybackState.Failed) {
                    Button(onClick = onRetry, colors = ButtonDefaults.primaryButtonColors()) {
                        Text("↻", style = MaterialTheme.typography.title2)
                    }
                } else {
                    Button(onClick = onStop, colors = ButtonDefaults.primaryButtonColors()) {
                        Text("■", style = MaterialTheme.typography.title3)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            VolumeRow(
                fraction = state.volumeFraction,
                known = state.volumeSteps > 0,
                onUp = onVolumeUp,
                onDown = onVolumeDown,
            )
        }
    }
}

/**
 * Громкость: колесо часов и две кнопки на случай, когда крутить нечем.
 *
 * Кнопки маленькие намеренно. Основной способ здесь — колесо: оно не требует
 * попадать пальцем и работает, когда экран уже погас наполовину. Кнопки — запасной
 * путь, и место у «стоп» они отбирать не должны.
 */
@Composable
private fun VolumeRow(
    fraction: Float,
    known: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompactButton(
            onClick = onDown,
            colors = ButtonDefaults.secondaryButtonColors(),
        ) {
            Text("−", style = MaterialTheme.typography.title3)
        }

        Text(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(46.dp),
            // Прочерк, если система не сказала, сколько ступеней у громкости.
            // Ноль процентов и «неизвестно» — разные вещи, и путать их нельзя.
            text = if (known) "${(fraction * 100).roundToInt()}%" else "—",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        CompactButton(
            onClick = onUp,
            colors = ButtonDefaults.secondaryButtonColors(),
        ) {
            Text("+", style = MaterialTheme.typography.title3)
        }
    }
}

/**
 * Копилка хода колеса.
 *
 * Одно движение короны — это десятки пикселей прокрутки, а ступеней громкости на
 * часах всего полтора десятка. Без порога один щелчок пролетал бы всю шкалу.
 */
private class RotaryAccumulator(private val threshold: Float = 40f) {

    private var travelled = 0f

    fun add(pixels: Float, step: (up: Boolean) -> Unit) {
        travelled += pixels
        while (travelled >= threshold) {
            travelled -= threshold
            step(true)
        }
        while (travelled <= -threshold) {
            travelled += threshold
            step(false)
        }
    }
}
