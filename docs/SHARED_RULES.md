# Shared campaign rules (dev.10)

**English** | [中文](SHARED_RULES.zh-CN.md)

API `0.3.3-dev.10` adds explicit, per-MOD gameplay rule declarations. New multiplayer campaigns require matching code **and** matching declared rules before preparation/start. New campaigns freeze the selected values before world generation. Resumed campaigns use their saved values. The framework does not overwrite configuration files or synchronize arbitrary fields, commands or running state.

## Declare and read

Declare once in the context-aware `acbric` entrypoint. Use your validated configuration to construct the values; only include gameplay settings safe to disclose to other players. There is one declaration per MOD ID, assigned by the context. Require `"acbric_api": ">=0.3.3-dev.10"` in `fabric.mod.json`.

```java
SharedRules rules = context.sharedRules(1,
    new JSONObject().put("damagePercent", 100), values -> {
        Object value = values.get("damagePercent");
        if (!(value instanceof Integer n) || n < 1 || n > 10000) {
            throw new IllegalArgumentException("damagePercent must be 1..10000");
        }
    });
// world is the actual CampaignWorld; read after generation has started,
// in CREATED/LOADED, or during the campaign.
int damagePercent = rules.forCampaign(world.map).values().getInt("damagePercent");
// An explicit configuration reload may change candidates for future campaigns:
rules.update(new JSONObject().put("damagePercent", 125));
```

Imports: `net.fabricacs.api.rules.SharedRules`, `org.json.JSONObject`. Retain the handle in your MOD. Use `forCampaign(world.map)` in gameplay; `current()` is only the candidate for a **new** campaign. An update never modifies an existing campaign or writes a config file. Obtain the current map again after native recovery replaces it. The optional template `SharedRulesExample` compiles but is not called by default and implements no damage mechanic.

| Public member | Meaning |
| --- | --- |
| `context.sharedRules(version, values, validator)` | Register one declaration owned by the context; duplicate declarations fail |
| `SharedRules.modId()` / `current()` | Owning ID / immutable new-campaign snapshot |
| `update(JSONObject)` | Validate and replace the candidate; failure keeps the old value; equivalent values do not invalidate preparation |
| `forCampaign(Object worldMap)` | Read and validate saved rules for this MOD; missing values or schema mismatch fail |
| `checked(SharedRuleSnapshot)` | Validate a snapshot against this handle's schema and validator, without storing it |
| `new SharedRules(id, version, values, validator)` | Construct an unregistered handle; this alone does not participate in lobby checks or freezing. Prefer the context factory |
| `new SharedRuleSnapshot(version, values)` | Construct an immutable, bounded snapshot; does not register it |
| `SharedRuleSnapshot.version()` / `values()` | Nonnegative integer schema version / independent JSON copy |
| `SharedRules.Validator.validate(JSONObject)` | Validate a copy; may throw `Exception` to reject it |

`checked`/`forCampaign` throw `IllegalStateException` for schema mismatch; validator exceptions are wrapped in `IllegalArgumentException`. Missing saved rules throw `IllegalStateException`; unsupported maps fail. Invalid JSON values/limits fail at snapshot construction. `equals`/`hashCode` on snapshots compare version and canonical values. Validation never applies mutations made to its copy.

Use declaration/update/read on the game thread and keep validators deterministic, quick and free of side effects. Do not register/update rules or write configuration, saves or network messages inside validation. Validation may occur during declaration, update, campaign read, resumed-lobby refresh and load; it must depend on the declared schema and supplied values, not the current machine's local preference. Reentrant validation is rejected. Synchronization protects handles, not arbitrary cross-thread game operations.

## Freeze, save and resume

The native `WorldGenScreen(CampaignWorld, AirshipGame)` constructor RETURN hook freezes values before generation and before `CREATED`/the first autosave. Singleplayer selects current candidates; multiplayer requires the snapshot confirmed by its campaign lobby. Custom multiplayer entry paths bypassing that lobby are unsupported. Later local changes affect only future campaigns. Even an empty declaration set is frozen for a new campaign.

Rules are kept in the reserved `acbric_api` campaign-data namespace, schema 1, under `sharedRules` as canonical JSON text. This avoids the game's floating-point JSON encoding changing values. MODs must not edit this namespace. The existing native save/recovery pipeline preserves it and unknown MOD rule entries. This is persistence through existing map serialization, not a new live synchronization system.

On JSON campaign load, structural and declared-schema validation must succeed **before** `LOADED`. Invalid data aborts loading with `IOException`. Native map reconstruction checks structure only, without running MOD validators or migrations. `forCampaign` validates the requested rule. Resume checks compare the saved set, including entries for currently absent MODs; they do not compare current local candidates.

An old save with no declaration block remains usable when no installed MOD declares rules. If a MOD now declares a rule absent from that save, loading/resuming fails with `RULE_MISSING`. Unsupported schemas/invalid values also fail. There is no implicit default or host override on load. dev.11 adds separate [explicit adoption and migration](RULE_SAVE_MIGRATION.md): preview and save a new copy before loading; normal loading does not convert saves. MOD authors must plan save compatibility before opting in or changing schema versions. Existing MODs which do not opt in retain their public APIs and behavior; their undeclared settings are not covered by these checks.

## Equality, limits and protocol

Object keys are sorted; array order matters; equivalent decimal numbers such as `1` and `1.0` compare equally. The schema version, MOD ID and source (`NEW` versus `SAVED`) also participate. Supported values are objects, arrays, strings, JSON null, booleans, integral primitive wrappers and finite `Float`/`Double`. Arbitrary objects, cycles, invalid Unicode and nonfinite numbers are rejected.

Limits: at most 64 MOD declarations; each encoded snapshot and the **entire encoded rule set** at most 12,000 UTF-8 bytes, so metadata/escaping reduces the space available for values; nesting at most 16 and traversal at most 4,096 nodes per encoding. Use small settings, not world state. Oversized aggregate sets are unverifiable. Differences identify MOD/schema/JSON paths without printing values; the wire payload nevertheless includes declared values.

Internal message `acbric:campaign_rules` v1 is bounded to 40,000 UTF-8 bytes and bound to room, sender and the complete verified code-session map. Attempts occur at two-second intervals, at most five within ten seconds. History, wrong sessions/rooms, unsupported formats and late unconfirmed replies cannot succeed. Contradictory reports within one context remain unverifiable until a fresh check. Native `acbricLobby` preparation metadata is now v2 and includes the rule-set `rulesDigest`; the code handshake itself remains v1. These internal formats are not public MOD networking APIs.

The bilingual lobby status distinguishes waiting/checking, matching, different, unverifiable and timed-out rules. Click the status button to retry. A changed candidate starts a fresh code/rule context and clears preparation. Matching rules are required in addition to native resource/player conditions and current all-player preparation proofs. This is self-reported consistency checking, not authentication, consensus, anti-cheat or proof of deterministic gameplay.

## Verification

The build passes 541 assertions (64 new rule checks); two pre-existing symlink scenarios are skipped because this host lacks permission. Games 1.2.15.2 and 1.2.14 pass 14 isolated native Server/two-Fabric-client experiments with 204 top-level checks: matching/different rules, missing/version-incompatible saves, saved rules overriding local candidates, post-ready updates, and no-declaration ordinary-room compatibility. Tests invoke the transformed generation hook and native storage pipeline with minimal fixtures; they do not generate a full world or complete GUI gameplay. Official-server authentication, cross-machine sessions, full save/resume/recovery and sustained play still need manual acceptance.
