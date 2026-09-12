package io.github.kmpfire.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.kmpfire.AppVersion
import io.github.kmpfire.cli.CliProgress
import io.github.kmpfire.cli.UserPrompt
import io.github.kmpfire.cli.cliProgressFor
import io.github.kmpfire.cli.userPromptFor
import io.github.kmpfire.configure.ConfigureException
import io.github.kmpfire.configure.ConfigureOptions
import io.github.kmpfire.configure.ConfigureService
import io.github.kmpfire.configure.SdkMode
import io.github.kmpfire.configure.TargetPlatform
import io.github.kmpfire.fs.currentWorkingDirectory

class ConfigureCommand(
    private val serviceFactory: (UserPrompt, CliProgress) -> ConfigureService =
        { prompt, progress -> ConfigureService(prompt = prompt, progress = progress) },
) : CliktCommand(name = "configure") {
    override fun help(context: Context): String =
        "Register Firebase apps and write google-services.json / GoogleService-Info.plist"

    override fun helpEpilog(context: Context): String =
        "kmpfire ${AppVersion.VALUE}"

    private val project by option("--project", "-P", help = "Firebase project id").default("")
    private val projectDir by option("--project-dir", help = "KMP project root").default("")
    private val platforms by option(
        "--platforms",
        help = "Comma list: android,ios (omit for interactive multi-select)",
    )
    private val sdk by option("--sdk", help = "gitlive|native").default("gitlive")
    private val yes by option("--yes", "-y", help = "Non-interactive defaults").flag(default = false)
    private val createProject by option(
        "--create-project",
        help = "Create Firebase project if --project id is missing",
    ).flag(default = false)
    private val deps by option("--deps", help = "Also patch Gradle dependencies").flag(default = false)
    private val dryRun by option("--dry-run", help = "Print actions without writing or creating apps").flag(default = false)
    private val androidPackageName by option("--android-package-name", help = "Android applicationId")
    private val iosBundleId by option("--ios-bundle-id", help = "iOS PRODUCT_BUNDLE_IDENTIFIER")
    private val androidOut by option("--android-out", help = "Path for google-services.json")
    private val iosOut by option("--ios-out", help = "Path for GoogleService-Info.plist")
    private val flavor by option(
        "--flavor",
        help = "Named flavor / buildConfiguration (omit → platforms.*.default)",
    )

    override fun run() {
        val term = currentContext.terminal
        term.println("kmpfire ${AppVersion.VALUE}")
        val prompt = userPromptFor(term, nonInteractive = yes)
        val progress = cliProgressFor(term)
        val options = ConfigureOptions(
            projectDir = projectDir.ifBlank { currentWorkingDirectory() },
            firebaseProjectId = project.ifBlank { null },
            platforms = platforms?.let { TargetPlatform.parseList(it) },
            sdk = SdkMode.parse(sdk),
            yes = yes,
            createProject = createProject,
            deps = deps,
            dryRun = dryRun,
            androidPackageName = androidPackageName,
            iosBundleId = iosBundleId,
            androidOut = androidOut,
            iosOut = iosOut,
            flavor = flavor,
        )
        try {
            val result = serviceFactory(prompt, progress).configure(options)
            if (!progress.printsLive) {
                result.messages.forEach { term.println(it) }
            }
        } catch (e: ConfigureException) {
            throw CliktError(e.message ?: "configure failed")
        } catch (e: IllegalStateException) {
            throw CliktError(e.message ?: "configure failed")
        }
    }
}
