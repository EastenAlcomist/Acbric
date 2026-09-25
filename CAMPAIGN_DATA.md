# Campaign mod data

> dev.10: see [shared rules](SHARED_RULES.md) for explicit declarations, pre-generation freezing and resume checks. Existing local config/storage writes still do not broadcast. Versioned validation records below are historical.

**English** | [中文](CAMPAIGN_DATA.zh-CN.md)

Requires `acbric_api >=0.3.3-dev.3` (unreleased). This API stores shared, campaign-level JSON data per mod ID. It does not discover arbitrary Java fields, send network messages, or provide per-ship/city storage automatically.

## Access and lifetime

Keep the `AcbricModContext` supplied to your entrypoint. Once you have a live campaign, pass its **`WorldMap`** (normally `campaignWorld.map`):

```java
import net.fabricacs.api.save.CampaignData;
import net.fabricacs.api.save.CampaignDataSnapshot;
import org.json.JSONObject;

CampaignData data = context.campaignData(campaignWorld.map);
if (data.read().isEmpty()) {
    data.write(1, new JSONObject().put("counter", 0));
}
CampaignDataSnapshot snapshot = data.read().orElseThrow();
JSONObject next = snapshot.data();
next.put("counter", next.getInt("counter") + 1);
data.write(snapshot.dataVersion(), next);
```

This snippet shows explicit state changes. Execute it only at the intended gameplay point, on the simulation thread; do not run it in a draw callback or every serialization. In multiplayer, changes must originate from an agreed command or deterministic simulation step executed by all peers. Calling `write` locally does not broadcast anything.

`context.campaignData(worldMap)` scopes the handle to `context.modId()`. `new CampaignData(worldMap, modId)` is also available, primarily for helpers; use your own mod ID. Namespace separation prevents accidental collisions, not malicious access by code sharing the process. IDs follow Fabric's `[a-z][a-z0-9_-]{1,63}` shape.

A handle is bound to one map instance. Obtain a new handle when a different campaign is loaded or reconnection replaces the map. A retained handle does not automatically follow the active world. Passing `CampaignWorld`, null, or an untransformed object instead of an Acbric-enabled `WorldMap` throws `IllegalArgumentException`.

## Public API

Types are in `net.fabricacs.api.save`. `AcbricModContext.campaignData(Object worldMap)` returns `CampaignData`.

| Member | Contract |
| --- | --- |
| `CampaignData(Object worldMap, String modId)` | Bind to the given map and mod namespace |
| `modId()` | Namespace ID |
| `read()` | `Optional<CampaignDataSnapshot>`; empty means no data, not a stored empty object |
| `write(int dataVersion, JSONObject data)` | Validate and replace this namespace's value; copy all data, reject a lower version than already stored |
| `remove()` | Explicitly delete this namespace; return whether it existed, even if its schema is newer |
| `migrate(int targetVersion, CampaignData.Migration migration)` | Explicit atomic migration; return whether a value changed |
| `CampaignDataSnapshot(int dataVersion, JSONObject data)` | Construct an independent immutable snapshot |
| `CampaignDataSnapshot.dataVersion()` | Mod-owned, non-negative schema version, separate from the mod/API version |
| `CampaignDataSnapshot.data()` | A fresh mutable JSON copy on every call |
| `Migration.migrate(int previousVersion, JSONObject data)` | Return replacement JSON; may throw `Exception`; input is a copy |

Accepted payloads are JSON objects containing nested objects/arrays, strings, booleans, JSON null, integral Java byte/short/int/long values, and finite float/double values. Floating-point/integer representation is normalized through JSON text. Unsupported Java objects (including arbitrary `Number` subclasses), cycles and nesting deeper than 64 edges are rejected. No original input objects or returned mutable JSON objects are retained as live storage.

Invalid arguments throw runtime exceptions. A lower schema write or unsupported newer schema encountered by `migrate` throws `IllegalStateException`. These failures leave the previous stored value intact. Synchronization protects the container, not game objects: use the game simulation thread and avoid concurrent read-modify-write sequences.

