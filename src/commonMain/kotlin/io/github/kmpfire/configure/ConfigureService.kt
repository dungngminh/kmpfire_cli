package io.github.kmpfire.configure

import io.github.kmpfire.cli.CliProgress
import io.github.kmpfire.cli.NoopCliProgress
import io.github.kmpfire.cli.RejectPrompt
import io.github.kmpfire.cli.UserPrompt
import io.github.kmpfire.config.ConfigWriter
import io.github.kmpfire.config.KmpfireState
import io.github.kmpfire.config.PlatformConfigs
import io.github.kmpfire.config.PlatformState
import io.github.kmpfire.config.StateStore
import io.github.kmpfire.firebase.FirebaseAppProvisioner
import io.github.kmpfire.firebase.FirebaseCli
import io.github.kmpfire.firebase.FirebaseCliException
import io.github.kmpfire.firebase.FirebaseClient
import io.github.kmpfire.firebase.FirebaseProjectResolver
import io.github.kmpfire.fs.Fs
import io.github.kmpfire.fs.Paths
import io.github.kmpfire.gradle.GradleDepsPatcher
import io.github.kmpfire.project.DetectedProject
import io.github.kmpfire.project.ProjectDetectionException
import io.github.kmpfire.project.ProjectDetector
import io.github.kmpfire.xcode.XcodePbxPatcher

class ConfigureException(message: String) : Exception(message)

