# Campaign lobby code check (dev.9–dev.10)

> dev.10 also requires [matching declared rules](SHARED_RULES.md). Code status below is only one prerequisite; unconfirmed rules block ready/start. Rules freeze before generation and resume uses saved values.

**English** | [中文](LOBBY_HANDSHAKE.zh-CN.md)

dev.9 connects the [internal handshake](CODE_HANDSHAKE.md) to native multiplayer campaign lobbies. Checks begin automatically on entry. Pending, different, unverifiable or timed-out code blocks preparation and game start. dev.9 added no public MOD networking API; dev.10 stores rules within existing campaign extension data.

## Player behavior

An `Acbric` status button replaces the connection text at the top left, in English or Chinese. Its tooltip preserves the original connection information and shows peer states, selected differences and scope. Click it to retry. Use the offline manifest comparator for complete differences and local startup reports for entrypoint failure traces.

| Status | Action |
| --- | --- |
| Waiting for room / peers | Wait for connection and another player |
| Checking code / waiting for host | Wait for checks and the current host update |
| Code match | Prepare once native resource/player requirements are also satisfied |
| Code differs | Align game build, Java version, framework and loaded mods; restart |
| Unverifiable | Inspect startup reports for failed entrypoints, hashing failures or limits |
| Timed out | Check connections and framework versions, then retry |

There is no bypass switch. Code match is only one requirement; native prerequisites and every player's preparation remain necessary. Explicit retry, room/member/connection changes and native map/empire-selection changes handled by the host revoke old preparation. Reconnecting or recreating a lobby requires preparing again.

Routine 30-second renewal temporarily stops accepting new preparation but retains intent when identities remain unchanged. Explicit retry clears that intent. A host update containing every current proof can grant a game-start execution window of at most two seconds, reducing split transitions at a lease boundary. Known connection/member/session changes and subsequent invalid host updates revoke it immediately. This does not guarantee atomic start or distributed consensus under network partitions.

## Compatibility and scope

- Existing public MOD APIs and event semantics remain. Legacy feature mods need no rebuild for this feature, but all peers need matching code and a framework supporting these checks. Mixing old framework versions is not supported; missing replies time out instead of succeeding.
- Only multiplayer campaign lobbies, both new and resumed campaigns, gain ready/start gates. Singleplayer and combat lobbies do not. The global receive hook still removes the framework's reserved messages so delayed packets cannot enter the campaign command interpreter.
- Native `modsLoaded`, reload/failure, empire selection and all-player-ready requirements remain. Native resource download checks are not replaced.
- Checks use the in-memory startup [code identity](CODE_MANIFEST.md), never an editable exported report. Retry neither reloads nor rehashes mods. Restart after changing files.
- Only declared new-campaign or saved rules are compared; complete local settings, effective native resources, expansions, other save data and live state are neither compared nor synchronized. Self-reported identities are not authentication or anti-cheat. Matching code can still produce different state.
- Resumed worlds may still be constructed before preparation completes. Existing `LOADED` timing is unchanged and is not proof of a successful handshake.

## Integration and preparation proofs

The product handles already-consumed messages at `AirshipGame.pollMessage` return, preserving unrelated message order, membership frames and native host processing. It performs no additional Client polling and consumes reserved `acbric:code_handshake` and `acbric:campaign_rules` messages. Current connection, welcome metadata and live member frames define context. History cannot confirm code/preparation; duplicate or out-of-order live frames are rejected.

**LAN campaigns may use channel 0**. Welcome metadata and active screen distinguish campaigns from public chat. Native LAN `initiatorID` is always zero; framework host updates supply the actual player ID. Other rooms use the native initiator ID. These values remain self-reported, not authenticated.

Internal `acbricLobby` metadata extends native `strategicReady`, `strategicResumeReady` and `hosterUpdate` messages:

| Field | Contract |
| --- | --- |
| `v`, `channel`, `host` | Version 2, actual room ID and host player ID |
| `hostSession`, `round` | Host context UUID and increasing batch within that context |
| `sessions` | Complete map of current player IDs to verified context UUIDs |
| `rulesDigest` | SHA-256 of the current rule set; different digests cannot confirm preparation |
| `proof` | Ready messages only: UUID generated when the player actually prepares |
| `ready` | Host updates only: map of accepted player IDs to preparation UUIDs |

The host accepts preparation only for the full current context. Bare native ready flags are insufficient; each client must also recognize its own proof. Changed membership, host or sessions cannot reuse an old batch. Native player rows must exactly cover membership. Only a complete valid update can authorize native `canStart`; actual `sendReady` and `startGame` entries check the conditions again.

The Client mixin observes socket and reconnect-flag changes because native `isConnectedRaw` may remain true during reconnect. `ResumeScreen` can recreate a lobby without calling `processWelcome`; the recovery hook transfers room context, closes the old session and requires a fresh handshake.

Protocol fields and `impl` bridges are not stable MOD APIs. New mixins are `ClientConnectionMixin`, `LobbyNetworkMixin` and `StrategicLobbyMixin`; existing `ResumeScreenMixin` is extended, bringing dev.9 to 19; dev.10 adds the generation hook for 20 total. The status button redirects native rendering calls; another mod targeting those same calls can conflict. Check Mixin logs when integrating such mods.

## Validation and remaining acceptance

The dev.9 baseline passed 477 headless checks, including 44 new lobby checks. Native games 1.2.15.2 and 1.2.14 each run a real Server plus two independent Fabric/Client processes for LAN and ordinary-room paths. Ten final experiments pass 104 top-level checks, covering retry, stale proofs, native reconnect, recovery handoff, ready/start blocking and modified/missing/failed mods.

Probes use minimal lobby/world fixtures. They execute real welcome, receive, ready and condition methods, and blocked startGame calls; successful cases validate canStart without generating worlds. Recovery invokes the transformed RETURN hook, not the full recovery screen flow. Ordinary-room tests still use a local Server, with the probe filling the native preprocessing queue; official authentication is not exercised. Rendering targets transform successfully, but full GUI layout, successful game start, complete save/resume/recovery, cross-machine/official-server play and sustained sessions need manual acceptance.

Current dev.10 validation: 541 standard assertions; 14 experiments/204 network checks across two versions. See [rule validation scope](SHARED_RULES.md#verification).
