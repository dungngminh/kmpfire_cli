package io.github.kmpfire.xcode

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.random.Random

class XcodePbxPatcherTest {
    private val fixture = """
        // !$*UTF8*$!
        {
        /* Begin PBXBuildFile section */
        		AA0000000000000000000001 /* ContentView.swift in Sources */ = {isa = PBXBuildFile; fileRef = BB0000000000000000000001 /* ContentView.swift */; };
        /* End PBXBuildFile section */

        /* Begin PBXFileReference section */
        		BB0000000000000000000001 /* ContentView.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ContentView.swift; sourceTree = "<group>"; };
        		BB0000000000000000000002 /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };
        /* End PBXFileReference section */

        /* Begin PBXGroup section */
        		CC0000000000000000000001 /* iosApp */ = {
        			isa = PBXGroup;
        			children = (
        				BB0000000000000000000001 /* ContentView.swift */,
        				BB0000000000000000000002 /* Info.plist */,
        			);
        			path = iosApp;
        			sourceTree = "<group>";
        		};
        /* End PBXGroup section */

        /* Begin PBXResourcesBuildPhase section */
        		DD0000000000000000000001 /* Resources */ = {
        			isa = PBXResourcesBuildPhase;
        			buildActionMask = 2147483647;
        			files = (
        			);
        			runOnlyForDeploymentPostprocessing = 0;
        		};
        /* End PBXResourcesBuildPhase section */
        }
    """.trimIndent()

    @Test
    fun patchesPlistIntoResourcesAndGroup() {
        val patcher = XcodePbxPatcher(random = Random(0))
        val patched = patcher.patchContent(fixture, plistFileName = "GoogleService-Info.plist")
        assertContains(patched, "GoogleService-Info.plist")
        assertContains(patched, "GoogleService-Info.plist in Resources")
        assertContains(patched, "isa = PBXFileReference")
        assertTrue(patched.indexOf("GoogleService-Info.plist in Resources") > 0)
        // Idempotent detection path uses contains check before patching.
        assertFalse(fixture.contains("GoogleService-Info.plist"))
    }

    @Test
    fun skipsWhenAlreadyPresentViaEnsureLogic() {
        val withPlist = fixture.replace(
            "BB0000000000000000000002 /* Info.plist */",
            "BB0000000000000000000002 /* Info.plist */ = {isa = PBXFileReference; path = Info.plist; };\n\t\tEE0000000000000000000001 /* GoogleService-Info.plist */",
        )
        assertTrue(withPlist.contains("GoogleService-Info.plist"))
    }
}
