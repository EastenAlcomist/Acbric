# Changelog

## 0.3.3-dev.22 — External-install preflight and loading prototype (2026-09-26)

- Add a checker that does not initialize game classes, a Windows Java bootstrap check, and `preflightTools` containing explicit launch dependencies only. Early temporary reports preserve failures even when installation/instance paths are invalid.
- Parse real JSON; inspect root A/B, stable dependency order, duplicate classes, critical dependencies, version/code fingerprint, basic resource directories and x64 DLL PE headers. Retain the established steamworks version exclusion.
- External Provider uses explicit paths, instance locking and write checks. A real Knot probe reads selected installation code directly; an early Mixin separates native resources and instance userdata. Legacy startup and public API signatures remain unchanged.
- **904 standard checks** (32 added); **25 external checks on 1.2.15.3**, with **5,325 installation file contents unchanged**; **71×2=142 ARC checks** on legacy layouts for 1.2.15.2 / 1.2.14. Windows PowerShell 5.1 clearly rejects Java 8 and passes preflight with Java 21.
- No normal Main invocation or GPU/native DLL loading/full generation/DLC/Workshop/multiplayer acceptance; the earlier full UI matrix was not rerun. Normal external launch returns `EXTERNAL_NOT_READY`. Resource caches, LaunchSettings overrides, clean distribution/templates, migration and installer remain later phases; existing distZip is still not a clean framework package.
- See [external-install instructions](EXTERNAL_INSTALL.md). dev.22 is committed at user request; not pushed, and no player runtime copy was replaced.


## 0.3.3-dev.21 — Console shortcut (2026-09-26)

Since dev.21, press the **backtick / tilde key** below Esc and left of 1 (physical `GRAVE`; Shift is optional) to open the console with the command field focused. The opening key is consumed. Ctrl/Alt/Meta combinations, inactive displays, native error/help/chat overlays, and existing Acbric windows do not trigger it. Close another Acbric window before using the shortcut. While the console is open this key remains ordinary text; use Esc/X/Close to dismiss. Holding the key cannot repeatedly reopen it. This is a fixed console shortcut, not a general key-binding API.

872 standard checks and 464 real Fabric integration checks across both game versions pass, including 50 new shortcut assertions. Existing console-demo 0.1.0 and UI showcase 0.3.0 need no update. Windows real focus/keyboard-layout acceptance remains interactive.


## 0.3.3-dev.20 — Developer tools and console (2026-09-26)

- Built-in API-details entries, status/MOD snapshots, filtered paged messages, detail/copy and asynchronous diagnostic export.
- Public namespaced commands with typed positional arguments, metadata-driven help/static completion and closeable registration; game-thread/context checks, SHARED reserved and refused.
- Bounded capture of existing logging/startup/resource diagnostics, preserving event exception semantics; no global stdout interception or automatic gameplay synchronization.
- Compatible text-field onSubmit; console Enter/history/Tab behavior, bilingual UI and documentation.
- Consolidated current README/API/developer navigation; standalone console-demo 0.1.0 remains outside the default framework distribution.
- 872 standard and 414 real Fabric checks across two game builds pass. Two existing symlink scenarios skipped; OS/GPU/full-game acceptance remains interactive.


dev.19 validation: 810 standard checks (41 new), and 356 across two real Fabric game builds (169 main + 5 restart + 4 corrupt-config startup each). All 80 public types/428 member signatures from dev.18 are retained. Clipboard success uses a substitute; failure uses actual headless AWT. Native font/input paths are checked with GPU-free drawing terminals; Windows clipboard, screenshots, IME and focus transitions still require manual validation. dev.18 and earlier counts below are historical.

## Development build — API 0.3.3-dev.19

dev.19 adds font-measured caret placement, drag/Shift selection, Ctrl+C/X, and held navigation/deletion keys. Settings use a fixed action footer and confirm before Cancel/X/Esc discard a draft. Existing constructors, programmatic close and lifecycle cleanup retain their behavior. Companion showcase: 0.3.0. See [UI contract](UI.md) and [settings contract](SETTINGS.md).

2026-09-26 player acceptance: the user reported no problems testing dev.18 and requested a commit. All 28 changed files matched the build-validation snapshot before acceptance notes were added. Code is unchanged; the existing 769 standard checks and 304 integration checks across two game versions remain the validation evidence. No detailed manual test matrix was supplied, so this feedback does not establish coverage of every GPU, IME or third-party MOD combination.

