package io.github.kmpfire.fs

import java.io.File

actual object Fs {
    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun isDirectory(path: String): Boolean = File(path).isDirectory

    actual fun isFile(path: String): Boolean = File(path).isFile

    actual fun readText(path: String): String = File(path).readText()

    actual fun writeText(path: String, contents: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(contents)
    }

    actual fun createDirectories(path: String) {
        File(path).mkdirs()
    }

    actual fun list(path: String): List<String> {
        val file = File(path)
        if (!file.isDirectory) return emptyList()
        return file.listFiles()?.map { it.name }?.sorted().orEmpty()
    }

    actual fun join(first: String, vararg more: String): String {
        var result = File(first)
        for (part in more) {
            result = File(result, part)
        }
        return result.path
    }

    actual fun parent(path: String): String? = File(path).parent

    actual fun walkFiles(root: String, maxDepth: Int): List<String> {
        val out = mutableListOf<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || !dir.isDirectory) return
            dir.listFiles()?.forEach { child ->
                if (child.isFile) out += child.path
                else if (child.isDirectory) walk(child, depth + 1)
            }
        }
        walk(File(root), 0)
        return out
    }
}
