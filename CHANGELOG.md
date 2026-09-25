# Changelog

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