Final validation: 769 standard checks (57 new) and 304 checks across two game versions covering Fabric/UI, showcase saving, fresh-process reload and corrupt-config startup; dev.17 public signatures retain 71 types/354 members. English/Chinese UI and settings examples are compiled. Drawing terminals are GPU-free; player feedback is recorded above, and automated checks do not cover OS display, IME or focus switching.

## Development build — API 0.3.3-dev.18

dev.18 adds explicit MOD settings forms, numeric/choice controls and controlled text binding, with draft apply/cancel/defaults/reload and memory/disk conflict protection. New APIs: ModConfig.defaults()/save(expected,data), ConfigField, ConfigEditor and SettingsUi. MOD code implements effect timing; configuration is not automatically synchronized and existing campaigns are not rewritten. See [settings API](SETTINGS.md).

2026-09-26 player feedback: the user accepted the dev.17 UI fixes and approved proceeding. This stage includes shared components, details/tool entries, English/Chinese support, text positioning, focus-return hit testing, and the close glyph. Existing validation: 712 standard checks and 230 integration checks across two game versions. All 34 changed files matched the tested snapshot before this commit; only acceptance notes were then added. Unchanged code was not retested. This does not establish coverage of every GPU, IME, or third-party MOD combination.

## Development build — API 0.3.3-dev.17

dev.17 corrects shared-UI hit testing when Slick click-event coordinates drift from the current cursor. Native clicks still trigger actions; hit testing uses the polled cursor with scaling, masking and missing-cursor fallback preserved. Title-bar close now uses the supported X glyph. Offset replay passes; Windows focus switching still needs a real-game retest.

## Development build — API 0.3.3-dev.16

The caret incorrectly used MyDraw.tw (large-font toggle width including decoration). It now measures AGame.FOUNT, matching the rendered text, and centers text and caret vertically. Click semantics and focus are unchanged. The latest player log contained no new exception; this does not rule out missing input events. acbric-ui-input.log and acbric-ui-input.previous.log in the game user-data directory retain up to 64 KiB each. They record display activity, mouse press/release, native clicks, navigation keys, and framework barrier/layout/hit decisions, never text content. A write failure disables diagnostics without interrupting gameplay.

## Development build — API 0.3.3-dev.15

Fix Details returning to the main menu: modal input masking set the cursor to null, but native MyDraw.button requires a non-null drawing coordinate. Restore drawing cursor/state at render HEAD, before Screen.render; keep pointer/keyboard masking in input. Handle the close-release frame and an absent device cursor. Both old game builds reproduce the dev.14 failure; the fixed builds pass 132 UI/locale/native-render checks using real AirshipGame.render and MyDraw.button with a no-GPU terminal. Standard build: 698 checks; actual GPU visuals still require acceptance.

## Development build — API 0.3.3-dev.14

Fix Chinese detection for the native `chi` locale (also recognize `zh`/`zho`). Add `AcbricLanguage`, dynamic UI entry labels, localized default message actions and MOD description fields. Showcase 0.1.1 follows game language instead of the system locale. Lobby labels use the same selector. Add eight language assertions (698 total); both real Fabric game probes cover switching and language mismatch. No network protocol changes.

## Development build — API 0.3.3-dev.13

- Add public shared UI: labels, buttons, toggles, Unicode single-line editing, row/column/panel/scroll layouts, focus, tooltips, modal children and managed cleanup. See [UI contract](UI.md).
- Add framework MOD details/tool entries using the same components; preserve restart-based management, row icons and selection feedback. Independent showcase remains outside this repository.
- Mask native input after scaling while continuing game ticks. Guard opening/closing clicks, stale scroll hits, disabled ancestors, screen changes and cleanup reentrancy. Legacy event timing remains unchanged.
- Add 38 standard checks (690 total). Real Fabric probes cover both game builds, native input loops and scaling, tool factories, actual details callbacks, and clip restoration through a recording renderer. OpenGL, IME and full GUI acceptance remain pending; previous multiplayer/restart results were not rerun in full.

## Development build — API 0.3.3-dev.12

