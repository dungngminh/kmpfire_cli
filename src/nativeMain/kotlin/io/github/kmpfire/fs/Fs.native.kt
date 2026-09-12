package io.github.kmpfire.fs

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.BUFSIZ
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
actual object Fs {
    actual fun exists(path: String): Boolean = memScoped {
        val st = alloc<stat>()
        platform.posix.stat(path, st.ptr) == 0
    }

    actual fun isDirectory(path: String): Boolean = memScoped {
        val st = alloc<stat>()
        if (platform.posix.stat(path, st.ptr) != 0) return false
        (st.st_mode.toUInt() and S_IFMT.toUInt()) == S_IFDIR.toUInt()
    }

    actual fun isFile(path: String): Boolean = memScoped {
        val st = alloc<stat>()
        if (platform.posix.stat(path, st.ptr) != 0) return false
        (st.st_mode.toUInt() and S_IFMT.toUInt()) == S_IFREG.toUInt()
    }

    actual fun readText(path: String): String {
        val file = fopen(path, "r") ?: error("Cannot open $path")
        return try {
            buildString {
                memScoped {
                    val buffer = ByteArray(BUFSIZ.toInt().coerceAtLeast(1024))
                    while (true) {
                        val line = fgets(buffer.refTo(0), buffer.size, file)?.toKString() ?: break
                        append(line)
                    }
                }
            }
        } finally {
            fclose(file)
        }
    }

    actual fun writeText(path: String, contents: String) {
        parent(path)?.let { createDirectories(it) }
        val file = fopen(path, "w") ?: error("Cannot write $path")
        try {
            val bytes = contents.encodeToByteArray()
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
        } finally {
            fclose(file)
        }
    }

    actual fun createDirectories(path: String) {
        if (path.isBlank() || exists(path)) return
        parent(path)?.let { createDirectories(it) }
        mkdir(path, 511u /* 0777 */)
    }

    actual fun list(path: String): List<String> {
        val dir = opendir(path) ?: return emptyList()
        return try {
            buildList {
                while (true) {
                    val entry = readdir(dir) ?: break
                    val name = entry.pointed.d_name.toKString()
                    if (name != "." && name != "..") add(name)
                }
            }.sorted()
        } finally {
            closedir(dir)
        }
    }

    actual fun join(first: String, vararg more: String): String {
        if (more.isEmpty()) return first
        val parts = buildList {
            add(first.trimEnd('/'))
            more.forEach { add(it.trim('/')) }
        }
        return parts.joinToString("/")
    }

    actual fun parent(path: String): String? {
        val normalized = path.trimEnd('/')
        val idx = normalized.lastIndexOf('/')
        if (idx <= 0) return if (idx == 0) "/" else null
        return normalized.substring(0, idx)
    }

    actual fun walkFiles(root: String, maxDepth: Int): List<String> {
        val out = mutableListOf<String>()
        fun walk(dir: String, depth: Int) {
            if (depth > maxDepth || !isDirectory(dir)) return
            list(dir).forEach { name ->
                val child = join(dir, name)
                when {
                    isFile(child) -> out += child
                    isDirectory(child) -> walk(child, depth + 1)
                }
            }
        }
        walk(root, 0)
        return out
    }
}
