/* ExternalTextureMixin.java — 只替换原生加载器的文件实参，纹理解码/显存缓存仍交原生处理。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.SpriteUtils;
import net.fabricacs.api.impl.ExternalTextureCache;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpriteUtils.class, remap = false)
public abstract class ExternalTextureMixin {
    @Inject(method = "ensureTexFilesInGeneratedDirectory(Ljava/io/File;)V", at = @At("HEAD"), cancellable = true)
    private static void acbric$keepOriginals(File images, CallbackInfo ci) {
        if (ExternalTextureCache.active()) ci.cancel();
    }
    @ModifyVariable(method = "loadImageFromFile(Ljava/lang/String;Lcom/zarkonnen/airships/SpriteUtils$ImageEntry;Ljava/io/File;)Lorg/newdawn/slick/Image;", at = @At("HEAD"), argsOnly = true, index = 2)
    private static File acbric$cachedFile(File file) { return ExternalTextureCache.image(file); }
}
