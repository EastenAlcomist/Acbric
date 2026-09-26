/* ExternalInstaller.java — 中英文实例配置向导入口；无参数打开窗口，显式参数供自动化部署和回归。 */
package net.fabricacs.acbric;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ExternalInstaller {
    private ExternalInstaller() {}
    public static void main(String[] args) {
        try {
            ExternalGameInstallation.runtime(System.getProperty("os.name"), Runtime.version().feature(), System.getProperty("os.arch"));
            Path bundle = Path.of(ExternalInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().getParent().toRealPath();
            if (args.length == 0) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                SwingUtilities.invokeLater(() -> {
                    JFrame frame = new JFrame("Acbric — 实例配置 / Instance setup");
                    InstallerPanel panel = new InstallerPanel(bundle);
                    frame.setContentPane(panel); frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    frame.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override public void windowClosing(java.awt.event.WindowEvent event) { if (!panel.busy()) frame.dispose(); }
                    });
                    frame.pack(); frame.setMinimumSize(frame.getSize()); frame.setLocationRelativeTo(null); frame.setVisible(true);
                });
                return;
            }
            Map<String, String> options = new HashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 == args.length || !Set.of("--game-dir", "--instance-dir", "--language").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null)
                    throw new IOException("USAGE / 用法: --game-dir <game> --instance-dir <instance> [--language zh|en]");
            }
            if (!options.containsKey("--game-dir") || !options.containsKey("--instance-dir")) throw new IOException("PATHS_REQUIRED / 请指定游戏和实例路径");
            var preview = InstanceSetup.preview(bundle, Path.of(options.get("--game-dir")), Path.of(options.get("--instance-dir")), options.getOrDefault("--language", "zh"));
            System.out.println("SETUP_OK / 实例配置成功: " + InstanceSetup.save(preview));
            System.out.println("MOD directory / MOD 目录: " + ExternalMods.directory(bundle, preview.plan()));
        } catch (Exception ex) {
            System.err.println(failure(ex)); System.exit(2);
        }
    }

    static String failure(Throwable ex) {
        String message = "Setup failed / 配置失败: " + ex.getMessage();
        try {
            Path log = Files.createTempFile("acbric-setup-", ".log");
            try (var writer = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8))) { ex.printStackTrace(writer); }
            return message + "\nLog / 日志: " + log;
        } catch (IOException logging) { return message + "\nCannot write log / 无法写入日志: " + logging.getMessage(); }
    }
}
