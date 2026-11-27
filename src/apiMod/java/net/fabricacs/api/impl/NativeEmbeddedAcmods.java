/*
 * NativeEmbeddedAcmods.java — 缓存原版 MOD 携带的 ACMOD，并在原版列表行中绘制、安装和启用。
 * 扫描只在 MOD 列表刷新/打开时进行，绘制热路径不重新读取 JAR 或配置。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.AGame;
import com.zarkonnen.airships.Lang;
import com.zarkonnen.airships.Mod;
import com.zarkonnen.airships.MyDraw;
import net.fabricacs.api.util.AcbricLanguage;
import net.fabricacs.api.util.AcbricLogger;
import net.fabricacs.api.util.AirshipsPaths;
import net.fabricacs.management.ModSelection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class NativeEmbeddedAcmods {
    private static final AcbricLogger LOGGER = new AcbricLogger("acbric_api");
    private static volatile Map<Path, Carrier> carriers = Map.of();

    private NativeEmbeddedAcmods() {
    }

    /** 本地目录和 Steam 下载目录都参与；未携带 AcbricMod 的原版 MOD 不受影响。 */
    public static synchronized void refresh(List<Mod> mods) {
        Map<Path, Carrier> next = new HashMap<>();
        Set<String> disabled = Set.of();
        String configProblem = "";
        try {
            disabled = ModSelection.read(ModSelection.file(AirshipsPaths.configDir())).disabled();
        } catch (IOException | RuntimeException error) {
            configProblem = "Cannot read the Acbric disabled-MOD configuration: " + error.getMessage();
            LOGGER.error(configProblem, error);
        }

        Set<String> externalDisabled;
        try {
            externalDisabled = ModSelection.ids(System.getProperty(ModSelection.EXTERNAL_PROPERTY));
        } catch (IOException | RuntimeException error) {
            externalDisabled = Set.of();
            configProblem = "Cannot read launch-argument disabled MODs: " + error.getMessage();
            LOGGER.error(configProblem, error);
        }

        for (Mod mod : mods) {
            if (mod == null || mod.dir == null) continue;
            try {
                var source = NativeEmbeddedAcmodScanner.find(mod.dir.toPath());
                if (source.isEmpty()) continue;
                var found = source.get();
                Path target = AirshipsPaths.modsDir().resolve(found.jar().getFileName()).normalize();
                CarrierState state = inspect(found, target, disabled, externalDisabled, configProblem);
                Path key = mod.dir.toPath().toAbsolutePath().normalize();
                next.put(key, new Carrier(key, found.jar(), target, found.modId(), sha256(found.jar()), state));
            } catch (IOException | RuntimeException error) {
                LOGGER.error("Cannot inspect bundled ACMOD in " + mod.dir, error);
            }
        }

        carriers = Map.copyOf(next);
        if (!next.isEmpty()) LOGGER.info("Detected " + next.size() + " native MOD(s) carrying AcbricMOD.");
    }

    private static CarrierState inspect(
            NativeEmbeddedAcmodScanner.CarrierSource source,
            Path target,
            Set<String> disabled,
            Set<String> externalDisabled,
            String configProblem) throws IOException {
        if (!configProblem.isBlank()) return new CarrierState(State.ERROR, configProblem);
        if (externalDisabled.contains(source.modId())) {
            return new CarrierState(State.EXTERNAL_DISABLED,
                    "This ACMOD is disabled by a launch argument and cannot be enabled here.");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return new CarrierState(State.READY, "");
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !sha256(source.jar()).equals(sha256(target))) {
            return new CarrierState(State.CONFLICT, "A different file already exists at " + target);
        }
        if (disabled.contains(source.modId())) {
            return new CarrierState(State.READY,
                    "The ACMOD is installed but disabled in Acbric; enable it on restart.");
        }
        return new CarrierState(State.ENABLED, "");
    }

    public static Carrier carrier(Mod mod) {
        if (mod == null || mod.dir == null) return null;
        return carriers.get(mod.dir.toPath().toAbsolutePath().normalize());
    }

    public static String suffix(Mod mod) {
        return carrier(mod) == null ? "" : localized(" carries AcbricMOD", " 携带AcbricMOD");
    }

    public static int extraHeight(Mod mod) {
        return carrier(mod) == null ? 0 : MyDraw.BUTTON_H + MyDraw.UI_SPACING;
    }

    /** 按钮位于原版名称/状态占用高度之后；额外高度由适配器的 getHeight RETURN 注入预留。 */
    public static void drawAction(
            Mod mod,
            MyDraw draw,
            int x,
            int y,
            int width,
            Consumer<String> report) {
        Carrier carrier = carrier(mod);
        if (carrier == null) return;

        String label = label(carrier.state());
        int logoHeight = AGame.BIG_FOUNT.height + AGame.FOUNT.height;
        int textX = x + logoHeight + MyDraw.UI_SPACING;
        int available = Math.max(1, width - logoHeight - MyDraw.UI_SPACING * 2);
        int buttonWidth = Math.min(available, draw.bw(label));
        int actionY = y + originalRowHeight(mod, draw, width);
        boolean actionable = carrier.state().state() == State.READY;

        draw.button(textX, actionY, buttonWidth, label, () -> {
            Activation result = activate(carrier);
            if (!result.success()) report.accept(result.message());
        }, actionable);
        draw.tooltip(textX, actionY, buttonWidth, MyDraw.BUTTON_H, tooltip(carrier));
    }

    private static int originalRowHeight(Mod mod, MyDraw draw, int width) {
        int logoHeight = AGame.BIG_FOUNT.height + AGame.FOUNT.height;
        int textWidth = width - logoHeight - MyDraw.UI_SPACING * 2
                - draw.tw(Lang._t("Enabled")) - MyDraw.ICON_BUTTON_SZ - MyDraw.BUTTON_SPACING;
        int nameHeight = (int) draw.textSize(mod.getName() + suffix(mod),
                AGame.BIG_FOUNT, 0, 0, Math.max(1, textWidth)).height;
        String status = nativeStatus(mod);
        int statusHeight = status.isEmpty() ? 0 : (int) draw.textSize(
                status, AGame.FOUNT, 0, 0, Math.max(1, textWidth)).height;
        return Math.max(logoHeight, nameHeight + statusHeight) + MyDraw.SCROLL_EL_SPACING;
    }

    private static String nativeStatus(Mod mod) {
        if (mod.preemptedBy != null) {
            return "[bb421d]" + Lang._t("mod_preempted", mod.preemptedBy.getName());
        }
        if (mod.loadFailed) return "[bb421d]" + Lang._t("mod_loading_failed");
        return mod.isCurrentlyEnabled() ? Lang._t("Enabled") : "";
    }

    private static synchronized Activation activate(Carrier carrier) {
        if (carrier.state().state() == State.ENABLED) return new Activation(true, "");
        if (carrier.state().state() == State.CONFLICT
                || carrier.state().state() == State.EXTERNAL_DISABLED) {
            return new Activation(false, tooltip(carrier));
        }
        if (carrier.state().state() == State.ERROR) {
            return new Activation(false, carrier.state().detail());
        }

        try {
            if (!Files.exists(carrier.target(), LinkOption.NOFOLLOW_LINKS)) {
                FabricModInstallBridge.InstallResult installed =
                        FabricModInstallBridge.installIfFabricModJar(carrier.source().toFile());
                if (!installed.isInstalled()) return new Activation(false, installed.getMessage());
            }
            if (!Files.isRegularFile(carrier.target(), LinkOption.NOFOLLOW_LINKS)
                    || !carrier.sourceHash().equals(sha256(carrier.target()))) {
                return new Activation(false, localized(
                        "The copied ACMOD does not match its source file: ",
                        "复制的 ACMOD 与源文件不一致：") + carrier.target());
            }

            enableSelection(carrier.modId());
            JavaModManager.refresh();
            Carrier enabled = new Carrier(carrier.modDirectory(), carrier.source(), carrier.target(),
                    carrier.modId(), carrier.sourceHash(), new CarrierState(State.ENABLED, ""));
            Map<Path, Carrier> next = new HashMap<>(carriers);
            next.put(enabled.modDirectory(), enabled);
            carriers = Map.copyOf(next);
            LOGGER.info("Enabled bundled ACMOD " + carrier.modId()
                    + " from " + carrier.modDirectory() + " into " + carrier.target());
            return new Activation(true, "");
        } catch (IOException | RuntimeException error) {
            String message = localized("Cannot enable bundled ACMOD: ", "无法启用携带的 ACMOD：")
                    + error.getMessage();
            LOGGER.error(message, error);
            return new Activation(false, message);
        }
    }

    /** 复制和停用配置写入无法组成单一事务；配置失败时保留“启用”状态以允许重试。 */
    private static void enableSelection(String modId) throws IOException {
        Path config = ModSelection.file(AirshipsPaths.configDir());
        ModSelection.Snapshot snapshot = ModSelection.read(config);
        if (!snapshot.disabled().contains(modId)) return;
        Set<String> next = new java.util.TreeSet<>(snapshot.disabled());
        next.remove(modId);
        ModSelection.write(config, snapshot, next);
    }

    private static String label(CarrierState state) {
        return switch (state.state()) {
            case READY -> localized("Enable bundled ACMOD", "启用携带ACMOD");
            case ENABLED -> localized("Enabled", "已启用");
            case EXTERNAL_DISABLED -> localized("Disabled at launch", "启动参数停用");
            case CONFLICT -> localized("File conflict", "文件冲突");
            case ERROR -> localized("Unavailable", "不可用");
        };
    }

    private static String tooltip(Carrier carrier) {
        return switch (carrier.state().state()) {
            case READY -> carrier.state().detail().isBlank()
                    ? localized("Copy into Acbric's MOD folder and enable it; takes effect after restart.",
                            "复制到 Acbric 的 MOD 文件夹并启用；重启游戏后生效。")
                    : localized("The ACMOD is installed but disabled in Acbric; click to enable it for the next restart.",
                            "该 ACMOD 已复制，但在 Acbric 中处于停用状态；点击后将在重启时启用。");
            case ENABLED -> localized("The ACMOD is in Acbric's MOD folder. Restart the game if it is not loaded yet.",
                    "ACMOD 已位于 Acbric 的 MOD 文件夹；若尚未加载，请重启游戏。");
            case EXTERNAL_DISABLED -> localized("This ACMOD is disabled by a launch argument and cannot be enabled here.",
                    "该 ACMOD 已被启动参数停用，无法在此启用。");
            case CONFLICT -> localized("A different file with the same name already exists: ",
                    "目标位置已有内容不同的同名文件：") + carrier.target();
            case ERROR -> localized("Action unavailable: ", "操作不可用：") + carrier.state().detail();
        };
    }

    private static String localized(String english, String chinese) {
        return AcbricLanguage.isChinese() ? chinese : english;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    public record Carrier(Path modDirectory, Path source, Path target, String modId,
                          String sourceHash, CarrierState state) {
    }

    public record CarrierState(State state, String detail) {
    }

    public enum State {
        READY,
        ENABLED,
        EXTERNAL_DISABLED,
        CONFLICT,
        ERROR
    }

    private record Activation(boolean success, String message) {
    }
}
