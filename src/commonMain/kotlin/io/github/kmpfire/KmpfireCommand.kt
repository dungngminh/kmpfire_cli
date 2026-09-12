package io.github.kmpfire

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.versionOption
import io.github.kmpfire.commands.ConfigureCommand
import io.github.kmpfire.commands.ReconfigureCommand

class KmpfireCommand : CliktCommand(name = "kmpfire") {
    init {
        versionOption(
            version = AppVersion.VALUE,
            names = setOf("--version", "-V"),
        )
        subcommands(
            ConfigureCommand(),
            ReconfigureCommand(),
        )
    }

    override val printHelpOnEmptyArgs: Boolean = true

    override fun help(context: Context): String =
        "Configure Firebase for Kotlin Multiplatform / Compose Multiplatform projects"

    override fun helpEpilog(context: Context): String =
        "kmpfire ${AppVersion.VALUE}"

    override fun run() {
        // Root alone → help (printHelpOnEmptyArgs). Subcommands print their own banner.
        if (currentContext.invokedSubcommand == null) {
            currentContext.terminal.println("kmpfire ${AppVersion.VALUE}")
        }
    }
}
