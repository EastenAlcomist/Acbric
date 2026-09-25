# 本地代码清单与离线比较（dev.7）

[English](CODE_MANIFEST.md) | **中文**

这是联机一致性检查的第一阶段：导出本地启动代码清单，并比较两份文件。dev.8 已新增[内部握手核心](CODE_HANDSHAKE.zh-CN.md)，**尚未接入大厅、准备/开局阻断或自动同步。** 类与 JSON 格式属于框架内部诊断工具，不是新增的稳定 MOD API。

## 导出与比较

正常通过 Acbric 启动，在 `acbric` 入口处理完成后生成：

```text
game/logs/acbric/<本次启动的会话 ID>/code-manifest.json
```

同目录的 `launch.properties` 和 `startup.json` 用于确认游戏版本、本次会话和详细初始化结果。每次启动使用新会话；不要拿旧清单当成当前进程状态。初始化中断可能没有清单；报告写入失败只告警，不更改原有游戏启动结果。

在开发仓库运行（路径有空格时为整个参数加引号）：

```powershell
.\gradlew.bat compareCodeManifests '-PmanifestLeft=C:/reports/left.json' '-PmanifestRight=C:/reports/right.json'
```

完整分发布局下，也可以在运行包根目录执行：

```powershell
.\jre\bin\java.exe -Dfile.encoding=UTF-8 -cp 'game/mods/acbric-api.jar;libs/*;libs/asplit-A.zip;libs/asplit-B.zip' net.fabricacs.api.impl.CodeManifestCompare C:/reports/left.json C:/reports/right.json
$LASTEXITCODE
```

工具只读两份清单，标准输出为 JSON，不启动游戏、不访问网络。`libs/*` 不包含 ZIP，必须保留上面的两项 `asplit` 依赖。开发仓库 JAR 名称则为 `build/libs/Acbric-1.0-SNAPSHOT-api-mod.jar`。

| 状态 / 进程退出码 | 含义 |
| --- | --- |
| `CODE_MATCH` / 0 | 本清单覆盖的代码身份和状态相同 |
| `DIFFERENT` / 1 | 均可验证，但游戏、Java 版本、MOD 版本/内容/集合等有差异 |
| `UNVERIFIABLE` / 2 | 至少一端缺失身份、读取失败、初始化失败或尚未完成；同时列出已知差异 |
| `INVALID_INPUT` / 3 | 文件不存在、损坏、过大、未知格式/算法或参数错误 |

Gradle 会把退出码 1–3 表示为任务失败；其中 1 和 2 可以是正常的比较结论。差异以左、右清单为参照，`ONLY_LEFT/ONLY_RIGHT` 表示只在相应文件中存在，`MOD_CONTENT` 表示同 ID 的内容不同。两个失败状态相同也不会显示 `CODE_MATCH`。

## 覆盖范围

- 游戏：沿用 Provider 本次识别的真实版本与 `acbric-game-archives-v1` 字节码归档指纹，通过进程内字符串传给 API 层；不从可编辑的旧日志推断。
- Java：比较 `java.runtime.version` 完整版本字符串，没有散列整个 JDK。
- MOD：来自 `FabricLoader.getAllMods()`，排除由上述字段表示的 `airships` 和 `java`；包含 API、Fabric Loader、MixinExtras、嵌套 MOD，以及未知/旧 MOD。磁盘上未加载的 JAR 不参与。
- 初始化：只表示 **`acbric` 入口阶段**的成功、失败或未执行。没有该入口标记为 `NO_ACBRIC_ENTRYPOINT`，不等于所有 preLaunch、Mixin、资源加载或后续回调均已成功。
- 清单不包含绝对来源路径、配置文件、存档或异常堆栈。具体失败堆栈仍看本地 `startup.json`。

`CODE_MATCH` **不是“可以保证联机同步”**：本阶段尚未比较原生 MOD 的实际启用资源、扩展包、共享玩法配置、加载后的战役数据、启动垫片自身及全部外部类路径依赖，也未验证消息发送者身份。相同代码仍可能在不同时序使用本地设置或随机数。客户端自报清单不能提供反作弊保证。

## 内容指纹与限制

MOD 算法为 `acbric-loaded-roots-v1`，遍历 Loader 已解析的实际根目录，不从展示列表或父 MOD 的名称推断内容：

1. SHA-256 输入以算法名开头；字符串为 UTF-8，长度和数量使用 8 字节大端整数编码。
2. 包含根数量与 Loader 返回的根顺序，以保留开发环境中多根的查找优先级。
3. 每根包含文件数量，按相对路径的 Java 字符串顺序排序后写入路径、字节数和完整内容。
4. 绝对路径、ZIP 顺序、压缩方式和文件时间戳不参与；文件名/内容/根顺序变化会改变指纹。空目录不参与，空根标记无法验证。

嵌套 MOD 以 Loader 解析出的实际根独立计算。父 MOD 包含的子 JAR 文件同时也是父根中的普通文件，故**重新打包子 JAR 仍可能改变父 MOD 的指纹**。游戏指纹沿用原有完整归档算法，不适用 MOD 的 ZIP 时间戳归一化规则。

拒绝有歧义的 ZIP 重复条目、符号链接或无法识别的来源；未知来源、读错误和预算超限都留下失败状态，不用空哈希当作成功。每个 MOD 最多 64 根、50000 个文件、512 MiB 内容；每次导出累计读取最多 2 GiB；单根遍历节点最多 100000。导出模型最多 1024 个 MOD；JSON 文件最多 1 MiB。格式解析拒绝重复键、重复 ID、尾部垃圾、错误字段类型、未知字段/schema/算法及无效 UTF-8。

散列在启动阶段同步执行一次，不进入渲染热路径。前后目录属性检查可识别常见的边读边改，但没有锁定整个文件系统；清单不是运行中内存字节码证明。更改 MOD 文件后应重启并重新导出。两个异常清单不能通过比较。

## 验证范围

标准构建通过 356 项无界面回归，新增清单检查 45 项；Windows 符号链接权限不足的检查按日志标为跳过。两套实际游戏字节码（1.2.15.2 / 1.2.14）的隔离 Fabric 加载链均导出成功，覆盖真实嵌套 MOD、新模板及旧 MOD；既有事件、存档、生命周期探针继续通过。另验证四种 CLI 退出码及两份真实清单的自比较。GUI、真实双客户端通信和线上服务尚未验收。

后续验证：dev.8 当前标准检查总计 433 项，两版真实本机 Server/Client 已验证内部握手；前述 dev.7 数量和未测范围是当轮记录。产品大厅仍未接入，见握手文档。
