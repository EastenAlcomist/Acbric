# AGENTS.md — Acbric

## 当前范围

- 当前源码/API：`0.3.8`，尚未发布稳定版。版本以 `src/apiMod/resources/fabric.mod.json` 为准。
- 2026-11-27：原版 MOD 的 `AcbricMod/*.jar` 内嵌 ACMOD 发现、行后缀、启用复制与停用项移除已并入 API 默认实现；本地与 Steam 原版 MOD 都扫描。独立 `acbric_embedded_mods` 已由默认 API 取代，不应同时安装。
- 编译基线：游戏 1.2.15.3；运行期兼容 1.2.15.2。其他游戏版本不保证可用。
- 开始工作先检查 Git 分支和本地修改，保留用户已有改动。未经用户要求不提交、不推送、不删除构建产物。
- 当前框架由启动层、API 层和独立 MOD 模板组成；功能 MOD 不属于框架本体。

## 构建与测试

```powershell
.\build              # 只编译
.\build full         # 编译并运行全部测试
.\build clean        # 清理后编译
.\test all           # 全部测试
.\test list          # 列出套件
.\test ui            # 指定套件
```

Linux/macOS/Git Bash 使用 `./build.sh`、`./test.sh`。项目要求 JDK 21 或更高版本，Java 源和目标均为 21。

编译输入：

- `libs/asplit-A.zip`、`libs/asplit-B.zip`
- `libs/fabric-loader-0.19.3.jar`
- 游戏自带 `lib/*.jar`
- 测试还需 `libs/FloatIO.jar`
- 旧布局 `startAirships` 还需 `game/Airships.json` 及游戏数据

构建和输入说明以[双语构建指南](docs/BUILDING.zh-CN.md)为准。不要在 `build.cmd`、`build.sh`、`test.cmd`、`test.sh` 中加入构建逻辑；脚本只查找 JDK 并转发 Gradle。

修改后先运行对应套件，收尾运行 `test all`。Mixin 或 `fabric.mod.json` 变更还必须核对真实字节码和目标加载日志。录制绘制器不能代替输入法、窗口焦点或 OpenGL 实机检查。

## 架构

| 层 | 位置 | 职责 |
| --- | --- | --- |
| 启动层 | `src/main` | `AirshipsGameProvider` 解析游戏输入、组装类路径并接入 Fabric |
| 共用层 | `src/shared` | 启动与 API 共用的协议和安装逻辑 |
| API 层 | `src/apiMod` | `acbric_api`、事件、原生/战斗 UI、配置、战役数据、诊断与 Mixin |
| 模板 | `acbric-mod-template` | 独立 Gradle MOD 项目 |

主链路：

1. Fabric `KnotClient` 通过 ServiceLoader 发现 `AirshipsGameProvider`。
2. Provider 准备游戏类路径和外部实例参数。
3. Fabric `preLaunch` 调用 `AcbricApiPreLaunch`。
4. `AcbricApiPreLaunch` 逐个执行 `acbric` 入口，异常按入口隔离记录。
5. 游戏启动后由 Mixin 触发事件和 UI/输入适配。

公开扩展面是 `net.fabricacs.api` 及其 `event`、`util`、`config`、`save`、`rules`、`ui`、`combat`、`command` 包。`impl`、`mixin`、`UiRuntime` 和具体适配器属于内部实现，即使声明为 public 也不构成稳定契约。

## MOD 约定

独立 MOD 使用 `src/main/java` 与 `src/main/resources`。`fabric.mod.json` 至少声明：

```json
{
  "entrypoints": {
    "acbric": ["net.fabricacs.example.ExampleMod"]
  },
  "depends": {
    "java": ">=21",
    "acbric_api": ">=0.3.8"
  }
}
```

入口实现 `AcbricInitializer`。兼容旧二进制时同时实现无参方法；使用上下文时在 `onInitializeAcbric(AcbricModContext)` 注册。

Java MOD 列表描述可使用：

```json
"custom": {
  "acbric": {
    "description": {
      "en": "English",
      "chi": "中文",
      "de": "Deutsch",
      "fr": "Français",
      "jpn": "日本語",
      "kor": "한국어",
      "ru": "Русский"
    }
  }
}
```

按游戏 Locale 选择，当前语言缺失时回退英文，再回退顶层 `description`。`zh`/`zho` 兼容 `chi`，`ja`/`ko` 兼容 `jpn`/`kor`。`gradle.properties` 中文值必须使用 `\uXXXX`。

