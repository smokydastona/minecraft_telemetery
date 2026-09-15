# Fabric Platform Targets

Minecraft 26.2 migration uses a separate Fabric Loom sibling with verified loader/API coordinates and a deliberately small lifecycle/config adapter.

## What Happened
The Forge source tree is tightly coupled to Forge event, networking, config-screen, and client APIs. Fabric-specific equivalents were not added without verified 26.2 signatures; unsupported mechanics remain documented rather than guessed.

## Takeaway
Keep the Forge edition at the repository root and isolate Fabric under `fabric/` until each ported behavior has a verified 26.2 API contract. Minecraft 26.2 requires Java 25.

## History
- 2026-09-15 (minecraft-telemetry/migration): initial