- Add Java MOD enable/disable controls, disk inventory including disabled archives, initialization failure and pending restart states; see [contract](MOD_MANAGEMENT.md). Changes save immediately and apply on the next restart.
- Merge persisted and external disabled IDs in the launcher before dependency resolution, entrypoints and Mixin application. Protect core mods; precheck dependencies, versions, aliases, hard conflicts and nested metadata. Use config locks, snapshot conflict checks and atomic replacement; keep JAR files intact.
- Block owned bundled native resources according to the startup snapshot, preserving preferences and files. Legacy unowned directories require explicit migration. Update launcher and API together; an older launcher makes UI management read-only.
- Add 55 checks (652 total) and real restart-chain tests for both game builds covering entrypoints, Mixin execution, nested libraries, inventory and native resources. Still 21 mixins; no new stable public API. Rendered GUI/mouse interaction and full campaigns remain outside headless validation.


- Both builds pass 114 restart checks, 56 old-launcher/failed-entry/button-callback checks and six expected startup failures. All 57 public types/237 member signatures from dev.11 remain.

## Development build — API 0.3.3-dev.11

- Add read-only saved-rule reports, registered direct schema upgrades, explicit missing-rule adoption and immutable conversion previews. Publish a separate native directory save; never overwrite the source or an existing destination. See [contract](RULE_SAVE_MIGRATION.md).
- Run rule preflight at native file-load return before world construction, with MOD IDs and versions; keep the constructor check before LOADED. Loading/serialization never executes converters. Adds one mixin (21 total).
- Keep unrelated binary chunks, MOD namespaces and absent rule entries. Reject malformed/unsupported storage, stale source/declarations and invalid conversion outputs. Single-file save conversion is outside this release.
- Add 56 checks (597 total), six real Fabric processes/136 checks and four dual-client experiments/54 checks. Separate workspace test MOD provides legacy/v1/v2 variants; it changes no combat mechanics. Full procedural generation, GUI and cross-machine acceptance remain pending.


## Development build — API 0.3.3-dev.10

- Add `context.sharedRules`, immutable snapshots and explicit candidate updates; see [shared rules](SHARED_RULES.md). New campaigns freeze confirmed values before generation; resume reads saved values without changing local config.
- Extend lobby checks with declared rules and field paths; changed candidates revoke preparation. Internal ready metadata v2 binds the rule digest. Adds `WorldGenScreenMixin` (20 mixins total).
- Persist the reserved rule set through existing campaign data. Reject missing/incompatible declared rules before LOADED; retain absent MOD entries and do not silently migrate old saves. Existing nonparticipating MOD APIs remain.
- Add 64 checks (541 total), a disabled-by-default template example and bilingual guides. Two game snapshots pass 14 real local-network experiments (204 top-level checks). Public baseline signatures remain: 31 types/125 members and dev.6's 46 types/185 members.
- Real-loader checks pass with original sample/template and current template. An exploratory mixed fixture failed on a separate shot-target MOD's Steam-incompatible Redirect; it is not claimed compatible or changed here. Full GUI/world generation/cross-machine acceptance remains pending.


[中文改动记录](CHANGELOG.zh-CN.md) | [API guide](API.md) ([中文](API.zh-CN.md))

## Unreleased — API build 0.3.3-dev.9

- Connect campaign lobbies to automatic code checks, a bilingual status/retry button and native ready/start guards. Preserve native resource and player requirements; direct actions also recheck conditions. See [integration contract](LOBBY_HANDSHAKE.md).
- Bind preparation and host updates to the complete current member/session batch. Revoke stale preparation after context/reconnect changes, native setup changes or retry. Preserve unchanged intent during routine renewal; complete host updates provide a bounded start window.
- Track native socket/reconnect generations and transfer room context when ResumeScreen recreates a lobby without a new welcome. Support native LAN channel 0, preserve unrelated messages and filter reserved protocol tails before campaign interpretation.
- Use the in-memory startup manifest. Add three mixins (19 total), extend the recovery hook, retain public MOD APIs and save formats. Legacy MODs need no rebuild; peers still need matching framework/code. Shared settings/state synchronization is not added.

Validation: 477 standard assertions (44 new lobby checks), 104 top-level checks in ten final native Server/dual Fabric Client experiments on 1.2.15.2 and 1.2.14, and retained original 31 types/125 members plus dev.6's 46 types/185 members. Probes cover real lobby methods and transformed recovery hooks with minimal fixtures, not full GUI, successful world generation or official-server authentication. Full new/resumed campaign and cross-machine acceptance remains manual.

## Previous development build — API 0.3.3-dev.8

