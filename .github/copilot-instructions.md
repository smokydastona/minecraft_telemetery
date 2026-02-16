# Copilot Instructions — Krümblegård Mod

## Big picture
- Repo is a Forge 1.20.1 + GeckoLib mod template for Krümblegård.
- **Real sources live here:** `src/main/{java,resources}`.
- Root Gradle project compiles the template via `sourceSets.main` (see `build.gradle`).
- Mod id: `kruemblegard` | Base package: `com.kruemblegard`.

## Core gameplay architecture (follow this flow)
- **Traprock ambush pattern**:
  - `Traprock` starts dormant (no AI / no movement).
  - It awakens if a player interacts with it or lingers too close, then attacks.

## Advancements & triggers (project-specific)
- Don’t grant vanilla advancements directly.
- If you add advancements, prefer custom triggers in `init/ModCriteria` and fire them from gameplay.
- Note: no custom criteria triggers are currently registered.

## Worldgen (config-driven + data-driven biomes)
- Worldgen is currently minimal; `init/ModWorldgen` is intentionally empty.
- Config is in `config/ModConfig` (COMMON): `enableWaystones`, `waystoneRarity`.
- If you add/restore any waystone worldgen in the future, re-run the full worldgen impact-radius checklist.

## Crumbling Codex (in-game guidebook)
- Page text is data-driven: `src/main/resources/data/kruemblegard/books/crumbling_codex.json`.
- The Codex is granted once on first join (tracked in player persistent NBT).

## GeckoLib conventions
- Model/animation/texture binding: `client/render/model/KruemblegardBossModel` (geo/texture/animation `ResourceLocation`s).
- Renderer registration: `client/KruemblegardClient` + `client/render/KruemblegardBossRenderer`.
- Boss animations/controllers live in `entity/KruemblegardBossEntity`.

## Boss music
- Key: `music.kruemblegard` → registered in `registry/ModSounds` and mapped in `assets/kruemblegard/sounds.json`.
- Synced with fight using `KruemblegardBossEntity.isEngaged()` (SynchedEntityData).

## Assets
- Entity texture: `assets/kruemblegard/textures/entity/kruemblegard.png`.
- Block texture reuse: `assets/kruemblegard/textures/block/standing_stone.png` is intentionally reused by multiple blocks.

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

### Impact radius checklists
- **Worldgen / datapack changes** (`src/main/resources/data/**/worldgen/**`, biome tags, biome modifiers)
  - Verify all referenced IDs exist (features, structures, structure_sets, template_pools, processor_lists, tags)
  - Grep for common invalid/renamed vanilla IDs (e.g., biomes like `minecraft:mountains` are invalid in 1.20.1)
  - Verify structure_set placement schema (e.g., `minecraft:random_spread` includes required fields like `spread_type`)
  - Check `data/forge/biome_modifier/*.json` references correct tags/ids
- **Registries / DeferredRegister changes**
  - Check all `RegistryObject` usages for eager `.get()` during registration
  - Check cross-registry references (items referencing entities/blocks; block entities referencing blocks)
  - Confirm client-only registrations stay under `client/`
- **Assets / GeckoLib JSON changes**
  - Confirm `assets/kruemblegard/**` file paths match code `ResourceLocation`s
  - Ensure animation/model JSONs are well-formed and avoid known fragile patterns (e.g., keyframes)
- **Gameplay flow changes**
  - Re-scan Trigger → Controller → Boss flow to ensure trigger placement/removal and server/client separation still holds

### CI-first default (IMPORTANT)
- If the user reports a crash/CI failure or asks for a fix, and you make code/resource changes:
  - **Stage + commit + push by default** to trigger GitHub Actions and produce an updated jar artifact.
  - Only skip commit/push if the user explicitly asks you not to, or if you’re still mid-debug and expect more immediate edits.
- **Always push after committing**
  - If you created a commit during the session, **push it before ending your turn** (unless the user explicitly says “don’t push yet”).
- If there are uncommitted changes when the user expects a new jar, treat that as a bug in the workflow and push.
- **User override: “push” means push, always**
  - If the user says “push” / “push updates” / “push it”, ALWAYS run `git push` even if there are no new local commits.
  - If there are local changes (dirty working tree) and the user says “push”, treat that as “ship what’s currently in the working tree”:
    - Run the required scan(s), update `README.md` and `CHANGELOG.md` as needed, then **stage + commit + push**.
    - Only skip committing if the user explicitly says “don’t commit yet” / “don’t push yet”.
  - Do NOT create empty commits unless the user explicitly asks for an empty commit; instead, report the current `HEAD` SHA so the user can match it to the latest GitHub Actions build.
- Build validation:
  - Never build locally (as Copilot/agent) — rely on GitHub Actions for validation.
  - GitHub Actions is the authoritative “clean environment” build.
- Keep workspace clean:
  - Don’t commit generated outputs like `build/` or `.gradle/`.
- Log handling:
  - When asked to “read the log”, use the newest `latest.log` from the current run; don’t rely on stale copies.
- Releases:
  - Don’t auto-tag or create releases; user manages tags/releases manually.

## Copilot behavior rules (repo-specific)
- Always respect: mod id `kruemblegard`, base package `com.kruemblegard`, and asset paths under `assets/kruemblegard`.
- Always follow the Trigger → Controller → Boss flow when changing gameplay.
- Client-only features (renderer, music, GeckoLib model wiring) go under `client/`.
- Persistent logic (arena controller, boss state, arena build) goes under `blockentity/`, `entity/`, `world/arena/`.
- Don’t suggest code outside: `src/main/java/com/kruemblegard`.

## Build / run
- CI build command: `./gradlew --no-daemon clean build`
- CI: `.github/workflows/build.yml` uses the Gradle wrapper; keep CI on `./gradlew`.

