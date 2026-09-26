# 外部游戏安装：实现与验证记录（dev.25）

[English](EXTERNAL_INSTALL.md)

原型已实现 **安装定位与预检查、实例启动设置及纹理缓存隔离**。dev.24 在隔离测试中验证真实菜单绘制与音频初始化，并修复中文安装路径的 OpenAL 加载。现有 `run.bat`、开发启动和 legacy 布局仍沿用原流程。预检查工具不启动游戏，不是安装器；战役及媒体功能场景已完成定向验收，已知 GL 异常按用户决定暂缓处理；dev.25 已提供正式启动入口和干净发行包，操作见 [外部启动说明](EXTERNAL_START.zh-CN.md)。尚未实现安装器和旧数据迁移。


本轮验证：935 项标准回归通过（两项既有符号链接权限跳过），36 项真实外部加载检查通过；另测解析后核心缺失/版本错配在 preLaunch 前拒绝、Java 8 引导拒绝。解压包在中文/空格路径下用正式入口启动两次，每次真实菜单绘制 30 帧并创建音频，第二次验证包内 Java 优先；另有 API + ARC 两次菜单启动记录。实例占用、核心哈希损坏、错误游戏路径、独立模板 UTF-8 本地路径构建均通过。6 项发行扫描对抗测试通过；所有运行场景源安装 5,325 文件内容未变。证据：工作区 99-研究工具/Acbric正式外部发行-dev25-20260926。此轮不重跑完整战役/媒体；此前 dev.24 结果保留其原边界，不扩张为跨设备/Workshop/全部 MOD 验收。


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

Provider 接收成对的 `acbric.external.install` / `acbric.external.instance` 属性，严格使用所选安装；缺路径或错误安装不会静默回到旧 `libs`。dev.25 正常入口调用游戏 `Main`，须显式提供并核对发行核心 API；缺少核心会报 `CORE_REQUIRED`，核心版本或实际来源不匹配会在 preLaunch 前拒绝。内部 `ExternalRuntimeProbe` 仅保留供回归。这些属性及测试入口不是公开 MOD API，也不是权限边界；不要向玩家提供绕过限制的启动命令。

原型在真实 Knot 中直接读取安装 A/B 与 `lib`，Fabric `gameDir` 指向实例；新增路径 Mixin 在 API preLaunch 之前生效，`AGame.getStaticGameDirectory()` 指向安装，`AGame.getGameDirectory()` 指向实例 `userdata/`。Java MOD/配置/框架日志留在实例，配套原版资源落在实例 `userdata/mods`。旧模式不触发路径覆盖。`AirshipsPaths.staticDataDir()` 等公开方法保持原来的实例侧语义，尚未新增只读安装资源 API。

## 启动设置与用户输出隔离

外部 Provider 在游戏类初始化之前读取安装的 `launch_settings.json`，叠加实例 `config/launch-settings.json`，原子写入实例 `.fabric/acbric/launch-settings.json`。原生初始化改读这份合并结果。未指定字段沿用安装值；这是顶层覆盖，不递归合并对象。缺失文件表示无覆盖；损坏 JSON、重复键、链接路径、嵌套过深或超过 1 MiB 的文件中止启动，不静默回退。解析失败保留原文件和上次合并结果。修改实例配置后重启，不编辑生成文件。

以下两个输出字段始终强制指向实例，即使安装或实例配置要求其他位置也不采用：

| 原生字段 | 实际位置 |
|---|---|
| `customDataDirectoryLocation` | 实例 `userdata/` |
| `customGIFSaveDirectoryLocation` | 实例 `userdata/gifs/` |

GIF 导出入口再次检查实例目标可写，不可用时在原生 Desktop/用户主目录/安装目录回退之前中止。外部模式禁用开发用 checksum 写入及随机用户目录。这不是针对任意第三方 MOD 代码的沙箱。真实 GIF 渲染/导出及失败路径的覆盖与图形诊断限制见下方媒体验收。

## 纹理缓存

外部模式拦截原生共用图片文件加载方法，并跳过原生 `.tex` 移动。只把本次需要的图片及相关图片/缓存候选按需镜像到实例 `cache/game-textures/v1/<内容键>/`，不复制完整资源树。原生文件读取与 raw 缓存生成随后在镜像上执行。MOD/DLC/基础资源查找顺序仍由原逻辑决定；generated 图片与嵌套路径走同一拦截，Heroes 的定义和纹理已纳入下方组合测试；完整英雄玩法及其他组合不在此结论内。

