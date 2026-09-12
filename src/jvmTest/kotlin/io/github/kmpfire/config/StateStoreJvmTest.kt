package io.github.kmpfire.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StateStoreJvmTest {
    @Test
    fun roundTripState_writesKmpSectionInFirebaseJson() {
        val root = Files.createTempDirectory("kmpfire-state").toFile()
        try {
            val store = StateStore()
            val state = KmpfireState(
                projectId = "demo",
                sdk = "gitlive",
                layoutKind = "new-template",
                android = PlatformConfigs(
                    default = PlatformState(
                        appId = "1:1:android:a",
                        fileOutput = "androidApp/google-services.json",
                        packageNameOrBundleId = "com.example.app",
                    ),
                    buildConfigurations = mapOf(
                        "dev" to PlatformState(
                            appId = "1:1:android:dev",
                            fileOutput = "androidApp/src/dev/google-services.json",
                            packageNameOrBundleId = "com.example.app.dev",
                        ),
                    ),
                ),
                ios = PlatformConfigs(
                    default = PlatformState(
                        appId = "1:1:ios:b",
                        fileOutput = "iosApp/iosApp/GoogleService-Info.plist",
                        packageNameOrBundleId = "com.example.app.ios",
                    ),
                ),
            )
            store.write(root.absolutePath, state)
            val firebaseJson = root.resolve("firebase.json")
            assertTrue(firebaseJson.exists())
            assertFalse(root.resolve("kmpfire.json").exists())
            val text = firebaseJson.readText()
            assertTrue(text.contains("\"kotlinMultiplatform\""))
            assertTrue(text.contains("\"projectId\""))
            assertTrue(text.contains("\"platforms\""))
            assertTrue(text.contains("\"buildConfigurations\""))
            assertTrue(text.contains("\"dev\""))
            assertFalse(text.contains("\"kmpfire\""))
            assertFalse(text.contains("\"kmp\""))

            val loaded = store.read(root.absolutePath)
            assertNotNull(loaded)
            assertEquals("demo", loaded.projectId)
            assertEquals("1:1:android:a", loaded.android?.default?.appId)
            assertEquals("1:1:android:dev", loaded.android?.buildConfigurations?.get("dev")?.appId)
            assertEquals("1:1:ios:b", loaded.ios?.default?.appId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun write_mergesWithoutClobberingExistingFirebaseKeys() {
        val root = Files.createTempDirectory("kmpfire-merge").toFile()
        try {
            root.resolve("firebase.json").writeText(
                """
                {
                  "hosting": { "public": "public" },
                  "flutter": { "platforms": {} }
                }
                """.trimIndent(),
            )
            StateStore().write(
                root.absolutePath,
                KmpfireState(
                    projectId = "demo",
                    layoutKind = "new-template",
                    android = PlatformConfigs(
                        default = PlatformState(
                            appId = "1:1:android:a",
                            fileOutput = "androidApp/google-services.json",
                        ),
                    ),
                ),
            )
            val text = root.resolve("firebase.json").readText()
            assertTrue(text.contains("\"hosting\""))
            assertTrue(text.contains("\"flutter\""))
            assertTrue(text.contains("\"kotlinMultiplatform\""))
            assertTrue(text.contains("\"public\""))
            assertFalse(text.contains("\"kmp\""))
            assertFalse(text.contains("\"kmpfire\""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun read_fallsBackToLegacyKmpfireJson() {
        val root = Files.createTempDirectory("kmpfire-legacy").toFile()
        try {
            root.resolve("kmpfire.json").writeText(
                """
                {
                  "projectId": "legacy-demo",
                  "sdk": "native",
                  "layoutKind": "app-nested",
                  "android": {
                    "appId": "1:1:android:x",
                    "fileOutput": "app/androidApp/google-services.json",
                    "packageNameOrBundleId": "com.legacy"
                  }
                }
                """.trimIndent(),
            )
            val loaded = StateStore().read(root.absolutePath)
            assertNotNull(loaded)
            assertEquals("legacy-demo", loaded.projectId)
            assertEquals("native", loaded.sdk)
            assertEquals("1:1:android:x", loaded.android?.default?.appId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun hasKmpSection_detectsKey() {
        val root = Files.createTempDirectory("kmpfire-has").toFile()
        try {
            val store = StateStore()
            assertFalse(store.hasKmpSection(root.absolutePath))
            store.write(
                root.absolutePath,
                KmpfireState(
                    projectId = "demo",
                    layoutKind = "new-template",
                    android = PlatformConfigs(
                        default = PlatformState(appId = "1:1:android:a", fileOutput = "a.json"),
                    ),
                ),
            )
            assertTrue(store.hasKmpSection(root.absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }
}

class ConfigWriterJvmTest {
    @Test
    fun writesNestedServiceFile() {
        val root = Files.createTempDirectory("kmpfire-config").toFile()
        try {
            val path = root.resolve("androidApp/google-services.json").absolutePath
            ConfigWriter().writeServiceFile(path, """{"ok":true}""")
            assertEquals("""{"ok":true}""", root.resolve("androidApp/google-services.json").readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
