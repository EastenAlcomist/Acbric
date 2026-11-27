# Building and testing

[中文](BUILDING.zh-CN.md) · Applies to the current `0.3.8` source

## Cheat sheet

| Task | Windows cmd | PowerShell | Linux / macOS / Git Bash |
| --- | --- | --- | --- |
| Compile only | `build` | `.\build` | `./build.sh` |
| Compile and run all tests | `build full` | `.\build full` | `./build.sh full` |
| Clean, then compile | `build clean` | `.\build clean` | `./build.sh clean` |
| Forward a Gradle task | `build <task>` | `.\build <task>` | `./build.sh <task>` |
| Run all tests | `test all` | `.\test all` | `./test.sh all` |
| Run one suite | `test event` | `.\test event` | `./test.sh event` |
| List suites | `test list` | `.\test list` | `./test.sh list` |
| Launch legacy-layout game | `gradlew startAirships` | — | `./gradlew startAirships` |

`build` and `test` only locate a JDK and forward to Gradle. Build behavior belongs in `build.gradle`, not duplicated in batch or shell scripts.

## Setup

### JDK

The project requires **JDK 21 or newer**, while source and target bytecode are Java 21. `build.cmd` / `build.sh` check `JAVA_HOME`, `PATH` and common installation directories, accepting a major version of at least 21.

