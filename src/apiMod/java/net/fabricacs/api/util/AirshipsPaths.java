package net.fabricacs.api.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AirshipsPaths {
    private AirshipsPaths() {
    }

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Path modsDir() {
        return gameDir().resolve("mods");
    }

    public static Path staticDataDir() {
        return gameDir().resolve("data");
    }

    public static Path generatedDir() {
        return gameDir().resolve("generated");
    }

    public static Path cacheDir() {
        return gameDir().resolve(".fabric").resolve("acbric").resolve("cache");
    }

    public static Path modConfigDir(String modId) {
        return configDir().resolve(modId);
    }

    public static Path modDataDir(String modId) {
        return gameDir().resolve("data").resolve("acbric").resolve(modId);
    }

    public static Path ensureDirectory(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory: " + path, e);
        }
    }

    public static Path ensureConfigDir() {
        return ensureDirectory(configDir());
    }

    public static Path ensureModsDir() {
        return ensureDirectory(modsDir());
    }

    public static Path ensureGeneratedDir() {
        return ensureDirectory(generatedDir());
    }

    public static Path ensureCacheDir() {
        return ensureDirectory(cacheDir());
    }

    public static Path ensureModConfigDir(String modId) {
        return ensureDirectory(modConfigDir(modId));
    }

    public static Path ensureModDataDir(String modId) {
        return ensureDirectory(modDataDir(modId));
    }
}
