# Bass Shaker Telemetry — Feature Reference

## Telemetry sources

- Player speed: `player.getDeltaMovement().length()`
- Player accel: per-tick delta of speed
- Elytra state: `player.isFallFlying()`
- Damage event: client-side hurt event (LivingHurtEvent) for accurate timing
- Biome transitions: biome key change at player position

## Audio engine behavior

- Sample rate: 48 kHz
- Format:
	- Default: 16-bit PCM stereo (mono mix duplicated to L/R)
	- Optional: 16-bit PCM 7.1 (8-channel interleaved) when **Sound Scape** is enabled
- Smoothing: 1-pole smoothing on continuous amplitude to avoid clicks
- Safety: output headroom + soft limiter (tanh) to reduce clipping
- Tone shaping: optional Output EQ (single band) is available in Advanced settings → Audio
- Auto-level: optional Smart Volume (slow AGC) is available in Advanced settings → Audio
- Gating: automatically fades out and eventually closes the audio line when no live telemetry (e.g., menus / pause)
- Priority & ducking (mono): when multiple effects overlap, one dominant vibration wins; others are ducked so impacts stay readable
- Latency tuning: optional JavaSound output buffer size selection (larger buffers are often more stable but add latency)
- Latency test: an in-game latency test pulse toggle is available in Advanced settings → Audio
- Calibration tools: quick test tones + a frequency sweep (plus a Stop/Silence button) are available in Advanced settings → Audio
- Debug overlay: optional developer overlay showing the last vibration source/key, priority, frequency, gain, and recent suppression.
- Demo sequence: a simple built-in demo runner is available in Advanced settings → Audio for repeatable tuning.

External output:

- Optional WebSocket telemetry output (JSON events) can be enabled via config keys (see below).

## Effects (current)

- **Road texture**: low-frequency filtered noise scaled by speed (toggleable; default OFF).
- **Damage**: triggers on hurt timing (with client-tick fallback). When a damage source position is available (attacker/projectile/source position), `damage.generic` is **directional** so you can feel where it came from.
- **Periodic danger ticks**: subtle repeating pulses for fire/drowning/poison/wither.
- **Death rumble**: one-shot death effect (boom + “womp” tail).
- **Flight wind (Elytra)**: low rumble impulses while gliding that shift left/right as you turn (profile key: `flight.wind`).
- **Mounted haptics**: while riding, ground mounts emit hoof “clump” pulses (`mount.hoof`). Mounts that can fly swap to `flight.wind` while airborne.
- **Warden heartbeat**: heartbeat pulses triggered by the **actual Warden heartbeat sound** (profile key: `boss.warden_heartbeat`). This is directional and gets louder as you get closer, but is intentionally capped to stay quieter than damage.
- **Biome chime**: short low sine “bump” on biome changes.
- **Accel bump**: short low thump on large accel spikes (toggleable; default OFF).
- **Sound haptics**: maps many in-game sounds (explosions, thunder, hurt, break/place, steps, attacks, doors/containers/buttons/levers, etc.) into short impulses.
- **Deduplication**: when an authoritative server-relayed event fires (e.g., explosion/block break/combat hit), nearby matching sound-inferred impulses are briefly suppressed to avoid double-triggering.
- **Gameplay haptics (non-sexual)**: maps basic interactions (attack/use click edges, mining pulse while holding attack on a block, XP gains) into short impulses.
- **Footsteps**: grounded walking emits short step pulses (no continuous "engine" rumble).
- **Mining swing**: emits a pulse at the start of each arm swing while mining a block (visual-sync).

## In-game config UI

- Per-effect volume sliders are available under **Advanced settings → Effect volumes**.
- Each effect slider has a **Test** button directly underneath to preview that effect without needing to trigger it in gameplay.

Sound Scape routing:

- A **Sound Scape (7.1)** screen is available for routing broad haptic categories to specific channels (FL/FR/C/LFE/SL/SR/BL/BR) or to named groups.
- Groups are editable lists of channels (e.g., a “Seat” group could be `[LFE, BL, BR]`).
- Per-effect overrides can be added as **debug key → target** rules. Overrides use exact key match (case-insensitive) and take priority over category routing.
- If a multichannel output device cannot be detected/opened, the UI restricts routing options to stereo (FL/FR).

Timing knobs are available under **Advanced settings → Timing** (durations/cooldowns/periods).

## Build / artifacts

This repo is intended to produce test JARs via GitHub Actions on `git push`.

Local Gradle builds / `runClient` are intentionally not part of the workflow on this machine.

## Mod Integration API

Other mods can emit haptic events into Bass Shaker Telemetry using the public API package:

- `com.smoky.bassshakertelemetry.api.HapticApi`
- `com.smoky.bassshakertelemetry.api.HapticEvent`

Notes:

- The API is safe to call on dedicated servers (it gates all work behind `Dist.CLIENT`).
- If your mod does not hard-depend on Bass Shaker Telemetry, use reflection so your mod doesn't crash when the mod is absent.

Example (hard dependency / compile-time reference):

```java
import com.smoky.bassshakertelemetry.api.HapticApi;

// short impulse
HapticApi.sendImpulse("mymod.recoil", 60.0, 90, 0.8);

// sustained tone (click-safe)
HapticApi.sendTone("mymod.engine", 45.0, 400, 0.25);

// sweep
HapticApi.sendSweep("mymod.scan", 20.0, 120.0, 1200, 0.7);
```

Example (optional dependency via reflection):

```java
try {
	Class<?> api = Class.forName("com.smoky.bassshakertelemetry.api.HapticApi");
	api.getMethod("sendImpulse", String.class, double.class, int.class, double.class)
			.invoke(null, "mymod.recoil", 60.0, 90, 0.8);
} catch (Throwable ignored) {
	// Bass Shaker Telemetry not installed (or API changed)
}
```

