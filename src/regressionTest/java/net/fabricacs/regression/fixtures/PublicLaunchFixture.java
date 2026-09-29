/* PublicLaunchFixture.java — 正式发行入口的测试 MOD；观测真实菜单及代码来源，等待控制文件后退出测试。 */
package net.fabricacs.regression.fixtures;

import com.zarkonnen.airships.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainMenu.class, remap = false)
public abstract class PublicLaunchFixture {
    @Unique private int acbric$frames;
    @Inject(method = "render", at = @At("RETURN"))
    private void acbric$checkpoint(MyDraw draw, com.zarkonnen.catengine.util.ScreenMode mode, com.zarkonnen.catengine.Hooks hooks, com.zarkonnen.catengine.util.Pt cursor, CallbackInfo ci) throws Exception {
        if (++acbric$frames < 30) return;
        Path instance = FabricLoader.getInstance().getGameDir();
        if (acbric$frames == 30) {
            Path install = Path.of(System.getProperty("acbric.external.install"));
            Path source = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!source.equals(install.resolve("asplit-A.zip")) && !source.equals(install.resolve("asplit-B.zip"))) throw new AssertionError("Wrong game source");
            if (!Path.of("").toAbsolutePath().normalize().equals(instance)) throw new AssertionError("Wrong child cwd");
            if (System.getProperty("acbric.internal.externalProbeMain") != null) throw new AssertionError("Probe entry used");
            if (!Display.isCreated() || !org.lwjgl.openal.AL.isCreated()) throw new AssertionError("Native display/audio missing");
            if (!AGame.getGameDirectory().toPath().equals(instance.resolve("userdata"))) throw new AssertionError("Wrong userdata");
            if (!AGame.getStaticGameDirectory().toPath().equals(install)) throw new AssertionError("Wrong resources");
            if (Files.exists(instance.resolve("java-only-test"))) {
                try { net.fabricacs.regression.JavaOnlyModListChecks.run(instance, draw, mode, hooks, cursor); }
                catch (Throwable failure) {
                    failure.printStackTrace();
                    Files.writeString(instance.resolve("java-only-failure.txt"), failure.toString());
                    System.exit(71);
                }
            } else acbric$checkMods(instance);
            String scenario = Files.exists(instance.resolve("java-only-test"))
                    ? "Java-only ModsScreen rendering, native availability isolation, refresh and restart selection"
                    : "shared Java/native MOD scan/install/bundles/manager/fleet";
            Files.writeString(instance.resolve("public-checkpoint.txt"), "PASS: actual Main, 30 rendered frames, native audio, selected A/B, instance cwd/data, external core, " + scenario + "\njava.home=" + System.getProperty("java.home") + "\n");
        }
        if (Files.exists(instance.resolve("allow-test-exit"))) System.exit(0);
    }
    @Unique private static void acbric$checkMods(Path instance) throws Exception {
        Path root = Path.of(System.getProperty("acbric.external.mods"));
        if (!net.fabricacs.api.util.AirshipsPaths.modsDir().equals(root)) throw new AssertionError("Wrong Java MOD API path");
        if (!net.fabricacs.api.impl.LocalModPaths.nativeMods().equals(root)) throw new AssertionError("Wrong native MOD root");
        Mod nativeMod = Mod.getById("native_test");
        if (nativeMod == null || !nativeMod.dir.toPath().equals(root.resolve("native_test"))) throw new AssertionError("Native MOD not scanned");
        if (Mod.getById("old_path_test") != null) throw new AssertionError("Old MOD path still scanned");
        if (!Files.isRegularFile(root.resolve("public_launch_test/info.json"))) throw new AssertionError("Bundled MOD not extracted to shared root");
        net.fabricacs.api.impl.JavaModManager.refresh();
        var manager = net.fabricacs.api.impl.JavaModManager.current();
        if (manager == null || !manager.entry("public_launch_test").manageable()) throw new AssertionError("Shared Java MOD not manageable");
        manager.toggle("public_launch_test"); manager.toggle("public_launch_test");
        Class<?> backend = Class.forName("com.zarkonnen.airships.FleetCreationScreen$FleetModsBackend");
        var constructor = backend.getDeclaredConstructor(); constructor.setAccessible(true);
        Object files = constructor.newInstance();
        var raw = backend.getDeclaredMethod("getRawFile", java.util.List.class); raw.setAccessible(true);
        if (!((java.io.File) raw.invoke(files, java.util.List.of())).toPath().equals(root)) throw new AssertionError("Fleet browser root unchanged");
        // 强制转换舰队打开/保存实现，验证其挂钩签名，避免只有进入编辑器才发现不兼容。
        Class.forName("com.zarkonnen.airships.FleetCreationScreen$OpenFleetMission");
        Class.forName("com.zarkonnen.airships.FleetCreationScreen$SaveFleetMission");
        Path source = instance.resolve("native-install.amod");
        if (!Files.exists(root.resolve("installed_native/info.json"))) {
            try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(source))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("installed_native/info.json"));
                zip.write("{\"id\":\"installed_native\",\"name\":\"Installed native\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
            var gameField = AirshipGame.class.getDeclaredField("instance"); gameField.setAccessible(true);
            ModsScreen screen = new ModsScreen((AirshipGame) gameField.get(null));
            var install = ModsScreen.class.getDeclaredMethod("doInstall", java.io.File.class, com.zarkonnen.catengine.Input.class);
            install.setAccessible(true); install.invoke(screen, source.toFile(), null);
            if (!Files.exists(root.resolve("installed_native/info.json"))) throw new AssertionError("Native install used wrong root");
        }
        Path jar = instance.resolve("installed-java.jar");
        if (!Files.exists(root.resolve("installed-java.jar"))) {
            try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
                zip.write("{\"schemaVersion\":1,\"id\":\"installed_java\",\"version\":\"1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
            if (!net.fabricacs.api.impl.FabricModInstallBridge.installIfFabricModJar(jar.toFile()).isInstalled()
                    || !Files.exists(root.resolve("installed-java.jar"))) throw new AssertionError("Java install used wrong root");
        } else if (!FabricLoader.getInstance().isModLoaded("installed_java")) throw new AssertionError("Installed Java MOD not loaded after restart");
        if (Files.exists(instance.resolve("mods")) || Files.exists(instance.resolve("userdata/mods/installed_native"))) throw new AssertionError("Old destination used");
    }

}
