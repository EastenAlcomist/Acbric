# Changelog

[中文](CHANGELOG.zh-CN.md)

This page records user-visible behavior, API and compatibility changes. Build and test commands are in the [build guide](BUILDING.md).

## 0.3.8 (unreleased)

### 2026-11-27 — External releases include Java 21 by default

- `externalDistZip` now runs JDK 21 `jlink` and includes `runtime/` by default, so players do not need a separate Java installation.
- `-PbundleRuntime=false` explicitly creates a minimal package without a runtime. The default runtime task fails clearly when the build JDK is not Java 21.

### 2026-11-27 — Embedded native ACMODs become a default API capability

- The API now scans local and Steam-downloaded native MODs by default. Exactly one `*.jar` under `AcbricMod/` enables the `carries AcbricMOD` suffix and `Enable bundled ACMOD` action.
- Installation reuses Fabric metadata validation and atomic publication, copies into Acbric's Java MOD folder, and removes the disabled selection. Same-name conflicts, multiple JARs, damaged metadata and protected IDs are rejected.
- The standalone `acbric_embedded_mods` is no longer required and must not be installed alongside the updated API.
- Validation: all 16 suites pass 1223 checks, including 83 in `mods`; a real Knot probe recognizes both local and Steam carriers and directly calls transformed `ModsScreen$5.name` to verify the row suffix injection.

### 2026-11-23 — Localized Java MOD descriptions

- `fabric.mod.json` may provide `custom.acbric.description` for the native languages `en`, `chi`, `de`, `fr`, `jpn`, `kor` and `ru`.
- The native MOD list and Acbric details view follow the active game Locale, falling back to English and then the top-level `description`.
- The template provides `modDescriptionEnglish` and `modDescriptionChinese`. Chinese `gradle.properties` values use Unicode escapes to avoid Java Properties decoding corruption.

### 2026-11-21 — Steam launch and Workshop

- The player home offers **Steam launch (owned copy)** while direct launch remains the default.
- Steam mode is limited to Steam App `342560`. The child JVM inherits the Steam environment, and the provider checks App/Client/Launch identity before enabling Steamworks.
- Steam may synchronize Workshop subscriptions on first launch or after MOD updates, delaying the main menu.

### 2026-11-18 — Combat command-panel controls

- Added `context.commandPanelUi()` for up to four buttons, toggles or status labels per MOD and 32 globally.
- Supports panel-above and viewport nine-grid anchors, bounded offsets, horizontal rows and vertical sidebars.
- The API handles drawing and accepted interaction only. It does not synchronize gameplay, resize the native panel or provide hotkeys.

## 0.3.7.1 — Native UI sharpness (2026-11-06)

- 100%/150%/200% use native Small/Medium/Large assets directly; intermediate sizes sample from the next larger tier.
- Native font tiers, fixed title size, texture filtering and rendering resolution remain unchanged.

## 0.3.7 — Native UI percentage scaling (2026-11-06)

- Added `UiScale.setPercent(50..200)`, `percent()` and `useVanilla()`.
- 100% is native Small and 0 means vanilla. The API manages a process-wide display request but does not persist settings.

## 0.3.6 — Lobby preparation and host-authoritative rules (2026-10-03)

- Preparation proof is retained per player session; member changes, rule updates and native lobby changes no longer clear everyone's preparation.
- Timed-out code checks retry automatically with backoff, and native `ResumeScreen` reconstruction restores previously expressed preparation intent.
- New campaigns adopt the host's rule snapshot after local validation. Local configuration files are not overwritten.
- The lobby status button opens a panel for re-checking, MOD settings and restart-based MOD enable/disable.
- `UiWindow` limits are 4096 nodes and depth 32.

## 0.3.5.1 — Separate Java and native MOD roots (2026-09-30)

- Fixed shared mode replacing `fabric.modsFolder`, which prevented Java MODs from loading.
- Java `.jar` files always use the framework `mods`; native MODs and `acbric_vanilla/` use the active data environment's native MOD root.
- The compile baseline and compatibility target are game 1.2.15.3, with continued 1.2.15.2 support.

## 0.3.5 — Shared data environment (2026-09-30)

- **Sync now** uses vanilla saves and MODs directly without copying.
- Optional **Environment isolation** mirrors vanilla data into an instance and backs up every overwritten or removed item.
- The mode is stored per instance. Returning to shared mode keeps isolated copies, and the framework never deletes the vanilla folder.

## 0.3.4 — Engine input compatibility (2026-09-30)

- Removed the hard-coded `SlickEngine$MyInput` type, supporting both Slick and LWJGL3 input implementations.
- Ended the `0.3.3-dev` line.

