import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}
// --- VERIFY AGAINST ALL BUILDS AFTER 2026 ---
intellijPlatform {
    pluginVerification {
        ides {
            // Verify against all IDEA builds from 2026 onward
            select {
                types.set(listOf(IntelliJPlatformType.IntellijIdea))
                sinceBuild = "2026"
            }
        }
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

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
