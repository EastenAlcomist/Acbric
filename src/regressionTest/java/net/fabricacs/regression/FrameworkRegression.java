/*
 * FrameworkRegression.java — 无界面回归总入口：在 build 沙箱构造资源、数据错误和类路径夹具。
 */
package net.fabricacs.regression;

import net.fabricacs.acbric.AirshipsGameProvider;
import net.fabricacs.api.event.AirshipsDataEvents;
import net.fabricacs.api.impl.BundledVanillaModLoader;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 不启动 GUI，也不依赖外部测试框架；文件夹具全部位于 build 沙箱。 */
public final class FrameworkRegression {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(), "run-");
        loadResults();
        bundles(root);
        classPaths(root);
        checks += net.fabricacs.api.impl.BundleStoreRegression.run(root.resolve("managed-bundles"));
        checks += EventRegression.run();
        checks += RenamePanelRegression.run();
        checks += ModManagementRegression.run(root.resolve("mod-management"));
        checks += net.fabricacs.acbric.GameIdentityRegression.run(root.resolve("game-identity"));
        checks += net.fabricacs.api.impl.StartupDiagnosticsRegression.run(root.resolve("startup-diagnostics"));
        checks += CampaignDataRegression.run(root.resolve("campaign-data"));
        checks += CampaignLifecycleRegression.run();
        checks += ConfigRegression.run(root.resolve("configs"));
        System.out.println("REGRESSION PASS: " + checks + " checks; fixtures=" + root);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }

    private static Path jar(Path path, Map<String, String> entries) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return path;
    }

    private static void extract(Path jar, Path mods, String id) throws Exception {
        Method method = BundledVanillaModLoader.class.getDeclaredMethod(
                "extractBundled", Path.class, Path.class, String.class);
        method.setAccessible(true);
        method.invoke(null, jar, mods, id);
    }

    private static void loadResults() throws Exception {
        Method hook = Class.forName("net.fabricacs.api.mixin.LoadableMixin")
                .getDeclaredMethod("acbric$onDataLoaded", CallbackInfoReturnable.class);
        hook.setAccessible(true);
        List<Boolean> observed = new ArrayList<>();
        AirshipsDataEvents.DATA_LOADED.register(observed::add);
        try {
            for (boolean result : new boolean[]{false, true}) {
                CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("regression", true, result);
                hook.invoke(null, callback);
                check(callback.getReturnValueZ() == result && !callback.isCancelled(),
                        "data hook preserves result " + result);
            }
            check(observed.equals(List.of(false, true)), "DATA_LOADED reports original results exactly once");
        } finally {
            AirshipsDataEvents.DATA_LOADED.clearListeners();
        }
    }

    private static void bundles(Path root) throws Exception {
        Path mods = root.resolve("extraction/mods");
        String[] invalid = {"../escaped.txt", "sub/../../escaped.txt", "/absolute.txt",
                "C:/absolute.txt", "C:relative.txt", "..\\escaped.txt", "sub/file:stream",
                "sub/.. /escaped.txt", "sub./file.txt"};
        for (int i = 0; i < invalid.length; i++) {
            // 先放一个合法条目，验证后续非法路径也不会导致部分发布。
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("acbric_vanilla/safe.txt", "safe");
            entries.put("acbric_vanilla/" + invalid[i], "unsafe");
            Path archive = jar(root.resolve("fixtures/invalid-" + i + ".jar"), entries);
            extract(archive, mods, "invalid_" + i);
            check(!Files.exists(mods.resolve("invalid_" + i)), "reject bundle path " + invalid[i]);
        }
        check(!Files.exists(mods.resolve("escaped.txt")) && !Files.exists(mods.getParent().resolve("escaped.txt")),
                "no escaped resource written");

        Path valid = jar(root.resolve("fixtures/valid.jar"), Map.of("acbric_vanilla/Type/value.json", "[]"));
        extract(valid, mods, "valid_mod");
        check(Files.readString(mods.resolve("valid_mod/Type/value.json")).equals("[]")
                && Files.isRegularFile(mods.resolve("valid_mod/info.json")), "valid bundle and generated info committed");

        Path suppliedInfo = jar(root.resolve("fixtures/info.jar"), Map.of("acbric_vanilla/info.json", "{\"id\":\"custom\"}"));
        extract(suppliedInfo, mods, "custom_info");
        check(Files.readString(mods.resolve("custom_info/info.json")).equals("{\"id\":\"custom\"}"),
                "preserve bundled info.json");

        Files.writeString(mods.resolve("valid_mod/Type/value.json"), "user edited");
        extract(valid, mods, "valid_mod");
        check(Files.readString(mods.resolve("valid_mod/Type/value.json")).equals("user edited"),
                "preserve user-edited managed resource during update");

        Map<String, String> conflict = new LinkedHashMap<>();
        conflict.put("acbric_vanilla/file", "file");
        conflict.put("acbric_vanilla/file/child", "conflict");
        extract(jar(root.resolve("fixtures/conflict.jar"), conflict), mods, "retry_mod");
        check(!Files.exists(mods.resolve("retry_mod")), "failed extraction leaves no installed directory");
        try (var paths = Files.list(mods.getParent().resolve(".acbric-bundles/retry_mod"))) {
            check(paths.noneMatch(p -> p.getFileName().toString().startsWith("stage-") || p.getFileName().toString().startsWith("incoming-")),
                    "failed extraction removes staging directory");
        }
        extract(valid, mods, "retry_mod");
        check(Files.isRegularFile(mods.resolve("retry_mod/info.json")), "valid retry succeeds after failed extraction");

        extract(valid, mods, "../invalid_id");
        check(!Files.exists(mods.getParent().resolve("invalid_id")), "reject invalid mod ID");
        Path outside = Files.createDirectories(root.resolve("link-destination"));
        Path link = mods.resolve("linked_mod");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException e) {
            System.out.println("SKIP symlink fixture: " + e);
            return;
        }
        extract(valid, mods, "linked_mod");
        check(!Files.exists(outside.resolve("info.json")), "reject symbolic link destination");
        Path parentLink = root.resolve("linked-parent");
        Files.createSymbolicLink(parentLink, outside);
        extract(valid, parentLink.resolve("mods"), "parent_link_mod");
        check(!Files.exists(outside.resolve("mods")), "reject symbolic link ancestor before creating directories");
    }

    @SuppressWarnings("unchecked")
    private static void classPaths(Path root) throws Exception {
        Path libs = Files.createDirectories(root.resolve("classpath/libs"));
        String[] markers = {"net/fabricmc/loader/api/FabricLoader.class",
                "org/spongepowered/asm/mixin/Mixin.class", "org/objectweb/asm/ClassReader.class",
                "org/objectweb/asm/tree/analysis/Analyzer.class", "net/fabricacs/acbric/AirshipsGameProvider.class"};
        for (int i = 0; i < markers.length; i++) jar(libs.resolve("renamed-" + i + ".jar"), Map.of(markers[i], "fixture"));
        Path game = jar(libs.resolve("asplit-A.zip"), Map.of("com/zarkonnen/airships/Main.class", "fixture"));
        Path gameLib = jar(libs.resolve("game-library.jar"), Map.of("some/library/Helper.class", "fixture"));
        jar(libs.resolve("steamworks4j-1.3.0.jar"), Map.of("old/steam.class", "fixture"));
        Path config = root.resolve("classpath/Airships.json");
        Files.writeString(config, "{\"classPath\":[\"renamed-0.jar\",\"renamed-1.jar\",\"renamed-2.jar\",\"asplit-A.zip\"]}");

        AirshipsGameProvider provider = new AirshipsGameProvider();
        Field directory = AirshipsGameProvider.class.getDeclaredField("libsDirectory");
        directory.setAccessible(true);
        directory.set(provider, libs);
        Method read = AirshipsGameProvider.class.getDeclaredMethod("readAirshipsConfig", Path.class);
        read.setAccessible(true);
        read.invoke(provider, config);
        Method collect = AirshipsGameProvider.class.getDeclaredMethod("collectClassPath");
        collect.setAccessible(true);
        collect.invoke(provider);
        Field cp = AirshipsGameProvider.class.getDeclaredField("gameClassPath");
        cp.setAccessible(true);
        List<Path> actual = (List<Path>) cp.get(provider);
        check(actual.size() == 2 && actual.contains(game) && actual.contains(gameLib),
                "config and directory scan exclude renamed launcher libraries, preserve game libraries");
        collect.invoke(provider);
        check(actual.size() == 2, "classpath collection does not duplicate entries");
    }
}
