# Code handshake protocol and state machine

**English** | [中文](CODE_HANDSHAKE.zh-CN.md)

This protocol implements internal packets and state management and is connected to the [campaign lobby](LOBBY_HANDSHAKE.md), including status UI and ready/start guards. `CodeHandshakeProtocol` and `CodeHandshakeSession` are package-private implementation details, not public MOD APIs.

## Meaning of confirmation

Coverage is inherited from [code manifests](CODE_MANIFEST.md). `CODE_MATCH` means this client received a valid response to its current challenge from every other current room member, found matching code manifests, and observed the same comparison status reported by the responder. This is **local, per-peer evidence**, not proof that every client has reached room-wide consensus or permission to start a game.

The native Server does not authenticate sender fields in custom messages. IDs, sessions and manifests are self-reported. Random session/challenge values isolate stale traffic; they do not authenticate identity or provide anti-cheat protection. Native resources, shared settings, campaign state and simulation synchronization remain outside this check.

## States and lifetime

| State | Meaning |
| --- | --- |
| `IDLE` | No room bound, or disconnected |
| `ALONE` | Only this client is present; no peer was verified |
| `CHECKING` | At least one current peer still needs a valid response |
| `CODE_MATCH` | All current peers confirmed matching code; evidence has not expired |
| `DIFFERENT` | Verifiable code differences, with per-mod/field details |
| `UNVERIFIABLE` | Unverifiable/oversized manifest, or contradictory comparison results |
| `TIMED_OUT` | No valid response before the deadline; an older framework or invalid/unsupported traffic can cause this |
| `CLOSED` | Permanently closed; member records released |

Immutable snapshots expose per-peer status, attempt count, last confirmed remote session and comparison details. Local problems take priority as `UNVERIFIABLE`; otherwise aggregation uses `UNVERIFIABLE`, `DIFFERENT`, `TIMED_OUT`, `CHECKING`, then `CODE_MATCH`. Per-peer records preserve simultaneous problems.

- Changing the room ID or the local ID restarts the exchange with a new session and challenges; a member-set change only adds or removes check targets — this machine's token and the confirmed peers' sessions are kept, and reordering members does not reset timers. A new player entering the room therefore no longer makes everyone check again.
- Disconnect/rebind creates a new session even if IDs are unchanged. Native internal reconnect can preserve IDs: the adapter must explicitly report disconnect or call `restart`.
- Requests retry every 2 seconds, at most 5 attempts, with a 10-second deadline measured from the start of that peer check. Failed sends still consume attempts. A stalled timer does not burst-send missed retries.
- After a round times out the next round opens **automatically** without manual intervention: the wait backs off with the number of failed rounds (5, 10, 20, 30 seconds, then a fixed 30 seconds) on the same session token, so it neither drags other members' preparation down nor triggers a new room batch. Late responses and requests still cannot restore the previous round's result.
- Matching evidence expires after 30 seconds and must be revalidated. Duplicate responses never extend its lease. Difference/unverifiable states remain blocking until explicit retry or context changes.
- A valid request carrying a different session from a previously confirmed peer conservatively triggers a new check. A delayed old request may cause rechecking, but cannot directly restore an old successful result.
- Replies are limited to one per peer per 250 ms. Each `tick` produces at most one request per peer; there is no unbounded outgoing queue.

## Wire version 1

Use ordinary room messages with `type = acbric:code_handshake` and `protocol = 1`. Do not store them as history or broadcast globally.

| Field | Contract |
| --- | --- |
| `kind` | `request` or `response` |
| `channel`, `from`, `to` | Nonnegative room ID (LAN campaigns may use 0); distinct, nonnegative member IDs |
| `members` | Full current member set, at most 32, no duplicates; must equal the receiver's current set |
| `session` | Sender's context UUID |
| `requestSession`, `challenge` | Requester's context UUID and per-peer challenge UUID; echoed in responses |
| `status` | `REQUEST` for requests; `CODE_MATCH`, `DIFFERENT` or `UNVERIFIABLE` for responses |
| `manifest` | Manifest JSON object; explicit JSON `null` if the sender's manifest exceeds the budget |
| `###` | Optional nonnegative integer transport sequence added by the native Client; not application confirmation |

Serialized manifests are limited to **32,000 UTF-8 bytes** and complete protocol messages to **40,000 bytes**, leaving room under the native 50,000-byte send limit. Oversized local manifests produce `MANIFEST_TOO_LARGE`, a small null-manifest announcement, and `UNVERIFIABLE` on both sides. Null never means an empty mod list. Manifests are neither truncated nor fragmented; the offline tool retains its 1 MiB limit.

Parsing rejects unknown fields/versions, duplicate keys, wrong types, floating-point pseudo-integers, invalid UUIDs, unsupported manifests and exceeded limits. Numeric types are checked before invoking the game's floating-point serializer. Invalid, misaddressed, wrong-room/roster, stale-challenge and history-frame traffic cannot confirm a peer. Missing valid responses eventually time out; there is no compatibility-success fallback.

## Adapter obligations

1. Call from one thread with a monotonic nonnegative millisecond clock. Tick and obtain fresh snapshots regularly; after a pause, advance time before considering any gate.
2. Obtain context from the current connection's native welcome/member frames. Filter stale connections, rooms and out-of-order outer frames. Use welcome metadata to exclude public chat; native LAN campaigns also use room 0. Context errors such as oversized rosters must remain unconfirmed.
3. Pass the actual outer frame's room/history metadata into `receive`, not values claimed inside the custom packet.
4. Send returned texts as ordinary room messages and treat native send failures as unconfirmed attempts, never successful checks. Do not enable history storage or global broadcast.
5. Consume only the reserved framework message type. Preserve native message/member/room handling, avoid duplicate Client polling, and keep these packets out of the campaign command interpreter.
6. Implement separate coordinated ready/start invalidation and final checks. Local `CODE_MATCH` is not room-wide agreement. Host changes, multiple start entry points and early construction of loaded worlds are addressed by the separate lobby adapter; see its scope and validation limits.

Neither layer changes `LOADED` timing, add gameplay APIs or automatically broadcast campaign data.

## Security boundary

Player IDs, sessions and manifests are self-reported, and the native server does not authenticate them. Random challenges isolate stale and duplicate traffic; they do not authenticate identity or prevent cheating. This protocol alone does not guarantee cross-machine play, official-server behavior or sustained multiplayer state.
