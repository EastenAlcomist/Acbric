/*
 * BundledResourceStore.java — 内嵌资源内部存储器：校验路径、按哈希维护归属、保护用户修改并恢复中断事务。
 * 暂存和备份位于原版 MOD 扫描目录之外；该实现不是面向 MOD 作者的稳定 API。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 按文件归属执行可恢复的目录替换；不属于公开扩展协议。 */
final class BundledResourceStore {
    static final String MANIFEST = ".acbric-bundle.json";
    private static final String PREFIX = "acbric_vanilla/";

    private BundledResourceStore() {}

    /** 先取得每个 MOD 的文件锁并恢复旧事务，再准备此次安装或显式迁移。 */
    static void install(Path archive, Path mods, String id, boolean migrate) throws IOException {
        if (!id.matches("[a-z][a-z0-9_-]{1,63}")) throw new IOException("Invalid Fabric mod ID: " + id);
        mods = mods.toAbsolutePath().normalize();
        safeDirectories(mods);
        Path target = mods.resolve(id);
        rejectLinks(target);
        Path control = mods.getParent().resolve(".acbric-bundles").resolve(id);
        safeDirectories(control);
        Path lockPath = control.resolve("lock");
        rejectLinks(lockPath);
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another bundle update is running: " + id);
            recover(control, target, id);
            update(archive, target, control, id, migrate);
        } catch (OverlappingFileLockException e) {
            throw new IOException("Another bundle update is running: " + id, e);
        }
    }

    /** 比较旧归属、当前文件与新包内容；只更新未被用户修改的受管理文件。 */
    private static void update(Path archive, Path target, Path control, String id, boolean migrate) throws IOException {
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists && !Files.isDirectory(target)) throw new IOException("Bundle destination is not a directory: " + target);
        State old = exists ? readState(target, id) : null;
        if (exists && old == null && !migrate) {
            DiagnosticHub.publish(id,net.fabricacs.api.diagnostics.DiagnosticMessage.Level.WARN,"Unmanaged resources preserved; explicit migration required / 已保留无归属资源，需要显式迁移: "+target,null);
            System.err.println("[Acbric] Unmanaged bundle directory preserved; explicit backup/migration required: " + target);
            return;
        }
        if (migrate && (!exists || old != null)) throw new IOException("Migration requires an existing unmanaged directory: " + target);

        Path incoming = Files.createTempDirectory(control, "incoming-");
        String revision = UUID.randomUUID().toString();
        Path stage = control.resolve("stage-" + revision);
        try {
            if (!unpack(archive, incoming, id)) {
                if (migrate) throw new IOException("No bundled resources to adopt: " + archive);
                if (old == null) return;
                // 已受管理的 MOD 可以撤掉整个资源包；删除时仍检查归属，保留用户修改。
            }
            Map<String, String> desired = files(incoming);
            Map<String, String> before = exists ? snapshot(target) : Map.of();
            Files.createDirectory(stage);
            if (exists) copyTree(target, stage);
            Map<String, String> owned = new TreeMap<>(old == null ? Map.of() : old.files);
            Map<String, String> conflicts = new TreeMap<>();

            if (old != null) {
                for (var entry : old.files.entrySet()) {
                    if (desired.containsKey(entry.getKey())) continue;
                    Path current = resourcePath(stage, entry.getKey());
                    if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) owned.remove(entry.getKey());
                    else if (Files.isRegularFile(current) && hash(current).equals(entry.getValue())) {
                        Files.delete(current);
                        owned.remove(entry.getKey());
                    } else conflicts.put(entry.getKey(), "Removed upstream, but locally modified; preserved");
                }
            }
            for (var entry : desired.entrySet()) {
                String name = entry.getKey(), nextHash = entry.getValue();
                Path current = resourcePath(stage, name);
                String currentHash = Files.isRegularFile(current) ? hash(current) : null;
                String previousHash = owned.get(name);
                if (migrate) {
                    // 只接管与包内哈希一致的旧文件，不替换来源未知的旧内容。
                    if (nextHash.equals(currentHash)) owned.put(name, nextHash);
                    else conflicts.put(name, "Legacy content differs or is missing; not adopted");
                } else if (previousHash != null) {
                    if (previousHash.equals(currentHash) || nextHash.equals(currentHash)) {
                        if (!nextHash.equals(currentHash)) Files.copy(incoming.resolve(name), current, StandardCopyOption.REPLACE_EXISTING);
                        owned.put(name, nextHash);
                    } else conflicts.put(name, "Locally modified, deleted or replaced; preserved");
                } else if (exists && old != null && old.conflicts.containsKey(name)) {
                    if (nextHash.equals(currentHash)) owned.put(name, nextHash);
                    else conflicts.put(name, old.conflicts.get(name));
                } else if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) || blockedParent(current, stage)) {
                    conflicts.put(name, "Unowned file or directory collision; preserved");
                } else {
                    Files.createDirectories(current.getParent());
                    Files.copy(incoming.resolve(name), current);
                    owned.put(name, nextHash);
                }
            }
            State next = new State(revision, owned, desired, conflicts);
            if (old != null && old.files.equals(owned) && old.bundle.equals(desired) && old.conflicts.equals(conflicts)) {
                report(target, conflicts);
                return;
            }
            Files.writeString(stage.resolve(MANIFEST), next.json(id).toString(2), StandardCharsets.UTF_8);
            if (exists && !snapshot(target).equals(before)) throw new IOException("Bundle changed while preparing update; retry later: " + target);
            publish(control, target, stage, id, revision, exists, before);
            report(target, conflicts);
        } finally {
            deleteTree(incoming);
            // 有未完成事务时保留暂存目录，交由下次恢复流程处理。
            if (!Files.exists(control.resolve("pending.json"))) deleteTree(stage);
        }
    }

    /** 先校验全部资源路径再写入暂存区，避免非法路径导致部分安装。 */
    private static boolean unpack(Path archive, Path incoming, String id) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries()).stream()
                    .filter(e -> e.getName().startsWith(PREFIX)).toList();
            if (entries.stream().noneMatch(e -> !e.isDirectory())) return false;
            for (ZipEntry entry : entries) {
                String name = entry.getName().substring(PREFIX.length());
                if (name.isEmpty()) continue;
                resourcePath(incoming, name);
                if (name.split("/")[0].equalsIgnoreCase(MANIFEST)) throw new IOException("Reserved bundle manifest path: " + name);
            }
            for (ZipEntry entry : entries) {
                String name = entry.getName().substring(PREFIX.length());
                if (name.isEmpty()) continue;
                Path path = resourcePath(incoming, name);
                if (entry.isDirectory()) Files.createDirectories(path);
                else {
                    Files.createDirectories(path.getParent());
                    try (InputStream in = zip.getInputStream(entry)) { Files.copy(in, path); }
                }
            }
            if (!Files.exists(incoming.resolve("info.json"))) {
                JSONObject info = new JSONObject().put("id", id).put("name", new JSONObject().put("en", id))
                        .put("description", new JSONObject().put("en", "Acbric bundled vanilla data."))
                        .put("tags", new org.json.JSONArray().put("acbric"));
                Files.writeString(incoming.resolve("info.json"), info.toString(2), StandardCharsets.UTF_8);
            }
            return true;
        }
    }

    /** 记录恢复日志，备份原目录后发布暂存结果；失败则尝试恢复。 */
    private static void publish(Path control, Path target, Path stage, String id, String revision,
                                boolean exists, Map<String, String> before) throws IOException {
        Path backups = control.resolve("backups");
        safeDirectories(backups);
        Path backup = backups.resolve(revision);
        JSONObject journal = new JSONObject().put("schema", 1).put("id", id).put("revision", revision).put("hadOriginal", exists);
        writeJournal(control.resolve("pending.json"), journal);
        try {
            rejectLinks(target);
            if (exists) {
                move(target, backup);
                // 检测预检后、目录移动前的改动，避免提交期间丢失用户编辑。
                if (!snapshot(backup).equals(before)) throw new IOException("Bundle changed during commit; restoring original");
            }
            move(stage, target);
            Files.delete(control.resolve("pending.json"));
            if (exists) System.out.println("[Acbric] Bundle backup retained: " + backup);
        } catch (IOException e) {
            try { recover(control, target, id); } catch (IOException recovery) { e.addSuppressed(recovery); }
            throw e;
        }
    }

    /** 仅完成可识别事务或恢复缺失原目录，歧义状态保留并报告人工检查。 */
    private static void recover(Path control, Path target, String id) throws IOException {
        Path pending = control.resolve("pending.json");
        if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) return;
        rejectLinks(pending);
        JSONObject journal = readJson(pending);
        String revision = journal.optString("revision");
        if (journal.optInt("schema") != 1 || !id.equals(journal.optString("id"))
                || !revision.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IOException("Invalid bundle recovery journal: " + pending);
        }
        Path backup = control.resolve("backups").resolve(revision);
        Path stage = control.resolve("stage-" + revision);
        rejectLinks(target); rejectLinks(backup); rejectLinks(stage);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isDirectory(backup)) move(backup, target);
            else if (journal.optBoolean("hadOriginal")) throw new IOException("Missing original bundle backup; manual recovery required: " + pending);
        } else if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            State current = readState(target, id);
            if (current == null || !revision.equals(current.revision)) {
                throw new IOException("Destination changed during interrupted update; preserved with backup: " + pending);
            }
        }
        deleteTree(stage);
        Files.delete(pending);
        System.out.println("[Acbric] Recovered bundle transaction: " + id);
    }

    private static void writeJournal(Path path, JSONObject value) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), "journal-", ".tmp");
        try {
            byte[] bytes = value.toString(2).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
    }

    private record State(String revision, Map<String, String> files, Map<String, String> bundle,
                         Map<String, String> conflicts) {
        JSONObject json(String id) {
            return new JSONObject().put("schema", 1).put("id", id).put("revision", revision)
                    .put("files", new JSONObject(files)).put("bundle", new JSONObject(bundle))
                    .put("conflicts", new JSONObject(conflicts));
        }
    }

    static boolean hasOwnership(Path target, String id) throws IOException {
        rejectLinks(target);
        return readState(target, id) != null;
    }

    private static State readState(Path target, String id) throws IOException {
        Path path = target.resolve(MANIFEST);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        rejectLinks(path);
        JSONObject json = readJson(path);
        if (json.optInt("schema") != 1 || !id.equals(json.optString("id")) || json.optString("revision").isBlank()) {
            throw new IOException("Invalid bundle ownership manifest; preserved: " + path);
        }
        try {
            return new State(json.getString("revision"), readMap(json.getJSONObject("files"), target, true),
                    readMap(json.getJSONObject("bundle"), target, true), readMap(json.getJSONObject("conflicts"), target, false));
        } catch (RuntimeException e) { throw new IOException("Invalid bundle ownership manifest: " + path, e); }
    }

    private static Map<String, String> readMap(JSONObject json, Path root, boolean hashes) throws IOException {
        Map<String, String> values = new TreeMap<>();
        for (Iterator<?> keys = json.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            resourcePath(root, key);
            if (key.split("/")[0].equalsIgnoreCase(MANIFEST)) throw new IOException("Reserved ownership path");
            String value = json.getString(key);
            if (hashes && !value.matches("[0-9a-f]{64}")) throw new IOException("Invalid bundle hash: " + key);
            values.put(key, value);
        }
        return values;
    }

    private static JSONObject readJson(Path path) throws IOException {
        try { return new JSONObject(Files.readString(path, StandardCharsets.UTF_8)); }
        catch (RuntimeException e) { throw new IOException("Invalid JSON: " + path, e); }
    }

    /** 禁止越界、绝对路径和 Windows 特殊路径形式，统一约束文件归属键。 */
    static Path resourcePath(Path root, String relative) throws IOException {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\") || relative.contains(":")) throw new IOException("Invalid resource path: " + relative);
        for (String part : relative.split("/")) {
            if (part.equals("..") || part.endsWith(".") || part.endsWith(" ")) throw new IOException("Invalid resource path: " + relative);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Resource escapes its directory: " + relative);
        return target;
    }

    private static boolean blockedParent(Path path, Path root) {
        for (Path parent = path.getParent(); !parent.equals(root); parent = parent.getParent()) {
            if (Files.exists(parent) && !Files.isDirectory(parent)) return true;
        }
        return false;
    }

    private static Map<String, String> files(Path root) throws IOException {
        Map<String, String> result = snapshot(root);
        result.entrySet().removeIf(e -> e.getValue().equals("directory"));
        return result;
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> result = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                rejectLinks(dir);
                if (!dir.equals(root)) result.put(root.relativize(dir).toString().replace('\\', '/'), "directory");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Unsupported linked/special bundle file: " + file);
                result.put(root.relativize(file).toString().replace('\\', '/'), hash(file));
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        for (var entry : snapshot(from).entrySet()) {
            Path destination = to.resolve(entry.getKey());
            if (entry.getValue().equals("directory")) Files.createDirectories(destination);
            else {
                Files.createDirectories(destination.getParent());
                Files.copy(from.resolve(entry.getKey()), destination);
            }
        }
    }

    private static String hash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    static void safeDirectories(Path path) throws IOException {
        rejectLinks(path); Files.createDirectories(path); rejectLinks(path);
    }

    /** 检查目标及所有祖先，拒绝符号链接和其他特殊文件系统节点。 */
    static void rejectLinks(Path path) throws IOException {
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attrs = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("Linked resource path is not supported: " + part);
            }
        }
    }

    /** 仅对 AccessDenied 做有限重试；中断时恢复中断标记并向上传播。 */
    private static void move(Path from, Path to) throws IOException {
        for (int attempt = 0;; attempt++) {
            try { Files.move(from, to); return; }
            catch (AccessDeniedException e) {
                if (attempt == 3) throw e;
                try { Thread.sleep(50L << attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted bundle commit", interrupted); }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        rejectLinks(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void report(Path target, Map<String, String> conflicts) {
        if (conflicts.isEmpty()) System.out.println("[Acbric] Bundled resources ready: " + target);
        else for (var conflict : conflicts.entrySet()) {
            System.err.println("[Acbric] Bundle conflict " + target.resolve(conflict.getKey()) + ": " + conflict.getValue());
            DiagnosticHub.publish(target.getFileName().toString(),net.fabricacs.api.diagnostics.DiagnosticMessage.Level.WARN,"Resource conflict / 资源冲突: "+target.resolve(conflict.getKey())+": "+conflict.getValue(),null);
        }
    }
}
