# 构建与测试指南

> **English**: [BUILDING.md](BUILDING.md)
> 面向加入本项目的**人类与 agent**。读完这一篇，你应该能在任何一台机器上编译、测试并知道出错时该看哪里。

---

## 0. 速查

| 你想做什么 | Windows (cmd) | Windows (PowerShell) | Linux / macOS / Git Bash |
|---|---|---|---|
| 只编译 | `build` | `.\build` | `./build.sh` |
| 编译 + 全部测试 | `build full` | `.\build full` | `./build.sh full` |
| 清理后编译 | `build clean` | `.\build clean` | `./build.sh clean` |
| 透传给 Gradle | `build installApiMod` | `.\build installApiMod` | `./build.sh installApiMod` |
| **跑全部测试** | `test all` | `.\test all` | `./test.sh all` |
| **只跑某个套件** | `test event` | `.\test event` | `./test.sh event` |
| 列出所有套件 | `test list` | `.\test list` | `./test.sh list` |
| 启动游戏 | `gradlew startAirships` | — | `./gradlew startAirships` |

> PowerShell **不会**从当前目录执行 `build.cmd`，必须写 `.\build`。cmd.exe 可以直接敲 `build`。
> 由于大小写不敏感的文件系统上 `build` 会和 Gradle 的 `build/` 目录撞车，POSIX 端刻意命名为 `build.sh`。

---

## 1. 为什么需要"快速编译"

这不是为了好看，是实测出来的：

| 操作（Windows、暖守护进程） | 耗时 | 输出行数 |
|---|---|---|
| `gradlew --version`（纯启动地板） | 1975 ms | 15 |
| `gradlew assemble`（改一个源文件后） | 2775 ms | **23** |
| `gradlew build`（同样改动） | 3822 ms | **167** |
| `gradlew assemble --no-daemon` | 12421 ms | — |
| `gradlew assemble` 但缺 `libs/` | 失败 | **825 行** |

四条理由：

1. **真正的反馈环是「改代码 → 启动游戏（约 72 s）→ 读日志」。** 编译前的每一步都是纯开销，
   能砍就砍。这个项目的正确性只有在运行期才暴露（mixin 是加载期字节码注入），所以迭代次数多。
2. **`build` 原本没有"只编译"的语义。** 上游把 `check` 接到了 `regressionTest` 上，
   所以 `gradlew build` = 编译 + 80 项断言。想只编译必须记住 `assemble` 这个 Gradle 内部名。
   现在 `build` 就是"编译"，`build full` 才是"编译 + 测试"。
3. **145 行测试噪声会把编译错误埋掉。** 回归会打印大量 `[Acbric] Bundle conflict …`，
   而编译错误只有几行。默认只编译，错误一眼可见。
4. **新机器/新协作者的上手成本。** 缺 `libs/` 时 Gradle 会吐 825 行，
   完全看不出该怎么办；JDK 21 在 F11 之后成了硬门槛但没人帮忙找；
   Linux/macOS 协作者此前没有任何顺手的入口。

---

## 2. 一次性准备

### 2.1 JDK 21（必需）

`build.gradle` 里 `sourceCompatibility = JavaVersion.VERSION_21`。
仓库**故意不写死 JDK 路径**（`gradle.properties` 里没有 `org.gradle.java.home`），
所以官方要求你自己提供 JDK 21。

`build` / `build.sh` **会自己找**，顺序是：

1. `JAVA_HOME`（若指向的 JDK 主版本 < 21 则跳过）
2. `PATH` 上的 `java`（同样校验主版本）
3. 常见安装位置

| 平台 | 扫描的位置 |
|---|---|
| Windows | `%ProgramFiles%\Eclipse Adoptium\jdk-2*`、`%ProgramFiles%\Java\jdk-2*`、`Microsoft\jdk-2*`、`Amazon Corretto\jdk2*`、`Zulu\zulu-2*`、`BellSoft\LibericaJDK-2*`、`%USERPROFILE%\.jdks\*` 等 |
| Linux | `/usr/lib/jvm/*`、`/usr/java/*`、`/opt/java/*`、`/opt/jdk*`、`$HOME/.sdkman/candidates/java/*` |
| macOS | `/Library/Java/JavaVirtualMachines/*/Contents/Home`、`/opt/homebrew/opt/openjdk*`、`/usr/local/opt/openjdk*` |

