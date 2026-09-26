# 外部游戏安装：第一阶段（dev.22）

[English](EXTERNAL_INSTALL.md)

本阶段实现 **安装定位与预检查、最小外部加载原型**。现有 `run.bat`、开发启动和 legacy 布局仍沿用原流程。新工具不会启动正常游戏，不是安装器；尚未完成资源缓存读写分离，不能把它当成已可公开交付的只读安装运行模式。

## 检查一份本机游戏

先用 JDK 21 构建检查工具（不需要打完整旧版发行包）：

```powershell
.\gradlew.bat preflightTools
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build\preflight\check-install.ps1 -GameDir "D:\Games\Airships Conquer the Skies" -InstanceDir "D:\Acbric\instances\default" -JavaHome "D:\Java\jdk-21"
```

路径换成自己实际位置。Windows x64，Java 21+；参数 `-JavaHome` 默认读 `JAVA_HOME`，不能使用游戏附带的 Java 8。检查脚本先运行 Java 版本检查，因此旧 Java 也会收到中英文可读提示及日志，不会直接报框架 class 版本错误。

`GameDir` 必须是包含 `Airships.json`、根目录 A/B 归档、`lib/`、`data/` 和默认设计资源的原版安装根。实例与安装必须分开，不能互相包含。检查会在实例创建锁文件、必要目录和诊断，测试这些目录能否写入，但不导入原存档、不安装 API、不复制游戏。首次创建的空目录和锁文件可保留，锁是否占用取决于进程持有的系统文件锁。

无需使用脚本时，可通过兼容 Java 调用：

```powershell
java -cp "build/preflight/loader-libs/*" net.fabricacs.acbric.ExternalPreflight --game-dir "D:/Games/Airships Conquer the Skies" --instance-dir "D:/Acbric/instances/default"
```

退出码 `0` = 本阶段预检查通过，`2` = 参数或检查失败。成功报告状态 `PREFLIGHT_OK_NOT_LAUNCHED` 表示没有启动游戏，不代表完整资源或 MOD 兼容验证。

## 检查及诊断范围

- 使用 Loader 已有的 JSON 解析器；正确处理转义、嵌套无关字段，拒绝重复关键字段、非法类型及尾随内容。根配置的 `classPath` 相对游戏安装根解析，限制在安装目录内。
- 必须有根 A/B 和原生 `Main.main(String[])`，只接受该标准入口；不执行配置中的 `vmArgs` 或旧 `jrePath`。此阶段不支持自定义主类、改造版安装或其他平台目录结构。
- 先按配置排列代码，再稳定排列 `lib/` 依赖；保留此前排除 steamworks4j 1.3、使用 1.9 的策略。按归档内容排除 Loader/Mixin/ASM/框架启动层，重复普通类立即报错，不默许另一版本覆盖。
- 静态读取版本及游戏代码 SHA256；不定义或初始化游戏类。检查关键依赖类、若干基本资源目录非空，以及四个 Windows x64 图形/输入 DLL 的 PE 架构。这不是全资源校验或 DLL 实际加载测试；Steam/Workshop 和完整游戏兼容另行验证。
- 1.2.14 / 1.2.15.2 / 1.2.15.3 只是已知版本号，仍报告 `KNOWN_VERSION_UNVERIFIED_CONTENT`；其他可解析版本报告 `UNKNOWN_VERSION`，预检查可列出计划，但不宣称受支持。未知/无法读取的版本或指纹失败。
- 实例目录链接/重解析路径拒绝；安装与实例不能互相包含。实例写入失败不回退到玩家全局目录，文件锁防止同一实例同时由两个外部原型进程使用；旧启动流程尚未接入这把锁。

即使选错游戏/实例目录也先写系统临时目录：Java 报告 `%TEMP%/acbric-preflight-*/preflight.properties`，脚本引导日志 `%TEMP%/acbric-bootstrap-*.log`。成功后另存到实例 `logs/acbric/preflight/`。控制台打印完整位置；报告记录路径、版本、代码指纹、类路径顺序和警告。脚本连 Java 都未能启动时，查看引导日志。日志包含本机路径，反馈前可去掉个人信息。

## 内部外部加载原型

Provider 接收成对的 `acbric.external.install` / `acbric.external.instance` 属性，严格使用所选安装；缺路径或错误安装不会静默回到旧 `libs`。当前只能进入仓库测试类 `ExternalRuntimeProbe`，普通外部启动明确报 `EXTERNAL_NOT_READY`。这些属性及测试入口不是公开 MOD API，也不是权限边界；不要向玩家提供绕过限制的启动命令。

原型在真实 Knot 中直接读取安装 A/B 与 `lib`，Fabric `gameDir` 指向实例；新增路径 Mixin 在 API preLaunch 之前生效，`AGame.getStaticGameDirectory()` 指向安装，`AGame.getGameDirectory()` 指向实例 `userdata/`。Java MOD/配置/框架日志留在实例，配套原版资源落在实例 `userdata/mods`。旧模式不触发路径覆盖。`AirshipsPaths.staticDataDir()` 等公开方法保持原来的实例侧语义，尚未新增只读安装资源 API。

## 自动验证与后续

```powershell
.\gradlew.bat build preflightTools regressionTestClasses
python tools/test_external_install.py --tag my-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

脚本需要 Windows、Python 3.11+ 和 JDK 21，每次换新 tag。输出 `build/external-tests/<tag>/`；源安装逐文件内容哈希在运行前后对照，原型不会调用 `Main.main`。测试源码和探针 JAR 不进入发行 API。夹具、日志与资源不提交/分发。测试输入不应由其他游戏进程同时改动，否则哈希差异无法归因。

dev.22 验证：904 项标准回归（新增 32）；完整 1.2.15.3 安装的 25 项真实 Knot 检查，5,325 个文件内容无变化。检查涵盖 preLaunch 解包、代码来源、版本/路径、实例日志/配置与用户数据缺失时禁止回退。旧布局 1.2.15.2 / 1.2.14 的 ARC 定向检查各 71 项、合计 142 项通过；不能据此宣称新版本完整游戏已经兼容。

下一阶段处理 `LaunchSettings` 实例覆盖、GIF 等用户输出路径、基础/DLC/原版 MOD 的纹理缓存读取/生成/失效及所有安装写入路径，再验证只读安装到主菜单和完整战役。之后清理发行与模板、实现迁移及安装器。当前旧 `distZip` 仍会包含本地游戏依赖，**不能作为干净框架发行包分享**；本轮 `preflightTools` 仅包含框架启动层、明确启动依赖、脚本和说明。
