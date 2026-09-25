# MOD configuration API

> See [dev.11 conversion](RULE_SAVE_MIGRATION.md) for save preflight, explicit adoption/migration and save-as boundaries. Normal loading still does not migrate.

> dev.10: see [shared rules](SHARED_RULES.md) for explicit declarations, pre-generation freezing and resume checks. Existing local config/storage writes still do not broadcast. Versioned validation records below are historical.

**English** | [中文](CONFIG.zh-CN.md)

Requires `acbric_api >=0.3.3-dev.5` and JDK 21. Files live under the Fabric game directory at `config/<modId>/<name>.json`, normally `game/config/` in a distribution. This is separate from native saves and native mods. Existing manually managed configuration files are neither scanned nor adopted.

## Example

```java
import net.fabricacs.api.config.ModConfig;
import org.json.JSONObject;

// Inside the context-aware entrypoint. Construction validates defaults but does no disk I/O.
ModConfig config = context.config("settings", 2,
    new JSONObject().put("showHud", true).put("newCampaignIncrement", 1), data -> {
        Object n = data.get("newCampaignIncrement");
        if (!(data.get("showHud") instanceof Boolean) || !(n instanceof Integer)
                || (Integer) n < 1 || (Integer) n > 10) {
            throw new IllegalArgumentException("Invalid settings");
        }
    });
config.load();
config.migrate((previous, data) -> {
    if (previous != 1) throw new IllegalArgumentException("Unsupported version");
    if (data.has("step")) {
        data.put("newCampaignIncrement", data.get("step"));
        data.remove("step");
    }
    return data;
});
config.save(); // Creating files, persisting defaults and migration all require an explicit save.
JSONObject settings = config.read().data();
```

The caller handles `IOException`, including `ConfigException`, and decides whether to disable a feature, report an error or keep previously active settings. Do not blindly overwrite defaults after a failure. This uses the game's `org.json`; its `getInt/getBoolean` methods coerce values, so inspect `get()` types for strict validation.

## Public API and state

Package `net.fabricacs.api.config` contains `ModConfig`, `ConfigSnapshot` and `ConfigException`. Prefer `AcbricModContext.config(String name, int dataVersion, JSONObject defaults, ModConfig.Validator validator)`. Tools and tests may directly construct `ModConfig(Path configRoot, String modId, String name, int dataVersion, JSONObject defaults, Validator validator)`.

| Member | Contract |
| --- | --- |
| `path()` / `backupPath()` | Primary / sibling `.json.bak` paths; no I/O |
| `load()` | Read once; subsequent calls return the current snapshot, retaining pending edits |
| `read()` | Current snapshot; throws `IllegalStateException` before loading |
| `reload()` | Explicit disk read; success discards pending edits, failure retains the previous snapshot |
| `update(JSONObject)` | Merge missing defaults and validate; replace memory only; requires current version |
| `migrate(Migration)` | Run only for an older version; current version returns false; success updates memory only |
| `save()` | Explicit commit; requires a loaded, current-version snapshot |
| `ConfigSnapshot.dataVersion()` / `data()` | Schema version / fresh defensive JSON copy |

`ConfigSnapshot(int, JSONObject)` validates the version and copies data. `Validator.validate(JSONObject)` and `Migration.migrate(int previousVersion, JSONObject)` may throw exceptions; migration returns an object. Invalid names/negative versions throw `IllegalArgumentException`; missing required arguments may throw `NullPointerException`.

A missing file yields current-version defaults without saving. For the current version, defaults recursively fill missing keys only: existing nulls, wrong types and arrays are not replaced. The validator decides whether they are acceptable. Unknown data/envelope fields survive. Saving reformats JSON; the backup preserves original bytes.

Older schemas receive structural checks only, without current defaults or validation. Explicit migration runs on a copy, then merges defaults and validates before replacing memory. Failure retains old memory and disk. Future versions are rejected, preventing downgrade overwrites. A callback may implement multiple upgrade steps; no migration route is selected automatically.

