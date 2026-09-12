package io.github.kmpfire.cli

import com.github.ajalt.mordant.animation.Animation
import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Interactive progress: Mordant [textAnimation] spinner (caller-thread ticks), then ✅ / ❌.
 *
 * Spinner frames must be single BMP code points (braille). Multi-codepoint emoji
 * (e.g. moon phases) break when indexed as [Char] and show as � in many terminals.
 */
class MordantCliProgress(
    private val terminal: Terminal,
) : CliProgress {
    override val printsLive: Boolean = true

    private val canSpin: Boolean
        get() = terminal.terminalInfo.outputInteractive

    override fun info(message: String) {
        terminal.println("ℹ️  $message")
    }

    override fun ok(message: String) {
        terminal.println("✅ $message")
    }

    override fun warn(message: String) {
        terminal.println("⚠️  $message")
    }

    override fun fail(message: String) {
        terminal.println("❌ $message")
    }

    override fun <T> spin(loading: String, done: (T) -> String, block: () -> T): T {
        if (!canSpin) {
            return PrintCliProgress(terminal::println).spin(loading, done, block)
        }

        // Braille dots: each is one Char (BMP). Avoid emoji surrogate pairs.
        val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
        var tick = 0
        val animation: Animation<Int> = terminal.textAnimation { frame ->
            "${frames[frame % frames.size]} $loading"
        }

        animation.update(0)
        return try {
            val result = BackgroundTicker.whileRunning(
                intervalMs = 80,
                onTick = {
                    tick += 1
                    animation.update(tick)
                },
                block = block,
            )
            animation.clear()
            ok(done(result))
            result
        } catch (e: Throwable) {
            animation.clear()
            fail(loading)
            throw e
        }
    }
}

fun cliProgressFor(terminal: Terminal, quiet: Boolean = false): CliProgress {
    if (quiet) return NoopCliProgress
    if (!terminal.terminalInfo.outputInteractive) {
        return PrintCliProgress { terminal.println(it) }
    }
    return MordantCliProgress(terminal)
}
