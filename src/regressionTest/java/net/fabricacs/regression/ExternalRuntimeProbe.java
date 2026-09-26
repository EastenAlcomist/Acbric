/* ExternalRuntimeProbe.java — 真实 Knot 中验证外部代码来源和 preLaunch 路径；不调用 Main，不创建图形或战役。 */
package net.fabricacs.regression;

import com.zarkonnen.airships.AGame;
import net.fabricacs.api.AcbricInitializer;
import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.util.AirshipsPaths;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

public final class ExternalRuntimeProbe implements AcbricInitializer {
    private static int checks;
    private static boolean initialized;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++; System.out.println("PASS external runtime: " + message);
    }
    @Override public void onInitializeAcbric() { throw new AssertionError("Context entry expected"); }
    @Override public void onInitializeAcbric(AcbricModContext context) {
        Path instance = Path.of(System.getProperty("acbric.external.instance"));
        check(AGame.getGameDirectory().toPath().equals(instance.resolve("userdata")), "userdata correct during acbric preLaunch entry");
        check(Files.isRegularFile(instance.resolve("userdata/mods/external_probe/marker.txt")), "bundled resources installed before entry under instance userdata");
        check(AirshipsPaths.ensureModConfigDir("external_probe").startsWith(instance), "MOD config belongs to instance");
        initialized = true;
    }
    public static void main(String[] args) throws Exception {
        Path install = Path.of(System.getProperty("acbric.external.install"));
        Path instance = Path.of(System.getProperty("acbric.external.instance"));
        check(initialized, "real API preLaunch and contextual entry completed");
        check(FabricLoader.getInstance().getGameDir().equals(instance), "Fabric gameDir is instance");
        check(AGame.getStaticGameDirectory().toPath().equals(install), "native static resources point to selected install");
        check(AGame.getGameDirectory().toPath().equals(instance.resolve("userdata")), "native userdata never selects APPDATA");
        for (String name : List.of("Main", "AGame", "WorldMap", "GameSetupScreen", "WorldGenScreen", "CampaignWorld", "StrategicLobbyScreen", "AirshipGame", "LoadingScreen", "Mod", "ModsScreen", "Client")) {
            Class<?> type = Class.forName("com.zarkonnen.airships." + name, false, Thread.currentThread().getContextClassLoader());
            Path source = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
            check(source.equals(install.resolve("asplit-A.zip")) || source.equals(install.resolve("asplit-B.zip")), "transformed " + name + " comes from selected A/B");
        }
        String version = (String) AGame.class.getField("VERSION").get(null);
        check(version.equals(System.getProperty("acbric.internal.game.version")), "runtime version matches preflight bytecode identity");
        check(System.getProperty("java.library.path").equals(install.resolve("lib/native").toString()), "native path belongs to install");
        check(Files.isRegularFile(instance.resolve("logs/acbric/" + System.getProperty("acbric.internal.diagnostics.session") + "/launch.properties")), "startup diagnostics belong to instance");
        check(AirshipsPaths.ensureModDataDir("external_probe").startsWith(instance), "MOD persistent data remains writable instance data");
        check(!Files.exists(instance.resolve("asplit-A.zip")) && !Files.exists(instance.resolve("lib")), "no game archive/library copies in instance");
        // 原生失败回退必须关闭：模拟运行中用户数据目录消失，不去创建全局目录。
        Path userdata = instance.resolve("userdata");
        Path held = instance.resolve("probe-userdata-held");
        Files.move(userdata, held);
        try {
            try { AGame.getGameDirectory(); throw new AssertionError("Expected userdata rejection"); }
            catch (IllegalStateException expected) { check(expected.getMessage().contains("no fallback"), "missing userdata fails without native fallback"); }
        } finally { Files.move(held, userdata); }
        System.out.println("EXTERNAL RUNTIME PASS: " + checks + " checks");
    }
}
