package com.pyradio.wear

import android.app.Application
import com.pyradio.wear.data.Connectivity
import com.pyradio.wear.data.RadioPreferences
import com.pyradio.wear.data.StationRepository
import com.pyradio.wear.data.StreamCache
import com.pyradio.wear.playback.PlaybackHub
import com.pyradio.wear.playback.Volume
import com.pyradio.wear.resolver.PlaylistResolver

/**
 * Держатель зависимостей. Их немного, они не меняются в рантайме и не подменяются
 * в тестах — то, что стоит за ними, тестируется на своём уровне, — поэтому
 * фреймворк внедрения здесь был бы дороже задачи.
 *
 * Живёт в `Application`, а не в службе, потому что читают отсюда четверо:
 * экран, служба, плитка и комплик. Последние двое открываются, когда службы
 * может не быть вовсе.
 */
class RadioApp : Application() {

    val stations: StationRepository by lazy { StationRepository(this) }
    val preferences: RadioPreferences by lazy { RadioPreferences(this) }
    val streams: StreamCache by lazy { StreamCache(PlaylistResolver()) }
    val connectivity: Connectivity by lazy { Connectivity(this) }
    val playback: PlaybackHub by lazy { PlaybackHub() }
    val volume: Volume by lazy { Volume(this) }
}
