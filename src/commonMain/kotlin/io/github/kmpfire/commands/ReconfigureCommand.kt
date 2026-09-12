package io.github.kmpfire.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.kmpfire.AppVersion
import io.github.kmpfire.cli.CliProgress
import io.github.kmpfire.cli.cliProgressFor
import io.github.kmpfire.configure.ConfigureException
import io.github.kmpfire.configure.ReconfigureService
import io.github.kmpfire.fs.currentWorkingDirectory

class ReconfigureCommand(
    private val serviceFactory: (CliProgress) -> ReconfigureService =
        { progress -> ReconfigureService(progress = progress) },
) : CliktCommand(name = "reconfigure") {
    override fun help(context: Context): String =
        "Rewrite Firebase service files using paths stored in firebase.json (`kotlinMultiplatform` section)"

    override fun helpEpilog(context: Context): String =
        "kmpfire ${AppVersion.VALUE}"

    private val projectDir by option("--project-dir", help = "KMP project root")
    private val deps by option("--deps", help = "Also patch Gradle dependencies").flag(default = false)
    private val dryRun by option("--dry-run").flag(default = false)
    private val flavor by option(
        "--flavor",
        help = "Only rewrite this buildConfiguration (or `default`); omit → all",
    )

    override fun run() {
        val term = currentContext.terminal
        term.println("kmpfire ${AppVersion.VALUE}")
        val progress = cliProgressFor(term)
        val root = projectDir ?: currentWorkingDirectory()
        try {
            val result = serviceFactory(progress).reconfigure(
                projectDir = root,
                deps = deps,
                dryRun = dryRun,
                flavor = flavor,
            )
            if (!progress.printsLive) {
                result.messages.forEach { term.println(it) }
            }
        } catch (e: ConfigureException) {
            throw CliktError(e.message ?: "reconfigure failed")
        }
    }
}
