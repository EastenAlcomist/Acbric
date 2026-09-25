# 存档规则预检查与显式转换（dev.11）

[English](RULE_SAVE_MIGRATION.md) | **中文**

`acbric_api 0.3.3-dev.11` 新增 `CampaignRuleSaves`，并为[共享规则](SHARED_RULES.zh-CN.md)提供显式注册的版本转换。普通加载仍不补默认、不执行迁移。原生文件加载钩子在构造战役前报告声明规则不兼容；原有战役构造检查继续保留，在 `LOADED` 前阻断，也覆盖绕过文件入口传入的联机战役数据。

## MOD 注册约定

初始化时在声明句柄上注册纯转换。每个回调从一个旧版本直接转换到当前声明版本，不自动串联、不降级，也不修复同版本非法值。

```java
SharedRules rules = context.sharedRules(2,
    new JSONObject().put("basisPoints", 10000), values -> {
        if (!(values.get("basisPoints") instanceof Integer n) || n < 1 || n > 100000)
            throw new IllegalArgumentException("Invalid basisPoints");
    });
rules.migration(1, (previousVersion, oldValues) ->
    new JSONObject().put("basisPoints",
        Math.multiplyExact(oldValues.getInt("percent"), 100)));
```

`migration(int sourceVersion, SharedRules.Migration)` 拒绝负版本、当前/更新版本、重复注册及未通过上下文注册的句柄。`Migration.migrate(int previousVersion, JSONObject values)` 可以抛 `Exception`，输入是副本，输出按当前 schema 及合计大小限制验证。迁移应在检查存档前注册。回调必须确定、无副作用；框架不能撤销 MOD 自行造成的外部副作用。

## 检查、预览、另存

公开入口为 `net.fabricacs.api.rules.CampaignRuleSaves`。在所有相关 MOD 声明完成后，游戏线程上、未运行战役且原档未被写入时调用。框架不会管理第三方线程，也不能自动识别所有外部写入者。

```java
var inspection = CampaignRuleSaves.inspect(sourceDirectory);
var report = inspection.report();
// 先向用户展示问题。缺失声明的值应由调用方显式提供，
// 例如来自转换界面中确认过的初始参数：
var plan = inspection.prepare(Map.of(
    "my_mod", new JSONObject().put("basisPoints", 12500)));
// 没有 MISSING 条目则传 Map.of()。
// 展示 plan.changes() 中的原版本/值与目标版本/值后，再执行：
Path converted = plan.writeNew(newSaveDirectory);
```

`adoptions` 必须恰好包含报告中所有 `MISSING` 的 MOD ID，它不是通用覆盖表。已存在且匹配或非法的规则不能用本机默认值覆盖；旧版本必须有对应转换器。原档、声明、候选值或迁移注册变化后，应重新检查并预览。

| 类型/成员 | 契约 |
| --- | --- |
| `inspect(Path)` | 读取原生目录存档的固定快照，不构造战役、不迁移、不写文件 |
| `Inspection.source()` / `report()` | 规范源路径 / 不可变兼容性报告 |
| `Inspection.prepare(Map<String, JSONObject>)` | 在副本上显式接纳和执行注册迁移，返回已验证的内存计划 |
| `Report.issues()` / `compatible()` / `summary()` | 不可变问题列表、兼容性结果及含 MOD ID/版本的双语提示 |
| `Issue.modId()` / `savedVersion()` / `targetVersion()` / `status()` | 版本 `-1` 分别表示缺少存档条目或没有当前声明 |
| `Plan.changes()` / `report()` | 不可变变更前后快照及结果报告 |
| `Change.modId()` / `before()` / `after()` | 显式接纳时 `before == null`；快照不可变 |
| `Plan.writeNew(Path)` | 发布到不存在的目标，不重新执行迁移或校验回调 |

状态：`MATCH` 为当前有效值；`MISSING` 须显式选择初始值；`MIGRATABLE` 有直达转换器，但转换前**不能加载**；`UNSUPPORTED_VERSION` 需要匹配 MOD 或转换器；`INVALID` 未通过当前校验；`RETAINED` 为缺失 MOD 的保留条目，不执行其代码。只有全为 `MATCH`/`RETAINED` 才兼容。存储结构损坏或版本不支持直接以 `IOException` 拒绝检查，不当成空规则集。

转换失败、文件冲突、输出非法均抛 `IOException`；迁移注册参数非法抛 `IllegalArgumentException` 或 `IllegalStateException`。没有自动重试、默认值替换或备用存档恢复。公开嵌套 record/enum 属于本开发版 API，`impl` 类仍是内部实现。

## 文件边界与联机

本版转换游戏当前使用的 **IODirectory 目录式存档**，通常是名称以 `.json` 结尾的目录。更早的单文件 JSON、压缩文件、packedJSON 暂不支持转换，会明确拒绝；普通原生加载仍保留其格式支持并执行规则预检查。转换要求主 `index.gug` 有效，不替代备用索引修复工具。

快照最多 20,000 个普通文件、合计 256 MiB，目录深度最多 16，每个解码的二进制 JSON 块最多 16 MiB。拒绝内部符号链接、junction/其他特殊文件和越界索引路径。共享规则大小限制不变。验证索引引用文件存在，但这不是所有游戏对象和功能 MOD 数据格式的全面校验。

输出副本只重写世界扩展标记、`acbric_campaign_data` 块及必要索引。其他二进制块、附属文件按原字节复制，其他 MOD 命名空间、未知规则和 `acbric_api` 内的其他字段保留。原档从不写入/删除，本身就是转换前保留副本，不额外复制一份备份。

目标必须不存在，父目录必须存在，源和目标不能重叠。发布前再次检查原档内容和声明代次，验证暂存结果，再以不覆盖方式移动目录。原档变化要求重新检查。发布前失败只清理本次暂存目录；强行终止可能留下隐藏的 `.acbric-rules-*` 目录。这不承诺断电持久性或抵抗恶意并发文件替换；转换期间应停止其他进程写入原档。

转换不修改当前活动世界或配置文件。联机读取转换后存档，并沿用全员规则检查；通过正常流程分发/加载新档，各端仍须代码和存档规则一致。不要各自按不同本地默认值转换，也不能把规则一致当成完整状态同步。

## 验证与示例

标准构建通过 597 项检查，含新增 56 项存档转换检查；两项已有符号链接场景因本机权限跳过。两版真实游戏分别运行独立测试 MOD 的 legacy/v1/v2，共六个 Fabric 进程、136 项检查，覆盖真实 `WorldGenScreen` 构造、`setupPlayer`、原生磁盘存取、预检查、测试 MOD 转换动作及地图状态重建。使用小型夹具地图，没有完整程序生成世界或 GUI 渲染。

两版共四轮原生 Server/双 Client 实验通过 54 项上层检查：转换结果相同可准备，不同则阻断准备/开局。成功路径验证 `canStart`，不等于完整多人开局。跨机器、官方服务及完整玩法仍待人工验收。

独立工作区项目 `acbric-rules-test` 提供三个互斥 JAR 和双语状态文字，以及检查/预览/另存/失败/重载热键。放在本地测试套件，不是框架默认 MOD，不修改伤害玩法。安装与旧档升级练习见工作区测试手册。
