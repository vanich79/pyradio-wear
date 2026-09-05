// Приложение для часов: экраны на Compose for Wear OS и медиасессия Media3.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pyradio.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pyradio.wear"
        // Wear OS 3 и новее. TicWatch Atlas, на котором проверялось, —
        // Wear OS 4 / Android 13 / API 33.
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        // Релизный ключ приходит из окружения, чтобы не лежать в репозитории.
        create("release") {
            val storePath = System.getenv("ATLAS_KEYSTORE")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("ATLAS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ATLAS_KEY_ALIAS")
                keyPassword = System.getenv("ATLAS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("ATLAS_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    sourceSets.named("main") { java.srcDirs("src/main/kotlin") }
    sourceSets.named("test") { java.srcDirs("src/test/kotlin") }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:resolver"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.tooling.preview)
    // Пометка «идёт длительная работа» для индикатора на циферблате.
    implementation(libs.wear.ongoing)
    // androidx.wear:wear и wear-input сюда намеренно не включены: ничего из них
    // приложение не использует, а первая тянет за собой androidx.fragment 1.2.4 —
    // версию, на которой ломается ActivityResult API.

    // Плитка и её разметка
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.tiles.tooling.preview)
    debugImplementation(libs.wear.tiles.tooling)

    // Комплик
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.concurrent.futures.ktx)

    // Плеер, медиасессия и HLS. Наличие media3-exoplayer-hls на classpath — это
    // и есть вся поддержка `.m3u8`: DefaultMediaSourceFactory находит её сама.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    // MediaController отдаётся через ListenableFuture, поэтому Guava нужна явно.
    implementation(libs.guava)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
