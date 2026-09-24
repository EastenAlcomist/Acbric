# Building and testing

> **中文**: [BUILDING.zh-CN.md](BUILDING.zh-CN.md)
> Written for the **humans and agents** who join this project. After reading it you should be able to
> compile and test on any machine, and know where to look when something fails.

---

## 0. Cheat sheet

| What you want | Windows (cmd) | Windows (PowerShell) | Linux / macOS / Git Bash |
|---|---|---|---|
| Compile only | `build` | `.\build` | `./build.sh` |
| Compile + all tests | `build full` | `.\build full` | `./build.sh full` |
| Clean, then compile | `build clean` | `.\build clean` | `./build.sh clean` |
| Forward to Gradle | `build installApiMod` | `.\build installApiMod` | `./build.sh installApiMod` |
| **Run all tests** | `test all` | `.\test all` | `./test.sh all` |
| **Run one suite** | `test event` | `.\test event` | `./test.sh event` |
| List suites | `test list` | `.\test list` | `./test.sh list` |
| Launch the game | `gradlew startAirships` | — | `./gradlew startAirships` |

> PowerShell does **not** run `build.cmd` from the current directory; type `.\build`. cmd.exe accepts a bare `build`.
> The POSIX launcher is deliberately `build.sh`, not an extension-less `build`: on case-insensitive
> filesystems (Windows, macOS by default) that name collides with Gradle's `build/` output directory.

---

## 1. Why a "fast compile" entry point exists

Measured on Windows with a warm Gradle daemon — not a guess:

| Command | Time | Output lines |
|---|---|---|
| `gradlew --version` (pure startup floor) | 1975 ms | 15 |
| `gradlew assemble` (one source file edited) | 2775 ms | **23** |
| `gradlew build` (same edit) | 3822 ms | **167** |
| `gradlew assemble --no-daemon` | 12421 ms | — |
| `gradlew assemble` with `libs/` missing | fails | **825** |

Four reasons:

1. **The real feedback loop is "edit → launch the game (~72 s) → read the log".** Everything before the
   launch is pure overhead, and correctness here only shows up at runtime (mixins are load-time bytecode
   injection), so the loop runs often.
2. **`build` had no compile-only meaning.** Upstream wired `check` to `regressionTest`, so
   `gradlew build` = compile + 872 assertions. Compiling alone meant remembering Gradle's internal
   `assemble` name. Now `build` compiles and `build full` compiles and tests.
3. **145 lines of test noise bury compile errors.** The regression prints a lot of
   `[Acbric] Bundle conflict …`; a compile error is a couple of lines. Compiling only keeps it visible.
4. **Onboarding cost on a fresh machine.** With `libs/` missing Gradle emits 825 lines that never say what
   to do; JDK 21 became a hard requirement (F11) with nothing to help you find one; and Linux/macOS
   contributors previously had no convenient entry point at all.

---

## 2. One-time setup

### 2.1 JDK 21 (required)

`build.gradle` sets `sourceCompatibility = JavaVersion.VERSION_21`. The repository deliberately does
**not** pin a JDK path (`gradle.properties` has no `org.gradle.java.home`), so you supply JDK 21.

`build` / `build.sh` locate one for you, in this order:

1. `JAVA_HOME` (skipped when its major version is < 21)
2. `java` on `PATH` (same version check)
3. common installation directories

| Platform | Locations scanned |
|---|---|
| Windows | `%ProgramFiles%\Eclipse Adoptium\jdk-2*`, `%ProgramFiles%\Java\jdk-2*`, `Microsoft\jdk-2*`, `Amazon Corretto\jdk2*`, `Zulu\zulu-2*`, `BellSoft\LibericaJDK-2*`, `%USERPROFILE%\.jdks\*`, … |
| Linux | `/usr/lib/jvm/*`, `/usr/java/*`, `/opt/java/*`, `/opt/jdk*`, `$HOME/.sdkman/candidates/java/*` |
| macOS | `/Library/Java/JavaVirtualMachines/*/Contents/Home`, `/opt/homebrew/opt/openjdk*`, `/usr/local/opt/openjdk*` |

If none is found the script prints actionable instructions and exits, instead of leaving you to decode a
Gradle stack trace.

