# BST Haptic Engine (external)

This is an **optional** external “SimHub-style” haptic engine for **Bass Shaker Telemetry**.

- The Minecraft mod acts as the *telemetry + event source* (WebSocket server).
- This tool acts as the *ShakeIt layer + synthesis engine* (JSON mapping → effects → audio device output).

## Prereqs

- Windows
- .NET 8 SDK (x64)

## 1) Enable WebSocket output in the mod

In `config/bassshakertelemetry.json`:

- `webSocketEnabled`: `true`
- `webSocketPort`: `7117` (default)
- `webSocketSendHapticEvents`: `true`
- (optional) `webSocketSendTelemetry`: `true`

## 2) Configure this engine

- `engine.json`: output device selection + buffering + gains
- `mappings.json`: event → effect mapping rules

By default it targets a device name containing `CABLE Input` (VB-Cable). Change `output.deviceNameContains` to match your device.

## 3) Run

From this folder:

- `dotnet run -- --config engine.json --map mappings.json`

Options:
- `--list-devices` prints Windows audio render devices
- `--click` plays a calibration click

## Protocol

Connects to the mod’s built-in WebSocket server:

- URL: `ws://127.0.0.1:7117/`

Packets are single JSON objects with a `type`:

- `telemetry`: `{ "type":"telemetry", "t":..., "speed":..., "accel":..., "elytra":true|false }`
- `haptic`: `{ "type":"haptic", "t":..., "key":"...", "f0":..., "f1":..., "ms":..., "gain":..., "noise":..., "pattern":"...", "pulsePeriodMs":..., "pulseWidthMs":..., "priority":..., "delayMs":..., "azimuthDeg":..., "directionBand":"front|rear|left|right" }`
- `event`: `{ "type":"event", "t":..., "id":"...", "kind":"impact|continuous|...", "intensity":..., ... }`

This engine is tolerant of extra fields, so the protocol can evolve.
