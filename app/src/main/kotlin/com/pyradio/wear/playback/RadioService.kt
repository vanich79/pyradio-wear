package com.pyradio.wear.playback

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pyradio.wear.R
import com.pyradio.wear.RadioApp
import com.pyradio.wear.model.FailureReason
import com.pyradio.wear.model.PlaybackState
import com.pyradio.wear.model.Station
import com.pyradio.wear.resolver.Resolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Держит плеер, медиасессию и всё, что нужно, чтобы станция заиграла.
 *
 * Запуск воспроизведения живёт здесь, а не в экране, намеренно: играть просят
 * три поверхности — приложение, плитка и комплик, — и две из них открываются, не
 * заходя в приложение. Единственное место, до которого дотягиваются все трое, —
 * служба. Она же публикует состояние в [PlaybackHub], откуда его читают все.
 *
 * Разворачиванием плейлистов служба не занимается: это `core:resolver`, и он
 * проверяется тестами без часов и эмулятора.
 */
@UnstableApi
class RadioService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var session: MediaSession? = null
    private val deps get() = application as RadioApp

    /** Станция, которую сейчас пытаются играть. Плеер знает только адрес. */
    private var current: Station? = null

    /**
     * Идёт наш собственный запуск: элементы плееру уже отданы, но `prepare` ещё
     * не вызван. В этом промежутке плеер сообщает `STATE_IDLE`, и без этой отметки
     * его невозможно отличить от остановки, пришедшей снаружи.
     */
    private var starting = false

    /** Сколько раз подряд переподключались, не услышав звука. Сбрасывается, когда он пошёл. */
    private var reconnects = 0
    private var reconnectJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            // Половина станций живёт на http, а их редиректы уводят то на https,
            // то обратно. Без этого флага плеер обрывает переход как небезопасный.
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            // Тот же заголовок, что и у резолвера: по нему Icecast понимает, что
            // на линии плеер, и начинает подмешивать в поток название трека.
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Наушники отключились — замолчать, а не заорать из динамика часов.
            .setHandleAudioBecomingNoisy(true)
            // Радио играет при погашенном экране, и без этого процессор с Wi-Fi
            // уснут вместе с ним.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                // Заводские значения рассчитаны на видео и держат полминуты буфера.
                // Для эфира это лишние секунды до первого звука и лишняя память;
                // здесь буфер укорочен до размеров, при которых старт заметно быстрее,
                // а запаса всё ещё хватает пережить провал связи на пару секунд.
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 30_000,
                        /* bufferForPlaybackMs = */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000,
                    )
                    .build(),
            )
            .build()

        player.addListener(playerListener)
        session = MediaSession.Builder(this, player).build()

        // Своё уведомление вместо заводского: то же самое плюс пометка
        // OngoingActivity, без которой Wear не считает воспроизведение
        // длительной работой пользователя.
        setMediaNotificationProvider(RadioNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                // На передний план выходим здесь, синхронно, ещё до обращения
                // к сети. Две причины. Первая: службу, запущенную через
                // startForegroundService, система убьёт, если startForeground не
                // случится за пять секунд, — а разворачивание .pls столько и
                // занимает. Вторая важнее: пока служба фоновая, App Standby вправе
                // её остановить, и это и происходило, стоило часам уйти в ambient:
                //   ActivityManager: Stopping service due to app idle: RadioService
                showPlaceholder()
                // Идентификатора может не быть: плитка просит «последнюю», не зная,
                // какая она. Кто именно — служба выясняет сама, чтобы вызывающему
                // не приходилось лезть в хранилище и ждать его ради одного нажатия.
                startStation(intent.getStringExtra(EXTRA_STATION_ID))
            }
            ACTION_STOP -> stopPlayback()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Ставит службу на передний план и обновляет её уведомление.
     *
     * Первый вызов приходит из `onStartCommand`, когда станция ещё неизвестна, —
     * там это заглушка «Подключение…». Дальше метод зовётся на каждую перемену
     * состояния, и `startForeground` с тем же номером просто заменяет содержимое:
     * имя станции, название трека, кнопка «Стоп».
     */
    /** Заглушка на те секунды, пока станция ещё не известна. */
    private fun showPlaceholder() = startForegroundWith(RadioNotifications.placeholder(this))

    private fun showNotification(state: PlaybackState) =
        startForegroundWith(RadioNotifications.build(this, state))

    private fun startForegroundWith(notification: android.app.Notification) {
        ServiceCompat.startForeground(
            this,
            RadioNotifications.ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    // --- Воспроизведение ---

    private fun startStation(stationId: String?) {
        reconnectJob?.cancel()
        scope.launch {
            val id = stationId ?: deps.preferences.lastStationId.first()
            val station = id?.let { deps.stations.byId(it) } ?: return@launch
            current = station
            publish(PlaybackState.Connecting(station))
            deps.preferences.rememberStation(station.id)

            when (val resolution = deps.streams.resolve(station)) {
                is Resolution.Failure -> publish(PlaybackState.Failed(station, explain(resolution.reason)))

                is Resolution.Streams -> {
                    // Пока ходили в сеть, могли попросить другую станцию —
                    // тогда этот ответ уже не про то, что играют.
                    if (current?.id != station.id) return@launch

                    val player = session?.player ?: return@launch
                    starting = true
                    // Все зеркала из .pls кладём в очередь разом: тогда обрыв
                    // первого — это переход к следующему элементу, а не новый
                    // круг разворачивания плейлиста.
                    player.setMediaItems(resolution.urls.map { mediaItem(it, station) })
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun stopPlayback() {
        reconnectJob?.cancel()
        reconnects = 0
        session?.player?.run {
            stop()
            clearMediaItems()
        }
        current = null
        starting = false
        publish(PlaybackState.Idle)
        // Держать службу без звука не за чем: состояние живёт в Application и
        // переживёт её смерть, а сессию система пересоздаст при следующем запуске.
        stopSelf()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()

        override fun onPlayerError(error: PlaybackException) {
            val station = current ?: return
            val player = session?.player ?: return

            if (player.hasNextMediaItem()) {
                // В плейлисте было несколько зеркал. Молча берём следующее:
                // для слушателя это не ошибка, а чуть более долгий старт.
                player.seekToNextMediaItem()
                player.prepare()
                return
            }

            // Зеркала кончились. Для эфира это ещё не приговор: в ambient связь
            // проваливается и возвращается, и правильный ответ — позвонить ещё раз,
            // а не замолчать навсегда. Сдаёмся только исчерпав попытки.
            if (reconnects < RECONNECT_DELAYS.size) {
                val delayMillis = RECONNECT_DELAYS[reconnects]
                reconnects++
                publish(PlaybackState.Connecting(station))
                reconnectJob = scope.launch {
                    delay(delayMillis)
                    // Адрес мог протухнуть вместе со связью — разворачиваем заново.
                    deps.streams.invalidate(station.id)
                    startStation(station.id)
                }
                return
            }

            reconnects = 0
            scope.launch { deps.streams.invalidate(station.id) }
            publish(PlaybackState.Failed(station, explain(error.toFailureReason())))
        }
    }

    private fun refresh() {
        val station = current ?: return
        val player = session?.player ?: return

        val next = when (player.playbackState) {
            Player.STATE_BUFFERING -> {
                starting = false
                PlaybackState.Buffering(station)
            }
            Player.STATE_READY ->
                if (player.isPlaying) {
                    // Звук пошёл — прошлые срывы больше не в счёт.
                    starting = false
                    reconnects = 0
                    PlaybackState.Playing(station, player.icyTitle())
                } else {
                    // Готов, но молчит: обычно чужое приложение забрало звук.
                    PlaybackState.Buffering(station)
                }
            // Поток кончился там, где кончиться не мог, — эфир оборвался.
            Player.STATE_ENDED -> PlaybackState.Failed(station, FailureReason.STREAM_UNREACHABLE)
            // STATE_IDLE во время нашего запуска — обычное дело: элементы отданы,
            // prepare ещё впереди. А тот же STATE_IDLE после того, как звук уже
            // шёл, означает остановку снаружи — медиакнопкой на гарнитуре или из
            // системной панели. Без этой ветки служба оставалась на переднем плане
            // с уведомлением «играет», которого уже не происходит.
            Player.STATE_IDLE -> {
                if (starting) return
                stopPlayback()
                return
            }
            else -> return
        }
        publish(next)
    }

    /**
     * Публикует состояние и просит циферблат с плиткой перерисоваться.
     *
     * Обе поверхности не опрашивают ничего сами: они рисуются по запросу и узнают
     * о переменах только отсюда.
     */
    private fun publish(state: PlaybackState) {
        // Звука нет и не будет — уходим с переднего плана. Иначе после
        // сорвавшейся станции на часах навсегда осталось бы уведомление
        // «идёт воспроизведение», которого не происходит, а служба держалась бы
        // в памяти до перезагрузки. Повтор поднимет её обратно.
        if (state is PlaybackState.Failed || state is PlaybackState.Idle) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } else {
            showNotification(state)
        }
        deps.playback.publish(state)
        RadioSurfaces.requestUpdate(this)
    }

    /**
     * Название трека из ICY. `null` — станция его не передаёт.
     *
     * Поле `title` принадлежит потоку и только ему: имя станции служба знает
     * из [current] и в метаданные плеера за ним не ходит.
     */
    private fun Player.icyTitle(): String? =
        mediaMetadata.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun mediaItem(url: String, station: Station) = MediaItem.Builder()
        .setUri(url)
        .setMediaId(station.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                // Имя станции идёт в `displayTitle` и `station`, но не в `title`.
                //
                // Media3 собирает итоговые метаданные как
                // `динамические.buildUpon().populate(mediaItem.mediaMetadata)` —
                // то есть наши накладываются поверх пришедших из потока. Имя
                // станции, положенное в `title`, затирало приходящий из ICY
                // `StreamTitle` при каждом обновлении, и название трека не
                // появлялось никогда: его перезаписывали мы сами.
                //
                // `displayTitle` при этом остаётся заголовком уведомления —
                // там нужна как раз станция, а не меняющийся каждые три минуты трек.
                .setDisplayTitle(station.name)
                .setStation(station.name)
                .setArtist(station.genre)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()

    /**
     * Уточняет причину отказа тем, что знает только приложение.
     *
     * Резолвер и плеер видят одно: адрес не ответил. Отличить мёртвую станцию от
     * часов в лифте может лишь тот, кто вправе спросить систему о сети, — и если
     * сети нет, любая другая формулировка вводит в заблуждение: человек начнёт
     * перебирать станции вместо того, чтобы выйти из лифта.
     */
    private fun explain(reason: FailureReason): FailureReason =
        if (deps.connectivity.isOnline()) reason else FailureReason.NO_NETWORK

    private fun PlaybackException.toFailureReason(): FailureReason = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> FailureReason.NO_NETWORK

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        -> FailureReason.STREAM_UNREACHABLE

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> FailureReason.UNSUPPORTED_FORMAT

        else -> FailureReason.STREAM_UNREACHABLE
    }

    /**
     * Пользователь смахнул приложение из недавних. Если звук идёт — это не повод
     * его обрывать: радио для того и фоновое. А если не идёт, держать процесс не за чем.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Служба уходит — значит звука больше нет, чем бы это ни было вызвано:
        // нашим stopSelf() или нехваткой памяти. Оставить в хабе «играет» значит
        // соврать плитке и циферблату, которые её переживают.
        deps.playback.publish(PlaybackState.Idle)
        RadioSurfaces.requestUpdate(this)

        scope.cancel()
        session?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        private const val USER_AGENT = "PyRadioWear/1.0"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000

        /**
         * Паузы между попытками переподключения, миллисекунды.
         *
         * Первая почти сразу — обрыв чаще всего мгновенный; дальше реже, чтобы не
         * долбиться в мёртвую станцию и не жечь батарею. Всего около минуты: дольше
         * ждать молча нельзя, человек должен увидеть отказ и решить сам.
         */
        private val RECONNECT_DELAYS = longArrayOf(2_000, 4_000, 8_000, 15_000, 30_000)

        private const val ACTION_PLAY = "com.pyradio.wear.action.PLAY"
        private const val ACTION_STOP = "com.pyradio.wear.action.STOP"
        private const val EXTRA_STATION_ID = "station_id"

        /**
         * Запустить станцию. Звать можно откуда угодно в процессе — из экрана,
         * из плитки, из комплика, — лишь бы в этот момент приложение было на
         * переднем плане: с Android 12 фоновый запуск службы система запрещает.
         */
        fun play(context: Context, stationId: String? = null) {
            // Именно startForegroundService, а не startService: обычная фоновая
            // служба на часах живёт до первого простоя приложения и умирает
            // вместе со звуком.
            ContextCompat.startForegroundService(
                context,
                Intent(context, RadioService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_STATION_ID, stationId),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RadioService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
