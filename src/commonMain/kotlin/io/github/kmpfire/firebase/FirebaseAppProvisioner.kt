package io.github.kmpfire.firebase

import io.github.kmpfire.configure.ConfigureException

/**
 * Find existing Firebase apps by package/bundle, or create them via Firebase CLI.
 */
class FirebaseAppProvisioner(
    private val firebaseCli: FirebaseClient,
) {
    data class Auth(
        val account: String? = null,
        val token: String? = null,
        val serviceAccount: String? = null,
    )

    fun findOrCreateAndroid(
        projectId: String,
        displayName: String,
        packageName: String,
        dryRun: Boolean,
        auth: Auth = Auth(),
    ): Pair<FirebaseApp, Boolean> {
        val apps = try {
            firebaseCli.appsList(
                project = projectId,
                platform = "ANDROID",
                account = auth.account,
                token = auth.token,
                serviceAccount = auth.serviceAccount,
            )
        } catch (e: FirebaseCliException) {
            throw ConfigureException(formatFirebaseError("apps:list ANDROID", e))
        }
        val existing = apps.firstOrNull { it.packageNameOrBundleId == packageName }
        if (existing != null) return existing to false
        if (dryRun) {
            return FirebaseApp(
                appId = "dry-run-android",
                displayName = "$displayName (android)",
                platform = "ANDROID",
                packageName = packageName,
            ) to true
        }
        return try {
            firebaseCli.appsCreateAndroid(
                project = projectId,
                displayName = "$displayName (android)",
                packageName = packageName,
                account = auth.account,
                token = auth.token,
                serviceAccount = auth.serviceAccount,
            ) to true
        } catch (e: FirebaseCliException) {
            throw ConfigureException(formatFirebaseError("apps:create android", e))
        }
    }

    fun findOrCreateIos(
        projectId: String,
        displayName: String,
        bundleId: String,
        dryRun: Boolean,
        auth: Auth = Auth(),
    ): Pair<FirebaseApp, Boolean> {
        val apps = try {
            firebaseCli.appsList(
                project = projectId,
                platform = "IOS",
                account = auth.account,
                token = auth.token,
                serviceAccount = auth.serviceAccount,
            )
        } catch (e: FirebaseCliException) {
            throw ConfigureException(formatFirebaseError("apps:list IOS", e))
        }
        val existing = apps.firstOrNull { it.packageNameOrBundleId == bundleId }
        if (existing != null) return existing to false
        if (dryRun) {
            return FirebaseApp(
                appId = "dry-run-ios",
                displayName = "$displayName (ios)",
                platform = "IOS",
                bundleId = bundleId,
            ) to true
        }
        return try {
            firebaseCli.appsCreateIos(
                project = projectId,
                displayName = "$displayName (ios)",
                bundleId = bundleId,
                account = auth.account,
                token = auth.token,
                serviceAccount = auth.serviceAccount,
            ) to true
        } catch (e: FirebaseCliException) {
            throw ConfigureException(formatFirebaseError("apps:create ios", e))
        }
    }

    companion object {
        fun formatFirebaseError(step: String, e: FirebaseCliException): String = buildString {
            append(e.message ?: "Firebase CLI failed")
            append("\n")
            append("step: $step")
            append("\n")
            append("command: firebase ${e.command}")
            if (e.message?.contains("firebase-debug.log", ignoreCase = true) == true) {
                append("\n")
                append("Hint: run `firebase login` then retry. Also check project id and `firebase-debug.log`.")
            }
        }
    }
}
