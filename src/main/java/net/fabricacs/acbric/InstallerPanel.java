/* InstallerPanel.java — 独立配置向导界面；明确选择中英文，耗时检查和写入在后台执行。 */
package net.fabricacs.acbric;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

final class InstallerPanel extends JPanel {
    private final Path bundle;
    final JComboBox<String> language = new JComboBox<>(new String[]{"中文", "English"});
    final JTextField game = new JTextField(43), instance = new JTextField(43);
    final JButton check = new JButton(), save = new JButton(), load = new JButton(), play = new JButton();
    final JButton openMods = new JButton();
    private final JButton browseGame = new JButton(), browseInstance = new JButton(), open = new JButton(), logs = new JButton();
    private final JLabel title = new JLabel(), gameLabel = new JLabel(), instanceLabel = new JLabel(), hint = new JLabel();
    final JTextArea status = new JTextArea(11, 65);
    private InstanceSetup.Preview preview;
    private Path savedInstance;
    private boolean working;

    InstallerPanel(Path bundle) {
        super(new BorderLayout(12, 16)); this.bundle = bundle;
        setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JPanel heading = new JPanel(new BorderLayout()); title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        heading.add(title, BorderLayout.CENTER); heading.add(language, BorderLayout.EAST); add(heading, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(10, 14)), form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(6, 0, 6, 10); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; form.add(gameLabel, c); c.gridy = 1; form.add(instanceLabel, c);
        c.gridx = 1; c.gridy = 0; c.weightx = 1; form.add(game, c); c.gridy = 1; form.add(instance, c);
        c.gridx = 2; c.gridy = 0; c.weightx = 0; form.add(browseGame, c); c.gridy = 1; form.add(browseInstance, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 3; form.add(hint, c);
        center.add(form, BorderLayout.NORTH); status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true);
        center.add(new JScrollPane(status), BorderLayout.CENTER); add(center, BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(0, 3, 10, 10));
        for (JButton button : new JButton[]{load, check, save, openMods, open, logs, play}) actions.add(button);
        add(actions, BorderLayout.SOUTH);
        instance.setText(bundle.resolve("instances/default").toString());
        DocumentListener invalidate = new DocumentListener() {
            private void reset() { preview = null; savedInstance = null; updateEnabled(); }
            public void insertUpdate(DocumentEvent e) { reset(); }
            public void removeUpdate(DocumentEvent e) { reset(); }
            public void changedUpdate(DocumentEvent e) { reset(); }
        };
        game.getDocument().addDocumentListener(invalidate); instance.getDocument().addDocumentListener(invalidate);
        language.addActionListener(e -> { preview = null; translate(); });
        browseGame.addActionListener(e -> choose(game)); browseInstance.addActionListener(e -> choose(instance));
        load.addActionListener(e -> {
            Path path;
            try { path = path(instance); } catch (Exception ex) { failed(ex); return; }
            work(() -> InstanceSetup.read(path), config -> {
                game.setText(config.game().toString()); language.setSelectedIndex(config.language().equals("zh") ? 0 : 1);
                status.setText(text("已读取已有实例。请检查并保存，以绑定当前框架和 Java。", "Instance loaded. Check and save to bind this framework and Java."));
            });
        });
        check.addActionListener(e -> {
            Path gamePath, instancePath;
            try { gamePath = path(game); instancePath = path(instance); } catch (Exception ex) { failed(ex); return; }
            String lang = language.getSelectedIndex() == 0 ? "zh" : "en";
            work(() -> InstanceSetup.preview(bundle, gamePath, instancePath, lang), value -> {
                preview = value;
                status.setText(text("检查通过。点击“保存并生成入口”完成配置。", "Checks passed. Select Save & create launcher to finish.")
                        + "\n\n" + text("游戏版本：", "Game version: ") + value.plan().identity().rawVersion()
                        + "\n" + text("游戏目录：", "Game: ") + value.plan().install()
                        + "\n" + text("实例目录：", "Instance: ") + value.plan().instance()
                        + "\n" + text("框架目录：", "Framework: ") + bundle
                        + "\n" + text("统一 MOD 目录：", "Shared MOD folder: ") + bundle.resolve("mods")
                        + "\n\n" + text("保存配置后，同目录的 Start Acbric.cmd 将启动此实例；MOD 放在同目录 mods。", "After saving, Start Acbric.cmd here launches this instance; MODs go in mods in the same folder.")
                        + "\n" + text("这是实验版本。Workshop 和任意 MOD 组合尚未完整验收。", "Experimental release. Workshop and arbitrary MOD combinations are not fully validated."));
            });
        });
        save.addActionListener(e -> {
            InstanceSetup.Preview expected = preview;
            if (expected == null) return;
            work(() -> InstanceSetup.save(expected), entry -> {
                savedInstance = expected.plan().instance(); preview = null;
                status.setText(text("配置完成！今后双击下面的入口即可启动：", "Setup complete! Double-click this launcher next time:") + "\n\n" + entry
                        + "\n\n" + text("Java MOD 的 jar 和原版 MOD 文件夹都放到：", "Put Java MOD jars and native MOD folders together here:") + "\n" + bundle.resolve("mods")
                        + "\n" + text("旧目录的 MOD 请手动复制到此处。", "Copy MODs from old folders here manually.")
                        + "\n" + text("可以为启动文件手动创建桌面快捷方式。", "You can create a desktop shortcut to the launch file."));
            });
        });
        openMods.addActionListener(e -> folder(bundle.resolve("mods")));
        open.addActionListener(e -> folder(savedInstance)); logs.addActionListener(e -> folder(savedInstance.resolve("logs/acbric")));
        play.addActionListener(e -> {
            Path selected = savedInstance;
            work(() -> {
                var config = InstanceSetup.read(selected);
                if (!config.framework().equals(bundle)) throw new IllegalStateException("Framework changed / 框架配置已改变，请重新检查保存");
                return ExternalLauncher.run(new String[]{"--game-dir", config.game().toString(), "--instance-dir", selected.toString()});
            }, exit -> status.setText(text("游戏已退出，返回码：", "Game exited, code: ") + exit + "\n" + text("启动日志：", "Launch logs: ") + selected.resolve("logs/acbric/launcher")));
        });
        translate();
    }

