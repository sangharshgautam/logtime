import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "logtime"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.10"
        id("org.jetbrains.changelog") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // Configure all projects' repositories
    repositories {
        // Route Maven Central through JetBrains' cache-redirector mirror. Direct access from
        // shared CI runners frequently gets 429 "Too Many Requests" from repo.maven.apache.org,
        // which disables the repo for the whole build and cascades into other resolution failures.
        maven("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2/")

        // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
        intellijPlatform {
            defaultRepositories()
        }
    }
}
