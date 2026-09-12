package io.github.kmpfire.project

import io.github.kmpfire.fs.Fs

class ProjectDetector(
    private val fs: Fs = Fs,
) {
    fun detect(projectDir: String): DetectedProject {
        val root = projectDir.trimEnd('/', '\\')
        if (!looksLikeGradleRoot(root)) {
            throw ProjectDetectionException(
                "Not a Gradle/KMP project root (missing settings.gradle(.kts)): $root. Use --project-dir.",
            )
        }

        val hasShared = fs.isDirectory(fs.join(root, "shared"))
        val hasAndroidApp = fs.isDirectory(fs.join(root, "androidApp"))
        val hasIosApp = fs.isDirectory(fs.join(root, "iosApp"))
        val hasComposeApp = fs.isDirectory(fs.join(root, "composeApp"))

        val appDir = fs.join(root, "app")
        val hasAppShared = fs.isDirectory(fs.join(appDir, "shared"))
        val hasAppAndroidApp = fs.isDirectory(fs.join(appDir, "androidApp"))
        val hasAppIosApp = fs.isDirectory(fs.join(appDir, "iosApp"))

        return when {
            hasShared && hasAndroidApp ->
                detectSplitTemplate(
                    root = root,
                    layoutKind = LayoutKind.NewTemplate,
                    moduleBase = root,
                    hasIosApp = hasIosApp,
                )
            hasAppShared && hasAppAndroidApp ->
                detectSplitTemplate(
                    root = root,
                    layoutKind = LayoutKind.AppNested,
                    moduleBase = appDir,
                    hasIosApp = hasAppIosApp,
                )
            hasComposeApp && !hasAndroidApp && !hasAppAndroidApp ->
                detectLegacyComposeApp(root, hasIosApp)
            else -> throw ProjectDetectionException(
                "Unsupported layout under $root. Expected:\n" +
                    "  - new template: shared + androidApp [+ iosApp]\n" +
                    "  - nested app/: app/shared + app/androidApp [+ app/iosApp]\n" +
                    "  - legacy: composeApp [+ iosApp]\n" +
                    "Override paths with --android-out / --ios-out.",
            )
        }
    }

    private fun looksLikeGradleRoot(root: String): Boolean =
        fs.exists(fs.join(root, "settings.gradle.kts")) ||
            fs.exists(fs.join(root, "settings.gradle"))

    private fun detectSplitTemplate(
        root: String,
        layoutKind: LayoutKind,
        moduleBase: String,
        hasIosApp: Boolean,
    ): DetectedProject {
        val androidDir = fs.join(moduleBase, "androidApp")
        val sharedDir = fs.join(moduleBase, "shared")
        val iosDir = if (hasIosApp) fs.join(moduleBase, "iosApp") else null
        val androidPackage = readAndroidPackage(androidDir)
        val iosBundleId = iosDir?.let { readIosBundleId(it) }
        val iosOut = iosDir?.let { defaultIosPlistPath(it) }
        return DetectedProject(
            root = root,
            layoutKind = layoutKind,
            androidModuleDir = androidDir,
            iosAppDir = iosDir,
            sharedModuleDir = sharedDir,
            androidPackageName = androidPackage,
            iosBundleId = iosBundleId,
            defaultAndroidOut = fs.join(androidDir, "google-services.json"),
            defaultIosOut = iosOut,
        )
    }

    private fun detectLegacyComposeApp(root: String, hasIosApp: Boolean): DetectedProject {
        val composeDir = fs.join(root, "composeApp")
        val iosDir = if (hasIosApp) fs.join(root, "iosApp") else null
        val androidPackage = readAndroidPackage(composeDir)
        val iosBundleId = iosDir?.let { readIosBundleId(it) }
        return DetectedProject(
            root = root,
            layoutKind = LayoutKind.LegacyComposeApp,
            androidModuleDir = composeDir,
            iosAppDir = iosDir,
            sharedModuleDir = composeDir,
            androidPackageName = androidPackage,
            iosBundleId = iosBundleId,
            defaultAndroidOut = fs.join(composeDir, "google-services.json"),
            defaultIosOut = iosDir?.let { defaultIosPlistPath(it) },
        )
    }

    private fun readAndroidPackage(androidModuleDir: String): String? {
        val candidates = listOf(
            fs.join(androidModuleDir, "build.gradle.kts"),
            fs.join(androidModuleDir, "build.gradle"),
        )
        for (path in candidates) {
            if (!fs.isFile(path)) continue
            GradleIdParser.parseAndroidPackage(fs.readText(path))?.let { return it }
        }
        return null
    }

    private fun readIosBundleId(iosAppDir: String): String? {
        val files = fs.walkFiles(iosAppDir)
        val pbx = files.firstOrNull { it.endsWith("project.pbxproj") }
        val xcconfigs = files
            .filter { it.endsWith(".xcconfig", ignoreCase = true) }
            .sorted()
        if (pbx == null && xcconfigs.isEmpty()) return null
        return PbxBundleIdParser.parseBundleId(
            pbxproj = pbx?.let { fs.readText(it) },
            xcconfigs = xcconfigs.map { fs.readText(it) },
        )
    }

    private fun defaultIosPlistPath(iosAppDir: String): String {
        val nested = fs.join(iosAppDir, "iosApp")
        val targetDir = if (fs.isDirectory(nested)) nested else iosAppDir
        return fs.join(targetDir, "GoogleService-Info.plist")
    }
}
