package io.github.kmpfire.fs

object Paths {
    fun normalize(path: String): String =
        path.replace('\\', '/').replace(Regex("/+"), "/")

    fun isAbsolute(path: String): Boolean {
        val n = normalize(path)
        return n.startsWith("/") || (n.length > 2 && n[1] == ':')
    }

    /** Path relative to [root], always with `/` separators. */
    fun relativize(root: String, path: String): String {
        val normRoot = normalize(root).trimEnd('/')
        val normPath = normalize(path)
        if (!isAbsolute(normPath)) {
            return normPath.removePrefix("./")
        }
        if (normPath == normRoot) return "."
        val prefix = "$normRoot/"
        if (normPath.startsWith(prefix, ignoreCase = false)) {
            return normPath.removePrefix(prefix)
        }
        // Windows drive letter case-insensitive
        if (normPath.startsWith(prefix, ignoreCase = true)) {
            return normPath.substring(prefix.length)
        }
        return normPath
    }

    fun resolve(root: String, path: String): String {
        val n = normalize(path)
        if (isAbsolute(n)) return n
        return normalize(Fs.join(root, n.removePrefix("./")))
    }
}
