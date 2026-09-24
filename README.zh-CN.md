# Acbric — 飞艇 Fabric MOD 框架

[English](README.md) | **中文**

面向《Airships: Conquer the Skies》的轻量级 Fabric 风格 MOD 加载框架。

框架把 Fabric Loader 的 `KnotClient` 启动器嫁接到游戏上，让 MOD 能用 Java 代码 + mixin
扩展游戏逻辑，同时保留并整合游戏原生的 JSON 数据 MOD 系统。

## 当前开发版与文档

当前 API 为 **0.3.3-dev.1**。本次修复保留旧公共接口，同时新增准确命名的重命名面板事件。

- [完整 API 开发手册](API.zh-CN.md)：入口、上下文、目录、事件、取消与资源管理。
- [本次改动记录](CHANGELOG.zh-CN.md)：12 项修复、兼容变化、验证结果和当前限制。
- [UI 事件契约](EVENTS.md) / [资源更新与迁移](BUNDLED_RESOURCES.md)。
- [MOD 模板说明](acbric-mod-template/README.zh-CN.md)：创建独立功能 MOD。

标准构建有 80 项无界面回归。独立运行包经本地人工测试反馈运行正常、表现与原包基本一致；没有完整场景/MOD 清单，存档、联机及所有第三方 MOD 仍需分别验证。

---

## ⚠️ 本仓库不含任何游戏内容

本仓库只包含**框架本体**。以下三项不含：

| 不含 | 原因 |
|---|---|
| `libs/` | 游戏的编译后 class（`asplit-A.zip` / `asplit-B.zip`）以及随游戏分发的库 jar。转载涉及版权。 |
| `game/` | 游戏静态数据（`data/`、`ships/`、`images/` 等），约 1.3 GB。 |
| 功能 MOD | 不属于框架本体。 |

在补齐前两项之前，框架**无法编译，也无法运行**。详见下文。

---

## 1. 准备游戏文件

请从你合法拥有的 Airships 安装目录，按相同相对路径复制到本项目。

### `libs/` —— 编译与运行依赖

```
libs/
├── asplit-A.zip                # 游戏 class（编译 + 运行）
├── asplit-B.zip                # 游戏 class（编译 + 运行）
├── fabric-loader-0.19.3.jar    # Fabric Loader 0.19.3（来自 Fabric，不来自游戏）
├── <游戏自带的库 jar>
└── native/                     # 本地库
```

`libs/` 最终需包含游戏自身启动所用的 jar 与 zip —— 通常有 `CatEngine.jar`、
`CatSlick.jar`、`slick.jar`、`lwjgl.jar`、`lwjgl_util.jar`、`ibxm.jar`、`jinput.jar`、
`jogg-*.jar`、`jorbis-*.jar`、`commons-*.jar`、`joda-time-*.jar`、`steamworks4j-*.jar`
和 `FloatIO.jar` —— 以及游戏的 `asplit-*.zip` class 包。

> `fabric-loader-0.19.3.jar` 来自 Fabric 而非游戏，请自行下载后放入 `libs/`；
> 构建脚本目前以本地文件方式引用它。

### `game/` —— 游戏自己的目录

请复制包含 `Airships.json` 的那个游戏目录：

```
game/
├── Airships.json               # 必需 —— 启动器解析它
├── launch_settings.json        # 可选，建议保留
├── data/                       # 必需 —— 游戏数据
├── lib/、native 库 等
└── mods/                       # MOD 安装目录
```

`game/Airships.json` 是 `AirshipsGameProvider` 用来发现 `mainClass` 与 `classPath` 的文件；
缺少它，启动器无法定位游戏。

---

## 2. 环境要求

- **JDK 21** —— 使用 `JAVA_HOME` 或 Gradle 参数 `-Dorg.gradle.java.home=<JDK 路径>` 指定；仓库不再固定某台机器的安装路径。
- Gradle 8.13（通过仓库自带 wrapper 使用）。

## 3. 构建与运行

快速入口（会自己找 JDK 21，再转发给 Gradle）：

```powershell
build                 # Windows cmd；PowerShell 里写 .\build
build full            # 编译 + 全部回归检查
./build.sh            # Linux / macOS / Git Bash
```

前置条件、实测依据、跨平台注意事项与排错见 **[BUILDING.zh-CN.md](BUILDING.zh-CN.md)**
（[English](BUILDING.md)）。

底层仍然是标准 Gradle：

```powershell
# 编译启动层与 API，并运行无界面回归检查
.\gradlew.bat build --console=plain

# 构建框架并启动游戏
.\gradlew.bat startAirships --console=plain

# 只构建/安装 API jar
.\gradlew.bat installApiMod --console=plain

# 打包分发包（含 jlink 内置 JRE）
.\gradlew.bat distZip --console=plain
```

`build` 已包含 `apiModJar` 和 `regressionTest`。回归检查覆盖数据加载结果通知、内嵌资源解包和类路径边界，
使用 `build/regression-sandbox/` 下的独立数据，需要本地编译依赖，不启动 GUI、不使用真实存档。
操作系统不允许创建符号链接时，对应检查会明确报告跳过。这些检查不能替代对局、存档与联机验收。
检查还覆盖受管理资源升级／恢复、事件订阅语义及 Fabric 元数据校验。

分发入口为 `run.bat`；`loader-libs/` 保存启动垫片、Fabric Loader、Mixin 和 ASM，`libs/` 保存游戏依赖。
两者与 `game/`、`jre/` 保持相邻。Provider 也会按归档内容排除旧式混合 `libs/` 中的启动基础设施，避免重复类身份。
分发包包含框架 LICENSE。生成 ZIP 成功不代表已补齐完整游戏资源。

行为变更和仍待处理的限制见 [未发布变更记录](CHANGELOG.md)。
内嵌原版资源的哈希归属、冲突保留、备份和旧目录手动迁移见 [资源管理说明](BUNDLED_RESOURCES.md)。

新增 UI 事件、触发顺序、取消规则与旧接口迁移见 [事件契约](EVENTS.md)。
当前开发 API 为 `0.3.3-dev.1`（尚未发布），新模板已声明对应最低版本。

## 4. 项目结构

```
Acbric/
├── src/
│   ├── main/            # 启动垫片：AirshipsGameProvider + GameProvider 服务注册
│   ├── apiMod/          # 运行时 API（acbric_api）：事件系统、入口桥、
│                        # 原生 MOD 界面集成、hook mixin
│   └── regressionTest/  # 无界面回归及真实面板探针入口
├── acbric-mod-template/ # 独立 MOD 模板项目
├── gradle/              # Gradle wrapper
└── build.gradle
```

### 架构

| 层 | 位置 | 职责 |
|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 解析 `game/Airships.json`、拼装类路径、反射调用 `Main.main`。 |
| API 层 | `src/apiMod` | `acbric_api` —— 事件系统、入口桥、原生 MOD 界面集成、13 个 hook mixin。 |

**启动主链路**：`KnotClient.main` → ServiceLoader 发现 `AirshipsGameProvider` →
拼装类路径 → Fabric 跑 `preLaunch` 入口 → `AcbricApiPreLaunch` 遍历所有 `acbric`
入口并调用 `AcbricInitializer.onInitializeAcbric`（逐个 try/catch）→ 游戏启动 →
API 的 mixin 触发事件 → MOD 监听器执行。

## 5. 许可与声明

本仓库的框架代码以 **MIT 许可**发布，见 [`LICENSE`](LICENSE)。

游戏《Airships: Conquer the Skies》及其全部资源归其各自所有者所有，**不在**本仓库内。
