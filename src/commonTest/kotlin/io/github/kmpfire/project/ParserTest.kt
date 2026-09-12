package io.github.kmpfire.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradleIdParserTest {
    @Test
    fun parsesApplicationId() {
        val src = """
            android {
                namespace = "com.example.shared"
                defaultConfig {
                    applicationId = "com.example.app"
                }
            }
        """.trimIndent()
        assertEquals("com.example.app", GradleIdParser.parseAndroidPackage(src))
    }

    @Test
    fun fallsBackToNamespace() {
        val src = """android { namespace = "com.example.app" }"""
        assertEquals("com.example.app", GradleIdParser.parseAndroidPackage(src))
    }
}

class PbxBundleIdParserTest {
    @Test
    fun parsesBundleIdIgnoringVariables() {
        val src = """
            PRODUCT_BUNDLE_IDENTIFIER = com.example.app;
            PRODUCT_BUNDLE_IDENTIFIER = "$(PRODUCT_BUNDLE_IDENTIFIER)";
            PRODUCT_BUNDLE_IDENTIFIER = "${'$'}{TEAM}.app";
        """.trimIndent()
        assertEquals("com.example.app", PbxBundleIdParser.parseBundleId(src))
    }

    @Test
    fun returnsNullWhenOnlyVariables() {
        val src = """PRODUCT_BUNDLE_IDENTIFIER = "$(PRODUCT_BUNDLE_IDENTIFIER)";"""
        assertNull(PbxBundleIdParser.parseBundleId(src))
    }

    @Test
    fun parsesComposeMultiplatformXcconfigAndStripsEmptyTeamId() {
        val xcconfig = """
            TEAM_ID=

            PRODUCT_NAME=KMPFireTest
            PRODUCT_BUNDLE_IDENTIFIER=me.dungngminh.kmpfiretest.KMPFireTest${'$'}(TEAM_ID)
        """.trimIndent()
        assertEquals(
            "me.dungngminh.kmpfiretest.KMPFireTest",
            PbxBundleIdParser.parseBundleId(pbxproj = null, xcconfigs = listOf(xcconfig)),
        )
    }

    @Test
    fun resolvesPbxPlaceholderFromXcconfig() {
        val pbx = """PRODUCT_BUNDLE_IDENTIFIER = "$(PRODUCT_BUNDLE_IDENTIFIER)";"""
        val xcconfig = """
            TEAM_ID=ABC
            PRODUCT_BUNDLE_IDENTIFIER=com.example.app${'$'}(TEAM_ID)
        """.trimIndent()
        assertEquals(
            "com.example.appABC",
            PbxBundleIdParser.parseBundleId(pbxproj = pbx, xcconfigs = listOf(xcconfig)),
        )
    }

    @Test
    fun prefersLiteralPbxprojOverXcconfig() {
        val pbx = """PRODUCT_BUNDLE_IDENTIFIER = com.example.app.ios;"""
        val xcconfig = "PRODUCT_BUNDLE_IDENTIFIER=com.example.other"
        assertEquals(
            "com.example.app.ios",
            PbxBundleIdParser.parseBundleId(pbxproj = pbx, xcconfigs = listOf(xcconfig)),
        )
    }
}
