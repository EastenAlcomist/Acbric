# Acbric 外部发行与启动

[English](EXTERNAL_START.md)

Acbric 的外部发行包引用玩家已有的游戏安装，不包含游戏代码、资源、游戏依赖或玩家数据。推荐使用 `Acbric.exe`；需要脚本或排错时再使用 `advanced/` 中的工具。

```text
Acbric/
├─ Acbric.exe
├─ advanced/
├─ mods/
├─ core/acbric-api.jar
├─ loader-libs/
├─ runtime/             默认 Java 21 运行时 / default Java 21 runtime
└─ instances/default/...
```

Java `.jar` 放在 Acbric 同层 `mods/`。原版 MOD 使用当前数据环境的原生 `mods/`：共享模式为原版数据目录，隔离模式为 Acbric `mods/`。原版文件夹应直接包含 `info.json`，`.amod` 通过游戏内“安装 MOD”导入。旧布局目录不会自动参与扫描。

## 玩家入口

双击 `Acbric.exe`，确认游戏位置后点击 **使用此游戏**，以后点击 **开始游戏**。详细操作、Steam 启动、环境隔离与更新见[玩家指南](INSTALLER.zh-CN.md)。

## 手动启动

玩家通常不需要手动启动。需要诊断时，可以在完整发行包中执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "C:/Tools/Acbric/start.ps1" -GameDir "C:/Games/Airships" -InstanceDir "C:/AcbricInstances/default"
```

`GameDir` 必须包含 `Airships.json` 和游戏 A/B 归档；`InstanceDir` 是可写目录，二者不能互相包含。若省略 `JavaHome`，启动器优先使用包内 `runtime`，否则使用 `JAVA_HOME`。不要使用游戏自带的 Java 8。

启动器会验证核心与启动依赖、重新检查游戏，并在实例目录启动子 JVM。API 从 `core/acbric-api.jar` 加载，不要把它复制到 `mods`。哈希清单用于发现缺失或损坏，不是数字签名。

## 数据位置

- MOD：`<Acbric 目录>/mods/`
- 存档及原版设置：`<InstanceDir>/userdata/`
- MOD 配置：`<InstanceDir>/config/`
- 启动设置：`<InstanceDir>/config/launch-settings.json`
- 启动日志：`<InstanceDir>/logs/acbric/launcher/launch-*.log`
- 原生日志：`<InstanceDir>/userdata/log.txt`

实例默认独立，不自动导入旧存档。首次启动会创建本地纹理缓存；不要分享实例、缓存或游戏副本。第三方 Java MOD 仍可主动写入其他位置。

## 构建发行包

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships"
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships" -PbundleRuntime=false
```

默认命令使用 JDK 21 的 `jlink` 附带固定 Java 21 运行时；第二个命令生成不带运行时的精简包。输出为 `build/external-dist/Acbric-external-<version>.zip`，校验报告为同目录的 `verification.json`。

外部发行包不包含游戏本体。`externalGameDir` 仅用于校验发行包未混入玩家游戏内容。旧 `distZip` 会打包本地游戏目录，只用于旧布局开发。

## 已知边界

当前主要面向 Windows x64。创意工坊、任意第三方 MOD、跨设备和不同显卡组合仍需针对性验证。原生地形 GL 异常已暂缓处理，尚不能判定来源于框架或游戏。