> **A `PATH` pointing at Java 8 is fine.** The script skips it and keeps looking. Verified: with
> `JAVA_HOME` pointed at Java 8 it still found the Adoptium JDK 21 on disk and compiled successfully.

### 2.2 `libs/` (required to compile, ~13 MB)

**Not redistributed** (copyright). Copy it from your own Airships installation:

```powershell
# Windows
Copy-Item '<your Airships>\libs' .\libs -Recurse -Force
```

```sh
# Linux / macOS
cp -r "<your Airships>/libs" ./libs
```

Three files are mandatory; `verifyFrameworkInputs` stops the build before compilation if any is missing:

| File | Purpose |
|---|---|
| `libs/asplit-A.zip` | game kernel classes (compile + run) |
| `libs/asplit-B.zip` | game kernel classes (compile + run) |
| `libs/fabric-loader-0.19.3.jar` | `compileOnly` dependency |

One more file is needed **only by the regression tests** (not by compilation). `verifyTestInputs`
checks it when you run `test`/`check`, so a compile-only `build` still works without it:

| File | Purpose |
|---|---|
| `libs/FloatIO.jar` | vanilla JSON decimal formatting; patched into `jdk.unsupported` for `regressionTest` |

### 2.3 `game/` (only to launch the game)

About 1.3 GB: `Airships.json`, `data/`, `lib/native`, … **Neither compilation nor the headless
regression needs it.** Only `gradlew startAirships` does, and `verifyGameInputs` reports it clearly.

If you would rather not copy 1.3 GB, keep only `mods/` separate and junction/symlink the rest to an
existing game directory — see `tools/reports/06-build-and-test.md` for a verified recipe.

---

## 3. Compiling

```sh
./build.sh              # compile only: gradle assemble
./build.sh full         # compile + all tests: gradle build
./build.sh clean        # clean, then compile: gradle clean assemble
./build.sh installApiMod        # anything else is forwarded to gradlew
./build.sh --info assemble      # Gradle options work too
```

Artifacts:

| File | What it is |
|---|---|
| `build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar` | the API layer (source of `game/mods/acbric-api.jar`) |
| `build/libs/Acbric-1.0-SNAPSHOT.jar` | the launch shim `AirshipsGameProvider` |

> The scripts do exactly two things: **find a JDK 21** and **hand the command to `gradlew`**.
> All build logic lives in `build.gradle`, so the IDE, CI and a hand-typed `gradlew` behave identically.
> The scripts are not a build system — do not put logic in them.

### Why it is fast

Three settings in `gradle.properties`, each backed by a measurement:

| Setting | Effect |
|---|---|
| `org.gradle.configuration-cache=true` | reuses the configuration phase: `assemble` 2.8 s → **1.9 s (−33 %)** |
| `org.gradle.parallel=true` | schedules the main / apiMod / regressionTest source sets in parallel |
| `org.gradle.caching=true` | content-addressed task output reuse — faster rebuilds and branch switches |
| `org.gradle.daemon=true` | **must stay on**: `--no-daemon` turns `assemble` into 12.4 s |

The configuration cache was verified against `build`, `regressionTest`, `apiModJar`, `installApiMod`,
`startAirships`, `distDir` and `syncModTemplateLibs` — no warnings, no incompatibilities.
If it ever misbehaves on your machine, disable it for one run:

```sh
./build.sh --no-configuration-cache
```

---

## 4. Testing

```sh
./test.sh all          # every suite (currently 872 assertions)
./test.sh event        # one suite
./test.sh ev           # unique prefix works too
./test.sh data rename  # several suites, run in the order given
./test.sh cp           # alias (cp = classpath)
./test.sh list         # list suites and what they cover
```

### Suites

