/* ExternalLaunchSettingsMixin.java — 原生静态初始化只读取合并后的实例设置；不在初始化尾部补写路径。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.LaunchSettings;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = LaunchSettings.class, remap = false)
public abstract class ExternalLaunchSettingsMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/io/FileInputStream;<init>(Ljava/io/File;)V"), index = 0)
    private static File acbric$settings(File original) {
        if (System.getProperty("acbric.external.install") == null) return original;
        String path = System.getProperty("acbric.internal.launchSettings");
        if (path == null || !new File(path).isFile()) throw new IllegalStateException("Missing instance launch settings / 缺少实例启动设置");
        return new File(path);
    }
}
