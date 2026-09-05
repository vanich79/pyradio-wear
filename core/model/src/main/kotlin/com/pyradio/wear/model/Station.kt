package com.pyradio.wear.model

import kotlinx.serialization.Serializable

/**
 * Станция в том виде, в каком она пришла из плейлиста PyRadio.
 *
 * [url] — это адрес **из файла**, а не адрес потока: у большинства станций в
 * `stations.csv` он ведёт на `.pls` или `.m3u`, внутри которого лежит настоящая
 * ссылка. Что именно с ним делать, говорит [kind]; разворачиванием занимается
 * `core:resolver`.
 */
@Serializable
data class Station(
    val id: String,
    val name: String,
    val url: String,
    val genre: String,
    val kind: StreamKind,
)

/**
 * Чем является [Station.url] по первому впечатлению — по расширению пути.
 *
 * Это гипотеза, а не факт: `slayradio.org/tune_in.php` отдаёт `.m3u`, не имея
 * расширения вовсе. Резолвер перепроверяет вид по телу ответа и вправе решить
 * иначе, поэтому ошибка в этом поле стоит одного лишнего запроса, а не поломки.
 */
@Serializable
enum class StreamKind {
    /** Готовый поток: `.mp3`, `.aac`, `.ogg`. Отдаётся плееру как есть. */
    DIRECT,

    /** Манифест HLS (`.m3u8`). Тоже отдаётся как есть — ExoPlayer разбирает сам. */
    HLS,

    /** Обёртка `.pls` / `.m3u`: внутри один или несколько адресов потока. */
    PLAYLIST,

    /** Расширения нет. Обычно ICY/Shoutcast, но точно скажет только ответ сервера. */
    UNKNOWN,
}

/** Содержимое `assets/stations.json` — результат импорта `stations.csv`. */
@Serializable
data class StationCatalog(
    val version: Int,
    val source: String,
    val stations: List<Station>,
)