class ConfigureService(
    private val firebaseCli: FirebaseClient = FirebaseCli(),
    private val provisioner: FirebaseAppProvisioner = FirebaseAppProvisioner(firebaseCli),
    private val projectDetector: ProjectDetector = ProjectDetector(),
    private val configWriter: ConfigWriter = ConfigWriter(),
    private val stateStore: StateStore = StateStore(),
    private val pbxPatcher: XcodePbxPatcher = XcodePbxPatcher(),
    private val depsPatcher: GradleDepsPatcher = GradleDepsPatcher(),
    private val prompt: UserPrompt = RejectPrompt,
    private val progress: CliProgress = NoopCliProgress,
    private val fs: Fs = Fs,
    private val log: (String) -> Unit = {},
) {
    private val projectResolver: FirebaseProjectResolver
        get() = FirebaseProjectResolver(firebaseCli, prompt, progress)

    fun configure(options: ConfigureOptions): ConfigureResult {
        val messages = mutableListOf<String>()
        val wrote = mutableListOf<String>()

        if (!firebaseCli.exists()) {
            throw ConfigureException(
                "Firebase CLI not found. Install firebase-tools: https://firebase.google.com/docs/cli",
            )
        }
        val version = progress.spin(
            loading = "Checking Firebase CLI",
            done = { "Firebase CLI $it" },
        ) { firebaseCli.version() }
        messages += "Firebase CLI: $version"

        val detected = progress.spin(
            loading = "Detecting KMP project",
            done = { "Detected ${KmpfireState.layoutKindWire(it.layoutKind)} at ${it.root}" },
        ) {
            try {
                projectDetector.detect(options.projectDir)
            } catch (e: ProjectDetectionException) {
                throw ConfigureException(e.message ?: "Project detection failed")
            }
        }

        val existing = stateStore.read(detected.root)
        val reuseExisting = decideReuseExisting(detected.root, existing, options, messages)

        val effective = if (reuseExisting && existing != null) {
            applyReuseDefaults(options, existing)
        } else {
            options
        }

        val platforms = PlatformSelector.resolve(
            requested = effective.platforms,
            detected = detected,
            yes = effective.yes,
            prompt = prompt,
            messages = messages,
        )
        if (effective.yes && effective.firebaseProjectId.isNullOrBlank()) {
            throw ConfigureException("Pass --project=<FIREBASE_PROJECT_ID> with --yes")
        }

        val firebaseProjectId = projectResolver.resolve(
            requestedProjectId = effective.firebaseProjectId,
            createIfMissing = effective.createProject,
            dryRun = effective.dryRun,
            auth = FirebaseProjectResolver.Auth(
                account = effective.account,
                token = effective.token,
                serviceAccount = effective.serviceAccount,
            ),
            messages = messages,
        )

        val flavor = effective.flavor?.trim()?.takeIf { it.isNotEmpty() }
        var androidConfigs = existing?.android ?: PlatformConfigs()
        var iosConfigs = existing?.ios ?: PlatformConfigs()

        if (TargetPlatform.Android in platforms) {
            val prior = androidConfigs.entry(flavor)
            val packageName = effective.androidPackageName
                ?: prior?.packageNameOrBundleId
                ?: detected.androidPackageName
                ?: throw ConfigureException(
                    "Android package name not found. Pass --android-package-name=",
                )
            val out = effective.androidOut
                ?: prior?.fileOutput
                ?: detected.defaultAndroidOut
                ?: throw ConfigureException("Android output path unknown. Pass --android-out=")
            val result = configureAndroid(
                detected = detected,
                projectId = firebaseProjectId,
                packageName = packageName,
                out = out,
                options = effective,
                messages = messages,
                wrote = wrote,
                flavorLabel = flavor,
            )
            androidConfigs = androidConfigs.withEntry(flavor, result)
        }

        if (TargetPlatform.Ios in platforms) {
            val prior = iosConfigs.entry(flavor)
            val bundleId = effective.iosBundleId
                ?: prior?.packageNameOrBundleId
                ?: detected.iosBundleId
                ?: throw ConfigureException("iOS bundle id not found. Pass --ios-bundle-id=")
            val out = effective.iosOut
                ?: prior?.fileOutput
                ?: detected.defaultIosOut
                ?: throw ConfigureException("iOS output path unknown. Pass --ios-out=")
            val result = configureIos(
                detected = detected,
                projectId = firebaseProjectId,
                bundleId = bundleId,
                out = out,
                options = effective,
                messages = messages,
                wrote = wrote,
                flavorLabel = flavor,
            )
            iosConfigs = iosConfigs.withEntry(flavor, result)
        }

        val state = KmpfireState(
            projectId = firebaseProjectId,
            sdk = SdkMode.toWire(effective.sdk),
            layoutKind = KmpfireState.layoutKindWire(detected.layoutKind),
            android = androidConfigs.takeUnless { it.isEmpty() },
            ios = iosConfigs.takeUnless { it.isEmpty() },
        )

        if (effective.dryRun) {
            messages += "dry-run: would write ${stateStore.pathFor(detected.root)} (kotlinMultiplatform)"
            if (!progress.printsLive) progress.info(messages.last())
        } else {
            progress.spin(
                loading = "Writing ${KmpfireState.FILE_NAME} (kotlinMultiplatform)",
                done = { "Wrote ${KmpfireState.FILE_NAME} (kotlinMultiplatform)" },
            ) {
                stateStore.write(detected.root, state)
            }
            wrote += stateStore.pathFor(detected.root)
            messages += "Wrote ${KmpfireState.FILE_NAME} (kotlinMultiplatform)"
        }

        if (effective.deps) {
            if (effective.dryRun) {
                messages += "dry-run: would patch Gradle deps (sdk=${SdkMode.toWire(effective.sdk)})"
                if (!progress.printsLive) progress.info(messages.last())
            } else {
                val depsResult = progress.spin(
                    loading = "Patching Gradle deps (sdk=${SdkMode.toWire(effective.sdk)})",
                    done = { r ->
                        val n = r.changedFiles.size
                        if (n == 0) "Gradle deps already up to date"
                        else "Patched Gradle deps ($n file${if (n == 1) "" else "s"})"
                    },
                ) {
                    depsPatcher.patch(detected, effective.sdk)
                }
                messages += depsResult.messages
                wrote += depsResult.changedFiles
            }
        }

        messages += "Next: Android auto-inits via google-services plugin; on iOS call FirebaseApp.configure() if needed."
        if (!progress.printsLive) messages.forEach(log)
        return ConfigureResult(messages = messages, wroteFiles = wrote)
    }

    /**
     * FlutterFire-style: if `firebase.json` already has a `kotlinMultiplatform` section, offer to reuse values.
     */
    private fun decideReuseExisting(
        projectRoot: String,
        existing: KmpfireState?,
        options: ConfigureOptions,
        messages: MutableList<String>,
    ): Boolean {
        if (existing == null || !existing.hasPlatforms()) return false
        if (options.yes) {
            messages += "i Reusing existing firebase.json kotlinMultiplatform configuration (--yes)"
            return true
        }
        val reuse = prompt.confirm(
            "You have an existing `firebase.json` file with a `kotlinMultiplatform` section " +
                "(and possibly already configured this project for Firebase). " +
                "Would you prefer to reuse the values in your existing `firebase.json` " +
                "file to configure your project?",
            defaultYes = true,
        )
        if (reuse) {
            messages += "i Reusing existing firebase.json kotlinMultiplatform configuration"
            progress.info("Reusing existing firebase.json kotlinMultiplatform configuration")
        } else {
            messages += "i Configuring from scratch (not reusing firebase.json kotlinMultiplatform values)"
        }
        return reuse
    }

    private fun applyReuseDefaults(options: ConfigureOptions, existing: KmpfireState): ConfigureOptions {
        val flavor = options.flavor?.trim()?.takeIf { it.isNotEmpty() }
        val androidPrior = existing.android?.entry(flavor)
        val iosPrior = existing.ios?.entry(flavor)
        val platformsFromState = buildList {
            if (existing.android != null && !existing.android.isEmpty()) add(TargetPlatform.Android)
            if (existing.ios != null && !existing.ios.isEmpty()) add(TargetPlatform.Ios)
        }.ifEmpty { null }
        return options.copy(
            firebaseProjectId = options.firebaseProjectId ?: existing.projectId,
            platforms = options.platforms ?: platformsFromState,
            androidPackageName = options.androidPackageName ?: androidPrior?.packageNameOrBundleId,
            iosBundleId = options.iosBundleId ?: iosPrior?.packageNameOrBundleId,
            androidOut = options.androidOut ?: androidPrior?.fileOutput,
            iosOut = options.iosOut ?: iosPrior?.fileOutput,
            sdk = runCatching { SdkMode.parse(existing.sdk) }.getOrDefault(options.sdk),
        )
    }

    private fun configureAndroid(
        detected: DetectedProject,
        projectId: String,
        packageName: String,
        out: String,
        options: ConfigureOptions,
        messages: MutableList<String>,
        wrote: MutableList<String>,
        flavorLabel: String?,
    ): PlatformState {
        val label = flavorLabel?.let { " [$it]" }.orEmpty()
        val (app, created) = progress.spin(
            loading = "Android$label: resolve app ($packageName)",
            done = { (resolved, wasCreated) ->
                if (wasCreated) "Created Android app ${resolved.appId} ($packageName)$label"
                else "Reused Android app ${resolved.appId} ($packageName)$label"
            },
        ) {
            provisioner.findOrCreateAndroid(
                projectId = projectId,
                displayName = displayName(detected, flavorLabel),
                packageName = packageName,
                dryRun = options.dryRun,
                auth = FirebaseAppProvisioner.Auth(
                    account = options.account,
                    token = options.token,
                    serviceAccount = options.serviceAccount,
                ),
            )
        }
        messages += if (created) {
            "Created Android app ${app.appId} ($packageName)$label"
        } else {
            "Reused Android app ${app.appId} ($packageName)$label"
        }
        val absoluteOut = resolveOut(detected.root, out)
        if (options.dryRun) {
            messages += "dry-run: would fetch sdkconfig ANDROID and write $absoluteOut"
            if (!progress.printsLive) progress.info(messages.last())
        } else {
            progress.spin(
                loading = "Android$label: download sdkconfig",
                done = { "Wrote ${Paths.relativize(detected.root, absoluteOut)}" },
            ) {
                val config = try {
                    firebaseCli.appsSdkConfig(
                        platform = "ANDROID",
                        appId = app.appId,
                        project = projectId,
                        account = options.account,
                        token = options.token,
                        serviceAccount = options.serviceAccount,
                    )
                } catch (e: FirebaseCliException) {
                    throw ConfigureException(
                        FirebaseAppProvisioner.formatFirebaseError("apps:sdkconfig ANDROID", e),
                    )
                }
                configWriter.writeServiceFile(absoluteOut, config.fileContents)
            }
            wrote += absoluteOut
            messages += "Wrote ${Paths.relativize(detected.root, absoluteOut)}"
        }
        return PlatformState(
            appId = app.appId,
            fileOutput = Paths.relativize(detected.root, absoluteOut),
            packageNameOrBundleId = packageName,
        )
    }

    private fun configureIos(
        detected: DetectedProject,
        projectId: String,
        bundleId: String,
        out: String,
        options: ConfigureOptions,
        messages: MutableList<String>,
        wrote: MutableList<String>,
        flavorLabel: String?,
    ): PlatformState {
        val label = flavorLabel?.let { " [$it]" }.orEmpty()
        val (app, created) = progress.spin(
            loading = "iOS$label: resolve app ($bundleId)",
            done = { (resolved, wasCreated) ->
                if (wasCreated) "Created iOS app ${resolved.appId} ($bundleId)$label"
                else "Reused iOS app ${resolved.appId} ($bundleId)$label"
            },
        ) {
            provisioner.findOrCreateIos(
                projectId = projectId,
                displayName = displayName(detected, flavorLabel),
                bundleId = bundleId,
                dryRun = options.dryRun,
                auth = FirebaseAppProvisioner.Auth(
                    account = options.account,
                    token = options.token,
                    serviceAccount = options.serviceAccount,
                ),
            )
        }
        messages += if (created) {
            "Created iOS app ${app.appId} ($bundleId)$label"
        } else {
            "Reused iOS app ${app.appId} ($bundleId)$label"
        }
        val absoluteOut = resolveOut(detected.root, out)
        if (options.dryRun) {
            messages += "dry-run: would fetch sdkconfig IOS and write $absoluteOut"
            if (!progress.printsLive) progress.info(messages.last())
        } else {
            progress.spin(
                loading = "iOS$label: download sdkconfig",
                done = { "Wrote ${Paths.relativize(detected.root, absoluteOut)}" },
            ) {
                val config = try {
                    firebaseCli.appsSdkConfig(
                        platform = "IOS",
                        appId = app.appId,
                        project = projectId,
                        account = options.account,
                        token = options.token,
                        serviceAccount = options.serviceAccount,
                    )
                } catch (e: FirebaseCliException) {
                    throw ConfigureException(
                        FirebaseAppProvisioner.formatFirebaseError("apps:sdkconfig IOS", e),
                    )
                }
                configWriter.writeServiceFile(absoluteOut, config.fileContents)
            }
            wrote += absoluteOut
            messages += "Wrote ${Paths.relativize(detected.root, absoluteOut)}"
            val iosDir = detected.iosAppDir
            if (iosDir != null) {
                val patch = pbxPatcher.ensurePlistInResources(iosDir, absoluteOut)
                messages += patch.message
                if (patch.changed) progress.ok(patch.message)
            } else {
                val warn = "No iosApp dir detected; add GoogleService-Info.plist to the Xcode target manually if needed."
                messages += warn
                progress.warn(warn)
            }
        }
        return PlatformState(
            appId = app.appId,
            fileOutput = Paths.relativize(detected.root, absoluteOut),
            packageNameOrBundleId = bundleId,
        )
    }

    private fun displayName(detected: DetectedProject, flavor: String?): String {
        val name = detected.root.trimEnd('/', '\\')
            .replace('\\', '/')
            .substringAfterLast('/')
        val base = name.ifBlank { "kmpfire" }
        return if (flavor.isNullOrBlank()) base else "$base-$flavor"
    }

    private fun resolveOut(root: String, out: String): String = Paths.resolve(root, out)
}
