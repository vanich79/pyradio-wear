package com.pyradio.wear.playback

import com.pyradio.wear.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единственный источник правды о том, что играет, — для всех поверхностей сразу.
 *
 * Приложение, плитка и комплик живут в одном процессе, поэтому им незачем
 * договариваться через систему: пишет сюда только [RadioService], читают все
 * остальные. До этой перестройки состояние знал `RadioViewModel`, и плитка,
 * которую открывают, не заходя в приложение, не могла узнать ничего.
 *
 * Состояние переживает смерть службы, потому что живёт в `Application`. Если
 * службу убьют во время эфира, [RadioService.onDestroy] вернёт сюда [PlaybackState.Idle] —
 * лучше показать «ничего не играет», чем врать про звук, которого уже нет.
 */
class PlaybackHub {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    internal fun publish(state: PlaybackState) {
        _state.value = state
    }
}
