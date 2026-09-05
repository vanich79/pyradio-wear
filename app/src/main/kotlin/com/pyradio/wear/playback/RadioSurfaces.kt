package com.pyradio.wear.playback

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.pyradio.wear.complication.NowPlayingComplicationService
import com.pyradio.wear.tile.RadioTileService

/**
 * Просит плитку и комплик перерисоваться.
 *
 * Обе поверхности сами ничего не опрашивают и не просыпаются по таймеру: они
 * рисуются по запросу системы и узнают о переменах только отсюда. Будить часы
 * ради того, чтобы перечитать то же самое, — самый дешёвый способ посадить
 * батарею на устройстве, которое и так живёт сутки.
 */
internal object RadioSurfaces {

    fun requestUpdate(context: Context) {
        TileService.getUpdater(context).requestUpdate(RadioTileService::class.java)

        ComplicationDataSourceUpdateRequester
            .create(
                context,
                ComponentName(context, NowPlayingComplicationService::class.java),
            )
            .requestUpdateAll()
    }
}
