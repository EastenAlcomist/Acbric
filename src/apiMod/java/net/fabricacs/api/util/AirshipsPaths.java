/*
 * AirshipsPaths.java — 提供 Fabric 游戏根目录下的路径与显式创建目录方法。
 * modsDir 是 Java MOD 目录，不是 AGame 用户数据下的原版 MOD 目录。
 */
package net.fabricacs.api.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AirshipsPaths {
    private AirshipsPaths() {
    }

    /** Fabric 识别的游戏工作目录，一般为运行包的 game。 */
    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** Java/Fabric JAR 的安装目录；原版 MOD 另由 AGame 定位。 */
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

    /** 按调用方给定 ID 拼路径，不会校验或清洗任意用户输入。 */
    public static Path modDataDir(String modId) {
        return gameDir().resolve("data").resolve("acbric").resolve(modId);
    }

    /** 显式递归创建目录，将 IOException 包装为未检查异常。 */
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
