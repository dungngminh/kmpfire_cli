import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalMainFunctionArgumentsDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.nomisrev"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    js {
        nodejs {
            @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
            passCliArgumentsToMainFunction()
        }

        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs {
            @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
            passCliArgumentsToMainFunction()
        }

        binaries.executable()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
        binaries {
            executable {
                mainClass = "io.github.nomisrev.MainKt"
            }
        }
        mainRun {
            mainClass = "io.github.nomisrev.MainKt"
        }
    }

    listOf(macosX64(), linuxX64(), linuxArm64(), macosArm64(), mingwX64()).forEach { target ->
        target.binaries {
            executable {
                entryPoint = "io.github.nomisrev.main"
                baseName = "MainKt"
            }
        }
    }
}
