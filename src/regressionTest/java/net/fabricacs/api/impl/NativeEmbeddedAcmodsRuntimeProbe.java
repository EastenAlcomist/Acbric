/* NativeEmbeddedAcmodsRuntimeProbe.java — 在真实 Knot 中验证 API 默认识别本地与 Steam 原版 MOD 的 ACMOD。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.Mod;
import com.zarkonnen.airships.ModsScreen;
import net.fabricmc.loader.api.FabricLoader;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class NativeEmbeddedAcmodsRuntimeProbe {
    private NativeEmbeddedAcmodsRuntimeProbe() {
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class.forName("com.zarkonnen.airships.Mod", false, loader);
        Class.forName("com.zarkonnen.airships.ModsScreen$5", false, loader);

        Path game = FabricLoader.getInstance().getGameDir();
        Path steamRoot = game.resolve("steam/mods/123/fixture");
        Path localRoot = game.resolve("mods/local_fixture");
        writeCarrier(steamRoot, "steam.jar", "steam_fixture_acmod");
        writeCarrier(localRoot, "local.jar", "local_fixture_acmod");

        var constructor = Mod.class.getDeclaredConstructor(java.io.File.class, boolean.class);
        constructor.setAccessible(true);
        Mod steam = constructor.newInstance(steamRoot.toFile(), false);
        steam.id = "steam_fixture";
        steam.name.put(Locale.ENGLISH, "Steam fixture");
        Mod local = constructor.newInstance(localRoot.toFile(), true);
        local.id = "local_fixture";
        local.name.put(Locale.ENGLISH, "Local fixture");

        NativeEmbeddedAcmods.refresh(List.of(steam, local));
        assertCarrier(steam, "Steam");
        assertCarrier(local, "local");
        assertInjectedRowSuffix(local);
        System.out.println("NATIVE EMBEDDED ACMOD RUNTIME PASS");
    }

    private static void writeCarrier(Path root, String name, String id) throws Exception {
        Path carrier = Files.createDirectories(root.resolve("AcbricMod"));
        try (var output = Files.newOutputStream(carrier.resolve(name));
             var jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1\"}")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    private static void assertCarrier(Mod mod, String label) {
        if (NativeEmbeddedAcmods.carrier(mod) == null
                || !NativeEmbeddedAcmods.suffix(mod).contains("AcbricMOD")) {
            throw new AssertionError(label + " carrier was not recognized by the default API");
        }
    }

    private static void assertInjectedRowSuffix(Mod mod) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        ModsScreen screen = (ModsScreen) unsafe.allocateInstance(ModsScreen.class);
        Class<?> adapterType = Class.forName("com.zarkonnen.airships.ModsScreen$5");
        var constructor = adapterType.getDeclaredConstructor(ModsScreen.class);
        constructor.setAccessible(true);
        Object adapter = constructor.newInstance(screen);
        var name = adapterType.getDeclaredMethod("name", Mod.class);
        name.setAccessible(true);
        String row = String.valueOf(name.invoke(adapter, mod));
        if (!row.contains("AcbricMOD")) {
            throw new AssertionError("Default API row-name injection missing: " + row);
        }
    }
}
