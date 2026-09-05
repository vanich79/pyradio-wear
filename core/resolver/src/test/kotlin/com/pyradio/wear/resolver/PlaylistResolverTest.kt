package com.pyradio.wear.resolver

import com.pyradio.wear.model.FailureReason
import com.pyradio.wear.model.Station
import com.pyradio.wear.model.StreamKind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Проверки на форматах, которые реально встретились в `stations.csv`: примеры
 * ниже — сокращённые тела ответов SomaFM, SlayRadio и Frequence 3, а не выдумка.
 */
class PlaylistResolverTest {

    private lateinit var server: MockWebServer
    private val resolver = PlaylistResolver()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun station(path: String, kind: StreamKind = StreamKind.PLAYLIST) = Station(
        id = "test",
        name = "Test",
        url = server.url(path).toString(),
        genre = "Test",
        kind = kind,
    )

    private fun enqueue(body: String, contentType: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", contentType)
                .setBody(body),
        )
    }

    @Test
    fun `pls разворачивается в адреса по порядку File-N`() = runTest {
        // Порядок строк в файле обратный номерам — предпочтение задаёт номер.
        enqueue(
            """
            [playlist]
            numberofentries=2
            File2=http://ice2.somafm.com/groovesalad-128-mp3
            File1=http://ice1.somafm.com/groovesalad-128-mp3
            Title1=SomaFM: Groove Salad
            Version=2
            """.trimIndent(),
            "audio/x-scpls",
        )

        val result = resolver.resolve(station("/groovesalad.pls"))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf(
            "http://ice1.somafm.com/groovesalad-128-mp3",
            "http://ice2.somafm.com/groovesalad-128-mp3",
        )
        result.hls shouldBe false
    }

    @Test
    fun `m3u отбрасывает комментарии и достраивает относительные ссылки`() = runTest {
        enqueue(
            """
            #EXTM3U
            #EXTINF:-1,Frequence 3
            /hd-mp3-stream
            http://other.example.org/backup
            """.trimIndent(),
            "audio/x-mpegurl",
        )

        val result = resolver.resolve(station("/hd-mp3.m3u"))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf(
            server.url("/hd-mp3-stream").toString(),
            "http://other.example.org/backup",
        )
    }

    @Test
    fun `манифест HLS не разворачивается, а отдаётся плееру целиком`() = runTest {
        // Ключевая проверка: строки внутри — это сегменты по несколько секунд.
        // Развернув их, мы проиграли бы десять секунд эфира и замолчали.
        enqueue(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            segment-001.aac
            #EXTINF:10.0,
            segment-002.aac
            """.trimIndent(),
            "application/vnd.apple.mpegurl",
        )

        val url = server.url("/playlist.m3u8").toString()
        val result = resolver.resolve(station("/playlist.m3u8"))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf(url)
        result.hls shouldBe true
    }

    @Test
    fun `готовый поток не вызывает ни одного запроса`() = runTest {
        val result = resolver.resolve(station("/live.mp3", kind = StreamKind.DIRECT))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf(server.url("/live.mp3").toString())
        server.requestCount shouldBe 0
    }

    @Test
    fun `манифест по расширению m3u8 отдаётся без запроса`() = runTest {
        val result = resolver.resolve(station("/live.m3u8", kind = StreamKind.HLS))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.hls shouldBe true
        server.requestCount shouldBe 0
    }

    @Test
    fun `адрес без расширения, отдающий звук, признаётся потоком`() = runTest {
        // Radio Paradise: `Content-Type: audio/aacp`, тела читать нельзя.
        enqueue("<двоичный звук>", "audio/aacp")

        val url = server.url("/aac-128").toString()
        val result = resolver.resolve(station("/aac-128", kind = StreamKind.UNKNOWN))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf(url)
    }

    @Test
    fun `html на месте потока отличается от обрыва сети`() = runTest {
        enqueue("<html><body>Station offline</body></html>", "text/html; charset=utf-8")

        val result = resolver.resolve(station("/listen.pls"))

        result shouldBe Resolution.Failure(FailureReason.NOT_AUDIO)
    }

    @Test
    fun `плейлист без единого адреса — это не поток`() = runTest {
        enqueue("[playlist]\nnumberofentries=0\nVersion=2", "audio/x-scpls")

        val result = resolver.resolve(station("/empty.pls"))

        result shouldBe Resolution.Failure(FailureReason.EMPTY_PLAYLIST)
    }

    @Test
    fun `вложенный плейлист разворачивается, соседи остаются запасными`() = runTest {
        // SlayRadio: `tune_in.php` отдаёт `.m3u`, а тот ссылается на `.pls`.
        val nested = server.url("/inner.pls").toString()
        enqueue("$nested\nhttp://fallback.example.org/stream", "audio/x-mpegurl")
        enqueue("[playlist]\nFile1=http://real.example.org/128\n", "audio/x-scpls")

        val result = resolver.resolve(station("/tune_in.php"))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf(
            "http://real.example.org/128",
            "http://fallback.example.org/stream",
        )
    }

    @Test
    fun `параметр запроса не мешает опознать pls`() = runTest {
        // JazzGroove: `listen.pls?sid=1` — расширение есть, но не в конце адреса.
        enqueue("[playlist]\nFile1=http://jazz.example.org/8015\n", "audio/x-scpls")

        val result = resolver.resolve(station("/listen.pls?sid=1"))

        result.shouldBeInstanceOf<Resolution.Streams>()
        result.urls shouldContainExactly listOf("http://jazz.example.org/8015")
    }

    @Test
    fun `мёртвый домен — это недоступная станция, а не отсутствие сети`() = runTest {
        // `.invalid` не резолвится нигде и никогда (RFC 2606), поэтому проверка
        // не зависит от того, есть ли интернет у машины, где идут тесты.
        // Ровно так себя ведёт radio035.net из плейлиста PyRadio: домен истёк.
        val dead = Station(
            id = "dead",
            name = "Dead",
            url = "http://station.invalid/listen.pls",
            genre = "Test",
            kind = StreamKind.PLAYLIST,
        )

        val result = resolver.resolve(dead)

        result shouldBe Resolution.Failure(FailureReason.STREAM_UNREACHABLE)
    }

    @Test
    fun `ответ 404 — станция недоступна`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = resolver.resolve(station("/gone.pls"))

        result shouldBe Resolution.Failure(FailureReason.STREAM_UNREACHABLE)
    }
}
