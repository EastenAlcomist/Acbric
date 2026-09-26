# MOD 放置目录 / MOD folder

此文件夹位于 `Acbric` 内，与 `Setup.cmd` 同级。Java MOD 的 `.jar` 和原版 MOD 的文件夹都放在这里；原版文件夹内应直接包含 `info.json`。原版 `.amod` 压缩包使用游戏内“安装 MOD”导入。

Put Java MOD `.jar` files and native MOD folders here, beside `Setup.cmd` inside `Acbric`. Native folders must directly contain `info.json`. Import native `.amod` archives using the game's Install MOD action.

核心 API 从 `core` 自动加载，不要另放一份。旧实例/User 目录不会自动导入，关闭游戏后手动复制需要的 MOD。移动整套框架时请一起移动此目录；分享框架时使用干净发行 ZIP，不打包自己的 MOD 或用户数据。

The API loads automatically from `core`; do not add a duplicate. Old instance/User folders are not imported; close the game before copying wanted MODs. Move this folder together with Acbric when relocating the release. Share the clean release ZIP, without your MODs or user data.
