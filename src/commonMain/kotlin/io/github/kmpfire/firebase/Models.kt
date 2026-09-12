package io.github.kmpfire.firebase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class FirebaseCliEnvelope(
    val status: String,
    val result: JsonElement? = null,
    val error: String? = null,
)

@Serializable
data class FirebaseProject(
    val projectId: String,
    val displayName: String? = null,
    val state: String? = null,
    val projectNumber: String? = null,
)

@Serializable
data class FirebaseApp(
    val appId: String,
    val displayName: String? = null,
    val platform: String,
    val packageName: String? = null,
    val bundleId: String? = null,
) {
    val packageNameOrBundleId: String?
        get() = packageName ?: bundleId
}

@Serializable
data class FirebaseAppSdkConfig(
    val fileName: String,
    val fileContents: String,
)

@Serializable
data class FirebaseAppsCreateResult(
    val appId: String,
    val displayName: String? = null,
    @SerialName("platform") val platformRaw: String? = null,
    val packageName: String? = null,
    val bundleId: String? = null,
)

class FirebaseCliException(
    val command: String,
    override val message: String,
) : Exception(message)
