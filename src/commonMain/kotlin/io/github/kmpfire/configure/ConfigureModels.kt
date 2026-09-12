package io.github.kmpfire.configure

enum class SdkMode {
    GitLive,
    Native,
    ;

    companion object {
        fun parse(value: String): SdkMode = when (value.lowercase()) {
            "gitlive" -> GitLive
            "native" -> Native
            else -> error("Unknown sdk '$value'. Use gitlive|native")
        }

        fun toWire(mode: SdkMode): String = when (mode) {
            GitLive -> "gitlive"
            Native -> "native"
        }
    }
}

enum class TargetPlatform {
    Android,
    Ios,
    ;

    companion object {
        fun parseList(raw: String): List<TargetPlatform> =
            raw.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .map {
                    when (it) {
                        "android" -> Android
                        "ios" -> Ios
                        else -> error("Unknown platform '$it'. Use android,ios")
                    }
                }
                .distinct()
    }
}

data class ConfigureOptions(
    val projectDir: String,
    val firebaseProjectId: String?,
    /** null = detect + interactive (unless --yes). */
    val platforms: List<TargetPlatform>?,
    val sdk: SdkMode,
    val yes: Boolean,
    val deps: Boolean,
    val dryRun: Boolean,
    val androidPackageName: String?,
    val iosBundleId: String?,
    val androidOut: String?,
    val iosOut: String?,
    /**
     * Named flavor / build configuration (FlutterFire `buildConfigurations` key).
     * null/blank → write under `platforms.*.default`.
     */
    val flavor: String? = null,
    /** Auto-create Firebase project when --project id is missing from the account list. */
    val createProject: Boolean = false,
    val account: String? = null,
    val token: String? = null,
    val serviceAccount: String? = null,
)

data class ConfigureResult(
    val messages: List<String>,
    val wroteFiles: List<String>,
)
