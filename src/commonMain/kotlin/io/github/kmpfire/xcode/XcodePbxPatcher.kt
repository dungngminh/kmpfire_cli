package io.github.kmpfire.xcode

import io.github.kmpfire.fs.Fs
import kotlin.random.Random

data class PbxPatchResult(
    val success: Boolean,
    val pbxprojPath: String?,
    val message: String,
    /** True when the pbxproj was modified. */
    val changed: Boolean = false,
)

/**
 * Best-effort patch: add GoogleService-Info.plist to an Xcode target Resources phase.
 * On failure returns [PbxPatchResult.success]=false with manual steps — never throws for parse issues.
 */
class XcodePbxPatcher(
    private val fs: Fs = Fs,
    private val random: Random = Random.Default,
) {
    fun ensurePlistInResources(
        iosAppDir: String,
        plistAbsolutePath: String,
    ): PbxPatchResult {
        val pbxproj = fs.walkFiles(iosAppDir)
            .firstOrNull { it.endsWith("project.pbxproj") }
            ?: return PbxPatchResult(
                success = false,
                pbxprojPath = null,
                message = "No project.pbxproj under $iosAppDir. Add GoogleService-Info.plist to the app target manually in Xcode.",
            )

        val original = try {
            fs.readText(pbxproj)
        } catch (e: Exception) {
            return PbxPatchResult(
                success = false,
                pbxprojPath = pbxproj,
                message = "Cannot read $pbxproj (${e.message}). Add the plist to the app target Resources manually.",
            )
        }

        if (original.contains("GoogleService-Info.plist")) {
            return PbxPatchResult(
                success = true,
                pbxprojPath = pbxproj,
                message = "GoogleService-Info.plist already referenced in $pbxproj",
            )
        }

        return try {
            val patched = patchContent(original, plistFileName = "GoogleService-Info.plist")
            fs.writeText(pbxproj, patched)
            PbxPatchResult(
                success = true,
                pbxprojPath = pbxproj,
                message = "Patched $pbxproj to include GoogleService-Info.plist in Resources",
                changed = true,
            )
        } catch (e: Exception) {
            PbxPatchResult(
                success = false,
                pbxprojPath = pbxproj,
                message = "Failed to patch $pbxproj (${e.message}). " +
                    "File kept at $plistAbsolutePath — in Xcode: Add Files to Target → GoogleService-Info.plist → check target membership.",
            )
        }
    }

    internal fun patchContent(original: String, plistFileName: String): String {
        val fileRefId = newId()
        val buildFileId = newId()

        var content = original

        val fileRefLine =
            "\t\t$fileRefId /* $plistFileName */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = \"$plistFileName\"; sourceTree = \"<group>\"; };\n"
        content = insertIntoSection(
            content = content,
            beginMarker = "/* Begin PBXFileReference section */",
            endMarker = "/* End PBXFileReference section */",
            line = fileRefLine,
        )

        val buildFileLine =
            "\t\t$buildFileId /* $plistFileName in Resources */ = {isa = PBXBuildFile; fileRef = $fileRefId /* $plistFileName */; };\n"
        content = insertIntoSection(
            content = content,
            beginMarker = "/* Begin PBXBuildFile section */",
            endMarker = "/* End PBXBuildFile section */",
            line = buildFileLine,
        )

        content = addToResourcesPhase(content, buildFileId, plistFileName)
        content = addToGroupWithInfoPlist(content, fileRefId, plistFileName)

        return content
    }

    private fun insertIntoSection(
        content: String,
        beginMarker: String,
        endMarker: String,
        line: String,
    ): String {
        val begin = content.indexOf(beginMarker)
        val end = content.indexOf(endMarker)
        if (begin < 0 || end < 0 || end <= begin) {
            error("Missing section $beginMarker")
        }
        return content.substring(0, end) + line + content.substring(end)
    }

    private fun addToResourcesPhase(content: String, buildFileId: String, plistFileName: String): String {
        val marker = "isa = PBXResourcesBuildPhase;"
        val isaIndex = content.indexOf(marker)
        if (isaIndex < 0) error("No PBXResourcesBuildPhase found")
        val filesIndex = content.indexOf("files = (", isaIndex)
        if (filesIndex < 0) error("PBXResourcesBuildPhase has no files = (")
        val insertAt = filesIndex + "files = (".length
        val entry = "\n\t\t\t\t$buildFileId /* $plistFileName in Resources */,"
        return content.substring(0, insertAt) + entry + content.substring(insertAt)
    }

    private fun addToGroupWithInfoPlist(content: String, fileRefId: String, plistFileName: String): String {
        // Prefer a group that already lists Info.plist so the file lands next to it.
        val infoPattern = Regex("""([A-F0-9]{24}) /\* Info\.plist \*/""")
        val infoMatch = infoPattern.find(content)
        if (infoMatch != null) {
            val infoRef = infoMatch.groupValues[1]
            val groupBlock = Regex(
                """([A-F0-9]{24}) /\* [^*]+ \*/ = \{[^}]*?isa = PBXGroup;[^}]*?children = \(([\s\S]*?)\);""",
            )
            for (match in groupBlock.findAll(content)) {
                if (match.groupValues[2].contains(infoRef)) {
                    val childrenOpen = match.range.first + match.value.indexOf("children = (") + "children = (".length
                    val entry = "\n\t\t\t\t$fileRefId /* $plistFileName */,"
                    return content.substring(0, childrenOpen) + entry + content.substring(childrenOpen)
                }
            }
        }

        // Fallback: first PBXGroup with a children list.
        val anyGroup = Regex("""isa = PBXGroup;\s*children = \(""")
        val match = anyGroup.find(content) ?: error("No PBXGroup children list found")
        val insertAt = match.range.last + 1
        val entry = "\n\t\t\t\t$fileRefId /* $plistFileName */,"
        return content.substring(0, insertAt) + entry + content.substring(insertAt)
    }

    private fun newId(): String {
        val alphabet = "0123456789ABCDEF"
        return buildString(24) {
            repeat(24) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }
}
