# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project aims to follow Semantic Versioning.

## [Unreleased]

## [0.1.20] - 2026-02-16

### Changed
- Expanded sound-to-haptics mapping to cover more world interactions (doors/containers/buttons/levers), thunder, totem, and boss cues.
- Large sound events (explosions/thunder) now scale intensity by distance to reduce unrealistic full-strength rumbles from far away.

## [0.1.19] - 2026-02-16

### Fixed
- Damage burst haptics now triggers at the moment the player is hurt (event-timed), rather than waiting for tick-based health polling.

## [0.1.18] - 2026-02-16

### Changed
- Softened footstep haptics to feel less punchy/abrupt (crunchier, longer, and avoids multi-step bursts in one tick).

## [0.1.17] - 2026-02-16

### Fixed
- Adjusted grounded footstep haptics cadence to avoid a double-step feel while walking.

## [0.1.16] - 2026-02-16

### Added
- Grounded footstep haptics that emit short pitter-patter pulses while walking on blocks (no continuous engine-like rumble).

### Changed
- Block mining haptics now sync to the on-screen arm swing timing (and disables the legacy periodic mining pulse by default).

## [0.1.15] - 2026-02-16

### Changed
- Output device selection now opens in its own screen (no overlapping dropdown in the main config screen).

## [0.1.14] - 2026-02-16

### Fixed
- Fixed output device dropdown selection when the list overlaps other controls (dropdown now gets mouse input priority while open).

## [0.1.13] - 2026-02-16

### Changed
- Moved per-effect volume sliders (and their Test buttons) into a new Advanced settings screen to keep the main config screen simple.

### Added
- Timing controls in Advanced settings (durations/cooldowns/periods for relevant effects).

## [0.1.12] - 2026-02-16

### Added
- Per-effect volume sliders in the config screen, each with a Test button directly underneath to preview the effect.

## [0.1.11] - 2026-02-16

### Changed
- Adjusted defaults to feel less like a vehicle: road texture and accel bump are now OFF by default.
- Made the road texture rumble fade in at higher speeds with a gentler ramp (less constant vibration while walking).

## [0.1.10] - 2026-02-15

### Changed
- Output device selection is now a dropdown list (click-to-expand) instead of a cycle button.

## [0.1.9] - 2026-02-15

### Removed
- Removed the speed tone feature entirely (audio generation, config keys, and UI toggle).

## [0.1.8] - 2026-02-15

### Changed
- Softened transient effects to be less punchy by smoothing impulse envelopes and low-passing noise components (damage burst + impulse noise).

## [0.1.7] - 2026-02-15

### Fixed
- Improved audio output device selection on Windows by using a more unique device id (name + description) with legacy-name fallback, and added a safe fallback to default output if opening the chosen device fails.

## [0.1.6] - 2026-02-15

### Fixed
- Reworked the in-game config screen layout to prevent overlapping controls on smaller UI scales (toggles arranged in a two-column grid).

## [0.1.5] - 2026-02-15

### Fixed
- Prevented additional client crashes in sound haptics when a sound instance is dispatched with an unresolved backing sound (guarded source/volume access during `PlaySoundEvent`).

## [0.1.4] - 2026-02-15

### Fixed
- Prevented a client crash in sound haptics when a sound instance is dispatched with an unresolved backing sound (null-safe `getLocation()` during `PlaySoundEvent`).

## [0.1.3] - 2026-02-15

### Added
- Gameplay haptics layer (attack/use click edges, mining pulse, XP gain) mixed into the shaker audio.
- In-game config toggle for gameplay haptics.

## [0.1.2] - 2026-02-15

### Added
- Sound-to-haptics translation (client-side sound events mapped into tactile impulses).
- Generic impulse effect in the audio engine to support non-movement events.
- In-game config toggle for sound haptics.

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
