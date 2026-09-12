# AGENTS.md — kmpfire_cli

Guidance for AI agents working in this repository.

## What this repo is

`kmpfire_cli` ships the **`kmpfire`** command — a FlutterFire-style CLI for **Kotlin Multiplatform / Compose Multiplatform**.
It wraps the **Firebase CLI** (`firebase-tools`, `--json`) to register Android/iOS apps and write
`google-services.json` / `GoogleService-Info.plist`. It does **not** generate `DefaultFirebaseOptions`
or Kotlin Firebase options source.

Package: `io.github.kmpfire`  
Project: `kmpfire_cli` · Binary / command: `kmpfire` (Kotlin/Native per OS/arch; JVM for local/dev)

## Read first

- Spec: `docs/superpowers/specs/2026-09-11-kmpfire-cli-design.md`
- Plan: `docs/superpowers/plans/2026-09-11-kmpfire-cli-implementation.md`
- User-facing usage: `README.md`
- Skills: `.cursor/skills/kmpfire/` (use CLI in a KMP app) and `.cursor/skills/kmpfire-cli/` (change this repo)

## Architecture map

```
src/commonMain/kotlin/io/github/kmpfire/
  commands/          Clikt: configure | reconfigure
  configure/         ConfigureService, ReconfigureService, models
  firebase/          FirebaseCli, FirebaseClient, FirebaseAppProvisioner
  project/           ProjectDetector, parsers (applicationId / bundle id)
  config/            StateStore → firebase.json (`kotlinMultiplatform` section), ConfigWriter
  gradle/            GradleDepsPatcher (--deps only)
  xcode/             XcodePbxPatcher (plist → Resources)
  fs/                Fs, Paths, Cwd (expect/actual)
  process/           ProcessRunner (expect/actual)
```

JVM/native actuals live under `src/jvmMain` and `src/nativeMain`.

## Non-negotiables (v1)

- Thin wrapper around `firebase` CLI — no Firebase Management REST in-tree
- Platforms: **Android + iOS only** (no desktop/web)
- `firebase.json` → `"kotlinMultiplatform"` stores **projectId** + platforms (`default` + `buildConfigurations` flavors); **project-relative** `fileOutput` paths
- Merge only the `"kotlinMultiplatform"` object (parallel to FlutterFire `"flutter"`); never wipe sibling keys (`hosting`, `flutter`, …)
- Interactive: if `kotlinMultiplatform` already present → FlutterFire-style reuse confirm; `--yes` auto-reuses
- Note: Firebase CLI JSON schema does not officially list `flutter`/`kotlinMultiplatform`; rare strict validation may warn — same trade-off FlutterFire accepted
- Gradle deps only with **`--deps`**
- Soft-fail Gradle/`pbxproj` patches: warn, do not abort successful config writes
- Keep Clikt commands thin; put logic in `*Service` classes

## Layouts to support

| Kind | Signal |
|---|---|
| `new-template` | `shared/` + `androidApp/` + `iosApp/` |
| `app-nested` | `app/shared/` + `app/androidApp/` + `app/iosApp/` |
| `legacy-composeApp` | `composeApp/` + `iosApp/` |

Google Services JSON applies on **androidApp only**, never on `shared`.

## Commands (shipped)

| Command | Role |
|---|---|
| `kmpfire configure` | Interactive project + platform select (or flags); find/create apps → sdkconfig → files → `firebase.json` (`kotlinMultiplatform`) |
| `kmpfire reconfigure` | Rewrite service files from `firebase.json` (`kotlinMultiplatform`) |
| `kmpfire --version` / `-V` | Print generated `AppVersion.VALUE` (= Gradle `project.version`) |

Interactive (flutterfire-like): omit `--platforms` / `--project` → multi-select platforms + project picker (`<create a new project>`). `--yes` skips prompts (requires `--project`). Missing project id → confirm create, or `--create-project`.

## Dev commands

```bash
./gradlew jvmTest
./gradlew jvmRun --args='configure --help'
./gradlew linkReleaseExecutableMacosArm64
```

Prefer `jvmTest` for fast feedback. Native link when changing process/fs actuals.

## Coding conventions

- Match existing package layout and naming
- New Firebase CLI ops go through `FirebaseClient` / `FirebaseCli.runJson`
- Reuse `FirebaseAppProvisioner` for find-or-create apps (do not duplicate list/create)
- When updating only one platform/flavor, **merge** into existing `firebase.json` `kotlinMultiplatform` section (do not null out the other)
- Live step messages: `…` while working, `✓` / created vs reused when done
- Bump `version` in `build.gradle.kts` when cutting a release (`AppVersion` is generated from it)
- Do not commit unless the user asks

## Tests

- Put shared unit tests in `src/commonTest` when pure Kotlin
- Filesystem / Process tests that need temp dirs: `src/jvmTest`
- Fake `FirebaseClient` in tests — do not call real Firebase in unit tests

## Release / install packaging

| Piece | Role |
|---|---|
| `install.sh` | curl install from GitHub Releases → `~/.local/bin/kmpfire` |
| `.github/workflows/build.yml` | CI: `jvmTest` + native link on push/PR |
| `.github/workflows/release.yml` | Tag `v*`: native tar.gz assets + GitHub Release |
| `.github/workflows/deploy-homebrew.yml` | On release published → push formula to `dungngminh/homebrew-kmpfire_cli` |
| `.github/homebrew/kmpfire.rb.template` | Formula template (`{{VERSION}}`, SHA placeholders) |
| `packaging/homebrew-kmpfire_cli/` | Scaffold to publish as the tap repo |

Release assets: `kmpfire-macos-arm64.tar.gz`, `kmpfire-macos-x64.tar.gz`, `kmpfire-linux-x64.tar.gz` (+ `checksums.txt`).

Secret: `HOMEBREW_TAP_TOKEN` (PAT, push to tap).

## Out of scope unless asked

- Desktop / Web Firebase targets
- Generating Kotlin `FirebaseOptions`
- Auto-adding Crashlytics / Analytics SPM packages
- Scoop packaging
