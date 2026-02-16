# Bass Shaker Telemetry (Forge 1.20.1)

Client-side Forge mod that converts simple Minecraft telemetry (speed/accel, damage, biome transitions, elytra) into a **dedicated audio stream** routed via JavaSound to a selectable output device (useful for bass shakers / tactile transducers).

## Features

- Speed/accel-based continuous rumble (speed tone)
- One-shot events: damage burst, biome chime, accel bump
- Simulated “road texture” rumble layer (toggle in config UI)
- Output safety: headroom scaling + soft limiter, plus automatic mute/sleep when no live telemetry (prevents menu rumble)
- Sound-to-haptics: translates many game sounds (steps, hits, explosions, block break/place, etc.) into short tactile impulses
- Gameplay haptics: translates basic gameplay interactions (attack/use clicks, mining pulse, XP gain) into tactile impulses

## Build

```powershell
./gradlew.bat build
```

## Run in dev

```powershell
./gradlew.bat runClient
```

## Configure in-game

- Minecraft main menu → **Mods** → **Bass Shaker Telemetry** → **Config**
- Choose an output device, set master volume, toggle signals.

Config is persisted at:

- `config/bassshakertelemetry.json`
