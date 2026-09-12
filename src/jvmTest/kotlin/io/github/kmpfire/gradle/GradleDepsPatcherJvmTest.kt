package io.github.kmpfire.gradle

import io.github.kmpfire.configure.SdkMode
import io.github.kmpfire.project.DetectedProject
import io.github.kmpfire.project.LayoutKind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GradleDepsPatcherJvmTest {
    @Test
    fun patchesNewTemplateIdempotently() {
        val root = Files.createTempDirectory("kmpfire-deps").toFile()
        try {
            root.resolve("gradle").mkdirs()
            root.resolve("gradle/libs.versions.toml").writeText(
                """
                [versions]
                agp = "8.7.0"
                [libraries]
                [plugins]
                androidApplication = { id = "com.android.application", version.ref = "agp" }
                """.trimIndent(),
            )
            root.resolve("build.gradle.kts").writeText(
                """
                plugins {
                    alias(libs.plugins.androidApplication) apply false
                }
                """.trimIndent(),
            )
            root.resolve("androidApp").mkdirs()
            root.resolve("androidApp/build.gradle.kts").writeText(
                """
                plugins {
                    alias(libs.plugins.androidApplication)
                }
                """.trimIndent(),
            )
            root.resolve("shared").mkdirs()
            root.resolve("shared/build.gradle.kts").writeText(
                """
                kotlin {
                    sourceSets {
                        commonMain.dependencies {
                            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                        }
                    }
                }
                """.trimIndent(),
            )

            val detected = DetectedProject(
                root = root.absolutePath,
                layoutKind = LayoutKind.NewTemplate,
                androidModuleDir = root.resolve("androidApp").absolutePath,
                iosAppDir = null,
                sharedModuleDir = root.resolve("shared").absolutePath,
                androidPackageName = "com.example.app",
                iosBundleId = null,
                defaultAndroidOut = root.resolve("androidApp/google-services.json").absolutePath,
                defaultIosOut = null,
            )

            val patcher = GradleDepsPatcher()
            val first = patcher.patch(detected, SdkMode.GitLive)
            assertTrue(first.changedFiles.isNotEmpty())
            assertTrue(root.resolve("androidApp/build.gradle.kts").readText().contains("google.services"))
            assertTrue(root.resolve("shared/build.gradle.kts").readText().contains("dev.gitlive:firebase-app"))

            val second = patcher.patch(detected, SdkMode.GitLive)
            assertEquals(0, second.changedFiles.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nativeSkipsGitLive() {
        val root = Files.createTempDirectory("kmpfire-deps-native").toFile()
        try {
            root.resolve("gradle").mkdirs()
            root.resolve("gradle/libs.versions.toml").writeText(
                """
                [versions]
                [libraries]
                [plugins]
                """.trimIndent(),
            )
            root.resolve("build.gradle.kts").writeText("plugins {\n}\n")
            root.resolve("androidApp").mkdirs()
            root.resolve("androidApp/build.gradle.kts").writeText("plugins {\n}\n")
            root.resolve("shared").mkdirs()
            root.resolve("shared/build.gradle.kts").writeText(
                "kotlin { sourceSets { commonMain.dependencies {\n} } }\n",
            )

            val detected = DetectedProject(
                root = root.absolutePath,
                layoutKind = LayoutKind.NewTemplate,
                androidModuleDir = root.resolve("androidApp").absolutePath,
                iosAppDir = null,
                sharedModuleDir = root.resolve("shared").absolutePath,
                androidPackageName = null,
                iosBundleId = null,
                defaultAndroidOut = null,
                defaultIosOut = null,
            )
            GradleDepsPatcher().patch(detected, SdkMode.Native)
            assertTrue(!root.resolve("shared/build.gradle.kts").readText().contains("gitlive"))
        } finally {
            root.deleteRecursively()
        }
    }
}
