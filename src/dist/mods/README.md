# MOD 放置目录 / MOD folder

此文件夹位于 `Acbric` 内，与 `Acbric.exe` 同级，是 Java MOD 根。原版 MOD 放在当前数据环境的 `mods`：共享模式为原版数据目录，环境隔离模式为本文件夹。原版文件夹内应直接包含 `info.json`；原版 `.amod` 压缩包使用游戏内“安装 MOD”导入。

This folder sits beside `Acbric.exe` and is the Java MOD root. Native MODs live in the active data environment's `mods`: the vanilla data directory in shared mode, or this folder in isolation. Native folders must directly contain `info.json`. Import native `.amod` archives using the game's Install MOD action.

API 默认识别原版 MOD 根目录 `AcbricMod/` 中的唯一 `.jar`，本地与 Steam 原版 MOD 都适用。The API recognizes one ACMOD `.jar` under `AcbricMod/` for both local and Steam native MODs.

核心 API 从 `core` 自动加载，不要另放一份。旧实例/User 目录不会自动导入，关闭游戏后手动复制需要的 MOD。移动整套框架时请一起移动此目录；分享框架时使用干净发行 ZIP，不打包自己的 MOD 或用户数据。

The API loads automatically from `core`; do not add a duplicate. Old instance/User folders are not imported; close the game before copying wanted MODs. Move this folder together with Acbric when relocating the release. Share the clean release ZIP, without your MODs or user data.
