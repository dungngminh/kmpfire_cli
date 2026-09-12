package io.github.kmpfire.process

import java.io.File
import java.util.concurrent.TimeUnit

actual fun createProcessRunner(): ProcessRunner = JvmProcessRunner()

private class JvmProcessRunner : ProcessRunner {
    override fun run(
        command: String,
        args: List<String>,
        workingDirectory: String?,
        environment: Map<String, String>,
    ): ProcessResult {
        val builder = ProcessBuilder(listOf(command) + args)
            .redirectErrorStream(false)
        if (workingDirectory != null) {
            builder.directory(File(workingDirectory))
        }
        if (environment.isNotEmpty()) {
            builder.environment().putAll(environment)
        }
        val process = builder.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(5, TimeUnit.MINUTES)
        val exitCode = if (finished) process.exitValue() else {
            process.destroyForcibly()
            -1
        }
        return ProcessResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }
}