纯资源 MOD 可以提供 metadata 与 `acbric_vanilla/`，无需 Java 入口。`acbric_vanilla/` 解包到原生 MOD 根下的 `<Fabric MOD ID>/`，不是 Java `.jar` 目录。

## 路径与数据环境

- Java MOD：`AirshipsPaths.modsDir()` / `fabric.modsFolder`，即 `Acbric.exe` 同层 `mods/`。
- 原生 MOD：`LocalModPaths.nativeMods()`；共享模式为原版用户数据 `mods/`，隔离模式为实例。
- 原生存档：由 `AGame.getGameDirectory()` 指向当前数据环境。
- MOD 配置：Fabric `config/<modId>/<name>.json`。
- MOD 数据目录：`context.dataDir()`，不是战役存档目录。

共享模式由 `acbric.external.dataDir` 指向原版数据目录；原生 MOD 扫描使用 `acbric.external.mods`。两个模式都必须保持 Java 与原生 MOD 根分离。不要用 `acbric.external.mods` 覆盖 `fabric.modsFolder`。

配置和战役数据写入不自动广播。联机共享状态必须有确定性指令、模拟或既有规则分发契约。

## 关键约束

- 保持既有公开 API、事件顺序、取消语义和二进制兼容；破坏性调整先取得明确同意。
- 新 UI、玩家说明和 API 文档同时维护中文与英文；游戏其他语言按 `AcbricLanguage` 规则处理。
- 配置、资源、存档迁移必须显式；损坏文件、未来版本和未知数据不得静默重置。
- 资源更新依据归属哈希，保留用户修改与未归属文件。事务日志只覆盖进程中断，不承诺硬件故障。
- Java MOD 启停重启后生效，不热卸载；停用不能保证绕过损坏 JAR 或损坏 metadata。
- 代码清单一致不是身份认证、反作弊、房间共识或完整玩法同步。
- 旧布局数据不自动导入。原版目录不会被框架删除。
- 游戏代码、资源、`libs/`、`game/`、用户数据、构建包和第三方功能 MOD 不加入 Git。

## Mixin 约束

项目使用 `sponge-mixin 0.17.3+mixin.0.8.7`，没有 mappings，Mixin 使用 `remap = false`：

- 没有 `@Local` 注解，不能直接取得方法局部变量。
- 同一调用点只允许一个 `@Redirect`。多个 MOD 重定向同一位置时，后注册者可能被跳过。
- `@Redirect` handler 的尾参可接收目标方法参数，但不能读取局部变量。
- `@ModifyArg` / `@ModifyVariable` 可能在目标节点被 `@Redirect` 后报错；`@Inject` 可在同一节点共存。
- MixinExtras 运行时可用，但不在默认编译类路径；需要 `@WrapOperation` 时先接入依赖。
- 目标 owner 按接收者静态类型写入。不要凭声明类推断，使用 `javap -p -c` 核对真实字节码。
- 修改 Mixin 后检查目标计数、注入结果和真实 Knot 日志。

已知高风险目标包括 `ModuleType`、原生 MOD 界面、战斗面板和原生大厅消息入口。新增钩子前检查现有调用点。

## 主要文件

- `src/main/.../AirshipsGameProvider.java`：启动垫片。
- `src/main/resources/META-INF/services/net.fabricmc.loader.impl.game.GameProvider`：Provider 注册。
- `src/apiMod/.../api/`：公开 API。
- `src/apiMod/.../api/impl/`：入口、安装、MOD 列表、资源与大厅适配。
- `src/apiMod/.../api/mixin/`：游戏适配 Mixin。
- `src/regressionTest/`：隔离回归。
- `src/dist/`：玩家入口和维护脚本。
- `tools/`：发行扫描与运行时测试工具。

## 发布

- 对外使用 `externalDistZip`；干净发行不得包含游戏本体、玩家数据或第三方功能 MOD。
- `externalDistZip` 默认使用 **JDK 21** 的 `jlink` 携带 `runtime/`；非 Java 21 构建 JDK 会明确失败。`-PbundleRuntime=false` 生成不带运行时的精简包。
- 旧 `distZip` 会带入本地游戏内容，仅供旧布局开发。
- 修改文档后运行 `python tools/verify_docs.py`。
- 没有用户要求时不提交、不推送、不清理证据。
