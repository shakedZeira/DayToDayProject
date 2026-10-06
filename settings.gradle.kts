pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.google.com")
    }
    plugins {
        id("com.android.application") version "8.4.0" apply false
        id("org.jetbrains.kotlin.multiplatform") version "1.9.23" apply false
        id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
        id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
        id("com.google.dagger.hilt.android") version "2.50" apply false
        id("org.jetbrains.compose.desktop") version "1.6.10" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "DayToDayProject"

include(":client")
include(":server")
include(":shared")
include(":composeApp")