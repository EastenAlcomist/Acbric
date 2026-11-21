/* SteamGameLocator.java — 只读识别 Windows Steam 注册表和游戏库，不扫描整盘或改写 Steam 配置。 */
package net.fabricacs.acbric;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class SteamGameLocator {
    static final String APP_ID = "342560";
    private static final Pattern PAIR = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private SteamGameLocator() {}

    static List<Path> find() {
        return find(steamRoots());
    }

    private static Set<Path> steamRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        registry(roots, "HKCU\\Software\\Valve\\Steam", "SteamPath");
        registry(roots, "HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath");
        registry(roots, "HKLM\\SOFTWARE\\Valve\\Steam", "InstallPath");
        for (String variable : List.of("ProgramFiles(x86)", "ProgramFiles")) {
            String value = System.getenv(variable);
            if (value != null) roots.add(Path.of(value, "Steam"));
        }
        return roots;
    }

    private static void registry(Set<Path> roots, String key, String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("reg.exe", "query", key, "/v", name).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) return;
            if (process.exitValue() != 0) return;
            String output = new String(process.getInputStream().readAllBytes(), Charset.forName(System.getProperty("native.encoding", Charset.defaultCharset().name())));
            for (String line : output.lines().toList()) {
                String[] parts = line.trim().split("\\s+", 3);
                if (parts.length == 3 && parts[0].equalsIgnoreCase(name) && parts[1].equals("REG_SZ")) roots.add(Path.of(parts[2]));
            }
        } catch (IOException | RuntimeException ignored) { /* 无 Steam 或拒绝访问时仍允许手动选择。 */ }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
    }

    static List<Path> find(Collection<Path> roots) {
        Set<Path> libraries = new LinkedHashSet<>(), games = new LinkedHashSet<>();
        for (Path root : roots) {
            libraries.add(root.toAbsolutePath().normalize());
            for (String file : List.of("steamapps/libraryfolders.vdf", "config/libraryfolders.vdf")) {
                for (var pair : pairs(root.resolve(file))) {
                    if (pair.getKey().equals("path") || pair.getKey().matches("[0-9]+")) {
                        try { Path p = Path.of(pair.getValue()); if (p.isAbsolute()) libraries.add(p.normalize()); }
                        catch (InvalidPathException ignored) { }
                    }
                }
            }
        }
        for (Path library : libraries) {
            Path common = library.resolve("steamapps/common");
            Set<Path> candidates = new LinkedHashSet<>();
            for (var pair : pairs(library.resolve("steamapps/appmanifest_342560.acf"))) {
                if (pair.getKey().equals("installdir")) {
                    try {
                        Path p = Path.of(pair.getValue());
                        if (!p.isAbsolute() && p.getNameCount() == 1 && !pair.getValue().equals("..")) candidates.add(common.resolve(p));
                    } catch (InvalidPathException ignored) { }
                }
            }
            candidates.add(common.resolve("Airships Conquer the Skies"));
            for (Path candidate : candidates) {
                if (!Files.isRegularFile(candidate.resolve("Airships.json"))) continue;
                try {
                    Path probe = Path.of(System.getProperty("java.io.tmpdir"), "acbric-steam-check-" + UUID.randomUUID());
                    games.add(ExternalGameInstallation.inspect(candidate, probe).install());
                } catch (IOException | RuntimeException ignored) { /* 不完整或版本结构不同的目录交给手动检查。 */ }
            }
        }
        return List.copyOf(games);
    }

    /**
     * 只有游戏位于 Steam 库且对应 App 342560 清单时返回该库所属 Steam 客户端。
     * 返回值用于 -applaunch；它本身不证明账号所有权，所有权由 Steam 启动 App 时校验。
     */
    static Optional<Path> steamExecutable(Path game) {
        return steamExecutable(steamRoots(), game);
    }

    static Optional<Path> steamExecutable(Collection<Path> roots, Path game) {
        try {
            Path target = game.toRealPath();
            for (Path root : roots) {
                Path executable = root.toAbsolutePath().normalize().resolve("steam.exe");
                if (!Files.isRegularFile(executable)) continue;
                for (Path library : libraries(root)) {
                    if (!manifestMatches(library, target)) continue;
                    return Optional.of(executable);
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return Optional.empty();
    }

    private static Set<Path> libraries(Path root) {
        Set<Path> libraries = new LinkedHashSet<>();
        libraries.add(root.toAbsolutePath().normalize());
        for (String file : List.of("steamapps/libraryfolders.vdf", "config/libraryfolders.vdf")) {
            for (var pair : pairs(root.resolve(file))) {
                if (pair.getKey().equals("path") || pair.getKey().matches("[0-9]+")) {
                    try {
                        Path path = Path.of(pair.getValue());
                        if (path.isAbsolute()) libraries.add(path.normalize());
                    } catch (InvalidPathException ignored) {
                    }
                }
            }
        }
        return libraries;
    }

    private static boolean manifestMatches(Path library, Path game) {
        String installdir = null;
        boolean appId = false;
        for (var pair : pairs(library.resolve("steamapps/appmanifest_" + APP_ID + ".acf"))) {
            if (pair.getKey().equals("appid") && pair.getValue().equals(APP_ID)) appId = true;
            if (pair.getKey().equals("installdir")) installdir = pair.getValue();
        }
        if (!appId || installdir == null || installdir.isBlank()
                || installdir.equals("..") || installdir.contains("/") || installdir.contains("\\")
                || installdir.contains(":")) return false;
        try {
            return library.resolve("steamapps/common").resolve(installdir).toRealPath().equals(game);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    /** 读取 VDF 中的标量键值，兼容新库的 path 与旧库的数字键；忽略注释。 */
    private static List<Map.Entry<String, String>> pairs(Path file) {
        List<Map.Entry<String, String>> pairs = new ArrayList<>();
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4 * 1024 * 1024) return pairs;
            String value = Files.readString(file).replaceAll("(?m)^\\s*//.*$", "");
            var matcher = PAIR.matcher(value);
            while (matcher.find()) pairs.add(Map.entry(unescape(matcher.group(1)), unescape(matcher.group(2))));
        } catch (IOException | RuntimeException ignored) { }
        return pairs;
    }
    private static String unescape(String value) { return value.replace("\\\\", "\\").replace("\\\"", "\""); }
}
