# Bundled vanilla resource management

JAR mods may supply `acbric_vanilla/`. At preLaunch, Acbric installs these files into
`<local native MOD root>/<fabric-id>/`. External releases (dev.28+) use the shared `mods` folder beside Setup.cmd inside Acbric; legacy mode uses the vanilla user-data `mods` folder.

外部发行 dev.28 起，配套资源与 Java MOD 共用 Setup.cmd 同级的 `mods`；旧布局仍用原生用户数据下的 mods。
Top-level JAR/ZIP origins are supported; nested or exploded mod resources are not.

New installations contain `.acbric-bundle.json` with an ownership map of SHA-256 hashes,
the incoming bundle map and any unresolved conflicts. This filename is reserved and
must not be supplied by a mod archive.

## Updates

- Unchanged owned files are updated. Unchanged owned files removed upstream are deleted,
  including when an existing managed mod removes its entire bundle.
- Locally modified/deleted files are preserved and reported as conflicts. User files not
  owned by the framework are preserved; an incoming path collision is reported.
- A directory without a valid ownership record is never silently adopted or replaced.
  Invalid ownership JSON fails closed; repair requires inspecting the existing files.
- Conflicts appear in the console and the manifest's `conflicts` map. To accept an
  incoming file, close the game and explicitly replace that selected file with the new
  bundle's content. Matching content is recognized on the next launch and clears the conflict.

Changes are prepared outside the scanned mods directory. Before replacing a directory,
Acbric verifies it has not changed, records a transaction, and retains the original at
`<user-data>/.acbric-bundles/<fabric-id>/backups/<transaction-id>/`. A per-mod file lock
prevents simultaneous framework updates. An interrupted replacement is recovered before
the next install/update for that mod: restore the original if its destination is missing,
or finalize a recognized completed transaction. An ambiguous destination is preserved
and reported for manual recovery, never overwritten.

Backups are retained. They may be cleaned manually once the game is closed, no transaction
is pending and their contents are no longer needed. Do not remove a pending recovery
journal or its backup while diagnosing an interrupted update. This is process-interruption
recovery, not a guarantee against hardware/storage failure.

## Explicit migration of an old directory

Close the game. From a prepared source checkout, run the following with JDK 21. Replace
the three final arguments with the actual archive, **vanilla** mods directory and Fabric ID:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -cp 'build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar;libs/*;libs/asplit-A.zip;libs/asplit-B.zip' `
    net.fabricacs.api.impl.BundledVanillaModMigration `
    'C:/mods/example.jar' 'C:/Users/you/AppData/Roaming/AirshipsGame/mods' 'example_mod'
```

From an assembled distribution, use its bundled runtime and API location instead:

```powershell
& './jre/bin/java.exe' -cp 'game/mods/acbric-api.jar;libs/*;libs/asplit-A.zip;libs/asplit-B.zip' `
    net.fabricacs.api.impl.BundledVanillaModMigration `
    'C:/mods/example.jar' 'C:/Users/you/AppData/Roaming/AirshipsGame/mods' 'example_mod'
```

Migration requires an existing unmanaged directory. It retains a full backup, adopts
only files already identical to the supplied bundle, and records different/missing
legacy files as conflicts. It does not fill missing legacy files or replace user edits.
Matching adopted files can subsequently be updated normally. A directory already managed
by Acbric is updated at launch instead of being migrated again.

Removing a Java mod does not automatically remove its extracted vanilla directory or
backups. That lifecycle is intentionally separate from resource upgrades.
