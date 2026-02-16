# Copilot Instructions — Bass Shaker Telemetry (Forge 1.20.1)

## Project identity (keep these consistent)
- Mod id: `bassshakertelemetry`
- Mod name: `Bass Shaker Telemetry`
- Base package / group id: `com.smoky.bassshakertelemetry`
- Minecraft: `1.20.1` | Forge: `47.2.0` | Java: `17`

## Source of truth (where to edit)
- Java sources: `src/main/java/com/smoky/bassshakertelemetry/**`
- Resources: `src/main/resources/**`
- Mod metadata: `src/main/resources/META-INF/mods.toml` (expanded from Gradle properties)
- User config file on disk: `config/bassshakertelemetry.json` (written by `BstConfig`)

## Core architecture (follow this flow)
- Entry point: `BassShakerTelemetryMod`
  - Loads config early (`BstConfig.load()`).
  - Registers client-only init via `DistExecutor.safeRunWhenOn`.
  - Starts audio engine on client during common setup when enabled.
- Client bootstrap: `client/ClientInit`
  - Registers `TelemetryEventHandler` on `MinecraftForge.EVENT_BUS`.
  - Registers the in-game config UI (`TelemetryConfigScreen`).
- Telemetry collection: `client/TelemetryEventHandler`
  - Computes speed/accel/elytra state and calls `AudioOutputEngine.updateTelemetry(...)`.
  - Triggers one-shot events (damage burst, biome chime) via `AudioOutputEngine.trigger*()`.
- Audio rendering: `audio/AudioOutputEngine`
  - Runs a dedicated daemon thread that writes 48kHz 16-bit stereo PCM.
  - Uses JavaSound `SourceDataLine` and an optional selected `Mixer` from config.
  - Must avoid clicks: smoothing/limiting should stay simple and stable.

## Client-only safety rules (important)
- Anything that imports `net.minecraft.client.*` must remain client-only.
- Keep client-only wiring under `client/` and gated by `DistExecutor`.
- Do not reference client classes from common/server execution paths.

## Dev workflows & safety features
- Every change should follow this workflow.

## Workflow After Every Code Change (STRICT)

**After ANY code/resource/data change, you MUST follow this complete workflow:**

1. **Scan all files first**
  - Run workspace-wide error checking (VS Code Problems / diagnostics) across the entire codebase.
  - Then do the relevant “impact radius” scan (see checklists below).
  - **Immediately after the scan:** update any relevant docs so they match the final behavior:
    - `README.md`
    - `CHANGELOG.md`
    - `docs/MOD_FEATURES.md` (feature reference; keep this complete and current)
    - Any other docs touched by the change (e.g., codex text JSON)
  - If changes are notable and you’re about to ship a test jar, record them under a **versioned** `CHANGELOG.md` section that matches the jar version (see versioning in `build.gradle`).
  - If version uses git history (e.g., commit count), ensure GitHub Actions uses full history checkout (`fetch-depth: 0`) so CI jar versions match.
2. **Fix errors systematically**
  - Address errors discovered by the scan in a structured way.
  - Do not stop after fixing “just one file”; iterate until the workspace is clean.
3. **Re-validate after each fix**
  - After each fix pass, re-run the workspace-wide error scan to ensure no new errors were introduced.
  - **Immediately after each re-scan:** refresh any relevant docs again if the fix changed behavior/assets.
4. **Explain every change**
  - State what was wrong, what changed, and why.
  - Ensure all relevant docs match the final behavior you're about to ship.
5. **Push to GitHub Actions**
  - Commit and push ONLY (no tags/releases).
  - **After EVERY commit, immediately run `git push`** so GitHub Actions produces a fresh artifact.
  - **Before every commit:** verify `README.md`, `CHANGELOG.md`, and `docs/MOD_FEATURES.md` are updated for this change.
6. **Only stop when 100% validated**
  - Continue until the workspace has no remaining errors related to the change and the project is in a shippable state.
7. **Update documentation if needed**
  - If behavior/config/workflow changed, update relevant docs/instructions.

### “Scan likely impact radius” definition (do this before committing)
- This is a *second step* after the workspace-wide “scan all files” pass.
- It means: identify what the change touches and proactively scan the *connected* files/registries/data that must remain consistent so we don’t ship a new jar that just crashes somewhere else.
## Repo hygiene
- Don’t commit generated outputs (`build/`, `.gradle/`, local tooling caches, logs).
- Prefer small focused commits with clear messages.

