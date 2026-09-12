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
}
