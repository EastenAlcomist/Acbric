# Saved rule preflight and explicit conversion (dev.11)

**English** | [中文](RULE_SAVE_MIGRATION.zh-CN.md)

`acbric_api 0.3.3-dev.11` adds `CampaignRuleSaves` and opt-in direct schema migrations for [shared rules](SHARED_RULES.md). Normal loading still never adopts defaults or runs migrations. A native file-load hook reports incompatible declared rules before constructing the campaign. The existing campaign constructor also checks before `LOADED`, including multiplayer data arriving without the file-load entrypoint.

## Authoring contract

Register a pure conversion on your declared handle during initialization. A handler converts one older version directly to the handle's current version; there is no implicit chain, downgrade or same-version repair.

```java
SharedRules rules = context.sharedRules(2,
    new JSONObject().put("basisPoints", 10000), values -> {
        if (!(values.get("basisPoints") instanceof Integer n) || n < 1 || n > 100000)
            throw new IllegalArgumentException("Invalid basisPoints");
    });
rules.migration(1, (previousVersion, oldValues) ->
    new JSONObject().put("basisPoints",
        Math.multiplyExact(oldValues.getInt("percent"), 100)));
```

`migration(int sourceVersion, SharedRules.Migration)` rejects negative/current/newer versions, duplicate registration and handles not registered through the context. `Migration.migrate(int previousVersion, JSONObject values)` may throw `Exception`; input is a defensive copy and output is validated against the current schema and aggregate limits. Register before inspection. Callbacks must be deterministic and side-effect-free; framework rollback does not undo external effects caused by MOD code.

## Inspect, preview, publish

The public entrypoint is `net.fabricacs.api.rules.CampaignRuleSaves`. Call after all relevant MOD declarations are installed, on the game thread, while the source is not being saved and no campaign is active. The framework cannot detect arbitrary external writers or manage third-party threading for you.

```java
var inspection = CampaignRuleSaves.inspect(sourceDirectory);
var report = inspection.report();
// Display every issue to the user. A missing MOD declaration requires an
// explicit value supplied by the caller, e.g. chosen in a conversion UI:
var plan = inspection.prepare(Map.of(
    "my_mod", new JSONObject().put("basisPoints", 12500)));
// If there are no MISSING entries, pass Map.of() instead.
// Display plan.changes(), including before/after versions and values.
Path converted = plan.writeNew(newSaveDirectory);
```

The `adoptions` map must contain **exactly** the IDs reported `MISSING`. It is not a general override map. Already present matching or invalid rules cannot be overwritten with local defaults. An older schema requires its registered converter. Review a new plan whenever the source, declarations, candidate values or migration registrations change.

| Type/member | Contract |
| --- | --- |
| `inspect(Path)` | Read an immutable snapshot of a native directory save; no campaign construction, migration or file writes |
| `Inspection.source()` / `report()` | Canonical source path / immutable compatibility report |
| `Inspection.prepare(Map<String, JSONObject>)` | Run explicit adoption and registered converters on copies; produce a validated in-memory plan |
| `Report.issues()` / `compatible()` / `summary()` | Immutable issues, compatibility result and bilingual diagnostic with MOD IDs/versions |
| `Issue.modId()` / `savedVersion()` / `targetVersion()` / `status()` | Version `-1` means missing saved entry or absent current declaration |
| `Plan.changes()` / `report()` | Immutable before/after changes and validated resulting report |
| `Change.modId()` / `before()` / `after()` | `before == null` for explicit adoption; snapshots are immutable |
| `Plan.writeNew(Path)` | Publish to an absent destination; does not rerun migration or validation callbacks |

Statuses: `MATCH` is valid current data; `MISSING` requires explicit initial values; `MIGRATABLE` has a registered direct converter but is **not** load-compatible until converted; `UNSUPPORTED_VERSION` needs a matching MOD or a supported converter; `INVALID` is rejected by the current validator; `RETAINED` belongs to an absent MOD and is preserved without invoking its code. A report is compatible only when all entries are `MATCH`/`RETAINED`. Corrupt/unsupported storage structure fails inspection with `IOException`, rather than becoming an empty report.

Conversion, I/O conflicts and invalid results throw `IOException`; invalid registration arguments fail with `IllegalArgumentException` or `IllegalStateException`. No automatic retry, default replacement or fallback save recovery is performed. The public nested records/enums are part of this development API; `impl` classes remain internal.

## Files, boundaries and multiplayer

This release converts the game's native **IODirectory directory saves**, usually directories ending in `.json`. Legacy single-file JSON/compressed/packedJSON saves are explicitly unsupported by the conversion API. Normal native loading retains its format support and gains rule preflight. Conversion requires a valid primary `index.gug`; repairing corrupted primary indexes from backups is outside its scope.

The converter snapshots at most 20,000 regular files / 256 MiB total, directory depth 16 and 16 MiB per decoded binary-JSON chunk. It rejects internal symbolic links, junctions/other special files and unsafe index paths. Shared-rule limits remain unchanged. It verifies all indexed chunks exist, but this is a rule/storage preflight, not a complete validator for every game object or feature MOD schema.

Only the world extension marker, the `acbric_campaign_data` block and necessary indexes are rewritten in the output copy. Other binary chunks and ancillary files are copied byte for byte; other MOD namespaces, unknown rule entries and other fields in the reserved `acbric_api` payload remain. The original is never written or deleted, and acts as the retained pre-conversion copy. No extra backup of it is created.

The destination must not exist; its parent must exist, and source/destination must not overlap. Before publication the converter checks source content and the declaration revision again, verifies the staged output, then moves the staging directory without replacement. A changed source requires a fresh inspection. A failure before publication removes only that operation's temporary directory; hard termination may leave a hidden `.acbric-rules-*` directory. This does not promise power-loss durability or protection against hostile concurrent filesystem changes. Coordinate saves so no process writes the source during conversion.

Conversion does not modify an active world or a config file. Multiplayer uses the resulting saved rules and the existing full-peer lobby check. Distribute/load the converted save through the normal workflow; all peers still need matching game/framework/MOD code and saved rules. Do not convert independently using different local defaults or infer full state synchronization from rule agreement.

## Tests and example

The standard build passes 597 checks, including 56 new file/conversion checks; two existing symlink fixtures are skipped on this host for lack of permission. Two real game versions run the independent test MOD's legacy/v1/v2 variants: 136 checks across six Fabric processes cover actual `WorldGenScreen` construction, `setupPlayer`, native disk save/load, preflight, conversion actions and map-state reconstruction. These use small fixture maps, not procedural generation of a complete world or a rendered GUI.

Four native Server/two-Client experiments across both versions pass 54 top-level checks: converted matching rules allow preparation and differing converted rules block preparation/start. The successful path checks `canStart`, not a full multiplayer game launch. Cross-machine/official-server sessions and full gameplay remain manual.

The separate workspace project `acbric-rules-test` provides three mutually exclusive JAR variants and a bilingual HUD with inspect/preview/write/failure/reload hotkeys. It is distributed in the local test kit, not as a default framework MOD. It changes no damage mechanic. See the workspace test manual for installation and a full old-save upgrade exercise.
