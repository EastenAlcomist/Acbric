# 运行期诊断与订阅范围

[English](EVENT_SCOPES.md) | **中文**

从 `acbric_api >=0.3.3-dev.6` 起，可通过 `AcbricModContext.eventScope(name)` 创建 `net.fabricacs.api.event.EventScope`，集中管理订阅并给回调错误关联 MOD、事件与用途。范围必须由 MOD 显式关闭；框架不会猜测它属于应用、界面还是战役。

## 使用

```java
EventScope application = context.eventScope("application");
application.registerOnce(AirshipsDataEvents.DATA_LOADED, successful -> {
    context.logger().info("Data loaded: " + successful);
});
EventHandle tick = application.register(AirshipsClientEvents.CLIENT_TICK_START, game -> {
    // 本地观察；线程仍由事件调用位置决定。
});
tick.unregister(); // 只取消这一份订阅。
application.close(); // 取消剩余订阅，重复调用无副作用。
```

同一个监听器可重复注册，各有独立句柄。同名范围是不同对象，不会自动合并。创建范围和正常分发不会写诊断文件。`EventScope(String modId, String name)` 供自定义事件和测试直接构造；实际 MOD 推荐上下文入口，避免手填归属。标签必须非空且最多 256 字符，归属是调用方声明，不是权限或安全校验。

| 成员 | 语义 |
| --- | --- |
| `register(Event<T>, T)` | 普通受管理订阅，返回 `EventHandle` |
| `registerOnce(Event<T>, T)` | 首次执行前消费资格，失败也不重试 |
| `close()` | 永久关闭范围并注销全部订阅；之后注册抛 `IllegalStateException` |
| `isClosed()` | 是否已关闭 |
| `size()` | 尚未注销/消费的订阅数；并发下是瞬时观察值 |
| `modId()` / `name()` | 范围的 MOD 归属和用途标签 |

范围实现 `AutoCloseable`，临时操作可用 try-with-resources；长期应用监听不能在初始化结束时就 close。订阅接口必须由 Java interface 表示（与旧 registerOnce 的代理要求相似），支持继承接口。普通 `Event.register` 仍可用于原有自定义类型。

## 关闭、快照和线程

关闭或单独注销会释放代理持有的监听器引用，即便旧 invoker 快照仍被其他代码缓存，后续调用也使用事件空工厂的中性结果，例如 `PASS`。已经取得监听器、进入执行路径的并发回调可能继续完成；close 不阻塞等待、不打断线程，不保证其后完全没有在途回调。

一次性订阅在首次执行前从事件和范围移除，递归和并发只有一个执行者。回调内 close 可阻止同一旧快照里尚未开始的其他本范围回调。其他范围和旧注册不受影响。外部 `event.clearListeners()` 或通过 `event.listeners()` 中的代理注销，也会同步释放受管理订阅；MOD 自己应使用返回的句柄，不要清空全局事件。

新范围注册返回的代理与原始监听器不是同一对象；`event.unregister(originalListener)` 不负责查找代理，请使用句柄或范围。注册失败会撤销本次受管理注册。close 先使所有代理失效，再逐一注销；若自定义事件工厂重建时抛错，仍尝试清理其他条目，最后抛出第一个错误。自定义工厂应无副作用，空列表要能产生中性 invoker，避免在工厂里操作其他事件或范围。

范围控制订阅寿命，不调度游戏线程、不回滚 MOD 的外部修改，也不阻止用户自行重复注册。初始化中途失败时，可在自己的 catch/finally 中关闭已创建范围；旧预启动入口不会自动回滚所有注册。

## 战役中的推荐分工

- `application` 保存 CREATED/LOADED/RESTORED/EXITED 等跨战役观察者，整个应用期间保留。
- MOD 接受一个新世界实例时，关闭旧 `campaign` 范围，创建新范围并重新绑定局部监听器。RESTORED 也要处理世界替换；不要重新初始化共享数据。
- EXITED 仅在与当前绑定世界匹配时关闭战役范围。不要顺便关闭 application，否则下一局不会再收到创建/加载通知。

CREATED/LOADED 不保证世界已是当前活动界面，真实时机以[生命周期契约](CAMPAIGN_LIFECYCLE.zh-CN.md)为准。需要“只在活动战役工作”的 MOD 应结合自身界面/活动世界判断管理范围。示例 v3.1 演示绑定与释放，不把每次 JSON 构造当成真正进入游戏。

## 运行期报告

受管理回调抛错时，控制台输出一行 `[Acbric runtime event]` JSON；真实框架启动还会写入当前诊断会话的 `game/logs/acbric/<session>/runtime-events.jsonl`。与 `startup.json`/`launch.properties` 共用会话目录；没有错误时文件可以不存在。脱离框架启动的独立 EventScope 使用有界控制台记录。

记录包含 MOD ID、scope、事件名、回调方法、监听器类名、线程、发生时间、该订阅失败次数、原异常类型/消息和截断堆栈。不会保存事件参数、游戏对象或 Throwable 引用。报告格式属于内部诊断，不是稳定数据 API。

为防止绘制/tick 回调反复刷屏，每份订阅仅记录第 **1、2、4、8、16** 次失败；每启动会话最多 **64 条、1 MiB 编码数据**。消息最多 1024 字符，堆栈最多 4096 字符并标记截断。超过限制仍抛出每一次异常，监听器也不会自动停用；报告不是所有错误的完整计数清单。日志文件写入失败后停用本会话的文件输出，保留受同样限制的控制台输出。格式化/日志错误尽力隔离，不替代原始 MOD 异常。

文件仅供排错，非事务日志；进程崩溃/强杀不保证最后一行完整。分享报告前按需检查 MOD 异常消息和本地路径。嵌套事件逐层记录各自受管理边界，外层归属表示传播经过该 MOD 回调，不能据此断言外层就是根因。

## 兼容边界

新增 `Event(String name, InvokerFactory<T>)` 与 `name()`。原构造器保持，名称默认为 `custom`；所有 34 个内置事件使用 `事件类.字段` 名称。名称供排错，不参与顺序或取消判断。

旧 `register/registerWithHandle/registerOnce` 不自动获得 MOD 归属或新增错误记录，原有快照和异常语义保持。新方式同样按注册顺序同步执行：第一个 CANCEL 中止后续分发，异常保留原对象并继续向外传播，不吞错、不自动禁用，不自动重试。代理会解包反射异常；未在接口声明的 checked exception 仍可能被 Java Proxy 包装，建议使用接口声明的异常或 RuntimeException/Error。

本轮未增加通用网络指令、联机一致性握手或热卸载能力。模板已使用受管理订阅；工作区独立 v3.1 示例提供 F12 手动诊断探针，战役 schema 仍是 3，配置 schema 仍是 2。
