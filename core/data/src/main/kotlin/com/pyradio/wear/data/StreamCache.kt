package com.pyradio.wear.data

import com.pyradio.wear.model.Station
import com.pyradio.wear.resolver.PlaylistResolver
import com.pyradio.wear.resolver.Resolution
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Помнит, чем закончилось разворачивание плейлиста, чтобы переключение туда-обратно
 * между двумя станциями не стоило двух запросов каждый раз.
 *
 * Кэш живёт только в памяти процесса и только [TTL_MILLIS]. Оба ограничения
 * намеренные: адреса внутри `.pls` у SomaFM — это текущие узлы CDN, они меняются,
 * и кэш, переживающий перезапуск, однажды начнёт настойчиво звонить по адресу,
 * которого больше нет. Полчаса — это про «послушал, отвлёкся, вернулся», а не
 * про «хранить вечно».
 */
class StreamCache(
    private val resolver: PlaylistResolver,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class Entry(val streams: Resolution.Streams, val at: Long)

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun resolve(station: Station): Resolution {
        mutex.withLock {
            entries[station.id]?.let { if (now() - it.at < TTL_MILLIS) return it.streams }
        }

        // Запрос идёт вне блокировки: разворачивание ходит в сеть на секунды, и
        // держать на нём мьютекс значит заморозить переключение станций.
        val result = resolver.resolve(station)

        if (result is Resolution.Streams) {
            mutex.withLock { entries[station.id] = Entry(result, now()) }
        }
        return result
    }

    /** Забыть станцию: вызывается, когда её поток оборвался и адрес пора перепроверить. */
    suspend fun invalidate(stationId: String) {
        mutex.withLock { entries.remove(stationId) }
    }

    private companion object {
        const val TTL_MILLIS = 30 * 60 * 1000L
    }
}
