package net.fabricacs.api.impl;

import com.zarkonnen.airships.Lang;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;

public final class FabricModInstallBridge {
    private static final String FABRIC_MOD_JSON = "fabric.mod.json";

    private FabricModInstallBridge() {
    }

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

    public static InstallResult installIfFabricModJar(File source) {
        if (!isFabricModJar(source)) {
            return InstallResult.notHandled();
        }

        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
        Path target = modsDir.resolve(source.getName()).toAbsolutePath().normalize();
        if (!target.startsWith(modsDir)) {
            return InstallResult.handled(false, translate("unable_to_install_mod"));
        }

        try {
            Files.createDirectories(modsDir);
            if (Files.exists(target)) {
                return InstallResult.handled(false, translate("mod_already_installed"));
            }

            Files.copy(source.toPath(), target);
            return InstallResult.handled(true, "Fabric mod installed: " + source.getName() + "\nRestart Airships to enable it.");
        } catch (IOException e) {
            System.err.println("[Acbric API] Failed to install Fabric mod jar: " + source);
            e.printStackTrace();
            return InstallResult.handled(false, translate("unable_to_copy_mod"));
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
