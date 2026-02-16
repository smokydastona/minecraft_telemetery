# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project aims to follow Semantic Versioning.

## [Unreleased]

## [0.1.1] - 2026-02-15

### Added
- GT7-inspired audio safety/mixing improvements: headroom scaling, soft limiter, and effect ducking.
- Automatic mute/sleep gating when no live telemetry (prevents rumble in menus/paused states).
- Simulated road texture rumble layer and accel-bump one-shot effect.
- In-game config toggle for road texture.

### Fixed
- Null-safety/diagnostic issues in the config screen and client tick handler.

## [0.1.0] - 2026-02-15

### Added
- Initial Forge 1.20.1 mod sources for Bass Shaker Telemetry.
- GitHub repository setup (initial commit on `main`) and CI build workflow.

### Changed
- Added `.gitignore` rules to avoid committing build outputs and local tooling caches.
