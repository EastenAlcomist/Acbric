# Acbric 安装、启动与更新（dev.29）

[English](INSTALLER.md)

解压 `Acbric-external.zip`，打开其中的 `Acbric` 文件夹。**Setup.cmd、Start Acbric.cmd 和 mods 现在位于同一层。**

```text
Acbric/
├─ Setup.cmd             配置 / Setup
├─ Start Acbric.cmd      启动 / Play
├─ Update Acbric.cmd     更新 / 恢复
├─ 使用说明.txt          玩家简明流程
├─ QUICK_START.txt       英文简明流程
├─ mods/
│  ├─ arc-overhaul.jar
│  └─ native-mod/info.json
├─ core/acbric-api.jar
└─ instances/default/...
```

## 第一次配置

1. 双击 **Setup.cmd**，选择中文或 English。
2. 点击“从 Steam 查找游戏”，或浏览包含 `Airships.json` 的完整游戏目录。查找只读取 Steam 注册表及游戏库配置，支持其他磁盘上的库；找到多份时选择一份，找不到时提示手动指定并保留已填写的路径。识别不安装游戏、不改写 Steam 配置。
3. 选择独立实例目录，默认是 `Acbric/instances/default`；也可以使用其他可写位置。实例保存存档、设置、缓存和日志，与游戏目录不能互相包含；框架内仅允许 `instances` 下的实例。
4. 点击“检查目录”，确认显示的游戏、实例与 MOD 路径；检查阶段不创建实例或更改启动绑定。
5. 点击“保存并生成入口”。成功后，同目录的 **Start Acbric.cmd** 就会启动本次保存的实例。

以后直接双击 **Start Acbric.cmd**，不需要再次运行安装器。若保存了多个实例，根目录入口使用**最近一次成功保存的实例**；切换时在 Setup 中选择需要的实例，读取、检查并保存。旧的实例内启动入口仍可使用。

## 放置 MOD

点击安装器“打开 MOD 目录”，或直接打开与 Setup 同层的 **mods**：

- Java MOD 放 `.jar` 文件。
- 原版 MOD 放直接包含 `info.json` 的文件夹；`.amod` 压缩包通过游戏内“安装 MOD”导入。
- API 从 `core/acbric-api.jar` 自动加载，不要另放一份。

dev.27 的外层相邻 mods、旧实例 `mods`/`userdata/mods` 以及原来 User 目录不再参与本地扫描。关闭游戏后，把需要的 MOD 手动复制到新的 `Acbric/mods`；不自动搬迁或删除旧文件。多个实例共用这些 MOD 文件，各自保留设置；一个 MOD 目录同一时间只允许一个游戏会话使用。

## 本地 ZIP 更新与恢复

1. 关闭游戏和 Setup，保留从可信来源取得的新版 `Acbric-external.zip`，不要直接覆盖解压。
2. 双击 **Update Acbric.cmd**，选择需要更新的现有 Acbric 目录。
3. 选择“是”，选择 ZIP，核对目标与版本后确认更新；选择“否”则恢复上一次更新前的框架。

首次从 dev.28 更新：先把新包解压到另一个目录，从新目录运行更新器，选择原 dev.28 目录为目标。更早的无清单版本需要新解压并配置。完整包应使用同样带 Java 的更新包；不能通过轻量包移除已有运行时。

更新仅处理发行清单中归属明确且哈希匹配的框架文件，保留 `mods`（包括说明文件）、实例、存档、设置和默认启动绑定。用户改过的框架文件、缺失文件或未归属同名文件会使更新停止。校验包括 ZIP 路径、文件哈希、核心清单和 API 版本；它用于完整性检查，不是发行者签名验证。

写入前保存备份和恢复记录。普通写入失败会尝试自动恢复；进程被强行结束时，下次启动拒绝使用混合版本，先运行更新器恢复。备份在 `.acbric-maintenance`，不会自动清理。恢复期间不要移动框架；如果旧版本没有更新入口，或中断时入口不可用，从另行解压的新包运行更新器并选择原目录。

恢复仅还原框架文件，不回退 MOD、存档或数据格式；新版本保存的数据不保证可由旧版本读取。更新/恢复均拒绝覆盖额外修改的文件；遇到错误请保留现场和备份，依据弹窗显示的临时日志排查。

