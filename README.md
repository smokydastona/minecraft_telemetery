# Bass Shaker Telemetry (Forge 1.20.1 / Java 17)

Bass Shaker Telemetry is a Forge mod that turns Minecraft gameplay events into a **dedicated tactile audio stream** (JavaSound) for bass shakers / tactile transducers.

The current design goal is **"encoded mono surround"** for stereo output: direction is encoded into *one* vibration waveform using small frequency bias + micro-delay, while a **priority + ducking** mixer ensures one dominant vibration stays readable.

The mod also supports an optional **Sound Scape (7.1)** mode that routes haptic categories across up to **8 output channels** (FL/FR/C/LFE/SL/SR/BL/BR) so you can drive multiple transducers.

## What it does

- **Tactile audio output**: 48kHz 16‑bit PCM, routed to a selectable output device.
	- Default: stereo output (mono mix duplicated to L/R for compatibility; directional impulses can optionally pan L/R when Spatial is enabled).
	- Optional: 7.1 (8ch) output in **Sound Scape** mode, with category/group routing.
- **Priority & ducking (non-optional)**: when multiple effects overlap, one dominant vibration wins; others are ducked to keep impacts clear.
- **Encoded-mono direction**: when a source position is known and a profile is `directional: true`, the mod selects `front/rear/left/right` encoding bands and applies:
	- `frequencyBiasHz` (small Hz offset)
	- `timeOffsetMs` (small micro-delay)
	- `intensityMul` (optional gain multiplier)

For DSP-backed `instrument` playback, directional feel is handled inside the instrument graph via the `direction` node (recommended: `useProfileEncoding: true` and `band: "auto"`).

## Signal sources (high level)

- **Telemetry-driven layers** (optional): movement texture (land/flight/swim, speed-scaled), accel bump, biome chime.
- **Event impulses**: damage (directional when a source is known), danger ticks (fire/drowning/poison/wither), death rumble.
- **Flight wind (Elytra)**: low rumble impulses while gliding that shift left/right as you turn (key: `flight.wind`).
- **Swim wind**: low rumble impulses while swimming/in water that shift left/right as you turn (key: `swim.wind`).
- **Mounted haptics**: ground mounts emit hoof “clump” pulses (key: `mount.hoof`); flying mounts swap to `flight.wind` while airborne.
- **Warden heartbeat**: directional heartbeat pulses that follow the **actual in-game Warden heartbeat sound timing** (key: `boss.warden_heartbeat`), louder when closer and intentionally quieter than damage.
- **Client-only sound haptics**: infers impulses from `PlaySoundEvent` (explosions, thunder, hurt, break/place, steps, attacks, doors/containers/buttons/levers, etc.). These now also participate in encoded-mono direction using the sound instance position.
- **Gameplay haptics (non-sexual)**: attack/use clicks, mining pulse, XP gains.
- **Footsteps / mining swing**: short pulses tuned for readability (no constant “engine rumble”).

## Multiplayer / timing

Some events are optionally hooked server-side and relayed to the client via a small packet so timing and source position are accurate in multiplayer.

Note: server-relayed events require the mod on **both** the server and the client.

## Configuration

In-game:

- Minecraft main menu → **Mods** → **Bass Shaker Telemetry** → **Config**
- Game sounds device selection (Minecraft audio output), haptics output device selection (JavaSound), master volume, then page buttons: **Damage / Movement / Misc / Advanced**.
- **Damage** includes incoming damage controls plus outgoing hit-confirm (server-relayed `combat.hit`) and melee hit tuning.
- **Misc** is paged (Prev/Next) and groups non-movement toggles/volumes (Sound haptics, Gameplay haptics, Biome chime, Accessibility HUD) plus a Tools page (latency test pulse, debug overlay toggle, demo runner).
- **Movement settings** groups movement-related tuning in one place: Movement texture master toggle, Flight/Air/Swim/Water sliders, plus Footsteps and Mounted footsteps controls (set Footsteps volume to 0 to disable).
- Advanced settings (paged with Prev/Next) includes an output buffer size selector (JavaSound backend) to tune **latency vs stability**, calibration tones/sweep, and a haptic instrument graph editor (Phase 2).
- Advanced settings also includes a **Spatial** section (Phase 3) for Sound Scape: spatial panning toggle, distance attenuation, a guided per-channel calibration wizard (gain + simple EQ, burst test, RMS auto-trim, comfort limit capture), and a real-time spatial debugger (meters/waveform/spectrogram/timeline/latency).
- Each effect slider includes a **Test** button.
- **Sound Scape (7.1)**: category routing + group management for mapping haptics across multiple output channels. If no multichannel device is available, the UI restricts routing choices to stereo.
	- Includes an optional per-effect overrides editor (debug key → target).

UI bundle (Neon skin):

- The config screens can load their **style/assets** (and optional **screen schema**) from a UI bundle.
- Neon screens auto-reload the UI bundle when opened.
- All in-game config pages use the Neon theme (including device picker, Sound Scape editors, and Spatial screens).
- Bundle folders:
	- Disk override: `config/bassshakertelemetry/ui_bundle/`
	- Disk remote (auto-updated): `config/bassshakertelemetry/ui_bundle_remote/`

For the full UI bundle and schema details, see `docs/MOD_FEATURES.md`.

Optional integrations:

- WebSocket telemetry output (JSON) can be enabled via config keys (client-only).

On disk:

- Main config: `config/bassshakertelemetry.json`
- Vibration profiles: `config/bassshakertelemetry_vibration_profiles.json`
- Haptic instruments (Phase 2 DSP patches): `config/bassshakertelemetry_haptic_instruments.json`

Advanced keys in `bassshakertelemetry.json` include:

- `audioBackend` (currently `javasound`; other ids are reserved for future backends)

Profiles are the source of truth for per-event tuning (frequency, intensity, duration, noise mix, pattern), plus:

- `priority` (0..100)
- `directional` (boolean)
- `instrument` (string, optional): references a reusable haptic instrument from `bassshakertelemetry_haptic_instruments.json`
- root-level `encoding` bands

For the full feature reference, see `docs/MOD_FEATURES.md`.

For other mods, a small public integration API is available under `com.smoky.bassshakertelemetry.api`.

Hardware tuning + troubleshooting guide: `docs/HARDWARE_TUNING_GUIDE.md`.

When filing bugs, please include a debug overlay capture (enable via Misc → Tools) and the JavaSound buffer (requested vs accepted) log lines.

## Latency notes

- The mod is tuned for low latency by default (requested JavaSound buffer defaults to ~20ms, and the engine renders in ~10ms chunks at 48kHz).
- Your actual end-to-end delay still depends on the Windows audio stack + device/driver: some drivers clamp/ignore requested JavaSound buffer sizes.
- If the driver forces an extreme buffer (hundreds of ms+), the mod will log it; for tight sync you may need a different output device/driver path.

## Known limitations (alpha)

- **Audio device variability**: some drivers clamp/ignore requested JavaSound buffer sizes.
- **Overlay meaning**: the overlay shows the last vibration/suppression, not a per-sample “truth” of what you felt.
- **Sound source positions**: some Minecraft sounds have approximate/"artistic" origins; direction encoding can be perceptually off in those cases.

## Builds / workflow

This repo is intended to be validated via editor diagnostics and built by GitHub Actions on `git push`.

GitHub Actions also verifies that every `lang/*.json` file stays structurally in sync with `lang/en_us.json`, and it fails the workflow if locale files drift or if any non-English locale still appears to be mostly English fallback text.

Important: do **not** run local Gradle builds or `runClient` on this machine.
