# Campaign lobby code check (dev.9–dev.10)

> From 0.3.6 preparation intent is retained per member, gameplay settings are distributed host-authoritatively and the status button opens the multiplayer lobby panel; this document describes 0.3.6, and the historical dev.9–dev.10 behavior differences are in the [changelog](CHANGELOG.md#036--the-lobby-no-longer-clears-preparation-in-bulk-gameplay-settings-become-host-authoritative-2026-10-03).

> See [dev.11 conversion](RULE_SAVE_MIGRATION.md) for save preflight, explicit adoption/migration and save-as boundaries. Normal loading still does not migrate.

> dev.10 also requires [matching declared rules](SHARED_RULES.md). Code status below is only one prerequisite; unconfirmed rules block ready/start. Rules freeze before generation and resume uses saved values.

**English** | [中文](LOBBY_HANDSHAKE.zh-CN.md)

dev.9 connects the [internal handshake](CODE_HANDSHAKE.md) to native multiplayer campaign lobbies. Checks begin automatically on entry. Pending, different, unverifiable or timed-out code blocks preparation and game start. dev.9 added no public MOD networking API; dev.10 stores rules within existing campaign extension data.

## Player behavior

An `Acbric` status button replaces the connection text at the top left, in English or Chinese. Its tooltip preserves the original connection information and shows peer states, selected differences and scope. Click it to open the **multiplayer lobby panel**, which contains the status plus a manual re-check, the gameplay-settings entry points registered by mods (starting cities, cash, AI fleets and so on) and enable/disable-on-next-start for Java MODs (AcMod) and native MODs. Use the offline manifest comparator for complete code differences and local startup reports for entrypoint failure traces.

| Status | Action |
| --- | --- |
| Waiting for room / peers | Wait for connection and another player |
| Checking code / waiting for host | Wait for checks and the current host update |
| Code match | Prepare once native resource/player requirements are also satisfied |
| Code differs | Align game build, Java version, framework and loaded mods; restart |
| Unverifiable | Inspect startup reports for failed entrypoints, hashing failures or limits |
| Timed out | Check connections and framework versions; the framework retries automatically, and the panel also offers a manual re-check |

There is no bypass switch. Code match is only one requirement; native prerequisites and every player's preparation remain necessary. From 0.3.6 preparation intent is **retained per member**: only a machine whose session changed (reconnect, manual re-check, switching rooms) must prepare again; routine lease renewal, other members joining or leaving, gameplay-setting updates and the native changes the host handles such as map settings and empire selection no longer revoke everyone's preparation. After the native resync (`ResumeScreen` rebuilding the lobby clears every player's ready flag and does not copy `readySent`), the framework re-sends preparation once automatically when this machine had actually clicked ready and the gate allows it, so the player does not have to click again in the lobby.

After a check round times out no manual intervention is needed: the framework backs off with the number of failed rounds (5/10/20/30 seconds) and starts the next round automatically on the same session token, so it does not drag other players' preparation down. The rules exchange likewise retries automatically in repeated windows instead of stalling permanently after a timeout.

Routine 30-second renewal temporarily stops accepting new preparation but retains intent when identities remain unchanged. A host update containing every current proof can grant a game-start execution window of at most two seconds, reducing split transitions at a lease boundary. Known connection/member/session changes still immediately revoke that member's decision and the start grant; subsequent invalid host updates revoke the grant too. This does not guarantee atomic start or distributed consensus under network partitions.

## Compatibility and scope

- Existing public MOD APIs and event semantics remain. Legacy feature mods need no rebuild for this feature, but all peers need matching code and a framework supporting these checks. Mixing old framework versions is not supported; missing replies time out instead of succeeding.
- Only multiplayer campaign lobbies, both new and resumed campaigns, gain ready/start gates. Singleplayer and combat lobbies do not. The global receive hook still removes the framework's reserved messages so delayed packets cannot enter the campaign command interpreter.
- Native `modsLoaded`, reload/failure, empire selection and all-player-ready requirements remain. Native resource download checks are not replaced.
- Mod controls in the lobby panel write each side's enabled state and **take effect after a restart**: Java MODs (AcMod) are filtered by `ModSelection` on the next launch, and native MODs write the game's own enable settings. Multiplayer requires every peer to load the same mod set, so the panel lists the native list and difference hints as well; mods are never hot-reloaded inside the lobby (native hot reload only covers native MODs and would invalidate an already confirmed code manifest).
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
| `rulesDigest` | SHA-256 of the current rule set; the previous digest's proofs are still accepted only inside the 5-second grace window after a digest change, so preparation already on its way is not dropped |
| `proof` | Ready messages only: UUID generated when the player actually prepares |
| `ready` | Host updates only: map of accepted player IDs to preparation UUIDs |

The host accepts preparation only for the full current context. Bare native ready flags are insufficient; each client must also recognize its own proof. Changed membership, host or sessions cannot reuse an old batch, but **members whose session is unchanged keep their own proof**: a peer joining or leaving only makes new members check from scratch and revokes the start grant, without making anyone else prepare again. Native player rows must exactly cover membership. Only a complete valid update can authorize native `canStart`; actual `sendReady` and `startGame` entries check the conditions again.

The Client mixin observes socket and reconnect-flag changes because native `isConnectedRaw` may remain true during reconnect. `ResumeScreen` can recreate a lobby without calling `processWelcome`; the recovery hook transfers room context and the expressed **preparation intent** and closes the old session, and preparation is restored automatically once the new lobby completes its handshake.

Protocol fields and `impl` bridges are not stable MOD APIs. New mixins are `ClientConnectionMixin`, `LobbyNetworkMixin` and `StrategicLobbyMixin`; existing `ResumeScreenMixin` is extended, bringing dev.9 to 19; dev.10 adds the generation hook for 20 total. The status button redirects native rendering calls; another mod targeting those same calls can conflict. Check Mixin logs when integrating such mods.

## Validation and remaining acceptance

The dev.9 baseline passed 477 headless checks, including 44 new lobby checks. Native games 1.2.15.2 and 1.2.14 each run a real Server plus two independent Fabric/Client processes for LAN and ordinary-room paths. Ten final experiments pass 104 top-level checks, covering retry, stale proofs, native reconnect, recovery handoff, ready/start blocking and modified/missing/failed mods.

Probes use minimal lobby/world fixtures. They execute real welcome, receive, ready and condition methods, and blocked startGame calls; successful cases validate canStart without generating worlds. Recovery invokes the transformed RETURN hook, not the full recovery screen flow. Ordinary-room tests still use a local Server, with the probe filling the native preprocessing queue; official authentication is not exercised. Rendering targets transform successfully, but full GUI layout, successful game start, complete save/resume/recovery, cross-machine/official-server play and sustained sessions need manual acceptance.

Current dev.10 validation: 541 standard assertions; 14 experiments/204 network checks across two versions. See [rule validation scope](SHARED_RULES.md#verification).

Current 0.3.6 validation: all 16 suites pass with 1135 checks (`handshake` 134 and `rules` 132, the two suites together adding 25 over 0.3.5.1), covering per-member preparation retention, sessions surviving members coming and going, a digest change retaining preparation and the grace window, automatic retry after a timeout, host-snapshot adoption and refusal, and the periodic rules-exchange retry window. The following still need manual acceptance: the ready/start flow in the real game, the visible preparation state after a resync, whether the panel's mod controls actually take effect, and cross-machine or official-server play.
