# MOD 配置 API

[English](CONFIG.md) | **中文**

要求 `acbric_api >=0.3.3-dev.5`、JDK 21。配置文件位于 Fabric 游戏目录的 `config/<modId>/<name>.json`，与原生存档、原生 MOD 目录分开。通常即分发包的 `game/config/`。已有手工读写配置代码不受影响；框架不会扫描或接管它们。

## 使用方式

```java
import net.fabricacs.api.config.ModConfig;
import org.json.JSONObject;

// 在 context 入口内创建；构造只校验默认值，不创建目录、不读磁盘。
ModConfig config = context.config("settings", 2,
    new JSONObject().put("showHud", true).put("newCampaignIncrement", 1), data -> {
        Object n = data.get("newCampaignIncrement");
        if (!(data.get("showHud") instanceof Boolean) || !(n instanceof Integer)
                || (Integer) n < 1 || (Integer) n > 10) {
            throw new IllegalArgumentException("Invalid settings");
        }
    });
config.load();
config.migrate((previous, data) -> {
    if (previous != 1) throw new IllegalArgumentException("Unsupported version");
    if (data.has("step")) {
        data.put("newCampaignIncrement", data.get("step"));
        data.remove("step");
    }
    return data;
});
config.save(); // 首次创建、补默认字段、迁移都须显式保存。
JSONObject settings = config.read().data();
```

调用者处理 `IOException`（包括 `ConfigException`），决定记录错误、停用功能或保留上一份生效配置。不要捕获错误后无条件覆盖默认值。上例使用游戏随附的 `org.json`；`getInt/getBoolean` 会做类型转换，严格类型验证应检查 `get()` 的实际类型。

## 公开成员与状态

`net.fabricacs.api.config` 提供 `ModConfig`、`ConfigSnapshot`、`ConfigException`。常用入口是 `AcbricModContext.config(String name, int dataVersion, JSONObject defaults, ModConfig.Validator validator)`。独立工具/测试也可直接构造 `ModConfig(Path configRoot, String modId, String name, int dataVersion, JSONObject defaults, Validator validator)`。

| 成员 | 契约 |
| --- | --- |
| `path()` / `backupPath()` | 主文件 / 同目录 `.json.bak` 路径，不触发读写 |
| `load()` | 首次从磁盘加载；已加载则返回当前快照，不丢弃待保存修改 |
| `read()` | 当前快照；未加载时抛 `IllegalStateException` |
| `reload()` | 显式重新读取磁盘；成功替换内存并丢弃未保存修改；失败保留上次快照 |
| `update(JSONObject)` | 合并缺失默认字段、验证后仅替换内存，要求当前版本 |
| `migrate(Migration)` | 旧版本才执行回调；当前版本返回 false。成功转换至声明版本，仅更新内存 |
| `save()` | 显式提交当前版本；未加载或未迁移抛 `IllegalStateException` |
| `ConfigSnapshot.dataVersion()` / `data()` | 版本 / 新的独立 JSON 副本；修改副本不会影响配置 |

`ConfigSnapshot(int, JSONObject)` 也会验证版本及复制数据。`Validator.validate(JSONObject)` 和 `Migration.migrate(int previousVersion, JSONObject)` 均允许抛出异常；迁移必须返回对象。参数错误如负版本、非法名称是 `IllegalArgumentException`，空必需参数也可能是 `NullPointerException`。

文件不存在时生成当前版本的默认快照，**不自动保存**。当前版本文件递归补齐缺失字段；已有值为 null、类型错误或数组时不会被默认值覆盖，交给验证器决定是否合法。未知数据字段和未知封装字段保留，保存会重新排版，原始字节留在备份。

旧版本只做结构检查，不合并当前默认值、不调用当前验证器；MOD 必须显式迁移。回调接收副本，成功返回后才合并默认字段并执行验证。失败保留原内存和磁盘。更高版本直接拒绝加载，不允许降级覆盖。迁移流程可在一个回调内按旧版本依次升级，不自动选择路径。

## 格式和错误

