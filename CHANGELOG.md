# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project aims to follow Semantic Versioning.

## [Unreleased]

### Added
- Sound Scape (7.1) routing: per-category routing UI with editable channel groups (targets up to 8 output channels: FL/FR/C/LFE/SL/SR/BL/BR).
- Sound Scape overrides editor: per-effect (debug key) routing rules that take priority over category routing.
- Optional 7.1 (8-channel) JavaSound output with stereo fallback when multichannel output is unavailable.
- Encoded-mono direction encoding scaffolding in vibration profiles (`encoding` root object + per-profile `directional` + `priority`).
- Priority-aware impulse mixing with ducking so only one vibration is dominant at a time.
- Server-relayed haptics packet now includes source position (enables directional encoding in multiplayer).
- Advanced settings: JavaSound output buffer size selector (helps tune latency/stability).
- Advanced settings: latency test pulse toggle.
- Advanced settings: Output EQ tone shaping (single-band, with freq + gain).
- Advanced settings: Smart Volume auto-level (slow AGC).
- Advanced settings: calibration test tones (30 Hz, 60 Hz) and a 20→120 Hz sweep.
- Advanced settings: calibration Stop/Silence button.
- New impulse pattern: `flat` (sustained tone-friendly envelope).
- Public Mod Integration API (`com.smoky.bassshakertelemetry.api.HapticApi`) so other mods can emit haptic events.
- Optional WebSocket telemetry output (client-only): broadcasts JSON telemetry + haptic events for external tools/hardware.
- WebSocket unified event packets (`type":"event"`) now support real IDs + source/position/metadata via a lightweight context system.
- Directional damage haptics when a source position is known.
- Directional flight wind impulses while gliding (profile key: `flight.wind`).
- Mounted haptics: hoof pulses on ground mounts (profile key: `mount.hoof`) and flying mounts swap to `flight.wind` while airborne.
- Directional Warden heartbeat pulses that follow the in-game heartbeat sound timing, scale with distance, and are capped to stay quieter than damage (profile key: `boss.warden_heartbeat`).
- Ender Dragon sound cues (roar/growl/flap) now produce directional, distance-scaled haptics so the rumble shifts around you as the dragon moves (and are capped to never exceed 90% of damage haptics).
- Phase 2 foundation: DSP node graph runtime and a haptic instrument library file (`config/bassshakertelemetry_haptic_instruments.json`).
- Vibration profiles now support optional `instrument` ids to play DSP-backed instrument impulses.
- Phase 2: in-game haptic instrument graph editor (MVP) under Advanced settings.
- Phase 2 DSP: added `direction` encoder node and a lightweight per-node preview value in the graph editor (visual debugging).
- Phase 2 DSP: `direction` node supports `band: "auto"` to follow event source direction when available.
- Phase 3 Spatial (Sound Scape): azimuth + distance hints are propagated into the audio engine for true multichannel panning.
- Phase 3 Spatial (Sound Scape): optional distance attenuation control.
- Phase 3 Spatial (Sound Scape): optional per-bus routing (`soundScapeBusRouting`) in addition to category/override routing.
- Phase 3 Spatial (Sound Scape): per-transducer calibration (`soundScapeCalibration`) with per-channel gain trim + simple EQ applied post-mix.
- Advanced settings: new **Spatial** screen (toggles + distance attenuation + links to routing, calibration wizard, and spatial debugger).
- Spatial calibration wizard screen with per-channel test tones/sweep/latency pulse and calibration sliders.
- Spatial calibration wizard: burst test button, RMS auto-trim helper, per-channel comfort limit, and a guided Back/Next flow with one-click comfort capture.
- Minimal spatial debugger screen showing last event, azimuth/distance, and engine status.
- Accessibility HUD: optional on-screen cue list for key danger events (damage, explosions, thunder, boss cues, Warden heartbeat) plus a low-health warning, with 8-way directional arrows (←→↑↓↖↗↘↙) that update as you turn.

### Changed
- Default directional instruments (`impact_heavy`, `heartbeat_warden`, `wind_elytra`) now include a `direction` node with `band: "auto"`.
- Network protocol version bumped (client/server must match mod version).
- Audio output now goes through a backend abstraction selected by `audioBackend` (Phase 1 foundation; currently only `javasound` is implemented).
- Impulse ducking is now per-bus (see `HapticBus`); impulses are no longer globally ducked by other one-shot effects, while road texture still ducks under active events.
- Spatial debugger now shows per-channel meters, waveform, low-frequency spectrogram, recent event timeline, and a buffer/queued latency estimate.

### Fixed
- Output device selection now stays on the selected device when 7.1 (8ch) output can’t be opened (falls back to stereo on the same device instead of silently switching to the system default).
- Output device selection UI is no longer gated by 7.1 support (devices won’t revert to `<Default>` just because Sound Scape is enabled).

## [0.1.23] - 2026-02-16

### Added
- Engine-side vibration patterns: `single`, `pulse_loop`, `shockwave`, `fade_out`.
- Optional `pulsePeriodMs` / `pulseWidthMs` fields in vibration profiles (used by `pulse_loop`).
- Server-relayed event haptics (multiplayer-capable) via a small network packet:
	- Explosion detonate (`explosion.generic`, distance-scaled)
	- Block break completion (`world.block_break`)
	- Attacker hit confirmation (`combat.hit`, scaled by damage amount)
	- Fall impact (`damage.fall`, scaled by fall distance)

## [0.1.22] - 2026-02-16

### Added
- JSON vibration profile system: `config/bassshakertelemetry_vibration_profiles.json` (data-driven frequency/intensity/duration per event).

### Changed
- Damage/footsteps/landing/mining-swing haptics now use the vibration profile file for their core tuning.

## [0.1.21] - 2026-02-16

### Added
- Console-style danger feedback: subtle periodic pulses for fire, drowning (low air), poison, and wither.
- One-shot death rumble.

### Fixed
- Damage burst timing now has a client-tick fallback (hurtTime/health) to stay responsive even when event hooks are unreliable.

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
