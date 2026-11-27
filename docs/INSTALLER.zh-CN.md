# Acbric 玩家使用指南

[English](INSTALLER.md)

## 首次使用

1. 完整解压 `Acbric-external-<version>.zip`，打开其中的 `Acbric.exe`。游戏本体需单独安装。
2. 启动器会自动查找 Steam 游戏。确认位置后点击 **使用此游戏**；未找到或想使用另一份游戏时，点击 **选择游戏文件夹**。
3. 配置完成后点击 **开始游戏**。以后打开同一个 EXE 即可。

右上角可切换中文/English。游戏运行期间，启动器会等待游戏退出后恢复操作。首次使用时可在首页点击 **一键同步**，直接使用原版存档与 MOD。

```text
Acbric/
├─ Acbric.exe
├─ mods/
├─ docs/
├─ advanced/
├─ core/、loader-libs/、runtime/
└─ instances/default/
```

`Acbric.exe` 是日常入口，不是单文件程序。请保留完整目录；需要桌面入口时创建 EXE 快捷方式。

## 启动方式

- **开始游戏**：直接启动，不要求 Steam。
- **Steam 启动（正版）**：要求游戏位于真实 Steam 库、Steam 已登录，且账号拥有 App `342560`。该方式启用 Steamworks、游戏状态和创意工坊；首次使用或更新 MOD 时，Steam 可能先同步订阅内容。

## 添加 MOD

点击 **打开 MOD 文件夹**：

- Java MOD：将 `.jar` 放入 Acbric 同层 `mods`。
- 原版 MOD：直接包含 `info.json` 的文件夹放入当前数据环境的 `mods`。共享模式下这是原版游戏的 MOD 目录；环境隔离模式下是 Acbric 同层 `mods`。
- 原版 `.amod`：在游戏内使用“安装 MOD”导入。

**打开 MOD 文件夹**打开 Java MOD 根；共享模式的原生 MOD 应放入原版数据目录。API 已内置，不要重复放入。增删或启停 Java MOD 后需重启游戏。各实例的 MOD 设置位于 `instances/default/config`。

## 存档与 MOD 数据

默认模式直接使用原版数据，不复制存档或 MOD：

1. 原版游戏若设置过自定义数据位置，Acbric 优先读取游戏安装目录 `launch_settings.json` 的 `customDataDirectoryLocation`。
2. 否则使用 `%APPDATA%\AirshipsGame`。
3. **一键同步**只让当前实例绑定这些位置，不写入或删除原版文件。Java `.jar` 仍位于 Acbric 的 `mods`。

若要让 Acbric 使用独立副本，打开 **设置 → 环境隔离**：

1. 关闭游戏。
2. 确认窗口会列出复制和移除范围。
3. 原版数据会完整镜像到 `instances/default/userdata` 与实例 `mods`；被覆盖或移除的条目先备份到 `instances/default/userdata/.acbric-sync-backup/<时间戳>/`。
4. 再次执行环境隔离可刷新副本；点击首页 **一键同步** 会切回直接使用原版数据。原版目录不会被 Acbric 删除。

## 设置与维护

- **更换游戏位置**：选择移动后的游戏目录。
- **环境隔离**：切换独立存档与 MOD 副本。
- **安装更新包**：关闭游戏后选择可信的 `Acbric-external-<version>.zip`。不要直接把更新包覆盖解压到旧目录。
- **恢复上一版**：恢复最近一次更新前的框架文件。存档、设置和 MOD 不随框架回滚；旧框架可能无法读取新数据。
- **高级设置**：自定义数据目录或切换已有实例，日常使用无需修改。

从 dev.32 或更早版本升级时，先把新包解压到另一处，运行其中的 `advanced/Update Acbric.cmd`，再选择原目录和新 ZIP。没有受支持清单的旧版需要重新配置；Acbric 不自动迁移旧布局数据。

更新会核对文件归属、哈希和核心版本。修改过的文件、路径冲突或被占用的文件会阻止替换；哈希校验不验证发布者身份。中断更新后先使用启动器提示的恢复流程，恢复前不要移动目录。

## 排错

启动器问题请附启动器/维护日志；MOD 导致的游戏问题请向 MOD 作者提供原生日志。

- 启动器与维护日志：`%LOCALAPPDATA%/Acbric/launcher-logs`
- 完整启动输出：`<实例>/logs/acbric/launcher/launch-*.log`
- 原生日志：`<实例>/userdata/log.txt`
- 环境隔离备份：`<实例>/userdata/.acbric-sync-backup/<时间戳>`

在 **帮助 → 导出诊断** 中生成 ZIP。诊断包含版本和近期日志，不包含存档，也不会自动上传。**帮助 → 技术详情** 仅在主动打开时显示异常细节。

## 平台与限制

当前发行面向 Windows x64。默认发行包携带固定 Java 21 runtime，无需单独安装 Java；只有显式使用 `-PbundleRuntime=false` 生成的精简包才需要有效的 `JAVA_HOME`。EXE 尚未代码签名，不提供联网自动更新、旧布局自动导入或卸载向导。
