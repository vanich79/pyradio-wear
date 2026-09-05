package com.pyradio.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pyradio.wear.R
import com.pyradio.wear.model.FailureReason
import com.pyradio.wear.model.PlaybackState

/**
 * Подписи состояний. Собраны в одном месте, потому что одно и то же состояние
 * подписывается на трёх экранах, и расходиться этим подписям незачем.
 */

@Composable
fun StationFilter.label(): String = when (this) {
    StationFilter.All -> stringResource(R.string.stations_title)
    StationFilter.Favorites -> stringResource(R.string.favorites)
    is StationFilter.Genre -> name
}

/** Короткая строка под именем станции: помещается в один ряд списка. */
@Composable
fun PlaybackState.shortLabel(): String = when (this) {
    PlaybackState.Idle -> stringResource(R.string.state_idle)
    is PlaybackState.Connecting -> stringResource(R.string.state_connecting)
    is PlaybackState.Buffering -> stringResource(R.string.state_buffering)
    // Название трека, если станция его прислала. Иначе — «Играет», а не жанр:
    // жанр уже написан у той же станции в списке ниже, и повторять его здесь
    // значит делать строку «сейчас играет» неотличимой от обычной строки списка.
    is PlaybackState.Playing -> title ?: stringResource(R.string.state_playing)
    is PlaybackState.Failed -> reason.message()
}

@Composable
fun FailureReason.message(): String = stringResource(
    when (this) {
        FailureReason.NO_NETWORK -> R.string.fail_no_network
        FailureReason.EMPTY_PLAYLIST -> R.string.fail_empty_playlist
        FailureReason.STREAM_UNREACHABLE -> R.string.fail_unreachable
        FailureReason.NOT_AUDIO -> R.string.fail_not_audio
        FailureReason.UNSUPPORTED_FORMAT -> R.string.fail_unsupported
    },
)
