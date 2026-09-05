package com.pyradio.wear.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.pyradio.wear.R
import com.pyradio.wear.ui.MainActivity

/**
 * Уведомление медиасессии, помеченное как **ongoing activity**.
 *
 * Media3 умеет строить уведомление само, и почти всё здесь делает именно оно.
 * Надстройка нужна ради Wear: `OngoingActivity` — это способ сказать системе
 * часов, что идёт длительная работа, начатая пользователем. Без такой пометки
 * приложение с точки зрения энергополитики ничем не отличается от фонового,
 * а на циферблате нет ни следа того, что радио играет.
 *
 * Заодно это возврат на экран воспроизведения одним касанием индикатора.
 */
@UnstableApi
class RadioNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private val delegate = DefaultMediaNotificationProvider.Builder(context)
        .build()
        .apply { setSmallIcon(R.drawable.ic_notification) }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val base = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback,
        )

        // Пересобираем построенное уведомление, чтобы дописать в него пометку.
        // Media3 отдаёт готовый Notification, а OngoingActivity умеет работать
        // только со строителем, — отсюда этот разбор и сборка обратно.
        val builder = NotificationCompat.Builder(context, base.notification)

        OngoingActivity.Builder(context, base.notificationId, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(openApp())
            .setStatus(status(mediaSession))
            .build()
            .apply(context)

        return MediaNotification(base.notificationId, builder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: android.os.Bundle,
    ): Boolean = false

    /**
     * Подпись индикатора — имя станции.
     *
     * Берём `displayTitle`, а не `title`: последний принадлежит потоку и меняется
     * с каждым треком, а на циферблате нужна станция, которая не мигает.
     */
    private fun status(session: MediaSession): Status {
        val station = session.player.mediaMetadata.displayTitle?.toString()
            ?: context.getString(R.string.app_name)

        return Status.Builder()
            .addTemplate(TEMPLATE)
            .addPart(PART_STATION, Status.TextPart(station))
            .build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /**
         * Идентификатор и канал берём те же, что у Media3.
         *
         * Служба выставляет себя на передний план сама, ещё до того как заиграет
         * звук, и делает это своим уведомлением-заглушкой. Совпадение номера с
         * media3 означает, что её настоящее уведомление не добавится вторым, а
         * заменит заглушку на месте.
         */
        const val NOTIFICATION_ID = DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID
        const val CHANNEL_ID = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID

        private const val TEMPLATE = "#station#"
        private const val PART_STATION = "station"
        private const val REQUEST_OPEN = 3
    }
}
