package com.pyradio.wear.resolver

import com.pyradio.wear.model.FailureReason
import com.pyradio.wear.model.Station
import com.pyradio.wear.model.StreamKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ProtocolException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Превращает ссылку из `stations.csv` в адрес, который примет ExoPlayer.
 *
 * PyRadio эту работу не делает сам — за него её делает `mpv`, разворачивая
 * `.pls` и `.m3u` молча. ExoPlayer так не умеет: он попытается проиграть текстовый
 * файл, не сможет и скажет «формат не поддерживается». Отсюда весь этот класс.
 *
 * Разворачивание живёт в рантайме, а не в сборке, потому что содержимое `.pls`
 * непостоянно: SomaFM отдаёт в нём текущие адреса CDN, и запечённый в APK список
 * протухнет раньше, чем выйдет следующая версия.
 */
class PlaylistResolver internal constructor(
    private val http: OkHttpClient,
    private val io: CoroutineDispatcher,
) {

    /**
     * Обычный способ создания. OkHttp намеренно не виден снаружи модуля:
     * приложению нечего настраивать в HTTP-клиенте резолвера, а протекший в
     * публичную сигнатуру тип потребовал бы тащить okhttp в classpath каждого,
     * кто просто хочет развернуть плейлист.
     */
    constructor() : this(defaultClient(), Dispatchers.IO)

    suspend fun resolve(station: Station): Resolution = withContext(io) {
        when (station.kind) {
            // Готовый поток и манифест HLS отдаём плееру нетронутыми: лишний
            // запрос здесь — это лишняя секунда до звука и лишний разряд батареи.
            StreamKind.DIRECT -> Resolution.Streams(listOf(station.url), hls = false)
            StreamKind.HLS -> Resolution.Streams(listOf(station.url), hls = true)
            StreamKind.PLAYLIST, StreamKind.UNKNOWN -> fetchAndUnwrap(station.url, depth = 0)
        }
    }

    private fun fetchAndUnwrap(url: String, depth: Int): Resolution {
        if (depth > MAX_DEPTH) return Resolution.Failure(FailureReason.STREAM_UNREACHABLE)

        val response = try {
            http.newCall(request(url)).execute()
        } catch (e: ProtocolException) {
            // OkHttp отказывается разбирать строку статуса `ICY 200 OK`, которую
            // отдают старые Shoutcast. Отказ — сам по себе ответ: текстовый файл
            // так не представляется, значит на том конце живой поток.
            return if (e.message?.contains("ICY", ignoreCase = true) == true) {
                Resolution.Streams(listOf(url), hls = false)
            } else {
                Resolution.Failure(FailureReason.STREAM_UNREACHABLE)
            }
        } catch (e: UnknownHostException) {
            // Имя не разрешилось — но чьё, наше или станции, отсюда не видно.
            // Домен `radio035.net` из плейлиста PyRadio мёртв, и на машине с
            // работающим интернетом он даёт ровно то же исключение, что и часы
            // без сети. Резолвер не берётся это различать: он честно говорит
            // «станция недоступна», а вывод «интернета нет вообще» делает тот,
            // кто может спросить систему, — приложение.
            return Resolution.Failure(FailureReason.STREAM_UNREACHABLE)
        } catch (e: IOException) {
            return Resolution.Failure(FailureReason.STREAM_UNREACHABLE)
        }

        response.use { r ->
            if (!r.isSuccessful) return Resolution.Failure(FailureReason.STREAM_UNREACHABLE)

            when (classify(r)) {
                // Тело качать нельзя: это уже звук, и чтение начнёт его буферизовать.
                // Закрываем ответ, не тронув ни байта, и отдаём адрес плееру.
                ContentClass.AUDIO -> return Resolution.Streams(listOf(url), hls = false)
                ContentClass.HLS -> return Resolution.Streams(listOf(url), hls = true)
                // Станция закрылась, а домен подхватил парковщик: на месте потока
                // лежит HTML. Это не «сеть упала» — переключаться, а не ждать.
                ContentClass.NOT_AUDIO -> return Resolution.Failure(FailureReason.NOT_AUDIO)
                ContentClass.PLAYLIST -> Unit
            }

            val body = r.body ?: return Resolution.Failure(FailureReason.EMPTY_PLAYLIST)
            val text = body.byteStream().readUpTo(MAX_BODY_BYTES).toString(Charsets.UTF_8)

            return when (val parsed = PlaylistParser.parse(text, base = r.request.url.toString())) {
                PlaylistParser.Body.Hls -> Resolution.Streams(listOf(url), hls = true)
                PlaylistParser.Body.Empty -> Resolution.Failure(FailureReason.EMPTY_PLAYLIST)
                is PlaylistParser.Body.Urls -> unwrapNested(parsed.urls, depth)
            }
        }
    }

    /**
     * Плейлист, ссылающийся на плейлист, — не выдумка: `tune_in.php` у SlayRadio
     * отдаёт `.m3u`, а внутри `.m3u` встречается `.pls`. Разворачиваем только те
     * вложения, которые видно по расширению, и только пока не кончилась глубина;
     * остальные адреса остаются кандидатами как есть.
     */
    private fun unwrapNested(urls: List<String>, depth: Int): Resolution {
        val head = urls.first()
        if (depth < MAX_DEPTH && head.looksLikePlaylist()) {
            val inner = fetchAndUnwrap(head, depth + 1)
            if (inner is Resolution.Streams) {
                // Развёрнутые адреса впереди, неразвёрнутые соседи — следом,
                // чтобы при обрыве было куда переключиться.
                return inner.copy(urls = (inner.urls + urls.drop(1)).distinct())
            }
        }
        return Resolution.Streams(urls, hls = false)
    }

    private enum class ContentClass { AUDIO, HLS, PLAYLIST, NOT_AUDIO }

    /**
     * Что нам ответили — по заголовку `Content-Type`.
     *
     * Расширению в адресе здесь не верим вовсе: `tune_in.php` не имеет расширения,
     * а `listen.pls?sid=1` имеет, но с параметром. Заголовок надёжнее, а когда его
     * нет — считаем плейлистом и смотрим тело, потому что ошибка в эту сторону
     * стоит чтения шестидесяти килобайт, а в обратную — сорванного воспроизведения.
     */
    private fun classify(r: Response): ContentClass {
        val type = r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            ?: return ContentClass.PLAYLIST

        return when (type) {
            "application/vnd.apple.mpegurl", "application/x-mpegurl",
            "audio/mpegurl", "audio/x-mpegurl",
            "audio/x-scpls", "application/pls+xml",
            -> ContentClass.PLAYLIST

            else -> when {
                type.startsWith("audio/") -> ContentClass.AUDIO
                type == "application/ogg" || type == "application/octet-stream" -> ContentClass.AUDIO
                type.startsWith("text/html") || type.startsWith("application/xhtml") -> ContentClass.NOT_AUDIO
                else -> ContentClass.PLAYLIST
            }
        }
    }

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        // Просим ICY-метаданные сразу: часть серверов по наличию этого заголовка
        // решает, отдавать поток или страницу-заглушку для браузера.
        .header("Icy-MetaData", "1")
        .header("Accept", "*/*")
        .build()

    /**
     * Читает не больше [limit] байт. `InputStream.readNBytes` подошёл бы, но он
     * появился только в API 33, а минимум у приложения — 30.
     */
    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun String.looksLikePlaylist(): Boolean {
        val path = substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".pls") || path.endsWith(".m3u")
    }

    companion object {
        private const val MAX_DEPTH = 2
        private const val MAX_BODY_BYTES = 64 * 1024
        private const val USER_AGENT = "PyRadioWear/1.0"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
