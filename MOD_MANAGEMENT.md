# Java mod enable/disable management

**English** | [中文](MOD_MANAGEMENT.zh-CN.md)

API `0.3.3-dev.12` adds Java/Fabric mod controls to the game's mod list. **Changes apply on the next full game restart. Update both the launcher JAR and the API JAR.** No running classes or mixins are unloaded, and no mod archives are moved, renamed or deleted.

## Workflow

Open the mod screen and use **Disable** or **Enable** on a Fabric row. The selection is saved immediately; the status shows the pending restart action. Click again to undo. Restart the game to apply it. Disabled archives remain listed and can be enabled again.

Vanilla Apply, Reload, Reset and Disable All still apply only to vanilla data mods. They neither apply nor undo Java mod selections. Synthetic Java rows remain outside vanilla hot reload. Mod settings, saves and resources are preserved; whether a save works without a gameplay mod depends on that mod.

Statuses distinguish loaded code, failed Acbric entrypoints, disabled mods and pending restart actions. Entrypoint success does not validate other entrypoint types, background tasks or gameplay.

## Scope and dependencies

- Manages regular, visible, lowercase `.jar` files directly in the mod directory, normally `game/mods`; `fabric.modsFolder` is recognized.
- Protects `acbric_api`, `fabricloader`, `airships`, `java` and `mixinextras`. Nested mods, development directories, multiple-path and external classpath origins are read-only. Manage embedded libraries through their parent mod.
- Prechecks required `depends`, versions, `provides` aliases and hard `breaks` conflicts. Disable dependents first; enable libraries first. Other mods are never changed automatically.
- Reads declared nested metadata within size, count and depth limits so parents with embedded dependencies can be enabled again. Complex candidate/version selection, cyclic groups and unscanned external sources may not be adjustable one row at a time. Fabric remains the final dependency resolver.
- Changed archives or configuration invalidate the inspected snapshot. Reopen the screen before retrying. Invalid metadata, duplicate IDs or unreadable archives make that inventory unavailable; files are preserved and the cause is logged.

This release does not implement removal, downloads, upgrades, automatic dependency repair or hot loading. The existing installer validates metadata and refuses same-filename overwrites.

## Bundled vanilla resources

On the next launch, a disabled Java mod's resource directory under vanilla user data `mods/<Java mod ID>` is blocked when it has valid `.acbric-bundle.json` ownership. Original resource bytes and native enable preferences remain intact. Re-enabling Java code restores normal native enable checks; it does not force a previously disabled native resource mod on.

Ownership is checked by directory, so a different native ID in `info.json` works. Legacy unowned bundles require [explicit resource migration](BUNDLED_RESOURCES.md) first; the UI refuses to assume they are safely disabled. Unrelated same-named native directories are not adopted. Copies relocated elsewhere, cached copies and unowned resources are outside this filter.

## Configuration and startup

`game/config/acbric_mod_manager.json`:

```json
{
  "schemaVersion": 1,
  "disabledMods": ["example_mod"]
}
```

A missing file means no manager-disabled mods and is not created by reading. A legacy object containing only `disabledMods` is accepted. Unknown or duplicate fields/IDs, invalid types, protected IDs, malformed JSON and files over 64 KiB are rejected without reset. Writes use a lock, expected-byte conflict checks and atomic replacement. Unsupported atomic moves fail explicitly. Linked/special paths are rejected; this is not a defense against hostile concurrent filesystem replacement.

The Provider reads the file during game location and merges external `fabric.debug.disableModIds`. The pinned Fabric Loader 0.19.3 filters candidates before dependency resolution, entrypoints and mixin application. **Archive metadata is still scanned and parsed**; disabling cannot necessarily bypass corrupt archives or malformed metadata. Mods disabled by external launch arguments are read-only in the UI.

The active startup snapshot is separate from pending settings, so clicks do not change the current code manifest, lobby checks or resource state. The API makes management read-only when the updated launcher is missing.

If damaged configuration prevents startup, close the game, back up and correct the file. An explicitly empty `disabledMods` list restores manager-disabled mods. If a manually disabled library causes a Loader dependency error, restore it or disable its dependents as well. The framework does not suppress Loader errors. Distribution packages exclude this local selection and lock file.

## Validation

Regression tests cover configuration, protected components, dependency/alias/conflict checks and disk conflicts. Separate real Fabric processes for both game builds cover enabled → disabled → enabled restarts, actual entrypoint/mixin execution, nested libraries, list membership and native resource checks.

Headless tests do not verify rendered controls, mouse interaction, complete campaigns or every third-party mod. Management classes are internal implementation, not a new stable public API.
