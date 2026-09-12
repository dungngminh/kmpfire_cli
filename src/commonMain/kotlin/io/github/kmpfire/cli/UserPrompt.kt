package io.github.kmpfire.cli

/**
 * Interactive prompts for configure / reconfigure.
 * Tests inject a fake; Clikt wires [LineUserPrompt] from the terminal.
 */
interface UserPrompt {
    fun confirm(message: String, defaultYes: Boolean = true): Boolean
    fun choose(message: String, choices: List<String>): Int
    fun ask(message: String, default: String? = null): String

    /**
     * Multi-select (flutterfire-style). Returns selected indices.
     * [defaultSelected] size must match [choices].
     */
    fun multiSelect(
        message: String,
        choices: List<String>,
        defaultSelected: List<Boolean>,
    ): List<Int>
}

/** Always declines confirms / fails chooses — for --yes paths that must not prompt. */
object RejectPrompt : UserPrompt {
    override fun confirm(message: String, defaultYes: Boolean): Boolean = false
    override fun choose(message: String, choices: List<String>): Int =
        error("Interactive choice required: $message")
    override fun ask(message: String, default: String?): String =
        default ?: error("Interactive input required: $message")
    override fun multiSelect(
        message: String,
        choices: List<String>,
        defaultSelected: List<Boolean>,
    ): List<Int> = defaultSelected.mapIndexedNotNull { i, on -> if (on) i else null }
}

/**
 * Line-based prompt using a read/write pair (works on JVM + Native without Mordant Prompt).
 */
class LineUserPrompt(
    private val println: (String) -> Unit,
    private val readLine: () -> String?,
) : UserPrompt {
    override fun confirm(message: String, defaultYes: Boolean): Boolean {
        val hint = if (defaultYes) "Y/n" else "y/N"
        println("$message [$hint]")
        val line = readLine()?.trim()?.lowercase().orEmpty()
        if (line.isEmpty()) return defaultYes
        return line == "y" || line == "yes"
    }

    override fun choose(message: String, choices: List<String>): Int {
        require(choices.isNotEmpty())
        println(message)
        choices.forEachIndexed { i, c -> println("  ${i + 1}) $c") }
        while (true) {
            println("Enter number (1-${choices.size}):")
            val line = readLine()?.trim().orEmpty()
            val n = line.toIntOrNull()
            if (n != null && n in 1..choices.size) return n - 1
            println("Invalid choice.")
        }
    }

    override fun ask(message: String, default: String?): String {
        val suffix = if (default != null) " ($default)" else ""
        println("$message$suffix:")
        val line = readLine()?.trim().orEmpty()
        if (line.isNotEmpty()) return line
        return default ?: ask(message, default = null)
    }

    override fun multiSelect(
        message: String,
        choices: List<String>,
        defaultSelected: List<Boolean>,
    ): List<Int> {
        require(choices.isNotEmpty())
        require(choices.size == defaultSelected.size)
        val selected = defaultSelected.toMutableList()
        fun render() {
            println(message)
            println("(toggle numbers e.g. 1 or 1,2 — Enter to confirm)")
            choices.forEachIndexed { i, c ->
                val mark = if (selected[i]) "x" else " "
                println("  [$mark] ${i + 1}) $c")
            }
        }
        while (true) {
            render()
            val line = readLine()?.trim().orEmpty()
            if (line.isEmpty()) {
                val indices = selected.mapIndexedNotNull { i, on -> if (on) i else null }
                if (indices.isNotEmpty()) return indices
                println("Select at least one platform.")
                continue
            }
            val parts = line.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
            var toggled = false
            for (part in parts) {
                val n = part.toIntOrNull()
                if (n != null && n in 1..choices.size) {
                    selected[n - 1] = !selected[n - 1]
                    toggled = true
                }
            }
            if (!toggled) println("Invalid input. Use numbers like 1 or 1,2")
        }
    }
}
