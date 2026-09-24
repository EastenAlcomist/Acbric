# Acbric — 飞艇 Fabric MOD 框架

[English](README.md) | **中文**

面向《Airships: Conquer the Skies》的轻量级 Fabric 风格 MOD 加载框架。

框架把 Fabric Loader 的 `KnotClient` 启动器嫁接到游戏上，让 MOD 能用 Java 代码 + mixin
扩展游戏逻辑，同时保留并整合游戏原生的 JSON 数据 MOD 系统。

---

## ⚠️ 本仓库不含任何游戏内容

本仓库只包含**框架本体**。以下三项刻意不含：

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

请复制包含 `Airships.json` 的那个游戏目录（启动垫片要解析它）：

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

- **JDK 21** —— `gradle.properties` 已 pin `C:/Program Files/Java/jdk-21`，请按你的机器修改。
- Gradle 8.13（通过仓库自带 wrapper 使用）。

## 3. 构建与运行

```powershell
# 构建框架并启动游戏
.\gradlew.bat startAirships --console=plain

# 只构建/安装 API jar
.\gradlew.bat installApiMod --console=plain

# 打包分发包（含 jlink 内置 JRE）
.\gradlew.bat distZip --console=plain
```

## 4. 项目结构

```
Acbric/
├── src/
│   ├── main/            # 启动垫片：AirshipsGameProvider + GameProvider 服务注册
│   └── apiMod/          # 运行时 API（acbric_api）：事件系统、入口桥、
│                        # 原生 MOD 界面集成、hook mixin
├── acbric-mod-template/ # 独立 MOD 模板项目
├── gradle/              # Gradle wrapper
└── build.gradle
```

### 架构

| 层 | 位置 | 职责 |
|---|---|---|
| 启动层 | `src/main` | `AirshipsGameProvider` 把游戏塞进 Fabric：解析 `game/Airships.json`、拼装类路径、反射调用 `Main.main`。零依赖，不碰 API。 |
| API 层 | `src/apiMod` | `acbric_api` —— 事件系统、入口桥、原生 MOD 界面集成、13 个 hook mixin。 |

**启动主链路**：`KnotClient.main` → ServiceLoader 发现 `AirshipsGameProvider` →
拼装类路径 → Fabric 跑 `preLaunch` 入口 → `AcbricApiPreLaunch` 遍历所有 `acbric`
入口并调用 `AcbricInitializer.onInitializeAcbric`（逐个 try/catch）→ 游戏启动 →
API 的 mixin 触发事件 → MOD 监听器执行。

## 5. 许可与声明

本仓库的框架代码以 **MIT 许可**发布，见 [`LICENSE`](LICENSE)。

游戏《Airships: Conquer the Skies》及其全部资源归其各自所有者所有，**不在**本仓库内。
