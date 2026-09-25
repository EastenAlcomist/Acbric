# 战役生命周期事件

> dev.10：显式声明共享规则、生成前固化及续局检查见 [共享规则](SHARED_RULES.zh-CN.md)。原有本地配置/存储写入仍不自动广播；下文分版本验证记录保留为历史。

[English](CAMPAIGN_LIFECYCLE.md) | **中文**

从 `acbric_api 0.3.3-dev.4` 起提供 `net.fabricacs.api.event.AirshipsCampaignEvents`。原有事件和战役数据 API 保持；存储格式仍为 dev.3 的格式 1。

## 事件与时机

| 字段 / 回调 | 参数和时机 |
| --- | --- |
| `CREATED` / `onCreated(Object campaignWorld)` | 新战役生成已准备地图内容及玩家，首次自动保存及联机校验快照之前。挂在生成期间的 `CampaignWorld.setupPlayer()` 正常返回处，同一生成实例最多一次 |
| `LOADED` / `onLoaded(Object campaignWorld, boolean multiplayerLoad)` | `CampaignWorld(JSONObject, AirshipGame, boolean, InPipe)` 完整成功返回；所有扩展数据已读入。包括单机存档和联机大厅接收到的存档数据，不代表已进入战役，也不保证来自本机磁盘 |
| `RESTORED` / `onRestored(Object previousWorld, Object currentWorld)` | 原版 `ResumeScreen.input` 正常返回且恢复后的战役已安装到活动界面。传递实际新旧对象；同一次对象交接去重，不另发 CREATED、LOADED 或旧对象 EXITED |
| `EXITED` / `onExited(Object campaignWorld)` | 观察到的活动战役返回主菜单/联机大厅、切到另一战役或正常退出程序。正常切界面在客户端 input 边界检测；不是关闭按钮的即时回调，不保证崩溃/强杀时通知 |

所有参数对象实际为 `CampaignWorld`，其 `map` 可传给 `context.campaignData`。事件不可取消，按注册顺序在原调用线程同步执行，沿用普通 Event 的异常传播和快照契约。框架不自动迁移、不回滚监听器已产生的副作用；初始化失败后应停止使用该战役并修正 MOD，不继续覆盖默认值。

## 创建与加载不是“进入界面”

新战役的最外层构造返回时，地图还未完成分阶段生成，不能在那里发“创建完成”。本版在生成流程准备玩家后通知，此时第一份自动保存尚未写出。CREATED 回调可写入确定性的初始数据。

LOADED 表示一次成功的反序列化。联机大厅可能多次重建存档对象，所以不能把它当“每个战役一生一次”或在每次回调中发奖励。`multiplayerLoad` 来自原版构造参数；这个时刻 `world.isMultiplayer()` 可能仍为 false，因为客户端连接尚未绑定。不要按本地玩家身份、主机时钟等生成共享数据。

单机和大厅存档加载允许 MOD **显式调用**自己的版本检查/迁移；回调应幂等，已是目标版本时不再修改。恢复管线使用地图重建和另一个 CampaignWorld 构造重载，因此不会触发 LOADED。

## 推荐写法

```java
AirshipsCampaignEvents.CREATED.register(object -> {
    CampaignWorld world = (CampaignWorld) object;
    CampaignData data = context.campaignData(world.map);
    if (data.read().isEmpty()) data.write(1, new JSONObject().put("counter", 0));
});

AirshipsCampaignEvents.LOADED.register((object, multiplayerLoad) -> {
    CampaignData data = context.campaignData(((CampaignWorld) object).map);
    // 显式处理旧存档缺少本 MOD 数据、检查版本并按需要 migrate。
    // 不因“加载了一次”增加奖励或计数。
});

AirshipsCampaignEvents.RESTORED.register((previous, current) -> {
    CampaignData fresh = context.campaignData(((CampaignWorld) current).map);
    // 替换 MOD 缓存的句柄，读取恢复数据；不写入、不初始化、不迁移。
});

AirshipsCampaignEvents.EXITED.register(object -> {
    // 释放与该对象绑定的本地缓存，不把此回调当保存机会。
});
```

完整例子是工作区独立 `acbric-campaign-demo` 工程，而非框架内置功能。安装示例后，创建/加载会显式准备它自己的命名空间；框架不对其他 MOD 这样做。

## 活动会话与恢复边界

客户端各自持有活动战役引用，没有全局世界缓存。在 input 前后识别战略地图、科技界面、关联战役的 UniScreen；未知临时界面保留原引用，不推断退出。恢复等待界面不退出；成功的恢复钩子更新会话引用，再发 RESTORED。普通界面刷新不重复发这些事件。

EXITED 只针对已被客户端观察到的活动战役；仅在大厅构造后取消的存档对象不一定产生 EXITED。回调先释放/更新内部引用，因此异常或重入不会让同一退出无限重发。自定义 MOD 绕过原版路径、直接改写公共字段时不保证覆盖。新世界生成取消也不属于活动战役退出通知。

RESTORED 只是本机已安装恢复结果的通知，发生时各端可能不同时到达。因此应只重新绑定、重建本地视图或读取数据，不能增加共享计数、重新发资源或执行迁移。当前 API 没有禁止第三方直接写入的沙箱；这是必须遵守的调用契约。

## 验证范围

标准构建 141 项检查；真实 Fabric/Mixin 加载在游戏 1.2.15.2、1.2.14 上通过，含创建准备、JSON 加载成功/失败、恢复等待/安装条件、对象身份去重、正常退出及示例迁移。恢复检查调用变换后目标类的 RETURN handler，并用隔离的最小界面夹具验证条件；没有伪造完整网络重连成功，也没有完成真实 GUI 验收。原有存档/事件回归继续通过。

完整用户验收仍需新建战役、第一份自动存档、退出重载、示例 v1→v2 迁移、缺失 MOD 数据保留及双机恢复测试。数据契约见 [战役数据手册](CAMPAIGN_DATA.zh-CN.md)。
