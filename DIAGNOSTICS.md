# Game identity and startup diagnostics

**English** | [中文](DIAGNOSTICS.zh-CN.md)

Available in the unreleased `0.3.3-dev.2` framework build. This work adds internal diagnostics, without new public mod APIs or changes to event ordering, cancellation or exception propagation.

## Game identity

Before Fabric resolves mod dependencies, the launch provider reads the constant String field `AGame.VERSION` directly from game bytecode using ASM. It does not load or initialize the game class. The first `AGame.class` in the actual game classpath wins; shadowed definitions are reported. A missing or unreadable first definition is not replaced by a version from a later archive.

Recognized versions, including the four-component `1.2.15.2`, become the built-in `airships` mod version used by Fabric dependency resolution. Unrecognized version strings are retained as raw values, with a warning and a normalized fallback of `0.0.0`; an unavailable raw value is `unknown`. No release number is guessed from archive names.

**Compatibility change:** `airships` is no longer always `0.0.0`. A mod requiring exactly that old placeholder can now fail dependency resolution. Authors should declare the actual game versions they have tested, or use `*` if they are intentionally leaving that check open. Acbric does not rewrite dependency declarations or bypass Fabric's resolver.

The code fingerprint uses the complete bytes of every classpath archive containing `.class` entries under `com/zarkonnen/airships/`, in classpath order. Each archive gets a SHA-256. The combined fingerprint is SHA-256 of UTF-8 `acbric-game-archives-v1\n`, followed by each lowercase archive hash and a newline. Names and paths are excluded; archive order and byte changes matter. ZIP repacking can change the fingerprint even if its classes are unchanged. No archives, or an archive inspection failure, produces `unavailable`, not a partial fingerprint presented as complete.

This identifies code inputs, not compatibility. It does not cover loose assets, every dependency, mod contents, saves or multiplayer behavior.

## Reports

Each launch creates a new UUID session under the **Fabric game directory**:

```text
game/logs/acbric/<session UUID>/
├── launch.properties
└── startup.json
```

The console prints the directory. Both reports include the same session ID. They are UTF-8 local files; nothing is uploaded. Reports are retained without automatic pruning. Close the game before manually removing old session directories. The internal schema and session system property are diagnostic implementation details, not stable mod APIs.

`launch.properties` is written before Fabric dependency resolution. It records Java version, process ID, game version/source, ordered archive hashes, fingerprint, warnings and launch phase:

| Phase | Meaning |
| --- | --- |
| `GAME_LOCATED` | Launch inputs were found; Loader resolution, transformation and API initialization may still fail |
| `ENTERING_MAIN` | About to resolve and invoke the configured main class |
| `MAIN_RETURNED` | The configured main method returned normally; this is not proof that every gameplay subsystem succeeded |
| `MAIN_FAILED` | Main class resolution/invocation failed; the original exception still propagates |

`startup.json` begins when the Acbric API reaches pre-launch. It lists all mods reported as loaded by Fabric, including their IDs and versions (therefore including `fabricloader`, `airships` and `acbric_api`). It tracks only `acbric` entrypoints, including failures while constructing them:

| `acbricStatus` | Meaning |
| --- | --- |
| `NOT_REACHED` | Acbric entrypoint discovery has not completed |
| `NO_ACBRIC_ENTRYPOINT` | No `acbric` entrypoint was found; this is valid for resource-only mods and infrastructure |
| `PENDING` | Entrypoints were discovered but have not all run |
| `RUNNING` | An entrypoint is currently executing |
| `SUCCEEDED` | All of this mod's `acbric` entrypoints returned normally |
| `FAILED` | At least one entrypoint failed, even if another succeeded |

Each entrypoint has its own definition and state. Failures include exception type, message and stack trace. A failure in one entrypoint remains logged and does not prevent later entrypoints from running, preserving previous behavior. There is no rollback of partial initialization.

The report phase distinguishes resource preparation, entrypoint initialization, completion with/without entrypoint failures and fatal pre-launch failure. Completion is **not** a resource-installation success certificate: the existing bundled resource loader reports recoverable errors separately to the console. Other Fabric entrypoint types and errors occurring after `acbric` initialization are not tracked here.

## Reading an incomplete run

- If only `launch.properties` exists, inspect the Fabric log for dependency or transformation failures before Acbric pre-launch.
- `RUNNING`/`PENDING` without completion can indicate process interruption or a callback that never returned. The report records the last persisted stage; it does not diagnose a hang by itself.
- A failed `acbric` entrypoint does not necessarily stop the game. Check per-mod results even when the main method is reached.
- Report write failures emit a warning and do not change initialization or launch results. Existing blocking files are not deleted. With a failed write, the last persisted phase may be stale or no report may exist.

Reports use temporary files and atomic replacement where supported, with a replacement fallback. They are diagnostic snapshots, not a crash-proof transaction log. Errors before the provider locates game inputs, early Loader failures, unrelated pre-launch callbacks, background-thread errors and forced termination are not all captured. Preserve the normal console/Fabric log alongside these reports. Stack traces may contain local paths or mod-provided messages; review them before sharing.