一个都找不到时，脚本会打印一段可以直接照做的中文/英文说明并退出，而不是让你去猜 Gradle 的报错。

> **PATH 上只有 Java 8 也没关系**：脚本会跳过它继续找，只有真的一个 JDK 21 都没有才失败。
> 这一点已实测：把 `JAVA_HOME` 指向 Java 8，脚本照样在磁盘上找到了 Adoptium JDK 21 并编译成功。

### 2.2 `libs/`（编译必需，约 13 MB）

**不随仓库分发**（版权原因）。从你自己的 Airships 安装目录复制：

```powershell
# Windows
Copy-Item '<你的 Airships>\libs' .\libs -Recurse -Force
```

```sh
# Linux / macOS
cp -r "<your Airships>/libs" ./libs
```

至少需要这三个文件，缺任何一个都会在编译前被 `verifyFrameworkInputs` 拦下来：

| 文件 | 用途 |
|---|---|
| `libs/asplit-A.zip` | 游戏内核 class（编译 + 运行） |
| `libs/asplit-B.zip` | 游戏内核 class（编译 + 运行） |
| `libs/fabric-loader-0.19.3.jar` | `compileOnly` 依赖 |

### 2.3 `game/`（只有"启动游戏"才需要）

约 1.3 GB：`Airships.json`、`data/`、`lib/native` 等。
**编译和无界面回归都不需要它**；只有 `gradlew startAirships` 需要，缺了会被 `verifyGameInputs` 拦下并给出提示。

不想复制 1.3 GB 的话，可以只让 `mods/` 独立、其余用 junction/符号链接共享一份现成的游戏目录 —— 具体做法见 `tools/reports/06-build-and-test.md`（本机实测路线）。

---

## 3. 编译

```sh
./build.sh              # 只编译：gradle assemble
./build.sh full         # 编译 + 全部测试：gradle build
./build.sh clean        # 清理后编译：gradle clean assemble
./build.sh installApiMod        # 其余参数原样透传给 gradlew
./build.sh --info assemble      # 也可以带 Gradle 选项
```

产物：

| 文件 | 说明 |
|---|---|
| `build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar` | API 层（也就是 `game/mods/acbric-api.jar` 的来源） |
| `build/libs/Acbric-1.0-SNAPSHOT.jar` | 启动垫片 `AirshipsGameProvider` |

> 脚本只做两件事：**找 JDK 21**、**把命令交给 `gradlew`**。
> 所有构建逻辑都在 `build.gradle` 里，所以 IDE、CI 和你手敲 `gradlew` 的行为完全一致。
> 脚本不是构建系统，别往里塞逻辑。

### 为什么快

`gradle.properties` 里开了三项（每一项都有实测依据）：

| 配置 | 效果 |
|---|---|
| `org.gradle.configuration-cache=true` | 复用配置阶段，`assemble` 2.8s → **1.9s（约 −33%）** |
| `org.gradle.parallel=true` | main / apiMod / regressionTest 三个 source set 并行调度 |
| `org.gradle.caching=true` | 任务输出按内容复用，换分支、重跑更快 |
| `org.gradle.daemon=true` | **必须保留**：`--no-daemon` 会让 `assemble` 变成 12.4s |

配置缓存已逐一验证与 `build` / `regressionTest` / `apiModJar` / `installApiMod` / `startAirships` / `distDir` / `syncModTemplateLibs` 全部兼容，无告警。
万一某个环境上出现意外，单次关掉即可：

```sh
./build.sh --no-configuration-cache
```

---

## 4. 测试

```sh
./test.sh all          # 全部套件（当前 80 项断言）
./test.sh event        # 只跑 event
./test.sh ev           # 唯一前缀也行
./test.sh data rename  # 多选，按给定顺序执行
./test.sh cp           # 别名（cp = classpath）
./test.sh list         # 列出套件与覆盖范围
```

