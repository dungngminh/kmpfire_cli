package io.github.kmpfire.cli

/**
 * Live CLI progress (spinners / status lines).
 * Tests can use [NoopCliProgress] and assert on returned messages instead.
 */
interface CliProgress {
    /** True when this progress already prints to the terminal (skip re-printing messages). */
    val printsLive: Boolean

    fun info(message: String)
    fun ok(message: String)
    fun warn(message: String)
    fun fail(message: String)

    /**
     * Show loading for [loading], run [block], clear loading, then print success via [done].
     * Never leaves both a loading line and a success line on screen.
     */
    fun <T> spin(
        loading: String,
        done: (T) -> String = { loading },
        block: () -> T,
    ): T
}

object NoopCliProgress : CliProgress {
    override val printsLive: Boolean = false
    override fun info(message: String) = Unit
    override fun ok(message: String) = Unit
    override fun warn(message: String) = Unit
    override fun fail(message: String) = Unit
    override fun <T> spin(loading: String, done: (T) -> String, block: () -> T): T = block()
}

/**
 * Non-TTY / CI: one line per step (final status only — no leading loading line).
 */
class PrintCliProgress(
    private val println: (String) -> Unit,
) : CliProgress {
    override val printsLive: Boolean = true

    override fun info(message: String) = println("ℹ️  $message")
    override fun ok(message: String) = println("✅ $message")
    override fun warn(message: String) = println("⚠️  $message")
    override fun fail(message: String) = println("❌ $message")

    override fun <T> spin(loading: String, done: (T) -> String, block: () -> T): T {
        return try {
            val result = block()
            println("✅ ${done(result)}")
            result
        } catch (e: Throwable) {
            println("❌ $loading")
            throw e
        }
    }
}
