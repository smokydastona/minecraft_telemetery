# Bass Shaker Telemetry (Forge 1.20.1)

Client-side Forge mod that converts simple Minecraft telemetry (speed/accel, damage, biome transitions, elytra) into a **dedicated audio stream** routed via JavaSound to a selectable output device (useful for bass shakers / tactile transducers).

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
