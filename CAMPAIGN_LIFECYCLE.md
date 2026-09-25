# Campaign lifecycle events

> See [dev.11 conversion](RULE_SAVE_MIGRATION.md) for save preflight, explicit adoption/migration and save-as boundaries. Normal loading still does not migrate.

> dev.10: see [shared rules](SHARED_RULES.md) for explicit declarations, pre-generation freezing and resume checks. Existing local config/storage writes still do not broadcast. Versioned validation records below are historical.

**English** | [中文](CAMPAIGN_LIFECYCLE.zh-CN.md)

Available since `acbric_api 0.3.3-dev.4` in `net.fabricacs.api.event.AirshipsCampaignEvents`. Existing events and campaign-data APIs remain available; storage still uses dev.3 format 1.

## Events and timing

| Field / callback | Arguments and timing |
| --- | --- |
| `CREATED` / `onCreated(Object campaignWorld)` | Generated map content and player are ready, before the first autosave and multiplayer state snapshot. Runs after `CampaignWorld.setupPlayer()` during generation, at most once per generated instance |
| `LOADED` / `onLoaded(Object campaignWorld, boolean multiplayerLoad)` | Successful completion of `CampaignWorld(JSONObject, AirshipGame, boolean, InPipe)`, after extension data is restored. Covers singleplayer saves and save data received in multiplayer lobbies; does not imply activation or a local disk source |
| `RESTORED` / `onRestored(Object previousWorld, Object currentWorld)` | `ResumeScreen.input` returned normally and the recovered campaign is installed in the active screen. Supplies actual old/new objects and deduplicates the handoff; does not additionally emit CREATED, LOADED or EXITED for the old object |
| `EXITED` / `onExited(Object campaignWorld)` | An observed active campaign returns to the main menu/lobby, is replaced by another campaign, or the application exits normally. Screen transitions are observed at client input boundaries; not an immediate close-button callback or a crash/forced-termination guarantee |

World arguments are actual `CampaignWorld` instances; pass their `map` to `context.campaignData`. Events are non-cancellable, synchronous on the calling thread, and dispatched in registration order. Ordinary Event exception/snapshot semantics apply. The framework does not automatically migrate or roll back listener side effects. After initialization failure, stop using that campaign and fix the mod instead of overwriting data with defaults.

## Preparation is different from screen activation

The outer new-world constructor finishes before staged map generation completes. CREATED instead runs after the generation pipeline prepares the player and before its initial autosave, allowing deterministic initial data to be included.

LOADED represents one successful deserialization. A multiplayer lobby can reconstruct save objects multiple times: do not interpret this as once per campaign lifetime or grant rewards on every callback. `multiplayerLoad` is the original constructor flag; `world.isMultiplayer()` can still be false because its network client has not been attached. Shared data must not depend on local player identity or wall-clock time.

Mods may explicitly check/migrate schemas during save loading. Make this idempotent; data already at the supported version must remain unchanged. Recovery reconstructs the map and uses a different CampaignWorld constructor, so it does not emit LOADED.

## Recommended usage

```java
AirshipsCampaignEvents.CREATED.register(object -> {
    CampaignWorld world = (CampaignWorld) object;
    CampaignData data = context.campaignData(world.map);
    if (data.read().isEmpty()) data.write(1, new JSONObject().put("counter", 0));
});

AirshipsCampaignEvents.LOADED.register((object, multiplayerLoad) -> {
    CampaignData data = context.campaignData(((CampaignWorld) object).map);
    // Explicitly handle absent data, validate versions, and migrate when necessary.
    // Do not increase counters or grant rewards merely because a save was loaded.
});

AirshipsCampaignEvents.RESTORED.register((previous, current) -> {
    CampaignData fresh = context.campaignData(((CampaignWorld) current).map);
    // Replace cached handles and read recovered data; no writes, initialization or migration.
});

AirshipsCampaignEvents.EXITED.register(object -> {
    // Release local caches bound to this object; this is not a save callback.
});
```

The workspace's standalone `acbric-campaign-demo` project provides a complete example. Its listeners explicitly prepare its own namespace during creation/loading. The framework does not do this for other mods.

## Active sessions and recovery boundaries

Each client owns its active-campaign reference; there is no global world cache. Input-boundary observation recognizes strategic maps, technology screens and campaign-associated UniScreens. Unknown temporary screens preserve the reference instead of implying an exit. Recovery waiting does not exit; a successful recovery hook updates the reference before RESTORED. Ordinary screen refresh does not duplicate these events.

EXITED covers observed active campaigns. Save objects constructed and then discarded in a lobby do not necessarily receive EXITED. Internal references are updated/cleared before notification, preventing recursive duplicate exits even when a callback fails. Custom mods that bypass native paths or directly replace public fields are outside coverage. Cancelling world generation is not an active-campaign exit event.

RESTORED is a local notification that recovered state has been installed; peers need not reach it simultaneously. Only rebind handles, rebuild local views or read data. Do not mutate shared counters, grant resources or migrate. Java mods are not sandboxed against violating this contract.

## Validation scope

The standard build passes 141 assertions. Real Fabric/Mixin probes on games 1.2.15.2 and 1.2.14 cover generation preparation, successful/failed JSON construction, recovery waiting/installation conditions, identity deduplication, normal exit and demo migration. Recovery checks invoke the transformed target's RETURN handler using isolated minimal screen fixtures; they do not establish a real network connection or provide full GUI acceptance. Existing save/event checks also pass.

Manual acceptance must still cover campaign creation, initial autosaves, exit/reload, demo v1→v2 migration, absent-mod preservation and live multiplayer recovery. See the [campaign-data contract](CAMPAIGN_DATA.md).
