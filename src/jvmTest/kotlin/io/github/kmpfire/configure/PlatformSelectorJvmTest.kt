package io.github.kmpfire.configure

import io.github.kmpfire.cli.LineUserPrompt
import io.github.kmpfire.cli.RejectPrompt
import io.github.kmpfire.project.DetectedProject
import io.github.kmpfire.project.LayoutKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformSelectorJvmTest {
    private val detected = DetectedProject(
        root = "/tmp/demo",
        layoutKind = LayoutKind.NewTemplate,
        androidModuleDir = "/tmp/demo/androidApp",
        iosAppDir = "/tmp/demo/iosApp",
        sharedModuleDir = "/tmp/demo/shared",
        androidPackageName = "com.example",
        iosBundleId = "com.example.ios",
        defaultAndroidOut = "/tmp/demo/androidApp/google-services.json",
        defaultIosOut = "/tmp/demo/iosApp/iosApp/GoogleService-Info.plist",
    )

    @Test
    fun yesUsesDetectedPlatforms() {
        val messages = mutableListOf<String>()
        val selected = PlatformSelector.resolve(
            requested = null,
            detected = detected,
            yes = true,
            prompt = RejectPrompt,
            messages = messages,
        )
        assertEquals(listOf(TargetPlatform.Android, TargetPlatform.Ios), selected)
        assertTrue(messages.any { it.contains("Selected platforms: android,ios") })
    }

    @Test
    fun multiSelectCanDropIos() {
        val inputs = ArrayDeque(listOf("2", "")) // uncheck ios, confirm
        val prompt = LineUserPrompt(
            println = {},
            readLine = { inputs.removeFirstOrNull() },
        )
        val messages = mutableListOf<String>()
        val selected = PlatformSelector.resolve(
            requested = null,
            detected = detected,
            yes = false,
            prompt = prompt,
            messages = messages,
        )
        assertEquals(listOf(TargetPlatform.Android), selected)
        assertTrue(messages.any { it.contains("Selected platforms: android") })
    }

    @Test
    fun explicitFlagSkipsPrompt() {
        val messages = mutableListOf<String>()
        val selected = PlatformSelector.resolve(
            requested = listOf(TargetPlatform.Ios),
            detected = detected,
            yes = false,
            prompt = RejectPrompt,
            messages = messages,
        )
        assertEquals(listOf(TargetPlatform.Ios), selected)
    }
}
