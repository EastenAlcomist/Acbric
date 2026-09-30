# Acbric player guide (0.3.5)

[中文](INSTALLER.zh-CN.md)

## First use

1. Fully extract the distribution with bundled Java and open **Acbric.exe** in its `Acbric` folder. Install the game separately.
2. The launcher searches Steam automatically. Confirm the displayed location and select **Use this game**. Use **Choose game folder** if nothing was found or you want another installation.
3. Select **Start game** when ready. Open the same EXE next time; no repeated setup is needed.
4. To keep the saves, designs and MODs from your original game, select **Sync now** on the home screen (see below).

The Chinese/English choice at the top right is saved. The default data folder is configured automatically; ordinary players do not need to manage an “instance”. Launcher actions are disabled while the game runs and restored after it closes.

```text
Acbric/
├─ Acbric.exe            One everyday entry
├─ mods/                 Put MODs here
├─ 使用说明.txt / QUICK_START.txt
├─ docs/                 Full documentation
├─ advanced/             Legacy setup, launch and maintenance tools
├─ core/, loader-libs/, runtime/ and supporting files
└─ instances/default/    Data created during first setup
```

There is one EXE **entry**, not a single-file framework. Keep the entire folder together; create an EXE shortcut for desktop access. Supporting PowerShell scripts do not need to be opened manually.

## Add MODs

Select **Open MOD folder**. Place Java `.jar` MODs or native folders directly containing `info.json` here. Import `.amod` through the game's Install MOD action. The API is included; do not add a duplicate API JAR. Restart after adding, removing or enabling/disabling Java MODs.

Old layouts/User folders are not imported on their own; a new instance uses the vanilla saves and MODs directly (next section), so no copying is needed. MOD settings live in `instances/default/config`. Each instance records its own data environment: shared instances use the vanilla data, isolated ones use their own copies.

## Sync now (use the vanilla saves and MODs directly, no copying)

By default Acbric does **not** copy any save or MOD: it works directly on the original game data. **Sync now** at the bottom of the home screen detects the vanilla locations and points this instance at them in one click:

- Detection: the game installation's `launch_settings.json` (`customDataDirectoryLocation`) wins when you moved the data folder in the original game; otherwise `%APPDATA%\AirshipsGame`, that is `C:\Users\<you>\AppData\Roaming\AirshipsGame`.
- Used directly: `saves`, designs (`ships`/`buildings`/`landships`), `combats`, `missions`, `recordings` and `recordingsArchive`, plus the vanilla MOD folders under `mods` — all of them stay in the vanilla folder.
- Only one copy exists: at launch Acbric points both the native data directory and the native MOD scan at that folder, so nothing is duplicated, no extra space is used, the two can never drift apart, and the original game and Acbric share the same data.
- Java `.jar` MODs still live in the `mods` folder beside `Acbric.exe`: the original game does not load Fabric MODs, so this copy is not duplicated either.

Syncing writes and deletes nothing, so it asks for no confirmation and simply reports the detected paths in the status line. If the vanilla folder is missing (for example the original game was never started), the launcher asks you to run it once or to use isolation below.

## Environment isolation (optional)

When this instance should use its own data and stop touching the vanilla folder, open **Settings → Environment isolation**:

1. Close the game first; isolation is refused while the game runs or another instance holds the MOD folder.
2. The confirmation window lists how many files will be copied and removed, where the vanilla data is and what is covered.
3. On confirmation the vanilla data is mirrored into the instance copies (`instances/default/userdata` and Acbric's `mods`) and the mode switches only after that succeeds. The copy is a **full mirror**: extra items in the instance copy are removed, and everything overwritten or removed is first backed up to `instances/default/userdata/.acbric-sync-backup/<timestamp>/` (split into `userdata/` and `mods/`; the result window shows the path).
4. Open **Settings → Environment isolation** again to refresh the copies from the current vanilla data. Selecting **Sync now** goes back to using the vanilla data directly; the isolated copies stay on disk and are never deleted. Files in the vanilla folder are never deleted either.

## Settings and maintenance

- **Change game location**: select a moved or different game, retaining the original data folder, then confirm.
- **Environment isolation**: switch this instance to its own saves and MODs (see above) — useful when you want two separate progress sets or do not want to touch the vanilla install.
- **Install update package**: close the game and choose a trusted `Acbric-external-<version>.zip` in Settings. The launcher exits before updating and reopens on success. Do not extract updates over the old directory.
- **Restore previous version**: restore framework files from before the last update. MODs, saves and settings are not rolled back; older frameworks may not read newer saved data.
- **Advanced settings**: choose a custom data folder or switch existing instances. Select the instance, load, check and save. Everyday use does not require this screen.

First upgrade from dev.32 or earlier: extract the new package elsewhere, run its `advanced/Update Acbric.cmd`, and select the original Acbric folder and new ZIP. The old updater does not recognize the EXE file manifest. Releases without a supported manifest require fresh setup; legacy data is not migrated automatically. This compatibility tool is for initial upgrades/recovery; later updates use the EXE.

Updates still verify ownership manifests, file hashes and core versions. Edited files, path conflicts and occupied files block replacement. Verification does not authenticate the publisher. Keep `.acbric-maintenance` backups. After interrupted updates the EXE offers recovery first. If the entry itself is missing or damaged, use maintenance tools from a separately extracted complete package and select the original folder. Do not relocate it before recovery.

## Getting help

Normal errors show a readable explanation and **Retry**, without a Java stack trace. Use **Help → Export diagnostics** to save a ZIP and send it to the developer yourself. Export requires confirmation: it contains the version, recent launcher/game-launch logs and potentially local paths, but no saves. Nothing is uploaded automatically. Each category contributes up to 10 logs, at most 1 MiB per log and 10 MiB total; large logs are truncated. Existing reports are not overwritten.

**Help → Technical details** displays the latest operation's exception only when requested. If Java is missing or the GUI cannot open, the EXE shows a short explanation and an Open diagnostics folder button.

Diagnostic locations:

- Launcher/maintenance logs: `%LOCALAPPDATA%/Acbric/launcher-logs`.
- Complete game launch output: `<instance>/logs/acbric/launcher/launch-*.log`.
- Native game log: `<instance>/userdata/log.txt`; provide separately if needed. Export does not collect userdata.
- Environment isolation backups: `<instance>/userdata/.acbric-sync-backup/<timestamp>`, holding whatever isolation replaced or removed; delete it yourself when it is no longer needed.
- Advanced setup failures also retain temporary `acbric-setup-*.log` files; ordinary setup exceptions go to launcher logs.

Damaged configuration is preserved rather than reset or replaced with another guessed save folder. Export diagnostics; do not create an empty instance.json. Initial saving retains atomic directory publication, occupancy retries and locking.

## Moving folders and limits

Move the whole Acbric folder, open the EXE and confirm the game to rebind the original instance. For moved external instances, explicitly select the original folder in Advanced settings. Internal instance bindings are relative; external bindings are absolute.

Currently Windows x64. Player packages include Java 21; minimal packages require a valid `JAVA_HOME`. The EXE is not code-signed. There is no online auto-update, automatic legacy import or uninstall wizard; the vanilla data is used directly by default and a copy is made only when you choose **Settings → Environment isolation**. Native GL issues, arbitrary MOD combinations and other devices require targeted acceptance.
