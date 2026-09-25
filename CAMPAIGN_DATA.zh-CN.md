# 战役 MOD 数据接口

[English](CAMPAIGN_DATA.md) | **中文**

要求 `acbric_api >=0.3.3-dev.3`（未发布）。按 MOD ID 保存战役级共享 JSON 数据；不会自动收集 Java 字段、发送网络消息，也不会自动给舰船/城市增加属性存储。

## 获取接口与生命周期

保存初始化入口收到的 `AcbricModContext`，取得实际战役后传入其 **`WorldMap`**（通常是 `campaignWorld.map`）：

```java
import net.fabricacs.api.save.CampaignData;
import net.fabricacs.api.save.CampaignDataSnapshot;
import org.json.JSONObject;

CampaignData data = context.campaignData(campaignWorld.map);
if (data.read().isEmpty()) {
    data.write(1, new JSONObject().put("counter", 0));
}
CampaignDataSnapshot snapshot = data.read().orElseThrow();
JSONObject next = snapshot.data();
next.put("counter", next.getInt("counter") + 1);
data.write(snapshot.dataVersion(), next);
```

上述示例展示显式修改，只能在预期的玩法时机、游戏模拟线程执行；不能放进绘制或每次序列化回调。联机修改应来自各端共同收到的指令，或各端一致执行的模拟步骤。只在本机调用 `write` 不会广播。

`context.campaignData(worldMap)` 自动采用 `context.modId()`。也可使用 `new CampaignData(worldMap, modId)`，主要供辅助方法调用；请使用自己的 MOD ID。命名空间用于避免误冲突，不是同进程 Java 代码之间的安全沙箱。ID 格式为 Fabric 的 `[a-z][a-z0-9_-]{1,63}`。

句柄只绑定一个地图实例。切换战役或重连替换地图后，应重新获取；保留旧句柄不会自动跟随当前世界。传入 `CampaignWorld`、null 或未经过 Acbric 变换的对象，会抛出 `IllegalArgumentException`。

## 公开接口

类型位于 `net.fabricacs.api.save`。`AcbricModContext.campaignData(Object worldMap)` 返回 `CampaignData`。

| 成员 | 契约 |
| --- | --- |
| `CampaignData(Object worldMap, String modId)` | 绑定地图及 MOD 命名空间 |
| `modId()` | 命名空间 ID |
| `read()` | `Optional<CampaignDataSnapshot>`；缺失不同于已保存的空对象 |
| `write(int dataVersion, JSONObject data)` | 验证并替换本 MOD 的数据，完整复制；拒绝比现有格式更旧的版本 |
| `remove()` | 显式删除本命名空间，返回原来是否存在；即使现有格式更新也允许明确删除 |
| `migrate(int targetVersion, CampaignData.Migration migration)` | 显式原子迁移，返回是否发生更新 |
| `CampaignDataSnapshot(int dataVersion, JSONObject data)` | 构造独立不可变快照 |
| `CampaignDataSnapshot.dataVersion()` | MOD 自己定义的非负格式版本，与 MOD/API 版本号无关 |
| `CampaignDataSnapshot.data()` | 每次返回新的可修改 JSON 副本 |
| `Migration.migrate(int previousVersion, JSONObject data)` | 输入是副本，返回替换 JSON；可以抛出 `Exception` |

数据必须是 JSON 对象；可包含对象/数组、字符串、布尔、JSON null、Java byte/short/int/long 整数及有限 float/double。数字经 JSON 文本规范化，浮点整数的表示可能改变。不支持任意 Java 对象及任意 `Number` 子类，拒绝循环引用和超过 64 层连接深度的嵌套。原始输入对象、读出的可修改对象都不会作为实时存储引用保留。

非法参数抛出运行时异常；降级写入、迁移遇到更新的数据版本抛出 `IllegalStateException`，原数据保持不变。容器同步不代表游戏对象线程安全；应在游戏模拟线程调用，避免并发读改写。

## 显式迁移

