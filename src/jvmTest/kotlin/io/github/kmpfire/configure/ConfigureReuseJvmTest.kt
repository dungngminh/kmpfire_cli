package io.github.kmpfire.configure

import io.github.kmpfire.cli.UserPrompt
import io.github.kmpfire.config.KmpfireState
import io.github.kmpfire.config.PlatformConfigs
import io.github.kmpfire.config.PlatformState
import io.github.kmpfire.config.StateStore
import io.github.kmpfire.firebase.FirebaseApp
import io.github.kmpfire.firebase.FirebaseAppSdkConfig
import io.github.kmpfire.firebase.FirebaseClient
import io.github.kmpfire.firebase.FirebaseProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigureReuseJvmTest {
    @Test
    fun interactiveReuse_prefillsProjectAndPathsFromKmpSection() {
        val root = Files.createTempDirectory("kmpfire-reuse").toFile()
        try {
            root.resolve("settings.gradle.kts").writeText("rootProject.name=\"demo\"")
            root.resolve("shared").mkdirs()
            root.resolve("androidApp").mkdirs()
            root.resolve("androidApp/build.gradle.kts").writeText(
                """android { defaultConfig { applicationId = "com.example.app" } }""",
            )
            root.resolve("iosApp/iosApp").mkdirs()
            root.resolve("iosApp/App.xcodeproj").mkdirs()
            root.resolve("iosApp/App.xcodeproj/project.pbxproj").writeText(
                "PRODUCT_BUNDLE_IDENTIFIER = com.example.app.ios;",
            )

            StateStore().write(
                root.absolutePath,
                KmpfireState(
                    projectId = "stored-project",
                    sdk = "gitlive",
                    layoutKind = "new-template",
                    android = PlatformConfigs(
                        default = PlatformState(
                            appId = "1:1:android:a",
                            fileOutput = "androidApp/google-services.json",
                            packageNameOrBundleId = "com.example.app",
                        ),
                    ),
                ),
            )

            val prompt = object : UserPrompt {
                override fun confirm(message: String, defaultYes: Boolean): Boolean {
                    assertTrue(message.contains("firebase.json"))
                    assertTrue(message.contains("`kotlinMultiplatform`"))
                    return true
                }

                override fun choose(message: String, choices: List<String>): Int = 0
                override fun ask(message: String, default: String?): String =
                    default ?: error(message)

                override fun multiSelect(
                    message: String,
                    choices: List<String>,
                    defaultSelected: List<Boolean>,
                ): List<Int> = defaultSelected.mapIndexedNotNull { i, on -> if (on) i else null }
            }

            val fake = object : FirebaseClient {
                override fun exists(): Boolean = true
                override fun version(): String = "13.0.0"
                override fun projectsList(
                    account: String?,
                    token: String?,
                    serviceAccount: String?,
                ): List<FirebaseProject> = listOf(
                    FirebaseProject(projectId = "stored-project", displayName = "Stored", state = "ACTIVE"),
                )

                override fun projectsCreate(
                    projectId: String,
                    displayName: String?,
                    account: String?,
                    token: String?,
                    serviceAccount: String?,
                ): FirebaseProject = error("should not create")

                override fun appsList(
                    project: String,
                    platform: String?,
                    account: String?,
                    token: String?,
                    serviceAccount: String?,
                ): List<FirebaseApp> = listOf(
                    FirebaseApp("1:1:android:a", "a", "ANDROID", "com.example.app", null),
                )

                override fun appsCreateAndroid(
                    project: String,
                    displayName: String,
                    packageName: String,
                    account: String?,
                    token: String?,
                    serviceAccount: String?,
                ): FirebaseApp = error("should reuse")

                override fun appsCreateIos(
                    project: String,
                    displayName: String,
                    bundleId: String,
                    account: String?,
                    token: String?,
                    serviceAccount: String?,
                ): FirebaseApp = error("no ios")

                override fun appsSdkConfig(
                    platform: String,
                    appId: String,
                    account: String?,
                    token: String?,
                    serviceAccount: String?,
                    project: String?,
                ): FirebaseAppSdkConfig = FirebaseAppSdkConfig(
                    fileName = "google-services.json",
                    fileContents = """{"reused":true}""",
                )
            }

            val result = ConfigureService(firebaseCli = fake, prompt = prompt).configure(
                ConfigureOptions(
                    projectDir = root.absolutePath,
                    firebaseProjectId = null,
                    platforms = null,
                    sdk = SdkMode.GitLive,
                    yes = false,
                    deps = false,
                    dryRun = false,
                    androidPackageName = null,
                    iosBundleId = null,
                    androidOut = null,
                    iosOut = null,
                ),
            )

            assertTrue(result.messages.any { it.contains("Reusing existing firebase.json kotlinMultiplatform") })
            assertEquals("""{"reused":true}""", root.resolve("androidApp/google-services.json").readText())
            val reloaded = StateStore().read(root.absolutePath)
            assertEquals("stored-project", reloaded?.projectId)
            assertTrue(root.resolve("firebase.json").readText().contains("\"kotlinMultiplatform\""))
        } finally {
            root.deleteRecursively()
        }
    }
}