- Add a package-private code-handshake protocol and state machine: per-context/per-peer challenges, roster invalidation, bounded retries, explicit timeout, expiring confirmation and immutable diagnostic snapshots. See [contract](CODE_HANDSHAKE.md).
- Bound wire messages and manifests below native transport limits; oversized manifests remain explicitly unverifiable. Reject stale/history/misaddressed messages and unsupported formats without falling back to success.
- Preserve raw numeric types during strict protocol/manifest parsing before serialization. Existing configuration normalization is unchanged.
- No lobby UI, ready/start gate or automatic multiplayer synchronization is connected. No public MOD API or mixin changes.

Validation: 433 standard assertions (77 new handshake checks). Actual native Server plus independent Fabric clients on games 1.2.15.2 and 1.2.14 exercise the product engine through an experimental adapter; 70 real communication assertions and six CLI checks pass; original 31 types/125 members and dev.6's 46 types/185 members are retained. GUI and cross-machine acceptance remain outside this stage.

## Previous development build — API 0.3.3-dev.7

- Export a local startup code manifest from resolved Loader roots, game identity and Acbric entrypoint results. Handle nested/development roots, normalized content hashes, bounded reads and explicit unverifiable states.
- Add a strict offline JSON comparator and `compareCodeManifests` Gradle task with directional differences and distinct match/difference/unverifiable/invalid outcomes. See the bilingual [tool guide](CODE_MANIFEST.md).
- Preserve public MOD APIs and all 16 mixins. No room handshake, ready gate, shared-settings exchange or automatic synchronization is added. Startup export failure only warns.

Validation: 356 standard checks (45 new manifest checks); two actual Fabric/game probes export 8/13 resolved containers including nested and legacy mods, while existing 5 event, 10 storage and 31 lifecycle/demo checks pass per game. Six CLI process checks cover all exit outcomes and real-manifest self-comparison. Original 31 types/125 members and dev.6's 46 public types/185 members remain. No live multiplayer or full GUI acceptance claim.

## Previous development build — API 0.3.3-dev.6

- Add EventScope/context.eventScope(name): grouped cleanup, one-shot consumption, stale-snapshot deactivation and listener-reference release. MODs explicitly manage application/campaign lifetimes.
- Name all 34 built-in events. Managed failures report MOD/event/scope/thread context in the current startup session runtime-events.jsonl, with occurrence/count/byte limits and best-effort logging that preserves original exceptions.
- Preserve legacy registration, order, cancellation and exception behavior; legacy listeners are not automatically wrapped. Update the template and independent demo v3.1 (F12 probe; campaign schema 3/config schema 2 unchanged). See bilingual [contracts](EVENT_SCOPES.md).

Validation: 311 standard checks (81 new scope checks and 27 diagnostic checks). Games 1.2.15.2 and 1.2.14 each pass 5 transformed-event diagnostic, 10 campaign-storage and 31 lifecycle/demo checks. The former loads the new template, the latter legacy mods. All 31 original public types/125 members and dev.5's 45 public types/174 members remain. Probes use minimal fixtures, with explicit event dispatch for some policies. Full GUI/live multiplayer acceptance remains manual.

## Previous development build — API 0.3.3-dev.5

- Add `AcbricModContext.config` and `ModConfig`/`ConfigSnapshot`/`ConfigException`: strict namespaced JSON, missing defaults, validation, explicit migration/reload and defensive snapshots.
- Save through a locked, conflict-checked, same-directory atomic replacement with a one-generation raw backup. Corrupt/future files and failed migrations are not silently reset. See the bilingual [configuration contract](CONFIG.md), included in distributions.
- Extend the separate demo to schema 3: F11 reloads local preferences; gameplay increments are frozen in campaign data. New multiplayer campaigns and old-save migrations use deterministic defaults, not peer-local config. Existing public members, storage format and the 16 mixins are unchanged.

Validation: 203 headless checks (62 new config checks). Games 1.2.15.2 and 1.2.14 each pass 10 campaign-data and 28 lifecycle/demo-v3 checks through real Fabric; configuration-policy checks also dispatch events explicitly. The 1.2.14 run includes legacy mods. All 31 original public types and 125 members/descriptors remain. Evidence is recorded in the workspace research directory. Full GUI and live multiplayer testing remain manual; dev.4 user feedback is not dev.5 acceptance.

## Previous development build — API 0.3.3-dev.4

