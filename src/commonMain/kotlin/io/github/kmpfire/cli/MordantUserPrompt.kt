package io.github.kmpfire.cli

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.interactiveMultiSelectList
import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.terminal.StringPrompt
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.YesNoPrompt
import io.github.kmpfire.configure.ConfigureException

/**
 * FlutterFire-style prompts via Mordant (bundled with Clikt):
 * arrow keys + space/enter for lists; YesNo / String prompts for the rest.
 *
 * Falls back to [LineUserPrompt] when stdin/stdout are not a TTY.
 */
class MordantUserPrompt(
    private val terminal: Terminal,
    private val fallback: UserPrompt = LineUserPrompt(
        println = { terminal.println(it) },
        readLine = { terminal.readLineOrNull(hideInput = false) },
    ),
) : UserPrompt {
    private val interactive: Boolean
        get() = terminal.terminalInfo.interactive

    override fun confirm(message: String, defaultYes: Boolean): Boolean {
        if (!interactive) return fallback.confirm(message, defaultYes)
        return YesNoPrompt(
            prompt = message,
            terminal = terminal,
            default = defaultYes,
        ).ask() ?: defaultYes
    }

    override fun choose(message: String, choices: List<String>): Int {
        require(choices.isNotEmpty())
        if (!interactive) return fallback.choose(message, choices)
        val selected = terminal.interactiveSelectList(
            entries = choices,
            title = message,
        ) ?: throw ConfigureException("Selection cancelled.")
        val index = choices.indexOf(selected)
        if (index < 0) throw ConfigureException("Invalid selection: $selected")
        return index
    }

    override fun ask(message: String, default: String?): String {
        if (!interactive) return fallback.ask(message, default)
        return StringPrompt(
            prompt = message,
            terminal = terminal,
            default = default,
            showDefault = !default.isNullOrBlank(),
            allowBlank = default != null,
        ).ask()?.takeIf { it.isNotBlank() }
            ?: default
            ?: ask(message, default = null)
    }

    override fun multiSelect(
        message: String,
        choices: List<String>,
        defaultSelected: List<Boolean>,
    ): List<Int> {
        require(choices.isNotEmpty())
        require(choices.size == defaultSelected.size)
        if (!interactive) return fallback.multiSelect(message, choices, defaultSelected)

        val selectedTitles = terminal.interactiveMultiSelectList {
            title(message)
            keyToggle(KeyboardEvent(" ")) // footer: "space toggle"
            choices.forEachIndexed { i, title ->
                addEntry(
                    title = title,
                    selected = defaultSelected[i],
                )
            }
        } ?: throw ConfigureException("Selection cancelled.")

        if (selectedTitles.isEmpty()) {
            throw ConfigureException("Select at least one platform.")
        }
        return selectedTitles.map { title ->
            choices.indexOf(title).also { idx ->
                if (idx < 0) throw ConfigureException("Invalid selection: $title")
            }
        }.sorted()
    }
}

/** Prefer Mordant interactive UI when TTY; otherwise line fallback / reject. */
fun userPromptFor(terminal: Terminal, nonInteractive: Boolean): UserPrompt {
    if (nonInteractive) return RejectPrompt
    return MordantUserPrompt(terminal)
}
