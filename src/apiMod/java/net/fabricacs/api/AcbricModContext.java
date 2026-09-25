/*
 * AcbricModContext.java — 封装当前 MOD 的元数据、配置/数据目录和日志；目录位置由 AirshipsPaths 定义。
 */
package net.fabricacs.api;

import net.fabricacs.api.util.AcbricLogger;
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

    public AcbricLogger logger() {
        return logger;
    }
}
