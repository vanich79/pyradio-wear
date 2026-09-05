package com.pyradio.wear.tile

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.pyradio.wear.R
import com.pyradio.wear.RadioApp
import com.pyradio.wear.model.PlaybackState
import com.pyradio.wear.model.Station
import com.pyradio.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Плитка: свайп вправо с циферблата — и последняя станция играет с одного касания.
 *
 * В сеть плитка не ходит и ходить не может: на её построение система даёт доли
 * секунды, а разворачивание `.pls` в это окно не укладывается. Она читает
 * запомненную станцию и текущее состояние из процесса, рисует их, а всю работу
 * по нажатию делает [com.pyradio.wear.playback.RadioService].
 */
class RadioTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        scope.launch {
            val deps = applicationContext as RadioApp
            val playback = deps.playback.state.value

            // Что показывать: то, что играет, а если ничего — то, что слушали
            // последним. Плитка без имени станции бесполезна, а «последняя» —
            // единственный осмысленный ответ на вопрос «что включить одним касанием».
            val station = playback.stationOrNull()
                ?: deps.preferences.lastStationId.first()?.let { deps.stations.byId(it) }

            completer.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setTileTimeline(
                        TimelineBuilders.Timeline.fromLayoutElement(
                            layout(applicationContext, requestParams.deviceConfiguration, station, playback),
                        ),
                    )
                    .build(),
            )
        }
        "tile-request"
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        // Своих картинок у плитки нет: имя станции и одна кнопка говорят всё,
        // и не требуют ресурсов, которые пришлось бы версионировать.
        completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        "tile-resources"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}

/** Станция, к которой относится состояние, — или `null`, когда состояние ни к чему не относится. */
private fun PlaybackState.stationOrNull(): Station? = when (this) {
    PlaybackState.Idle -> null
    is PlaybackState.Connecting -> station
    is PlaybackState.Buffering -> station
    is PlaybackState.Playing -> station
    is PlaybackState.Failed -> station
}

/**
 * Разметка плитки: имя станции, строка состояния и одна кнопка.
 *
 * Список станций сюда не помещается и не должен: плитку открывают, чтобы включить
 * то же, что и вчера, а выбирать идут в приложение — оно в одном касании по телу
 * плитки.
 */
private fun layout(
    context: Context,
    device: DeviceParameters,
    station: Station?,
    playback: PlaybackState,
): LayoutElementBuilders.LayoutElement {

    val playing = playback is PlaybackState.Playing ||
        playback is PlaybackState.Buffering ||
        playback is PlaybackState.Connecting

    val headline = station?.name ?: context.getString(R.string.tile_no_station)
    val caption = when (playback) {
        PlaybackState.Idle ->
            if (station == null) {
                context.getString(R.string.tile_pick_in_app)
            } else {
                context.getString(R.string.tile_last_played)
            }
        is PlaybackState.Connecting -> context.getString(R.string.state_connecting)
        is PlaybackState.Buffering -> context.getString(R.string.state_buffering)
        // Название трека, если станция его передаёт: на плитке это самая живая
        // строка из возможных, и ради неё стоит потеснить всё остальное.
        is PlaybackState.Playing -> playback.title ?: context.getString(R.string.state_playing)
        is PlaybackState.Failed -> context.getString(R.string.tile_failed)
    }

    val accent = if (playback is PlaybackState.Failed) ERROR else ACCENT

    val content = LayoutElementBuilders.Box.Builder()
        .setWidth(dp(device.screenWidthDp.toFloat() * CONTENT_WIDTH_FRACTION))
        // Тело плитки открывает приложение: там список и выбор.
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setClickable(openApp(context))
                .build(),
        )
        .addContent(
            LayoutElementBuilders.Column.Builder()
                .addContent(
                    Text.Builder(context, headline)
                        .setTypography(Typography.TYPOGRAPHY_TITLE3)
                        .setColor(argb(accent))
                        .setMaxLines(2)
                        .build(),
                )
                .addContent(
                    Text.Builder(context, caption)
                        .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                        .setColor(argb(MUTED))
                        .setMaxLines(2)
                        .build(),
                )
                .build(),
        )
        .build()

    val builder = PrimaryLayout.Builder(device)
        .setResponsiveContentInsetEnabled(true)
        .setPrimaryLabelTextContent(
            Text.Builder(context, context.getString(R.string.app_name))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .setColor(argb(MUTED))
                .build(),
        )
        .setContent(content)

    // Кнопки нет, только когда нечего включать: слушали ещё ни разу. Предлагать
    // «Играть» в этом случае значит обещать то, чего плитка не может выполнить.
    if (station != null) {
        builder.setPrimaryChipContent(
            CompactChip.Builder(
                context,
                context.getString(if (playing) R.string.action_stop else R.string.action_play),
                action(context, if (playing) RadioActionActivity.ACTION_STOP else RadioActionActivity.ACTION_PLAY_LAST),
                device,
            )
                .setChipColors(ChipColors.primaryChipColors(Colors.DEFAULT))
                .build(),
        )
    }

    return builder.build()
}

/**
 * Нажатие уходит в [RadioActionActivity], а не прямо в службу: плитка умеет
 * запускать только активность.
 */
private fun action(context: Context, action: String): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setId(action)
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(RadioActionActivity::class.java.name)
                        .addKeyToExtraMapping(
                            "action",
                            ActionBuilders.stringExtra(action),
                        )
                        .build(),
                )
                .build(),
        )
        .build()

private fun openApp(context: Context): ModifiersBuilders.Clickable =
    ModifiersBuilders.Clickable.Builder()
        .setId("open")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(context.packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()

/**
 * Цвета плитки заданы числами, а не темой Compose: плитка рисуется системным
 * процессом по описанию разметки, и `MaterialTheme` сюда не доходит.
 */
private const val CONTENT_WIDTH_FRACTION = 0.9f
private val ACCENT = 0xFFAECBFA.toInt()
private val MUTED = 0xFF9AA0A6.toInt()
private val ERROR = 0xFFEE675C.toInt()