```json
{
  "format": 1,
  "version": 2,
  "data": { "showHud": true, "newCampaignIncrement": 1 }
}
```

`format` 是框架封装版本，固定 1；`version` 是 MOD 自己的非负整数版本（最大 2147483647），与 MOD 发布版本无关。`data` 必须是 JSON 对象。UTF-8，接受 BOM；严格 JSON，不接受注释、重复键（含转义后重名）、尾逗号、单引号、尾部垃圾或非有限数值。数值沿用游戏 JSONObject 的 Integer/Long/Double 表示，超出 long 范围的大整数不保证精确，应使用字符串。文件读写最大 1 MiB；数据层使用与战役 JSON 相同的 64 层深度检查，封装解析另限制 72 层。不支持任意 Java 对象或循环引用。

配置名匹配 `[a-z][a-z0-9_-]{0,63}`，不带扩展名；MOD ID 沿用 2–64 字符规则；两者拒绝 Windows 保留设备名。拒绝符号链接、检测出的 junction/特殊路径，不把它当作对恶意本机进程的安全沙箱。

`ConfigException extends IOException`，可读取 `code()`；公开构造器为 `(Code, String)` 和 `(Code, String, Throwable)`。

| Code | 含义 |
| --- | --- |
| `INVALID_FORMAT` | UTF-8、JSON、封装、数据结构或深度非法 |
| `VALIDATION_FAILED` | 默认值、更新、当前版本加载或迁移结果未通过验证 |
| `NEWER_VERSION` | 磁盘版本高于声明版本 |
| `MIGRATION_FAILED` | 迁移回调异常、重入或返回无效 JSON |
| `CONFLICT` | 锁被占用，或磁盘内容与最后一次成功加载/保存的原始字节不同 |

文件权限、大小限制和原子移动失败等仍抛普通 `IOException`。校验器接收副本，其修改被丢弃；回调应无外部副作用，不能重入同一句柄的 load/reload/update/migrate/save。框架无法回滚回调在其他对象上的副作用。实例方法同步保护单句柄，不等于自动协调游戏线程或跨句柄事务。

## 保存与备份

同目录临时文件写完并 force 后，使用原子移动替换；不支持原子移动时失败，不退回直接截断主文件。替换现有主文件前，把其**原始字节**保存到 `.json.bak`，仅保留一代。备份写失败时不改主文件。字节完全不变的 save 不轮换备份。首次创建无旧文件可备份；不会清除已存在的历史备份。`.json.lock` 是正常保留的空锁文件。

锁协调使用本接口的写入者，提交时再次对比磁盘基线；外部编辑、删除文件后应 reload 再决定保存。外部编辑器不遵守此锁，检查与原子替换之间仍有窄竞态，尽量在没有同时保存时编辑。不保证断电事务、目录持久化或跨文件事务。极少数提交后关闭锁失败可能使调用报错而文件已替换，重读确认再重试。

不会自动从 `.bak` 恢复，不会把损坏文件悄悄换成默认值。人工恢复前退出游戏，保留损坏文件，再复制备份到主文件；重启或显式 reload。

## 本地偏好与战役参数

配置不是存档，不参加校验、不发送网络消息，也没有文件监视器或自动热重载事件。本地 UI/日志偏好可在 reload 后生效。玩法参数只在合适的战役创建阶段复制进 `CampaignData`；后续运行和加载读取存档里的值，不能每次加载都用本机配置重写。

双方 MOD 一样不代表配置一样。缺少权威参数传递时，不应让联机各端从本地文件生成共享状态。独立示例 v3 对新单机战役固化 `newCampaignIncrement`；旧战役迁移、加载缺失数据和新联机战役使用确定值 1，已有存档规则保留。示例 F11 只重载本地配置，F6/F8 联机写入仍被阻止；RESTORED 仍只重新绑定读取。

本轮不提供配置 GUI、自动广播、自动迁移或通用网络指令 API。参见 [战役数据](CAMPAIGN_DATA.zh-CN.md) 与 [生命周期](CAMPAIGN_LIFECYCLE.zh-CN.md)。
