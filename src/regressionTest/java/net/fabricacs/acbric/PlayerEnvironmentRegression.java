/* PlayerEnvironmentRegression.java — 数据环境回归：默认直接使用原版存档与模组（不复制）、识别规则、
 * 可选环境隔离的镜像与备份，以及真实首页按钮与「设置 → 环境隔离」。 */
package net.fabricacs.acbric;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import javax.swing.*;
import net.fabricacs.api.impl.LocalModPaths;

public final class PlayerEnvironmentRegression {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; System.out.println("PASS environment: " + label); }
    private interface Action { void run() throws Exception; }
    private static void reject(String code, Action action) throws Exception {
        try { action.run(); } catch (Exception ex) { check(ex.toString().contains(code), code); return; }
        throw new AssertionError("Expected " + code);
    }

    /** 原版用户数据夹具：含镜像范围内的内容与范围外的内容各若干。 */
    static Path vanilla(Path root) throws Exception {
        write(root, "saves/Catsworth/1.json", "new save");
        write(root, "saves/Catsworth/2.json", "second save");
        write(root, "ships/T1/fleet.json", "fleet");
        write(root, "buildings/Iron Fort.json", "building");
        write(root, "landships/Boar.json", "landship");
        write(root, "combats/Ships vs 帝炮 etc.json", "combat");
        write(root, "missions/猎杀-克拉肯/plan.json", "mission");
        write(root, "recordings/run.json", "recording");
        write(root, "recordingsArchive/old.json", "archived");
        write(root, "mods/SomeMod/info.json", "some mod v2");
        write(root, "mods/Another Mod/info.json", "another mod");
        write(root, "prefs.json", "vanilla settings");
        write(root, "tmp/scratch.txt", "temporary");
        write(root, ".acbric-bundles/keep.bin", "managed elsewhere");
        return root;
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    public static int run(Path root) throws Exception {
        checks = 0;
        Path vanilla = vanilla(root.resolve("原版 AppData & AirshipsGame"));
        Path game = ExternalInstallRegression.installation(root.resolve("游戏 & install"));
        Path framework = InstanceSetupRegression.bundle(root.resolve("Acbric 框架 & 中文"));
        Files.writeString(framework.resolve("Acbric.exe"), "fixture");
        var service = new PlayerLauncherService(framework);
        var ready = service.configure(service.inspect(), game, "zh");
        Path instance = ready.instance(), javaMods = framework.resolve("mods");
        String previous = System.getProperty(PlayerEnvironment.VANILLA_PROPERTY);
        System.setProperty(PlayerEnvironment.VANILLA_PROPERTY, vanilla.toString());
        try {
            sharing(root, service, ready, game, instance, vanilla, javaMods);
            isolation(service, ready, game, instance, vanilla, javaMods);
            ui(root, game, vanilla);
        } finally {
            if (previous == null || previous.isEmpty()) System.clearProperty(PlayerEnvironment.VANILLA_PROPERTY);
            else System.setProperty(PlayerEnvironment.VANILLA_PROPERTY, previous);
        }
        return checks;
    }

    /** 默认共享：识别原版目录、直接使用、不产生任何副本；识别失败与配置损坏都有明确诊断。 */
    private static void sharing(Path root, PlayerLauncherService service, PlayerLauncherService.State ready, Path game,
                                Path instance, Path vanilla, Path javaMods) throws Exception {
        check(PlayerEnvironment.detect(game).equals(vanilla), "detection finds the vanilla data folder");
        check(!Files.exists(instance.resolve("acbric-launcher/environment.json")), "a fresh instance stores no environment until the player chooses");
        var shared = service.environment(ready);
        check(shared.dataDir().equals(vanilla) && shared.modsDir().equals(vanilla.resolve("mods")), "an unconfigured instance uses the vanilla data and MOD folders");
        check(!Files.exists(instance.resolve("userdata/saves")) && !Files.exists(instance.resolve("userdata/mods")) && !Files.exists(javaMods.resolve("SomeMod")), "sharing copies nothing into the instance");

        var applied = service.useVanillaData(ready);
        check(applied.dataDir().equals(vanilla) && service.environment(ready).dataDir().equals(vanilla), "one click records the detected vanilla paths");
        check(service.useVanillaData(ready).dataDir().equals(vanilla), "repeating the detection is harmless");
        check(Files.readString(vanilla.resolve("saves/Catsworth/1.json")).equals("new save") && Files.exists(vanilla.resolve("mods/SomeMod/info.json")), "the vanilla folder is untouched");
        check(vanilla.resolve("mods").equals(service.environment(ready).modsDir()) && Files.isDirectory(vanilla.resolve("mods")), "native MODs are read from the vanilla folder directly");

        List<String> arguments = ExternalLauncher.pathArguments(game, instance, shared, javaMods);
        check(arguments.contains("-Dacbric.external.dataDir=" + vanilla) && arguments.contains("-Dacbric.external.mods=" + vanilla.resolve("mods")), "shared launch points the game at the vanilla data and MOD folders");
        check(arguments.contains("-Dfabric.modsFolder=" + javaMods) && !arguments.contains("-Dacbric.external.dataDir=" + instance.resolve("userdata")), "Java JARs keep using the framework MOD folder");
        check(singleModRoot(vanilla.resolve("mods"), game), "the game resolves native MODs to that one vanilla folder");

        System.setProperty(ExternalGameInstallation.DATA_PROPERTY, vanilla.toString());
        Path settings = ExternalLaunchSettings.prepare(ExternalGameInstallation.inspect(game, instance));
        String prepared = Files.readString(settings);
        check(prepared.contains(ExternalLaunchSettings.quote(vanilla.toString())) && prepared.contains(ExternalLaunchSettings.quote(vanilla.resolve("gifs").toString()))
                && Files.isDirectory(vanilla.resolve("gifs")), "the game reads the vanilla data and GIF folders in shared mode");
        System.clearProperty(ExternalGameInstallation.DATA_PROPERTY);
        Path isolatedSettings = ExternalLaunchSettings.prepare(ExternalGameInstallation.inspect(game, instance));
        check(Files.readString(isolatedSettings).contains(ExternalLaunchSettings.quote(instance.resolve("userdata").toString())), "without the property the game keeps using instance userdata");

        // 原版游戏在 launch_settings.json 里自定义数据目录时优先识别该目录；该文件损坏则回退识别。
        Path moved = vanilla(root.resolve("移动后的原版数据"));
        Path movedGame = ExternalInstallRegression.installation(root.resolve("游戏 自定义目录"));
        write(movedGame, "launch_settings.json", "{\"customDataDirectoryLocation\":" + ExternalLaunchSettings.quote(moved.toString()) + "}");
        check(PlayerEnvironment.detect(movedGame).equals(moved), "the vanilla custom data folder from launch settings wins");
        write(movedGame, "launch_settings.json", "{broken");
        check(PlayerEnvironment.detect(movedGame).equals(vanilla), "a damaged launch settings file falls back to the detected folder");

        // 记录过的目录被移走时重新识别，而不是直接失败。
        Path recorded = vanilla(root.resolve("更早的原版数据"));
        PlayerEnvironment.write(instance, new PlayerEnvironment.Layout(PlayerEnvironment.SHARED, recorded, recorded.resolve("mods"), recorded));
        deleteTree(recorded);
        check(service.environment(ready).dataDir().equals(vanilla), "a moved vanilla folder is detected again automatically");

        System.setProperty(PlayerEnvironment.VANILLA_PROPERTY, root.resolve("missing-vanilla").toString());
        reject("VANILLA_DATA_MISSING", () -> PlayerEnvironment.detect(game));
        reject("VANILLA_DATA_MISSING", () -> service.useVanillaData(ready));
        write(root, "not-a-folder", "file");
        System.setProperty(PlayerEnvironment.VANILLA_PROPERTY, root.resolve("not-a-folder").toString());
        reject("VANILLA_DATA_MISSING", () -> service.environment(ready));
        System.setProperty(PlayerEnvironment.VANILLA_PROPERTY, vanilla.toString());

        write(instance, "acbric-launcher/environment.json", "{broken");
        reject("ENVIRONMENT_CONFIG_INVALID", () -> service.environment(ready));
        check(Files.readString(instance.resolve("acbric-launcher/environment.json")).equals("{broken"), "a damaged environment file is preserved");
        System.clearProperty(PlayerEnvironment.VANILLA_PROPERTY);
        try { check(PlayerEnvironment.detect(game).getFileName().toString().equals("AirshipsGame"), "the default folder is the vanilla folder in AppData"); }
        catch (IOException missingAppData) { check(missingAppData.toString().contains("VANILLA_DATA_MISSING"), "no AppData folder reports a readable error instead of guessing"); }
        System.setProperty(PlayerEnvironment.VANILLA_PROPERTY, vanilla.toString());
        PlayerEnvironment.write(instance, PlayerEnvironment.shared(javaMods, game));
        check(service.environment(ready).dataDir().equals(vanilla), "the recovered environment shares again");
    }

    /** 环境隔离：完全镜像到实例副本、先备份再覆盖、Java MOD 不受影响，失败时保持原环境。 */
    private static void isolation(PlayerLauncherService service, PlayerLauncherService.State ready, Path game,
                                  Path instance, Path vanilla, Path javaMods) throws Exception {
        Path userdata = instance.resolve("userdata");
        // 实例副本里先放上内容：一份旧存档、多出的存档与录像、以及玩家自己放的 Java MOD。
        write(userdata, "saves/Catsworth/1.json", "old save");
        write(userdata, "saves/Old World/1.json", "removed save");
        write(userdata, "recordings/keep.json", "extra recording");
        write(userdata, "prefs.json", "instance settings");
        write(userdata, "log.txt", "instance log");
        Files.createDirectories(userdata.resolve("ships/T1"));
        Files.copy(vanilla.resolve("ships/T1/fleet.json"), userdata.resolve("ships/T1/fleet.json"), StandardCopyOption.COPY_ATTRIBUTES);
        write(javaMods, "SomeMod/info.json", "old mod info");
        write(javaMods, "Stale Mod/info.json", "extra mod");
        write(javaMods, "ARC-Overhaul.jar", "java mod");
        write(javaMods, "README.md", "framework readme");

        var preview = service.isolatePreview(ready);
        check(preview.copy() == 10 && preview.remove() == 3 && preview.source().equals(vanilla), "isolation preview counts copies and removals: " + preview.copy() + "/" + preview.remove());
        check(Files.exists(userdata.resolve("saves/Old World/1.json")) && service.environment(ready).shared(), "the preview changes nothing and the instance still shares");

        var result = service.isolate(ready);
        check(result.copy() == 10 && result.remove() == 3 && result.backup() != null, "isolation mirrors the vanilla data into the instance");
        Path backup = result.backup();
        check(Files.readString(userdata.resolve("saves/Catsworth/1.json")).equals("new save"), "the older instance save is replaced by the vanilla one");
        check(Files.readString(backup.resolve("userdata/saves/Catsworth/1.json")).equals("old save"), "the replaced save is recoverable from the backup");
        check(!Files.exists(userdata.resolve("saves/Old World")) && Files.readString(backup.resolve("userdata/saves/Old World/1.json")).equals("removed save"), "extra saves move into the backup while isolating");
        check(!Files.exists(userdata.resolve("recordings/keep.json")) && Files.isRegularFile(backup.resolve("userdata/recordings/keep.json")), "extra recordings move into the backup while isolating");
        check(Files.readString(userdata.resolve("recordings/run.json")).equals("recording") && Files.readString(userdata.resolve("missions/猎杀-克拉肯/plan.json")).equals("mission"), "recordings and missions are copied");
        check(Files.readString(userdata.resolve("ships/T1/fleet.json")).equals("fleet") && Files.readString(userdata.resolve("buildings/Iron Fort.json")).equals("building") && Files.readString(userdata.resolve("landships/Boar.json")).equals("landship"), "designs are copied");
        check(Files.readString(userdata.resolve("prefs.json")).equals("instance settings") && Files.readString(userdata.resolve("log.txt")).equals("instance log"), "settings and logs outside the copied folders are untouched");
        check(Files.readString(javaMods.resolve("SomeMod/info.json")).equals("some mod v2") && Files.readString(backup.resolve("mods/SomeMod/info.json")).equals("old mod info"), "native MOD folders are copied with the previous copy backed up");
        check(!Files.exists(javaMods.resolve("Stale Mod")) && Files.isRegularFile(backup.resolve("mods/Stale Mod/info.json")), "a native MOD missing from vanilla is removed into the backup");
        check(Files.readString(javaMods.resolve("ARC-Overhaul.jar")).equals("java mod") && Files.readString(javaMods.resolve("README.md")).equals("framework readme"), "Java MODs and framework files are never touched");
        check(Files.getLastModifiedTime(userdata.resolve("saves/Catsworth/2.json")).equals(Files.getLastModifiedTime(vanilla.resolve("saves/Catsworth/2.json"))), "copied files keep the vanilla timestamp");

        var isolated = service.environment(ready);
        check(isolated.isolated() && isolated.dataDir().equals(userdata) && isolated.modsDir().equals(javaMods), "the instance switches to its own copies");
        List<String> arguments = ExternalLauncher.pathArguments(game, instance, isolated, javaMods);
        check(arguments.stream().noneMatch(a -> a.startsWith("-Dacbric.external.dataDir=")) && arguments.contains("-Dacbric.external.mods=" + javaMods), "isolated launch no longer reads the vanilla folders");
        check(singleModRoot(javaMods, game), "isolated native MOD scanning uses the framework MOD folder");

        check(service.isolatePreview(ready).empty() && service.isolate(ready).backup() == null, "isolating again is a no-op and creates no backup");
        Files.delete(vanilla.resolve("recordingsArchive/old.json"));
        Files.delete(vanilla.resolve("recordingsArchive"));
        check(service.isolatePreview(ready).remove() == 1 && service.isolate(ready).remove() == 1 && !Files.exists(userdata.resolve("recordingsArchive")), "a category missing from vanilla clears the instance copy");

        try (var shared = ExternalMods.open(javaMods, game, instance)) { reject("MODS_BUSY", () -> service.isolate(ready)); }
        check(Files.readString(javaMods.resolve("SomeMod/info.json")).equals("some mod v2"), "a busy MOD folder leaves data untouched");
        try (var lease = ExternalPreflight.InstanceLease.open(game, instance)) {
            check(service.useVanillaData(ready).dataDir().equals(vanilla), "sharing only records paths and never needs the game to be closed");
            reject("INSTANCE_BUSY", () -> service.isolate(ready));
        }
        check(service.environment(ready).shared() && Files.readString(userdata.resolve("saves/Catsworth/1.json")).equals("new save"), "a failed mirror keeps the previous environment and data");

        PlayerEnvironment.write(instance, PlayerEnvironment.isolated(instance, javaMods, game));
        Path kept = userdata.resolve("saves/Catsworth/1.json");
        var back = service.useVanillaData(ready);
        check(back.dataDir().equals(vanilla) && Files.exists(kept) && Files.readString(kept).equals("new save"), "going back to sharing keeps the isolated copy on disk");
        check(Files.readString(vanilla.resolve("saves/Catsworth/1.json")).equals("new save") && Files.isDirectory(vanilla.resolve("mods")), "the vanilla folder is never modified by isolation");
        reject("SETUP_REQUIRED", () -> service.useVanillaData(new PlayerLauncherService.State(instance, game, false)));
        reject("SETUP_REQUIRED", () -> service.isolate(new PlayerLauncherService.State(instance, game, false)));
    }

    /** 真实首页：一键同步只识别不复制；设置里有环境隔离，执行后状态切到副本。 */
    private static void ui(Path root, Path game, Path vanilla) throws Exception {
        Path framework = InstanceSetupRegression.bundle(root.resolve("UI/Acbric"));
        Files.writeString(framework.resolve("Acbric.exe"), "fixture");
        String[] reported = {null}; String[] planText = {null}; int[] confirms = {0};
        var service = new PlayerLauncherService(framework) {
            List<Path> discover() { return List.of(game); }
            int launch() { return 0; }
        };
        var diagnostics = new LauncherDiagnostics(root.resolve("UI/logs"));
        PlayerLauncherPanel[] panel = new PlayerLauncherPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new PlayerLauncherPanel(service, diagnostics, job -> {}) {
                boolean confirmIsolation(String plan) { confirms[0]++; planText[0] = plan; return true; }
                void reportIsolation(String summary) { reported[0] = summary; }
            };
            check(panel[0].sync.getText().equals("一键同步") && !panel[0].sync.isEnabled(), "the home offers sync and keeps it disabled before setup");
            check(footerFits(panel[0]), "footer buttons still fit the home width");
            check(List.of(panel[0].settingsOptions()).contains("环境隔离"), "settings offer environment isolation in Chinese");
            panel[0].refresh();
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> { check(!panel[0].sync.isEnabled(), "sync stays disabled until a game is configured"); panel[0].configure(game); });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].sync.isEnabled() && panel[0].status.getText().contains(vanilla.toString()), "the configured home shares the vanilla data by default: " + panel[0].status.getText());
            check(!Files.exists(panel[0].state.instance().resolve("userdata/saves")) && reported[0] == null, "sharing copies nothing and needs no dialog");
            panel[0].sync.doClick();
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].status.getText().contains("未复制文件") && panel[0].status.getText().contains(vanilla.toString()), "one click reports the detected paths without copying: " + panel[0].status.getText());
            check(!Files.exists(panel[0].state.instance().resolve("userdata/saves")) && reported[0] == null, "one click never writes a copy");
            panel[0].language.setSelectedIndex(1);
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].sync.getText().equals("Sync now") && List.of(panel[0].settingsOptions()).contains("Environment isolation"), "the sync button and isolation entry translate");
            panel[0].isolateEnvironment();
        });
        await(panel[0]);
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            Path userdata = panel[0].state.instance().resolve("userdata");
            check(confirms[0] == 1 && planText[0].toLowerCase().contains("isolation") && planText[0].contains(vanilla.toString()), "isolation asks once and shows what will happen");
            check(reported[0] != null && reported[0].contains("Isolated") && read(userdata.resolve("saves/Catsworth/1.json")).equals("new save"), "confirmed isolation mirrors the data and reports it: " + reported[0]);
            check(panel[0].status.getText().contains("Isolated"), "the home shows the isolated environment");
            panel[0].sync.doClick();
        });
        await(panel[0]);
        SwingUtilities.invokeAndWait(() -> {
            check(panel[0].status.getText().contains("nothing copied"), "sharing again only records the vanilla paths: " + panel[0].status.getText());
            check(read(panel[0].state.instance().resolve("userdata/saves/Catsworth/1.json")).equals("new save"), "the isolated copy is kept after going back to sharing");
        });
    }

    /** 界面断言运行在 EDT 回调里，读取夹具时把受检异常转成非受检异常。 */
    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }

    private static void deleteTree(Path path) throws Exception {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { Files.deleteIfExists(path); return; }
        try (var children = Files.list(path)) { for (Path child : children.toList()) deleteTree(child); }
        Files.deleteIfExists(path);
    }

    private static void await(PlayerLauncherPanel panel) throws Exception {
        for (int i = 0; i < 160; i++) { boolean[] busy = {true}; SwingUtilities.invokeAndWait(() -> busy[0] = panel.busy()); if (!busy[0]) return; Thread.sleep(50); }
        throw new AssertionError("player environment UI timeout");
    }

    /** 底部按钮排与面板同宽减去左右内边距；FlowLayout 换行会让最后的按钮看不见。 */
    private static boolean footerFits(PlayerLauncherPanel panel) {
        java.awt.Container footer = panel.sync.getParent();
        int needed = 0;
        for (java.awt.Component child : footer.getComponents()) if (child.isVisible()) needed += child.getPreferredSize().width + 10;
        return needed <= panel.getPreferredSize().width - 60;
    }

    /** 游戏解析原生 MOD 根只认正式启动设置的 `acbric.external.mods`，没有第二份来源。 */
    private static boolean singleModRoot(Path mods, Path install) {
        String previousInstall = System.getProperty("acbric.external.install"), previousMods = System.getProperty("acbric.external.mods");
        System.setProperty("acbric.external.install", install.toString());
        System.setProperty("acbric.external.mods", mods.toString());
        try { return LocalModPaths.nativeMods().toAbsolutePath().normalize().equals(mods.toAbsolutePath().normalize()); }
        finally {
            if (previousInstall == null) System.clearProperty("acbric.external.install"); else System.setProperty("acbric.external.install", previousInstall);
            if (previousMods == null) System.clearProperty("acbric.external.mods"); else System.setProperty("acbric.external.mods", previousMods);
        }
    }
}