- 来源位置、内容及元数据决定缓存命名空间，不同 MOD 来源与不同实例不会意外共用。
- 存在原图时忽略未校验的原 `.tex`，在实例重新生成 raw；不能仅凭旧 raw 时间戳较新认定其与改过的图片一致。只有 raw 的资源仍支持，`generated` 优先于旧 `images` 位置；不移动、不删除原文件。
- 重复访问使用有上限的内存元数据缓存，大小/时间/文件身份变化会重算内容键。同大小同时间的修改在原生 MOD 重载或进程重启后识别，不逐帧读取并哈希全部内容。
- raw 字节长度非法时，在原生内存映射读取之前删除实例坏缓存，有 PNG 时回退重建。这是结构检查，不能识别所有像素损坏。镜像原图损坏在重载/重启时修复。隔离失败抛错，不回到原资源旁写入。
- 磁盘缓存保留以便复用，目前没有自动回收旧命名空间。镜像及 raw 像素仍属于本地游戏内容，框架发行包不得包含实例/cache。这不是新增公开 MOD API。

## 自动验证与后续

```powershell
.\gradlew.bat build preflightTools regressionTestClasses
python tools/test_external_install.py --tag my-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

脚本需要 Windows、Python 3.11+ 和 JDK 21，每次换新 tag。输出 `build/external-tests/<tag>/`；源安装逐文件内容哈希在运行前后对照，原型不会调用 `Main.main`。测试源码和探针 JAR 不进入发行 API。夹具、日志与资源不提交/分发。测试输入不应由其他游戏进程同时改动，否则哈希差异无法归因。

dev.23 验证：924 项标准回归（比 dev.22 新增 20）；完整 1.2.15.3 安装的 36 项真实 Knot 检查，5,325 个文件内容无变化。检查涵盖 preLaunch、代码来源/路径、有效启动设置、原生纹理文件读写/缓存回退，以及用户数据失败禁止全局回退。仅测试 Mixin 替换末端 Slick Image 构造，因此原生文件 IO 实际执行，但没有 GPU。旧布局 1.2.15.2 / 1.2.14 的 ARC 定向检查各 71 项、合计 142 项通过。不据此宣称完整图形、原生库、DLC、Workshop 或战役兼容。探针结果 `build/external-tests/dev23-final/`；测试替身不进入产品 JAR。

### 真实菜单与音频测试（dev.24）

```powershell
.\gradlew.bat build preflightTools
python tools/test_external_menu.py --tag my-menu-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

该测试器使用 **JDK 21**：Java 写入/网络拦截依赖已弃用的 SecurityManager，仅装在测试进程，产品框架不安装它。每次使用新输出 tag。默认在同一隔离实例运行两次（空缓存首次启动、保留缓存重启）；`--runs 1` 只跑首次。每进程最多 90 秒，真正调用 `Main.main`，菜单完成 30 帧绘制后自动退出，不替换原生图形、资源、音频及纹理 IO。可能短暂出现游戏窗口及声音，不作为玩家启动器使用。render 返回检查点不等于逐项视觉检查或正常退出流程验收。

报告和控制台/原生日志位于 `build/external-menu/<tag>/`。测试拦截 Java 向本次测试目录之外写文件及所有 Java 网络连接；ZipFS `Files.isWritable` 等权限查询允许执行，真正打开写句柄仍受阻。它不是 OS 沙箱或 ACL 只读测试，不拦截原生驱动写入；源安装另外逐文件做运行前后内容哈希对照。联网功能在本测试中刻意禁用。

dev.24：924 标准检查、36 无头外部检查、142 旧布局 ARC 检查，以及 1.2.15.3 输入上的两次真实菜单启动通过。每次完成 30 帧，使用 NVIDIA RTX 5060 / OpenGL 4.6 上下文和 OpenAL Soft；两次运行后实例 raw 缓存均为 147 个。源安装 5,325 文件内容未变。标准回归仍有两项既有符号链接场景因主机权限跳过。这是本机设备结果，不等于广泛显卡兼容或主观听音质量验收。

首次真实测试发现中文安装路径的 OpenAL 加载失败（LWJGL 原生查找错误 126）。外部 `Main` 现通过 JVM `System.load` 预载所选安装的 `OpenAL64.dll`，LWJGL 随后可按已加载模块名取得句柄。不复制 DLL，不更改全局 PATH；失败时启动诊断保留 DLL 原因。旧启动路径不执行预载。

### 真实战役创建、存读档与退出

