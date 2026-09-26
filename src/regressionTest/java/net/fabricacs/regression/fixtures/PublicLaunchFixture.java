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
    private void acbric$checkpoint(CallbackInfo ci) throws Exception {
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
            Files.writeString(instance.resolve("public-checkpoint.txt"), "PASS: actual Main, 30 rendered frames, native audio, selected A/B, instance cwd/data, external core\njava.home=" + System.getProperty("java.home") + "\n");
        }
        if (Files.exists(instance.resolve("allow-test-exit"))) System.exit(0);
    }
}
