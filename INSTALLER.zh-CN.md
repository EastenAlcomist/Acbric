# Acbric 安装与启动（dev.28）

[English](INSTALLER.md)

解压 `Acbric-external.zip`，打开其中的 `Acbric` 文件夹。**Setup.cmd、Start Acbric.cmd 和 mods 现在位于同一层。**

```text
Acbric/
├─ Setup.cmd             配置 / Setup
├─ Start Acbric.cmd      启动 / Play
├─ mods/
│  ├─ arc-overhaul.jar
│  └─ native-mod/info.json
├─ core/acbric-api.jar
└─ instances/default/...
```

## 第一次配置

1. 双击 **Setup.cmd**，选择中文或 English。
2. 选择包含 `Airships.json` 的完整游戏目录。
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

## 更新与搬迁

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

内部 JSON 只是路径数据，不是公开 MOD API。框架更新/回滚、卸载、自动探测、旧数据迁移和自动桌面快捷方式暂未实现。可自行给 Start Acbric.cmd 创建快捷方式；不要单独移动启动文件。

## 复验

```powershell
.\gradlew.bat build externalDistZip -PbundleRuntime -PexternalGameDir="C:/Games/Airships"
python tools/test_external_release.py --game-dir "C:/Games/Airships" --java-home "C:/Java/jdk-21" --tag rootentrycheck --installer
```

脚本从无关工作目录实际执行根目录 CMD，验证 MOD 加载/安装、搬迁重绑定、占用拒绝、错误配置和模板安装位置；可加 `--arc-jar`。目录选择器、不同 DPI、完整战役、联机和 Workshop 不包含在该验收中。

dev.28 验证：977 项标准检查、36 项真实外部加载检查、6 项发行扫描对抗测试通过。实际执行与 Setup 同层的 CMD，带 ARC 首次/搬迁重绑定后各绘制 30 帧并初始化音频；MOD 扫描、安装、配套资源、启停选择和模板安装目标通过。两项脚本路由检查覆盖相对/绝对实例与中文、空格、& 路径。损坏/缺失启动绑定、缺失实例、占用和搬迁提示均已验证。5,325 个源游戏文件内容不变。中英文面板已检查；未重跑完整战役/联机/Workshop。未提交/推送，未覆盖玩家运行副本；证据 99-研究工具/Acbric同层启动与MOD目录-dev28-20260926。
