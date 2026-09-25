# UI events and compatibility

The new API is `0.3.3-dev.1` (unreleased). Mods using the new fields must declare
`"acbric_api": ">=0.3.3-dev.1"` in `fabric.mod.json` and compile against this API.
Existing 0.3.2 API members remain available. This is an additive change, not a
guarantee that every third-party mixin or every game version is compatible.

## Rename ship panel

`AirshipsCombatUiEvents` adds four independent events:

| Event | Game hook | Listener/context |
| --- | --- | --- |
| `RENAME_SHIP_BEFORE_DRAW` | `RenameShipPanel.draw` HEAD | `PlayerControlPanelBeforeDraw` / `PlayerControlPanelDrawContext` |
| `RENAME_SHIP_AFTER_DRAW` | `RenameShipPanel.draw` normal RETURN | `PlayerControlPanelAfterDraw` / `PlayerControlPanelDrawContext` |
| `RENAME_SHIP_BEFORE_TICK` | `RenameShipPanel.tick` HEAD | `PlayerControlPanelBeforeTick` / `PlayerControlPanelTickContext` |
| `RENAME_SHIP_AFTER_TICK` | `RenameShipPanel.tick` normal RETURN | `PlayerControlPanelAfterTick` / `PlayerControlPanelTickContext` |

All four pass `CombatUiPanelType.RENAME_SHIP` and the actual panel instance. Other
context values are the original method arguments, without copying. `elapsedMs`
is the tick's millisecond argument. Object-typed values may be cast to the matching
game classes; these interfaces retain the existing context/listener types.

Events run synchronously on the thread invoking the game method. They are per-call
draw/tick hooks, including calls when the panel's dialog is inactive. They do not
report a successful rename, a button click, a weapon firing, buoyancy or power use.
The existing `PLAYER_CONTROL_PANEL_*` events do not include this panel.

Within each event, listeners run in registration order. BEFORE returns `PASS` to
continue or `CANCEL` to stop later listeners/groups, skip the original method and
skip all AFTER events for that call. AFTER cannot cancel. A normal early return
from the original method still triggers AFTER. Exceptions propagate; these hooks
are not `finally` callbacks, and an exception stops subsequent listeners/groups.
Another mod injecting at the same method may affect this contract; ordering here
describes Acbric's own hooks, not an ordering guarantee over third-party mixins.

## Legacy compatibility

The twelve `ONE_SHOT_WEAPONS_*`, `ONE_SHOT_BUOYANCY_*`, `ONE_SHOT_POWER_*` fields
historically fired on **RenameShipPanel**, despite their names. They still do.
Their event objects, listener signatures, context labels, ordering and cancellation
remain separate and unchanged. The three legacy enum constants also remain in their
original positions. These members are deprecated with `forRemoval = false`.

For each draw or tick call, Acbric runs:

1. Legacy BEFORE: weapons, buoyancy, power (stop at the first CANCEL).
2. New rename BEFORE (only if all legacy groups passed).
3. Original game method (only if the new group also passed).
4. Legacy AFTER: weapons, buoyancy, power, then new rename AFTER.

There is no forwarding/aliasing between old and new event objects. Registering the
same work in both APIs can execute it twice. A legacy cancellation also prevents
the new BEFORE group; a new cancellation happens after legacy BEFORE and prevents
the original method and both old/new AFTER groups. No listeners means no new
context allocation.

## Migration and future additions

If your code really extends the rename panel, replace the old subscription with
the matching `RENAME_SHIP_*` field, and change any `panelType()` check to
`RENAME_SHIP`. Do not keep both registrations for the same action.

```java
AirshipsCombatUiEvents.RENAME_SHIP_BEFORE_TICK.register(context -> {
    // Observe/update panel state here. PASS keeps normal game input processing.
    return EventResult.PASS;
});
```

If your intended feature is one-shot weapons/buoyancy/power, renaming the event
subscription cannot implement it. Those mechanics need separately verified game
hooks and a new, explicitly documented API. Never silently move the legacy hooks.

New hooks must document the exact game class/method, supported game build evidence,
before/after timing, argument meaning, cancellation, exception handling and ordering.
Keep compatibility hooks stable; use accurate names in new templates and examples.
Add a regression against the transformed game method when changing injection points.
