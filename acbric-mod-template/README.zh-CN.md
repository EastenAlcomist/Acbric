# Acbric MOD 模板项目

[English](README.md)

独立 Gradle 项目，要求 JDK 21 或更高版本。复制 `local.properties.example` 为 `local.properties`，填入本机游戏 `gameInstallDir`、外部框架发行 `frameworkDir` 和目标实例 `instanceDir`，路径使用正斜杠。命令行 `-P` 可覆盖这些值。

```powershell
.\gradlew.bat build
.\gradlew.bat installMod
```

`build` 引用游戏 A/B、游戏 lib、框架启动依赖和核心 API，不把依赖复制进 MOD JAR。`installMod` 将产物复制到 `<frameworkDir>/mods`。游戏内容、个人 `local.properties` 和本地依赖都不应进入模板副本或发行包。

旧开发环境的 `libs/` 仍可作为兼容编译输入；`syncModTemplateLibs` 只用于本机。

## 改名清单

创建新 MOD 时通常需要修改这些位置：

- `gradle.properties`: `modId`、`modName`、`modDescription`、`modDescriptionEnglish`、`modDescriptionChinese`、`modVersion`、`mavenGroup`、`modArchiveName`
- `src/main/resources/fabric.mod.json`: entrypoint 类名、描述和图标 metadata
- `src/main/java/net/fabricacs/template/TemplateMod.java`: Java 包名和类名
- `src/main/resources/assets/acbric_template_mod/icon.png`: 默认 MOD 图标

`modId` 建议只使用小写字母、数字和下划线，例如 `my_airships_mod`。

### MOD 列表双语描述

`fabric.mod.json` 顶层 `description` 保留为兼容回退值；Acbric 会优先读取：

```json
"custom": {
  "acbric": {
    "description": {
      "en": "English description.",
      "chi": "中文描述。"
    }
  }
}
```

模板默认把 `modDescriptionEnglish` 和 `modDescriptionChinese` 展开到 `en`/`chi`；可以继续添加原版语言键 `de`、`fr`、`jpn`、`kor`、`ru`。Acbric 按当前游戏 Locale 选择，`zh`/`zho` 可兼容 `chi`，`ja`/`ko` 可兼容 `jpn`/`kor`；缺少当前语言时回退英文，最后回退顶层 `description`。这套描述用于原版 MOD 列表中的 Fabric 行和 Acbric 详情页，不改变标准 Fabric metadata 的单字符串限制。

`gradle.properties` 按 Java Properties 规则读取。中文值应使用 `\uXXXX` 转义；模板中的 `modDescriptionChinese` 已使用转义，不能直接改回普通 UTF-8 中文，否则展开结果会乱码。

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

## API 版本与事件示例

本模板需要 Acbric API `0.3.8` 或更新版本。先从匹配的框架发行或源码准备编译依赖。
入口的 `RENAME_SHIP_AFTER_TICK` 示例只在面板 tick 后输出一次日志，不表示改名已确认。
旧 `ONE_SHOT_*` 属于已弃用的兼容接口，新代码使用准确命名的新事件；复制模板时保留 metadata 中的最低 API 版本。

完整接口与取消/路径规则见 [API 手册](../docs/API.zh-CN.md)，版本变化见 [中文改动记录](../docs/CHANGELOG.zh-CN.md)。

## 战役数据示例

`CampaignDataExample.prepare(context, worldMap)` 展示初始化与格式迁移，不由模板自动执行。取得实际地图后，在游戏模拟线程的适当时机调用，联机要求各端一致执行。地图替换后重新获取句柄，写入不广播消息。详见[战役数据接口](../docs/CAMPAIGN_DATA.zh-CN.md)。

## 受管理订阅

模板用 `context.eventScope("application")` 管理注册；一次性监听执行后自动移出范围。应用范围跨战役保留，不在入口返回时关闭。局部范围由 MOD 显式关闭，初始化失败示例会清理已注册条目。完整契约见 [事件范围](../docs/EVENT_SCOPES.zh-CN.md)。

## 共享规则示例

`SharedRulesExample.declare(context, damagePercent)` 可在入口显式调用一次；玩法读取 `multiplier(handle, world)`，配置重载后用 `changeNextCampaign` 更新候选值。默认入口未启用此示例，不改变伤害。采用前阅读 [共享规则](../docs/SHARED_RULES.zh-CN.md)，尤其是缺少规则的旧存档不能自动接纳。
