package io.github.kmpfire.fs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathsJvmTest {
    @Test
    fun relativize_absoluteUnderRoot() {
        assertEquals(
            "app/androidApp/google-services.json",
            Paths.relativize(
                "/Users/me/loggoo",
                "/Users/me/loggoo/app/androidApp/google-services.json",
            ),
        )
    }

    @Test
    fun relativize_alreadyRelative() {
        assertEquals(
            "androidApp/google-services.json",
            Paths.relativize("/Users/me/proj", "androidApp/google-services.json"),
        )
    }

    @Test
    fun resolve_relative() {
        assertEquals(
            "/Users/me/proj/androidApp/google-services.json",
            Paths.resolve("/Users/me/proj", "androidApp/google-services.json"),
        )
    }

    @Test
    fun resolve_keepsAbsolute() {
        assertEquals(
            "/abs/google-services.json",
            Paths.resolve("/Users/me/proj", "/abs/google-services.json"),
        )
    }

    @Test
    fun isAbsolute() {
        assertTrue(Paths.isAbsolute("/tmp/a"))
        assertFalse(Paths.isAbsolute("rel/a"))
    }
}
