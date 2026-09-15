# Bass Shaker Telemetry (Forge 1.20.1 / Fabric 26.2)

Bass Shaker Telemetry is a Minecraft mod that turns supported gameplay events into a **dedicated tactile audio stream** (JavaSound) for bass shakers / tactile transducers.

The existing Forge project remains the feature-complete Minecraft 1.20.1 edition. A sibling Fabric project is provided under `fabric/` and targets verified Minecraft 26.2 coordinates.

## Platform targets

| Platform | Project | Verified coordinates | Current scope |
| --- | --- | --- | --- |
| Forge | repository root | Minecraft `1.20.1`, Forge `47.2.0`, Java `17` | Existing feature-complete Forge lifecycle, audio, config, UI, event, networking, and relay implementation |
| Fabric | `fabric/` | Minecraft `26.2`, Fabric Loader `0.19.5`, Fabric API `0.160.0+26.2`, Java `25` | Shared JavaSound/DSP/config/profile/instrument runtime with Fabric lifecycle, tick telemetry, state-delta damage, movement/mining hooks, bounded loopback WebSocket output, and a keybound core config screen; sound interception and server relay are not yet ported |
| NeoForge | `neoforge/` | Minecraft `26.2`, NeoForge `26.2.0.88`, Java `25` | Shared JavaSound/DSP/config/profile/instrument runtime with NeoForge client tick telemetry, state-delta damage, movement/mining hooks, and bounded loopback WebSocket output; Forge event parity and server relay are not yet ported |

Forge has no published Minecraft 26.2 artifact. A Minecraft 26.2 Forge build cannot be produced from the official Forge Maven; NeoForge `26.2.0.88` is provided as the separate 26.2 Forge-compatible loader target.

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
- **Misc** is paged (Prev/Next) across two pages: one for Sound haptics / Gameplay haptics / Biome chime, and one that combines Accessibility HUD with the utility tools (latency test pulse, debug overlay toggle, demo runner).
- **Movement settings** groups movement-related tuning in one place: Movement texture master toggle, Flight/Air/Swim/Water sliders, plus Footsteps and Mounted footsteps volume controls (set either volume to `0` to disable).
- Advanced settings (paged with Prev/Next) now keeps only advanced-only controls: movement texture / accel / mining effect volumes that are not exposed elsewhere, JavaSound buffer tuning, calibration tones/sweep, and a haptic instrument graph editor (Phase 2).
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

GitHub Actions also verifies that every `lang/*.json` file stays structurally in sync with `lang/en_us.json`, and it fails the workflow if locale files drift or if any translation-target locale still appears to be mostly English fallback text. English-variant and novelty locales are exempt from the translation-coverage gate, but they are still required to stay structurally synced.
The newly detected Minecraft locale codes `cv_cu`, `fr_ch`, `go_fr`, `got_de`, `uz_uz`, and `vro` are currently seeded English templates with no verified translation source. They are explicitly exempt from translation-coverage scoring while remaining structurally validated.

Important: do **not** run local Gradle builds or `runClient` on this machine.

The CI toolchains are intentionally separated: the retained Forge 1.20.1 project uses its Gradle 8.5 wrapper, Fabric 26.2 uses Gradle 9.5 for its pinned Loom 1.17.21 plugin, and NeoForge 26.2 uses its own Gradle 9.2 wrapper.

## Fabric adapter boundary

The Fabric sibling initializes through Fabric Loader, loads the same config/profile/instrument files as Forge, starts the shared JavaSound/DSP engine, and registers a client end-tick callback plus a keybound config/test screen through verified Fabric API hooks. The Fabric tick adapter provides movement telemetry, damage/death detection from client state, landing/footstep/mining pulses, and WebSocket telemetry emission through the shared neutral output sink.

The following features are explicitly omitted from the Fabric artifact in this release because this repository does not contain a verified 26.2 Fabric hook/contract for them: sound-play interception, server-authoritative relay packets, the Forge config-screen integration point, Forge overlays, and the Forge event-bus handlers. The Fabric screen is intentionally limited to the verified core enable/test controls; it edits the same full config object and does not invent a parallel settings schema. Sulfur Caves, Sulfur Cube, Geyser, and Vulkan hooks remain omitted because no verified 26.2 mapped APIs were found.

The supplied Sulfur Caves, Sulfur Cube, Geyser, and Vulkan snippets are also not part of this release: no corresponding verified 26.2 mapped mechanics were found, so no hooks were added.
