# Acbric MOD 模板项目

[English](README.md)

独立 Gradle 项目，使用 JDK 21。复制 `local.properties.example` 为 `local.properties`，填入本机游戏 `gameInstallDir`、外部框架发行 `frameworkDir` 和目标实例 `instanceDir`，路径使用正斜杠。命令行 `-P` 可覆盖这些值。

```powershell
.\gradlew.bat build
.\gradlew.bat installMod
```

`build` 只引用游戏 A/B、游戏 lib、框架启动依赖和核心 API；不复制依赖进 MOD JAR。`installMod` 需要显式目标，将产物复制到 `<instanceDir>/mods`。复制或分享模板时不携带游戏内容、`libs/` 或个人 `local.properties`。

旧开发环境的 `libs/` 仍可作为兼容编译输入；`syncModTemplateLibs` 仅用于本机，不再属于外部发行流程。不要分享其生成的依赖目录。

## 改名清单

创建新 MOD 时通常需要修改这些位置：

- `gradle.properties`: `modId`、`modName`、`modDescription`、`modVersion`、`mavenGroup`、`modArchiveName`
- `src/main/resources/fabric.mod.json`: entrypoint 类名、描述和图标 metadata
- `src/main/java/net/fabricacs/template/TemplateMod.java`: Java 包名和类名
- `src/main/resources/assets/acbric_template_mod/icon.png`: 默认 MOD 图标

编译依赖由每位开发者在自己的 `local.properties` 中指定，个人路径及依赖不应提交或分发。

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

本模板需要开发版 Acbric API `0.3.3-dev.10` 或更新版本及 Java 21，请先从匹配框架源码同步编译依赖。
入口的 `RENAME_SHIP_AFTER_TICK` 示例只在面板 tick 后输出一次日志，不表示改名已确认。
旧 `ONE_SHOT_*` 属于已弃用的兼容接口，新代码使用准确命名的新事件；复制模板时保留 metadata 中的最低 API 版本。

完整接口与取消/路径规则见框架仓库的 [API 手册](../API.zh-CN.md)，本次变化见 [中文改动记录](../CHANGELOG.zh-CN.md)。复制模板到独立位置后，可到上游仓库 dev 分支查阅这些文档。

## 战役数据示例

`CampaignDataExample.prepare(context, worldMap)` 展示初始化与格式迁移，不由模板自动执行。取得实际地图后，在游戏模拟线程的适当时机调用，联机要求各端一致执行。地图替换后重新获取句柄，写入不广播消息。详见[战役数据接口](../CAMPAIGN_DATA.zh-CN.md)。

## 受管理订阅（dev.6）

当前模板要求 API >=0.3.3-dev.10，用 `context.eventScope("application")` 管理注册；一次性监听执行后自动移出范围。应用范围跨战役保留，不在入口返回时关闭。局部范围由 MOD 显式关闭，初始化失败示例会清理已注册条目。完整契约见框架 EVENT_SCOPES.zh-CN.md。

## 共享规则示例（dev.10）

`SharedRulesExample.declare(context, damagePercent)` 可在入口显式调用一次；玩法读取 `multiplier(handle, world)`，配置重载后用 `changeNextCampaign` 更新候选值。默认入口未启用此示例，不改变伤害。采用前阅读 [共享规则](../SHARED_RULES.zh-CN.md)，尤其是缺少规则的旧存档不能自动接纳。

公共 UI 需要 API dev.13 或更新版：见 [UI.zh-CN.md](../UI.zh-CN.md)。使用这些接口时请提高模板的最低 API 依赖版本。

可选设置页需要 API >= dev.18，见 SETTINGS.zh-CN.md；模板基础最低依赖不变，使用新功能时再提高要求。