API stability notes:

- The intent is for `HapticApi` / `HapticEvent` to remain source-compatible across versions, but treat it as an alpha API.
- Prefer namespaced keys (example: `mymod.recoil`, `mymod.engine`) so debug overlay entries are easy to read.
- Avoid calling the API every tick for many entities; aggregate events and emit at a sensible rate.
- If you ship with an optional integration, reflection (as above) is the safest approach.

## Config file

Saved at `config/bassshakertelemetry.json`.

### WebSocket telemetry output (client-only)

When enabled, the mod starts a small WebSocket server on `127.0.0.1` and broadcasts JSON packets.

Config keys:

- `webSocketEnabled` (boolean, default `false`)
- `webSocketPort` (int, default `7117`)
- `webSocketSendTelemetry` (boolean, default `true`)
- `webSocketSendHapticEvents` (boolean, default `true`)

Packet formats (one JSON object per message):

- Telemetry:
	- `{"type":"telemetry","t":<epoch_ms>,"speed":<double>,"accel":<double>,"elytra":<bool>}`
- Haptic events (emitted when a new impulse voice is created):
	- `{"type":"haptic","t":<epoch_ms>,"key":"...","f0":<double>,"f1":<double>,"ms":<int>,"gain":<double>,"noise":<double>,"pattern":"...","pulsePeriodMs":<int>,"pulseWidthMs":<int>,"priority":<int>,"delayMs":<int>}`

## Vibration profiles (JSON)

Tunable vibration profiles are saved at `config/bassshakertelemetry_vibration_profiles.json`.

This file controls per-event frequency/intensity/duration (and optional pattern/falloff flags) so you can tune chair feel without recompiling.

### Encoded-mono directional encoding

Some profiles can be marked `directional: true`. When a source position is known (e.g., server-relayed explosions/block breaks/hits), the mod encodes direction into the *mono* waveform using a root-level `encoding` object in the profiles JSON:

- `encoding.center/front/rear/left/right.frequencyBiasHz`: small Hz offset applied to the profile’s base frequency
- `encoding.*.timeOffsetMs`: small micro-delay (ms) applied at trigger time
- `encoding.*.intensityMul`: optional gain multiplier per direction band

Directional encoding is applied for both server-relayed event haptics (packet includes a source position) and client-only sound haptics (derived from `PlaySoundEvent` using the sound instance world position).

Directional encoding is also applied to:

- `damage.generic` when a damage source position is known
- `flight.wind` (uses a synthetic source point that shifts with yaw-rate while gliding)

Profiles also support:

- `priority` (0..100): higher wins; only one effect is dominant at a time
- `directional` (boolean): whether to apply the above encoding when source position is available

### Patterns

Profiles can specify a `pattern`:

- `single`: one envelope-shaped impulse (default)
- `soft_single`: softer attack/release for less clicky taps
- `shockwave`: strong onset with rapid decay (good for explosions)
- `punch`: like shockwave, but more abrupt
- `fade_out`: sustained onset fading to 0 by the end (good for death/long events)
- `pulse_loop`: repeats short pulses for the profile duration
- `flat`: constant sustain with click-safe attack/release (useful for calibration tones)

For `pulse_loop`, you can optionally add:

- `pulsePeriodMs`: time between pulse starts (default `160`)
- `pulseWidthMs`: pulse width within the period (default `60`)

### Server-relayed event haptics

Some events are now hooked server-side and relayed to the client via a small packet so timing is reliable in multiplayer (instead of relying only on sound inference).

Note: these require the mod to be present on the server and the client.

Default keys used:

- `explosion.generic` (distance-scaled)
- `world.block_break`
- `combat.hit` (attacker feedback)
- `damage.fall` (scaled by fall distance)

Authoritative event ownership (current intent):

- **Explosion**: server-relayed (`explosion.*`) is authoritative; sound-inferred `explosion` bucket is suppressed briefly nearby.
- **Block break**: server-relayed `world.block_break` is authoritative; sound-inferred `block_break` bucket is suppressed briefly nearby.
- **Combat hit**: server-relayed `combat.hit` is authoritative; sound-inferred `attack`/`hurt` buckets are suppressed briefly nearby.
- **Damage to player**: game event timing (hurt hooks) is authoritative; sound inference is just a fallback texture.
- **Footsteps / ambient interactions**: client-only inference (no server relay).

Suppression windows are intentionally short (tens of ms) and bucket-scoped, with a small spatial radius (in blocks) to tolerate slightly offset sound origins.

Common keys (selected):

- `enabled`
- `outputDeviceName`
- `masterVolume`
- `damageBurstEnabled`
- `damageBurstGain`
- `biomeChimeEnabled`
- `biomeChimeGain`
- `roadTextureEnabled`
- `outputHeadroom`
- `limiterDrive`
- `roadTextureGain`, `roadTextureCutoffHz`
- `accelBumpEnabled`, `accelBumpThreshold`, `accelBumpMs`, `accelBumpGain`
- `soundHapticsEnabled`, `soundHapticsGain`, `soundHapticsCooldownMs`
- `gameplayHapticsEnabled`, `gameplayHapticsGain`, `gameplayHapticsCooldownMs`
- `gameplayAttackClickEnabled`, `gameplayUseClickEnabled`, `gameplayMiningPulseEnabled`, `gameplayMiningPulsePeriodMs`, `gameplayXpEnabled`
- `footstepHapticsEnabled`, `footstepHapticsGain`
- `mountedHapticsEnabled`, `mountedHapticsGain`
- `miningSwingHapticsEnabled`, `miningSwingHapticsGain`
