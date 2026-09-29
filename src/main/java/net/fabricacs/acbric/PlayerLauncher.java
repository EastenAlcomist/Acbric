/* PlayerLauncher.java — 无控制台玩家入口；保持框架使用锁，将异常写入本地日志。 */
package net.fabricacs.acbric;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class PlayerLauncher {
    private PlayerLauncher() {}
    public static void main(String[] args) {
        try {
            Path bundle = Path.of(PlayerLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent().toRealPath();
            Path logs = Path.of(System.getProperty("acbric.launcher.logs", System.getProperty("java.io.tmpdir") + "/acbric-player-logs"));
            LauncherDiagnostics diagnostics = new LauncherDiagnostics(logs);
            PrintStream output = new PrintStream(Files.newOutputStream(Files.createTempFile(logs, "session-", ".log")), true, StandardCharsets.UTF_8);
            System.setOut(output); System.setErr(output);
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> diagnostics.record(error));
            ExternalGameInstallation.runtime(System.getProperty("os.name"), Runtime.version().feature(), System.getProperty("os.arch"));
            FrameworkUse use = FrameworkUse.open(bundle);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { use.close(); } catch (IOException ignored) { } }, "acbric-player-release"));
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            // 统一 Windows 中文/英文界面字号，避免默认宋体小字号和控件字体不一致。
            var font = new javax.swing.plaf.FontUIResource("Microsoft YaHei UI", java.awt.Font.PLAIN, 14);
            for (Object key : UIManager.getDefaults().keySet().toArray()) if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) UIManager.put(key, font);
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("Acbric");
                PlayerLauncherPanel panel = new PlayerLauncherPanel(new PlayerLauncherService(bundle), diagnostics, job -> {
                    try {
                        Process helper = new ProcessBuilder(bundle.resolve("Acbric.exe").toString(), "--maintenance", job.toString()).start();
                        if (!helper.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) || helper.exitValue() != 0) throw new IOException("MAINTENANCE_START_FAILED");
                        frame.dispose(); System.exit(0);
                    } catch (Exception ex) { throw new IllegalStateException(ex); }
                });
                frame.setContentPane(panel); frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                frame.addWindowListener(new java.awt.event.WindowAdapter() { public void windowClosing(java.awt.event.WindowEvent e) { if (!panel.busy()) { frame.dispose(); System.exit(0); } } });
                frame.pack(); frame.setMinimumSize(frame.getSize()); frame.setLocationRelativeTo(null); frame.setVisible(true); panel.refresh();
            });
            String ready = System.getProperty("acbric.launcher.ready");
            if (ready != null) Files.writeString(Path.of(ready), "ready");
        } catch (Throwable ex) { ex.printStackTrace(); System.exit(2); }
    }
}
