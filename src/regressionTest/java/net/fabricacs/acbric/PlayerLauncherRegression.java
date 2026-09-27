/* PlayerLauncherRegression.java — 玩家启动流程与诊断导出的隔离回归；界面后台任务使用真实配置服务。 */
package net.fabricacs.acbric;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class PlayerLauncherRegression {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; System.out.println("PASS player: " + label); }
    private interface Action { void run() throws Exception; }
    private static void reject(String code, Action action) throws Exception {
        try { action.run(); } catch (IOException ex) { check(ex.toString().contains(code), code); return; }
        throw new AssertionError("Expected " + code);
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        Path game = ExternalInstallRegression.installation(root.resolve("游戏 & install"));
        Path bundle = InstanceSetupRegression.bundle(root.resolve("Acbric & 中文"));
        Files.writeString(bundle.resolve("Acbric.exe"), "fixture");
        var service = new PlayerLauncherService(bundle);
        var initial = service.inspect();
        check(!initial.ready() && initial.game() == null && !Files.exists(initial.instance()), "first open does not create or adopt an instance");
        check(service.language().equals("zh"), "first language defaults to Chinese");
        var ready = service.configure(initial, game, "zh");
        check(ready.ready() && ready.game().equals(game), "one configure action checks and saves the selected game");
        check(InstanceSetup.save(InstanceSetup.preview(bundle, game, ready.instance(), "zh")).equals(bundle.resolve("Acbric.exe")), "player package returns unified EXE entry");
        check(new PlayerLauncherService(bundle).inspect().ready(), "next launcher reads saved setup automatically");
        Path descriptor = game.resolve("Airships.json"); String descriptorText = Files.readString(descriptor);
        Files.delete(descriptor);
        check(!service.inspect().ready() && service.inspect().instance().equals(ready.instance()), "missing game preserves original instance and requires reselection");
        Files.writeString(descriptor, descriptorText);
        service.language("en");
        check(new PlayerLauncherService(bundle).language().equals("en"), "launcher language persists without changing campaign data");
        reject("LANGUAGE_INVALID", () -> service.language("fr"));
        String binding = Files.readString(bundle.resolve(FrameworkEntry.CONFIG));
        Files.writeString(bundle.resolve(FrameworkEntry.CONFIG), "{broken");
        reject("LAUNCH_CONFIG_INVALID", service::inspect);
        check(Files.readString(bundle.resolve(FrameworkEntry.CONFIG)).equals("{broken"), "damaged binding is preserved, not reset into a new instance");
        Files.writeString(bundle.resolve(FrameworkEntry.CONFIG), binding);
        Path relocated = root.resolve("relocated");
        try (var files = Files.walk(bundle)) {
            for (Path p : files.toList()) { Path to = relocated.resolve(bundle.relativize(p)); if (Files.isDirectory(p)) Files.createDirectories(to); else Files.copy(p, to); }
        }
        var moved = new PlayerLauncherService(relocated);
        check(!moved.inspect().ready() && moved.inspect().game().equals(game), "relocated framework retains game and instance for reconfiguration");
        check(moved.configure(moved.inspect(), game, "en").ready(), "confirming game rebinds relocated framework");
        var diagnostics = new LauncherDiagnostics(root.resolve("launcher-logs"));
        diagnostics.record(new IOException("secret-native-path / 原始异常"));
        check(diagnostics.details().contains("secret-native-path"), "technical details retained locally");
        check(!LauncherDiagnostics.friendly(new IOException("secret-native-path"), true).contains("secret-native-path"), "normal error does not expose stack or path");
        check(LauncherDiagnostics.friendly(new IOException("SETUP_PUBLISH_DENIED"), false).contains("saved"), "English friendly save failure available");
        Path secret = Files.writeString(ready.instance().resolve("userdata/private-save.txt"), "never-export-this");
        Path report = root.resolve("diagnostics.zip"); diagnostics.export(report, ready.instance());
        try (ZipFile zip = new ZipFile(report.toFile())) {
            check(zip.getEntry("about.txt") != null && zip.stream().anyMatch(e -> e.getName().startsWith("logs/")), "diagnostics export includes logs and version");
            check(zip.stream().noneMatch(e -> e.getName().contains("userdata") || e.getName().contains("private-save")), "diagnostics export excludes saves");
        }
        byte[] firstReport = Files.readAllBytes(report);
        reject("FileAlreadyExists", () -> diagnostics.export(report, ready.instance()));
        check(java.util.Arrays.equals(firstReport, Files.readAllBytes(report)) && Files.readString(secret).equals("never-export-this"), "existing report and saves are not overwritten");
        Files.writeString(bundle.resolve("maintenance.ps1"), "fixture"); Files.writeString(bundle.resolve("player-maintenance.ps1"), "fixture");
        Path job = service.maintenance(report, false, "en");
        String request = Files.readString(job.resolve("request.json"));
        check(request.contains("\"parent\":" + ProcessHandle.current().pid()) && request.contains("\"restore\":false") && request.contains("\"language\":\"en\""), "maintenance handoff records parent and chosen action as data");
        check(Files.readString(job.resolve("maintenance.ps1")).equals("fixture"), "maintenance executes from independent temporary copy");
        ui(root, game);
        return checks;
    }

    private static void ui(Path root, Path game) throws Exception {
        Path bundle = InstanceSetupRegression.bundle(root.resolve("UI"));
        int[] launched = {0};
        var service = new PlayerLauncherService(bundle) {
            List<Path> discover() { return List.of(game); }
            int launch() { launched[0]++; return 0; }
        };
        var diagnostics = new LauncherDiagnostics(root.resolve("UI-logs"));
        PlayerLauncherPanel[] panel = new PlayerLauncherPanel[1];
        SwingUtilities.invokeAndWait(() -> { panel[0] = new PlayerLauncherPanel(service, diagnostics, job -> {}); panel[0].refresh(); });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            var p = panel[0];
            check(p.primary.getText().equals("使用此游戏") && p.state.game().equals(game) && !p.mods.isEnabled(), "first screen offers one setup action after discovery");
            paint(p, root.resolve("first-zh.png")); p.primary.doClick();
            check(p.busy() && !p.settings.isEnabled(), "setup blocks conflicting actions while working");
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            var p = panel[0]; check(p.primary.getText().equals("开始游戏") && p.mods.isEnabled() && !p.browse.isVisible(), "saved setup becomes a simple playable home");
            paint(p, root.resolve("home-zh.png")); p.primary.doClick();
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> { check(launched[0] == 1 && panel[0].primary.isEnabled(), "game return restores launcher actions"); panel[0].language.setSelectedIndex(1); });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].primary.getText().equals("Start game"), "home translates to English"); paint(panel[0], root.resolve("home-en.png"));
            panel[0].state = new PlayerLauncherService.State(panel[0].state.instance(), root.resolve("missing-game"), false);
            panel[0].configure(root.resolve("missing-game"));
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].retry.isVisible() && !panel[0].status.getText().contains("IOException") && !panel[0].status.getText().contains("INSTALL_NOT_FOUND"), "failed setup shows retry and friendly explanation without technical error");
            check(!panel[0].primary.isEnabled() && panel[0].browse.isVisible() && panel[0].primary.getText().equals("Use this game"), "missing game cannot launch and offers reselection after failure");
            paint(panel[0], root.resolve("error-en.png")); panel[0].refresh();
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> { check(panel[0].state.ready() && panel[0].language.getSelectedIndex() == 1, "reopen retains previous valid setup and language after failed replacement"); });
    }
    private static void await(PlayerLauncherPanel panel) throws Exception {
        for (int i = 0; i < 160; i++) { boolean[] busy = {true}; SwingUtilities.invokeAndWait(() -> busy[0] = panel.busy()); if (!busy[0]) return; Thread.sleep(50); }
        throw new AssertionError("player UI timeout");
    }
    private static void layout(Container panel) { panel.doLayout(); for (Component c : panel.getComponents()) if (c instanceof Container nested) layout(nested); }
    private static void paint(PlayerLauncherPanel panel, Path path) {
        panel.setSize(700, 380); layout(panel); BufferedImage image = new BufferedImage(700, 380, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
        try { ImageIO.write(image, "png", path.toFile()); } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
