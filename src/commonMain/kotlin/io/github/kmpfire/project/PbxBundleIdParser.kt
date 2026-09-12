package io.github.kmpfire.project

object PbxBundleIdParser {
    private val productBundleIdRegex =
        Regex("""PRODUCT_BUNDLE_IDENTIFIER\s*=\s*([^;]+);""")

    fun parseBundleId(pbxproj: String): String? {
        val candidates = productBundleIdRegex.findAll(pbxproj)
            .map { it.groupValues[1].trim().trim('"') }
            .filter { it.isNotBlank() && !it.contains("\${") && it != "\$(PRODUCT_BUNDLE_IDENTIFIER)" }
            .distinct()
            .toList()
        return candidates.firstOrNull()
    }
}