    boolean busy() { return working; }
    private String text(String zh, String en) { return language.getSelectedIndex() == 0 ? zh : en; }
    private static Path path(JTextField field) {
        if (field.getText().isBlank()) throw new IllegalArgumentException("Path required / 请填写目录");
        return Path.of(field.getText().trim()).toAbsolutePath().normalize();
    }
    private void choose(JTextField field) {
        JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        try { if (!field.getText().isBlank()) chooser.setSelectedFile(Path.of(field.getText()).toFile()); }
        catch (InvalidPathException ignored) { /* 输入无效时仍允许重新浏览目录。 */ }
        if (chooser.showDialog(this, text("选择目录", "Select folder")) == JFileChooser.APPROVE_OPTION) field.setText(chooser.getSelectedFile().getAbsolutePath());
    }
    private void folder(Path path) { try { Desktop.getDesktop().open(path.toFile()); } catch (Exception ex) { failed(ex); } }
    private void failed(Throwable ex) { preview = null; status.setText(ExternalInstaller.failure(ex)); updateEnabled(); }
    private void translate() {
        title.setText(text("Acbric 实例配置", "Acbric instance setup"));
        gameLabel.setText(text("游戏目录", "Game folder")); instanceLabel.setText(text("实例目录", "Instance folder"));
        browseGame.setText(text("浏览…", "Browse…")); browseInstance.setText(text("浏览…", "Browse…"));
        hint.setText(text("选择包含 Airships.json 的游戏目录；实例用于存档和设置；MOD 放在 Setup.cmd 同目录的 mods。", "Select the game folder containing Airships.json; saves/settings stay in the instance; MODs go in mods beside Setup.cmd."));
        load.setText(text("读取已有实例", "Load existing instance")); check.setText(text("检查目录", "Check folders"));
        save.setText(text("保存并生成入口", "Save & create launcher")); open.setText(text("打开实例目录", "Open instance folder"));
        openMods.setText(text("打开 MOD 目录", "Open MOD folder"));
        logs.setText(text("打开日志目录", "Open logs")); play.setText(text("启动游戏", "Launch game"));
        status.setText(text("选择目录后先检查，再保存。不会导入旧版数据。", "Select folders, check, then save. Legacy data is not imported.")); updateEnabled();
    }
    private void updateEnabled() {
        for (JComponent field : new JComponent[]{game, instance, language, browseGame, browseInstance, load, check}) field.setEnabled(!working);
        save.setEnabled(!working && preview != null);
        for (JButton button : new JButton[]{openMods, open, logs, play}) button.setEnabled(!working && savedInstance != null);
    }
    private <T> void work(Callable<T> action, Consumer<T> completed) {
        working = true; updateEnabled(); status.setText(text("正在处理，请稍候…", "Working, please wait…"));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return action.call(); }
            @Override protected void done() {
                working = false;
                try { completed.accept(get()); }
                catch (Exception ex) { failed(ex.getCause() == null ? ex : ex.getCause()); }
                updateEnabled();
            }
        }.execute();
    }
}
