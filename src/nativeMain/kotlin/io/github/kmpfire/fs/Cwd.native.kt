package io.github.kmpfire.fs

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.getcwd

@OptIn(ExperimentalForeignApi::class)
actual fun currentWorkingDirectory(): String = memScoped {
    val buffer = allocArray<ByteVar>(4096)
    getcwd(buffer, 4096u)?.toKString()
        ?: error("Unable to resolve current working directory")
}
