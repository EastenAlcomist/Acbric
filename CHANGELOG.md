# Changelog

[中文改动记录](CHANGELOG.zh-CN.md) | [API guide (Chinese)](API.zh-CN.md)

## Unreleased — API 0.3.3-dev.1

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
