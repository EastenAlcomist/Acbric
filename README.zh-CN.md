# Acbric — 飞艇 Fabric MOD 框架

[English](README.md) | **中文**

面向《Airships: Conquer the Skies》的轻量级 Fabric 风格 MOD 加载框架。

框架把 Fabric Loader 的 `KnotClient` 启动器嫁接到游戏上，让 MOD 能用 Java 代码 + mixin
扩展游戏逻辑，同时保留并整合游戏原生的 JSON 数据 MOD 系统。

## 当前开发版与文档

- [运行期诊断与订阅范围](EVENT_SCOPES.zh-CN.md)：MOD 归属、集中注销和错误报告。

- [战役大厅代码检查](LOBBY_HANDSHAKE.zh-CN.md)：自动检查、准备失效及开局复查。

- [内部代码握手](CODE_HANDSHAKE.zh-CN.md)：有限重试、会话隔离、超时与明确结果。

- [本地代码清单与离线比较](CODE_MANIFEST.zh-CN.md)：导出实际加载的代码身份并定位差异。

当前 API 构建为 **0.3.3-dev.9**。本轮接入战役大厅自动代码检查及准备/开局门禁，双方需使用一致的框架、游戏和 MOD 代码；这不是自动状态同步，也不支持混用旧框架联机。保留运行期诊断与受管理订阅范围，继续提供配置、战役生命周期和存档/恢复集成，保留已有公开成员和事件语义。

- [完整 API 开发手册](API.zh-CN.md) / [English](API.md)：入口、上下文、目录、事件、取消与资源管理。
- [本次改动记录](CHANGELOG.zh-CN.md)：12 项修复、兼容变化、验证结果和当前限制。
- [UI 事件契约](EVENTS.md) / [资源更新与迁移](BUNDLED_RESOURCES.md)。
- [MOD 模板说明](acbric-mod-template/README.zh-CN.md)：创建独立功能 MOD。
- [游戏身份与启动诊断](DIAGNOSTICS.zh-CN.md)：真实版本、构建指纹和逐入口初始化结果。
- [战役生命周期](CAMPAIGN_LIFECYCLE.zh-CN.md)：创建、加载、恢复和退出时机。
- [战役数据 API](CAMPAIGN_DATA.zh-CN.md)：持久化、格式迁移及联机边界。

标准构建有 477 项无界面回归，含 44 项大厅、77 项握手、45 项清单、81 项订阅范围、27 项运行期诊断及原 62 项配置检查。用户对 dev.4 反馈暂未发现问题，未提供完整逐项清单；两套游戏的 10 组原版 Server/双 Fabric Client 隔离实验通过 104 项上层检查；dev.9 完整 GUI、成功生成世界及跨机器/官方服务器验收仍待人工完成。

- [配置 API](CONFIG.zh-CN.md)：本地偏好、显式重载和战役规则固化。

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
当前开发 API 构建为 `0.3.3-dev.9`（尚未发布）；模板最低依赖为 `>=0.3.3-dev.6`，包含需显式调用的战役数据示例。

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
| API 层 | `src/apiMod` | `acbric_api` —— 事件系统、入口桥、原生 MOD 界面集成、战役数据、大厅检查及 19 个 hook mixin。 |

**启动主链路**：`KnotClient.main` → ServiceLoader 发现 `AirshipsGameProvider` →
拼装类路径 → Fabric 跑 `preLaunch` 入口 → `AcbricApiPreLaunch` 遍历所有 `acbric`
入口并调用 `AcbricInitializer.onInitializeAcbric`（逐个 try/catch）→ 游戏启动 →
API 的 mixin 触发事件 → MOD 监听器执行。

## 5. 许可与声明

本仓库的框架代码以 **MIT 许可**发布，见 [`LICENSE`](LICENSE)。

游戏《Airships: Conquer the Skies》及其全部资源归其各自所有者所有，**不在**本仓库内。

战役创建、加载、恢复和退出事件从 dev.4 提供，见[生命周期手册](CAMPAIGN_LIFECYCLE.zh-CN.md)。独立存档示例作为单独 MOD 提供，不内置到框架。
