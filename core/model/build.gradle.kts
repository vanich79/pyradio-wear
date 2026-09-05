// Домен: станции, потоки, состояние воспроизведения. Чистый Kotlin без Android —
// тестируется обычным JUnit, без эмулятора.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
}

kotlin { jvmToolchain(21) }
