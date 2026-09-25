/*
 * CodeManifestRegression.java — 代码清单的内容变化、加载失败、严格输入及导出隔离回归。
 * 使用 build 沙箱内的文件和 Loader 容器夹具，不启动游戏或服务器。
 */
package net.fabricacs.api.impl;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import org.json.JSONObject;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class CodeManifestRegression {
    private static int checks;
    private static final String HASH = "a".repeat(64);
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    private static void invalid(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label); }
        catch (IllegalArgumentException | org.json.JSONException expected) { check(true, label); }
    }
    private static CodeManifest.Entry entry(String id, String digest, String status) {
        return new CodeManifest.Entry(id, "1.0.0", digest, "OK", status);
    }
    private static CodeManifest manifest(List<CodeManifest.Entry> extra) {
        var entries = new ArrayList<>(List.of(entry("acbric_api", HASH, "NO_ACBRIC_ENTRYPOINT"), entry("fabricloader", HASH, "NO_ACBRIC_ENTRYPOINT")));
        entries.addAll(extra);
        return new CodeManifest("1.2.15.2", HASH, "21.0.12", entries);
    }
    private static ModContentFingerprint.Result hash(List<Path> roots) {
        return ModContentFingerprint.inspect(roots, new ModContentFingerprint.Budget(1024 * 1024));
    }
    private static Path jar(Path target, Map<String, byte[]> entries, long time) throws Exception {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (var entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey()); ze.setTime(time); zip.putNextEntry(ze);
                zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return target;
    }
    private static ModContainer container(String id, List<Path> roots) {
        var metadata = new BuiltinModMetadata.Builder(id, "1.0.0").build();
        return (ModContainer) Proxy.newProxyInstance(ModContainer.class.getClassLoader(), new Class<?>[]{ModContainer.class}, (p,m,a) -> switch (m.getName()) {
            case "getMetadata" -> metadata;
            case "getRootPaths" -> roots;
            default -> throw new AssertionError("Unexpected container call: " + m.getName());
        });
    }
    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root);
        Path first = Files.createDirectories(root.resolve("one"));
        Files.writeString(first.resolve("A.class"), "class data");
        Files.writeString(first.resolve("fabric.mod.json"), "metadata");
        Path second = Files.createDirectories(root.resolve("two"));
        Files.copy(first.resolve("fabric.mod.json"), second.resolve("fabric.mod.json"));
        Files.copy(first.resolve("A.class"), second.resolve("A.class"));
        String baseline = hash(List.of(first)).digest();
        check(baseline.length() == 64 && baseline.equals(hash(List.of(second)).digest()), "fingerprint ignores absolute path and file creation order");
        Files.setLastModifiedTime(second.resolve("A.class"), java.nio.file.attribute.FileTime.fromMillis(1000));
        check(baseline.equals(hash(List.of(second)).digest()), "fingerprint ignores modification time");
        Files.move(second.resolve("A.class"), second.resolve("B.class"));
        check(!baseline.equals(hash(List.of(second)).digest()), "file rename changes fingerprint");
        Files.move(second.resolve("B.class"), second.resolve("A.class"));
        Files.writeString(second.resolve("A.class"), "other data");
        check(!baseline.equals(hash(List.of(second)).digest()), "same MOD version with changed bytes changes fingerprint");
        check(!hash(List.of(first, second)).digest().equals(hash(List.of(second, first)).digest()), "root precedence participates in fingerprint");
        check(!hash(List.of(first)).digest().equals(hash(List.of(first, first)).digest()), "root count participates in fingerprint");
        check(hash(List.of(root.resolve("missing"))).status().equals("UNSUPPORTED_ORIGIN"), "missing root is unverifiable");
        check(hash(List.of()).digest().isEmpty(), "empty origins never hash as success");
        check(ModContentFingerprint.inspect(List.of(first), new ModContentFingerprint.Budget(1)).status().equals("LIMIT_EXCEEDED"), "shared byte budget prevents oversized reads");
        try {
            Path link = root.resolve("link"); Files.createSymbolicLink(link, first.toAbsolutePath());
            check(hash(List.of(link)).status().equals("UNSUPPORTED_ORIGIN"), "symbolic root is rejected");
        } catch (java.io.IOException | UnsupportedOperationException denied) { System.out.println("SKIP manifest symlink test: unavailable on this host"); }
        var contents = new LinkedHashMap<String, byte[]>();
        contents.put("A.class", "class data".getBytes(StandardCharsets.UTF_8)); contents.put("fabric.mod.json", "metadata".getBytes(StandardCharsets.UTF_8));
        Path archive = jar(root.resolve("original.jar"), contents, 1000000000);
        var reversed = new LinkedHashMap<String, byte[]>(); reversed.put("fabric.mod.json", contents.get("fabric.mod.json")); reversed.put("A.class", contents.get("A.class"));
        Path repacked = jar(root.resolve("repacked.jar"), reversed, 2000000000);
        try (var a = FileSystems.newFileSystem(archive); var b = FileSystems.newFileSystem(repacked)) {
            check(hash(List.of(a.getPath("/"))).digest().equals(baseline), "archive and directory have same resolved content fingerprint");
            check(hash(List.of(a.getPath("/"))).digest().equals(hash(List.of(b.getPath("/"))).digest()), "ZIP entry order and timestamps do not change fingerprint");
        }
        // 用合法等长条目名改写 ZIP 的两个索引，构造 Java ZipOutputStream 不允许创建的重复条目。
        Path duplicate = jar(root.resolve("duplicate.jar"), Map.of("aa", new byte[]{1}, "bb", new byte[]{2}), 1000000000);
        byte[] duplicateBytes = Files.readAllBytes(duplicate);
        for (int i = 0; i < duplicateBytes.length - 1; i++) if (duplicateBytes[i] == 'b' && duplicateBytes[i + 1] == 'b') { duplicateBytes[i] = 'a'; duplicateBytes[i + 1] = 'a'; }
        Files.write(duplicate, duplicateBytes);
        try (var fs = FileSystems.newFileSystem(duplicate)) {
            check(hash(List.of(fs.getPath("/"))).status().equals("UNSUPPORTED_ORIGIN"), "duplicate ZIP entries are not silently normalized away");
        }
        var good = manifest(List.of(entry("legacy_mod", HASH, "SUCCEEDED")));
        var reordered = new CodeManifest(good.gameVersion(), good.gameFingerprint(), good.javaVersion(), good.mods().reversed());
        check(CodeManifest.compare(good, reordered).status().equals("CODE_MATCH"), "MOD enumeration order does not affect comparison");
        check(CodeManifest.parse(good.json().toString()).equals(good), "manifest JSON round trip preserves immutable values");
        try { good.mods().clear(); throw new AssertionError("mutable mods"); }
        catch (UnsupportedOperationException expected) { check(true, "manifest entries cannot be mutated after capture"); }
        var changed = manifest(List.of(entry("legacy_mod", "b".repeat(64), "SUCCEEDED")));
        var compared = CodeManifest.compare(good, changed);
        check(compared.status().equals("DIFFERENT") && compared.differences().stream().anyMatch(d -> d.code().equals("MOD_CONTENT") && d.id().equals("legacy_mod")), "comparison locates same-version content mismatch");
        check(CodeManifest.compare(good, manifest(List.of())).differences().stream().anyMatch(d -> d.code().equals("ONLY_LEFT")), "missing MOD has explicit direction");
        check(CodeManifest.compare(manifest(List.of()), good).differences().stream().anyMatch(d -> d.code().equals("ONLY_RIGHT")), "extra MOD has explicit direction");
        var failed = manifest(List.of(entry("legacy_mod", HASH, "FAILED")));
        check(CodeManifest.compare(failed, failed).status().equals("UNVERIFIABLE"), "identical initialization failures never match");
        for (String state : List.of("PENDING", "RUNNING", "NOT_REACHED")) check(!manifest(List.of(entry("legacy_mod", HASH, state))).verified(), "incomplete initialization rejected: " + state);
        var unreadable = manifest(List.of(new CodeManifest.Entry("legacy_mod", "1", "", "UNREADABLE", "SUCCEEDED")));
        check(CodeManifest.compare(unreadable, unreadable).status().equals("UNVERIFIABLE"), "two unreadable sources never match");
        check(!new CodeManifest("1", "unavailable", "21", good.mods()).verified(), "missing game identity is unverifiable");
        check(!new CodeManifest("1", HASH, "21", List.of()).verified(), "missing framework containers are unverifiable");
        check(CodeManifest.compare(good, new CodeManifest("1.2.14", "b".repeat(64), good.javaVersion(), good.mods())).differences().size() == 2, "game version and game content differences are separate");
        check(CodeManifest.compare(good, new CodeManifest(good.gameVersion(), HASH, "22", good.mods())).differences().getFirst().code().equals("JAVA_VERSION"), "Java runtime version is compared");
        invalid(() -> CodeManifest.parse(good.json().put("schema", 2).toString()), "unknown schema rejected");
        invalid(() -> CodeManifest.parse(good.json().put("contentAlgorithm", "unknown").toString()), "unknown fingerprint algorithm rejected");
        invalid(() -> CodeManifest.parse(good.json().put("extra", true).toString()), "unknown manifest fields rejected");
        invalid(() -> CodeManifest.parse(good.json().put("gameVersion", 123).toString()), "JSON type coercion rejected");
        invalid(() -> CodeManifest.parse(good.json().toString() + " trailing"), "trailing JSON garbage rejected");
        invalid(() -> CodeManifest.parse("{\"schema\":1," + good.json().toString().substring(1)), "duplicate JSON keys rejected");
        JSONObject dup = good.json(); dup.getJSONArray("mods").put(dup.getJSONArray("mods").getJSONObject(0));
        invalid(() -> CodeManifest.parse(dup.toString()), "duplicate MOD IDs rejected");
        invalid(() -> CodeManifest.parse(" ".repeat(CodeManifest.MAX_JSON_BYTES + 1)), "oversized text rejected before JSON parse");
        invalid(() -> new CodeManifest.Entry("bad/../id", "1", HASH, "OK", "SUCCEEDED"), "invalid MOD identifier rejected");
        invalid(() -> new CodeManifest.Entry("good_id", "1", "", "OK", "SUCCEEDED"), "empty successful fingerprint rejected");
        Path badUtf8 = root.resolve("bad-utf8.json"); Files.write(badUtf8, new byte[]{(byte) 0xc3, 0x28});
        try { CodeManifestCompare.read(badUtf8); throw new AssertionError("UTF8 accepted"); }
        catch (java.io.IOException expected) { check(true, "malformed UTF8 rejected"); }
        Path huge = root.resolve("huge.json"); Files.write(huge, new byte[CodeManifest.MAX_JSON_BYTES + 1]);
        try { CodeManifestCompare.read(huge); throw new AssertionError("huge input accepted"); }
        catch (java.io.IOException expected) { check(true, "file reader caps input size"); }
        String prevVersion = System.getProperty("acbric.internal.game.version"), prevHash = System.getProperty("acbric.internal.game.fingerprint");
        try {
            System.setProperty("acbric.internal.game.version", "1.2.15.2"); System.setProperty("acbric.internal.game.fingerprint", HASH);
            var loaded = List.of(container("acbric_api", List.of(first)), container("fabricloader", List.of(first)), container("legacy_mod", List.of(second)));
            var collected = CodeManifestCollector.collect(loaded, Map.of("acbric_api", "NO_ACBRIC_ENTRYPOINT", "fabricloader", "NO_ACBRIC_ENTRYPOINT", "legacy_mod", "SUCCEEDED"));
            check(collected.verified() && collected.mods().size() == 3, "collector includes actual loaded containers and their initialization states");
            Path export = root.resolve("export"); CodeManifestCollector.publish(export, collected);
            check(CodeManifestCompare.read(export.resolve("code-manifest.json")).equals(collected), "atomic report is readable by comparison tool");
            check(!Files.readString(export.resolve("code-manifest.json")).contains(root.toString()), "export contains no absolute content paths");
            try (var files = Files.list(export)) { check(files.count() == 1, "atomic export leaves no temporary file"); }
            check(!CodeManifestCollector.collect(loaded, Map.of()).verified(), "collector never defaults missing entrypoint status to success");
            Files.writeString(root.resolve("blocked"), "existing file");
            try { CodeManifestCollector.publish(root.resolve("blocked"), collected); throw new AssertionError("blocked export succeeded"); }
            catch (java.io.IOException expected) { check(Files.readString(root.resolve("blocked")).equals("existing file"), "export failure preserves existing file"); }
        } finally {
            restore("acbric.internal.game.version", prevVersion); restore("acbric.internal.game.fingerprint", prevHash);
        }
        // 保留小清单供真正的 CLI 进程/退出码检查。
        CodeManifestCollector.publish(root.resolve("cli-left"), good);
        CodeManifestCollector.publish(root.resolve("cli-different"), changed);
        CodeManifestCollector.publish(root.resolve("cli-unverifiable"), unreadable);
        return checks;
    }
    private static void restore(String name, String value) { if (value == null) System.clearProperty(name); else System.setProperty(name, value); }
}
