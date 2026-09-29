/*
 * JavaModManager.java — 合并已加载与磁盘 MOD 清单，校验下次启动选择；不热卸载、不移动归档。
 * 依赖预检查仅覆盖可确认的候选，最终解析仍由 Fabric 执行；嵌套和外部来源只读。
 */
package net.fabricacs.api.impl;

import net.fabricacs.management.ModSelection;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.metadata.*;
import net.fabricmc.loader.impl.metadata.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

public final class JavaModManager {
    public record Entry(ModMetadata metadata, Path archive, boolean loaded, String readOnlyReason) {
        public String id() { return metadata.getId(); }
        public boolean manageable() { return archive != null && readOnlyReason.isEmpty(); }
    }
    private static JavaModManager current;
    private final Path config;
    private final Path modsDir;
    private final Set<Path> jarPaths = new TreeSet<>();
    private final List<ModContainer> loaded;
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<Path, String> fingerprints = new TreeMap<>();
    private final Set<String> external;
    private final Map<String, List<LoaderModMetadata>> nested = new TreeMap<>();
    private ModSelection.Snapshot selection;
    private String error = "";

    private JavaModManager(Path modsDir, Path configDir, Collection<ModContainer> loaded) throws IOException {
        this.modsDir = modsDir;
        this.config = ModSelection.file(configDir); this.loaded = List.copyOf(loaded);
        selection = ModSelection.read(config);
        external = ModSelection.ids(System.getProperty(ModSelection.EXTERNAL_PROPERTY));
        if (System.getProperty(ModSelection.EFFECTIVE_PROPERTY) == null)
            error = "Update the launcher and API together / 请同时更新启动器和 API";
        Map<String, ModContainer> byId = new HashMap<>();
        for (ModContainer container : loaded) {
            ModMetadata metadata = container.getMetadata(); byId.put(metadata.getId(), container);
            entries.put(metadata.getId(), new Entry(metadata, null, true, "Nested / external / built-in MOD"));
        }
        ModSelection.rejectLinks(modsDir);
        if (Files.isDirectory(modsDir)) try (var paths = Files.list(modsDir)) {
            for (Path file : paths.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.startsWith(".") || !name.endsWith(".jar")) continue;
                if (Files.isHidden(file)) continue;
                jarPaths.add(file);
                ModSelection.rejectLinks(file);
                if (!Files.isRegularFile(file)) continue;
                String before = digest(file);
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    var json = zip.getEntry("fabric.mod.json"); if (json == null) continue;
                    if (json.getSize() < 0 || json.getSize() > 1024 * 1024) throw new IOException("Oversized MOD metadata: " + name);
                    if (zip.stream().filter(e -> e.getName().equals("fabric.mod.json")).count() != 1)
                        throw new IOException("Duplicate metadata: " + name);
                    LoaderModMetadata metadata;
                    try (var input = zip.getInputStream(json)) {
                        metadata = ModMetadataParser.parseMetadata(input, file.toString(), List.of(),
                                new VersionOverrides(), new DependencyOverrides(configDir), false);
                    }
                    String id = metadata.getId(); Entry old = entries.get(id);
                    if (old != null && old.archive() != null) throw new IOException("Duplicate MOD ID: " + id);
                    String reason = ModSelection.PROTECTED.contains(id) ? "Core MOD / 核心组件" : "";
                    if (!metadata.getEnvironment().matches(EnvType.CLIENT)) reason = "Not a client MOD / 非客户端 MOD";
                    ModContainer container = byId.get(id);
                    if (container != null && (container.getOrigin().getKind() != ModOrigin.Kind.PATH ||
                            container.getOrigin().getPaths().size() != 1 ||
                            !container.getOrigin().getPaths().getFirst().toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize())))
                        reason = "Loaded from another source / 当前加载来源不同";
                    if (external.contains(id)) reason = "Disabled by launch arguments / 启动参数停用";
                    entries.put(id, new Entry(metadata, file, container != null, reason));
                    nested.put(id, NestedModMetadata.read(zip, metadata, configDir));
                    if (!before.equals(digest(file))) throw new IOException("MOD archive changed during inspection: " + name);
                    fingerprints.put(file, before);
                } catch (ParseMetadataException | RuntimeException ex) { throw new IOException("Cannot inspect MOD: " + file.getFileName(), ex); }
            }
        }
    }

    public static void refresh() {
        try {
            var loader = FabricLoader.getInstance();
            Path mods = Path.of(System.getProperty("fabric.modsFolder", loader.getGameDir().resolve("mods").toString()));
            current = new JavaModManager(mods, loader.getConfigDir(), loader.getAllMods());
        } catch (IOException | RuntimeException ex) {
            current = null;
            System.err.println("[Acbric] MOD manager unavailable: " + ex);
        }
    }
    public static JavaModManager current() { return current; }
    public Collection<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry entry(String id) { return entries.get(id); }
    public boolean enabledNext(String id) { return !selection.disabled().contains(id) && !external.contains(id); }
    public String reason(String id) { return !error.isEmpty() ? error : entries.get(id).readOnlyReason(); }
    /** 展示层才读取游戏语言，磁盘校验不提前初始化游戏字体及资源。 */
    public String displayReason(String id) {
        String reason = reason(id);
        if (!net.fabricacs.api.util.AcbricLanguage.isChinese()) {
            int split = reason.indexOf(" / ");
            return split >= 0 && reason.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF) ? reason.substring(0, split) : reason;
        }
        return switch (reason) {
            case "Nested / external / built-in MOD" -> "嵌套、外部来源或内置 MOD，无法在此启停";
            case "Core MOD / 核心组件" -> "框架核心组件，无法停用";
            case "Not a client MOD / 非客户端 MOD" -> "非客户端 MOD";
            case "Loaded from another source / 当前加载来源不同" -> "当前加载来源不同，无法在此启停";
            case "Disabled by launch arguments / 启动参数停用" -> "已被启动参数停用";
            case "Update the launcher and API together / 请同时更新启动器和 API" -> "请同时更新启动器和 API";
            default -> reason;
        };
    }
    public String status(String id, boolean zh) {
        Entry entry = entries.get(id);
        String status = entry.loaded() ? (zh ? "已加载" : "Loaded") : (zh ? "未加载" : "Not loaded");
        if (entry.loaded() && StartupCodeManifest.current.mods().stream().anyMatch(e -> e.id().equals(id) && e.initialization().equals("FAILED")))
            status = zh ? "入口初始化失败" : "Entrypoint failed";
        if (entry.loaded() != enabledNext(id)) status += enabledNext(id)
                ? (zh ? " · 待重启启用" : " · Enable on restart") : (zh ? " · 待重启停用" : " · Disable on restart");
        else if (!entry.loaded()) status = zh ? "已停用" : "Disabled";
        return status;
    }

    public void toggle(String id) throws IOException {
        Entry entry = entries.get(id);
        if (entry == null || !entry.manageable() || !reason(id).isEmpty()) throw new IOException("Cannot manage MOD: " + id);
        Set<String> next = new TreeSet<>(selection.disabled());
        if (!next.remove(id)) next.add(id);
        Set<Path> now = new TreeSet<>();
        if (Files.isDirectory(modsDir)) try (var paths = Files.list(modsDir)) {
            for (Path path : paths.toList()) if (!path.getFileName().toString().startsWith(".")
                    && path.getFileName().toString().endsWith(".jar") && !Files.isHidden(path)) now.add(path);
        }
        if (!now.equals(jarPaths)) throw new IOException("MOD directory changed; reopen the MOD screen / MOD 目录已改变，请重新打开列表");
        if (next.contains(id)) verifyBundledResources(entry);
        // 磁盘上任何已检查的 JAR 改变时，必须重新打开列表，不能按过期依赖信息写配置。
        for (var value : fingerprints.entrySet()) if (!value.getValue().equals(digest(value.getKey())))
            throw new IOException("MOD files changed; reopen the MOD screen / MOD 文件已改变，请重新打开列表");
        Set<String> effective = new TreeSet<>(next); effective.addAll(external);
        List<ModMetadata> candidates = new ArrayList<>();
        for (Entry candidate : entries.values()) {
            if (effective.contains(candidate.id()) || !candidate.metadata().getEnvironment().matches(EnvType.CLIENT)) continue;
            if (candidate.archive() == null && hasDisabledParent(candidate.id(), effective, new HashSet<>())) continue;
            candidates.add(candidate.metadata());
        }
        // 启用先前未加载的父 MOD 时，也能看到它声明的嵌套依赖。子 MOD 仍不单独提供启停按钮。
        for (var group : nested.entrySet()) if (!effective.contains(group.getKey())
                && entries.get(group.getKey()).metadata().getEnvironment().matches(EnvType.CLIENT)) {
            for (var metadata : group.getValue()) if (!effective.contains(metadata.getId()) && metadata.getEnvironment().matches(EnvType.CLIENT)) {
                if (candidates.stream().noneMatch(c -> c.getId().equals(metadata.getId()) && c.getVersion().equals(metadata.getVersion())))
                    candidates.add(metadata);
            }
        }
        validateDependencies(candidates);
        selection = ModSelection.write(config, selection, next);
    }

    private static void verifyBundledResources(Entry entry) throws IOException {
        try (ZipFile zip = new ZipFile(entry.archive().toFile())) {
            if (zip.stream().noneMatch(e -> e.getName().startsWith("acbric_vanilla/") && !e.isDirectory())) return;
            Path target = LocalModPaths.nativeMods().resolve(entry.id());
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
            if (!BundledResourceStore.hasOwnership(target, entry.id()))
                throw new IOException("Unmanaged bundled resources; explicit resource migration required / 配套资源尚无归属记录，请先显式迁移：" + entry.id());
        }
    }

    private boolean hasDisabledParent(String id, Set<String> disabled, Set<String> seen) {
        if (!seen.add(id)) return true;
        for (ModContainer container : loaded) if (container.getMetadata().getId().equals(id)) {
            var origin = container.getOrigin();
            if (origin.getKind() == ModOrigin.Kind.NESTED) {
                String parent = origin.getParentModId();
                return disabled.contains(parent) || hasDisabledParent(parent, disabled, seen);
            }
        }
        return false;
    }

    static void validateDependencies(Collection<ModMetadata> candidates) throws IOException {
        for (ModMetadata mod : candidates) for (ModDependency dependency : mod.getDependencies()) {
            boolean found = candidates.stream().anyMatch(other ->
                    (other.getId().equals(dependency.getModId()) || other.getProvides().contains(dependency.getModId())) && dependency.matches(other.getVersion()));
            if (dependency.getKind() == ModDependency.Kind.DEPENDS && !found)
                throw new IOException(mod.getId() + " requires / 需要 " + dependency.getModId() + " " + dependency.getVersionRequirements());
            if (dependency.getKind() == ModDependency.Kind.BREAKS && found)
                throw new IOException(mod.getId() + " conflicts with / 冲突 " + dependency.getModId());
        }
    }

    private static String digest(Path file) throws IOException {
        ModSelection.rejectLinks(file);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = input.read(buffer)) >= 0) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
}
