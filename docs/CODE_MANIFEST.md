# Local code manifests and offline comparison

**English** | [中文](CODE_MANIFEST.zh-CN.md)

This tool exports a local startup code manifest and compares two files. Runtime room checks are documented under the [internal handshake](CODE_HANDSHAKE.md) and [campaign lobby integration](LOBBY_HANDSHAKE.md). The classes and JSON format are internal diagnostic tooling, not a stable MOD API, and provide no automatic state synchronization.

## Export and compare

After processing the `acbric` entrypoints, a normal framework launch writes:

```text
game/logs/acbric/<current launch session ID>/code-manifest.json
```

Use the adjacent `launch.properties` and `startup.json` to confirm the session, game identity and entrypoint results. Each launch gets a new session. An old report does not represent a running process. Interrupted initialization may produce no manifest; export failures warn without changing existing startup behavior.

From the development repository:

```powershell
.\gradlew.bat compareCodeManifests '-PmanifestLeft=C:/reports/left.json' '-PmanifestRight=C:/reports/right.json'
```

Quote paths containing spaces. The tool reads two files and prints JSON; it does not start the game or use the network. It requires prepared development dependencies; use the Gradle task above as the stable entry point.

| Status / process exit code | Meaning |
| --- | --- |
| `CODE_MATCH` / 0 | The code identity and status covered by this model match |
| `DIFFERENT` / 1 | Both manifests are verifiable but game, Java version, mod versions/content or membership differ |
| `UNVERIFIABLE` / 2 | At least one identity, source read or initialization is unavailable/failed/incomplete; known differences are still listed |
| `INVALID_INPUT` / 3 | Missing, malformed, oversized or unsupported input, or invalid arguments |

Gradle reports exit codes 1–3 as task failures; 1 and 2 can be expected comparison outcomes. `ONLY_LEFT/ONLY_RIGHT` identify a mod present only on that side. `MOD_CONTENT` identifies different bytes for the same ID. Identical failures never yield `CODE_MATCH`.

## Coverage

- Game: the Provider's current detected version and `acbric-game-archives-v1` archive fingerprint, passed as in-process strings rather than read from old logs.
- Java: the full `java.runtime.version` string; the entire JDK is not hashed.
- Mods: actual `FabricLoader.getAllMods()` containers, excluding `airships` and `java` represented above. Includes the API, Loader, MixinExtras, nested mods and unknown/legacy mods. Unloaded files in the mods directory are excluded.
- Initialization: the **`acbric` entrypoint stage only**. `NO_ACBRIC_ENTRYPOINT` does not certify other preLaunch entrypoints, mixins, native resources or subsequent callbacks.
- Exports contain no absolute source paths, settings, saves or stack traces. Local initialization details remain in `startup.json`.

`CODE_MATCH` does **not** guarantee multiplayer synchronization. This stage does not compare effective native mod resources, expansions, shared gameplay settings, loaded campaign data, the launch shim itself or all external classpath dependencies. It does not authenticate peers. Identical code may still use different local settings, random values or timing. Self-reported manifests are not anti-cheat proofs.

## Fingerprint rules and limits

`acbric-loaded-roots-v1` hashes the actual roots resolved by Loader:

1. SHA-256 input starts with the algorithm name. Strings use UTF-8; string lengths and counts use eight-byte big-endian integers.
2. Include the root count and Loader root order, preserving multi-root lookup precedence in development environments.
3. For each root, include the file count and files sorted by relative path using Java string ordering. Include each path, byte length and complete contents.
4. Absolute paths, ZIP entry order, compression and timestamps are excluded. Renaming files, changing content or reordering roots changes the fingerprint. Empty directories are ignored; an empty root is unverifiable.

A nested mod has its own resolved-root fingerprint. Its embedded JAR also remains an ordinary file in its parent's root, so repacking that child JAR may change the **parent** fingerprint. The game fingerprint retains the previous full-archive algorithm, without this mod timestamp normalization.

Ambiguous duplicate ZIP entries, symbolic links and unsupported origins are rejected. Read errors and exceeded budgets produce explicit unavailable states, never successful empty hashes. Limits: 64 roots, 50,000 files and 512 MiB per mod; 2 GiB read budget per export; 100,000 visited nodes per root; 1,024 mod entries and a 1 MiB JSON file. Parsing rejects duplicate keys/IDs, trailing garbage, wrong field types, unknown fields/schema/algorithms and invalid UTF-8.

Hashing runs synchronously once during startup, outside render loops. Before/after inventory checks detect ordinary concurrent changes, but do not lock the filesystem or certify in-memory transformed bytecode. Restart and export again after changing mod files.

The manifest covers only the startup code identity listed above. It does not authenticate players or prove that native resources, shared settings, save state or runtime simulation match.
