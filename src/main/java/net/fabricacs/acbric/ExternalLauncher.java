/* ExternalLauncher.java — 正式外部启动入口；验证发行清单后在独立实例工作目录启动子 JVM，保存完整输出。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

public final class ExternalLauncher {
    static final String CORE_VERSION = "0.3.3-dev.25";
    private ExternalLauncher() {}

    public static void main(String[] args) { System.exit(run(args)); }

    static Map<String, String> options(String[] args) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !Set.of("--game-dir", "--instance-dir").contains(args[i])
                    || args[i + 1].isBlank() || result.putIfAbsent(args[i], args[i + 1]) != null)
                throw new IOException("USAGE: --game-dir <installation> --instance-dir <instance> / 请明确指定游戏和实例目录");
        }
        if (result.size() != 2) throw new IOException("USAGE: both paths required / 两个路径均须指定");
        return result;
    }

    static Path requireCore(String value) throws IOException {
        if (value == null || value.isBlank()) throw new IOException("CORE_REQUIRED / 请通过发行包 start.ps1 启动，缺少核心 API 路径");
        Path path = Path.of(value).toAbsolutePath().normalize();
        ModSelection.rejectLinks(path);
        if (!Files.isRegularFile(path)) throw new IOException("CORE_MISSING / 核心 API 文件不存在: " + path);
        try (ZipFile zip = new ZipFile(path.toFile())) {
            for (String name : List.of("fabric.mod.json", "net/fabricacs/api/mixin/ExternalPathsMixin.class",
                    "net/fabricacs/api/mixin/ExternalMainMixin.class", "net/fabricacs/api/mixin/ExternalLaunchSettingsMixin.class",
                    "net/fabricacs/api/impl/ExternalTextureCache.class")) {
                if (zip.getEntry(name) == null) throw new IOException("CORE_INVALID / 核心 API 缺少外部隔离支持: " + name);
            }
        }
        return path;
    }

    /** 清单只检测缺失/变更，不构成来源签名。明确类路径避免捡到用户误放的游戏库。 */
    static List<Path> verifyBundle(Path root) throws IOException {
        Properties manifest = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("bundle.properties"), StandardCharsets.UTF_8)) { manifest.load(reader); }
        if (!"1".equals(manifest.getProperty("schema")) || !CORE_VERSION.equals(manifest.getProperty("api.version")))
            throw new IOException("BUNDLE_INVALID / 发行清单版本不匹配");
        List<Path> loaders = new ArrayList<>();
        boolean core = false;
        for (String name : manifest.stringPropertyNames().stream().sorted().toList()) {
            if (!name.startsWith("sha256.")) continue;
            String relative = name.substring(7);
            if (!relative.matches("(loader-libs/[^/\\\\]+\\.jar|core/acbric-api\\.jar)"))
                throw new IOException("BUNDLE_INVALID / 清单文件路径无效: " + relative);
            Path file = root.resolve(relative).normalize();
            ModSelection.rejectLinks(file);
            if (!Files.isRegularFile(file) || !sha256(file).equals(manifest.getProperty(name)))
                throw new IOException("BUNDLE_CHANGED / 发行文件缺失或已变更: " + relative);
            if (relative.startsWith("loader-libs/")) loaders.add(file); else core = true;
        }
        if (!core || loaders.isEmpty()) throw new IOException("BUNDLE_INVALID / 缺少启动层或核心清单");
        return List.copyOf(loaders);
    }

    static String sha256(Path file) throws IOException {
        try (var stream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int count; (count = stream.read(buffer)) != -1;) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static int run(String[] args) {
        Path early = null;
        try {
            early = Files.createTempFile("acbric-launch-", ".log");
            System.out.println("Bootstrap log / 引导日志: " + early);
            var options = options(args);
            ExternalGameInstallation.runtime(System.getProperty("os.name"), Runtime.version().feature(), System.getProperty("os.arch"));
            Path ownJar = Path.of(ExternalLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path bundle = ownJar.getParent().getParent().toRealPath();
            List<Path> loaders = verifyBundle(bundle);
            Path core = requireCore(bundle.resolve("core/acbric-api.jar").toString());
            var plan = ExternalGameInstallation.inspect(Path.of(options.get("--game-dir")), Path.of(options.get("--instance-dir")));
            if (plan.instance().equals(bundle) || bundle.startsWith(plan.instance())
                    || plan.instance().startsWith(bundle.resolve("loader-libs")) || plan.instance().startsWith(bundle.resolve("core"))
                    || plan.instance().startsWith(bundle.resolve("runtime")))
                throw new IOException("INSTANCE_OVERLAPS_FRAMEWORK / 实例不能包含框架或使用框架核心目录");
            // 预检查锁释放后由子进程重新获取并持有整个会话；并发竞争只能有一个游戏进程胜出。
            try (var lease = ExternalPreflight.InstanceLease.open(plan)) { lease.prepareDirectories(plan.instance()); }
            Path logDir = plan.instance().resolve("logs/acbric/launcher");
            ModSelection.rejectLinks(logDir); Files.createDirectories(logDir);
            Path log = Files.createTempFile(logDir, "launch-", ".log");
            List<String> command = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xmx4G",
                    "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "-Dacbric.external.install=" + plan.install(), "-Dacbric.external.instance=" + plan.instance(),
                    "-Dacbric.external.core=" + core, "-Dfabric.addMods=" + core,
                    "-cp", String.join(File.pathSeparator, loaders.stream().map(Path::toString).toList()),
                    "net.fabricmc.loader.impl.launch.knot.KnotClient"));
            Files.writeString(early, "install=" + plan.install() + "\ninstance=" + plan.instance() + "\nlog=" + log + "\n", StandardCharsets.UTF_8);
            System.out.println("Game log / 游戏启动日志: " + log);
            for (String warning : plan.warnings()) System.out.println(warning);
            ProcessBuilder builder = new ProcessBuilder(command).directory(plan.instance().toFile())
                    .redirectErrorStream(true).redirectOutput(log.toFile());
            // 不继承全局 JVM 注入参数，避免原版数据目录/类路径设置污染正式实例。
            for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")) builder.environment().remove(name);
            Process child = builder.start();
            Thread cleanup = new Thread(() -> { if (child.isAlive()) child.destroy(); }, "acbric-child-stop");
            Runtime.getRuntime().addShutdownHook(cleanup);
            int code;
            try { code = child.waitFor(); }
            catch (InterruptedException ex) { child.destroy(); throw ex; }
            finally { Runtime.getRuntime().removeShutdownHook(cleanup); }
            Files.writeString(early, "exit=" + code + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            System.out.println("Game exit / 游戏退出: " + code + "; " + log);
            return code;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            if (early != null) try (var writer = new PrintWriter(Files.newBufferedWriter(early, StandardCharsets.UTF_8, StandardOpenOption.APPEND))) { ex.printStackTrace(writer); }
            catch (IOException ignored) { }
            System.err.println("Launch failed / 启动失败: " + ex.getMessage());
            return 2;
        }
    }
}
