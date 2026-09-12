# Changelog

## [0.2.0] - 2026-09-13

### Fixed

- Detect iOS bundle id from Compose Multiplatform `*.xcconfig` (resolve `$(TEAM_ID)`). Wizard templates that only set `PRODUCT_BUNDLE_IDENTIFIER` in `Config.xcconfig` no longer fail `kmpfire configure`.

## [0.1.0] - 2026-09-12

### Added

- Initial `kmpfire` CLI: `configure` / `reconfigure`, Android + iOS Firebase apps, `firebase.json` `kotlinMultiplatform` state.
