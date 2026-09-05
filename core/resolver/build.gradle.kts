// Разворачивание ссылок из плейлиста в настоящий адрес потока.
// Тоже чистый Kotlin: разбор .pls/.m3u проверяется на MockWebServer за секунды.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.serialization.json)
}

kotlin { jvmToolchain(21) }
