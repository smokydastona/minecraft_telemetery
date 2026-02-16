# Bass Shaker Telemetry (Forge 1.20.1)

Client-side Forge mod that converts simple Minecraft telemetry (speed/accel, damage, biome transitions, elytra) into a **dedicated audio stream** routed via JavaSound to a selectable output device (useful for bass shakers / tactile transducers).

## Features

- Optional telemetry-driven continuous rumble (road texture), plus one-shot events
- One-shot events: damage burst, biome chime, accel bump (accel bump defaults OFF)
- Simulated “road texture” rumble layer (toggle in config UI; defaults OFF)
- Output safety: headroom scaling + soft limiter, plus automatic mute/sleep when no live telemetry (prevents menu rumble)
- Sound-to-haptics: translates many game sounds (steps, hits, explosions, block break/place, etc.) into short tactile impulses
- Gameplay haptics: translates basic gameplay interactions (attack/use clicks, mining pulse, XP gain) into tactile impulses
- Grounded footsteps: short "pitter-patter" pulses when walking on blocks
- Mining swing haptics: block breaking pulses synced to the on-screen arm swing
- In-game per-effect volume sliders (Advanced settings), each with a Test button to preview the effect

## Builds / artifacts

This project is validated via editor diagnostics and built by GitHub Actions on `git push`.

## Configure in-game

- Minecraft main menu → **Mods** → **Bass Shaker Telemetry** → **Config**
- Choose an output device, set master volume, toggle signals.

Config is persisted at:

- `config/bassshakertelemetry.json`
