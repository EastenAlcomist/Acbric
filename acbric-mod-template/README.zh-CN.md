# Acbric MOD 模板项目

这是一个独立 Gradle 项目，用来作为新的 Acbric MOD 起点。模板构建只依赖本目录自身内容，编译依赖放在 `libs/`，不会读取 Acbric 主工程的 `build.gradle`、`libs/` 或 `game/mods`。

## 构建

在本目录执行：

```powershell
.\gradlew.bat build
```

如果你把模板复制到了其他位置，`.\gradlew.bat build` 仍可直接运行。只有安装到游戏目录时需要修改 `gradle.properties`：

```properties
gameDir=C:/path/to/Acbric/game
```

Gradle 8.13 需要使用兼容的 Java 运行。若本机 `JAVA_HOME` 指向 JDK 25 或更新版本，请临时切到 JDK 21：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build
```

## 安装到游戏

```powershell
.\gradlew.bat installMod
```

该任务会构建 MOD，并复制到：

```text
<gameDir>/mods/acbric-template-mod.jar
```

## 改名清单

创建新 MOD 时通常需要修改这些位置：

- `gradle.properties`: `modId`、`modName`、`modDescription`、`modVersion`、`mavenGroup`、`modArchiveName`
- `src/main/resources/fabric.mod.json`: entrypoint 类名、描述和图标 metadata
- `src/main/java/net/fabricacs/template/TemplateMod.java`: Java 包名和类名
- `src/main/resources/assets/acbric_template_mod/icon.png`: 默认 MOD 图标

`libs/` 是模板的编译依赖目录，复制模板项目时需要一起复制。它包含 Acbric API、Fabric Loader、游戏编译桩和基础运行库，仅用于编译；最终 MOD jar 不会把这些依赖打包进去。在主工程内可执行 `.\gradlew.bat syncModTemplateLibs` 自动补齐此目录。

`modId` 建议只使用小写字母、数字和下划线，例如 `my_airships_mod`。

## 可用 API 示例

模板入口使用 Acbric API 的 `acbric` entrypoint：

```java
public final class TemplateMod implements AcbricInitializer {
    @Override
    public void onInitializeAcbric(AcbricModContext context) {
        context.logger().info("initialized");
    }

    @Override
    public void onInitializeAcbric() {
    }
}
```

常用上下文：

- `context.logger()`: 输出带 MOD id 的日志
- `context.ensureConfigDir()`: 获取并创建当前 MOD 的配置目录
- `context.ensureDataDir()`: 获取并创建当前 MOD 的数据目录

常用事件：

- `AirshipsLifecycleEvents`: 游戏生命周期
- `AirshipsDataEvents.DATA_LOADED`: ACS 数据加载完成
- `AirshipsClientEvents.CLIENT_TICK_START`: 客户端 tick
- `AirshipsCombatUiEvents`: 战斗 UI 扩展点

需要 Mixin 时，新增 `src/main/resources/<modid>.mixins.json`，并在 `fabric.mod.json` 里加入：

```json
"mixins": [
  "<modid>.mixins.json"
]
```

## UI 事件版本

本模板需要开发版 Acbric API `0.3.3-dev.3` 或更新版本及 Java 21，请先从匹配框架源码同步编译依赖。
入口的 `RENAME_SHIP_AFTER_TICK` 示例只在面板 tick 后输出一次日志，不表示改名已确认。
旧 `ONE_SHOT_*` 属于已弃用的兼容接口，新代码使用准确命名的新事件；复制模板时保留 metadata 中的最低 API 版本。

完整接口与取消/路径规则见框架仓库的 [API 手册](../API.zh-CN.md)，本次变化见 [中文改动记录](../CHANGELOG.zh-CN.md)。复制模板到独立位置后，可到上游仓库 dev 分支查阅这些文档。

## 战役数据示例

`CampaignDataExample.prepare(context, worldMap)` 展示初始化与格式迁移，不由模板自动执行。取得实际地图后，在游戏模拟线程的适当时机调用，联机要求各端一致执行。地图替换后重新获取句柄，写入不广播消息。详见[战役数据接口](../CAMPAIGN_DATA.zh-CN.md)。

## 受管理订阅（dev.6）

当前模板要求 API >=0.3.3-dev.6，用 `context.eventScope("application")` 管理注册；一次性监听执行后自动移出范围。应用范围跨战役保留，不在入口返回时关闭。局部范围由 MOD 显式关闭，初始化失败示例会清理已注册条目。完整契约见框架 EVENT_SCOPES.zh-CN.md。
