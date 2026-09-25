/*
 * ModContentFingerprint.java — 对 Loader 实际解析的根目录计算内容指纹，覆盖嵌套 JAR 和开发目录。
 * 根顺序、相对路径和内容参与摘要；压缩方式、时间戳和绝对路径不参与。失败不得产生成功指纹。
 */
package net.fabricacs.api.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipFile;

final class ModContentFingerprint {
    static final String ALGORITHM = "acbric-loaded-roots-v1";
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final int MAX_FILES = 50_000;

    record Result(String digest, String status) {}
    record FileInfo(Path path, String relative, long size, Object key, java.nio.file.attribute.FileTime modified) {}

    /** 一次导出的共享预算，避免大量 MOD 使启动读取量没有上限。 */
    static final class Budget {
        private long bytes;
        Budget(long bytes) { this.bytes = bytes; }
        void consume(long count) throws IOException {
            if (count > bytes) throw new FingerprintFailure("LIMIT_EXCEEDED");
            bytes -= count;
        }
    }

    private static final class FingerprintFailure extends IOException {
        FingerprintFailure(String status) { super(status); }
    }

    static Result inspect(List<Path> roots, Budget budget) {
        try {
            return new Result(hash(roots, budget), "OK");
        } catch (FingerprintFailure failure) {
            return new Result("", failure.getMessage());
        } catch (IOException | RuntimeException failure) {
            // 不把绝对路径、异常堆栈写入可交换清单。
            return new Result("", "UNREADABLE");
        }
    }

    private static String hash(List<Path> roots, Budget budget) throws IOException {
        if (roots == null || roots.isEmpty() || roots.size() > 64) throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
        MessageDigest digest = sha256();
        text(digest, ALGORITHM);
        number(digest, roots.size());
        long total = 0;
        int fileCount = 0;
        for (Path root : roots) {
            checkArchive(root);
            List<FileInfo> files = inventory(root);
            fileCount += files.size();
            if (fileCount > MAX_FILES) throw new FingerprintFailure("LIMIT_EXCEEDED");
            number(digest, files.size());
            for (FileInfo file : files) {
                if (file.size() > MAX_BYTES - total) throw new FingerprintFailure("LIMIT_EXCEEDED");
                total += file.size();
                text(digest, file.relative());
                number(digest, file.size());
                long read = 0;
                try (var input = Files.newInputStream(file.path())) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        budget.consume(count);
                        read += count;
                        if (read > file.size()) throw new FingerprintFailure("CHANGED_DURING_READ");
                        digest.update(buffer, 0, count);
                    }
                }
                if (read != file.size()) throw new FingerprintFailure("CHANGED_DURING_READ");
            }
            // 再看目录列表与属性，识别常见的边读边改/新增/删除；不是文件系统事务锁。
            if (!files.equals(inventory(root))) throw new FingerprintFailure("CHANGED_DURING_READ");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<FileInfo> inventory(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
        Path realRoot = root.toRealPath();
        List<FileInfo> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            private int nodes;
            private void boundary(Path path) throws IOException {
                if (++nodes > MAX_FILES * 2) throw new FingerprintFailure("LIMIT_EXCEEDED");
                if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(realRoot))
                    throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
            }
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                boundary(dir);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                boundary(path);
                if (!attrs.isRegularFile()) throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
                if (files.size() >= MAX_FILES) throw new FingerprintFailure("LIMIT_EXCEEDED");
                String relative = root.relativize(path).toString().replace('\\', '/');
                files.add(new FileInfo(path, relative, attrs.size(), attrs.fileKey(), attrs.lastModifiedTime()));
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(FileInfo::relative));
        if (files.isEmpty()) throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
        return files;
    }

    /** ZIP 文件系统会隐藏重复条目，因此在散列根视图前拒绝这种歧义归档。 */
    private static void checkArchive(Path root) throws IOException {
        String scheme = root.getFileSystem().provider().getScheme();
        if (scheme.equals("file")) return;
        if (!scheme.equals("jar")) throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
        String uri = root.toUri().getRawSchemeSpecificPart();
        int separator = uri.lastIndexOf("!/");
        if (separator < 0) throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
        var archiveUri = java.net.URI.create(uri.substring(0, separator));
        if (!"file".equals(archiveUri.getScheme())) throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(Path.of(archiveUri).toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!names.add(name) || name.contains("\\") || name.startsWith("/")
                        || Arrays.asList(name.split("/", -1)).contains("..")
                        || Arrays.asList(name.split("/", -1)).contains("."))
                    throw new FingerprintFailure("UNSUPPORTED_ORIGIN");
                if (names.size() > MAX_FILES * 2) throw new FingerprintFailure("LIMIT_EXCEEDED");
            }
        }
    }

    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    static void number(MessageDigest digest, long value) { digest.update(ByteBuffer.allocate(8).putLong(value).array()); }
    static void text(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        number(digest, bytes.length);
        digest.update(bytes);
    }
}
