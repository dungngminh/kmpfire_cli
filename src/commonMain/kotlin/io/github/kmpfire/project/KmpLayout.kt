package io.github.kmpfire.project

enum class LayoutKind {
    NewTemplate,
    /** shared + androidApp [+ iosApp] under `app/` (e.g. loggoo monorepo). */
    AppNested,
    LegacyComposeApp,
    Custom,
}

data class DetectedProject(
    val root: String,
    val layoutKind: LayoutKind,
    val androidModuleDir: String?,
    val iosAppDir: String?,
    val sharedModuleDir: String?,
    val androidPackageName: String?,
    val iosBundleId: String?,
    val defaultAndroidOut: String?,
    val defaultIosOut: String?,
)

class ProjectDetectionException(message: String) : Exception(message)
