/* PlayerLauncherProbe.java — 仅用于 EXE 验收的类加载器夹具；操作真实 Swing 首页，不进入发行包。 */
package net.fabricacs.acbric;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class PlayerLauncherProbe {
    /** 测试通过临时启动 JAR 接入，不要求精简运行时增加 java.instrument 模块。 */
    public static final class Loader extends ClassLoader {
        public Loader(ClassLoader parent) { super(parent); premain(null); }
    }
    public static void premain(String ignored) {
        new Thread(() -> {
            Path evidence = Path.of(System.getProperty("acbric.test.evidence"));
            try {
                Files.writeString(evidence.resolve("java.pid"), "" + ProcessHandle.current().pid());
                PlayerLauncherPanel[] panel = {null};
                for (int i = 0; i < 240 && panel[0] == null; i++) {
                    SwingUtilities.invokeAndWait(() -> {
                        for (Frame frame : Frame.getFrames()) if (frame instanceof JFrame f && f.isShowing() && f.getContentPane() instanceof PlayerLauncherPanel p && !p.busy() && p.state != null) panel[0] = p;
                    });
                    Thread.sleep(100);
                }
                if (panel[0] == null) throw new AssertionError("Player home did not open");
                var p = panel[0]; String mode = System.getProperty("acbric.test.mode");
                if (mode.equals("setup")) {
                    SwingUtilities.invokeAndWait(() -> {
                        if (p.state.ready()) throw new AssertionError("Fresh package was already configured");
                        capture(p, evidence.resolve("first-zh.png"));
                        p.configure(Path.of(System.getProperty("acbric.test.game")));
                    });
                    await(p);
                    SwingUtilities.invokeAndWait(() -> {
                        if (!p.state.ready()) throw new AssertionError(p.status.getText());
                        capture(p, evidence.resolve("home-zh.png")); p.language.setSelectedIndex(1);
                    });
                    await(p);
                } else {
                    SwingUtilities.invokeAndWait(() -> {
                        if (!p.state.ready() || !p.primary.getText().equals("Start game")) throw new AssertionError("Saved state/language not restored");
                        capture(p, evidence.resolve("home-en.png"));
                        if (mode.equals("play")) p.primary.doClick();
                    });
                    if (mode.equals("play")) {
                        Path checkpoint = p.state.instance().resolve("public-checkpoint.txt");
                        for (int i = 0; i < 600 && !Files.exists(checkpoint); i++) Thread.sleep(200);
                        if (!Files.exists(checkpoint)) throw new AssertionError("Game never reached actual menu");
                        Files.writeString(p.state.instance().resolve("allow-test-exit"), "complete");
                        await(p);
                        SwingUtilities.invokeAndWait(() -> { if (!p.primary.isEnabled() || p.retry.isVisible()) throw new AssertionError("Game return failed: " + p.status.getText()); });
                    }
                }
                Files.writeString(evidence.resolve("passed.txt"), "PASS " + mode + " / actual native EXE, Java GUI and saved state");
                System.exit(0);
            } catch (Throwable failure) {
                try { java.io.StringWriter s = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(s)); Files.writeString(evidence.resolve("failed.txt"), s.toString()); } catch (Exception ignoredFailure) { }
                System.exit(72);
            }
        }, "acbric-native-entry-probe").start();
    }
    private static void await(PlayerLauncherPanel panel) throws Exception {
        for (int i = 0; i < 300; i++) { boolean[] busy = {true}; SwingUtilities.invokeAndWait(() -> busy[0] = panel.busy()); if (!busy[0]) return; Thread.sleep(100); }
        throw new AssertionError("Player action timed out");
    }
    private static void capture(PlayerLauncherPanel panel, Path file) {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
        try { ImageIO.write(image, "png", file.toFile()); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
