package com.pyradio.wear.resolver

import com.pyradio.wear.model.StationCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Обход всего каталога по настоящей сети: какие станции из плейлиста PyRadio ещё живы.
 *
 * Не запускается вместе с остальными тестами и не должен: он ходит в интернет, идёт
 * минуту и краснеет от чужих неполадок, а не от наших. Это диагностика — «что
 * отвалилось за год», — и запускается руками:
 *
 * ```sh
 * PYRADIO_SMOKE=1 ./gradlew :core:resolver:test --tests '*RealStationsSmokeTest*' -i
 * ```
 */
class RealStationsSmokeTest {

    @Test
    fun `весь каталог разворачивается`() {
        assumeTrue("нужен PYRADIO_SMOKE=1", System.getenv("PYRADIO_SMOKE") == "1")

        val catalog = File("../../app/src/main/assets/stations.json")
        val stations = Json { ignoreUnknownKeys = true }
            .decodeFromString<StationCatalog>(catalog.readText())
            .stations

        val resolver = PlaylistResolver()
        val failures = mutableListOf<String>()

        runBlocking {
            stations.forEach { station ->
                when (val result = resolver.resolve(station)) {
                    is Resolution.Streams ->
                        println("ok    ${station.name} -> ${result.urls.size} адр. ${result.urls.first()}")

                    is Resolution.Failure -> {
                        println("ПЛОХО ${station.name} -> ${result.reason}")
                        failures += "${station.name}: ${result.reason}"
                    }
                }
            }
        }

        println("\nживых: ${stations.size - failures.size} из ${stations.size}")
        failures.forEach { println("  $it") }
    }
}
