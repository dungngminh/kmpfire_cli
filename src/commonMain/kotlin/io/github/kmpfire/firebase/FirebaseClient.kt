package io.github.kmpfire.firebase

interface FirebaseClient {
    fun exists(): Boolean
    fun version(): String
    fun projectsList(
        account: String? = null,
        token: String? = null,
        serviceAccount: String? = null,
    ): List<FirebaseProject>

    fun projectsCreate(
        projectId: String,
        displayName: String? = null,
        account: String? = null,
        token: String? = null,
        serviceAccount: String? = null,
    ): FirebaseProject

    fun appsList(
        project: String,
        platform: String? = null,
        account: String? = null,
        token: String? = null,
        serviceAccount: String? = null,
    ): List<FirebaseApp>

    fun appsCreateAndroid(
        project: String,
        displayName: String,
        packageName: String,
        account: String? = null,
        token: String? = null,
        serviceAccount: String? = null,
    ): FirebaseApp

    fun appsCreateIos(
        project: String,
        displayName: String,
        bundleId: String,
        account: String? = null,
        token: String? = null,
        serviceAccount: String? = null,
    ): FirebaseApp

    fun appsSdkConfig(
        platform: String,
        appId: String,
        account: String? = null,
        token: String? = null,
        serviceAccount: String? = null,
        project: String? = null,
    ): FirebaseAppSdkConfig
}