## Format and errors

```json
{
  "format": 1,
  "version": 2,
  "data": { "showHud": true, "newCampaignIncrement": 1 }
}
```

`format` is the framework envelope version, currently 1. `version` is the MOD's nonnegative integer schema version, at most 2147483647, independent of its release version. `data` must be an object. UTF-8 with optional BOM; strict JSON rejects comments, duplicate decoded keys, trailing commas, single quotes, trailing garbage and non-finite numbers. Numbers use the game's Integer/Long/Double representation; integers outside the long range may lose precision and should be strings. Maximum file size is 1 MiB for reads/writes. Data uses the campaign JSON 64-level depth check; the envelope parser has a separate 72-level limit. Arbitrary Java objects and cycles are unsupported.

Names match `[a-z][a-z0-9_-]{0,63}` without an extension; MOD IDs retain the 2–64 character rule. Both reject Windows device names. Symlinks and detected junctions/special paths are rejected. This is not a security sandbox against hostile local processes.

`ConfigException extends IOException` exposes `code()` and constructors `(Code, String)` / `(Code, String, Throwable)`.

| Code | Meaning |
| --- | --- |
| `INVALID_FORMAT` | Invalid UTF-8, JSON, envelope, data structure or depth |
| `VALIDATION_FAILED` | Defaults, updates, current-schema loads or migration results failed validation |
| `NEWER_VERSION` | Disk schema exceeds the declared version |
| `MIGRATION_FAILED` | Migration callback failed, re-entered the handle or returned invalid JSON |
| `CONFLICT` | Lock occupied, or raw disk bytes differ from the last successful load/save baseline |

Permissions, file size limits and atomic-move failures use ordinary `IOException`. Validators receive copies; their mutations are discarded. Callbacks should have no external side effects and cannot re-enter load/reload/update/migrate/save on the same handle. External callback effects cannot be rolled back. Synchronized instance methods protect one handle; they do not schedule game-thread work or provide cross-handle transactions.

## Saving and backup

A sibling temporary file is written and forced before atomic replacement. Unsupported atomic moves fail rather than falling back to truncating the primary file. Before replacing an existing file, its exact raw bytes are atomically saved as `.json.bak`, retaining one generation. Backup failure leaves the primary untouched. Byte-identical saves do not rotate backups. Initial creation has no prior file to back up and does not delete any existing historical backup. The empty `.json.lock` file normally remains on disk.

Locks coordinate writers using this API; disk bytes are checked again at commit. After external edits/deletion, reload before deciding to save. Editors do not honor the lock, so a narrow check-to-replace race remains; avoid simultaneous edits and saves. There is no guarantee of power-loss transactions, directory durability or multi-file transactions. In rare cases lock-close failure after replacement can report failure after disk has changed; reread before retrying.

There is no automatic backup recovery or silent reset of corrupt files. For manual recovery, stop the game, preserve the damaged file, copy the backup over the primary, then restart or explicitly reload.

## Local preferences and campaign rules

Configuration is not a save, is not checksummed or broadcast, and has no file watcher or automatic reload event. UI/log preferences may take effect after reload. Copy gameplay settings into `CampaignData` at the appropriate campaign creation stage; subsequent play/load must use saved rules rather than overwrite them from local settings.

Matching MODs do not imply matching configuration. Without authoritative parameter transfer, peers must not derive shared state from their own files. The independent v3 demo freezes `newCampaignIncrement` for new singleplayer campaigns. Old-schema migration, missing data on load and new multiplayer campaigns use deterministic 1; existing saved rules are retained. F11 reloads local settings only; F6/F8 multiplayer writes remain blocked. RESTORED still only rebinds and reads.

This revision adds no configuration GUI, automatic broadcast/migration or general network-command API. See [campaign data](CAMPAIGN_DATA.md) and [lifecycle](CAMPAIGN_LIFECYCLE.md).
