/*
 * ModManagementRegression.java — 验证 Fabric 安装元数据、拒绝覆盖和嵌套 MOD 来源显示。
 */
package net.fabricacs.regression;

import net.fabricacs.acbric.AirshipsGameProvider;
import net.fabricacs.api.impl.FabricModInstallBridge;
import net.fabricacs.api.impl.FabricModListBridge;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;

public final class ModManagementRegression {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++; System.out.println("PASS " + label);
    }
    private static Path jar(Path file, String entry, String value) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(entry)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return file;
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        Files.createDirectories(root);
        // 不初始化游戏类，直接核对编译用原版字节码中新的 render 挂钩目标。
        var screen = new org.objectweb.asm.tree.ClassNode();
        try (var input = ModManagementRegression.class.getResourceAsStream("/com/zarkonnen/airships/ModsScreen.class")) {
            new org.objectweb.asm.ClassReader(input).accept(screen, 0);
        }
        int visibilityCalls = 0;
        for (var method : screen.methods) if (method.name.equals("render")) {
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("com/zarkonnen/airships/Mod") && call.name.equals("getAvailableMods")
                        && call.desc.equals("()Ljava/util/ArrayList;")) visibilityCalls++;
            }
        }
        check(visibilityCalls == 1, "MOD list render has exactly one native availability gate for UI-only redirect");
        AirshipsGameProvider provider = new AirshipsGameProvider();
        Field gameDirectory = AirshipsGameProvider.class.getDeclaredField("gameDirectory");
        gameDirectory.setAccessible(true); gameDirectory.set(provider, root);
        FabricLoaderImpl.INSTANCE.setGameProvider(provider);
        String valid = "{\"schemaVersion\":1,\"id\":\"regression_install\",\"version\":\"1.0.0\"}";
        String[] invalid = {"not JSON", "[]", "{\"schemaVersion\":100,\"id\":\"unknown_schema\",\"version\":\"1\"}",
                "{\"schemaVersion\":1,\"version\":\"1\"}", "{\"schemaVersion\":1,\"id\":\"INVALID ID\",\"version\":\"1\"}",
                "{\"schemaVersion\":1,\"id\":\"missing_version\"}", "{\"schemaVersion\":1,\"id\":\"bad_entry\",\"version\":\"1\",\"entrypoints\":[]}"};
        for (int i = 0; i < invalid.length; i++) {
            Path bad = jar(root.resolve("invalid-" + i + ".jar"), "fabric.mod.json", invalid[i]);
            var result = FabricModInstallBridge.installIfFabricModJar(bad.toFile());
            check(result.isHandled() && !result.isInstalled() && !Files.exists(root.resolve("mods/" + bad.getFileName())), "reject invalid Fabric metadata " + i + " before publishing");
        }
        Path good = jar(root.resolve("valid.jar"), "fabric.mod.json", valid);
        check(FabricModInstallBridge.installIfFabricModJar(good.toFile()).isInstalled(), "valid metadata installs successfully");
        check(java.util.Arrays.equals(Files.readAllBytes(good), Files.readAllBytes(root.resolve("mods/valid.jar"))), "installed JAR equals validated source copy");
        Path vanilla = jar(root.resolve("vanilla.jar"), "info.json", "{}");
        check(!FabricModInstallBridge.installIfFabricModJar(vanilla.toFile()).isHandled(), "non-Fabric archive remains available to vanilla handler");
        try (var cache = Files.list(root.resolve(".fabric"))) {
            check(cache.noneMatch(p -> p.getFileName().toString().startsWith("acbric-install-")), "installer cleans staging after success and rejection");
        }

        ModOrigin origin = (ModOrigin) Proxy.newProxyInstance(ModOrigin.class.getClassLoader(), new Class<?>[]{ModOrigin.class},
                (p, method, args) -> switch (method.getName()) {
                    case "getKind" -> ModOrigin.Kind.NESTED;
                    case "getParentModId" -> "parent_mod";
                    case "getParentSubLocation" -> "META-INF/jars/child.jar";
                    case "getPaths" -> throw new UnsupportedOperationException("nested origin has no paths");
                    default -> throw new AssertionError(method.getName());
                });
        ModContainer container = (ModContainer) Proxy.newProxyInstance(ModContainer.class.getClassLoader(), new Class<?>[]{ModContainer.class},
                (p, method, args) -> switch (method.getName()) {
                    case "getOrigin" -> origin;
                    case "getRootPaths" -> List.of();
                    default -> throw new AssertionError(method.getName());
                });
        Method paths = FabricModListBridge.class.getDeclaredMethod("sourcePaths", ModContainer.class);
        paths.setAccessible(true);
        check(paths.invoke(null, container).equals("parent_mod!/META-INF/jars/child.jar"), "nested display source does not call unsupported getPaths");
        return checks;
    }
}