- Add `AirshipsCampaignEvents.CREATED`, `LOADED`, `RESTORED` and `EXITED`. Generation readiness precedes the first autosave/state snapshot; JSON loading occurs after extension restoration; recovery hands off actual world identities without replaying initialization.
- Track active campaign references per client and release them on observed menu/lobby transitions or normal application exit. Temporary screens and recovery waiting do not imply exit. See [lifecycle contracts](CAMPAIGN_LIFECYCLE.md) and its Chinese counterpart, included in distributions.
- Supply a separate workspace demo mod with schema 1/2 builds, explicit migration, a read-only restoration callback, on-screen data and singleplayer test controls. It is not included as a default framework mod. Storage format and existing public members are unchanged.

Validation: 141 standard assertions; real Fabric probes on games 1.2.15.2 and 1.2.14 each pass the existing 10 campaign-data checks plus 18 lifecycle/demo-v2 checks. The demo-v1 probe passes 16 lifecycle/demo checks. Recovery checks use a transformed RETURN handler and minimal screen fixtures, not a live network connection. Full GUI generation/autosave and multiplayer acceptance remain manual.

User feedback after testing dev.4: no issues found so far. No per-scenario checklist was supplied; this does not establish complete multiplayer/recovery coverage.

## Previous development build — API 0.3.3-dev.3

- Add `AcbricModContext.campaignData(worldMap)` and the `net.fabricacs.api.save` API for campaign JSON data isolated by mod ID. Reads return defensive snapshots; writes validate and copy; explicit migration commits only after a successful callback and validation. Reject downgrades and retain data belonging to absent mods.
- Attach storage to `WorldMap` serialization/restoration, including native disk saves and state recovery. A separate unversioned block prevents the game's same-age cache from hiding updates; its JSON text payload supports null values unsupported by the native binary/hash encoding. Capture immutable data when registering each deferred write.
- Read old saves without an extension as empty. Reject missing, damaged or unsupported declared blocks instead of replacing data with defaults. Save/hash operations never run migration callbacks. Writes do not broadcast network commands.
- Add bilingual [campaign data documentation](CAMPAIGN_DATA.md) and an opt-in, compiled `CampaignDataExample` in the template. The template now requires API `>=0.3.3-dev.3`; existing public members and event semantics are retained. City-upgrade features, automatic object attributes and network command APIs remain outside this change.

Validation: 131 standard headless assertions (31 new campaign assertions). Real Fabric probes on Steam `1.2.15.2` and packaged `1.2.14` each pass 10 additional campaign checks covering transformed map construction, native binary save/load, `CampaignWorld` round trips, actual `StoredState` recovery, same-age hash changes and frozen earlier snapshots. These supplement the existing 18 target classes and 22 rename-panel combinations per build. User feedback confirms dev.1 and dev.2 run normally; dev.3 still needs full gameplay and live multiplayer/reconnect acceptance.

## Previous development build — API 0.3.3-dev.2

- Read `AGame.VERSION` from bytecode before Fabric dependency resolution, without initializing game classes. Report recognized versions (including four-component versions) as the built-in `airships` version. Preserve an unrecognized raw value with a warned `0.0.0` fallback.
- Fingerprint the ordered game code archives using SHA-256; record per-archive hashes, version source and shadowed definitions. This identifies inputs, not compatibility.
- Write per-session local launch and `acbric` entrypoint reports under `game/logs/acbric/<UUID>/`. Distinguish loaded mods, mods without an Acbric entrypoint, pending/running initialization, success and failure. Retain each entrypoint result and stack trace; partial failure is not overwritten by a later success.
- Preserve entrypoint failure continuation and propagation of main-method failures. Diagnostic file-write errors warn without preventing startup. No new public mod APIs, city-upgrade functionality or generic configuration service is introduced.
- Include the English API guide and bilingual diagnostic guides in distributions. See [diagnostics](DIAGNOSTICS.md) for fields, phases and coverage limits.

**Dependency compatibility:** mods pinned to the previous `airships =0.0.0` placeholder may now fail Fabric resolution. Use tested real versions, or `*` when intentionally unconstrained; the framework does not bypass dependency checks. Public API signatures and event behavior remain unchanged; the template still supports API `>=0.3.3-dev.1`.

Validation: standard build passes 100 headless assertions (20 new); 31 baseline public types / 125 existing member descriptors remain available. Real Fabric/Mixin probes passed against Steam `1.2.15.2` with mixed libraries and packaged `1.2.14` with separated libraries and its existing mods. Each probe loads 18 target classes and exercises 22 rename-panel combinations. A real failing entrypoint constructor still permits the next entrypoint to run; a deliberately failing main method retains a nonzero exit and `MAIN_FAILED` report. These are isolated headless checks, not full GUI, gameplay, save or multiplayer acceptance. Previous manual feedback applies to `0.3.3-dev.1` only.

