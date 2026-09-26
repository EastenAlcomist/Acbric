/* ExternalMods.java — 外部发行的统一 MOD 目录与会话锁；同一目录不能被多个游戏实例同时改写。 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

final class ExternalMods implements AutoCloseable {
    static final String PROPERTY = "acbric.external.mods";
    private final FileChannel channel;
    private final FileLock lock;
    private ExternalMods(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }

    static Path directory(Path framework, ExternalGameInstallation.Plan plan) throws IOException {
        Path root = framework.toAbsolutePath().normalize().resolve("mods");
        validate(root, plan);
        if (framework.startsWith(root))
            throw new IOException("MODS_OVERLAP / MOD 目录不能包含框架: " + root);
        return root;
    }

    static void validate(Path root, ExternalGameInstallation.Plan plan) throws IOException {
        ModSelection.rejectLinks(root);
        for (Path other : new Path[]{plan.install(), plan.instance()}) {
            if (root.startsWith(other) || other.startsWith(root))
                throw new IOException("MODS_OVERLAP / MOD directory overlaps game or instance / MOD 目录与游戏或实例重叠: " + root);
        }
        if (Files.exists(root) && !Files.isDirectory(root))
            throw new IOException("MODS_UNAVAILABLE / MOD path is not a directory / MOD 路径不是文件夹: " + root);
    }

    static ExternalMods open(Path root, ExternalGameInstallation.Plan plan) throws IOException {
        validate(root, plan);
        Files.createDirectories(root);
        Path file = root.resolve(".acbric-mods.lock"); ModSelection.rejectLinks(file);
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new OverlappingFileLockException();
            return new ExternalMods(channel, lock);
        } catch (IOException | RuntimeException ex) {
            channel.close();
            if (ex instanceof OverlappingFileLockException)
                throw new IOException("MODS_BUSY / Shared MOD folder is in use; close the other game / 共用 MOD 目录正在使用，请关闭另一个游戏: " + root);
            throw ex;
        }
    }

    @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
