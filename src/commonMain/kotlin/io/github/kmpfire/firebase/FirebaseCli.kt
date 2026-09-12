package io.github.kmpfire.firebase

import io.github.kmpfire.process.ProcessRunner
import io.github.kmpfire.process.createProcessRunner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class FirebaseCli(
    private val processRunner: ProcessRunner = createProcessRunner(),
    private val firebaseCommand: String = "firebase",
) : FirebaseClient {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun exists(): Boolean {
        val result = processRunner.run(firebaseCommand, listOf("--version"))
        return result.exitCode == 0
    }

    override fun version(): String {
        val result = processRunner.run(firebaseCommand, listOf("--version"))
        if (result.exitCode != 0) {
            throw FirebaseCliException(
                "--version",
                result.stderr.ifBlank { result.stdout }.ifBlank {
                    "Firebase CLI not found. Install firebase-tools: https://firebase.google.com/docs/cli"
                },
            )
        }
        return result.stdout.trim()
    }

    override fun projectsList(
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): List<FirebaseProject> {
        val args = buildList {
            add("projects:list")
            if (token != null) add("--token=$token")
            if (account != null) add("--account=$account")
        }
        val result = runJson(args, serviceAccount = serviceAccount)
        return json.decodeFromJsonElement<List<FirebaseProject>>(result)
            .filter { it.state == null || it.state == "ACTIVE" }
    }

    override fun projectsCreate(
        projectId: String,
        displayName: String?,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseProject {
        val args = buildList {
            add("projects:create")
            add(projectId)
            if (displayName != null) add(displayName)
            if (token != null) add("--token=$token")
        }
        val result = runJson(args, account = account, serviceAccount = serviceAccount)
        val created = json.decodeFromJsonElement<FirebaseProject>(result)
        return created.copy(state = created.state ?: "ACTIVE")
    }

    override fun appsList(
        project: String,
        platform: String?,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): List<FirebaseApp> {
        val args = buildList {
            add("apps:list")
            if (platform != null) add(platform)
            if (token != null) add("--token=$token")
        }
        val result = runJson(
            args,
            project = project,
            account = account,
            serviceAccount = serviceAccount,
        )
        return json.decodeFromJsonElement(result)
    }

    override fun appsCreateAndroid(
        project: String,
        displayName: String,
        packageName: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseApp {
        val args = buildList {
            add("apps:create")
            add("android")
            add(displayName)
            add("--package-name=$packageName")
            if (token != null) add("--token=$token")
        }
        val created = decodeCreate(
            runJson(args, project = project, account = account, serviceAccount = serviceAccount),
        )
        return FirebaseApp(
            appId = created.appId,
            displayName = created.displayName ?: displayName,
            platform = "ANDROID",
            packageName = created.packageName ?: packageName,
            bundleId = null,
        )
    }

    override fun appsCreateIos(
        project: String,
        displayName: String,
        bundleId: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
    ): FirebaseApp {
        val args = buildList {
            add("apps:create")
            add("ios")
            add(displayName)
            add("--bundle-id=$bundleId")
            if (token != null) add("--token=$token")
        }
        val created = decodeCreate(
            runJson(args, project = project, account = account, serviceAccount = serviceAccount),
        )
        return FirebaseApp(
            appId = created.appId,
            displayName = created.displayName ?: displayName,
            platform = "IOS",
            packageName = null,
            bundleId = created.bundleId ?: bundleId,
        )
    }

    override fun appsSdkConfig(
        platform: String,
        appId: String,
        account: String?,
        token: String?,
        serviceAccount: String?,
        project: String?,
    ): FirebaseAppSdkConfig {
        val args = buildList {
            add("apps:sdkconfig")
            add(platform)
            add(appId)
            if (token != null) add("--token=$token")
        }
        val result = runJson(
            args,
            project = project,
            account = account,
            serviceAccount = serviceAccount,
        )
        return json.decodeFromJsonElement(result)
    }

    fun runJson(
        commandAndArgs: List<String>,
        project: String? = null,
        account: String? = null,
        serviceAccount: String? = null,
    ): kotlinx.serialization.json.JsonElement {
        require(exists()) {
            "Firebase CLI not found. Install firebase-tools: https://firebase.google.com/docs/cli"
        }
        val execArgs = buildList {
            addAll(commandAndArgs)
            add("--json")
            if (project != null) add("--project=$project")
            if (account != null) add("--account=$account")
        }
        val env = buildMap {
            if (serviceAccount != null) {
                put("GOOGLE_APPLICATION_CREDENTIALS", serviceAccount)
            }
        }
        val process = processRunner.run(
            command = firebaseCommand,
            args = execArgs,
            environment = env,
        )
        val raw = extractJsonPayload(
            listOf(process.stdout, process.stderr)
                .firstOrNull { it.contains('{') }
                ?: process.stdout.ifBlank { process.stderr },
        )
        val envelope = try {
            json.decodeFromString(FirebaseCliEnvelope.serializer(), raw)
        } catch (e: Exception) {
            throw FirebaseCliException(
                execArgs.joinToString(" "),
                "Failed to parse Firebase CLI JSON. exit=${process.exitCode}. output=${raw.take(800)}",
            )
        }
        if (envelope.status != "success") {
            throw FirebaseCliException(
                execArgs.joinToString(" "),
                envelope.error
                    ?: "Firebase CLI command failed (exit=${process.exitCode})",
            )
        }
        return envelope.result
            ?: throw FirebaseCliException(execArgs.joinToString(" "), "Firebase CLI returned empty result")
    }

    private fun decodeCreate(result: kotlinx.serialization.json.JsonElement): FirebaseAppsCreateResult =
        json.decodeFromJsonElement<FirebaseAppsCreateResult>(result)
}

internal fun extractJsonPayload(output: String): String {
    val trimmed = firebaseCliJsonSanitize(output).trim()
    val start = trimmed.indexOf('{')
    if (start < 0) return trimmed
    return trimmed.substring(start)
}

internal fun firebaseCliJsonSanitize(output: String): String {
    // FlutterFire strips a trailing timed-out error object that can break parsing.
    val timeoutTail =
        Regex("""\}\s*\{\s*"status"\s*:\s*"error"\s*,\s*"error"\s*:\s*"Timed out\."\s*\}""")
    return output.replaceFirst(timeoutTail, "}")
}
