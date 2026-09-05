package com.pyradio.wear.data

import android.content.Context
import com.pyradio.wear.model.Station
import com.pyradio.wear.model.StationCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Каталог станций, импортированный из `stations.csv` PyRadio.
 *
 * Список зашит в ассеты, а не тянется по сети: он не меняется без участия
 * пользователя, а часы, оставшиеся без интернета, всё равно должны показывать,
 * что у них есть. Обновление — это перезапуск `tools/import_pyradio.py` и новая
 * сборка; для сорока четырёх станций, меняющихся раз в год, этого достаточно.
 */
class StationRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: List<Station>? = null

    suspend fun stations(): List<Station> = cached ?: withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString<StationCatalog>(text).stations.also { cached = it }
    }

    suspend fun byId(id: String): Station? = stations().firstOrNull { it.id == id }

    /** Жанры в порядке убывания числа станций — так фильтр наверху полезнее. */
    suspend fun genres(): List<String> = stations()
        .groupingBy { it.genre }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

    private companion object {
        const val ASSET = "stations.json"
    }
}
