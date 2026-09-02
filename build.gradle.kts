import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}
// --- ADDED THIS BLOCK TO TARGET A SINGLE VERSION ---
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
        version.set(providers.gradleProperty("pluginVersion").orElse("0.0.1"))
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
    version = providers.gradleProperty("pluginVersion").orElse("0.0.1").get()
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