Set a JDK explicitly when discovery fails:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21'
```

```sh
export JAVA_HOME=/path/to/jdk-21
```

### `libs/`

Game files are not redistributed. Compilation requires the game A/B archives, game libraries and Fabric Loader. For a Windows game layout:

```powershell
$gameInstall = 'C:/Games/Airships Conquer the Skies'
$framework = 'C:/Acbric'
New-Item -ItemType Directory -Path ./libs -Force | Out-Null
Copy-Item -LiteralPath "$gameInstall/asplit-A.zip", "$gameInstall/asplit-B.zip" -Destination ./libs
Copy-Item -Path "$gameInstall/lib/*.jar" -Destination ./libs
Copy-Item -LiteralPath "$framework/loader-libs/fabric-loader-0.19.3.jar" -Destination ./libs
```

Required inputs:

| File | Purpose |
| --- | --- |
| `libs/asplit-A.zip` | game kernel classes |
| `libs/asplit-B.zip` | game kernel classes |
| `libs/fabric-loader-0.19.3.jar` | compile dependency |
| `libs/*.jar` | game-provided dependencies |

For another game layout, locate the archives from `Airships.json` classPath. The current compile baseline is **1.2.15.3**; the runtime also supports 1.2.15.2. After changing the baseline, run `build clean`, then `test all`.

Check the baseline:

```powershell
javap -p -constants -classpath libs com.zarkonnen.airships.AGame | Select-String VERSION
```

Tests additionally require `libs/FloatIO.jar` for the native JSON decimal formatter.

### `game/`

`game/` is needed only by legacy `startAirships`; it contains `Airships.json`, `data/`, `lib/native` and related files. Compilation and headless regression do not need it.

Prefer the external distribution for development, then select an existing game folder in Setup:

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships Conquer the Skies"
```

## Compiling

```sh
./build.sh                     # gradle assemble
./build.sh full                # gradle build; compile and run all tests
./build.sh clean               # gradle clean assemble
./build.sh installApiMod       # forward another Gradle task
./build.sh --info assemble     # Gradle options are supported
```

Artifacts:

| File | Contents |
| --- | --- |
| `build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar` | API and game adapters |
| `build/libs/Acbric-1.0-SNAPSHOT.jar` | `AirshipsGameProvider` launch shim |

`gradle.properties` enables configuration caching, parallel work, task caching and the Gradle daemon. Distribution tasks disable configuration caching where needed. If a cache problem occurs, disable it for one run:

```sh
./build.sh --no-configuration-cache
```

## Testing

```sh
./test.sh all
./test.sh event
./test.sh data rename
./test.sh cp
./test.sh list
```

Selectors support exact names, aliases, unique prefixes and unique substrings. Unknown or ambiguous selectors fail explicitly.

| Suite | Coverage |
| --- | --- |
| `data` | `DATA_LOADED` preserves the game's actual result |
| `bundle` | bundled native resource updates, conflicts, backups and recovery |
| `classpath` | launch classpath filtering and deduplication |
| `external` | external setup, instances, Steam, maintenance locks, caches and sync |
| `event` | registration, one-shot execution and handle identity |
| `rename` | rename-panel legacy/new event ordering and cancellation |
| `mods` | Fabric MOD installation, Java MOD enable/disable, localized descriptions and ACMODs embedded in native MODs |
| `campaign` | campaign data and lifecycle |
| `config` | MOD configuration and settings contracts |
| `scopes` | subscription scopes and runtime diagnostics |
| `manifest` | code manifest export and offline comparison |
| `handshake` | code handshake and lobby ready gates |
| `rules` | shared rules and old-save conversion |
| `identity` | build identity and startup diagnostics |
| `ui` | shared UI, command panel and scaling |
| `devtools` | console, commands and diagnostic buffer |

Regression uses an isolated directory under `build/regression-sandbox/` and does not read player saves. `check` and explicit `gradlew build` always run the full registry; `test <suite>` affects only local quick runs.

To add a suite:

1. Add a `run` method under `src/regressionTest/java/net/fabricacs/regression/` that returns its check count.
2. Register its name, description and entry in `FrameworkRegression.SUITES`.
3. Run `test list` and `test <name>`.

Do not create a parallel test entry point.

## Cross-platform rules

- `.cmd` files stay ASCII with CRLF; `.sh` files stay LF.
- POSIX entry points are `build.sh` / `test.sh`, avoiding a collision with `build/`.
- Scripts locate the repository from their own path and quote every path.
- `cmd.exe` splits `-Pkey=value` at `=`. Test wrappers use `ACBRIC_SUITES`, giving both platforms the same invocation.

## External distribution

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships Conquer the Skies"
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships Conquer the Skies" -PbundleRuntime=false
```

- `externalDistZip` creates a clean framework-only package with launchers, API, template and docs, and **by default** creates `runtime/` with JDK 21 `jlink`.
- `-PbundleRuntime=false` creates a minimal package without a runtime. The default runtime build requires the build JDK to be Java 21 and fails clearly otherwise.
- `externalGameDir` selects the input used for distribution scanning. It defaults to `game/`; pass it explicitly when no local `game/` exists.
- Output: `build/external-dist/Acbric-external-<version>.zip`
- Scan report: `build/external-dist/verification.json`

The external package does not include the game. Legacy `distZip` includes the local `game/` and `libs/` directories and is intended only for legacy-layout research. The Windows player EXE is built by `buildPlayerLauncher` with `csc.exe`; normal Java compilation and tests do not invoke it.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `No compatible JDK found (requires 21 or newer).` | Install JDK 21+ or set a valid `JAVA_HOME`. |
| `Missing game files required for compilation` | Add the required files under `libs/`. |
| `Missing game files required to launch Airships` | Affects only `startAirships`; add `game/` or use the external distribution. |
| Gradle wrapper lock timeout | Wait for the other download/build; do not delete the `.lck` file. |
| `Permission denied` on Linux/macOS | Run `chmod +x gradlew build.sh test.sh`. |
| `bad interpreter` | Check that the `.sh` file has LF line endings. |
| First build is slow | Maven Central and Fabric Maven are being downloaded. |
| `Task 'event' not found` | Use `test event`, or quote the Gradle argument containing `=`. |

After changing a Mixin or `fabric.mod.json`, check real bytecode targets and loading logs. Recording renderers do not replace IME, window-focus or OpenGL testing.

After documentation changes, run:

```powershell
python tools/verify_docs.py
```
