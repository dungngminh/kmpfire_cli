package io.github.kmpfire.project

object PbxBundleIdParser {
    private val productBundleIdRegex =
        Regex("""PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^;]+);""")
    private val xcconfigAssignmentRegex =
        Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$""")
    private val xcodeVarRegex =
        Regex("""\$\(([^)]+)\)""")

    fun parseBundleId(pbxproj: String? = null, xcconfigs: List<String> = emptyList()): String? {
        val vars = mutableMapOf<String, String>()
        for (xcconfig in xcconfigs) {
            vars.putAll(parseXcconfigVars(xcconfig))
        }

        val candidates = buildList {
            if (pbxproj != null) {
                addAll(
                    productBundleIdRegex.findAll(pbxproj)
                        .map { it.groupValues[1].trim().trim('"') },
                )
            }
            vars["PRODUCT_BUNDLE_IDENTIFIER"]?.let { add(it) }
        }

        return candidates
            .map { resolveXcodeVars(it, vars) }
            .map { it.trim().trim('"') }
            .firstOrNull { looksLikeBundleId(it) }
    }

    private fun parseXcconfigVars(xcconfig: String): Map<String, String> {
        val vars = linkedMapOf<String, String>()
        for (rawLine in xcconfig.lineSequence()) {
            val line = rawLine.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val match = xcconfigAssignmentRegex.matchEntire(line) ?: continue
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim().trim('"')
            vars[key] = value
        }
        return vars
    }

    private fun resolveXcodeVars(value: String, vars: Map<String, String>): String {
        var current = value
        repeat(8) {
            val next = xcodeVarRegex.replace(current) { match ->
                val name = match.groupValues[1]
                val replacement = vars[name] ?: return@replace ""
                if (replacement == match.value) "" else replacement
            }
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun looksLikeBundleId(value: String): Boolean =
        value.isNotBlank() &&
            !value.contains('$') &&
            !value.contains("\${") &&
            value != "PRODUCT_BUNDLE_IDENTIFIER"
}
