# Fabric Platform Targets

Fabric uses a separate Loom sibling with the Minecraft 26.2 unobfuscated loader/API toolchain and a deliberately small lifecycle/config adapter; NeoForge also covers Minecraft 26.2.

## What Happened
The Forge source tree is tightly coupled to Forge event, networking, config-screen, and client APIs. Fabric-specific equivalents were not added without verified 26.2 signatures; unsupported mechanics remain documented rather than guessed.

## Takeaway
Keep the Forge edition at the repository root and isolate Fabric and NeoForge under their sibling projects until each ported behavior has a verified Minecraft 26.2 API contract. Minecraft 26.2 requires Java 25.

## History
- 2026-09-15 (minecraft-telemetry/migration): initial
