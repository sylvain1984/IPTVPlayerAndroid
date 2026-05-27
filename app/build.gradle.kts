import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localProperty(name: String): String =
    localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""

val hasReleaseSigning = localProperty("ANDROID_SIGNING_STORE_FILE").isNotBlank()

android {
    namespace = "com.iptv.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.iptv.player"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "RTC_APP_ID", "\"${localProperty("RTC_APP_ID")}\"")
        buildConfigField("String", "RTC_TOKEN_URL", "\"${localProperty("RTC_TOKEN_URL")}\"")
        buildConfigField("String", "LIVE_REGISTRY_URL", "\"${localProperty("LIVE_REGISTRY_URL")}\"")
    }

    signingConfigs {
        create("release") {
            val signingStoreFile = localProperty("ANDROID_SIGNING_STORE_FILE")
            if (signingStoreFile.isNotBlank()) {
                storeFile = file(signingStoreFile)
            }
            storePassword = localProperty("ANDROID_SIGNING_STORE_PASSWORD")
            keyAlias = localProperty("ANDROID_SIGNING_KEY_ALIAS")
            keyPassword = localProperty("ANDROID_SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")

    // Jetpack Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-rc01")

    // Media3 / ExoPlayer (HLS, HTTP TS, RTMP via extension)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")

    // OkHttp (replaces URLSession)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines (replaces Swift async/await + actors)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ViewModel + StateFlow (replaces @MainActor ObservableObject + @Published)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")

    // DataStore (replaces file-based JSON persistence)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Gson (replaces Codable)
    implementation("com.google.code.gson:gson:2.10.1")

    // Coil (replaces AsyncImage)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // WorkManager — background periodic refresh
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Volcano Engine RTC native SDK (exclude legacy support lib that conflicts with AndroidX)
    implementation("com.volcengine:VolcEngineRTC:3.56.1.508200")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
