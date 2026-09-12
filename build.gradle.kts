import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.kmpfire"
version = "0.1.0"

val generateAppVersion = tasks.register("generateAppVersion") {
    val outputDir = layout.buildDirectory.dir("generated/appVersion/commonMain/kotlin")
    val projectVersion = provider { project.version.toString() }
    inputs.property("projectVersion", projectVersion)
    outputs.dir(outputDir)
    doLast {
        val versionValue = projectVersion.get()
        val packageDir = outputDir.get().asFile.resolve("io/github/kmpfire")
        packageDir.mkdirs()
        packageDir.resolve("AppVersion.kt").writeText(
            """
            |package io.github.kmpfire
            |
            |/** Generated from Gradle `project.version` — do not edit. */
            |object AppVersion {
            |    const val VALUE = "$versionValue"
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
        binaries {
            executable {
                mainClass = "io.github.kmpfire.MainKt"
            }
        }
        mainRun {
            mainClass = "io.github.kmpfire.MainKt"
        }
    }

    listOf(macosX64(), linuxX64(), linuxArm64(), macosArm64(), mingwX64()).forEach { target ->
        target.binaries {
            executable {
                entryPoint = "io.github.kmpfire.main"
                baseName = "kmpfire"
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion.map { it.outputs.files.singleFile })
            dependencies {
                implementation(libs.clikt)
                implementation(libs.mordant)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateAppVersion)
}
