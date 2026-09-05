package com.pyradio.wear.playback

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService

/**
 * Громкость музыкального потока.
 *
 * Своей громкости приложение не заводит и не должно: на часах ею заведует система,
 * её же слушают наушники и медиасессия. Здесь только тонкая обёртка над
 * `STREAM_MUSIC` — чтобы колесо и кнопки на экране двигали ровно тот регулятор,
 * который пользователь потом увидит в системной панели.
 */
class Volume(context: Context) {

    private val audio = context.applicationContext.getSystemService<AudioManager>()

    /** Сколько всего ступеней. На TicWatch Atlas их немного, обычно 15. */
    val steps: Int get() = audio?.getStreamMaxVolume(STREAM) ?: 0

    fun current(): Int = audio?.getStreamVolume(STREAM) ?: 0

    fun raise() = adjust(AudioManager.ADJUST_RAISE)

    fun lower() = adjust(AudioManager.ADJUST_LOWER)

    private fun adjust(direction: Int) {
        // Системный ползунок не показываем: он накрыл бы экран целиком при каждом
        // щелчке колеса, а свой уровень мы и так рисуем.
        runCatching { audio?.adjustStreamVolume(STREAM, direction, /* flags = */ 0) }
        // Молча глотаем только отказ: в режиме «Не беспокоить» система запрещает
        // менять громкость без отдельного разрешения, и падать из-за этого нельзя.
    }

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
    }
}
