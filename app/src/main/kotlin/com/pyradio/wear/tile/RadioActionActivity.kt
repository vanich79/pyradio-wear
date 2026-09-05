package com.pyradio.wear.tile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.pyradio.wear.playback.RadioService

/**
 * Прозрачная заглушка без интерфейса: получает нажатие с плитки, толкает
 * [RadioService] и тут же закрывается.
 *
 * Существует не от хорошей жизни. Плитка умеет ровно два действия — перерисовать
 * себя и запустить **активность**; запустить службу она не может. А запуск службы
 * из фона с Android 12 запрещён, и разрешение даётся как раз тому, кто пришёл из
 * активности. Отсюда эта пустая: она нужна системе как основание, а пользователю
 * не показывается вовсе — тема прозрачная, окно не рисуется, в «недавних» не
 * попадает. Экран остаётся на плитке.
 */
class RadioActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.getStringExtra(EXTRA_ACTION)) {
            // Идентификатор станции не передаётся: плитка просит «последнюю», а
            // какая она — знает служба. Иначе заглушке пришлось бы дождаться
            // чтения хранилища, то есть пожить, а жить ей здесь нечем.
            ACTION_PLAY_LAST -> RadioService.play(this)
            ACTION_STOP -> RadioService.stop(this)
        }

        finish()
        // Гасим переход: любая анимация здесь — это мигание пустым окном поверх
        // плитки, ради которого пользователь ничего не нажимал.
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_ACTION = "action"
        const val ACTION_PLAY_LAST = "play_last"
        const val ACTION_STOP = "stop"

        /** Намерение для комплика, которому нужен `PendingIntent`, а не `LaunchAction`. */
        fun intent(context: Context, action: String): Intent =
            Intent(context, RadioActionActivity::class.java)
                .setAction(action)
                .putExtra(EXTRA_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