没有数据或版本相同时，`migrate` 返回 false，不调用回调。存储版本高于目标版本时直接报错。版本较旧时，把副本交给回调，结果验证成功才同时提交版本和内容。读取、保存、校验和重连都不会自动执行迁移。

```java
data.migrate(2, (previousVersion, json) -> {
    if (previousVersion != 1) {
        throw new IllegalStateException("不支持的存储格式：" + previousVersion);
    }
    json.put("visits", json.getInt("counter"));
    json.remove("counter");
    return json;
});
```

MOD 需明确处理自己支持的各个旧格式。回调失败或结果非法时保留原内存数据并报错，不写磁盘。迁移期间禁止通过接口修改同一地图的任何命名空间。回调还应避免修改其他游戏对象、其他战役、文件或随机数状态；这些外部副作用无法由接口回滚。不要吞掉迁移错误再用默认值覆盖原数据。模板 `CampaignDataExample.java` 提供已编译的初始化/迁移示例，不会自动调用。

## 存储和状态恢复

`WorldMap.toJSON(OutPipe)` 在地图内添加保留标记 `acbricCampaignData: 1`，并通过**游戏原有存储管线**登记 `acbric_campaign_data` 块。块含 `format: 1` 及 `payload` 字符串，后者编码以下逻辑 JSON：

```json
{
  "format": 1,
  "mods": {
    "example_mod": { "version": 2, "data": { "visits": 12 } }
  }
}
```

块内部使用 JSON 文本，是因为原版二进制写入器及哈希不支持 `JSONObject.NULL`。登记时即取得固定快照，延迟写入器不会读取后续实时数据。独立 `registerWithoutVersion` 块还能绕过原版按地图 age 缓存的边界，让暂停时修改数据也进入校验。即使没有命名空间也登记空扩展块，保证后来添加第一份或删除最后一份数据时，缓存中的地图标记仍然有效。

数据随使用同一管线的普通战役保存、自动保存、另存为，以及地图状态快照和恢复流转。它属于原版多文件/打包存档结构，不是另行管理的外置附加文件。复制时保留**整个原版存档**，不要只拷一个 `.gug` 块。Acbric 没有改变原版写盘事务或备份保证；序列化成功不等于磁盘保存成功。

从 JSON 构造 `WorldMap` 时通过 `InPipe` 恢复。旧存档无标记时使用空容器；存在标记但扩展块缺失、损坏或格式不支持时，以带 Acbric 说明的 `IOException` 中止本次加载，不静默回退。所有命名空间结构完整验证后才接受；各 MOD 内容字段的校验和迁移仍由 MOD 负责。

框架保留所有命名空间，不要求相应 MOD 已安装；支持的封装格式中的未知元数据也会保留，不运行缺失 MOD 的代码。缺少玩法 MOD 仍可能使战役无法正常运行。保留保证依赖此功能启用：用原版或较旧 Acbric 打开并再次保存，可能丢失扩展数据。

## 联机边界与验证

保存/恢复支持不等于自动实时同步。各端需要一致的框架/MOD 行为、玩法配置、初始状态、指令顺序、执行顺序及确定性随机数使用方式。界面偏好、本机时间、玩家本地设置不要放入共享战役数据。本版没有新增网络指令、权限检查或 MOD 清单握手。独立战斗存档、舰船设计及自动对象属性附着不在此接口范围内。

验证包括隔离的原版二进制存档往返、命名空间隔离、迁移失败保护、快照独立性及状态缓存检查。游戏 `1.2.15.2`、`1.2.14` 的真实 Fabric 探针覆盖变换后的地图构造、`CampaignWorld` 序列化/读取、磁盘往返及实际 `StoredState` 路径，含 age 不变的更新。这不替代完整战役操作或双机联机/重连验收。

取得/重新绑定地图的事件从 dev.4 提供，见[战役生命周期](CAMPAIGN_LIFECYCLE.zh-CN.md)；存储接口本身仍从 dev.3 开始支持。
