package com.pyradio.wear.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.pyradio.wear.R
import com.pyradio.wear.model.PlaybackState
import com.pyradio.wear.tile.RadioActionActivity
import com.pyradio.wear.ui.MainActivity

/**
 * Уведомление службы — то самое, которым она держится на переднем плане, и оно же
 * индикатор на циферблате.
 *
 * Строит его служба сама, а не Media3, и это вынужденно. Media3 берётся за
 * уведомление лишь тогда, когда к сессии подключился контроллер; в этом приложении
 * не подключается никто — экран говорит со службой напрямую, а плитка и комплик
 * тем более. В итоге на экране навсегда оставалась заглушка «Подключение…», а
 * пометки [OngoingActivity] не появлялось вовсе, потому что вешал её
 * [RadioNotificationProvider], которого Media3 ни разу не звал.
 *
 * Здесь же — «Стоп» прямо в уведомлении: пока радио играет, это самый короткий
 * путь его выключить.
 */
internal object RadioNotifications {

    const val ID = RadioNotificationProvider.NOTIFICATION_ID
    private const val CHANNEL = RadioNotificationProvider.CHANNEL_ID

    private const val REQUEST_OPEN = 10
    private const val REQUEST_STOP = 11

    private const val TEMPLATE = "#station#"
    private const val PART_STATION = "station"

    /**
     * Уведомление на те секунды, пока станция ещё не выяснена: служба обязана
     * выйти на передний план немедленно, а знать о станции ей в этот момент нечего.
     */
    fun placeholder(context: Context): Notification =
        notification(context, context.getString(R.string.app_name), context.getString(R.string.state_connecting))

    fun build(context: Context, state: PlaybackState): Notification {
        val station = state.stationOrNull()
        return notification(
            context,
            title = station?.name ?: context.getString(R.string.app_name),
            text = describe(context, state),
        )
    }

    private fun notification(context: Context, title: String, text: String): Notification {
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_stop),
                stop(context),
            )

        OngoingActivity.Builder(context, ID, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(openApp(context))
            // На циферблате нужна станция, а не трек: название меняется каждые
            // три минуты, и мигающая подпись там только мешает.
            .setStatus(
                Status.Builder()
                    .addTemplate(TEMPLATE)
                    .addPart(PART_STATION, Status.TextPart(title))
                    .build(),
            )
            .build()
            .apply(context)

        return builder.build()
    }

    private fun describe(context: Context, state: PlaybackState): String = when (state) {
        PlaybackState.Idle -> context.getString(R.string.state_idle)
        is PlaybackState.Connecting -> context.getString(R.string.state_connecting)
        is PlaybackState.Buffering -> context.getString(R.string.state_buffering)
        // Название трека, если станция его передаёт: ради него на уведомление
        // и смотрят.
        is PlaybackState.Playing -> state.title ?: context.getString(R.string.state_playing)
        is PlaybackState.Failed -> context.getString(R.string.tile_failed)
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** «Стоп» идёт через ту же заглушку, что и кнопка плитки, — служба слушает одно место. */
    private fun stop(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_STOP,
        RadioActionActivity.intent(context, RadioActionActivity.ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun PlaybackState.stationOrNull() = when (this) {
        PlaybackState.Idle -> null
        is PlaybackState.Connecting -> station
        is PlaybackState.Buffering -> station
        is PlaybackState.Playing -> station
        is PlaybackState.Failed -> station
    }
}