可使用 `update.ps1 -TargetDir <目录> -PackagePath <ZIP> -CheckOnly` 仅检查；去掉 `-CheckOnly` 执行更新，或使用 `-TargetDir <目录> -Restore` 恢复。不自动联网下载、不自动导入旧版数据。

## 搬迁或使用新解压目录

解压新包后运行新位置的 Setup，选择原来的实例，点击“读取已有实例”，确认游戏位置，检查并保存。原存档与配置保留。移动整套框架时一起移动整个 Acbric 文件夹，其中已经包含 mods；游戏或外置实例移动后也应重新选择路径并保存。

包内默认实例的启动绑定使用相对路径，外置实例使用绝对路径。搬迁后仍需用 Setup 更新实例保存的框架位置。尚未配置时，Start 会提示先运行 Setup；实例缺失或绑定损坏时显示具体错误，不猜测另一份实例。

损坏的 `.acbric-active-instance.json` 不会被安装器静默覆盖；先备份并移开该文件，再用 Setup 读取已有实例并保存即可重建绑定。实例自身配置或启动脚本损坏/被修改时同样保留现场。保存期间使用实例与 MOD 目录锁，过期预览不能覆盖其他向导的新绑定。实例配置与根入口绑定分别原子保存；若最后一步失败，可重新检查并保存，不会删除已经保存的实例。

## Java 与日志

完整包带独立 Java 21。轻量包可设置 `JAVA_HOME`，或运行：

```powershell
.\setup.ps1 -JavaHome "C:/Java/jdk-21"
```

Java 8 会被拒绝。启动日志位于实例 `logs/acbric/launcher`，可用安装器“打开日志目录”；原生日志在 `userdata/log.txt`。向导失败时会显示系统临时目录的 `acbric-setup-*.log`。

自动化配置（不打开界面、不启动游戏）：

```powershell
.\setup.ps1 -GameDir "C:/Games/Airships" -InstanceDir "C:/AcbricInstances/default" -Language zh
```

内部 JSON 与维护记录不是公开 MOD API。自动联网更新、卸载、旧数据迁移和自动桌面快捷方式暂未实现。可自行给 Start Acbric.cmd 创建快捷方式；不要单独移动启动文件。

## 复验

```powershell
.\gradlew.bat build externalDistZip -PbundleRuntime -PexternalGameDir="C:/Games/Airships"
python tools/test_external_release.py --game-dir "C:/Games/Airships" --java-home "C:/Java/jdk-21" --tag rootentrycheck --installer
python tools/test_maintenance.py
```

脚本从无关工作目录实际执行根目录 CMD，验证 MOD 加载/安装、搬迁重绑定、占用拒绝、错误配置和模板安装位置；可加 `--arc-jar`。目录选择器、不同 DPI、完整战役、联机和 Workshop 不包含在该验收中。

dev.29 验证：987 项标准检查、25 项更新事务检查、6 项发行扫描对抗测试和两项根入口路径检查通过。实际读取本机 Steam 注册表/游戏库，找到 H 盘安装；双语面板检查通过。使用已验证 dev.28 ZIP 配置后升级、搬迁重绑定、回退，带 ARC 的三次真实菜单各绘制 30 帧并初始化音频，再次升级与模板构建安装通过。更新/恢复前后玩家文件逐项哈希一致；5,325 个源游戏文件内容未变。可用 `--upgrade-from <dev.28.zip> --installer` 复验。未自动点击原生文件选择器或更新弹窗，未重跑完整战役/联机/Workshop；下方为旧验收记录。

dev.28 验证：977 项标准检查、36 项真实外部加载检查、6 项发行扫描对抗测试通过。实际执行与 Setup 同层的 CMD，带 ARC 首次/搬迁重绑定后各绘制 30 帧并初始化音频；MOD 扫描、安装、配套资源、启停选择和模板安装目标通过。两项脚本路由检查覆盖相对/绝对实例与中文、空格、& 路径。损坏/缺失启动绑定、缺失实例、占用和搬迁提示均已验证。5,325 个源游戏文件内容不变。中英文面板已检查；未重跑完整战役/联机/Workshop。未提交/推送，未覆盖玩家运行副本；证据 99-研究工具/Acbric同层启动与MOD目录-dev28-20260926。