## 0.3.3-dev.33 — Windows player launcher (2026-09-27)

- Added `Acbric.exe` with game discovery, first setup, direct launch, diagnostic export and local update/restore.
- Legacy CMD tools moved to `advanced/`; existing instance scripts remain compatible.

## 0.3.3-dev.30–32 — Installation and list fixes

- dev.30: the native MOD list remains visible when only Java MODs are installed.
- dev.31: clarified first setup, loading saved setup and corrupt-configuration messages.
- dev.32: bounded retries for Windows `AccessDeniedException` during first instance publication.

## 0.3.3-dev.25–29 — External installer and updates

- Added the external launcher, clean `externalDistZip`, instance setup wizard, Steam discovery, local ZIP updates and previous-version restore.
- `Acbric.exe`, launch scripts and `mods/` now share one layout.
- Legacy layouts are not migrated automatically.

## 0.3.3-dev.24 — External menu and Unicode paths (2026-09-26)

- External launch reaches the real game menu and loads native resources and audio from paths containing Chinese characters.
- The known native terrain GL issue remains deferred and is not suppressed.

## 0.3.3-dev.22–23 — External preflight and cache isolation

- Added external preflight for game A/B archives, dependency order, duplicate classes, version, code fingerprint and instance locking.
- Launch settings and user data stay inside the instance; texture reads and raw caches are isolated by source.

## 0.3.3-dev.20–21 — Developer tools and console

- Added namespaced commands, typed arguments, help, completion, closeable registration and an in-game console.
- The console offers status, MOD diagnostics, filtered messages and asynchronous export; `~` / `GRAVE` is a fixed shortcut.

## 0.3.3-dev.13–19 — Shared UI and settings

- dev.13: labels, buttons, toggles, single-line text, layouts, panels, scrolling, modal windows and managed cleanup.
- dev.14: English/Chinese selection follows the game Locale.
- dev.15–17: native drawing, text positioning, focus-return hit testing and close glyph fixes.
- dev.18: explicit settings fields, drafts, validation, defaults, reload and disk conflict protection.
- dev.19: selection, clipboard, key repeat, fixed footer and close confirmation.

## 0.3.3-dev.12 — Java MOD enable/disable

- The MOD list can enable or disable directly scanned Java `.jar` files on the next start without hot unload or archive deletion.
- The launcher filters disabled entries before Loader resolution and checks protected components, dependencies, aliases and conflicts.
- Owned bundled native resources follow the Java MOD state; older directories require explicit migration.

## 0.3.3-dev.8–11 — Multiplayer identity and rules

- dev.8: internal code handshake with bounded retries, session expiry and explicit unverifiable states.
- dev.9: lobby code checks, status UI and ready/start guards.
- dev.10: shared gameplay rule declarations, pre-generation freezing and resume validation.
- dev.11: saved-rule inspection, explicit adoption, direct conversion and write-new, never overwriting the source.

## 0.3.3-dev.7 — Code manifests

- Export loaded MODs, game identity and `acbric` entrypoint state, with strict offline comparison.
- `CODE_MATCH` proves startup code identity only; it does not authenticate peers or synchronize state.

## 0.3.3-dev.6 — Event scopes

- Added `context.eventScope` for grouped unsubscription, ownership diagnostics and stale-snapshot deactivation.
- Managed callback exceptions still propagate without automatic disable or retry.

## 0.3.3-dev.5 — MOD configuration

- Added strict per-MOD JSON configuration with defaults, validation, explicit migration, reload, backup and atomic save.
- Configuration does not broadcast or silently reset damaged or future-version files.

## 0.3.3-dev.3–4 — Campaign data and lifecycle

- dev.3: campaign JSON stored per MOD ID; reads return defensive snapshots, writes do not broadcast and damaged declared blocks abort loading.
- dev.4: `CREATED`, `LOADED`, `RESTORED` and `EXITED` distinguish generation, load, local recovery and leaving an active campaign.

## 0.3.3-dev.2 — Startup diagnostics

- Read `AGame.VERSION` before dependency resolution and hash ordered game archives.
- Write a per-launch session report distinguishing loaded code, missing entrypoints, initialization in progress, success and failure.

## 0.3.3-dev.1 — API compatibility baseline

- Preserved 0.3.2 public members, added `RENAME_SHIP_*` and retained deprecated `ONE_SHOT_*` behavior in place.
- Fixed data-load error propagation, one-shot events, resource ownership, classpath separation and MOD metadata validation.
- Standardized Java 21 output, UTF-8 source and Chinese responsibility comments.