| Suite | Checks | Covers | Ref |
|---|---|---|---|
| `data` | 3 | `DATA_LOADED` reports the game's real result: no log clearing, no failure-to-success rewriting | F01 |
| `bundle` | 44 | bundled vanilla resource store: 9 traversal cases, updates, user-edit preservation, conflicts, backups, interrupted-transaction recovery | F02 / F05 |
| `classpath` | 2 | launcher classpath excluded **by archive content** (Loader/Mixin/ASM/shim), game libraries kept, no duplicates | F03 |
| `event` | 12 | event bus: `registerOnce` fires exactly once, handle identity, duplicate registrations | F04 / F12 |
| `rename` | 7 | rename-panel legacy vs new event ordering and cancellation contract | F06 |
| `mods` | 67 | Fabric mod install validation and Java JAR enable/disable: bad schema / id / missing fields, dependency pre-check | F07 · `MOD_MANAGEMENT.md` |
| `campaign` | 41 | campaign data and lifecycle: missing-mod namespaces kept, load exits, explicit migration | `CAMPAIGN_DATA.md` · `CAMPAIGN_LIFECYCLE.md` |
| `config` | 119 | mod config and settings contract: drafts, same-handle conflict check, explicit save | `CONFIG.md` · `SETTINGS.md` |
| `scopes` | 108 | event subscription scopes and runtime diagnostics: listeners released, exceptions rethrown | `EVENT_SCOPES.md` |
| `manifest` | 45 | local code manifest export and offline compare | `CODE_MANIFEST.md` |
| `handshake` | 121 | code handshake protocol and lobby ready gate | `CODE_HANDSHAKE.md` · `LOBBY_HANDSHAKE.md` |
| `rules` | 120 | shared rule declaration, consistency check, explicit legacy-save migration | `SHARED_RULES.md` · `RULE_SAVE_MIGRATION.md` |
| `identity` | 20 | build identity and session startup diagnostics | `DIAGNOSTICS.md` |
| `ui` | 107 | public UI components, text selection/editing, input masking, zh/en language selection | `UI.md` |
| `devtools` | 56 | developer tools and console: command binding, execution, bounded diagnostics buffer | `DEVELOPMENT.md` · `COMMANDS.md` · `DEVELOPER_TOOLS.md` |

**872 checks in total**; the table is measured, not estimated — re-run `test list` after adding a suite.

Resolution order: exact name → alias → **unique** prefix → unique substring. An ambiguous or unknown
selector fails loudly with the list of valid names — it never silently runs the wrong thing.
Aliases cover the obvious synonyms: `cp`/`classes` → `classpath`, `settings` → `config`,
`lobby` → `handshake`, `migration` → `rules`, `java-mods` → `mods`, `console` → `devtools`.

### Sandbox

The regression needs **no** `game/` and **never touches player saves**: it redirects `user.home`,
`APPDATA` and the working directory into `build/regression-sandbox/` and writes a sandbox-pointing
`launch_settings.json` there. Fixtures live in `build/regression-sandbox/run-<random>/` and can be
deleted at any time.

### CI semantics are unchanged

`check` still depends on `regressionTest`, and with no selector `regressionTest` **runs every suite**.
So `gradlew build`, `build full` and CI behave exactly as before — suite selection only affects local
manual runs.

```sh
./build.sh full                                        # = gradlew build = compile + every suite
./test.sh all                                          # just every suite
gradlew regressionTest -Pacbric.suites=event,rename    # the equivalent for IDE / CI
```

> The launchers pass the selection through the **`ACBRIC_SUITES` environment variable** rather than
> `-Pacbric.suites=`, because cmd.exe splits an argument at `=`. The environment variable behaves
> identically in `.cmd` and `.sh`. Gradle itself accepts both forms.

### Adding a suite

1. Write a class under `src/regressionTest/java/net/fabricacs/regression/` following the existing style
   (`public static int run(...)`, asserting via `check(condition, message)` and returning the count).
2. Register it in the `static { ... }` block of `FrameworkRegression`:
   `register("name", "one-line description", FrameworkRegression::suiteXxx)`.
3. Call it from `suiteXxx` and accumulate with `checks += ...` like the others.
4. Verify with `./test.sh list` and `./test.sh <name>`.

**Do not start a parallel test entry point** (a second main, a second Gradle task) — the suite registry
is the single entry point, which is what keeps `test <name>` and CI from diverging.

---

## 5. Cross-platform notes

This entry point is designed so that it does not break on another machine. The hard constraints:

1. **`.cmd` files are ASCII-only and CRLF.** cmd.exe reads batch files using the console OEM code page,
   so UTF-8 comments get mis-decoded and executed as commands on a machine with a different code page
   (hit and fixed during development). `.gitattributes` pins `*.cmd text eol=crlf`.
