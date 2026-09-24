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

    public Path configDir() {
        return AirshipsPaths.modConfigDir(modId());
    }

    public Path ensureConfigDir() {
        return AirshipsPaths.ensureModConfigDir(modId());
    }

    public Path dataDir() {
        return AirshipsPaths.modDataDir(modId());
    }

    public Path ensureDataDir() {
        return AirshipsPaths.ensureModDataDir(modId());
    }

    public AcbricLogger logger() {
        return logger;
    }
}
