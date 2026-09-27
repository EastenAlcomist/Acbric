# 共享战役规则（dev.10）

[English](SHARED_RULES.md) | **中文**

API `0.3.3-dev.10` 新增按 MOD 显式声明的玩法规则。多人新战役须代码和声明规则均一致才能准备、开局；新战役在生成前固化选定值，续局使用存档值。框架不覆盖配置文件，不自动同步任意字段、命令或运行期状态。

## 声明与读取

在带上下文的 `acbric` 入口中声明一次，用已经校验的配置构建规则。只声明影响玩法且可以向其他玩家公开的设置；每个 MOD ID 一份声明，ID 由上下文绑定。`fabric.mod.json` 最低依赖为 `"acbric_api": ">=0.3.3-dev.10"`。

```java
SharedRules rules = context.sharedRules(1,
    new JSONObject().put("damagePercent", 100), values -> {
        Object value = values.get("damagePercent");
        if (!(value instanceof Integer n) || n < 1 || n > 10000) {
            throw new IllegalArgumentException("damagePercent must be 1..10000");
        }
    });
// world 是实际 CampaignWorld；生成开始后、CREATED/LOADED 或游戏过程中读取。
int damagePercent = rules.forCampaign(world.map).values().getInt("damagePercent");
// 显式重载配置后，可更新后续新战役的候选值：
rules.update(new JSONObject().put("damagePercent", 125));
```

导入 `net.fabricacs.api.rules.SharedRules`、`org.json.JSONObject`。MOD 保留声明句柄；玩法逻辑使用 `forCampaign(world.map)`，`current()` 仅是新战役候选值。更新不修改已有战役，也不写配置文件。原生恢复替换地图后须重新取得当前地图。模板的 `SharedRulesExample` 仅为可编译示例，默认不调用，也未实现伤害功能。

| 公开成员 | 含义 |
| --- | --- |
| `context.sharedRules(version, values, validator)` | 注册当前 MOD 的一份声明；重复注册报错 |
| `SharedRules.modId()` / `current()` | 归属 ID / 不可变的新战役候选快照 |
| `update(JSONObject)` | 校验后替换候选值；失败保留旧值，等价值不会使准备失效 |
| `forCampaign(Object worldMap)` | 读取并校验本 MOD 的存档规则；缺失或版本不符时报错 |
| `checked(SharedRuleSnapshot)` | 按句柄的版本和校验器检查快照，不写入存储 |
| `new SharedRules(id, version, values, validator)` | 仅构造未注册句柄，不参与大厅检查/固化；推荐上下文工厂 |
| `new SharedRuleSnapshot(version, values)` | 构造不可变、有界快照，不注册声明 |
| `SharedRuleSnapshot.version()` / `values()` | 非负整数版本 / 独立 JSON 副本 |
| `SharedRules.Validator.validate(JSONObject)` | 校验副本，可以抛出 `Exception` 拒绝数据 |

`checked`/`forCampaign` 版本不符抛 `IllegalStateException`，校验器异常包装为 `IllegalArgumentException`；缺失存档规则抛 `IllegalStateException`，不支持的地图会报错。非法 JSON 值/超限在快照构造时报错。快照 `equals`/`hashCode` 比较版本和规范值。修改校验副本不会更改实际规则。

在游戏线程声明、更新和读取。校验器应确定、快速、无副作用，不能在其中注册/更新规则、写配置、写存档或发消息。声明、更新、战役读取、续局大厅刷新和加载均可能调用校验器；它应只依赖声明版本和传入值，不依赖本机当前偏好。递归校验会被拒绝。句柄同步不代表游戏对象可跨线程操作。

## 固化、保存与续局

原生 `WorldGenScreen(CampaignWorld, AirshipGame)` 构造器 RETURN 钩子在生成前固化，早于 `CREATED` 和首次自动存档。单人使用当前候选值，多人必须使用战役大厅核准的快照；绕过该大厅的自定义多人生成入口不受支持。后续本地修改只影响新战役。没有声明的新战役也会写入空规则集。

存储使用保留的 `acbric_api` 战役命名空间，版本 1，字段 `sharedRules` 是规范 JSON 文本，避免游戏浮点 JSON 序列化改变数值。MOD 不应编辑此命名空间。现有原生地图保存/恢复管线保留规则及缺失 MOD 的规则条目；这不新增运行期网络同步能力。

JSON 战役加载在 `LOADED` 前完成结构与已声明版本校验，失败抛 `IOException` 中止加载。原生地图重建只校验结构，不执行 MOD 校验/迁移回调；`forCampaign` 校验所读规则。续局比较完整存档规则集，包括当前缺失 MOD 的条目，不使用本机候选值。

没有任何已安装 MOD 声明规则时，不带规则块的旧存档仍可使用；若 MOD 新增声明而旧存档缺少对应条目，则加载/续局以 `RULE_MISSING` 失败。版本不支持、数值非法同样阻断。普通加载没有自动补默认值或房主覆盖。dev.11 新增独立的 [显式旧档接纳与迁移](RULE_SAVE_MIGRATION.zh-CN.md)，须先预览再另存；旧档不会在加载时自动转换。MOD 作者接入或升级规则版本前须规划存档兼容性。未接入的旧 MOD 保留原公开接口和行为，但其未声明配置不在检查范围内。

## 比较、限制与协议

对象键排序，数组顺序有意义，`1` 与 `1.0` 等同值十进制数字视为相同。MOD ID、规则版本以及 `NEW`/`SAVED` 来源也参与比较。支持对象、数组、字符串、JSON null、布尔、基本整数包装类型及有限 `Float`/`Double`；不接受任意对象、循环引用、非法 Unicode 或非有限数。

最多 64 个 MOD 声明；每个编码快照和**完整编码规则集**均不得超过 12,000 个 UTF-8 字节，元数据和转义会占据预算。每次编码最多 16 层、遍历 4,096 个节点。规则适合少量设置，不适合世界状态；合计超限视为无法验证。差异信息列出 MOD、版本及 JSON 字段路径，不显示具体值；网络报文本身仍包含声明的值。

内部消息 `acbric:campaign_rules` v1 最大 40,000 字节，绑定房间、发送者及完整已验证代码会话映射，每两秒一次，十秒内最多五次。历史帧、错误会话/房间、不支持的格式和未确认后的超时回复都不能通过；同一上下文自报矛盾规则会保持无法验证，直到重新检查。原生 `acbricLobby` 准备元数据升到 v2，加入 `rulesDigest`；代码握手本身仍为 v1。这些内部格式不是公开 MOD 联网 API。

中英文大厅按钮区分规则等待/检查、一致、差异、无法验证与超时，点击可重试。候选值变化会新建代码/规则检查上下文并清除准备。规则一致后仍须满足原生资源、玩家条件及当前全员准备凭据。自报一致性不等于身份认证、共识、反作弊或确定性玩法保证。

## 验证范围

标准构建通过 541 项断言（新增规则检查 64 项）；两项已有符号链接场景因本机权限跳过。游戏 1.2.15.2、1.2.14 共 14 轮原生 Server/双 Fabric Client 隔离实验，通过 204 项上层检查：同值/异值、旧档缺失、版本错误、存档值优先、准备后修改和无声明普通房间兼容。探针使用最小夹具调用实际变换后的生成钩子与原生存储管线，没有完整生成世界或操作 GUI。官方服务认证、跨机器联机、完整保存/续局/恢复及长时间游戏仍需人工验收。
