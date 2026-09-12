package io.github.kmpfire.configure

import io.github.kmpfire.firebase.FirebaseApp
import io.github.kmpfire.firebase.FirebaseAppSdkConfig
import io.github.kmpfire.firebase.FirebaseClient
import io.github.kmpfire.firebase.FirebaseProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigureServiceJvmTest {
    @Test
    fun dryRunWritesNothingButRecordsPlan() {
        val root = Files.createTempDirectory("kmpfire-cfg").toFile()
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

            val fake = FakeFirebaseClient()
            val service = ConfigureService(firebaseCli = fake)
            val result = service.configure(
                ConfigureOptions(
                    projectDir = root.absolutePath,
                    firebaseProjectId = "demo-firebase",
                    platforms = listOf(TargetPlatform.Android, TargetPlatform.Ios),
                    sdk = SdkMode.GitLive,
                    yes = true,
                    deps = false,
                    dryRun = true,
                    androidPackageName = null,
                    iosBundleId = null,
                    androidOut = null,
                    iosOut = null,
                ),
            )

            assertTrue(result.messages.any { it.contains("dry-run") })
            assertTrue(result.wroteFiles.isEmpty())
            assertEquals(0, fake.createAndroidCalls)
            assertEquals(false, root.resolve("firebase.json").exists())
            assertEquals(false, root.resolve("kmpfire.json").exists())
            assertEquals(false, root.resolve("androidApp/google-services.json").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingFirebaseCliFails() {
        val service = ConfigureService(firebaseCli = FakeFirebaseClient(exists = false))
        assertFailsWith<ConfigureException> {
            service.configure(
                ConfigureOptions(
                    projectDir = "/tmp",
                    firebaseProjectId = "x",
                    platforms = listOf(TargetPlatform.Android),
                    sdk = SdkMode.GitLive,
                    yes = true,
                    deps = false,
                    dryRun = true,
                    androidPackageName = "com.example",
                    iosBundleId = null,
                    androidOut = "androidApp/google-services.json",
                    iosOut = null,
                ),
            )
        }
    }
}

private class FakeFirebaseClient(
    private val exists: Boolean = true,
) : FirebaseClient {
    var createAndroidCalls = 0

    override fun exists(): Boolean = exists
    override fun version(): String = "13.0.0-fake"

    override fun projectsList(
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): List<FirebaseProject> = listOf(FirebaseProject(projectId = "demo-firebase", state = "ACTIVE"))

    override fun projectsCreate(
        projectId: String,
        displayName: String?,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseProject = FirebaseProject(projectId = projectId, displayName = displayName, state = "ACTIVE")

    override fun appsList(
        project: String,
        platform: String?,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): List<FirebaseApp> = emptyList()

    override fun appsCreateAndroid(
        project: String,
        displayName: String,
        packageName: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseApp {
        createAndroidCalls++
        return FirebaseApp("1:1:android:x", displayName, "ANDROID", packageName, null)
    }

    override fun appsCreateIos(
        project: String,
        displayName: String,
        bundleId: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseApp = FirebaseApp("1:1:ios:y", displayName, "IOS", null, bundleId)

    override fun appsSdkConfig(
        platform: String,
        appId: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
        project: String?,
    ): FirebaseAppSdkConfig = FirebaseAppSdkConfig(
        fileName = if (platform.equals("ANDROID", true)) "google-services.json" else "GoogleService-Info.plist",
        fileContents = "config-for-$platform",
    )
}
