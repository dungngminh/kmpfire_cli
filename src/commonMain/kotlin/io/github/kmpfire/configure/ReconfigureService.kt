package io.github.kmpfire.configure

import io.github.kmpfire.cli.CliProgress
import io.github.kmpfire.cli.NoopCliProgress
import io.github.kmpfire.config.ConfigWriter
import io.github.kmpfire.config.PlatformState
import io.github.kmpfire.config.StateStore
import io.github.kmpfire.firebase.FirebaseCli
import io.github.kmpfire.firebase.FirebaseCliException
import io.github.kmpfire.firebase.FirebaseClient
import io.github.kmpfire.fs.Fs
import io.github.kmpfire.fs.Paths
import io.github.kmpfire.gradle.GradleDepsPatcher
import io.github.kmpfire.project.ProjectDetector
import io.github.kmpfire.xcode.XcodePbxPatcher

class ReconfigureService(
    private val firebaseCli: FirebaseClient = FirebaseCli(),
    private val stateStore: StateStore = StateStore(),
    private val configWriter: ConfigWriter = ConfigWriter(),
    private val projectDetector: ProjectDetector = ProjectDetector(),
    private val pbxPatcher: XcodePbxPatcher = XcodePbxPatcher(),
    private val depsPatcher: GradleDepsPatcher = GradleDepsPatcher(),
    private val progress: CliProgress = NoopCliProgress,
    private val fs: Fs = Fs,
) {
    fun reconfigure(
        projectDir: String,
        deps: Boolean,
        dryRun: Boolean,
        sdkOverride: SdkMode? = null,
        /** null = all flavors; otherwise only that buildConfiguration (or `default`). */
        flavor: String? = null,
    ): ConfigureResult {
        val messages = mutableListOf<String>()
        val wrote = mutableListOf<String>()

        if (!firebaseCli.exists()) {
            throw ConfigureException(
                "Firebase CLI not found. Install firebase-tools: https://firebase.google.com/docs/cli",
            )
        }

        val state = stateStore.read(projectDir)
            ?: throw ConfigureException(
                "No `kotlinMultiplatform` section in firebase.json (and no legacy kmpfire.json). Run kmpfire configure first.",
            )

        if (!state.hasPlatforms()) {
            throw ConfigureException("firebase.json `kotlinMultiplatform` section has no android/ios platforms")
        }

        val version = progress.spin(
            loading = "Checking Firebase CLI",
            done = { "Firebase CLI $it" },
        ) { firebaseCli.version() }
        messages += "Firebase CLI: $version"
        messages += "Reconfiguring project ${state.projectId}"
        progress.ok("Reconfiguring project ${state.projectId}")

        val flavorFilter = flavor?.trim()?.takeIf { it.isNotEmpty() }

        state.android?.let { android ->
            for ((name, entry) in selectEntries(android.allEntries(), flavorFilter)) {
                rewriteAndroid(projectDir, state.projectId, name, entry, dryRun, messages, wrote)
            }
        }

        state.ios?.let { ios ->
            for ((name, entry) in selectEntries(ios.allEntries(), flavorFilter)) {
                rewriteIos(projectDir, state.projectId, name, entry, dryRun, messages, wrote)
            }
        }

        if (deps) {
            val detected = runCatching { projectDetector.detect(projectDir) }.getOrNull()
            if (detected == null) {
                messages += "warn: could not detect project layout for --deps"
                progress.warn("could not detect project layout for --deps")
            } else {
                val sdk = sdkOverride ?: SdkMode.parse(state.sdk)
                if (dryRun) {
                    messages += "dry-run: would patch Gradle deps (sdk=${SdkMode.toWire(sdk)})"
                    if (!progress.printsLive) progress.info(messages.last())
                } else {
                    val result = progress.spin(
                        loading = "Patching Gradle deps",
                        done = { r ->
                            val n = r.changedFiles.size
                            if (n == 0) "Gradle deps already up to date"
                            else "Patched Gradle deps ($n file${if (n == 1) "" else "s"})"
                        },
                    ) {
                        depsPatcher.patch(detected, sdk)
                    }
                    messages += result.messages
                    wrote += result.changedFiles
                }
            }
        }

        return ConfigureResult(messages = messages, wroteFiles = wrote)
    }

    private fun selectEntries(
        entries: List<Pair<String, PlatformState>>,
        flavorFilter: String?,
    ): List<Pair<String, PlatformState>> {
        if (flavorFilter == null) return entries
        val match = entries.filter { (name, _) -> name == flavorFilter }
        if (match.isEmpty()) {
            throw ConfigureException(
                "No platform entry named `$flavorFilter` in firebase.json kotlinMultiplatform section. " +
                    "Known: ${entries.joinToString { it.first }}",
            )
        }
        return match
    }

    private fun rewriteAndroid(
        projectDir: String,
        rootProjectId: String,
        name: String,
        android: PlatformState,
        dryRun: Boolean,
        messages: MutableList<String>,
        wrote: MutableList<String>,
    ) {
        val label = if (name == "default") "" else " [$name]"
        val projectId = android.projectId ?: rootProjectId
        val out = resolveOut(projectDir, android.fileOutput)
        if (dryRun) {
            messages += "dry-run: would rewrite ANDROID$label sdkconfig → $out"
            if (!progress.printsLive) progress.info(messages.last())
            return
        }
        progress.spin(
            loading = "Android$label: download sdkconfig",
            done = { "Wrote $out" },
        ) {
            val config = try {
                firebaseCli.appsSdkConfig(
                    platform = "ANDROID",
                    appId = android.appId,
                    project = projectId,
                )
            } catch (e: FirebaseCliException) {
                throw ConfigureException(e.message ?: "apps:sdkconfig ANDROID failed")
            }
            configWriter.writeServiceFile(out, config.fileContents)
        }
        wrote += out
        messages += "Wrote $out"
    }

    private fun rewriteIos(
        projectDir: String,
        rootProjectId: String,
        name: String,
        ios: PlatformState,
        dryRun: Boolean,
        messages: MutableList<String>,
        wrote: MutableList<String>,
    ) {
        val label = if (name == "default") "" else " [$name]"
        val projectId = ios.projectId ?: rootProjectId
        val out = resolveOut(projectDir, ios.fileOutput)
        if (dryRun) {
            messages += "dry-run: would rewrite IOS$label sdkconfig → $out"
            if (!progress.printsLive) progress.info(messages.last())
            return
        }
        progress.spin(
            loading = "iOS$label: download sdkconfig",
            done = { "Wrote $out" },
        ) {
            val config = try {
                firebaseCli.appsSdkConfig(
                    platform = "IOS",
                    appId = ios.appId,
                    project = projectId,
                )
            } catch (e: FirebaseCliException) {
                throw ConfigureException(e.message ?: "apps:sdkconfig IOS failed")
            }
            configWriter.writeServiceFile(out, config.fileContents)
        }
        wrote += out
        messages += "Wrote $out"

        val detected = runCatching { projectDetector.detect(projectDir) }.getOrNull()
        val iosDir = detected?.iosAppDir
        if (iosDir != null) {
            val patch = pbxPatcher.ensurePlistInResources(iosDir, out)
            messages += patch.message
            if (patch.changed) progress.ok(patch.message)
        }
    }

    private fun resolveOut(root: String, out: String): String = Paths.resolve(root, out)
}
