# Kotlin Multiplatform CLI Template

This project shows how to create a Kotlin Multiplatform CLI application that runs on all JVM, native targets, and
NodeJS.
It has no dependencies and is ready to be used as a template on top of your favorite KMP libraries and frameworks.

## Build & Run Targets

### Native

```console
./gradlew linkReleaseExecutableMacosArm64
build/bin/macosArm64/releaseExecutable/MainKt.kexe one two three
```

```text
Hello KMP: one, two, three
```

### NodeJS

```console
./gradlew compileProductionExecutableKotlinJs
node build/compileSync/js/main/productionExecutable/kotlin/kmp-cli-template.js one two three
```

```text
Hello KMP: one, two, three
```

### Jvm

```console
./gradlew distZip
cd build/distributions
unzip kmp-cli-template-1.0-SNAPSHOT-jvm.zip 
cd kmp-cli-template-1.0-SNAPSHOT-jvm
./bin/kmp-cli-template one two three
```

```text
Hello KMP: one, two, three
```

## Running from Gradle directly

```console
./gradlew jvmRun --args='one two three'
```
