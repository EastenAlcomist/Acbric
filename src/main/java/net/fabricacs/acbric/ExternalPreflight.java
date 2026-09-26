/* ExternalPreflight.java — 外部安装检查的独立入口；先创建临时诊断，再检查实例，不调用 Fabric 或游戏。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class ExternalPreflight {
    private ExternalPreflight() {}

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    /** 即使游戏/实例路径错了，也将失败留在系统临时目录；不静默寻找另一份游戏。 */
    static int run(String[] args) {
        Path report = null;
        Properties values = new Properties();
        values.setProperty("schema", "1");
        values.setProperty("time", Instant.now().toString());
        values.setProperty("java.version", System.getProperty("java.version"));
        values.setProperty("os.arch", System.getProperty("os.arch"));
        try {
            report = Files.createTempDirectory("acbric-preflight-").resolve("preflight.properties");
            System.out.println("[Acbric] Preflight log / 预检查日志: " + report);
            values.setProperty("status", "CHECKING"); write(report, values);
            Map<String, String> options = new HashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 == args.length || !Set.of("--game-dir", "--instance-dir").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null)
                    throw new IOException("USAGE: --game-dir <installation> --instance-dir <instance> / 请指定游戏根目录和独立实例目录");
            }
            if (!options.keySet().equals(Set.of("--game-dir", "--instance-dir"))) throw new IOException("USAGE: --game-dir <installation> --instance-dir <instance> / 两个路径均须显式指定");
            values.setProperty("requested.install", options.get("--game-dir"));
            values.setProperty("requested.instance", options.get("--instance-dir"));
            ExternalGameInstallation.runtime(System.getProperty("os.name"), Runtime.version().feature(), System.getProperty("os.arch"));
            var plan = ExternalGameInstallation.inspect(Path.of(options.get("--game-dir")), Path.of(options.get("--instance-dir")));
            try (InstanceLease ignored = InstanceLease.open(plan)) {
                ignored.prepareDirectories(plan.instance());
                describe(values, plan);
                values.setProperty("status", "PREFLIGHT_OK_NOT_LAUNCHED");
                Path directory = plan.instance().resolve("logs/acbric/preflight");
                ModSelection.rejectLinks(directory); Files.createDirectories(directory);
                Path copy = directory.resolve(report.getParent().getFileName() + ".properties");
                write(copy, values);
                System.out.println("[Acbric] Instance report / 实例报告: " + copy);
            }
            write(report, values);
            System.out.println("[Acbric] Preflight passed; game NOT started / 预检查通过，未启动游戏。Version=" + plan.identity().rawVersion());
            for (String warning : plan.warnings()) System.out.println("[Acbric] " + warning);
            return 0;
        } catch (IOException | RuntimeException ex) {
            values.setProperty("status", "PREFLIGHT_FAILED");
            values.setProperty("failure", ex.toString());
            if (report != null) try { write(report, values); } catch (IOException logging) { System.err.println("[Acbric] Cannot save report / 无法保存诊断: " + logging); }
            System.err.println("[Acbric] " + ex.getMessage());
            return 2;
        }
    }

    static void describe(Properties values, ExternalGameInstallation.Plan plan) {
        values.setProperty("install", plan.install().toString());
        values.setProperty("instance", plan.instance().toString());
        values.setProperty("native", plan.nativeDir().toString());
        values.setProperty("game.version", plan.identity().rawVersion());
        values.setProperty("game.fingerprint", plan.identity().fingerprint());
        values.setProperty("mainClass", ExternalGameInstallation.MAIN);
        for (int i = 0; i < plan.classPath().size(); i++) values.setProperty("classpath." + i, plan.classPath().get(i).toString());
        for (int i = 0; i < plan.identity().archives().size(); i++) {
            var archive = plan.identity().archives().get(i);
            values.setProperty("archive." + i + ".name", archive.name());
            values.setProperty("archive." + i + ".sha256", archive.sha256());
        }
        for (int i = 0; i < plan.warnings().size(); i++) values.setProperty("warning." + i, plan.warnings().get(i));
    }

    private static void write(Path path, Properties values) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            values.store(writer, "Acbric preflight; internal diagnostic format");
        } catch (FileAlreadyExistsException ex) {
            // 仅重写本轮生成的随机临时报告，不覆盖已有实例配置/用户文件。
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) { values.store(writer, "Acbric preflight; internal diagnostic format"); }
        }
    }

    /** 预检查与原型进程使用同一实例锁；游戏安装和用户原档从不作为锁位置。 */
    static final class InstanceLease implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private InstanceLease(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        static InstanceLease open(ExternalGameInstallation.Plan plan) throws IOException {
            Path root = ExternalGameInstallation.validateInstance(plan.install(), plan.instance());
            Files.createDirectories(root);
            Path lockFile = root.resolve(".acbric-instance.lock"); ModSelection.rejectLinks(lockFile);
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) throw ExternalGameInstallation.failure("INSTANCE_BUSY", "Instance is in use", "实例正在使用，请关闭另一进程");
                return new InstanceLease(channel, lock);
            } catch (IOException | RuntimeException ex) {
                channel.close();
                if (ex instanceof OverlappingFileLockException) throw ExternalGameInstallation.failure("INSTANCE_BUSY", "Instance is in use", "实例正在使用，请关闭另一进程");
                throw ex;
            }
        }
        void prepareDirectories(Path root) throws IOException {
            for (String name : List.of("mods", "config", "userdata", "logs/acbric", ".fabric", "data/acbric", "generated")) {
                Path dir = root.resolve(name);
                try {
                    ModSelection.rejectLinks(dir); Files.createDirectories(dir);
                    Path test = Files.createTempFile(dir, ".acbric-write-check-", ".tmp"); Files.delete(test);
                } catch (IOException ex) { throw ExternalGameInstallation.failure("INSTANCE_UNAVAILABLE", dir + ": " + ex.getMessage(), "实例目录无法安全写入，不回退到原版用户数据"); }
            }
        }
        @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
}
