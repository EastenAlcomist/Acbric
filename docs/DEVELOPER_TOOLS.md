# Developer tools and console

[中文](DEVELOPER_TOOLS.zh-CN.md) · Current development API: `0.3.3-dev.21`.

Open **Mods → Acbric API → Details → Developer tools / Console**. These are built into the framework; no example MOD is required. UI follows the game's current language, supporting English and Chinese (`chi`, `zh`, `zho`); other languages fall back to English. Reopen a window after changing language. Since dev.21, press the **backtick / tilde key** below Esc and left of 1 (physical `GRAVE`; Shift is optional) to open the console with the command field focused. The opening key is consumed. Ctrl/Alt/Meta combinations, inactive displays, native error/help/chat overlays, and existing Acbric windows do not trigger it. Close another Acbric window before using the shortcut. While the console is open this key remains ordinary text; use Esc/X/Close to dismiss. Holding the key cannot repeatedly reopen it. This is a fixed console shortcut, not a general key-binding API.

Developer tools display game/API versions, fingerprint, session, startup phase, MOD entrypoint status and evicted-message count. Refresh captures a new snapshot. Loaded code, successful Acbric initialization and gameplay compatibility are different things: `NO_ACBRIC_ENTRYPOINT` means only that no such initializer was declared. MOD enable/disable remains a restart operation. Use `acbric_api:mod <id>` for source, next-start selection and initialization details.

## Built-in commands

| Command | Effect | Result |
|---|---|---|
| `acbric_api:help [command]` | READ_ONLY | List names, or generated syntax, description, arguments and requirements. |
| `acbric_api:status` | READ_ONLY | Current framework/game diagnostic snapshot summary. |
| `acbric_api:mods` | READ_ONLY | Loaded/discovered MOD summary. |
| `acbric_api:mod <mod_id>` | READ_ONLY | One MOD's details. |
| `acbric_api:diagnostics export` | LOCAL | Request an asynchronous diagnostic ZIP; completion/failure appears in messages. |

All five require `ACTIVE_SCREEN`. The help/completion data comes from command declarations. Add future commands as adapters of the same service used by other framework UI/API consumers, without implementing a second behavior inside the console. Registration contract: [COMMANDS.md](COMMANDS.md).

## Console interaction

- Focus the command field; Enter runs, Up/Down recalls up to 32 history lines. At the newest entry, Down restores the pre-history draft. History is window-local and is discarded on close.
- Tab completes names or declared choice/boolean arguments. Multiple candidates open a selectable dialog; Shift+Tab keeps normal focus navigation. Buttons provide the same actions.
- Filter messages by exact MOD ID (blank means all) and level. Older/Newer/Latest browse six records at a time. The page preview clips each message to 1000 units. Details opens the retained message and traceback; Copy page copies full retained records. If the clipboard is unavailable, the UI reports it and export remains available.
- The process-wide buffer retains at most 512 records and 524288 UTF-16 units across owner/message/detail. Message and detail each cap at 4096 units; older records are evicted. The status snapshot reports the eviction count. It is not a full persistent log.
- Captured sources: `AcbricLogger`, managed event failures (existing rate limits retained), UI callback failures, bundled-resource installation errors/conflicts, command input/results and export outcomes. Arbitrary `System.out`, all Fabric logs and the game's entire log are not intercepted. Native game exceptions remain in the game's user-data `log.txt`.
- Previously recorded text does not retroactively translate. IDs, stack traces, file paths and MOD-authored messages remain literal. Windows IME, real OS clipboard and focus switching still require interactive testing.

## Public diagnostics API

`DeveloperDiagnostics.snapshot()` runs on the game thread and returns an immutable `DiagnosticSnapshot`. Its MOD rows contain ID/name/version, current load state, next-start preference (nullable if unknown), Acbric initialization state, source, management note and initialization failure summary. The manager's existing inventory is used; refresh does not rescan arbitrary files. The snapshot also includes identity/session/startup phase and dropped-message count.

`DeveloperDiagnostics.messages(modId, level)` is thread-safe and returns immutable `DiagnosticMessage` records with sequence, timestamp, owner, INFO/WARN/ERROR, message and detail. Null/blank owner and null level mean no corresponding filter. Publish through `context.logger()` / `AcbricLogger`, not the internal buffer implementation.

`DeveloperDiagnostics.export()` must start on the game thread. It captures plain JSON before scheduling I/O and returns `CompletableFuture<Path>`. Only one export runs at a time; failures/busy state complete exceptionally and produce a diagnostic message. Do not call `.get()`/`.join()` from the game thread in normal MOD code, and do not touch UI/game objects from a future callback. Closing the console does not cancel export.

Output is `game/logs/acbric/exports/acbric-diagnostics-<UUID>.zip`, relative to the Fabric game directory. It contains snapshot/messages JSON (≤4 MiB each), a bilingual README and the current session's available `launch.properties`, `startup.json`, `code-manifest.json`, `runtime-events.jsonl` (≤1 MiB each). Missing, oversized or inaccessible reports are listed as unavailable; older sessions are not substituted. Reads may have different timestamps. Linked/special filesystem nodes are refused; a failed export removes its partial ZIP. No arbitrary export destination or attachment path is accepted by the public API.

The package omits saves, config files and game resources. It still contains local paths, MOD identities, command input/results and error messages: inspect it before sharing. The framework never uploads it. JSON/report layout and `impl` classes are internal formats; consume the public records instead. Startup file details: [DIAGNOSTICS.md](DIAGNOSTICS.md).

## Quick acceptance test

Open the built-in entry without an example MOD. Run `acbric_api:help`, `acbric_api:status`, `acbric_api:mod acbric_api`; check history, completion, filters, details, copy and export. Reopen with the other game language. For extension tests, install the separate `acbric-console-demo-0.1.0.jar` in the test copy's `game/mods/`: its `sum`, `echo`, `choice`, deliberate `fail`, campaign requirement and reserved shared command test the public API. Remove the example to remove those commands after restart; built-ins remain.
