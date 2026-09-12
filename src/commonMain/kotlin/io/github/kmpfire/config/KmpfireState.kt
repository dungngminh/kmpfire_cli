package io.github.kmpfire.config

import kotlinx.serialization.Serializable
import io.github.kmpfire.project.LayoutKind

/** One Firebase app registration (default or a named flavor / build configuration). */
@Serializable
data class PlatformState(
    val appId: String,
    val fileOutput: String,
    val packageNameOrBundleId: String? = null,
    /** Optional per-flavor Firebase project override; null → use section [KmpfireState.projectId]. */
    val projectId: String? = null,
)

/**
 * Android or iOS configs: FlutterFire-style `default` + `buildConfigurations` (flavors).
 */
@Serializable
data class PlatformConfigs(
    val default: PlatformState? = null,
    val buildConfigurations: Map<String, PlatformState> = emptyMap(),
) {
    fun isEmpty(): Boolean = default == null && buildConfigurations.isEmpty()

    fun entry(flavor: String?): PlatformState? =
        if (flavor.isNullOrBlank()) default else buildConfigurations[flavor]

    fun allEntries(): List<Pair<String, PlatformState>> = buildList {
        default?.let { add("default" to it) }
        buildConfigurations.forEach { (name, state) -> add(name to state) }
    }

    fun withEntry(flavor: String?, state: PlatformState): PlatformConfigs =
        if (flavor.isNullOrBlank()) {
            copy(default = state)
        } else {
            copy(buildConfigurations = buildConfigurations + (flavor to state))
        }
}

/**
 * In-memory configure/reconfigure state.
 * On disk: `firebase.json` → `"kotlinMultiplatform"` (parallel to FlutterFire `"flutter"`).
 */
@Serializable
data class KmpfireState(
    val projectId: String,
    val sdk: String = "gitlive",
    val layoutKind: String,
    val android: PlatformConfigs? = null,
    val ios: PlatformConfigs? = null,
) {
    companion object {
        const val FILE_NAME = "firebase.json"
        /** Top-level key in [FILE_NAME]. */
        const val ROOT_KEY = "kotlinMultiplatform"
        /** Previous nested keys; still read for migration. */
        val LEGACY_ROOT_KEYS: Set<String> = setOf("kmp", "kmpfire")
        /** Legacy standalone file from early builds. */
        const val LEGACY_FILE_NAME = "kmpfire.json"

        fun layoutKindWire(kind: LayoutKind): String = when (kind) {
            LayoutKind.NewTemplate -> "new-template"
            LayoutKind.AppNested -> "app-nested"
            LayoutKind.LegacyComposeApp -> "legacy-composeApp"
            LayoutKind.Custom -> "custom"
        }
    }

    fun hasPlatforms(): Boolean =
        (android != null && !android.isEmpty()) || (ios != null && !ios.isEmpty())
}

/** Wire shape under `firebase.json` → `kotlinMultiplatform`. */
@Serializable
data class KmpFirebaseSection(
    val projectId: String,
    val sdk: String = "gitlive",
    val layoutKind: String,
    val platforms: KmpPlatforms,
)

@Serializable
data class KmpPlatforms(
    val android: PlatformConfigs? = null,
    val ios: PlatformConfigs? = null,
)

fun KmpfireState.toFirebaseSection(): KmpFirebaseSection = KmpFirebaseSection(
    projectId = projectId,
    sdk = sdk,
    layoutKind = layoutKind,
    platforms = KmpPlatforms(
        android = android?.takeUnless { it.isEmpty() },
        ios = ios?.takeUnless { it.isEmpty() },
    ),
)

fun KmpFirebaseSection.toState(): KmpfireState? {
    val androidConfigs = platforms.android?.takeUnless { it.isEmpty() }
    val iosConfigs = platforms.ios?.takeUnless { it.isEmpty() }
    if (androidConfigs == null && iosConfigs == null) return null
    return KmpfireState(
        projectId = projectId,
        sdk = sdk,
        layoutKind = layoutKind,
        android = androidConfigs,
        ios = iosConfigs,
    )
}

/** Older nested section where projectId lived on each platform entry. */
@Serializable
internal data class LegacyNestedEntry(
    val projectId: String? = null,
    val appId: String,
    val fileOutput: String,
    val packageNameOrBundleId: String? = null,
)

@Serializable
internal data class LegacyNestedConfigs(
    val default: LegacyNestedEntry? = null,
)

@Serializable
internal data class LegacyNestedPlatforms(
    val android: LegacyNestedConfigs? = null,
    val ios: LegacyNestedConfigs? = null,
)

@Serializable
internal data class LegacyNestedSection(
    val sdk: String = "gitlive",
    val layoutKind: String = "custom",
    val platforms: LegacyNestedPlatforms,
    val projectId: String? = null,
)

/** Flat standalone `kmpfire.json`. */
@Serializable
internal data class LegacyFlatState(
    val projectId: String,
    val sdk: String = "gitlive",
    val layoutKind: String,
    val android: PlatformState? = null,
    val ios: PlatformState? = null,
)