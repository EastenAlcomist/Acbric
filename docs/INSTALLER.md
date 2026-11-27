# Acbric player guide

[中文](INSTALLER.zh-CN.md)

## First use

1. Fully extract `Acbric-external-<version>.zip` and open `Acbric.exe`. Install the game separately.
2. The launcher searches Steam automatically. Confirm the location and select **Use this game**, or select **Choose game folder** for another installation.
3. Select **Start game** when setup is complete. Use the same EXE next time.

The language selector at the top right is saved. While the game runs, launcher actions remain disabled until it exits. On first use, select **Sync now** on the home screen to use the vanilla saves and MODs directly.

```text
Acbric/
├─ Acbric.exe
├─ mods/
├─ docs/
├─ advanced/
├─ core/, loader-libs/, runtime/
└─ instances/default/
```

`Acbric.exe` is the everyday entry point, not a single-file program. Keep the complete directory together; create an EXE shortcut for desktop access if needed.

## Launch modes

- **Start game** launches directly and does not require Steam.
- **Steam launch (owned copy)** requires an installation in a real Steam library, a signed-in Steam client and an account that owns App `342560`. It enables Steamworks, game status and Workshop access. Steam may synchronize subscribed content on first use or after MOD updates.

## Add MODs

Select **Open MOD folder**:

- Java MODs: place `.jar` files in the `mods` folder beside Acbric.
- Native MODs: place folders that directly contain `info.json` in the active data environment's `mods` folder. In shared mode this is the vanilla MOD directory; with environment isolation it is Acbric's `mods` folder.
- Native `.amod` archives: import them through the game's Install MOD action.

**Open MOD folder** opens the Java MOD root; in shared mode, native MODs belong in the vanilla data directory. The API is included; do not add another copy. Restart after adding, removing or enabling/disabling Java MODs. MOD settings are stored under `instances/default/config`.

## Saves and MOD data

By default, Acbric uses the vanilla data directly and copies neither saves nor MODs:

1. If the game has a custom data directory, Acbric reads `customDataDirectoryLocation` from the installation's `launch_settings.json`.
2. Otherwise it uses `%APPDATA%\AirshipsGame`.
3. **Sync now** binds the current instance to those locations without writing to or deleting vanilla files. Java `.jar` MODs remain in Acbric's `mods` folder.

For isolated copies, open **Settings → Environment isolation**:

1. Close the game.
2. Review the copy/removal scope in the confirmation window.
3. Acbric mirrors the vanilla data into `instances/default/userdata` and the instance `mods` folder. Overwritten or removed items are backed up first under `instances/default/userdata/.acbric-sync-backup/<timestamp>/`.
4. Run isolation again to refresh the copies, or select **Sync now** to return to the vanilla data. Acbric does not delete the vanilla directory.

## Settings and maintenance

- **Change game location**: select the moved game installation.
- **Environment isolation**: switch to independent saves and MOD copies.
- **Install update package**: close the game and select a trusted `Acbric-external-<version>.zip`. Do not extract an update over the old directory.
- **Restore previous version**: restore framework files from before the last update. Saves, settings and MODs are not rolled back; older framework versions may not read newer data.
- **Advanced settings**: configure a custom data directory or switch existing instances. Everyday use does not require this screen.

To upgrade from dev.32 or earlier, extract the new package elsewhere, run its `advanced/Update Acbric.cmd`, then select the original folder and new ZIP. Packages without a supported manifest require fresh setup; Acbric does not migrate legacy layouts automatically.

Updates verify file ownership, hashes and core version. Edited, conflicting or occupied files block replacement; hash checks do not authenticate the publisher. After an interrupted update, follow the launcher's recovery flow and do not move the directory first.

## Troubleshooting

Send launcher/maintenance logs for launcher problems and the native log to the MOD author for MOD-related game problems.

- Launcher and maintenance logs: `%LOCALAPPDATA%/Acbric/launcher-logs`
- Complete launch output: `<instance>/logs/acbric/launcher/launch-*.log`
- Native log: `<instance>/userdata/log.txt`
- Isolation backups: `<instance>/userdata/.acbric-sync-backup/<timestamp>`

Use **Help → Export diagnostics** to create a ZIP containing the version and recent logs. It does not include saves and is never uploaded automatically. **Help → Technical details** shows exception details only when opened.

## Platform and limits

The current player distribution targets Windows x64. Default releases include a fixed Java 21 runtime and do not require a separate Java installation; only minimal packages built with `-PbundleRuntime=false` require a valid `JAVA_HOME`. The EXE is not code-signed. Online auto-update, automatic legacy import and an uninstall wizard are not provided.
