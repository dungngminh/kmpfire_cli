package io.github.kmpfire.fs

expect object Fs {
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun isFile(path: String): Boolean
    fun readText(path: String): String
    fun writeText(path: String, contents: String)
    fun createDirectories(path: String)
    fun list(path: String): List<String>
    fun join(first: String, vararg more: String): String
    fun parent(path: String): String?
    fun walkFiles(root: String, maxDepth: Int = 8): List<String>
}
