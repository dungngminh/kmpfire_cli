package io.github.kmpfire.process

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.chdir
import platform.posix.fgets
import platform.posix.getcwd
import platform.posix.getenv
import platform.posix.pclose
import platform.posix.popen
import platform.posix.setenv
import platform.posix.unsetenv

@OptIn(ExperimentalForeignApi::class)
actual fun createProcessRunner(): ProcessRunner = NativeProcessRunner()

@OptIn(ExperimentalForeignApi::class)
private class NativeProcessRunner : ProcessRunner {
    override fun run(
        command: String,
        args: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
    ): ProcessResult {
        val previousCwd = workingDirectory?.let { changeDirectory(it) }
        val previousEnv = environment.map { (key, value) ->
            val old = getenv(key)?.toKString()
            setenv(key, value, 1)
            key to old
        }
        try {
            val shellCommand = buildString {
                append(shellQuote(command))
                args.forEach { arg ->
                    append(' ')
                    append(shellQuote(arg))
                }
                append(" 2>&1")
            }
            val file = popen(shellCommand, "r")
                ?: return ProcessResult(
                    exitCode = 127,
                    stdout = "",
                    stderr = "Failed to start process: $command",
                )
            val stdout = buildString {
                memScoped {
                    val buffer = ByteArray(4096)
                    while (true) {
                        val line = fgets(buffer.refTo(0), buffer.size, file)?.toKString() ?: break
                        append(line)
                    }
                }
            }
            val exitCode = pclose(file)
            // pclose returns wait status on Unix; normalize to exit code when possible
            val normalized = if (exitCode == -1) -1 else (exitCode shr 8) and 0xff
            return ProcessResult(exitCode = normalized, stdout = stdout, stderr = "")
        } finally {
            previousEnv.forEach { (key, old) ->
                if (old == null) {
                    unsetenv(key)
                } else {
                    setenv(key, old, 1)
                }
            }
            previousCwd?.let { changeDirectory(it) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun changeDirectory(path: String): String? {
    val previous = memScoped {
        val buffer = allocArray<ByteVar>(4096)
        getcwd(buffer, 4096u)?.toKString()
    }
    if (chdir(path) != 0) {
        error("Failed to change directory to $path")
    }
    return previous
}

private fun shellQuote(value: String): String {
    if (value.isEmpty()) return "''"
    if (value.all { it.isLetterOrDigit() || it in "._-/@:=+," }) return value
    return "'" + value.replace("'", "'\\''") + "'"
}
