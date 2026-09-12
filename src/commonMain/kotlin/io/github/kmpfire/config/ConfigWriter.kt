package io.github.kmpfire.config

import io.github.kmpfire.fs.Fs

class ConfigWriter(
    private val fs: Fs = Fs,
) {
    fun writeServiceFile(path: String, contents: String) {
        fs.writeText(path, contents)
    }
}
