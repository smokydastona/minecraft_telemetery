# Fabric Platform Targets

Fabric uses a separate Loom sibling with published Minecraft 26.3 loader/API coordinates and a deliberately small lifecycle/config adapter; NeoForge covers Minecraft 26.2.

## What Happened
The Forge source tree is tightly coupled to Forge event, networking, config-screen, and client APIs. Fabric-specific equivalents were not added without verified 26.3 signatures; unsupported mechanics remain documented rather than guessed.

## Takeaway
Keep the Forge edition at the repository root, isolate Fabric under `fabric/`, and use NeoForge under `neoforge/` for Minecraft 26.2 until each ported behavior has a verified API contract. Minecraft 26.2 and 26.3 require Java 25.

## History
- 2026-09-15 (minecraft-telemetry/migration): initial
