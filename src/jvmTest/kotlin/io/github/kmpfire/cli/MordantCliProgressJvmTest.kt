package io.github.kmpfire.cli

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlin.test.Test
import kotlin.test.assertTrue

class MordantCliProgressJvmTest {
    @Test
    fun spin_ticks_while_work_runs_then_prints_ok() {
        val recorder = TerminalRecorder(
            width = 80,
            height = 24,
            outputInteractive = true,
            inputInteractive = true,
            supportsAnsiCursor = true,
        )
        val terminal = Terminal(terminalInterface = recorder)
        val progress = MordantCliProgress(terminal)

        progress.spin(
            loading = "Slow work",
            done = { "Slow work done" },
        ) {
            Thread.sleep(350)
            "ok"
        }
        val out = recorder.stdout() + recorder.stderr()
        assertTrue(out.contains("✅ Slow work done"), "output was: $out")
        assertTrue(out.contains("Slow work"), "expected loading text, got: $out")
        val braille = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
        val ticksSeen = out.count { c -> c in braille }
        assertTrue(ticksSeen >= 1, "expected at least one braille spinner glyph, got $ticksSeen in: $out")
    }
}
