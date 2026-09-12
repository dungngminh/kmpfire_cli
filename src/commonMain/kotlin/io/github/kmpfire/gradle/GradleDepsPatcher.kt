package io.github.kmpfire.gradle

import io.github.kmpfire.configure.SdkMode
import io.github.kmpfire.fs.Fs
import io.github.kmpfire.project.DetectedProject

data class GradlePatchResult(
    val messages: List<String>,
    val changedFiles: List<String>,
)

/**
 * Idempotent, best-effort Gradle patches for Google Services + optional GitLive.
 * Soft-fails with warning messages — never aborts configure.
 */
class GradleDepsPatcher(
    private val fs: Fs = Fs,
) {
    fun patch(detected: DetectedProject, sdk: SdkMode): GradlePatchResult {
        val messages = mutableListOf<String>()
        val changed = mutableListOf<String>()
        val androidDir = detected.androidModuleDir
        if (androidDir == null) {
            messages += "warn: no android module; skipped Google Services plugin patch"
            return GradlePatchResult(messages, changed)
        }

        val googlePluginAlias = detectGoogleServicesAlias(detected.root)

        patchVersionCatalog(detected.root, sdk, googlePluginAlias, messages, changed)
        patchRootBuild(detected.root, googlePluginAlias, messages, changed)
        patchAndroidAppBuild(androidDir, googlePluginAlias, messages, changed)

        if (sdk == SdkMode.GitLive) {
            val sharedDir = detected.sharedModuleDir
            if (sharedDir != null) {
                patchSharedGitLive(sharedDir, messages, changed)
            } else {
                messages += "warn: no shared module; skipped GitLive deps"
            }
        } else {
            messages += "sdk=native: skipped GitLive deps"
        }
        return GradlePatchResult(messages, changed)
    }

    private fun detectGoogleServicesAlias(root: String): String {
        val catalog = fs.join(root, "gradle", "libs.versions.toml")
        if (!fs.isFile(catalog)) return "google.services"
        val text = fs.readText(catalog)
        return when {
            Regex("""(?m)^googleService\s*=""").containsMatchIn(text) ||
                text.contains("googleService = {") -> "googleService"
            Regex("""(?m)^google-services\s*=""").containsMatchIn(text) ||
                text.contains("google-services = {") -> "google.services"
            else -> "google.services"
        }
    }

    private fun patchVersionCatalog(
        root: String,
        sdk: SdkMode,
        googlePluginAlias: String,
        messages: MutableList<String>,
        changed: MutableList<String>,
    ) {
        val path = fs.join(root, "gradle", "libs.versions.toml")
        if (!fs.isFile(path)) {
            messages += "warn: missing $path; skipped catalog patch"
            return
        }
        var text = fs.readText(path)
        val before = text
        val hasGoogle = text.contains("google-services") ||
            text.contains("googleService") ||
            text.contains("com.google.gms.google-services")
        if (!hasGoogle) {
            text = ensureVersionsBlock(text, """google-services = "4.4.2"""")
            text = ensurePluginsBlock(
                text,
                """google-services = { id = "com.google.gms.google-services", version.ref = "google-services" }""",
            )
        }
        if (sdk == SdkMode.GitLive &&
            !text.contains("dev.gitlive:firebase-app") &&
            !text.contains("gitlive-firebase")
        ) {
            text = ensureVersionsBlock(text, """gitlive-firebase = "2.1.0"""")
            text = ensureLibrariesBlock(
                text,
                """gitlive-firebase-app = { module = "dev.gitlive:firebase-app", version.ref = "gitlive-firebase" }""",
            )
        }
        if (text != before) {
            fs.writeText(path, text)
            changed += path
            messages += "Updated $path"
        } else {
            messages += "Catalog OK for Google Services (alias=$googlePluginAlias)"
        }
    }

    private fun patchRootBuild(
        root: String,
        googlePluginAlias: String,
        messages: MutableList<String>,
        changed: MutableList<String>,
    ) {
        val path = fs.join(root, "build.gradle.kts")
        if (!fs.isFile(path)) {
            messages += "warn: missing $path"
            return
        }
        val text = fs.readText(path)
        if (text.contains("google-services") || text.contains("googleService") || text.contains("google.services")) {
            messages += "Root build already references google-services"
            return
        }
        val pluginsBlock = Regex("""plugins\s*\{""").find(text) ?: run {
            messages += "warn: no plugins {} in $path"
            return
        }
        val line = if (text.contains("alias(libs.plugins.")) {
            """    alias(libs.plugins.$googlePluginAlias) apply false"""
        } else {
            """    id("com.google.gms.google-services") version "4.4.2" apply false"""
        }
        val insertAt = pluginsBlock.range.last + 1
        val updated = text.substring(0, insertAt) + "\n$line" + text.substring(insertAt)
        fs.writeText(path, updated)
        changed += path
        messages += "Updated $path"
    }

    private fun patchAndroidAppBuild(
        androidDir: String,
        googlePluginAlias: String,
        messages: MutableList<String>,
        changed: MutableList<String>,
    ) {
        val path = fs.join(androidDir, "build.gradle.kts")
        if (!fs.isFile(path)) {
            messages += "warn: missing $path"
            return
        }
        val text = fs.readText(path)
        if (text.contains("google-services") || text.contains("googleService") || text.contains("google.services")) {
            messages += "androidApp already applies google-services"
            return
        }
        val pluginsBlock = Regex("""plugins\s*\{""").find(text) ?: run {
            messages += "warn: no plugins {} in $path"
            return
        }
        val line = if (text.contains("alias(libs.plugins.")) {
            """    alias(libs.plugins.$googlePluginAlias)"""
        } else {
            """    id("com.google.gms.google-services")"""
        }
        val insertAt = pluginsBlock.range.last + 1
        val updated = text.substring(0, insertAt) + "\n$line" + text.substring(insertAt)
        fs.writeText(path, updated)
        changed += path
        messages += "Updated $path"
    }

    private fun patchSharedGitLive(
        sharedDir: String,
        messages: MutableList<String>,
        changed: MutableList<String>,
    ) {
        val path = fs.join(sharedDir, "build.gradle.kts")
        if (!fs.isFile(path)) {
            messages += "warn: missing $path; skipped GitLive"
            return
        }
        val text = fs.readText(path)
        if (text.contains("gitlive") || text.contains("dev.gitlive")) {
            messages += "shared already has GitLive deps"
            return
        }
        val match = Regex("""commonMain\.dependencies\s*\{""").find(text) ?: run {
            messages += "warn: no commonMain.dependencies {} in $path"
            return
        }
        val depLine = """            implementation("dev.gitlive:firebase-app:2.1.0")"""
        val insertAt = match.range.last + 1
        val updated = text.substring(0, insertAt) + "\n$depLine" + text.substring(insertAt)
        fs.writeText(path, updated)
        changed += path
        messages += "Added GitLive firebase-app to $path"
    }

    private fun ensureVersionsBlock(text: String, line: String): String {
        val key = line.substringBefore("=").trim()
        if (Regex("""(?m)^${Regex.escape(key)}\s*=""").containsMatchIn(text)) return text
        val match = Regex("""\[versions\]""").find(text) ?: return text + "\n[versions]\n$line\n"
        val insertAt = match.range.last + 1
        return text.substring(0, insertAt) + "\n$line" + text.substring(insertAt)
    }

    private fun ensurePluginsBlock(text: String, line: String): String {
        val key = line.substringBefore("=").trim()
        if (Regex("""(?m)^${Regex.escape(key)}\s*=""").containsMatchIn(text)) return text
        val match = Regex("""\[plugins\]""").find(text) ?: return text + "\n[plugins]\n$line\n"
        val insertAt = match.range.last + 1
        return text.substring(0, insertAt) + "\n$line" + text.substring(insertAt)
    }

    private fun ensureLibrariesBlock(text: String, line: String): String {
        val key = line.substringBefore("=").trim()
        if (Regex("""(?m)^${Regex.escape(key)}\s*=""").containsMatchIn(text)) return text
        val match = Regex("""\[libraries\]""").find(text) ?: return text + "\n[libraries]\n$line\n"
        val insertAt = match.range.last + 1
        return text.substring(0, insertAt) + "\n$line" + text.substring(insertAt)
    }
}