### 套件

| 套件 | 覆盖 | 对应修复 |
|---|---|---|
| `data` | `DATA_LOADED` 原样传递游戏结果：不清理诊断、不把失败改成成功 | F01 |
| `bundle` | 内嵌原版资源归属存储：9 种路径穿越、更新、保留用户改动、冲突、备份与中断恢复 | F02 / F05 |
| `classpath` | 启动类路径按**归档内容**排除 Loader/Mixin/ASM/垫片，保留游戏库，且不重复 | F03 |
| `event` | 事件总线：`registerOnce` 只执行一次、句柄身份、重复注册 | F04 / F12 |
| `rename` | 重命名面板新旧事件的顺序与取消契约 | F06 |
| `mods` | Fabric MOD 安装校验：非法 schema / id / 缺字段一律拒绝 | F07 |

选择规则：精确名 → 别名 → **唯一**前缀 → 唯一子串。歧义或未知会直接报错并列出可选套件，
不会静默跑错东西。

### 沙箱

回归**不需要 `game/`**，也**不碰玩家存档**：它把 `user.home`、`APPDATA`、工作目录全部改到
`build/regression-sandbox/` 下，并在那里写一个指向沙箱的 `launch_settings.json`。
夹具目录是 `build/regression-sandbox/run-<随机数>/`，可以直接删。

### CI 语义没有变

`check` 仍然依赖 `regressionTest`，而不传选择参数时 `regressionTest` **跑全部套件**。
所以 `gradlew build`、`build full`、CI 的行为与以前完全一致 —— 选套件只影响本地手动运行。

```sh
./build.sh full                                        # = gradlew build = 编译 + 全部套件
./test.sh all                                          # 只跑全部套件
gradlew regressionTest -Pacbric.suites=event,rename    # IDE / CI 里的等价写法
```

> 启动脚本用**环境变量 `ACBRIC_SUITES`** 而不是 `-Pacbric.suites=` 传选择，
> 因为 cmd.exe 会把参数里的 `=` 当分隔符拆开；环境变量在 `.cmd` 与 `.sh` 两端行为一致。
> Gradle 侧两种写法都支持。

### 加一个新套件

1. 在 `src/regressionTest/java/net/fabricacs/regression/` 下按现有风格写一个类
   （`public static int run(...)`，内部用 `check(condition, message)` 断言并返回条数）。
2. 在 `FrameworkRegression` 的 `static { ... }` 块里
   `register("名字", "一句话说明", FrameworkRegression::suiteXxx)`。
3. 在 `suiteXxx` 里调用它，并像其它套件一样 `checks += ...` 累加。
4. 跑 `./test.sh list` 与 `./test.sh <新名字>` 验证。

**不要新建平行的测试入口**（第二个 main、第二个 Gradle 任务）——套件注册表是唯一入口，
这样 `test <名字>` 与 CI 才不会分叉。

---

## 5. 跨平台注意事项

这套入口是刻意按"换台电脑也不出错"设计的，几条硬约束：

1. **`.cmd` 只写 ASCII，且必须 CRLF。** cmd.exe 按控制台 OEM 代码页读批处理，
   UTF-8 中文注释在别的代码页机器上会被当成命令执行（本机实测踩到）。
   `.gitattributes` 里的 `*.cmd text eol=crlf` 保证换行符。
2. **`.sh` 必须 LF。** CRLF 的 shell 脚本在 Linux/macOS 上会报
   `bad interpreter: No such file or directory`。`.gitattributes` 里的 `*.sh text eol=lf` 保证。
3. **不使用无扩展名的 `build` / `test` 作为 POSIX 入口。**
   在大小写不敏感的文件系统（Windows、macOS 默认）上，它和 Gradle 的 `build/`
   输出目录冲突，会导致 checkout 异常，所以 POSIX 端是 `build.sh` / `test.sh`。
4. **`gradlew` 的可执行位已修正为 100755。** 之前是 100644，Linux/macOS 上
   `./gradlew` 会直接 `Permission denied`。
