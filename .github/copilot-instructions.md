# Copilot Instructions — Bass Shaker Telemetry (Forge 1.20.1)

## Stack context (multi-repo workspace)
- This mod is one part of a 3-repo “SimHub-style” stack (driver + router + Minecraft mod).
- Workspace-level overview + cross-repo contracts live in `../../.github/copilot-instructions.md`.
- This mod can output:
  - **Haptics audio** to a selected Windows playback device (current/common pipeline).
  - **Optional WebSocket JSON telemetry** (default `127.0.0.1:7117`) that the Windows router can consume to synthesize haptics in-process.

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

## Evidence-first engineering
- Verify repository structure, call sites, schemas, build configuration, and CI behavior before making claims about them.
- Do not invent APIs, configuration keys, hardware capabilities, dependencies, or runtime behavior that has not been verified in the repository or environment.
- When an assumption is unavoidable, state it plainly and choose the most defensible behavior supported by the current codebase.
- Keep external boundaries explicit. Complete in-repository behavior, and document anything that depends on Windows audio drivers, external routers, servers, or physical hardware.
- Prefer the existing architecture and local patterns over generic rewrites or speculative abstractions.

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

## Implementation standards
- Implement requested behavior completely; do not leave placeholders, fake integrations, stubs, or “coming soon” paths.
- Keep changes minimal, readable, and focused. Avoid unrelated refactors and unnecessary dependencies.
- Preserve public APIs and backward compatibility where practical. If a public interface or external contract changes, update every affected producer, consumer, test, and document in the same change set.
- Keep gameplay logic, client wiring, audio rendering, configuration, networking, and hardware/infrastructure concerns separated according to the existing package boundaries.
- Validate configuration, serialization, localization, and schema changes across all consumers before considering the change complete.
- Keep secrets, credentials, machine-specific paths, and environment-specific values out of source control.

## Dev workflows & safety features
- Every change should follow this workflow.

## Workflow After Every Code Change (STRICT)

**Important:** Do **NOT** run local Gradle builds/tests (no `./gradlew build`, no `runClient`) on this machine.
Validation must use editor diagnostics (Problems / `get_errors`) and then a GitHub Actions run triggered by `git push`.

**After ANY code/resource/data change, you MUST follow this complete workflow:**

1. **Scan all files first**
  - Run workspace-wide error checking (VS Code Problems / diagnostics) across the entire codebase.
  - Then do the relevant “impact radius” scan (see checklists below).
  - **Immediately after the scan:** update any relevant docs so they match the final behavior:
    - `README.md`
    - `CHANGELOG.md`
    - `docs/MOD_FEATURES.md` (feature reference; keep this complete and current)
    - Any other docs touched by the change (e.g., codex text JSON)
  - **Localization always stays in sync:** if you change any translation keys or UI text in `src/main/resources/assets/bassshakertelemetry/lang/en_us.json`, you MUST immediately run `./tools/sync_lang_files.ps1` and commit the resulting updates so every locale file exists and contains all keys.
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

## Localization workflow (do not skip)
- `src/main/resources/assets/bassshakertelemetry/lang/en_us.json` is the source of truth for key set.
- The folder `src/main/resources/assets/bassshakertelemetry/lang/` contains one JSON per Minecraft-supported locale (seeded from `en_us.json` for contributor accessibility).
- After *any* change to `en_us.json`, run `./tools/sync_lang_files.ps1` so missing keys/files are automatically filled.
- When adding real translations to non-English files, keep the same keys; only change values.
- GitHub Actions now enforces this: CI runs `./tools/sync_lang_files.ps1` and fails if any locale file would be rewritten, and it also fails if any translation-target locale still looks like obvious English fallback content. English-variant and novelty locales are exempt from the translation-coverage gate, but they must still stay structurally in sync with `en_us.json`.

## Impact-radius checklist
Before committing, proactively review the connected files that must remain consistent:

- **Build and configuration:** `build.gradle`, `gradle.properties`, `settings.gradle`, `mods.toml`, config defaults, and CI workflow assumptions.
- **API and networking:** all call sites, packet registration and handlers, server/client boundaries, and optional integration behavior.
- **Audio and telemetry:** event producers, profile keys, mixer/bus routing, output formats, device handling, and debug/overlay reporting.
- **Resources and localization:** JSON schemas, assets, UI bundle files, `en_us.json`, and every synchronized locale file.
- **UI and UX:** screen navigation, config persistence, test controls, tooltips, and fallback behavior when optional assets or devices are unavailable.
- **Documentation and tests:** `README.md`, `CHANGELOG.md`, `docs/MOD_FEATURES.md`, hardware guidance, and the smallest meaningful validation for the changed behavior.

## Repository hygiene and safety
- Do not commit `build/`, `.gradle/`, local tooling caches, logs, temporary files, or generated output unless the repository explicitly expects it.
- Stage only intended source, resource, configuration, and documentation changes.
- Do not create tags or releases automatically.
- Do not force-push or rewrite history.
- Keep commits small and focused when commits are requested.

## Validation shortcuts
- Workspace diagnostics: use the VS Code Problems/diagnostics view, including after each meaningful fix.
- Localization sync: `./tools/sync_lang_files.ps1` (run from the repository root after changing `en_us.json`).
- Localization validation: `./tools/validate_lang_files.ps1`.
- CI build and artifact validation: push to GitHub Actions when a build is needed; CI is the authoritative build path for this machine.
- Do not use `./gradlew build`, `./gradlew runClient`, or other local Gradle/Forge runtime commands on this machine.

## Documentation and completion bar
- Keep `README.md`, `CHANGELOG.md`, `docs/MOD_FEATURES.md`, and relevant hardware/setup/troubleshooting docs aligned with the final behavior.
- Before stopping, confirm the requested behavior is implemented, affected diagnostics are clean, connected contracts and resources are synchronized, relevant documentation is current, and validation has actual evidence behind it.
- In the final summary, state what was wrong or missing, what changed, why it is correct, what checks were run, and any remaining dependency on external hardware, infrastructure, or unverified assumptions.

