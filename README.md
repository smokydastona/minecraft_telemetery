# Bass Shaker Telemetry (Forge 1.20.1 / Java 17)

Bass Shaker Telemetry is a Forge mod that turns Minecraft gameplay events into a **dedicated tactile audio stream** (JavaSound) for bass shakers / tactile transducers.

The current design goal is **"encoded mono surround"**: direction is encoded into *one* vibration waveform using small frequency bias + micro-delay, while a **priority + ducking** mixer ensures one dominant vibration stays readable.

## What it does

- **Tactile audio output**: 48kHz 16‑bit PCM (mono mix duplicated to L/R for compatibility), routed to a selectable output device.
- **Priority & ducking (non-optional)**: when multiple effects overlap, one dominant vibration wins; others are ducked to keep impacts clear.
- **Encoded-mono direction**: when a source position is known and a profile is `directional: true`, the mod selects `front/rear/left/right` encoding bands and applies:
	- `frequencyBiasHz` (small Hz offset)
	- `timeOffsetMs` (small micro-delay)
	- `intensityMul` (optional gain multiplier)

## Signal sources (high level)

- **Telemetry-driven layers** (optional): road texture (speed), accel bump, biome chime, elytra state.
- **Event impulses**: damage burst (timed tightly), danger ticks (fire/drowning/poison/wither), death rumble.
- **Client-only sound haptics**: infers impulses from `PlaySoundEvent` (explosions, thunder, hurt, break/place, steps, attacks, doors/containers/buttons/levers, etc.). These now also participate in encoded-mono direction using the sound instance position.
- **Gameplay haptics (non-sexual)**: attack/use clicks, mining pulse, XP gains.
- **Footsteps / mining swing**: short pulses tuned for readability (no constant “engine rumble”).

## Multiplayer / timing

Some events are optionally hooked server-side and relayed to the client via a small packet so timing and source position are accurate in multiplayer.

Note: server-relayed events require the mod on **both** the server and the client.

## Configuration

In-game:

- Minecraft main menu → **Mods** → **Bass Shaker Telemetry** → **Config**
- Output device selection, master volume, per-effect toggles and volume sliders.
- Advanced settings includes an optional output buffer size selector and a latency test pulse.
- Each effect slider includes a **Test** button.

On disk:

- Main config: `config/bassshakertelemetry.json`
- Vibration profiles: `config/bassshakertelemetry_vibration_profiles.json`

Profiles are the source of truth for per-event tuning (frequency, intensity, duration, noise mix, pattern), plus:

- `priority` (0..100)
- `directional` (boolean)
- root-level `encoding` bands

For the full feature reference, see `docs/MOD_FEATURES.md`.

Hardware tuning + troubleshooting guide: `docs/HARDWARE_TUNING_GUIDE.md`.

When filing bugs, please include a debug overlay capture and the JavaSound buffer (requested vs accepted) log lines.

## Known limitations (alpha)

- **Audio device variability**: some drivers clamp/ignore requested JavaSound buffer sizes.
- **Overlay meaning**: the overlay shows the last vibration/suppression, not a per-sample “truth” of what you felt.
- **Sound source positions**: some Minecraft sounds have approximate/"artistic" origins; direction encoding can be perceptually off in those cases.

## Builds / workflow

This repo is intended to be validated via editor diagnostics and built by GitHub Actions on `git push`.

Important: do **not** run local Gradle builds or `runClient` on this machine.
