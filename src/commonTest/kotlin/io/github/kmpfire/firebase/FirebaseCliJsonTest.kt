package io.github.kmpfire.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

class FirebaseCliJsonTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun extractJsonPayload_stripsLeadingNoise() {
        val payload = extractJsonPayload("Some log\n{\"status\":\"success\",\"result\":[]}")
        assertTrue(payload.startsWith("{"))
    }

    @Test
    fun sanitize_removesTimeoutTail() {
        val raw =
            """{"status":"success","result":[]}{"status":"error","error":"Timed out."}"""
        val cleaned = firebaseCliJsonSanitize(raw)
        assertEquals("""{"status":"success","result":[]}""", cleaned)
    }

    @Test
    fun decode_projectsList() {
        val raw = """
            {
              "status": "success",
              "result": [
                {
                  "projectId": "demo-app",
                  "displayName": "Demo",
                  "state": "ACTIVE",
                  "projectNumber": "123"
                }
              ]
            }
        """.trimIndent()
        val envelope = json.decodeFromString(FirebaseCliEnvelope.serializer(), raw)
        val projects = json.decodeFromJsonElement<List<FirebaseProject>>(envelope.result!!)
        assertEquals(1, projects.size)
        assertEquals("demo-app", projects[0].projectId)
    }

    @Test
    fun decode_appsList_and_sdkconfig() {
        val appsRaw = """
            {
              "status": "success",
              "result": [
                {
                  "appId": "1:123:android:abc",
                  "displayName": "App (android)",
                  "platform": "ANDROID",
                  "packageName": "com.example.app"
                },
                {
                  "appId": "1:123:ios:def",
                  "displayName": "App (ios)",
                  "platform": "IOS",
                  "bundleId": "com.example.app"
                }
              ]
            }
        """.trimIndent()
        val appsEnvelope = json.decodeFromString(FirebaseCliEnvelope.serializer(), appsRaw)
        val apps = json.decodeFromJsonElement<List<FirebaseApp>>(appsEnvelope.result!!)
        assertEquals("com.example.app", apps[0].packageNameOrBundleId)
        assertEquals("com.example.app", apps[1].packageNameOrBundleId)

        val sdkRaw = """
            {
              "status": "success",
              "result": {
                "fileName": "google-services.json",
                "fileContents": "{ \"project_info\": {} }"
              }
            }
        """.trimIndent()
        val sdkEnvelope = json.decodeFromString(FirebaseCliEnvelope.serializer(), sdkRaw)
        val sdk = json.decodeFromJsonElement<FirebaseAppSdkConfig>(sdkEnvelope.result!!)
        assertEquals("google-services.json", sdk.fileName)
        assertTrue(sdk.fileContents.contains("project_info"))
    }
}
