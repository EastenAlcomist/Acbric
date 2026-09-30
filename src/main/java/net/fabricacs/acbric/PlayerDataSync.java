/* PlayerDataSync.java — 环境隔离用的镜像引擎：把原版存档、设计、战役/任务/录像与原生 MOD 复制到实例副本。
 *
 * 默认环境是共享（直接使用原版目录，见 PlayerEnvironment），只有玩家在设置里主动隔离时才走这里。
 * 语义是完全镜像：隔离副本与原版一致，副本里多出的内容会被移除；因此所有被覆盖或被移除的内容
 * 先移入实例内的备份目录；共用 MOD 目录里只管理原生 MOD 文件夹，玩家自己放的 Java `.jar` 不参与。
 */
package net.fabricacs.acbric;

import net.fabricacs.management.ModSelection;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class PlayerDataSync {
    /** 备份目录名；位于实例 userdata 下，本身不在镜像的固定子目录列表里。 */
    static final String BACKUP = ".acbric-sync-backup";
    /** 镜像到实例 userdata 的子目录：存档、设计（舰船/建筑/陆行舰）、战役、任务与录像。 */
    static final List<String> USERDATA_TREES = List.of(
            "saves", "ships", "buildings", "landships", "combats", "missions", "recordings", "recordingsArchive");
    /** 原生 MOD 目录名；在外部布局里与 Setup 同级，由多个实例共用。 */
    static final String MODS_TREE = "mods";
    private static final String USERDATA = "userdata";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 单次同步的预览：源目录、将要复制的文件数与将要移除的条目数。 */
    record Preview(Path source, long copy, long remove) { boolean empty() { return copy == 0 && remove == 0; } }
    /** 同步结果：实际复制数、实际移除数，以及确有覆盖或移除时的备份目录（否则为 null）。 */
    record Result(long copy, long remove, Path backup) {}
    private record Copy(Path from, Path to) {}
    /** 先扫描后执行：执行阶段在锁内重新扫描，避免两次点击之间游戏改动留下半份状态。 */
    private record Scan(List<Copy> copies, List<Path> removes) {}

    private PlayerDataSync() {}

    /** 共用 MOD 目录；沿用外部安装的重叠与链接检查，不另建一套路径规则。 */
    static Path modsRoot(Path framework, Path install, Path instance) throws IOException {
        Path root = framework.toAbsolutePath().normalize().resolve(MODS_TREE);
        ExternalMods.validate(root, install, instance);
        if (framework.startsWith(root))
            throw ExternalGameInstallation.failure("MODS_OVERLAP", "MOD folder cannot contain the framework", "MOD 目录不能包含框架: " + root);
        return root;
    }

    /** 只读预览：给出源目录与复制/移除条目数，不写入任何文件，也不要求游戏已关闭。 */
    static Preview preview(Path source, Path install, Path instance, Path mods) throws IOException {
        Scan scan = scan(source, install, instance, mods);
        return new Preview(source, scan.copies().size(), scan.removes().size());
    }

    /** 持实例锁与共用 MOD 锁执行镜像；任一锁被占用说明游戏仍在运行，此时不做任何写入。 */
    static Result sync(Path source, Path install, Path instance, Path mods) throws IOException {
        try (var lease = ExternalPreflight.InstanceLease.open(install, instance);
             var shared = ExternalMods.open(mods, install, instance)) {
            Scan scan = scan(source, install, instance, mods);
            Path root = instance.resolve(USERDATA).resolve(BACKUP);
            Path backup = root.resolve(fresh(root));
            long removed = 0, replaced = 0;
            for (Path path : scan.removes()) { relocate(path, backup.resolve(location(path, instance, mods))); removed++; }
            for (Copy copy : scan.copies()) {
                if (Files.exists(copy.to(), LinkOption.NOFOLLOW_LINKS)) { relocate(copy.to(), backup.resolve(location(copy.to(), instance, mods))); replaced++; }
                Files.createDirectories(copy.to().getParent());
                Files.copy(copy.from(), copy.to(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                // 原版可能留下只读文件；同步后的副本保持可管理，否则下次覆盖会被拒绝。
                copy.to().toFile().setWritable(true);
            }
            return new Result(scan.copies().size(), removed, removed + replaced == 0 ? null : backup);
        }
    }

    /** 扫描：源侧内容不同的文件需要复制，目标侧多出的条目需要移除。 */
    private static Scan scan(Path source, Path install, Path instance, Path mods) throws IOException {
        if (!Files.isDirectory(source))
            throw ExternalGameInstallation.failure("VANILLA_DATA_MISSING", "No vanilla data folder: " + source, "没有找到原版数据文件夹: " + source);
        Path userdata = instance.resolve(USERDATA);
        ExternalMods.validate(mods, install, instance);
        // 目标根沿用既有的链接拒绝规则；源目录属于玩家自己的原版数据，只检查其中的条目。
        for (Path root : new Path[]{userdata, mods}) {
            ModSelection.rejectLinks(root);
            if (Files.exists(root) && !Files.isDirectory(root))
                throw ExternalGameInstallation.failure("SYNC_TARGET_INVALID", "Not a folder: " + root, "同步目标不是文件夹: " + root);
        }
        List<Copy> copies = new ArrayList<>(); List<Path> removes = new ArrayList<>();
        for (String name : USERDATA_TREES) tree(source.resolve(name), userdata.resolve(name), copies, removes);
        nativeMods(source.resolve(MODS_TREE), mods, copies, removes);
        return new Scan(List.copyOf(copies), List.copyOf(removes));
    }

    /** 完整镜像一棵子树：源侧每个文件在目标侧就位，目标侧多出的条目整体移除。 */
    private static void tree(Path source, Path target, List<Copy> copies, List<Path> removes) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            // 源侧没有这类数据时镜像要求目标侧同样为空：整个目录连壳一起移除，不留空文件夹。
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { rejectLink(target); removes.add(target); }
            return;
        }
        rejectLink(source);
        if (!Files.isDirectory(source))
            throw ExternalGameInstallation.failure("SYNC_SOURCE_INVALID", "Not a folder: " + source, "原版数据里的同名条目不是文件夹: " + source);
        Set<Path> present = new HashSet<>();
        for (Path file : files(source)) {
            Path relative = source.relativize(file);
            present.add(relative);
            for (Path parent = relative.getParent(); parent != null; parent = parent.getParent()) present.add(parent);
            Path destination = target.resolve(relative);
            if (differs(file, destination)) copies.add(new Copy(file, destination));
        }
        extras(target, target, present, removes);
    }

    /** 原生 MOD 只按文件夹镜像；共用目录里的 `.jar`、说明与锁文件始终保留。 */
    private static void nativeMods(Path source, Path mods, List<Copy> copies, List<Path> removes) throws IOException {
        Set<Path> folders = new LinkedHashSet<>();
        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            rejectLink(source);
            if (!Files.isDirectory(source))
                throw ExternalGameInstallation.failure("SYNC_SOURCE_INVALID", "Not a folder: " + source, "原版数据里的同名条目不是文件夹: " + source);
            for (Path child : entries(source)) {
                if (!Files.isDirectory(child)) continue;   // 根目录下的文件不属于同步范围
                rejectLink(child);
                folders.add(child.getFileName());
                tree(child, mods.resolve(child.getFileName()), copies, removes);
            }
        }
        if (!Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) return;
        for (Path child : entries(mods)) {
            if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) continue;   // Java MOD 与框架文件不属于同步范围
            if (!folders.contains(child.getFileName())) removes.add(child);
        }
    }

    /** 收集目标侧多出的条目；相对路径始终按树根计算，父目录已整体移除时不再重复列出其中的文件。 */
    private static void extras(Path root, Path target, Set<Path> present, List<Path> removes) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        rejectLink(target);
        if (!Files.isDirectory(target)) { removes.add(target); return; }
        for (Path child : entries(target)) {
            Path relative = root.relativize(child);
            rejectLink(child);
            if (!present.contains(relative)) removes.add(child);
            else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) extras(root, child, present, removes);
        }
    }

    private static List<Path> files(Path root) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || attrs.isOther()) throw linkFailure(file);
                if (attrs.isRegularFile()) found.add(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && (attrs.isSymbolicLink() || attrs.isOther())) throw linkFailure(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }

    private static List<Path> entries(Path directory) throws IOException {
        try (var stream = Files.list(directory)) { return stream.sorted().toList(); }
    }

    /** 同名同大小且修改时间一致即视为已同步；不比较内容，避免每次点击重读整份存档。 */
    private static boolean differs(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return true;
        if (Files.size(source) != Files.size(target)) return true;
        return !Files.getLastModifiedTime(source).equals(Files.getLastModifiedTime(target));
    }

    /** 目标路径在备份目录中的落点：userdata 与共用 MOD 目录分开存放，便于玩家取回。 */
    private static Path location(Path path, Path instance, Path mods) {
        Path userdata = instance.resolve(USERDATA);
        return path.startsWith(userdata) ? Path.of(USERDATA).resolve(userdata.relativize(path))
                : path.startsWith(mods) ? Path.of(MODS_TREE).resolve(mods.relativize(path))
                : path.getFileName();
    }

    /** 覆盖与移除都先搬进备份，再处理目标；跨卷或占用时退回复制后删除原文件。 */
    private static void relocate(Path path, Path backup) throws IOException {
        ModSelection.rejectLinks(backup);
        Files.createDirectories(backup.getParent());
        try {
            Files.move(path, backup);
        } catch (IOException | RuntimeException move) {
            try { copyEntry(path, backup); deleteEntry(path); }
            catch (IOException | RuntimeException fallback) { fallback.addSuppressed(move); throw fallback; }
        }
    }

    private static void copyEntry(Path from, Path to) throws IOException {
        if (Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(to);
            for (Path child : entries(from)) copyEntry(child, to.resolve(child.getFileName()));
        } else Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteEntry(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { Files.deleteIfExists(path); return; }
        for (Path child : entries(path)) deleteEntry(child);
        Files.deleteIfExists(path);
    }

    /** 唯一备份目录：同一秒内重复同步不覆盖上一份。 */
    private static String fresh(Path root) {
        String stamp = LocalDateTime.now().format(STAMP);
        if (!Files.exists(root.resolve(stamp))) return stamp;
        for (int index = 2; index < 1000; index++) if (!Files.exists(root.resolve(stamp + "-" + index))) return stamp + "-" + index;
        return stamp + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static void rejectLink(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) throw linkFailure(path);
    }

    private static IOException linkFailure(Path path) {
        return ExternalGameInstallation.failure("SYNC_LINK_UNSUPPORTED",
                "Linked item is not synced: " + path, "链接或重解析条目不参与同步: " + path);
    }
}