## Previous development build — API 0.3.3-dev.1

Existing public API method names, types and JVM descriptors remain compatible with the
0.3.2 baseline; some Event methods now synchronize registry access.
This is an unpublished development version. New event consumers and the updated template
declare `acbric_api >=0.3.3-dev.1`; old 0.3.2 API members remain available.

- Preserve the game's data-loading failure result and diagnostics. `DATA_LOADED` now
  reports the actual result. A data pack previously accepted because its errors were
  suppressed may now fail to load; fix its data rather than treating failure as success.
- Validate all bundled resource paths before extraction, reject linked destination
  directories/ancestors, and extract into a staging directory outside the scanned mods
  folder. Publish only after successful extraction; failed extraction is cleaned up
  and can be retried. Unmanaged directories are preserved. Windows access-denied
  failures during the final move receive a short, bounded retry.
- Separate launch libraries from game libraries in distributions, and prevent Loader,
  Mixin, ASM and the launch shim from being loaded again by the game class loader in
  legacy combined-library layouts. Archives containing these infrastructure classes
  are treated as launch libraries, even if renamed.
- Add the distribution `run.bat` and framework LICENSE; check that the launcher exists
  before assembling a distribution. The launcher uses the bundled Java runtime.
- Make standard `build` compile/package the API and run headless regression checks.
  Fix Java source encoding to UTF-8, specify Java 21 output and declare `java >=21` in
  the API metadata. Remove the machine-specific JDK path from Gradle properties.
- Track bundled resource ownership with SHA-256 hashes. Update/remove unchanged owned
  files, preserve user edits/deletions and unowned collisions, and record conflicts.
  Stage directory replacements with a recovery journal and retain original backups.
  Provide explicit offline migration for old directories; only matching files are
  adopted, and no unknown legacy content is replaced. See [resource management](BUNDLED_RESOURCES.md).
- Fix once-listener removal, cancellation, recursive/concurrent invocation and retained
  dispatcher snapshots. A consumed once-listener uses the factory's empty-list invoker
  to produce the event's neutral result. Handles identify one registration and repeated
  unregistration is harmless. Ordinary dispatcher snapshots and exception propagation
  retain their previous behavior.
- Validate the staged install candidate with Fabric Loader's metadata parser before
  publishing it. Invalid JSON, schemas, IDs and metadata structure are rejected;
  dependency resolution, nested archive contents and mixin execution are not preflighted.
- Display nested Fabric mod origins without calling unsupported path accessors. Isolate
  individual row-construction failures and avoid duplicate rows on repeated refresh.

- Add `RENAME_SHIP_*` draw/tick events with `RENAME_SHIP` context. Deprecate the
  twelve mislabeled `ONE_SHOT_*` fields and three context labels without removal
  or rerouting. Legacy groups run first; cancellation skips subsequent groups, the
  original method and all AFTER groups. See [event contract and migration](EVENTS.md).

### Deliberately unchanged / remaining limits

Unmanaged directories are not migrated automatically. Backups are retained and are not
automatically pruned; removing a Java mod does not automatically delete its extracted
resources. Nested/directory mod resource extraction is still unsupported.

Legacy one-shot UI hooks still trigger on RenameShipPanel by compatibility design.
Actual one-shot weapon, buoyancy and power action events are not introduced here.
Listener exceptions still propagate.

### Validation scope

`build` runs 80 assertions covering error-result observation, resource ownership,
migration, rollback/recovery, extraction boundaries, subscription semantics, metadata
validation, rename-panel hook contexts and launch classpath selection (symbolic-link checks may skip without OS
privileges). Local integration probes also exercise transformed game methods,
old example mod loading, nested list refresh, staged installs and launch paths. Those
probes use locally supplied game binaries and are not a claim of complete GUI, gameplay,
save or multiplayer compatibility.

The separately assembled full runtime was manually tested and reported to behave
normally, close to the original package. No exhaustive scenario/mod matrix was supplied.

This maintenance also documents the complete API in Chinese and adds Chinese file
headers and focused implementation comments across all 43 Java source files and
owned build/launch scripts. Comment maintenance does not change runtime logic.
