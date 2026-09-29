/*
 * ConfigFileStore.java — 同目录暂存、旧文件备份、原子替换和乐观冲突检查。
 * 锁协调使用本接口的写入者；外部编辑器不遵守锁，检查与替换间仍有竞态窗口。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.config.ConfigException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Locale;

public final class ConfigFileStore {
    public static final int MAX_BYTES = 1024 * 1024;
    private final Path path;
    public ConfigFileStore(Path root, String modId, String name) {
        CampaignDataStore.validateModId(modId);
        name(modId); name(name);
        path = root.toAbsolutePath().normalize().resolve(modId).resolve(name + ".json");
    }
    private static void name(String name) {
        if (name == null || !name.matches("[a-z][a-z0-9_-]{0,63}")
                || name.toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            throw new IllegalArgumentException("Invalid portable config name: " + name);
        }
    }
    public Path path() { return path; }
    public Path backupPath() { return path.resolveSibling(path.getFileName() + ".bak"); }
    private Path lockPath() { return path.resolveSibling(path.getFileName() + ".lock"); }
    private static void checkPath(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path segment : path) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                var attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("Linked/special config path: " + current);
            }
        }
    }
    public byte[] read() throws IOException {
        checkPath(path);
        // exists 会把部分权限错误也报告为 false；仅真实不存在才能退到默认值。
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException missing) { return null; }
        if (!attributes.isRegularFile()) throw new IOException("Not a config file: " + path);
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Config exceeds 1 MiB: " + path);
            return bytes;
        }
    }
    public void save(byte[] expected, byte[] next) throws IOException {
        if (next.length > MAX_BYTES) throw new IOException("Config exceeds 1 MiB: " + path);
        checkPath(path); checkPath(lockPath()); checkPath(backupPath());
        Files.createDirectories(path.getParent());
        checkPath(path);
        try (FileChannel channel = FileChannel.open(lockPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             var lock = channel.tryLock()) {
            if (lock == null) throw conflict("Config is locked");
            byte[] current = read();
            if (!Arrays.equals(expected, current)) throw conflict("Config changed on disk; reload before saving");
            if (Arrays.equals(current, next)) return;
            if (current != null) replace(backupPath(), current);
            replace(path, next);
        } catch (OverlappingFileLockException e) { throw conflict("Config is locked in this process"); }
    }
    private ConfigException conflict(String message) { return new ConfigException(ConfigException.Code.CONFLICT, message + ": " + path); }
    private static void replace(Path target, byte[] bytes) throws IOException {
        checkPath(target);
        Path temp = Files.createTempFile(target.getParent(), ".acbric-config-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            // 不支持原子移动时明确失败，不退回可能截断原文件的覆盖写入。
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { /* 暂存清理失败不能掩盖主操作结果。 */ }
        }
    }
}
