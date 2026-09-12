package io.github.kmpfire.fs

actual fun currentWorkingDirectory(): String =
    System.getProperty("user.dir")
        ?: error("Unable to resolve current working directory")
