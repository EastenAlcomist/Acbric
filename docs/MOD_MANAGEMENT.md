# Java mod enable/disable management

**English** | [中文](MOD_MANAGEMENT.zh-CN.md)

Applies to `acbric_api 0.3.8`. The game's mod list provides Java/Fabric mod controls. **Changes apply on the next full game restart, and the launcher and API must come from the same distribution.** No running classes or mixins are unloaded, and no mod archives are moved, renamed or deleted.

## Localized Java mod descriptions

An optional localized description can be placed under `custom.acbric.description` in `fabric.mod.json` without changing the compatibility semantics of the standard `description` field:

```json
{
  "description": "English fallback description.",
  "custom": {
    "acbric": {
      "description": {
        "en": "English description.",
        "chi": "Chinese description.",
        "de": "Deutsche Beschreibung.",
        "fr": "Description française.",
        "jpn": "日本語の説明。",
        "kor": "한국어 설명.",
        "ru": "Описание на русском."
      }
    }
  }
}
```

Keys use the native game language codes, currently `en`, `chi`, `de`, `fr`, `jpn`, `kor` and `ru`. Acbric selects by the active game Locale; `zh`/`zho` alias `chi`, `ja` aliases `jpn`, and `ko` aliases `kor`. If the active language is missing or malformed, Acbric falls back to English, then to the top-level `description`, so existing Java mods require no changes. The localized value is used by Fabric rows in the native mod list and by Acbric's mod details window; the standard Fabric metadata still stores only the top-level single string.

## Workflow

Acbric API and Java MOD rows remain visible even when no vanilla MOD is installed, including disabled Java MODs after restart. Java rows remain outside vanilla resource loading, Apply and Reload collections.

Open the mod screen and use **Disable** or **Enable** on a Fabric row. The selection is saved immediately; the status shows the pending restart action. Click again to undo. Restart the game to apply it. Disabled archives remain listed and can be enabled again.

The multiplayer lobby panel offers the same operation without leaving the lobby: select the `Acbric` status button at the top left to view Java MODs (AcMod) and native MODs and choose **disable/enable on next start**. The panel writes only the next-start state and never hot-reloads.

Vanilla Apply, Reload, Reset and Disable All still apply only to vanilla data mods. They neither apply nor undo Java mod selections. Synthetic Java rows remain outside vanilla hot reload. Mod settings, saves and resources are preserved; whether a save works without a gameplay mod depends on that mod.

## ACMODs embedded in native MODs

The API scans both local and Steam-downloaded native MODs. If a native MOD root contains `AcbricMod/` with exactly one lowercase `.jar`, its row shows `carries AcbricMOD` and offers `Enable bundled ACMOD`. The API validates Fabric metadata, copies the JAR into the Java MOD folder, removes its ID from the disabled list, and then shows `Enabled`; Fabric loads it after the next restart.

Different files with the same target name, damaged metadata, multiple JARs, protected IDs and linked paths are rejected or reported as conflicts without overwriting existing files. This is built into the API; do not install `acbric_embedded_mods` separately.

Statuses distinguish loaded code, failed Acbric entrypoints, disabled mods and pending restart actions. Entrypoint success does not validate other entrypoint types, background tasks or gameplay.

## Scope and dependencies

- Manages regular, visible, lowercase `.jar` files directly in the mod directory: `mods` beside `Acbric.exe` in external releases (`fabric.modsFolder`, independent of the native MOD root), or normally `game/mods` in legacy layouts; `fabric.modsFolder` is recognized.
- Protects `acbric_api`, `fabricloader`, `airships`, `java` and `mixinextras`. Nested mods, development directories, multiple-path and external classpath origins are read-only. Manage embedded libraries through their parent mod.
- Prechecks required `depends`, versions, `provides` aliases and hard `breaks` conflicts. Disable dependents first; enable libraries first. Other mods are never changed automatically.
- Reads declared nested metadata within size, count and depth limits so parents with embedded dependencies can be enabled again. Complex candidate/version selection, cyclic groups and unscanned external sources may not be adjustable one row at a time. Fabric remains the final dependency resolver.
- Changed archives or configuration invalidate the inspected snapshot. Reopen the screen before retrying. Invalid metadata, duplicate IDs or unreadable archives make that inventory unavailable; files are preserved and the cause is logged.

This release does not implement removal, downloads, upgrades, automatic dependency repair or hot loading. The existing installer validates metadata and refuses same-filename overwrites.

## Bundled vanilla resources

On the next launch, a disabled Java mod's resource directory `<Java mod ID>` under the local native MOD root (`mods` beside `Acbric.exe` in external releases) is blocked when it has valid `.acbric-bundle.json` ownership. Original resource bytes and native enable preferences remain intact. Re-enabling Java code restores normal native enable checks; it does not force a previously disabled native resource mod on.

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

Management classes are internal implementation, not a stable public MOD API. Enabling or disabling does not verify a third-party MOD's gameplay, complete campaign saves or every module combination.
