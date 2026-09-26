/* ExternalGifMixin.java — GIF 导出前确认实例目标可写，失败中止，不进入原生 Desktop/安装目录回退。 */
package net.fabricacs.api.mixin;

import com.zarkonnen.airships.*;
import net.fabricacs.api.impl.ExternalTextureCache;
import net.fabricacs.management.ModSelection;
import java.io.*;
import java.nio.file.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlaybackIntent.class, remap = false)
public abstract class ExternalGifMixin {
    @Inject(method = "takeGif(Lcom/zarkonnen/airships/UniScreen;ZZ)V", at = @At("HEAD"))
    private void acbric$gifTarget(UniScreen screen, boolean slow, boolean scaled, CallbackInfo ci) {
        if (!ExternalTextureCache.active()) return;
        Path gifs = ExternalTextureCache.instance().resolve("userdata/gifs");
        try {
            ModSelection.rejectLinks(gifs); Files.createDirectories(gifs);
            Path probe = Files.createTempFile(gifs, ".write-check-", ".tmp"); Files.delete(probe);
            LaunchSettings.customGIFSaveDirectoryLocation = gifs.toString();
        } catch (IOException ex) { throw new UncheckedIOException("Cannot write instance GIFs; no fallback / 实例 GIF 目录不可写，不回退", ex); }
    }
}
