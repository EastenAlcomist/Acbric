/* ExternalPathsMixin.java — 外部原型早期分离资源根与用户数据根；旧布局完全沿用原生路径逻辑。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.AGame;
import java.io.File;
import java.nio.file.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AGame.class, remap = false)
public abstract class ExternalPathsMixin {
    @Inject(method = "getStaticGameDirectory()Ljava/io/File;", at = @At("HEAD"), cancellable = true)
    private static void acbric$externalResources(CallbackInfoReturnable<File> cir) {
        String install = System.getProperty("acbric.external.install");
        if (install != null) cir.setReturnValue(Path.of(install).toFile());
    }

    /** 早于 preLaunch 的内嵌 MOD 解包；不引用 LaunchSettings，避免循环初始化及原生失败回退。 */
    @Inject(method = "getGameDirectory()Ljava/io/File;", at = @At("HEAD"), cancellable = true)
    private static void acbric$externalUserdata(CallbackInfoReturnable<File> cir) {
        if (System.getProperty("acbric.external.install") == null) return;
        String instance = System.getProperty("acbric.external.instance");
        if (instance == null) throw new IllegalStateException("Missing external instance / 缺少外部实例路径");
        Path data = Path.of(instance).resolve("userdata");
        if (!Files.isDirectory(data) || !Files.isWritable(data))
            throw new IllegalStateException("Instance userdata unavailable; no fallback / 实例用户数据不可用，不回退到玩家原档: " + data);
        cir.setReturnValue(data.toFile());
    }
}
