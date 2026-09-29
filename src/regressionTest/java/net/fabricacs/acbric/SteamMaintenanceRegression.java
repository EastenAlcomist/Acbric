/* SteamMaintenanceRegression.java — Steam 多游戏库只读识别及框架占用/恢复保护的隔离检查。 */
package net.fabricacs.acbric;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SteamMaintenanceRegression {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; System.out.println("PASS steam/maintenance: " + label); }
    private static String quoted(Path p) { return "\"" + p.toString().replace("\\", "\\\\") + "\""; }
    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root);
        Path steam = root.resolve("Steam 中文 & 空格"), other = root.resolve("其他库"), legacy = root.resolve("旧库");
        Path one = ExternalInstallRegression.installation(steam.resolve("steamapps/common/Airships Conquer the Skies"));
        Path two = ExternalInstallRegression.installation(other.resolve("steamapps/common/自定义名称"));
        Path three = ExternalInstallRegression.installation(legacy.resolve("steamapps/common/Airships Conquer the Skies"));
        Files.createDirectories(steam.resolve("config"));
        Files.writeString(steam.resolve("steamapps/libraryfolders.vdf"), "\"libraryfolders\" { \"0\" { \"path\" " + quoted(steam) + " } \"1\" { \"path\" " + quoted(other) + " } }");
        Files.writeString(steam.resolve("config/libraryfolders.vdf"), "\"LibraryFolders\" { \"1\" " + quoted(legacy) + " }");
        Files.writeString(other.resolve("steamapps/appmanifest_342560.acf"), "\"AppState\" { \"appid\" \"342560\" \"installdir\" \"自定义名称\" }");
        List<Path> before; try (var walk = Files.walk(root)) { before = walk.sorted().toList(); }
        var found = SteamGameLocator.find(List.of(steam, other, steam));
        check(found.size() == 3 && found.containsAll(List.of(one, two, three)), "modern/legacy libraries, custom manifest name, Unicode paths and duplicates");
        try (var walk = Files.walk(root)) { check(walk.sorted().toList().equals(before), "discovery creates no files or instance"); }
        Files.delete(two.resolve("Airships.json"));
        check(SteamGameLocator.find(List.of(steam)).size() == 2, "incomplete install excluded");
        Files.writeString(steam.resolve("steamapps/libraryfolders.vdf"), "malformed \"path\" \"relative/path\"");
        Files.writeString(steam.resolve("config/libraryfolders.vdf"), "broken");
        check(SteamGameLocator.find(List.of(steam)).equals(List.of(one)), "bad library config still checks main library");
        check(SteamGameLocator.find(List.of(root.resolve("missing"))).isEmpty(), "no Steam returns empty for manual selection");
        Path framework = Files.createDirectories(root.resolve("framework"));
        try (var first = FrameworkUse.open(framework); var nested = FrameworkUse.open(framework)) {
            first.close();
            check(lockBlocked(framework), "nested Java use still blocks exclusive PowerShell update lock");
        }
        check(!lockBlocked(framework), "closing final Java use releases update lock");
        Files.createDirectories(framework.resolve(".acbric-maintenance"));
        Files.writeString(framework.resolve(".acbric-maintenance/pending.json"), "{}");
        try (var unexpected = FrameworkUse.open(framework)) { throw new AssertionError("pending update allowed"); }
        catch (java.io.IOException ex) { check(ex.getMessage().contains("UPDATE_RECOVERY_REQUIRED"), "interrupted update blocks startup"); }
        return checks;
    }
    private static boolean lockBlocked(Path root) throws Exception {
        String path = root.resolve(".acbric-framework.lock").toString().replace("'", "''");
        var p = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "try { $f=[IO.File]::Open('" + path + "','Open','ReadWrite','None'); $f.Dispose(); exit 0 } catch { exit 7 }").start();
        if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("lock probe timeout"); }
        if (p.exitValue() != 0 && p.exitValue() != 7) throw new AssertionError("lock probe failed");
        return p.exitValue() == 7;
    }
    /** 单独执行本机 Steam 的只读检测；不启动游戏。 */
    public static void main(String[] args) { for (Path path : SteamGameLocator.find()) System.out.println(path); }
}