```powershell
.\gradlew.bat build preflightTools
python tools/test_external_menu.py --campaign --tag campaign-api --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
python tools/test_external_menu.py --campaign --arc-jar "D:/Mods/ARC-Overhaul-0.1.0-dev.3.jar" --tag campaign-arc --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

沿用上述 Windows/JDK 21 写入及网络隔离，输出改为 `build/external-campaign/<tag>/`，固定两个独立进程，每进程最多 240 秒。ARC 参数可选，只读取给定 JAR，不依赖其源码，也不会放入默认发行包。测试实例是新建的，不使用玩家存档。

测试从真实 Main 菜单进入征服设置，原生 `startGame` 驱动 WorldGenScreen 全流程，随后实际绘制战略地图 30 帧。采用 VERY_SMALL、种子 89412、关闭英雄生成的单人场景；不替换地图、道路、舰队或防御生成。调用原生 SaveGameMission 保存、原生 ExitScreen 正常退出；新进程通过 OpenGameMission 读取，检查世界 ID、道路/水域摘要、城市/城镇、势力余额和舰队/防御的归属及名称列表。资产检查并非完整舰船序列化字节比较，也不覆盖战斗。

CREATED/LOADED/EXITED 次数与 MOD 战役数据一并断言。ARC 场景设置人类 3 城市、3 城镇、12345 现金，验证 AI 城镇数量仍为原版；保存前扣除 123。重启前将本地候选配置改为 1 城市、0 城镇、999 现金，旧档应仍有 3+3 聚落和 12222 余额，不重发开局金钱。本机离线检查不等于完整 DLC 英雄玩法、极限开局参数或联机验收。

dev.24 提交 `42426a0` 后补充验收：API 单独 16+15=31 项、API + ARC dev.3 为 17+15=32 项，共 63 项战役检查及各进程 3 项 preLaunch 路径检查通过。四进程均完成战略地图 30 帧并由原生退出，源安装每组 5,325 文件内容不变；每实例 raw 缓存 200 个。924 项标准回归通过，两项既有符号链接权限跳过。产品 JAR 未改，API 仍为 dev.24，测试代码和文档尚未提交。与旧反编译资料对照的 12 个相关 1.2.15.3 class 字节相同。日志中的 Java 网络拒绝是测试隔离预期行为，未做联网验收。

### DLC、原生 MOD 与 GIF 测试

```powershell
.\gradlew.bat build preflightTools
python tools/test_external_menu.py --media --tag media-check --game-dir "D:/Games/Airships Conquer the Skies" --java-home "D:/Java/jdk-21"
```

测试需要所选安装已具有 Heroes and Villains DLC，沿用 Windows/JDK 21 隔离条件。输出在 `build/external-media/<tag>/`，默认首次及缓存重启两个进程，每进程最多 240 秒；`--runs 1` 只跑一次，`--media-part gif` 可单独诊断录像/GIF。不能与 `--campaign` 同用。会短暂出现窗口与声音。

测试在实例内生成一个最小原生 MOD，包含中英文元数据、派生火炮、用于像素对照的测试纹理。通过原生 MOD 重载流程检查 DLC+MOD、仅 MOD、均禁用、仅 DLC，确认定义加载、图片覆盖优先级、图片修改后重载以及移除覆盖后恢复 DLC 原图。该测试 MOD 是夹具，不代表 SteamProgress 等现成大修 MOD 已验收，也不发布进框架包。

录像测试采用原版默认舰船组成的双舰短场景，由原生模拟生成录像，原生 PlaybackIntent/GPU/GIF 编码器完成普通和半尺寸慢速导出。检查多帧文件可解码、尺寸与输出位置；重启再次导出时检查旧文件内容保持不变。用文件临时占用实例 GIF 目录检查拒绝路径，随后恢复；不修改桌面或安装目录。绘制回调只记录检查点，实际驱动操作在下一原生输入边界执行。

`--strict-gl` 启用 LWJGL 严格检查，观察到 GL 错误即使文件操作尚未失败也中止。普通媒体测试使用游戏通常的非严格设置，同时在支持 KHR_debug 的驱动上保留首个错误栈与错误次数；不会清除 GL 错误来伪造通过。出现此类诊断时汇总状态为 `PASS_WITH_NATIVE_GL_ERRORS`，与无错误的 `PASS` 分开。

定位对照可加 `--media-part gif --legacy-control --strict-gl --runs 1`：在新测试目录复制本机游戏资源/库，关闭外部路径与缓存适配，以旧框架布局运行同一短录像。该可选对照占用约 1.6 GB 本地空间，副本不得分发。为排除已知中文路径音频问题，测试入口为对照进程预载副本的 OpenAL DLL；不修改产品代码。

历史媒体验收（正式入口开放前，2026-09-26）：dev.24 基线 `42426a0` 已提交、未推送；后续战役及媒体验收代码/文档尚未提交。新增 DLC/原生 MOD 重载与真实 GIF 测试，两进程各 35 项，共 70 项功能检查通过；普通 960×640/15 帧、半尺寸慢速 480×320/30 帧，共 4 个 GIF，旧文件未覆盖。源安装 5,325 文件内容未变，924 标准回归通过（两项既有权限跳过）。但两进程各记录 454,032 次重复原生 GL 错误，状态为 PASS_WITH_NATIVE_GL_ERRORS；关闭外部适配的旧布局严格对照也在地形绘制处复现（首帧 2,268 次）。这不是完整图形验收通过，未修复/屏蔽该错误。2026-09-26 用户决定将该问题记为低优先级、暂缓处理，不阻塞本次安装重构；旧布局也能复现，但尚无完全不加载 Acbric 的纯原版对照，不能据此断定归因于框架或游戏。当时正常外部入口仍为 EXTERNAL_NOT_READY；该限制已由 dev.25 正式入口与必需核心检查替代。产品 JAR 与 dev.24 相同，ARC 源码和玩家运行副本未改；旧 distZip 仍非干净发行包。

### 已知问题：地形绘制 GL 异常（暂缓）

- **决定（2026-09-26）：** 用户认为相关功能使用较少，暂不修复，不作为本次安装重构的阻塞项；这不是将验收结果改为无错误通过。
- **现象与范围：** GIF 回放验收时地形绘制触发 GL_INVALID_OPERATION（1282），首次栈为 `ShaderProgram.bind → Appearance.drawBevelled → LandFormation.drawNonSoil`。非严格模式可完成导出；严格模式会中止。尚不能断言只影响 GIF。
- **归因边界：** 外部适配关闭的旧布局 Acbric 也能复现，排除仅由本次外部适配引入；未做不加载 Acbric 的纯原版对照，暂不归因于框架或游戏本体。
- **后续线索：** `glBegin/glEnd` 批次内绑定着色器；需要重新处理时先补纯原版对照。保留上方复现命令、首个错误栈、计数及本地证据 `99-研究工具/Acbric外部媒体验收-20260926`，不屏蔽错误、不改写历史结果。

### 写入路径审查与剩余验收

| 路径类别 | 已审查处理 / 当前边界 |
|---|---|
| 基础/DLC checksum | 从安装读取，外部 `doWritechecksum=false` 阻断开发写入；菜单测试观察到基础及 heroes 校验加载。 |
| 默认设计、设置、日志、录像清理 | `AGame` 复制/覆盖到 userdata，设置/日志/录像路径用该根；真实初始化写入留在测试实例。 |
| 图片/raw 缓存 | 共用加载方法改走实例镜像，跳过源 `.tex` 移动；首次和缓存重启菜单通过。 |
| MOD 派生图片/碎片 | 写向来源 MOD 目录，正常实例 MOD 位于实例可写位置；链接/自定义外置 MOD 目录和 Workshop 未验收。 |
| 任务/怪物 | 静态资源读取，后端锁保护静态任务与怪物，用户任务位于 userdata；任务编辑器完整流程未验收。 |
| GIF 与手动导出 | GIF 路径保护、普通/慢速缩小导出及重复命名已验收；图形诊断存在下述原生异常；用户主动选择的导出目标属于另一个显式操作。 |
| 作者离线工具 | 正常 Main 不调用，其硬编码开发输出目录不属于框架支持流程。 |

相关 1.2.15.3 class 已与已有 1.2.15.2 反编译资料对照，变化类另行核查。这是定向源码/字节码审查加运行时写入观测，不是对任意 MOD 或全部游戏分支的证明。

上述战役与媒体功能场景已通过；原生地形绘制的 GL 严格检查未通过，已按用户决定列为暂缓、不阻塞本次重构的已知问题。dev.25 已完成正式外部入口及干净发行/模板；下一步实现迁移及安装器。当前旧 `distZip` 仍会包含本地游戏依赖，**不能作为干净框架发行包分享**；`preflightTools` 仅包含框架启动层、明确启动依赖、脚本和说明。
