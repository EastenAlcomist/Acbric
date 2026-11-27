# 构建与测试指南

[English](BUILDING.md) · 适用于当前 `0.3.8` 源码

## 速查

| 操作 | Windows cmd | PowerShell | Linux / macOS / Git Bash |
| --- | --- | --- | --- |
| 只编译 | `build` | `.\build` | `./build.sh` |
| 编译并运行全部测试 | `build full` | `.\build full` | `./build.sh full` |
| 清理后编译 | `build clean` | `.\build clean` | `./build.sh clean` |
| 透传 Gradle 任务 | `build <task>` | `.\build <task>` | `./build.sh <task>` |
| 运行全部测试 | `test all` | `.\test all` | `./test.sh all` |
| 运行指定套件 | `test event` | `.\test event` | `./test.sh event` |
| 列出套件 | `test list` | `.\test list` | `./test.sh list` |
| 启动旧布局游戏 | `gradlew startAirships` | — | `./gradlew startAirships` |

`build` 和 `test` 只是查找 JDK 并转发给 Gradle。构建逻辑放在 `build.gradle`，不要重复加入批处理或 shell 脚本。

## 环境准备

### JDK

项目要求 **JDK 21 或更高版本**，源码与目标字节码固定为 Java 21。`build.cmd` / `build.sh` 会依次检查 `JAVA_HOME`、`PATH` 和常见安装目录，并接受主版本不低于 21 的 JDK。

