package io.github.kmpfire.configure

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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReconfigureServiceJvmTest {
    @Test
    fun rewritesServiceFilesFromState() {
        val root = Files.createTempDirectory("kmpfire-reconfig").toFile()
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
                """
                /* Begin PBXBuildFile section */
                /* End PBXBuildFile section */
                /* Begin PBXFileReference section */
                		BB0000000000000000000002 /* Info.plist */ = {isa = PBXFileReference; path = Info.plist; sourceTree = "<group>"; };
                /* End PBXFileReference section */
                /* Begin PBXGroup section */
                		CC0000000000000000000001 /* iosApp */ = {
                			isa = PBXGroup;
                			children = (
                				BB0000000000000000000002 /* Info.plist */,
                			);
                			path = iosApp;
                			sourceTree = "<group>";
                		};
                /* End PBXGroup section */
                /* Begin PBXResourcesBuildPhase section */
                		DD0000000000000000000001 /* Resources */ = {
                			isa = PBXResourcesBuildPhase;
                			files = (
                			);
                		};
                /* End PBXResourcesBuildPhase section */
                """.trimIndent(),
            )

            StateStore().write(
                root.absolutePath,
                KmpfireState(
                    projectId = "demo",
                    sdk = "gitlive",
                    layoutKind = "new-template",
                    android = PlatformConfigs(
                        default = PlatformState(
                            appId = "1:1:android:a",
                            fileOutput = "androidApp/google-services.json",
                            packageNameOrBundleId = "com.example.app",
                        ),
                    ),
                    ios = PlatformConfigs(
                        default = PlatformState(
                            appId = "1:1:ios:b",
                            fileOutput = "iosApp/iosApp/GoogleService-Info.plist",
                            packageNameOrBundleId = "com.example.app.ios",
                        ),
                    ),
                ),
            )

            val service = ReconfigureService(firebaseCli = ReconfigureFakeFirebaseClient())
            val result = service.reconfigure(
                projectDir = root.absolutePath,
                deps = false,
                dryRun = false,
            )
            assertTrue(root.resolve("androidApp/google-services.json").exists())
            assertTrue(root.resolve("iosApp/iosApp/GoogleService-Info.plist").exists())
            assertEquals("config-for-ANDROID", root.resolve("androidApp/google-services.json").readText())
            assertTrue(result.wroteFiles.size >= 2)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingStateFails() {
        val root = Files.createTempDirectory("kmpfire-reconfig-missing").toFile()
        try {
            assertFailsWith<ConfigureException> {
                ReconfigureService(firebaseCli = ReconfigureFakeFirebaseClient()).reconfigure(
                    projectDir = root.absolutePath,
                    deps = false,
                    dryRun = true,
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

private class ReconfigureFakeFirebaseClient : FirebaseClient {
    override fun exists(): Boolean = true
    override fun version(): String = "14.0.0-fake"
    override fun projectsList(account: String?, token: String?, serviceAccount: String?): List<FirebaseProject> =
        emptyList()

    override fun projectsCreate(
        projectId: String,
        displayName: String?,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseProject = error("not used")

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
    ): FirebaseApp = error("not used")
    override fun appsCreateIos(
        project: String,
        displayName: String,
        bundleId: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseApp = error("not used")
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
