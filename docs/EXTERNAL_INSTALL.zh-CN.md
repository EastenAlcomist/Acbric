# 外部游戏安装与隔离

[English](EXTERNAL_INSTALL.md)

本页说明 Acbric 如何引用已有游戏安装、隔离实例数据并加载发行核心。玩家操作见[使用指南](INSTALLER.zh-CN.md)，打包与手动启动见[外部发行](EXTERNAL_START.zh-CN.md)。

## 目录关系

外部模式需要两个独立目录：

- **GameDir**：原有游戏安装，包含 `Airships.json`、根目录 A/B 归档、`lib/`、`data/` 和默认设计资源。
- **InstanceDir**：实例目录，保存配置、用户数据、日志和缓存。

两者不能相同或互相包含。移动任一目录后必须更新绑定；Acbric 不会猜测新位置。

## 预检查

预检查在启动游戏前验证：

- `Airships.json` 的结构、`classPath` 与标准 `Main.main(String[])` 入口。
- 游戏 A/B、关键依赖、重复类和基础资源目录。
- 游戏版本与代码归档指纹。
- Windows x64 原生库的 PE 架构。
- 实例目录可写、路径不重叠、没有链接/重解析目录。

预检查不会初始化游戏类，也不会启动游戏。成功只表示输入满足启动条件，不证明完整资源、DLC、MOD 或玩法兼容。

需要单独检查一份安装时可运行：

```powershell
.\gradlew.bat preflightTools
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build\preflight\check-install.ps1 -GameDir "D:/Games/Airships Conquer the Skies" -InstanceDir "D:/Acbric/instances/default" -JavaHome "D:/Java/jdk-21"
```

退出码 `0` 表示本阶段检查通过，`2` 表示参数或检查失败。控制台会给出报告路径。错误发生得足够早时，报告位于系统临时目录；成功结果另存到实例的 `logs/acbric/preflight/`。

## 启动流程

启动器确认游戏、实例和核心后，通过 Fabric 启动游戏：

1. `ExternalLauncher` 锁定实例并检查发行文件哈希。
2. Provider 读取所选游戏安装，按 `Airships.json` 组装游戏类路径。
3. API 从发行包 `core/acbric-api.jar` 加载，并在预启动前校验版本与来源。
4. Acbric 设置实例工作目录和合并后的启动设置。
5. 子 JVM 调用游戏入口，Fabric 执行 `acbric` 入口与游戏适配 Mixin。

缺少核心、核心不匹配、游戏输入无效或实例被占用时，启动会在游戏主类运行前失败。内部启动属性和探针命令不是公开 MOD API，也不构成安全边界。

## MOD 目录

Java MOD 与原生 MOD 使用不同根：

- Java `.jar`：`fabric.modsFolder`，即 `Acbric.exe` 同层 `mods/`。
- 原生 MOD：当前数据环境的原生 `mods/`。
- Java MOD 的 `acbric_vanilla/`：解包到对应原生 MOD 根，并按 Java MOD 启停状态屏蔽。

共享数据环境中的原生 MOD 位于原版用户数据；隔离环境中的原生 MOD 位于实例。不要将 Java `.jar` 放入原生目录，否则 Fabric 不会按预期加载。

## 数据与设置

实例配置入口为 `instances/<id>/config/launch-settings.json`。Acbric 将它与安装的 `launch_settings.json` 做顶层合并，生成实例 `.fabric/acbric/launch-settings.json`；未指定字段沿用安装值。损坏输入会中止启动，不静默回退。

外部模式始终将以下输出定位到实例：

| 字段 | 实际位置 |
| --- | --- |
| `customDataDirectoryLocation` | 实例 `userdata/`；共享模式下由启动器改为原版数据目录 |
| `customGIFSaveDirectoryLocation` | 实例 `userdata/gifs/` |

实例用户数据失败时，框架不会退回全局用户目录。任意第三方 Java MOD 仍可主动写入其他位置，因此这不是完整沙箱。

## 纹理缓存

外部模式按需读取游戏或 MOD 的原始图片，并在实例 `cache/game-textures/v1/<内容键>/` 生成本地缓存：

- 不复制完整资源树，不移动或删除原文件。
- 有原图时重新生成 raw，不盲目沿用旧 `.tex`。
- 来源、内容和元数据参与缓存键；不同 MOD 或实例不共用命名空间。
- 非法 raw 长度会在原生映射前删除并尝试从原图重建。
- 旧缓存不会自动回收；不要将实例缓存放入框架发行包。

## 诊断位置

- 实例预检查：`<InstanceDir>/logs/acbric/preflight/`
- 启动器日志：`%LOCALAPPDATA%/Acbric/launcher-logs`
- 完整启动输出：`<InstanceDir>/logs/acbric/launcher/launch-*.log`
- 原生日志：`<InstanceDir>/userdata/log.txt`
- 早期脚本失败：系统临时目录的 `acbric-bootstrap-*.log`

报告包含本地路径和版本信息，分享前请检查隐私内容。

## 已知边界

当前主要面向 Windows x64。自定义启动类、其他平台布局、官方服务器联机、跨设备显卡组合及任意第三方 MOD 不由外部加载层保证。原生地形绘制存在暂缓处理的 GL 异常，目前尚不能判定来源于框架或游戏本体；框架不会屏蔽该错误。
