package io.github.kmpfire.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectDetectorJvmTest {
    @Test
    fun detectsNewTemplate() {
        val root = Files.createTempDirectory("kmpfire-new").toFile()
        try {
            FileTree(root).apply {
                file("settings.gradle.kts", "rootProject.name = \"demo\"")
                dir("shared")
                dir("androidApp")
                file(
                    "androidApp/build.gradle.kts",
                    """
                    android {
                      namespace = "com.example.shared"
                      defaultConfig { applicationId = "com.example.app" }
                    }
                    """.trimIndent(),
                )
                dir("iosApp/iosApp")
                file(
                    "iosApp/App.xcodeproj/project.pbxproj",
                    "PRODUCT_BUNDLE_IDENTIFIER = com.example.app.ios;",
                )
                file("iosApp/iosApp/Info.plist", "<plist/>")
            }

            val detected = ProjectDetector().detect(root.absolutePath)
            assertEquals(LayoutKind.NewTemplate, detected.layoutKind)
            assertEquals("com.example.app", detected.androidPackageName)
            assertEquals("com.example.app.ios", detected.iosBundleId)
            assertEquals(
                root.resolve("androidApp/google-services.json").absolutePath,
                detected.defaultAndroidOut,
            )
            assertEquals(
                root.resolve("iosApp/iosApp/GoogleService-Info.plist").absolutePath,
                detected.defaultIosOut,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun detectsAppNestedTemplate() {
        val root = Files.createTempDirectory("kmpfire-app-nested").toFile()
        try {
            FileTree(root).apply {
                file("settings.gradle.kts", "rootProject.name = \"loggoo\"")
                dir("app/shared")
                dir("app/androidApp")
                file(
                    "app/androidApp/build.gradle.kts",
                    """
                    android {
                      namespace = "com.loggoo.shared"
                      defaultConfig { applicationId = "com.loggoo.app" }
                    }
                    """.trimIndent(),
                )
                dir("app/iosApp/iosApp")
                file(
                    "app/iosApp/iosApp.xcodeproj/project.pbxproj",
                    "PRODUCT_BUNDLE_IDENTIFIER = com.loggoo.app.ios;",
                )
                file("app/iosApp/iosApp/Info.plist", "<plist/>")
                dir("core")
                dir("server")
            }

            val detected = ProjectDetector().detect(root.absolutePath)
            assertEquals(LayoutKind.AppNested, detected.layoutKind)
            assertEquals("com.loggoo.app", detected.androidPackageName)
            assertEquals("com.loggoo.app.ios", detected.iosBundleId)
            assertEquals(
                root.resolve("app/androidApp/google-services.json").absolutePath,
                detected.defaultAndroidOut,
            )
            assertEquals(
                root.resolve("app/iosApp/iosApp/GoogleService-Info.plist").absolutePath,
                detected.defaultIosOut,
            )
            assertEquals(
                root.resolve("app/shared").absolutePath,
                detected.sharedModuleDir,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun detectsLegacyComposeApp() {
        val root = Files.createTempDirectory("kmpfire-legacy").toFile()
        try {
            FileTree(root).apply {
                file("settings.gradle.kts", "rootProject.name = \"demo\"")
                dir("composeApp")
                file(
                    "composeApp/build.gradle.kts",
                    """android { namespace = "com.example.compose" }""",
                )
                dir("iosApp")
                file(
                    "iosApp/iosApp.xcodeproj/project.pbxproj",
                    "PRODUCT_BUNDLE_IDENTIFIER = com.example.compose.ios;",
                )
            }

            val detected = ProjectDetector().detect(root.absolutePath)
            assertEquals(LayoutKind.LegacyComposeApp, detected.layoutKind)
            assertEquals("com.example.compose", detected.androidPackageName)
            assertEquals("com.example.compose.ios", detected.iosBundleId)
            assertEquals(
                root.resolve("composeApp/google-services.json").absolutePath,
                detected.defaultAndroidOut,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun detectsIosBundleIdFromComposeMultiplatformXcconfig() {
        val root = Files.createTempDirectory("kmpfire-xcconfig").toFile()
        try {
            FileTree(root).apply {
                file("settings.gradle.kts", "rootProject.name = \"KMPFireTest\"")
                dir("shared")
                dir("androidApp")
                file(
                    "androidApp/build.gradle.kts",
                    """android { namespace = "me.dungngminh.kmpfiretest" }""",
                )
                dir("iosApp/iosApp")
                file(
                    "iosApp/iosApp.xcodeproj/project.pbxproj",
                    """
                    baseConfigurationReferenceRelativePath = Config.xcconfig;
                    GENERATE_INFOPLIST_FILE = YES;
                    """.trimIndent(),
                )
                file(
                    "iosApp/Configuration/Config.xcconfig",
                    """
                    TEAM_ID=

                    PRODUCT_NAME=KMPFireTest
                    PRODUCT_BUNDLE_IDENTIFIER=me.dungngminh.kmpfiretest.KMPFireTest${'$'}(TEAM_ID)
                    """.trimIndent(),
                )
            }

            val detected = ProjectDetector().detect(root.absolutePath)
            assertEquals("me.dungngminh.kmpfiretest.KMPFireTest", detected.iosBundleId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failsWhenNotGradleRoot() {
        val root = Files.createTempDirectory("kmpfire-empty").toFile()
        try {
            assertFailsWith<ProjectDetectionException> {
                ProjectDetector().detect(root.absolutePath)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

private class FileTree(private val root: java.io.File) {
    fun dir(relative: String) {
        root.resolve(relative).mkdirs()
    }

    fun file(relative: String, contents: String) {
        val f = root.resolve(relative)
        f.parentFile.mkdirs()
        f.writeText(contents)
    }
}
