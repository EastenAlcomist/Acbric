# Acbric 外部发行启动（dev.25）

[English](EXTERNAL_START.md)

这是 Windows x64 的实验性框架发行包。请自备完整游戏和 Java 21；包内不含游戏代码、资源、游戏依赖或玩家数据。尚未提供图形安装器、数据迁移及自动更新。

## 启动

解压后打开 PowerShell，执行（路径换成本机路径）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:/Tools/Acbric/start.ps1" -GameDir "C:/Games/Airships" -InstanceDir "C:/AcbricInstances/default" -JavaHome "C:/Java/jdk-21"
```

`GameDir` 是包含 `Airships.json` 和 `asplit-A.zip` 的完整游戏根目录；`InstanceDir` 是独立可写目录，两者不能互相包含。每次启动明确传入路径，不需要复制游戏。`JavaHome` 可省略：优先使用包内可选 `runtime`，否则使用 `JAVA_HOME`。不要选游戏自带 Java 8。移动目录后传入新路径即可。

启动器验证核心与启动依赖的 SHA-256 清单，重新检查游戏，随后在实例工作目录启动子 JVM。核心 API 由包内 `core/acbric-api.jar` 加载；**不要再把 API 复制到实例 mods**。清单用于检测缺失/损坏，不是数字签名。不要手工拼接 Knot 或内部探针命令作为玩家启动方式。

同一实例只允许一个游戏进程使用。框架不改变原安装设置；实例默认独立，不自动读取或导入原有存档。

## MOD、配置和日志

- Java MOD：`<InstanceDir>/mods/`。
- 原生资源 MOD：`<InstanceDir>/userdata/mods/`。
- 存档及原生设置：`<InstanceDir>/userdata/`。
- MOD 配置：`<InstanceDir>/config/`。
- 启动设置覆盖：`<InstanceDir>/config/launch-settings.json`；原生数据和 GIF 输出路径仍强制位于实例。
- 完整启动输出：`<InstanceDir>/logs/acbric/launcher/launch-*.log`；原生日志：`userdata/log.txt`。
- 定位/核心检查失败时：控制台给出系统临时目录的 `acbric-bootstrap-*.log` 或 `acbric-launch-*.log`。

首次启动会创建本地纹理缓存，不要把实例、缓存或游戏副本一起分享。框架无法阻止任意第三方 Java MOD 自行写入其他位置。

## 从源码打包与制作 MOD

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships"
# 可选：同时打包当前 JDK 21 生成的独立运行时
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships" -PbundleRuntime
```

产物为 `build/external-dist/Acbric-external.zip`，递归内容扫描报告为同目录 `verification.json`。不要分享旧 `distZip`，它仍含本地游戏内容，仅保留旧布局开发使用。

模板位于 `acbric-mod-template/`。复制 `local.properties.example` 为 `local.properties`，填入本机 `gameInstallDir`、本框架发行的 `frameworkDir` 和目标 `instanceDir`，使用 JDK 21 构建。只引用依赖，不复制游戏到模板；个人路径和旧 libs 均排除在 Git/发行清单之外。模板 `installMod` 仅在显式配置目标实例后执行。

## 已知边界

菜单、战役和媒体的详细证据及限制见 [外部安装验证](EXTERNAL_INSTALL.zh-CN.md)。旧布局也能复现的地形 GL 异常已按用户决定暂缓，不阻塞安装重构；不表示异常已经修复。Workshop、所有第三方 MOD、跨设备和多种显卡仍需进一步验收。迁移、安装器及更新/卸载将在后续阶段实现。

## 开发者复验

```powershell
python tools/test_distribution_scan.py
python tools/test_external_release.py --game-dir "C:/Games/Airships" --java-home "C:/Java/jdk-21" --tag releasecheck
```

先构建 `regressionTestClasses` 和 `externalDistZip`。脚本在 build 下创建全新隔离目录，短暂打开真实游戏，由仅在测试实例安装的 MOD 在观察菜单帧后退出；还检查实例锁、坏包、错误安装路径和独立模板构建。`--arc-jar <路径>` 可选加载 ARC。报告位于 `build/external-release-tests/<tag>/`，每次使用新标签。正式发行包不含测试 MOD。
