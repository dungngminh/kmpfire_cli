package io.github.kmpfire.firebase

import io.github.kmpfire.cli.CliProgress
import io.github.kmpfire.cli.NoopCliProgress
import io.github.kmpfire.cli.UserPrompt
import io.github.kmpfire.configure.ConfigureException

/**
 * Resolve a Firebase project id, matching flutterfire interactive UX:
 * list projects, optional `<create a new project>`, create via `firebase projects:create`.
 *
 * Differs from flutterfire when `--project` is missing from the list:
 * flutterfire throws; kmpfire offers to create (or auto-creates with createIfMissing).
 */
class FirebaseProjectResolver(
    private val firebaseCli: FirebaseClient,
    private val prompt: UserPrompt,
    private val progress: CliProgress = NoopCliProgress,
) {
    data class Auth(
        val account: String? = null,
        val token: String? = null,
        val serviceAccount: String? = null,
    )

    fun resolve(
        requestedProjectId: String?,
        createIfMissing: Boolean,
        dryRun: Boolean,
        auth: Auth = Auth(),
        messages: MutableList<String>,
    ): String {
        val projects = try {
            progress.spin(
                loading = "Fetching Firebase projects",
                done = { list -> "Found ${list.size} Firebase project(s)" },
            ) {
                firebaseCli.projectsList(
                    account = auth.account,
                    token = auth.token,
                    serviceAccount = auth.serviceAccount,
                )
            }
        } catch (e: FirebaseCliException) {
            val warn = "warn: failed to list Firebase projects: ${e.message}"
            messages += warn
            progress.warn(warn.removePrefix("warn: "))
            return resolveWhenListFails(
                requestedProjectId = requestedProjectId,
                createIfMissing = createIfMissing,
                dryRun = dryRun,
                auth = auth,
                messages = messages,
                listError = e,
            )
        }

        messages += "Found ${projects.size} Firebase project(s)"

        val requested = requestedProjectId?.trim()?.takeIf { it.isNotEmpty() }
        if (requested != null) {
            val existing = projects.firstOrNull { it.projectId == requested }
            if (existing != null) {
                messages += "Using Firebase project ${existing.projectId}"
                progress.ok("Using Firebase project ${existing.projectId}")
                return existing.projectId
            }
            return createMissingProject(
                projectId = requested,
                createIfMissing = createIfMissing,
                dryRun = dryRun,
                auth = auth,
                messages = messages,
            )
        }

        if (projects.isEmpty()) {
            messages += "No Firebase projects found for this account."
            return promptCreateNew(dryRun = dryRun, auth = auth, messages = messages)
        }

        val labels = projects.map { p ->
            val name = p.displayName?.takeIf { it.isNotBlank() }
            if (name != null) "${p.projectId} ($name)" else p.projectId
        } + CREATE_NEW_LABEL
        val index = prompt.choose(
            "Select a Firebase project to configure your KMP application with",
            labels,
        )
        if (index == labels.lastIndex) {
            return promptCreateNew(dryRun = dryRun, auth = auth, messages = messages)
        }
        val selected = projects[index].projectId
        messages += "Using Firebase project $selected"
        progress.ok("Using Firebase project $selected")
        return selected
    }

    private fun resolveWhenListFails(
        requestedProjectId: String?,
        createIfMissing: Boolean,
        dryRun: Boolean,
        auth: Auth,
        messages: MutableList<String>,
        listError: FirebaseCliException,
    ): String {
        val requested = requestedProjectId?.trim()?.takeIf { it.isNotEmpty() }
        if (requested != null) {
            if (createIfMissing || prompt.confirm(
                    "Could not verify project list. Create Firebase project `$requested` anyway?",
                    defaultYes = false,
                )
            ) {
                return createProject(requested, dryRun, auth, messages)
            }
            throw ConfigureException(
                FirebaseAppProvisioner.formatFirebaseError("projects:list", listError) +
                    "\nPass an existing --project or fix Firebase login, then retry.",
            )
        }
        if (prompt.confirm("Would you like to create a new Firebase project?", defaultYes = true)) {
            return promptCreateNew(dryRun = dryRun, auth = auth, messages = messages)
        }
        throw ConfigureException(
            FirebaseAppProvisioner.formatFirebaseError("projects:list", listError) +
                "\nRun `firebase login` then retry, or pass --project=<id>.",
        )
    }

    private fun createMissingProject(
        projectId: String,
        createIfMissing: Boolean,
        dryRun: Boolean,
        auth: Auth,
        messages: MutableList<String>,
    ): String {
        messages += "Firebase project `$projectId` not found."
        val shouldCreate = createIfMissing || prompt.confirm(
            "Create new Firebase project `$projectId`?",
            defaultYes = true,
        )
        if (!shouldCreate) {
            throw ConfigureException(
                "Firebase project `$projectId` not found. " +
                    "Pick an existing project, pass --create-project with --yes, " +
                    "or answer yes when prompted to create it.",
            )
        }
        return createProject(projectId, dryRun, auth, messages)
    }

    private fun promptCreateNew(
        dryRun: Boolean,
        auth: Auth,
        messages: MutableList<String>,
    ): String {
        val projectId = prompt.ask(
            "Enter a project id for your new Firebase project (e.g. my-cool-project)",
        )
        validateProjectId(projectId)
        return createProject(projectId, dryRun, auth, messages)
    }

    private fun createProject(
        projectId: String,
        dryRun: Boolean,
        auth: Auth,
        messages: MutableList<String>,
    ): String {
        validateProjectId(projectId)
        if (dryRun) {
            messages += "dry-run: would create Firebase project `$projectId`"
            progress.info("dry-run: would create Firebase project `$projectId`")
            return projectId
        }
        val created = try {
            progress.spin(
                loading = "Creating Firebase project `$projectId`",
                done = { "Created Firebase project ${it.projectId}" },
            ) {
                firebaseCli.projectsCreate(
                    projectId = projectId,
                    displayName = projectId,
                    account = auth.account,
                    token = auth.token,
                    serviceAccount = auth.serviceAccount,
                )
            }
        } catch (e: FirebaseCliException) {
            throw ConfigureException(
                FirebaseAppProvisioner.formatFirebaseError("projects:create", e) +
                    "\nHint: create the project in Firebase Console if GCP permissions fail, then re-run.",
            )
        }
        messages += "Created Firebase project ${created.projectId}"
        return created.projectId
    }

    companion object {
        const val CREATE_NEW_LABEL = "<create a new project>"

        fun validateProjectId(projectId: String) {
            val ok = projectId.matches(Regex("^[a-z0-9-]+$")) &&
                projectId.length in 6..30 &&
                projectId.first().isLetter() &&
                !projectId.endsWith("-")
            if (!ok) {
                throw ConfigureException(
                    "Invalid Firebase project id `$projectId`. " +
                        "Use 6–30 chars: lowercase letters, digits, hyphens; must start with a letter.",
                )
            }
        }
    }
}