5. **脚本全部自定位**：用 `%~dp0` / `$(dirname "$0")` 推出仓库根，不依赖当前目录。
   路径含空格（例如 `C:\Users\liu chang\…`）时全程加引号——本机就是这个路径，已实测。
6. **Git Bash / msys / cygwin 的 `JAVA_HOME` 是 Windows 路径**（`C:\Program Files\…`），
   `build.sh` 会把它归一化成 `/c/Program Files/…` 再判断。
7. **cmd.exe 会把参数里的 `=` 当分隔符拆开。** `build regressionTest -Pacbric.suites=event`
   到 Gradle 手里会变成 `-Pacbric.suites` 和 `event` 两个参数，然后报一个莫名其妙的
   `Task 'event' not found`。要么加引号（`build regressionTest "-Pacbric.suites=event"`），
   要么用环境变量（`set ACBRIC_SUITES=event`）——启动脚本走的就是后者。
   `build.cmd` 检测到这种拆分时会先打印一句提示，不让你对着 Gradle 的报错发呆。
8. **批处理的两个坑已在注释里标出**，改脚本时别踩回去：
   `for /f ('""quoted exe" args"') ` 的引号数必须是偶数，否则解析器会吞掉整个文件；
   路径里带 `)` 会提前关闭 `( … )` 块。现在的实现改成"重定向到临时文件 + `set /p`"，两个坑一起绕开。

---

## 6. 出错了看这里

| 现象 | 原因 | 处理 |
|---|---|---|
| `No JDK 21 found.` | 没装 JDK 21，且 `JAVA_HOME` 也无效 | 装一个，或 `set JAVA_HOME=C:\path\to\jdk-21` / `export JAVA_HOME=/path/to/jdk-21` |
| `Missing game files required for compilation` | 缺 `libs/asplit-*.zip` 等 | 按提示从自己的 Airships 安装目录复制（见 §2.2） |
| `Missing game files required to launch Airships` | 缺 `game/Airships.json` 等 | 只有启动游戏才需要；只想编译/测试可以忽略 |
| `Timeout … gradle-8.13-bin.zip` | 别的进程占着 wrapper 锁（常见是 VS Code 的 Gradle 扩展在后台下载） | 等它下完；不要删 `.lck` |
| Linux/macOS 上 `./gradlew: Permission denied` | 老版本 checkout 的可执行位丢失 | `chmod +x gradlew build.sh`，或重新 clone（本仓库已把模式修正为 100755） |
| `bad interpreter` | `.sh` 被 checkout 成 CRLF | 确认 `.gitattributes` 生效：`git check-attr eol build.sh` |
| 首次构建很慢 | 要从 Maven Central / maven.fabricmc.net 拉 sponge-mixin 与 ASM | 属正常；之后走 Gradle 缓存 |
| 写了 `-Pacbric.suites=event` 却报 `Task 'event' not found` | cmd.exe 在 `=` 处拆了参数 | 加引号，或改 `set ACBRIC_SUITES=event` |
| `unknown regression suite: 'x'` | 名字打错，或前缀有歧义 | 先 `test list`；用完整套件名 |

---

## 7. 给 agent 的约定

- **默认用 `build`** 验证编译，不要用 `gradlew build`（那会连带跑全部测试、刷 167 行）。
- 改完 mixin / `fabric.mod.json` 之后必须跑全部回归，并且要跑
  `Ac source/tools` 里的 `acbric.cmd verify`（静态校验注入目标与 `@At` 调用点）。
- **不要把逻辑写进 `build.cmd` / `build.sh` / `test.cmd` / `test.sh`**：
  它们只是"找 JDK + 转发"。要加行为就加 Gradle 任务，这样 IDE 和 CI 都受益。
- **先跑覆盖你改动的那一套，收尾前再跑 `test all`。** 全量只有 80 项断言、几秒钟，
  没有理由跳过。迭代过程中用 `test <套件>` 更快：改 `BundledResourceStore` 跑 `test bundle`，
  改 `Event` 跑 `test event`。
- 新增测试请加进套件注册表（见下文 "加一个新套件"），不要新建平行的测试入口。
