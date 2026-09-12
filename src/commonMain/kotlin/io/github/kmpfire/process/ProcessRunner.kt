package io.github.kmpfire.process

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface ProcessRunner {
    fun run(
        command: String,
        args: List<String>,
        workingDirectory: String? = null,
        environment: Map<String, String> = emptyMap(),
    ): ProcessResult
}

expect fun createProcessRunner(): ProcessRunner
