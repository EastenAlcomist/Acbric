/* PlayerLauncherPanel.java — 玩家首页与首次使用流程；技术配置和日志仅在帮助/高级设置中展开。 */
package net.fabricacs.acbric;

import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

final class PlayerLauncherPanel extends JPanel {
    final JButton primary = new JButton(), browse = new JButton(), mods = new JButton(), settings = new JButton(), help = new JButton(), retry = new JButton();
    final JLabel title = new JLabel(), location = new JLabel();
    final JTextArea status = new JTextArea(3, 42);
    final JComboBox<String> language = new JComboBox<>(new String[]{"中文", "English"});
    private final JPanel actions = new JPanel(new GridLayout(1, 0, 12, 0));
    final PlayerLauncherService service;
    final LauncherDiagnostics diagnostics;
    PlayerLauncherService.State state;
    private final Consumer<Path> handoff;
    private boolean working, translating;
    private Runnable again;

    PlayerLauncherPanel(PlayerLauncherService service, LauncherDiagnostics diagnostics, Consumer<Path> handoff) {
        this.service = service; this.diagnostics = diagnostics; this.handoff = handoff;
        setPreferredSize(new Dimension(700, 360));
        setLayout(new BorderLayout(12, 18)); setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
        JPanel heading = new JPanel(new BorderLayout());
        JLabel brand = new JLabel("Acbric"); brand.setFont(brand.getFont().deriveFont(Font.BOLD, 28)); heading.add(brand, BorderLayout.WEST);
        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT)); topRight.add(new JLabel(ExternalLauncher.CORE_VERSION)); topRight.add(language); heading.add(topRight, BorderLayout.EAST); add(heading, BorderLayout.NORTH);
        JPanel content = new JPanel(); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT); location.setAlignmentX(LEFT_ALIGNMENT); status.setAlignmentX(LEFT_ALIGNMENT); actions.setAlignmentX(LEFT_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20)); content.add(title); content.add(Box.createVerticalStrut(12)); content.add(location); content.add(Box.createVerticalStrut(16));
        status.setEditable(false); status.setFocusable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setOpaque(false); status.setFont(status.getFont().deriveFont(15f)); content.add(status);
        primary.setFont(primary.getFont().deriveFont(Font.BOLD, 20)); primary.setPreferredSize(new Dimension(260, 52)); actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52)); actions.add(primary); actions.add(browse); content.add(actions); add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); for (JButton button : List.of(mods, settings, help, retry)) footer.add(button); add(footer, BorderLayout.SOUTH);
        primary.addActionListener(e -> {
            if (state == null) return;
            if (state.ready()) work(text("游戏运行中，关闭游戏后可返回这里。", "Game running. Close the game to return here."), service::launch, result -> {
                if (result != 0) throw new IllegalStateException("GAME_EXIT_" + result);
                render();
            });
            else if (state.game() != null) configure(state.game());
        });
        browse.addActionListener(e -> chooseGame());
        mods.addActionListener(e -> open(service.bundle.resolve("mods")));
        settings.addActionListener(e -> settings()); help.addActionListener(e -> help());
        retry.addActionListener(e -> { if (again != null) again.run(); });
        language.addActionListener(e -> {
            if (translating) return;
            String selected = code();
            work(text("正在保存语言…", "Saving language…"), () -> { service.language(selected); return null; }, unused -> render());
        });
        render();
    }

    boolean busy() { return working; }
    private boolean zh() { return language.getSelectedIndex() == 0; }
    private String code() { return zh() ? "zh" : "en"; }
    private String text(String zh, String en) { return zh() ? zh : en; }

    void refresh() {
        work(text("正在查找游戏…", "Looking for your game…"), () -> {
            String lang = service.language(); var saved = service.inspect();
            return new Object[]{lang, saved, saved.game() == null ? service.discover() : List.<Path>of()};
        }, result -> {
            translating = true; language.setSelectedIndex(result[0].equals("zh") ? 0 : 1); translating = false;
            state = (PlayerLauncherService.State) result[1];
            @SuppressWarnings("unchecked") List<Path> found = (List<Path>) result[2];
            if (found.size() == 1) state = new PlayerLauncherService.State(state.instance(), found.getFirst(), false);
            else if (found.size() > 1) {
                Path selected = (Path) JOptionPane.showInputDialog(this, text("找到多份游戏，请选择要使用的一份。", "Choose which game installation to use."), "Acbric", JOptionPane.QUESTION_MESSAGE, null, found.toArray(), found.getFirst());
                if (selected != null) state = new PlayerLauncherService.State(state.instance(), selected, false);
            }
            render();
        });
    }

    void configure(Path game) {
        var selected = state; String lang = code();
        work(text("正在准备，请稍候…", "Preparing your game…"), () -> service.configure(selected, game, lang), value -> { state = value; render(); });
    }

    private void chooseGame() {
        if (state == null) return;
        JFileChooser picker = new JFileChooser(); picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        picker.setDialogTitle(text("选择 Airships 游戏文件夹", "Select the Airships game folder"));
        if (state.game() != null && Files.isDirectory(state.game())) picker.setCurrentDirectory(state.game().toFile());
        if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            state = new PlayerLauncherService.State(state.instance(), picker.getSelectedFile().toPath(), false); render();
        }
    }

    private void render() {
        boolean ready = state != null && state.ready();
        title.setText(ready ? text("准备就绪", "Ready to play") : text("欢迎使用 Acbric", "Welcome to Acbric"));
        location.setText(state == null || state.game() == null ? text("尚未选择游戏", "No game selected") : state.game().toString());
        location.setToolTipText(location.getText());
        status.setText(state != null && state.game() != null && !gamePresent() ? text("找不到原来的游戏文件，请重新选择完整的游戏文件夹。存档和 MOD 会保留。", "Game files are missing. Select the complete game folder again. Saves and MODs are preserved.")
                : ready ? text("MOD 放入 mods 文件夹后，点击开始游戏。", "Place MODs in the mods folder, then start the game.")
                : state != null && state.game() != null ? text("确认游戏位置后点击“使用此游戏”，其余配置会自动完成。", "Confirm this game location. Select Use this game to finish setup automatically.")
                : text("没有找到游戏时，请选择它的安装文件夹。", "If no game was found, select its installation folder."));
        primary.setText(ready ? text("开始游戏", "Start game") : text("使用此游戏", "Use this game"));
        browse.setText(text("选择游戏文件夹", "Choose game folder")); mods.setText(text("打开 MOD 文件夹", "Open MOD folder")); settings.setText(text("设置", "Settings")); help.setText(text("帮助", "Help")); retry.setText(text("重试", "Retry"));
        retry.setVisible(false); enabled();
    }

    private void enabled() {
        primary.setEnabled(!working && gamePresent());
        browse.setEnabled(!working && state != null); browse.setVisible(state == null || !state.ready());
        // 隐藏的组件仍会占用 GridLayout 单元格，因此就绪时移出浏览按钮。
        if (browse.isVisible() && browse.getParent() == null) actions.add(browse);
        if (!browse.isVisible() && browse.getParent() == actions) actions.remove(browse);
        actions.revalidate(); actions.repaint();
        mods.setEnabled(!working && state != null && state.ready()); settings.setEnabled(!working); help.setEnabled(!working); language.setEnabled(!working); retry.setEnabled(!working);
    }

    private <T> void work(String message, Callable<T> action, Consumer<T> complete) {
        again = () -> work(message, action, complete);
        working = true; retry.setVisible(false); status.setText(message); enabled();
        new SwingWorker<T, Void>() {
            protected T doInBackground() throws Exception { return action.call(); }
            protected void done() {
                working = false;
                try { complete.accept(get()); again = null; }
                catch (Exception ex) { failed(ex.getCause() == null ? ex : ex.getCause()); }
                enabled();
            }
        }.execute();
    }

    private boolean gamePresent() { return state != null && state.game() != null && Files.isRegularFile(state.game().resolve("Airships.json")); }
    private void failed(Throwable ex) { diagnostics.record(ex); render(); status.setText(LauncherDiagnostics.friendly(ex, zh())); retry.setVisible(again != null); }
    private void open(Path path) {
        again = () -> open(path);
        try { Desktop.getDesktop().open(path.toFile()); again = null; }
        catch (Exception ex) { failed(ex); }
    }

    private void settings() {
        String[] choices = {text("更换游戏位置", "Change game location"), text("安装更新包", "Install update package"), text("恢复上一版", "Restore previous version"), text("高级设置", "Advanced settings"), text("返回", "Back")};
        int choice = JOptionPane.showOptionDialog(this, text("设置与维护", "Settings and maintenance"), "Acbric", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (choice == 0) chooseGame();
        if (choice == 1 || choice == 2) maintenance(choice == 2);
        if (choice == 3) {
            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), text("高级配置", "Advanced setup"), true);
            InstallerPanel advanced = new InstallerPanel(service.bundle); advanced.language.setSelectedIndex(language.getSelectedIndex());
            if (state != null) { advanced.instance.setText(state.instance().toString()); if (state.game() != null) advanced.game.setText(state.game().toString()); }
            dialog.setContentPane(advanced); dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() { public void windowClosing(java.awt.event.WindowEvent e) { if (!advanced.busy()) dialog.dispose(); } });
            dialog.pack(); dialog.setLocationRelativeTo(this); dialog.setVisible(true); refresh();
        }
    }

    private void maintenance(boolean restore) {
        Path archive = null;
        if (!restore) {
            JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Acbric ZIP", "zip"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            archive = chooser.getSelectedFile().toPath();
        }
        if (JOptionPane.showConfirmDialog(this, (restore ? text("恢复上一次更新前的框架？", "Restore the framework from before the last update?") : text("安装此更新包？", "Install this update package?") + "\n" + archive.getFileName())
                + "\n" + text("启动器将关闭，完成后重新打开。MOD、存档和设置会保留。", "The launcher will close and reopen when finished. MODs, saves and settings are preserved."), "Acbric", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        Path selected = archive; String lang = code();
        work(text("正在准备维护…", "Preparing maintenance…"), () -> service.maintenance(selected, restore, lang), handoff);
    }

    private void help() {
        String[] options = {text("使用说明", "Instructions"), text("导出诊断", "Export diagnostics"), text("技术详情", "Technical details"), text("返回", "Back")};
        int choice = JOptionPane.showOptionDialog(this, text("把 MOD 放入 mods 文件夹，再开始游戏。遇到问题时可导出诊断交给开发者。", "Put MODs in the mods folder and start the game. Export diagnostics for the developer if you need help."), "Acbric", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice == 0) open(service.bundle.resolve(zh() ? "使用说明.txt" : "QUICK_START.txt"));
        if (choice == 2) {
            JTextArea detail = new JTextArea(diagnostics.details(), 18, 65); detail.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(detail), text("技术详情", "Technical details"), JOptionPane.PLAIN_MESSAGE);
        }
        if (choice == 1) {
            if (JOptionPane.showConfirmDialog(this, text("报告包含日志和本机路径，不包含存档，也不会自动上传。继续导出？", "The report includes logs and local paths, but no saves. Nothing is uploaded automatically. Export it?"), "Acbric", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("Acbric-diagnostics-" + System.currentTimeMillis() + ".zip"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path output = chooser.getSelectedFile().toPath(); Path instance = state == null ? null : state.instance();
            work(text("正在导出…", "Exporting…"), () -> { diagnostics.export(output, instance); return output; }, result -> status.setText(text("诊断已保存：", "Diagnostics saved: ") + result));
        }
    }
}
