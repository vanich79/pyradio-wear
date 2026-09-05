package com.pyradio.wear.resolver

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Разбор тела плейлиста. Без сети и без корутин — чистая функция от текста,
 * поэтому проверяется таблицей примеров, а не запущенным сервером.
 */
internal object PlaylistParser {

    /** Что мы поняли про скачанный текст. */
    internal sealed interface Body {
        /** Манифест HLS. Ссылки внутри не наши: сегменты разбирает плеер. */
        data object Hls : Body

        /** Обычный плейлист: список адресов в порядке предпочтения. */
        data class Urls(val urls: List<String>) : Body

        /** Текст есть, адресов в нём нет. */
        data object Empty : Body
    }

    private const val MAX_URLS = 8

    /**
     * @param base адрес, откуда пришёл текст, — по нему достраиваются
     *   относительные ссылки, которые встречаются в `.m3u`.
     */
    fun parse(text: String, base: String): Body {
        // HLS опознаётся раньше всего: его теги начинаются с `#`, и наивный
        // разбор `.m3u` выбросил бы их как комментарии, оставив список сегментов,
        // каждый из которых — десять секунд звука. Играть надо манифест целиком.
        if (text.lineSequence().any { it.trimStart().startsWith("#EXT-X-") }) return Body.Hls

        val urls = if (isPls(text)) parsePls(text) else parseM3u(text)

        val baseUrl = base.toHttpUrlOrNull()
        val absolute = urls.mapNotNull { it.toAbsolute(baseUrl) }
            .distinct()
            .take(MAX_URLS)

        return if (absolute.isEmpty()) Body.Empty else Body.Urls(absolute)
    }

    private fun isPls(text: String): Boolean =
        text.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.equals("[playlist]", ignoreCase = true) == true

    /**
     * `.pls` — это ini: `File1=`, `File2=`, … Порядок в файле произвольный,
     * а предпочтение задаётся номером, поэтому сортируем по нему, а не по
     * появлению в тексте.
     */
    private fun parsePls(text: String): List<String> =
        text.lineSequence()
            .mapNotNull { line ->
                val t = line.trim()
                if (!t.startsWith("File", ignoreCase = true)) return@mapNotNull null
                val eq = t.indexOf('=')
                if (eq < 0) return@mapNotNull null
                val index = t.substring(4, eq).trim().toIntOrNull() ?: return@mapNotNull null
                val value = t.substring(eq + 1).trim()
                if (value.isEmpty()) null else index to value
            }
            .sortedBy { it.first }
            .map { it.second }
            .toList()

    /** `.m3u` — построчный список; всё, что начинается с `#`, комментарий. */
    private fun parseM3u(text: String): List<String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    /**
     * Относительные ссылки в `.m3u` законны, и достроить их можно только
     * относительно того адреса, откуда пришёл сам файл.
     */
    private fun String.toAbsolute(base: HttpUrl?): String? {
        toHttpUrlOrNull()?.let { return it.toString() }
        return base?.resolve(this)?.toString()
    }
}
