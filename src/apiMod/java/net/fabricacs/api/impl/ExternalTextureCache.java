/* ExternalTextureCache.java — 按资源来源与内容隔离纹理缓存，仅按需复制当前贴图及可用旧缓存，不移动原资源。 */
package net.fabricacs.api.impl;

import net.fabricacs.management.ModSelection;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class ExternalTextureCache {
    private ExternalTextureCache() {}
    private record Stamp(Path path, long size, FileTime modified, Object key) {}
    private record Prepared(List<Stamp> stamps, Path directory) {}
    private static final Map<Path, Prepared> prepared = new LinkedHashMap<>();
    private static final AtomicLong revision = new AtomicLong(System.currentTimeMillis() - 86400000L);
    public static boolean active() { return System.getProperty("acbric.external.install") != null; }
    /** 原生 MOD 重载时清空会话元数据，下一次访问重算内容哈希；磁盘缓存按内容复用。 */
    public static synchronized void invalidate() { prepared.clear(); }

    public static synchronized File image(File input) {
        if (!active()) return input;
        try {
            Path source = input.toPath().toAbsolutePath().normalize();
            Path parent = source.getParent();
            if (parent == null || parent.getParent() == null) throw new IOException("Invalid texture path: " + source);
            Path origin = parent.getParent();
            String name = source.getFileName().toString();
            // 保留原生 images/generated 两次查找及 MOD→DLC→基础资源优先级。
            Path png = origin.resolve("images").resolve(name);
            Path generated = origin.resolve("generated").resolve(name);
            Path raw = origin.resolve("generated").resolve(name + ".tex");
            Path legacy = origin.resolve("images").resolve(name + ".tex");
            List<Path> inputs = List.of(source, png, generated, raw, legacy);
            List<Stamp> stamps = new ArrayList<>();
            for (Path file : inputs) {
                if (Files.isRegularFile(file)) {
                    var attr = Files.readAttributes(file, BasicFileAttributes.class);
                    stamps.add(new Stamp(file, attr.size(), attr.lastModifiedTime(), attr.fileKey()));
                } else if (Files.exists(file)) throw new IOException("Texture input is not a file: " + file);
                else stamps.add(new Stamp(file, -1, FileTime.fromMillis(0), null));
            }
            // 空查询不建立磁盘目录；后面的原生回退仍看到不存在的路径。
            if (stamps.stream().allMatch(s -> s.size() < 0)) return input;
            Prepared cached = prepared.get(source);
            if (cached == null || !cached.directory().startsWith(instance()) || !cached.stamps().equals(stamps)) {
                MessageDigest digest = sha256();
                digest.update(origin.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for (Stamp stamp : stamps) {
                    digest.update(("\n" + stamp.path().getParent().getFileName() + "/" + stamp.path().getFileName() + ":" + stamp.size() + ":" + stamp.modified()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (stamp.size() >= 0) try (var stream = Files.newInputStream(stamp.path())) {
                        byte[] bytes = new byte[65536]; int n;
                        while ((n = stream.read(bytes)) >= 0) digest.update(bytes, 0, n);
                    }
                }
                Path root = instance().resolve("cache/game-textures/v1").resolve(HexFormat.of().formatHex(digest.digest()));
                ModSelection.rejectLinks(root); Files.createDirectories(root.resolve("images")); Files.createDirectories(root.resolve("generated"));
                long time = revision.updateAndGet(old -> Math.max(old + 2, System.currentTimeMillis() - 86400000L));
                copy(source, root.resolve(parent.getFileName().toString()).resolve(name), time);
                copy(png, root.resolve("images").resolve(name), time);
                copy(generated, root.resolve("generated").resolve(name), time);
                // 原图存在时重新生成，旧 raw 没有内容校验，不能仅凭时间戳认定与原图一致。
                // 仅 raw 的资源仍支持；优先 generated，保持旧移动逻辑的胜出者，原文件不删。
                Path oldRaw = Files.isRegularFile(raw) ? raw : legacy;
                if (Files.isRegularFile(oldRaw) && !Files.isRegularFile(source)
                        && !Files.isRegularFile(png) && !Files.isRegularFile(generated)) {
                    long imageTime = Math.max(lastModified(root.resolve("images").resolve(name)), lastModified(root.resolve("generated").resolve(name)));
                    copy(oldRaw, root.resolve("generated").resolve(name + ".tex"), Math.max(time, imageTime + 1));
                }
                cached = new Prepared(List.copyOf(stamps), root);
                if (prepared.size() >= 32768) prepared.clear();
                prepared.put(source, cached);
            }
            Path mapped = cached.directory().resolve(parent.getFileName().toString()).resolve(name);
            if (!mapped.normalize().startsWith(cached.directory())) throw new IOException("Invalid cache path");
            ModSelection.rejectLinks(mapped);
            Path texture = cached.directory().resolve("generated").resolve(name + ".tex");
            ModSelection.rejectLinks(texture);
            // 原生会 mmap raw，损坏长度可能在异常分支留下 Windows 映射句柄；先删实例坏缓存再走 PNG。
            if (Files.isRegularFile(texture) && !validRaw(Files.size(texture))) Files.delete(texture);
            return mapped.toFile();
        } catch (IOException ex) { throw new UncheckedIOException("Cannot isolate texture cache / 无法隔离纹理缓存，不写入原资源", ex); }
    }

    public static Path instance() {
        String value = System.getProperty("acbric.external.instance");
        if (value == null) throw new IllegalStateException("Missing instance / 缺少实例目录");
        return Path.of(value);
    }
    private static long lastModified(Path path) throws IOException { return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0; }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); } }
    private static void copy(Path source, Path target, long time) throws IOException {
        if (!Files.isRegularFile(source)) return;
        ModSelection.rejectLinks(target);
        if (target.toString().endsWith(".tex") && Files.isRegularFile(target) && validRaw(Files.size(target))) return;
        if (Files.isRegularFile(target) && Files.mismatch(source, target) == -1) return;
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "texture-", ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(temp, FileTime.fromMillis(time));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static boolean validRaw(long bytes) {
        for (long size = 16; size <= 16384; size *= 2) if (bytes == size * size * 4) return true;
        return false;
    }
}
