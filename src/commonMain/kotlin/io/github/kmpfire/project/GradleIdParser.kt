package io.github.kmpfire.project

object GradleIdParser {
    private val applicationIdRegex =
        Regex("""applicationId\s*=\s*["']([^"']+)["']""")
    private val applicationIdParenRegex =
        Regex("""applicationId\s*\(\s*["']([^"']+)["']\s*\)""")
    private val namespaceRegex =
        Regex("""namespace\s*=\s*["']([^"']+)["']""")
    private val namespaceParenRegex =
        Regex("""namespace\s*\(\s*["']([^"']+)["']\s*\)""")

    fun parseAndroidPackage(gradleKts: String): String? {
        applicationIdRegex.find(gradleKts)?.groupValues?.get(1)?.let { return it }
        applicationIdParenRegex.find(gradleKts)?.groupValues?.get(1)?.let { return it }
        namespaceRegex.find(gradleKts)?.groupValues?.get(1)?.let { return it }
        return namespaceParenRegex.find(gradleKts)?.groupValues?.get(1)
    }
}