## Explicit migration

`migrate` does nothing and returns false for absent data or an equal schema. A stored schema newer than the requested target is an error. For an older schema, your callback receives a copy; the framework validates its result and commits both version and data together. It does not automatically run callbacks during load, save, checksum generation or reconnection.

```java
data.migrate(2, (previousVersion, json) -> {
    if (previousVersion != 1) {
        throw new IllegalStateException("Unsupported stored schema: " + previousVersion);
    }
    json.put("visits", json.getInt("counter"));
    json.remove("counter");
    return json;
});
```

Handle every old schema that your mod supports. Callback failure or an invalid result leaves the original in-memory value intact and reports the failure; no disk write occurs. Mutating any namespace in the same map's store from inside a migration is rejected. Callbacks must not modify other game objects, other campaigns, files or random state; those external side effects cannot be rolled back by this API. Do not silently catch migration errors and overwrite data with defaults. See the template's `CampaignDataExample.java` for a compiled initialization/migration example; it is not automatically invoked.

## Storage and state recovery

`WorldMap.toJSON(OutPipe)` adds the reserved marker `acbricCampaignData: 1` to the map and registers an `acbric_campaign_data` block through the **existing game storage pipeline**. The block contains `format: 1` and a `payload` string encoding this logical JSON structure:

```json
{
  "format": 1,
  "mods": {
    "example_mod": { "version": 2, "data": { "visits": 12 } }
  }
}
```

JSON text is used inside the block because the game's binary writer and state hash do not support `JSONObject.NULL`. The framework captures a fixed snapshot when registering the block; a deferred writer never reads later live data. An independent `registerWithoutVersion` block also avoids reusing the game's map-age cache when mod data changes while paused. Empty extension blocks are registered too, so cached map markers remain valid when the first namespace is added or the last is removed.

This follows ordinary campaign saves, autosaves and save-as operations that use the same pipeline, as well as map snapshots and reconstruction during state recovery. It is part of the game's native multi-file/packed save structure, not a separately managed sidecar. Copy the **whole native save**, not just one `.gug` block. Acbric does not change the game's disk transaction or backup guarantees and does not claim that serialization means a successful disk save.

The JSON `WorldMap` constructor restores the block through `InPipe`. Old saves without the marker start empty. A declared but missing, corrupt or unsupported block aborts that load with an `IOException` identifying Acbric campaign data; it is not silently replaced with defaults. Structural validation of every namespace happens before data is accepted. Each mod still owns validation and migration of its payload schema.

All namespaces are retained whether or not their mod is installed, including unrecognized metadata within the supported envelope format. Mod schemas are preserved without running code from missing mods. Missing gameplay mods may nevertheless make a campaign unusable. Preservation requires this feature to be enabled: opening and re-saving with vanilla or older Acbric builds can drop the extension.

## Multiplayer boundary and verification

Save/recovery support is not automatic live replication. All peers need matching framework/mod behavior, gameplay configuration, initial state, commands, execution order and deterministic random usage. Keep local UI settings, local clocks and player-specific preferences outside this shared store. No new command transport, permission checks or mod-set handshake are added in this version. Standalone battle saves, ship designs and automatic object-attached attributes are outside this API's scope.

Validation includes isolated native binary save round-trips, namespace isolation, migration failures, immutable snapshots and state-hash cache behavior. Real Fabric probes against game `1.2.15.2` and `1.2.14` exercise the transformed map constructor, `CampaignWorld` serialization/load, disk round-trips and the actual `StoredState` path, including same-age updates. These checks do not replace a complete live campaign or two-computer multiplayer/reconnection test.

Lifecycle hooks for obtaining/rebinding maps are available since dev.4: [campaign lifecycle](CAMPAIGN_LIFECYCLE.md). The data API itself still requires only dev.3.