Git Bash、MSYS 和 Cygwin 中的 Windows 形式 `JAVA_HOME` 会由 `build.sh` 转换。若未找到可用 JDK，可显式设置：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21'
```

```sh
export JAVA_HOME=/path/to/jdk-21
```

### `libs/`

游戏文件不随仓库分发。编译需要游戏 A/B 归档、游戏自带库和 Fabric Loader。以当前 Windows 游戏布局为例：

```powershell
$gameInstall = 'C:/Games/Airships Conquer the Skies'
$framework = 'C:/Acbric'
New-Item -ItemType Directory -Path ./libs -Force | Out-Null
Copy-Item -LiteralPath "$gameInstall/asplit-A.zip", "$gameInstall/asplit-B.zip" -Destination ./libs
Copy-Item -Path "$gameInstall/lib/*.jar" -Destination ./libs
Copy-Item -LiteralPath "$framework/loader-libs/fabric-loader-0.19.3.jar" -Destination ./libs
```

至少需要：

| 文件 | 用途 |
| --- | --- |
| `libs/asplit-A.zip` | 游戏内核类 |
| `libs/asplit-B.zip` | 游戏内核类 |
| `libs/fabric-loader-0.19.3.jar` | 编译依赖 |
| `libs/*.jar` | 游戏自带依赖 |

其他安装布局可按 `Airships.json` 的 classPath 查找对应归档。当前编译基线为游戏 **1.2.15.3**，运行期也支持 1.2.15.2。更换基线后先执行 `build clean`，再运行 `test all`。

核对基线：

```powershell
javap -p -constants -classpath libs com.zarkonnen.airships.AGame | Select-String VERSION
```

测试还需要 `libs/FloatIO.jar`，用于加载原版 JSON 小数格式化；只编译时不需要。

### `game/`

`game/` 只用于旧布局 `startAirships`，应包含 `Airships.json`、`data/`、`lib/native` 等文件。编译和无界面回归不需要它。

日常开发优先构建外部发行包，再在 Setup 中选择已有游戏目录，避免把游戏复制进仓库：

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships Conquer the Skies"
```

## 编译

```sh
./build.sh                     # gradle assemble
./build.sh full                # gradle build；编译并运行全部测试
./build.sh clean               # gradle clean assemble
./build.sh installApiMod       # 其余参数透传
./build.sh --info assemble     # 支持 Gradle 选项
```

主要产物：

| 文件 | 内容 |
| --- | --- |
| `build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar` | API 与游戏适配层 |
| `build/libs/Acbric-1.0-SNAPSHOT.jar` | 启动垫片 `AirshipsGameProvider` |

`gradle.properties` 启用了配置缓存、并行构建、任务缓存和守护进程。发行与维护任务会按需禁用不兼容的配置缓存。若本地出现缓存异常，可单次执行：

```sh
./build.sh --no-configuration-cache
```

## 测试

```sh
./test.sh all
./test.sh event
./test.sh data rename
./test.sh cp
./test.sh list
```

套件选择支持完整名、别名、唯一前缀和唯一子串；未知或歧义名称会直接报错。

| 套件 | 覆盖 |
| --- | --- |
| `data` | `DATA_LOADED` 保留游戏真实结果 |
| `bundle` | 内嵌原版资源更新、冲突、备份和恢复 |
| `classpath` | 启动类路径过滤与去重 |
| `external` | 外部安装、实例、Steam、维护锁、缓存与同步 |
| `event` | 事件注册、一次性执行和句柄身份 |
| `rename` | 重命名面板新旧事件顺序与取消 |
| `mods` | Fabric MOD 安装、Java MOD 启停、多语言描述和原版 MOD 内嵌 ACMOD |
| `campaign` | 战役数据与生命周期 |
| `config` | MOD 配置和设置契约 |
| `scopes` | 订阅范围与运行期诊断 |
| `manifest` | 代码清单导出与离线比较 |
| `handshake` | 代码握手和大厅准备门禁 |
| `rules` | 共享规则与旧档转换 |
| `identity` | 构建身份和启动诊断 |
| `ui` | 公共 UI、命令面板和缩放 |
| `devtools` | 控制台、命令和诊断缓冲 |

回归使用 `build/regression-sandbox/` 下的隔离目录，不读取玩家存档。`check` 和显式 `gradlew build` 始终运行全部套件；`test <suite>` 只影响本地快速测试。

新增套件时：

1. 在 `src/regressionTest/java/net/fabricacs/regression/` 添加返回检查数的 `run` 方法。
2. 在 `FrameworkRegression.SUITES` 注册名称、说明和入口。
3. 运行 `test list` 和 `test <name>`。

不要创建平行测试入口。

## 跨平台约定

- `.cmd` 只使用 ASCII 和 CRLF；`.sh` 使用 LF。
- POSIX 入口使用 `build.sh` / `test.sh`，避免与 `build/` 目录冲突。
- 脚本从自身路径定位仓库根，路径必须加引号。
- `cmd.exe` 会把 `-Pkey=value` 在 `=` 处拆开。测试脚本改用 `ACBRIC_SUITES` 环境变量，因此各平台调用一致。

## 外部发行

```powershell
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships Conquer the Skies"
.\gradlew.bat externalDistZip -PexternalGameDir="C:/Games/Airships Conquer the Skies" -PbundleRuntime=false
```

- `externalDistZip` 生成仅含框架、API、模板、文档和启动器的干净包，并**默认**使用 JDK 21 的 `jlink` 生成 `runtime/`。
- `-PbundleRuntime=false` 生成不带运行时的精简包；启用默认运行时打包时构建 JDK 必须是 Java 21，否则任务明确失败。
- `externalGameDir` 只用于发行扫描；未提供时使用 `game/`，没有本地 `game/` 时必须显式传入。
- 输出：`build/external-dist/Acbric-external-<version>.zip`
- 扫描报告：`build/external-dist/verification.json`

外部包不包含游戏本体。旧 `distZip` 会带入本地 `game/` 与 `libs/`，只用于旧布局研究。Windows 玩家 EXE 由 `buildPlayerLauncher` 使用 `csc.exe` 构建，普通 Java 编译和测试不会调用它。

## 排错

| 现象 | 处理 |
| --- | --- |
| `No compatible JDK found (requires 21 or newer).` | 安装 JDK 21+，或设置有效的 `JAVA_HOME`。 |
| `Missing game files required for compilation` | 按上方补齐 `libs/`。 |
| `Missing game files required to launch Airships` | 仅影响 `startAirships`；补齐 `game/` 或改用外部发行。 |
| Gradle Wrapper 锁超时 | 等待其他下载/构建结束，不要删除 `.lck`。 |
| Linux/macOS 上 `Permission denied` | 执行 `chmod +x gradlew build.sh test.sh`。 |
| `bad interpreter` | 检查 `.sh` 是否为 LF。 |
| 首次构建下载依赖很慢 | 需要访问 Maven Central 和 Fabric Maven。 |
| `Task 'event' not found` | 使用 `test event`，或给 Gradle 参数中的 `=` 段加引号。 |

修改 Mixin 或 `fabric.mod.json` 后，应核对真实字节码目标和加载日志。录制绘制器不能替代输入法、窗口焦点和 OpenGL 实机检查。

文档改动后运行：

```powershell
python tools/verify_docs.py
```
