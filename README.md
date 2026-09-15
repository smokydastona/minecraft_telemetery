# Bass Shaker Telemetry (Forge 1.20.1 / Fabric 26.2)

Bass Shaker Telemetry is a Minecraft mod that turns supported gameplay events into a **dedicated tactile audio stream** (JavaSound) for bass shakers / tactile transducers.

The existing Forge project remains the feature-complete Minecraft 1.20.1 edition. A sibling Fabric project is provided under `fabric/` and targets Minecraft 26.2's unobfuscated official-name toolchain.

## Platform targets

| Platform | Project | Verified coordinates | Current scope |
| --- | --- | --- | --- |
| Forge | repository root | Minecraft `1.20.1`, Forge `47.2.0`, Java `17` | Existing feature-complete Forge lifecycle, audio, config, UI, event, networking, and relay implementation |
| Fabric | `fabric/` | Minecraft `26.2`, Fabric Loader `0.19.5`, Fabric API `0.160.0+26.2`, Loom `1.17-SNAPSHOT`, Gradle `9.5.1`, Java `25`, no mappings declaration | Shared JavaSound/DSP/config/profile/instrument runtime with Fabric lifecycle, tick telemetry, state-delta damage, movement/mining hooks, bounded loopback WebSocket output, and a keybound core config screen; sound interception and server relay are not yet ported |
| NeoForge | `neoforge/` | Minecraft `26.2`, NeoForge `26.2.0.88`, Java `25` | Shared JavaSound/DSP/config/profile/instrument runtime with NeoForge client tick telemetry, state-delta damage, movement/mining hooks, and bounded loopback WebSocket output; Forge event parity and server relay are not yet ported |

Forge has no published Minecraft 26.2 artifact. A Minecraft 26.2 Forge build cannot be produced from the official Forge Maven; NeoForge `26.2.0.88` is provided as the separate 26.2 Forge-compatible loader target.

The current design goal is **"encoded mono surround"** for stereo output: direction is encoded into *one* vibration waveform using small frequency bias + micro-delay, while a **priority + ducking** mixer ensures one dominant vibration stays readable.

The mod also supports an optional **Sound Scape (7.1)** mode that routes haptic categories across up to **8 output channels** (FL/FR/C/LFE/SL/SR/BL/BR) so you can drive multiple transducers.

## What it does

	- Default: stereo output (mono mix duplicated to L/R for compatibility; directional impulses can optionally pan L/R when Spatial is enabled).
	- Optional: 7.1 (8ch) output in **Sound Scape** mode, with category/group routing.
	- `frequencyBiasHz` (small Hz offset)
	- `timeOffsetMs` (small micro-delay)
	- `intensityMul` (optional gain multiplier)

For DSP-backed `instrument` playback, directional feel is handled inside the instrument graph via the `direction` node (recommended: `useProfileEncoding: true` and `band: "auto"`).

## Signal sources (high level)


## Multiplayer / timing

Some events are optionally hooked server-side and relayed to the client via a small packet so timing and source position are accurate in multiplayer.

Note: server-relayed events require the mod on **both** the server and the client.

## Configuration

In-game:

	- Includes an optional per-effect overrides editor (debug key → target).

UI bundle (Neon skin):

	- Disk override: `config/bassshakertelemetry/ui_bundle/`
	- Disk remote (auto-updated): `config/bassshakertelemetry/ui_bundle_remote/`

For the full UI bundle and schema details, see `docs/MOD_FEATURES.md`.

Optional integrations:


On disk:


Advanced keys in `bassshakertelemetry.json` include:


Profiles are the source of truth for per-event tuning (frequency, intensity, duration, noise mix, pattern), plus:


For the full feature reference, see `docs/MOD_FEATURES.md`.

For other mods, a small public integration API is available under `com.smoky.bassshakertelemetry.api`.

Hardware tuning + troubleshooting guide: `docs/HARDWARE_TUNING_GUIDE.md`.

When filing bugs, please include a debug overlay capture (enable via Misc → Tools) and the JavaSound buffer (requested vs accepted) log lines.

## Latency notes


## Known limitations (alpha)


## Builds / workflow

This repo is intended to be validated via editor diagnostics and built by GitHub Actions on `git push`.

GitHub Actions also verifies that every `lang/*.json` file stays structurally in sync with `lang/en_us.json`, and it fails the workflow if locale files drift or if any translation-target locale still appears to be mostly English fallback text. English-variant and novelty locales are exempt from the translation-coverage gate, but they are still required to stay structurally synced.
The newly detected Minecraft locale codes `cv_cu`, `fr_ch`, `go_fr`, `got_de`, `uz_uz`, and `vro` are currently seeded English templates with no verified translation source. They are explicitly exempt from translation-coverage scoring while remaining structurally validated.

Important: do **not** run local Gradle builds or `runClient` on this machine.

The CI toolchains are intentionally separated: the retained Forge 1.20.1 project uses its Gradle 8.5 wrapper, Fabric 26.2 uses Gradle 9.5.1 with Loom 1.17-SNAPSHOT and no mappings declaration, and NeoForge 26.2 uses its own Gradle 9.2 wrapper.

## Fabric adapter boundary

The Fabric sibling initializes through Fabric Loader, loads the same config/profile/instrument files as Forge, starts the shared JavaSound/DSP engine, and registers a client end-tick callback plus a keybound config/test screen through verified Fabric API hooks. The Fabric tick adapter provides movement telemetry, damage/death detection from client state, landing/footstep/mining pulses, and WebSocket telemetry emission through the shared neutral output sink.

The following features are explicitly omitted from the Fabric artifact in this release because this repository does not contain a verified 26.2 Fabric hook/contract for them: sound-play interception, server-authoritative relay packets, the Forge config-screen integration point, Forge overlays, and the Forge event-bus handlers. The Fabric screen is intentionally limited to the verified core enable/test controls; it edits the same full config object and does not invent a parallel settings schema. Sulfur Caves, Sulfur Cube, Geyser, and Vulkan hooks remain omitted because no verified mapped APIs were found.

The supplied Sulfur Caves, Sulfur Cube, Geyser, and Vulkan snippets are also not part of this release: no corresponding verified mapped mechanics were found, so no hooks were added.
