import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("io.ktor:ktor-client-core:2.3.8")
                implementation("io.ktor:ktor-client-json:2.3.8")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
                implementation("io.ktor:ktor-client-logging:2.3.8")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
                implementation(platform("androidx.compose:compose-bom:2024.06.00"))
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.animation:animation")
                implementation("androidx.compose.material3:material3")
                implementation("androidx.compose.material:material-icons-extended")
                implementation("androidx.navigation:navigation-compose:2.8.3")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
                implementation("io.insert-koin:koin-core:3.5.6")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
                implementation("io.ktor:ktor-client-android:2.3.8")
                implementation(platform("androidx.compose:compose-bom:2024.06.00"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-tooling")
                implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
                implementation("androidx.datastore:datastore-preferences:1.1.1")
                implementation("androidx.datastore:datastore-preferences-rxjava3:1.1.1")
                implementation("io.reactivex.rxjava3:rxjava:3.1.6")
                implementation("androidx.room:room-runtime:2.6.1")
                implementation("androidx.room:room-ktx:2.6.1")
                implementation("io.insert-koin:koin-android:3.5.6")
                implementation("io.insert-koin:koin-androidx-compose:3.5.6")

                // Coil3 (code imports coil3.* APIs)
                implementation("io.coil-kt.coil3:coil-compose:3.0.4")
                // Without a network artifact Coil3 has no https fetcher and every
                // remote image falls through to the error slot.
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

                // Hilt
                implementation("com.google.dagger:hilt-android:2.52")
                implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
                implementation("androidx.activity:activity-compose:1.10.0")

                // Health Connect - reads Samsung Health step data (steps only;
                // calories are derived locally by CalorieCalculator, never fetched).
                // 1.1.0 is the first stable release; requires compileSdk 36+ and AGP 8.9.1+.
                implementation("androidx.health.connect:connect-client:1.1.0")

                // Spotify - PKCE auth + playback control
                implementation("com.squareup.retrofit2:retrofit:2.11.0")
                implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
                implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
                implementation("androidx.browser:browser:1.8.0")
            }
        }
    }
}

fun spotifyProps(name: String, default: String = ""): String {
    val props = Properties()
    val f = rootProject.file("spotify.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    return props.getProperty(name, default).trim()
}

android {
    namespace = "com.daytoday"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daytoday"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${spotifyProps("SPOTIFY_CLIENT_ID")}\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"${spotifyProps("SPOTIFY_REDIRECT_URI", "daytoday://callback")}\"")
        buildConfigField("String", "SPOTIFY_AUTH_URL", "\"https://accounts.spotify.com/authorize\"")
        buildConfigField("String", "SPOTIFY_TOKEN_URL", "\"https://accounts.spotify.com/api/token\"")
        buildConfigField("String", "BASE_URL_SPOTIFY", "\"https://api.spotify.com/v1/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }

    namespace = "com.daytoday"
}

dependencies {
    // KSP for Room annotation processing
    add("ksp", "androidx.room:room-compiler:2.6.1")
    add("ksp", "com.google.dagger:hilt-android-compiler:2.52")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.robolectric:robolectric:4.11")
    
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")
}