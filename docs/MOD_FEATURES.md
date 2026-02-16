# Bass Shaker Telemetry — Feature Reference

## Telemetry sources

- Player speed: `player.getDeltaMovement().length()`
- Player accel: per-tick delta of speed
- Elytra state: `player.isFallFlying()`
- Damage event: client-side health drop detection
- Biome transitions: biome key change at player position

## Audio engine behavior

- Sample rate: 48 kHz
- Format: 16-bit PCM stereo (mono mix duplicated to L/R)
- Smoothing: 1-pole smoothing on continuous amplitude to avoid clicks
- Safety: output headroom + soft limiter (tanh) to reduce clipping
- Gating: automatically fades out and eventually closes the audio line when no live telemetry (e.g., menus / pause)

## Effects (current)

- **Road texture**: low-frequency filtered noise scaled by speed (toggleable; default OFF).
- **Damage burst**: short decaying white-noise burst.
- **Biome chime**: short low sine “bump” on biome changes.
- **Accel bump**: short low thump on large accel spikes (toggleable; default OFF).
- **Sound haptics**: maps many in-game sounds (explosions, hurt, break/place, steps, attacks) into short impulses.
- **Gameplay haptics (non-sexual)**: maps basic interactions (attack/use click edges, mining pulse while holding attack on a block, XP gains) into short impulses.

## In-game config UI

- Per-effect volume sliders are available under **Advanced settings → Effect volumes**.
- Each effect slider has a **Test** button directly underneath to preview that effect without needing to trigger it in gameplay.

Timing knobs are available under **Advanced settings → Timing** (durations/cooldowns/periods).

## Config file

Saved at `config/bassshakertelemetry.json`.

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