2. **`.sh` files must stay LF.** A CRLF shell script dies with
   `bad interpreter: No such file or directory` on Linux/macOS. `.gitattributes` pins `*.sh text eol=lf`.
3. **No extension-less `build` / `test` on POSIX.** On case-insensitive filesystems (Windows, macOS by
   default) the name collides with Gradle's `build/` output directory, so the POSIX launchers are
   `build.sh` / `test.sh`.
4. **`gradlew` is now mode 100755.** It used to be 100644, which made `./gradlew` fail with
   `Permission denied` on Linux and macOS.
5. **The scripts locate the repo from their own path** (`%~dp0` / `$(dirname "$0")`), never from the
   current directory, and quote every path — this repository lives under
   `C:\Users\liu chang\…`, so paths with spaces are the normal case, not the exception.
6. **Git Bash / msys / cygwin hand over `JAVA_HOME` as a Windows path** (`C:\Program Files\…`);
   `build.sh` normalises it to `/c/Program Files/…` before testing it.
7. **cmd.exe splits arguments at `=`.** `build regressionTest -Pacbric.suites=event` arrives as
   `-Pacbric.suites` and `event`, and Gradle then reports a confusing `Task 'event' not found`.
   Quote it — `build regressionTest "-Pacbric.suites=event"` — or use the env variable
   (`set ACBRIC_SUITES=event`), which is what the launchers do. build.cmd detects the split and prints
   a hint instead of leaving you with Gradle's message.
8. **Two batch hazards are documented in the script comments** so they are not reintroduced:
   `for /f ('"\"quoted exe\" args"')` needs an **even** number of quotes or the parser swallows the rest
   of the file; and a path containing `)` closes a `( … )` block early. The current implementation
   redirects to a temp file and uses `set /p`, sidestepping both.

---

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `No JDK 21 found.` | no JDK 21 installed and `JAVA_HOME` is unusable | install one, or `set JAVA_HOME=C:\path\to\jdk-21` / `export JAVA_HOME=/path/to/jdk-21` |
| `Missing game files required for compilation` | `libs/asplit-*.zip` etc. absent | copy them from your Airships installation (§2.2) |
| `Missing game files required to launch Airships` | `game/Airships.json` etc. absent | only needed to launch; compiling and testing work without it |
| `Timeout … gradle-8.13-bin.zip` | another process holds the wrapper lock (often VS Code's Gradle extension downloading) | wait it out; do not delete the `.lck` |
| `./gradlew: Permission denied` on Linux/macOS | executable bit lost in an older checkout | `chmod +x gradlew build.sh`, or re-clone (the mode is fixed in this repository) |
| `bad interpreter` | a `.sh` checked out as CRLF | verify with `git check-attr eol build.sh` |
| First build is slow | Maven Central / maven.fabricmc.net fetch sponge-mixin and ASM | expected; cached afterwards |
| `Task 'event' not found` after `-Pacbric.suites=event` | cmd.exe split the argument at `=` | quote it, or use `set ACBRIC_SUITES=event` |
| `unknown regression suite: 'x'` | typo, or an ambiguous prefix | run `test list`; use the full suite name |

---

## 7. Rules for agents

- **Use `build`** to verify compilation. Do not use `gradlew build` for that — it also runs every test and
  prints 167 lines.
- After editing a mixin or `fabric.mod.json`, run the full regression **and**
  `acbric.cmd verify` from `Ac source/tools` (it statically checks injection targets and `@At` callsites
  against the real bytecode).
- **Never put logic in `build.cmd` / `build.sh` / `test.cmd` / `test.sh`.** They only find a JDK and
  forward. Add a Gradle task instead so the IDE and CI benefit too.
- **Run the suite that covers what you touched, then `test all` before you call it done.** A full run
  is 872 assertions and about 7 seconds; there is no reason to skip it. Use `test <suite>` while iterating
  (`test bundle` when editing `BundledResourceStore`, `test event` when editing `Event`).
- Add new tests to the suite registry (see "Adding a suite" in this document) rather than starting a
  parallel test entry point.
