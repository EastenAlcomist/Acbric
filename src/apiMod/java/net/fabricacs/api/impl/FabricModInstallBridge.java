/*
 * FabricModInstallBridge.java — 将游戏安装界面接入 Fabric JAR：暂存、校验元数据后发布，拒绝覆盖已有文件。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.Lang;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

public final class FabricModInstallBridge {
    private static final String FABRIC_MOD_JSON = "fabric.mod.json";

    private FabricModInstallBridge() {
    }

    /** 只识别 JAR 内是否存在元数据；存在不代表元数据有效。 */
    public static boolean isFabricModJar(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) {
            return false;
        }

        try (ZipFile zip = new ZipFile(file)) {
            return zip.getEntry(FABRIC_MOD_JSON) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** 尝试安装 Fabric JAR；非 Fabric 文件返回未处理，交还原版流程。 */
    public static InstallResult installIfFabricModJar(File source) {
        if (!isFabricModJar(source)) {
            return InstallResult.notHandled();
        }

        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        Path target = modsDir.resolve(source.getName()).toAbsolutePath().normalize();
        if (!target.startsWith(modsDir)) {
            return InstallResult.handled(false, translate("unable_to_install_mod"));
        }

        Path staging = null;
        try {
            Files.createDirectories(modsDir);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return InstallResult.handled(false, translate("mod_already_installed"));
            }

            // 在扫描目录之外校验实际要安装的副本；复制或解析失败时不发布 JAR。
            Path cache = FabricLoader.getInstance().getGameDir().resolve(".fabric");
            Files.createDirectories(cache);
            staging = Files.createTempDirectory(cache, "acbric-install-");
            Path candidate = staging.resolve("candidate.jar");
            Files.copy(source.toPath(), candidate);
            try (ZipFile zip = new ZipFile(candidate.toFile())) {
                if (zip.stream().filter(e -> FABRIC_MOD_JSON.equals(e.getName())).count() != 1) {
                    throw new IOException("Expected exactly one fabric.mod.json");
                }
                try (var metadata = zip.getInputStream(zip.getEntry(FABRIC_MOD_JSON))) {
                    // 复用固定 Loader 版本的 schema、ID 和版本校验；不解析依赖或执行 Mixin。
                    ModMetadataParser.parseMetadata(metadata, source.toString(), List.of(),
                            new VersionOverrides(), new DependencyOverrides(staging), false);
                }
            }
            Files.move(candidate, target);
            return InstallResult.handled(true, "Fabric mod installed: " + source.getName() + "\nRestart Airships to enable it.");
        } catch (ParseMetadataException e) {
            System.err.println("[Acbric API] Invalid Fabric metadata in " + source + ": " + e.getMessage());
            return InstallResult.handled(false, "Invalid Fabric mod metadata: " + source.getName() + "\n" + e.getMessage());
        } catch (IOException e) {
            System.err.println("[Acbric API] Failed to install Fabric mod jar: " + source);
            e.printStackTrace();
            return InstallResult.handled(false, translate("unable_to_copy_mod"));
        } finally {
            if (staging != null) {
                try {
                    Files.deleteIfExists(staging.resolve("candidate.jar"));
                    Files.delete(staging);
                } catch (IOException e) { System.err.println("[Acbric API] Failed to clean install staging: " + staging + ": " + e); }
            }
        }
    }

    private static String translate(String key) {
        return Lang._t(key, new Object[0]);
    }

    public static final class InstallResult {
        private static final InstallResult NOT_HANDLED = new InstallResult(false, false, null);

        private final boolean handled;
        private final boolean installed;
        private final String message;

        private InstallResult(boolean handled, boolean installed, String message) {
            this.handled = handled;
            this.installed = installed;
            this.message = message;
        }

        private static InstallResult notHandled() {
            return NOT_HANDLED;
        }

        private static InstallResult handled(boolean installed, String message) {
            return new InstallResult(true, installed, message);
        }

        public boolean isHandled() {
            return handled;
        }

        public boolean isInstalled() {
            return installed;
        }

        public String getMessage() {
            return message;
        }
    }
}
