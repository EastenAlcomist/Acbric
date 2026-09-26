/* InstanceSetupRegression.java — 配置事务、旧目录拒绝、并发保护及中英文界面的隔离回归。 */
package net.fabricacs.acbric;

import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.zip.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.channels.FileChannel;
import com.sun.nio.file.ExtendedOpenOption;

public final class InstanceSetupRegression {
    private static int checks;
    private interface Action { void run() throws Exception; }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; System.out.println("PASS setup: " + label); }
    private static void reject(String code, Action action) throws Exception {
        try { action.run(); } catch (Exception ex) { check(ex.toString().contains(code), code); return; }
        throw new AssertionError("Expected " + code);
    }
    private static Path bundle(Path root) throws Exception {
        Files.createDirectories(root.resolve("core")); Files.createDirectories(root.resolve("loader-libs"));
        Path api = root.resolve("core/acbric-api.jar"), loader = root.resolve("loader-libs/launcher.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(api))) {
            for (String name : List.of("fabric.mod.json", "net/fabricacs/api/mixin/ExternalPathsMixin.class", "net/fabricacs/api/mixin/ExternalMainMixin.class",
                    "net/fabricacs/api/mixin/ExternalLaunchSettingsMixin.class", "net/fabricacs/api/impl/ExternalTextureCache.class")) {
                zip.putNextEntry(new ZipEntry(name)); zip.write(0); zip.closeEntry();
            }
        }
        Files.writeString(loader, "loader fixture");
        Files.writeString(root.resolve("Start Acbric.cmd"), "root entry fixture");
        Files.writeString(root.resolve("bundle.properties"), "schema=1\napi.version=" + ExternalLauncher.CORE_VERSION
                + "\nsha256.core/acbric-api.jar=" + ExternalLauncher.sha256(api) + "\nsha256.loader-libs/launcher.jar=" + ExternalLauncher.sha256(loader));
        return root;
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        Path game = ExternalInstallRegression.installation(root.resolve("游戏 输入")), framework = bundle(root.resolve("框架 & 配置")), instance = root.resolve("实例 中文 & x");
        var preview = InstanceSetup.preview(framework, game, instance, "zh");
        check(!Files.exists(instance) && !Files.exists(framework.resolve("mods")), "preview does not create instance or MOD folders");
        Path rootEntry = InstanceSetup.save(preview);
        check(rootEntry.equals(framework.resolve("Start Acbric.cmd")), "setup returns root launcher beside Setup");
        check(FrameworkEntry.resolve(framework, FrameworkEntry.read(framework)).equals(instance), "root launcher bound to saved external instance");
        Path entry = instance.resolve("acbric-launcher/Start Acbric.cmd");
        check(Files.isRegularFile(entry), "managed launcher directory published");
        check(InstanceSetup.read(instance).game().equals(game) && InstanceSetup.read(instance).framework().equals(framework), "Unicode and special paths round trip as JSON data");
        Path mods = framework.resolve("mods");
        check(Files.isDirectory(mods) && Files.isDirectory(instance.resolve("userdata")) && !Files.exists(instance.resolve("mods")), "MOD directory beside Setup; no instance mods created");
        check(ExternalMods.directory(framework, preview.plan()).equals(mods), "MOD path independent of working directory");
        Files.writeString(mods.resolve("preserved.txt"), "existing MOD");
        try (var shared = ExternalMods.open(mods, preview.plan())) {
            reject("MODS_BUSY", () -> InstanceSetup.save(InstanceSetup.preview(framework, game, root.resolve("second-instance"), "zh")));
        }
        check(Files.readString(mods.resolve("preserved.txt")).equals("existing MOD"), "shared lock does not alter MOD files");
        reject("MODS_OVERLAP", () -> ExternalMods.validate(game.resolve("mods"), preview.plan()));
        reject("MODS_OVERLAP", () -> ExternalMods.validate(instance, preview.plan()));
        Path blockedMods = Files.writeString(root.resolve("not-mods"), "preserve");
        reject("MODS_UNAVAILABLE", () -> ExternalMods.validate(blockedMods, preview.plan()));
        check(!Files.exists(instance.resolve("asplit-A.zip")) && !Files.exists(instance.resolve("mods/acbric-api.jar")), "game and core never copied");
        byte[] script = Files.readAllBytes(entry.getParent().resolve("launch.ps1"));
        check(Arrays.equals(script, InstanceSetup.script("launch.ps1")), "generated scripts contain no interpolated local paths");
        Path saved = Files.writeString(instance.resolve("userdata/save-marker.txt"), "preserve old save");
        var first = InstanceSetup.preview(framework, game, instance, "en");
        InstanceSetup.save(first);
        check(InstanceSetup.read(instance).language().equals("en") && Files.readString(saved).equals("preserve old save"), "reconfigure preserves user content");
        reject("SETUP_CONFLICT", () -> InstanceSetup.save(first));
        var busy = InstanceSetup.preview(framework, game, instance, "zh");
        try (var lease = ExternalPreflight.InstanceLease.open(busy.plan())) {
            reject("INSTANCE_BUSY", () -> InstanceSetup.save(busy));
        }
        check(InstanceSetup.read(instance).language().equals("en"), "lock failure preserves previous config");
        Files.writeString(entry, "user script");
        reject("ENTRY_MODIFIED", () -> InstanceSetup.preview(framework, game, instance, "en"));
        check(Files.readString(entry).equals("user script"), "modified entry is never overwritten");
        Files.write(entry, InstanceSetup.script("Start Acbric.cmd"));
        Path config = entry.getParent().resolve("instance.json"); String original = Files.readString(config);
        Files.writeString(config, "{broken");
        reject("INSTANCE_CONFIG_INVALID", () -> InstanceSetup.preview(framework, game, instance, "en"));
        reject("Corrupt instance configuration", () -> InstanceSetup.loadSaved(instance));
        check(Files.readString(config).equals("{broken"), "corrupt config preserved"); Files.writeString(config, original);
        Files.writeString(config, "x".repeat(65537));
        reject("exceeds 64 KiB", () -> InstanceSetup.loadSaved(instance));
        check(Files.size(config) == 65537, "oversized saved config is preserved");
        Files.delete(config);
        reject("Missing or non-file", () -> InstanceSetup.loadSaved(instance));
        check(!Files.exists(config) && Files.readString(saved).equals("preserve old save"), "missing managed config is not recreated over existing user data");
        Files.writeString(config, original);
        Path old = root.resolve("old-layout"); Files.createDirectories(old); Files.writeString(old.resolve("player.txt"), "leave alone");
        reject("INSTANCE_NOT_FRESH", () -> InstanceSetup.preview(framework, game, old, "zh"));
        reject("INSTANCE_NOT_FRESH", () -> InstanceSetup.loadSaved(old));
        check(Files.readString(old.resolve("player.txt")).equals("leave alone"), "legacy data not adopted");
        reject("OVERLAPPING_PATHS", () -> InstanceSetup.preview(framework, game, game.resolve("instance"), "zh"));
        reject("INSTANCE_OVERLAPS_FRAMEWORK", () -> InstanceSetup.preview(framework, game, framework.resolve("docs/test"), "zh"));
        reject("LANGUAGE_INVALID", () -> InstanceSetup.preview(framework, game, instance, "fr"));
        Path retry = root.resolve("retry"); Files.createDirectories(retry.resolve("mods")); Files.writeString(retry.resolve(".acbric-instance.lock"), "");
        check(Files.isRegularFile(InstanceSetup.save(InstanceSetup.preview(framework, game, retry, "zh"))), "empty partial initialization can retry");
        Path empty = root.resolve("empty-unconfigured"); Files.createDirectories(empty);
        check(InstanceSetup.loadSaved(empty).isEmpty() && !Files.exists(empty.resolve("acbric-launcher")), "empty instance has no saved setup and load remains read-only");
        Path notDirectory = Files.writeString(root.resolve("instance-file"), "preserve");
        reject("INSTANCE_CONFIG_INVALID", () -> InstanceSetup.loadSaved(notDirectory));
        Path internal = framework.resolve("instances/内置 & 实例");
        var beforeOtherSave = InstanceSetup.preview(framework, game, instance, "zh");
        InstanceSetup.save(InstanceSetup.preview(framework, game, internal, "zh"));
        check(FrameworkEntry.resolve(framework, FrameworkEntry.read(framework)).equals(internal)
                && !FrameworkEntry.read(framework).contains(framework.toString().replace("\\", "\\\\")), "internal binding is relative and newest save becomes default");
        reject("SETUP_CONFLICT", () -> InstanceSetup.save(beforeOtherSave));
        check(FrameworkEntry.resolve(framework, FrameworkEntry.read(framework)).equals(internal), "stale preview cannot change default instance");
        String active = FrameworkEntry.read(framework);
        Files.writeString(framework.resolve(FrameworkEntry.CONFIG), "{broken");
        reject("LAUNCH_CONFIG_INVALID", () -> InstanceSetup.preview(framework, game, instance, "zh"));
        check(Files.readString(framework.resolve(FrameworkEntry.CONFIG)).equals("{broken"), "corrupt root binding preserved");
        Files.writeString(framework.resolve(FrameworkEntry.CONFIG), active);
        reject("LAUNCH_CONFIG_INVALID", () -> FrameworkEntry.resolve(framework, "{\"schema\":\"1\",\"instanceDir\":\"../escape\"}"));
        reject("LAUNCH_CONFIG_INVALID", () -> FrameworkEntry.resolve(framework, "{\"schema\":\"1\",\"schema\":\"1\",\"instanceDir\":\"instances/default\"}"));
        publication(root);
        ui(root, framework, game);
        return checks;
    }

    /** 使用 Windows 的真实禁止删除共享句柄，覆盖临时占用、持续拒绝和重试时目标冲突。 */
    private static void publication(Path root) throws Exception {
        Path source = Files.createDirectory(root.resolve("publish-source"));
        Path target = root.resolve("publish-target");
        Files.writeString(source.resolve("instance.json"), "preserve");
        SetupDirectoryPublisher.publish(source, target);
        check(!Files.exists(source) && Files.readString(target.resolve("instance.json")).equals("preserve"), "directory publication retains staged contents");
        Files.createDirectory(source); Files.writeString(source.resolve("source.txt"), "source");
        reject("SETUP_TARGET_CONFLICT", () -> SetupDirectoryPublisher.publish(source, target));
        check(Files.readString(target.resolve("instance.json")).equals("preserve") && Files.exists(source.resolve("source.txt")), "existing publication target and source are preserved");
        if (!System.getProperty("os.name").startsWith("Windows")) {
            System.out.println("SKIP setup: Windows delete-sharing checks require Windows"); return;
        }
        Path held = Files.createDirectory(root.resolve("publish-held"));
        Path script = Files.writeString(held.resolve("Start Acbric.cmd"), "rem held");
        Path published = root.resolve("publish-recovered");
        int[] pauses = {0};
        try (var handle = FileChannel.open(script, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            SetupDirectoryPublisher.publish(held, published, millis -> {
                pauses[0]++;
                try { handle.close(); } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            });
        }
        check(pauses[0] == 1 && !Files.exists(held) && Files.readString(published.resolve("Start Acbric.cmd")).equals("rem held"), "real Windows occupancy recovers after handle release on retry");
        Path denied = Files.createDirectory(root.resolve("publish-denied"));
        Path deniedScript = Files.writeString(denied.resolve("Start Acbric.cmd"), "rem denied");
        Path deniedTarget = root.resolve("publish-denied-target");
        try (var handle = FileChannel.open(deniedScript, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            java.util.ArrayList<Long> delays = new java.util.ArrayList<>();
            try {
                SetupDirectoryPublisher.publish(denied, deniedTarget, delays::add);
                throw new AssertionError("Expected persistent publication failure");
            } catch (java.io.IOException ex) {
                check(ex.getMessage().contains("SETUP_PUBLISH_DENIED") && ex.getMessage().contains("targetExists=false")
                        && ex.getCause() instanceof AccessDeniedException && ex.getSuppressed().length == 1, "persistent denial retains paths, final and initial native failures");
            }
            check(delays.equals(List.of(100L, 200L, 400L, 800L, 1600L)), "retry wait is bounded to 3.1 seconds");
            check(Files.readString(deniedScript).equals("rem denied") && !Files.exists(deniedTarget), "persistent denial does not publish partial target");
            try {
                SetupDirectoryPublisher.publish(denied, deniedTarget, millis -> { throw new InterruptedException("cancel fixture"); });
                throw new AssertionError("Expected interrupted publication");
            } catch (java.io.InterruptedIOException ex) {
                check(Thread.currentThread().isInterrupted() && ex.getSuppressed()[0] instanceof AccessDeniedException, "interrupted retry preserves cancellation and original denial");
            } finally { Thread.interrupted(); }
            reject("SETUP_TARGET_CONFLICT", () -> SetupDirectoryPublisher.publish(denied, deniedTarget, millis -> {
                try { Files.createDirectory(deniedTarget); Files.writeString(deniedTarget.resolve("other.txt"), "other instance"); }
                catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            }));
            check(Files.readString(deniedTarget.resolve("other.txt")).equals("other instance"), "target created during retry is never overwritten");
        }
    }
    private static void ui(Path root, Path framework, Path game) throws Exception {
        InstallerPanel[] value = new InstallerPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            InstallerPanel panel = value[0] = new InstallerPanel(framework);
            panel.game.setText(game.toString()); panel.instance.setText(root.resolve("ui-instance").toString());
            check(panel.check.getText().equals("检查目录") && !panel.save.isEnabled(), "Chinese defaults, save requires preview");
            check(panel.steam.getText().equals("从 Steam 查找游戏") && panel.steam.isEnabled(), "Chinese Steam lookup button available");
            paint(panel, root.resolve("installer-zh.png"));
            panel.load.doClick();
        });
        await(value[0]);
        SwingUtilities.invokeAndWait(() -> {
            InstallerPanel panel = value[0];
            check(panel.status.getText().contains("此目录尚未保存配置") && !panel.save.isEnabled() && !panel.play.isEnabled(), "fresh load shows Chinese first-time guidance without enabling unchecked actions");
            check(panel.game.getText().equals(game.toString()) && !Files.exists(root.resolve("ui-instance")), "fresh load keeps selected game path and creates no instance");
            panel.language.setSelectedIndex(1);
            check(panel.check.getText().equals("Check folders") && panel.game.getText().equals(game.toString()), "English switching preserves paths");
            check(panel.steam.getText().equals("Find game in Steam"), "English Steam lookup button available");
            paint(panel, root.resolve("installer-en.png")); panel.load.doClick();
        });
        await(value[0]);
        SwingUtilities.invokeAndWait(() -> {
            InstallerPanel panel = value[0];
            check(panel.status.getText().contains("No saved setup") && panel.status.getText().contains("Check folders → Save"), "fresh load shows English first-time guidance");
            panel.check.doClick();
            check(panel.busy() && !panel.game.isEnabled(), "background inspection disables editing");
        });
        await(value[0]);
        SwingUtilities.invokeAndWait(() -> { check(value[0].save.isEnabled(), "successful preview enables saving"); value[0].save.doClick(); });
        await(value[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(value[0].play.isEnabled() && Files.isRegularFile(root.resolve("ui-instance/acbric-launcher/instance.json")), "UI save publishes entry and enables launch");
            check(value[0].openMods.isEnabled() && value[0].status.getText().contains(framework.resolve("mods").toString()), "saved UI shows exact MOD path and enables folder button");
            value[0].game.setText("changed");
            check(!value[0].save.isEnabled() && !value[0].play.isEnabled(), "editing invalidates checked/saved actions");
            value[0].load.doClick();
        });
        await(value[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(value[0].game.getText().equals(game.toString()) && value[0].status.getText().contains("Instance loaded"), "loading saved setup restores the game folder");
            check(!value[0].save.isEnabled() && !value[0].play.isEnabled(), "loaded saved setup still requires check and save");
        });
    }
    private static void await(InstallerPanel panel) throws Exception {
        for (int i = 0; i < 100; i++) {
            boolean[] busy = new boolean[1]; SwingUtilities.invokeAndWait(() -> busy[0] = panel.busy());
            if (!busy[0]) return; Thread.sleep(50);
        }
        throw new AssertionError("UI worker timeout");
    }
    private static void layout(Container container) { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested); }
    private static void paint(InstallerPanel panel, Path output) {
        panel.setSize(panel.getPreferredSize()); layout(panel);
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        try { ImageIO.write(image, "png", output.toFile()); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
}
