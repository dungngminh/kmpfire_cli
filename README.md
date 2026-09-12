<p align="center">
  <img src="art/kmpfire-banner.png" alt="kmpfire" width="400" />
</p>

<h1 align="center">kmpfire_cli</h1>

<p align="center">
  <strong>A CLI helping configure Firebase for Kotlin Multiplatform / Compose Multiplatform applications quickly and easily</strong><br>
  Command: <code>kmpfire</code> — register Android / iOS Firebase apps and write native config from the terminal
</p>

<p align="center">
  <a href="https://github.com/dungngminh/kmpfire_cli/actions/workflows/build.yml"><img src="https://github.com/dungngminh/kmpfire_cli/actions/workflows/build.yml/badge.svg" alt="Build" /></a>
  <a href="https://github.com/dungngminh/kmpfire_cli/releases/latest"><img src="https://img.shields.io/github/v/release/dungngminh/kmpfire_cli" alt="GitHub release" /></a>
  <img src="https://img.shields.io/github/stars/dungngminh/kmpfire_cli" alt="GitHub Repo stars" />
  <a href="LICENSE"><img src="https://img.shields.io/github/license/dungngminh/kmpfire_cli" alt="License" /></a>
</p>

A CLI to help with using [Firebase](https://firebase.google.com/) in your **Kotlin Multiplatform** / **Compose Multiplatform** applications.

It wraps the [Firebase CLI](https://firebase.google.com/docs/cli) (`firebase-tools`) to register Android / iOS apps, write native service files, and track configuration in `firebase.json`.

Autodownload configuration files for Android and iOS apps, and patch the project files to use them.

---

## Requirements

- [Firebase CLI](https://firebase.google.com/docs/cli) installed and logged in (`firebase login`)
- A KMP / CMP project root (`settings.gradle.kts`)
- To **build** this repo: JDK 21+

### Supported layouts

| Layout                   | Paths                                             |
| ------------------------ | ------------------------------------------------- |
| New template             | `shared/` + `androidApp/` + `iosApp/`             |
| Nested `app/` (monorepo) | `app/shared/` + `app/androidApp/` + `app/iosApp/` |
| Legacy                   | `composeApp/` + `iosApp/`                         |

Platforms in v1: **Android + iOS only** (no desktop / web Firebase targets).

---

## Install / run

### curl (recommended)

```bash
curl -fsSL https://raw.githubusercontent.com/dungngminh/kmpfire_cli/main/install.sh | bash
```

Pin a release tag:

```bash
curl -fsSL https://raw.githubusercontent.com/dungngminh/kmpfire_cli/main/install.sh | bash -s -- v0.1.0
```

Installs into `~/.local/lib/kmpfire` and links `~/.local/bin/kmpfire` (adds that dir to your shell `PATH` if needed).

### Homebrew

```bash
brew tap dungngminh/kmpfire_cli
brew install kmpfire
```

Requires the tap repo [`dungngminh/homebrew-kmpfire_cli`](https://github.com/dungngminh/homebrew-kmpfire_cli) (updated on each GitHub Release).

### From a release archive

Download `kmpfire-<os>-<arch>.tar.gz` from [Releases](https://github.com/dungngminh/kmpfire_cli/releases), extract, rename to `kmpfire`, and put it on your `PATH`.

```bash
kmpfire --help
# Footer shows: kmpfire <version>
```

### From source (local development)

```bash
git clone https://github.com/dungngminh/kmpfire_cli.git
cd kmpfire_cli

# Fast JVM path (dev):
./gradlew jvmRun --args='--help'

# Native release binary (example: Apple Silicon):
./gradlew linkReleaseExecutableMacosArm64
./build/bin/macosArm64/releaseExecutable/kmpfire.kexe --help
```

Other native hosts: `MacosX64`, `LinuxX64`, `LinuxArm64`, `MingwX64`.

---

## Default setup

From your KMP project root, run:

```bash
kmpfire configure
```

At a minimum this prompts for a Firebase project and platforms (`android`, `ios`).

No prompts (CI / scripts):

```bash
kmpfire configure --yes --project=<FIREBASE_PROJECT_ID>
```

kmpfire auto-detects:

- Android `applicationId` from the androidApp Gradle file
- iOS bundle id from `project.pbxproj`

If no Firebase app matches, it creates one, then writes:

- `google-services.json` (default under `androidApp/` or nested `app/androidApp/`)
- `GoogleService-Info.plist` (default under `iosApp/…`)
- merges state into `firebase.json` → `"kotlinMultiplatform"`

Override package / bundle / paths when needed:

```bash
kmpfire configure --yes --project=<FIREBASE_PROJECT_ID> \
  --android-package-name=<ANDROID_PACKAGE_NAME> \
  --ios-bundle-id=<IOS_BUNDLE_ID> \
  --android-out=androidApp/google-services.json \
  --ios-out=iosApp/iosApp/GoogleService-Info.plist
```

Limit platforms:

```bash
kmpfire configure --yes --project=<FIREBASE_PROJECT_ID> --platforms=android,ios
```

Create the Firebase project if the id is missing from your account:

```bash
kmpfire configure --yes --project=<NEW_PROJECT_ID> --create-project
```

### `--yes` flag

- Uses detected platforms when `--platforms` is omitted
- Requires `--project` (unless reusing an existing `kotlinMultiplatform` section)
- Auto-reuses values from an existing `firebase.json` `kotlinMultiplatform` section when present

### `--project` flag

Selects the Firebase project and skips the project picker prompt.

### Reuse existing `firebase.json`

If `firebase.json` already has a `kotlinMultiplatform` section, interactive `configure` asks whether to reuse those values (project, platforms, package/bundle, outs). Answer yes to prefill; `--yes` reuses automatically.

---

## Multi build configuration / flavors

**Important:** kmpfire does not auto-detect flavors. Run `configure` once per flavor with explicit flags.

Use `--flavor=<name>` to store the entry under `platforms.*.buildConfigurations.<name>` (default entry is `platforms.*.default`).

### Android

```bash
kmpfire configure --platforms=android \
  --flavor=dev \
  --android-package-name=your.application.id.dev \
  --android-out=androidApp/src/dev/google-services.json \
  --project=<FIREBASE_PROJECT_ID> --yes
```

Place each `google-services.json` where the Google Services plugin expects it for that flavor/build type. Match the Firebase Android app’s application id to that flavor.

**Important:** pass `--android-package-name` for flavors; otherwise kmpfire detects the default `applicationId` only.

### iOS

```bash
kmpfire configure --platforms=ios \
  --flavor=Debug-dev \
  --ios-bundle-id=your.bundle.id.dev \
  --ios-out=iosApp/config/dev/GoogleService-Info.plist \
  --project=<FIREBASE_PROJECT_ID> --yes
```

**Important:** pass `--ios-bundle-id` for flavors; otherwise kmpfire detects the default bundle id from `pbxproj` only. iOS plist writes / Xcode patching are best-effort from a macOS environment.

### Example: debug + release style paths

```bash
# debug / default
kmpfire configure --yes --project=your-project-id --platforms=android,ios \
  --android-out=androidApp/src/debug/google-services.json \
  --android-package-name=com.example.myapp.debug \
  --ios-out=iosApp/debug/GoogleService-Info.plist \
  --ios-bundle-id=com.example.myapp.ios.debug

# release flavor
kmpfire configure --yes --project=your-project-id --platforms=android,ios \
  --flavor=release \
  --android-out=androidApp/src/release/google-services.json \
  --android-package-name=com.example.myapp.release \
  --ios-out=iosApp/release/GoogleService-Info.plist \
  --ios-bundle-id=com.example.myapp.ios.release
```

---

## `kmpfire reconfigure`

After changing Firebase products in the console (or to refresh configs), rewrite service files from stored paths:

```bash
kmpfire reconfigure
```

This uses `firebase.json` → `kotlinMultiplatform` to update each `fileOutput` in place (all `default` + `buildConfigurations` entries). Limit to one flavor:

```bash
kmpfire reconfigure --flavor=dev
```

Optional Gradle patch (Google Services on androidApp; GitLive on shared when `--sdk=gitlive`):

```bash
kmpfire reconfigure --deps
# or on first configure:
kmpfire configure --yes --project=<ID> --deps --sdk=gitlive
```

---

## State: `firebase.json` → `kotlinMultiplatform`

kmpfire stores configuration in `firebase.json` under a `kotlinMultiplatform` section.

```json
{
  "kotlinMultiplatform": {
    "projectId": "my-firebase",
    "sdk": "gitlive",
    "layoutKind": "app-nested",
    "platforms": {
      "android": {
        "default": {
          "appId": "1:…:android:…",
          "fileOutput": "app/androidApp/google-services.json",
          "packageNameOrBundleId": "com.example.app"
        },
        "buildConfigurations": {
          "dev": {
            "appId": "1:…:android:dev",
            "fileOutput": "app/androidApp/src/dev/google-services.json",
            "packageNameOrBundleId": "com.example.app.dev"
          }
        }
      },
      "ios": {
        "default": {
          "appId": "1:…:ios:…",
          "fileOutput": "app/iosApp/iosApp/GoogleService-Info.plist",
          "packageNameOrBundleId": "com.example.app"
        }
      }
    }
  }
}
```

`fileOutput` paths are **relative** to the project root.

---

## Useful flags

| Flag                          | Meaning                                           |
| ----------------------------- | ------------------------------------------------- |
| `--project` / `-P`            | Firebase project id                               |
| `--project-dir`               | KMP root (directory with `settings.gradle.kts`)   |
| `--platforms=android,ios`     | Platforms (omit → interactive multi-select)       |
| `--flavor`                    | Named `buildConfiguration` (omit → `default`)     |
| `--yes` / `-y`                | Non-interactive defaults / reuse existing section |
| `--create-project`            | Create Firebase project when id is missing        |
| `--sdk gitlive\|native`       | Affects `--deps` / stored in state                |
| `--deps`                      | Patch Gradle plugins / deps                       |
| `--android-package-name`      | Override applicationId                            |
| `--ios-bundle-id`             | Override PRODUCT_BUNDLE_IDENTIFIER                |
| `--android-out` / `--ios-out` | Custom service file paths                         |
| `--dry-run`                   | Print actions only                                |
| `--version` / `-V`            | Show CLI version                                  |

---

## Caveats

- Prefer native service files for Android (`google-services.json`) and iOS (`GoogleService-Info.plist`). kmpfire intentionally does **not** generate Kotlin `FirebaseOptions` source.
- Google Services Gradle plugin applies on **androidApp only**, never on `shared`.
- `--deps` is opt-in; Gradle / `pbxproj` patches soft-fail (warn, do not abort successful config writes).
- Firebase CLI’s published JSON schema does not list `kotlinMultiplatform`; rare strict validation may warn.
- iOS configuration assumes a macOS host for sensible Xcode / plist workflow.

---

## Contributing

```bash
git clone https://github.com/dungngminh/kmpfire_cli.git
cd kmpfire_cli
./gradlew jvmTest
./gradlew jvmRun --args='configure --help'
```

1. Fork this repository
2. Create a branch and make your changes
3. Open a Pull Request

---

## License

MIT — see [LICENSE](LICENSE)
