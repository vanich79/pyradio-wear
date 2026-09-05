package com.pyradio.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.pyradio.wear.R
import com.pyradio.wear.RadioApp
import com.pyradio.wear.model.PlaybackState
import com.pyradio.wear.model.Station
import com.pyradio.wear.tile.RadioActionActivity
import com.pyradio.wear.ui.MainActivity
import kotlinx.coroutines.flow.first

/**
 * Комплик: что играет — прямо на циферблате, и одно касание, чтобы включить.
 *
 * Самая дешёвая из трёх поверхностей: чтобы узнать, играет ли радио и что именно,
 * не нужно ни открывать приложение, ни листать до плитки — достаточно поднять руку.
 *
 * В сеть не ходит и по таймеру не просыпается: `UPDATE_PERIOD_SECONDS` в манифесте
 * равен нулю, а перерисовка приходит из [com.pyradio.wear.playback.RadioService]
 * тогда, когда состояние действительно изменилось.
 */
class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        // Предпросмотр в списке выбора компликов. Правдоподобный, но заведомо
        // ненастоящий: показывать здесь реальное состояние нельзя — его ещё нет.
        build(type, PREVIEW_GENRE, PREVIEW_STATION, playing = true, tap = null)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val deps = applicationContext as RadioApp
        val playback = deps.playback.state.value

        val playing = playback.stationOrNull()
        val station = playing
            ?: deps.preferences.lastStationId.first()?.let { deps.stations.byId(it) }

        val isOnAir = playback is PlaybackState.Playing || playback is PlaybackState.Buffering ||
            playback is PlaybackState.Connecting

        return build(
            type = request.complicationType,
            // Короткая форма — это жанр, а не имя станции: в семь знаков, которые
            // даёт циферблат, «Chillout» помещается, а «Chillout (Groove Salad -
            // SomaFM)» превращается в «Chillo…» и не значит ничего.
            short = if (isOnAir) station?.genre else null,
            long = station?.let { describe(it, playback) },
            playing = isOnAir,
            tap = tapAction(station, isOnAir),
        )
    }

    private fun build(
        type: ComplicationType,
        short: String?,
        long: String?,
        playing: Boolean,
        tap: PendingIntent?,
    ): ComplicationData? {
        // Прочерк означает «не играет», и это не то же самое, что пустая строка:
        // циферблат обязан показать что-то определённое, иначе комплик выглядит
        // сломанным, а не молчащим.
        val shortText = short ?: DASH
        val description = long ?: getString(R.string.complication_idle)

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(shortText).build(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            )
                .setMonochromaticImage(icon())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(description).build(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            )
                .setTitle(
                    PlainComplicationText.Builder(
                        getString(if (playing) R.string.state_playing else R.string.complication_off),
                    ).build(),
                )
                .setMonochromaticImage(icon())
                .setTapAction(tap)
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = icon(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            )
                .setTapAction(tap)
                .build()

            // Типов компликов больше, чем этот источник умеет. Шкала, например,
            // радио не описывает никак. Возвращать что-то приблизительное вместо
            // `null` значит занять место на циферблате бессмысленным элементом.
            else -> null
        }
    }

    private fun describe(station: Station, playback: PlaybackState): String = when (playback) {
        is PlaybackState.Playing -> playback.title ?: station.name
        is PlaybackState.Connecting -> getString(R.string.state_connecting)
        is PlaybackState.Buffering -> getString(R.string.state_buffering)
        is PlaybackState.Failed -> getString(R.string.tile_failed)
        PlaybackState.Idle -> station.name
    }

    /**
     * Куда ведёт нажатие.
     *
     * Пока молчит — включает последнюю станцию: ради этого комплик и ставят.
     * Пока играет — открывает приложение, а не останавливает. Случайно задеть
     * циферблат легко, и цена ошибки здесь несимметрична: лишний раз открыть
     * список не жалко, а оборванный эфир придётся включать заново.
     */
    private fun tapAction(station: Station?, playing: Boolean): PendingIntent? {
        val intent = when {
            station == null -> Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            playing -> Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else -> RadioActionActivity.intent(this, RadioActionActivity.ACTION_PLAY_LAST)
        }

        return PendingIntent.getActivity(
            this,
            if (playing || station == null) REQUEST_OPEN else REQUEST_PLAY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Платформенный Icon, а не IconCompat: API компликов объявлено через первый.
    private fun icon() = MonochromaticImage.Builder(
        Icon.createWithResource(this, R.drawable.ic_notification),
    ).build()

    private fun PlaybackState.stationOrNull(): Station? = when (this) {
        PlaybackState.Idle -> null
        is PlaybackState.Connecting -> station
        is PlaybackState.Buffering -> station
        is PlaybackState.Playing -> station
        is PlaybackState.Failed -> station
    }

    private companion object {
        const val DASH = "—"
        const val PREVIEW_GENRE = "Ambient"
        const val PREVIEW_STATION = "Groove Salad"
        const val REQUEST_OPEN = 1
        const val REQUEST_PLAY = 2
    }
}
