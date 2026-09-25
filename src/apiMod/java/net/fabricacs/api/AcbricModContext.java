/*
 * AcbricModContext.java — 封装当前 MOD 的元数据、配置/数据目录和日志；目录位置由 AirshipsPaths 定义。
 */
package net.fabricacs.api;

import net.fabricacs.api.util.AcbricLogger;
import net.fabricacs.api.save.CampaignData;
import net.fabricacs.api.config.ModConfig;
import net.fabricacs.api.config.ConfigException;
import net.fabricacs.api.event.EventScope;
import org.json.JSONObject;
import net.fabricacs.api.util.AirshipsPaths;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class AcbricModContext {
    private final ModContainer container;
    private final AcbricLogger logger;

    public AcbricModContext(ModContainer container) {
        this.container = Objects.requireNonNull(container, "container");
        this.logger = new AcbricLogger(modId());
    }

    public ModContainer container() {
        return container;
    }

    public ModMetadata metadata() {
        return container.getMetadata();
    }

    public String modId() {
        return metadata().getId();
    }

    public String modName() {
        String name = metadata().getName();
        return name == null || name.isBlank() ? modId() : name;
    }

    public String version() {
        return metadata().getVersion().getFriendlyString();
    }

    public String description() {
        String description = metadata().getDescription();
        return description == null ? "" : description;
    }

    public Optional<String> iconPath(int size) {
        return metadata().getIconPath(size);
    }

    /** 获取当前 MOD 的配置路径，不创建目录。 */
    public Path configDir() {
        return AirshipsPaths.modConfigDir(modId());
    }

    /** 创建并返回配置目录；I/O 失败包装为 UncheckedIOException。 */
    public Path ensureConfigDir() {
        return AirshipsPaths.ensureModConfigDir(modId());
    }

    /** 返回 game/data/acbric/modId；不是用户存档根目录。 */
    public Path dataDir() {
        return AirshipsPaths.modDataDir(modId());
    }

    /** 创建当前 MOD 的数据目录，不会主动载入其中的数据。 */
    public Path ensureDataDir() {
        return AirshipsPaths.ensureModDataDir(modId());
    }

    /** 当前 MOD 的公共界面入口；注册可在初始化时完成，打开窗口须在游戏线程。 */
    public net.fabricacs.api.ui.ModUi ui() { return new net.fabricacs.api.ui.ModUi(modId()); }

    public AcbricLogger logger() {
        return logger;
    }

    /** 创建独立订阅范围；同名也不会复用，关闭时机由 MOD 显式管理。 */
    public EventScope eventScope(String name) { return new EventScope(modId(), name); }

    /** 在 acbric 入口显式声明本 MOD 的玩法规则；本地 UI 偏好不应加入。 */
    public net.fabricacs.api.rules.SharedRules sharedRules(int version, JSONObject values, net.fabricacs.api.rules.SharedRules.Validator validator) {
        return net.fabricacs.api.impl.SharedRulesRegistry.register(modId(), version, values, validator);
    }

    /** 获取指定 WorldMap 的本 MOD 数据；写入不广播，须在模拟线程按同步规则调用。 */
    public CampaignData campaignData(Object worldMap) {
        return new CampaignData(worldMap, modId());
    }

    /** 创建配置句柄，不读写磁盘；名称不含扩展名，后续显式 load/reload/save。 */
    public ModConfig config(String name, int dataVersion, JSONObject defaults, ModConfig.Validator validator) throws ConfigException {
        return new ModConfig(AirshipsPaths.configDir(), modId(), name, dataVersion, defaults, validator);
    }
}
