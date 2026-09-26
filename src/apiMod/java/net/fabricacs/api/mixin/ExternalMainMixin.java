/* ExternalMainMixin.java — 外部 Windows 启动经 JVM Unicode 路径预载 OpenAL，兼容 LWJGL 2 的窄字符查找。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.Main;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Main.class, remap = false)
public abstract class ExternalMainMixin {
    @Inject(method = "main([Ljava/lang/String;)V", at = @At("HEAD"))
    private static void acbric$unicodeAudioLibrary(String[] args, CallbackInfo ci) {
        String install = System.getProperty("acbric.external.install");
        if (install == null) return;
        // 外部预检查目前只接受 Windows x64。使用所选安装的 DLL，不复制、不下载、不改全局 PATH。
        // LWJGL 随后按已加载模块名取得句柄；失败让启动诊断保留真正的 DLL 加载错误。
        Path audio = Path.of(install).resolve("lib/native/OpenAL64.dll").toAbsolutePath().normalize();
        try { System.load(audio.toString()); }
        catch (UnsatisfiedLinkError ex) {
            throw new IllegalStateException("Cannot load selected OpenAL / 无法加载所选游戏的音频库: " + audio, ex);
        }
    }
}
