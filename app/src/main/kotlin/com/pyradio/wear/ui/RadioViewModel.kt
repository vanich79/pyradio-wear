package com.pyradio.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pyradio.wear.RadioApp
import com.pyradio.wear.model.PlaybackState
import com.pyradio.wear.model.Station
import com.pyradio.wear.playback.RadioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Чем сейчас ограничен список на экране. */
sealed interface StationFilter {
    data object All : StationFilter
    data object Favorites : StationFilter
    data class Genre(val name: String) : StationFilter
}

data class UiState(
    val stations: List<Station> = emptyList(),
    val genres: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val filter: StationFilter = StationFilter.All,
    val playback: PlaybackState = PlaybackState.Idle,
    val volume: Int = 0,
    val volumeSteps: Int = 0,
) {
    /** Доля громкости от нуля до единицы. Ноль ступеней — значит система молчит о ней. */
    val volumeFraction: Float
        get() = if (volumeSteps <= 0) 0f else volume.toFloat() / volumeSteps

    /** Избранное всегда сверху: до него не должно быть прокрутки в сорок строк. */
    val visible: List<Station>
        get() {
            val filtered = when (filter) {
                StationFilter.All -> stations
                StationFilter.Favorites -> stations.filter { it.id in favorites }
                is StationFilter.Genre -> stations.filter { it.genre == filter.name }
            }
            return if (filter == StationFilter.All) {
                filtered.sortedByDescending { it.id in favorites }
            } else {
                filtered
            }
        }
}

/**
 * Экранная часть. Ничего не воспроизводит и не разворачивает: просит [RadioService]
 * и показывает то, что тот опубликовал.
 *
 * Так сделано не ради чистоты слоёв, а потому что играть просят три поверхности,
 * и две из них — плитка и комплик — открываются, не заходя в приложение. Знай о
 * воспроизведении только эта модель, они не смогли бы ни запустить станцию, ни
 * узнать, что она играет.
 */
class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val deps = application as RadioApp

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stations = deps.stations.stations()
            val genres = deps.stations.genres()
            _uiState.update { it.copy(stations = stations, genres = genres) }
        }
        viewModelScope.launch {
            deps.preferences.favorites.collect { favorites ->
                _uiState.update { it.copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            deps.playback.state.collect { playback ->
                _uiState.update { it.copy(playback = playback) }
            }
        }
        readVolume()
    }

    fun play(station: Station) = RadioService.play(getApplication(), station.id)

    fun stop() = RadioService.stop(getApplication())

    fun retry() {
        val station = _uiState.value.playback.stationOrNull() ?: return
        viewModelScope.launch {
            // Повтор после отказа не должен упереться в тот же протухший адрес.
            deps.streams.invalidate(station.id)
            play(station)
        }
    }

    fun toggleFavorite(stationId: String) {
        viewModelScope.launch { deps.preferences.toggleFavorite(stationId) }
    }

    fun setFilter(filter: StationFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    // --- Громкость ---

    fun volumeUp() {
        deps.volume.raise()
        readVolume()
    }

    fun volumeDown() {
        deps.volume.lower()
        readVolume()
    }

    /**
     * Перечитать громкость у системы.
     *
     * Нужно не только после своих нажатий: её могли изменить снаружи — с
     * системной панели, кнопкой на гарнитуре, — и показывать в это время
     * запомненное значение значит показывать неправду.
     */
    fun readVolume() {
        _uiState.update {
            it.copy(volume = deps.volume.current(), volumeSteps = deps.volume.steps)
        }
    }
}
