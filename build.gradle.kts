import org.ajoberstar.grgit.Grgit
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("org.ajoberstar.grgit") version "5.3.3"
}
// --- VERSION MANAGEMENT ---
// Derive the plugin version from the nearest git tag (e.g. tag `v0.1.1` -> `0.1.1`).
// When there are commits past the last tag, we append `-SNAPSHOT` to distinguish
// pre-release builds from the released artifact.
// An explicit `-PpluginVersion=...` (or gradle.properties `pluginVersion`) still wins
// over git derivation for CI builds / release drafts.
fun resolveGitVersion(): String {
    return try {
        val git = Grgit.open(mapOf("dir" to rootProject.projectDir))
        try {
            val exact = git.describe {
                tags = true
            }
            if (exact != null && !exact.contains("-")) return exact.removePrefix("v")

            // No exact tag match at HEAD: use the nearest ancestor tag plus a -SNAPSHOT suffix.
            val nearest = git.describe {
                tags = true
            }
            when {
                nearest.isNullOrBlank() -> "0.0.1-SNAPSHOT"
                nearest.contains("-") -> {
                    val m = Regex("^v?(.+)-([0-9]+)-g[0-9a-f]+$").matchEntire(nearest)
                    if (m != null) "${m.groupValues[1]}-${m.groupValues[2]}-SNAPSHOT" else nearest.removePrefix("v")
                }
                else -> "${nearest.removePrefix("v")}-SNAPSHOT"
            }
        } finally {
            git.close()
        }
    } catch (e: Exception) {
        "0.0.1-SNAPSHOT"
    }
}

val resolvedPluginVersion: String = providers.gradleProperty("pluginVersion")
    .orElse(providers.provider { resolveGitVersion() })
    .get()

project.version = resolvedPluginVersion

intellijPlatform {
    pluginVerification {
        ides {
            // This forces the verifier to ONLY test against 2025.2.6.2
            // Targets only your exact version explicitly
            select {
                // Pin it to IntelliJ IDEA Ultimate or Community matching your exact build
                types.set(listOf(IntelliJPlatformType.IntellijIdea))
                version = "2026.1.3"
//                sinceBuild.set("2025.2.6.2")
//                untilBuild.set("2025.2.6.2")
            }
        }
    }
    pluginConfiguration {
        version.set(resolvedPluginVersion)
        changeNotes.set(provider {
            val latest = try {
                changelog.getLatest()
            } catch (e: Exception) {
                changelog.getUnreleased()
            }
            changelog.renderItem(latest, Changelog.OutputType.HTML)
        })
    }
    publishing {
        token.set(System.getenv("INTELLIJ_PLUGIN_PUBLISH_TOKEN"))
        channels.set(listOf("default"))
        hidden.set(true)
    }
}
// --------------------------------------------------
dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.1.3")
        testFramework(TestFrameworkType.Platform)
    }
}

afterEvaluate {
    version = resolvedPluginVersion
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
