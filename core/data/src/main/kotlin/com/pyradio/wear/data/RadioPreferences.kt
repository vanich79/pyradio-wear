package com.pyradio.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "radio")

/**
 * Избранное и последняя игравшая станция.
 *
 * Громкости здесь нет намеренно: на часах ею заведует система — колёсико и
 * системная панель, — и заводить вторую, свою, значит рассинхронизировать их.
 */
class RadioPreferences(context: Context) {

    private val store = context.applicationContext.dataStore

    val favorites: Flow<Set<String>> = store.data.map { it[FAVORITES].orEmpty() }

    /**
     * Последняя станция. `null` означает «ещё ни разу не слушали» — и это не то же
     * самое, что первая станция списка: на пустом состоянии экран здоровается,
     * а не делает вид, что помнит выбор.
     */
    val lastStationId: Flow<String?> = store.data.map { it[LAST_STATION] }

    suspend fun toggleFavorite(id: String) {
        store.edit { prefs ->
            val current = prefs[FAVORITES].orEmpty()
            prefs[FAVORITES] = if (id in current) current - id else current + id
        }
    }

    suspend fun rememberStation(id: String) {
        store.edit { it[LAST_STATION] = id }
    }

    private companion object {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val LAST_STATION = stringPreferencesKey("last_station")
    }
}
