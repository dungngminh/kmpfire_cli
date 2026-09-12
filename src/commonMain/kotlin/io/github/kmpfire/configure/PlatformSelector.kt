package io.github.kmpfire.configure

import io.github.kmpfire.cli.UserPrompt
import io.github.kmpfire.project.DetectedProject

/**
 * Resolve which platforms to configure — flutterfire-style:
 * `--platforms` / `--yes` skip prompt; otherwise multi-select with detected defaults.
 */
object PlatformSelector {
    fun available(detected: DetectedProject): List<TargetPlatform> = buildList {
        if (detected.androidModuleDir != null) add(TargetPlatform.Android)
        if (detected.iosAppDir != null) add(TargetPlatform.Ios)
    }

    fun resolve(
        requested: List<TargetPlatform>?,
        detected: DetectedProject,
        yes: Boolean,
        prompt: UserPrompt,
        messages: MutableList<String>,
    ): List<TargetPlatform> {
        val available = available(detected)
        if (available.isEmpty()) {
            throw ConfigureException(
                "No Android/iOS modules detected. Pass --platforms=android,ios and " +
                    "--android-package-name / --ios-bundle-id (and out paths) as needed.",
            )
        }

        if (requested != null && requested.isNotEmpty()) {
            val selected = requested.distinct()
            messages += "i Selected platforms: ${selected.joinToString(",") { it.wireName() }}"
            return selected
        }

        if (yes) {
            messages += "i Selected platforms: ${available.joinToString(",") { it.wireName() }}"
            return available
        }

        val labels = available.map { it.wireName() }
        val defaults = available.map { true }
        val indices = prompt.multiSelect(
            message = "Which platforms should your configuration support?",
            choices = labels,
            defaultSelected = defaults,
        )
        if (indices.isEmpty()) {
            throw ConfigureException("Select at least one platform (android and/or ios).")
        }
        val selected = indices.map { available[it] }.distinct()
        messages += "i Selected platforms: ${selected.joinToString(",") { it.wireName() }}"
        return selected
    }
}

fun TargetPlatform.wireName(): String = when (this) {
    TargetPlatform.Android -> "android"
    TargetPlatform.Ios -> "ios"
}
