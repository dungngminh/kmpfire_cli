package io.github.kmpfire.config

import io.github.kmpfire.fs.Fs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Persist state in [KmpfireState.FILE_NAME] under `"kotlinMultiplatform"`,
 * merging with existing keys (`hosting`, `flutter`, …) — same approach as FlutterFire.
 */
class StateStore(
    private val fs: Fs = Fs,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun pathFor(projectRoot: String): String = fs.join(projectRoot, KmpfireState.FILE_NAME)

    fun read(projectRoot: String): KmpfireState? {
        readFromFirebaseJson(projectRoot)?.let { return it }
        return readLegacyKmpfireJson(projectRoot)
    }

    /** True when `firebase.json` already has a `"kotlinMultiplatform"` (or legacy) section. */
    fun hasKmpSection(projectRoot: String): Boolean {
        val path = pathFor(projectRoot)
        if (!fs.isFile(path)) return false
        val root = runCatching { json.parseToJsonElement(fs.readText(path)).jsonObject }.getOrNull()
            ?: return false
        return root.containsKey(KmpfireState.ROOT_KEY) ||
            KmpfireState.LEGACY_ROOT_KEYS.any { root.containsKey(it) }
    }

    fun write(projectRoot: String, state: KmpfireState) {
        val path = pathFor(projectRoot)
        val existingRoot = if (fs.isFile(path)) {
            runCatching { json.parseToJsonElement(fs.readText(path)).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
        } else {
            JsonObject(emptyMap())
        }

        val sectionElement = json.encodeToJsonElement(
            KmpFirebaseSection.serializer(),
            state.toFirebaseSection(),
        )
        val dropKeys = setOf(KmpfireState.ROOT_KEY) + KmpfireState.LEGACY_ROOT_KEYS
        val merged = buildJsonObject {
            existingRoot.forEach { (key, value) ->
                if (key !in dropKeys) put(key, value)
            }
            put(KmpfireState.ROOT_KEY, sectionElement)
        }
        fs.writeText(path, json.encodeToString(JsonObject.serializer(), merged) + "\n")
    }

    private fun readFromFirebaseJson(projectRoot: String): KmpfireState? {
        val path = pathFor(projectRoot)
        if (!fs.isFile(path)) return null
        val root = runCatching { json.parseToJsonElement(fs.readText(path)).jsonObject }.getOrNull()
            ?: return null
        val section = root[KmpfireState.ROOT_KEY]
            ?: KmpfireState.LEGACY_ROOT_KEYS.firstNotNullOfOrNull { root[it] }
            ?: return null
        runCatching {
            json.decodeFromJsonElement(KmpFirebaseSection.serializer(), section).toState()
        }.getOrNull()?.let { return it }
        return decodeLegacyNestedSection(section)
    }

    private fun decodeLegacyNestedSection(section: kotlinx.serialization.json.JsonElement): KmpfireState? {
        val decoded = runCatching {
            json.decodeFromJsonElement(LegacyNestedSection.serializer(), section)
        }.getOrNull() ?: return null
        val androidDefault = decoded.platforms.android?.default
        val iosDefault = decoded.platforms.ios?.default
        if (androidDefault == null && iosDefault == null) return null
        val projectId = decoded.projectId
            ?: androidDefault?.projectId
            ?: iosDefault?.projectId
            ?: return null
        return KmpfireState(
            projectId = projectId,
            sdk = decoded.sdk,
            layoutKind = decoded.layoutKind,
            android = androidDefault?.let {
                PlatformConfigs(
                    default = PlatformState(
                        appId = it.appId,
                        fileOutput = it.fileOutput,
                        packageNameOrBundleId = it.packageNameOrBundleId,
                    ),
                )
            },
            ios = iosDefault?.let {
                PlatformConfigs(
                    default = PlatformState(
                        appId = it.appId,
                        fileOutput = it.fileOutput,
                        packageNameOrBundleId = it.packageNameOrBundleId,
                    ),
                )
            },
        )
    }

    private fun readLegacyKmpfireJson(projectRoot: String): KmpfireState? {
        val path = fs.join(projectRoot, KmpfireState.LEGACY_FILE_NAME)
        if (!fs.isFile(path)) return null
        val flat = runCatching {
            json.decodeFromString(LegacyFlatState.serializer(), fs.readText(path))
        }.getOrNull() ?: return null
        return KmpfireState(
            projectId = flat.projectId,
            sdk = flat.sdk,
            layoutKind = flat.layoutKind,
            android = flat.android?.let { PlatformConfigs(default = it) },
            ios = flat.ios?.let { PlatformConfigs(default = it) },
        )
    }
}
