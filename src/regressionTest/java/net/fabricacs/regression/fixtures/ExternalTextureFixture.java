/* ExternalTextureFixture.java — 只替换显存创建终端，原生贴图文件读取、raw 校验/写入和回退仍真实执行。 */
package net.fabricacs.regression.fixtures;

import com.zarkonnen.airships.SpriteUtils;
import java.io.*;
import net.fabricacs.regression.TextureProbeImage;
import org.newdawn.slick.Image;
import org.newdawn.slick.opengl.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = SpriteUtils.class, remap = false)
public abstract class ExternalTextureFixture {
    @Redirect(method = "loadImageFromFile", at = @At(value = "NEW", target = "(Ljava/io/InputStream;Ljava/lang/String;Z)Lorg/newdawn/slick/Image;"))
    private static Image fixturePng(InputStream input, String name, boolean flipped) throws IOException {
        if (input.read() < 0) throw new IOException("Empty fixture PNG");
        return new TextureProbeImage();
    }
    @Redirect(method = "loadImageFromFile", at = @At(value = "NEW", target = "(Lorg/newdawn/slick/opengl/ImageData;)Lorg/newdawn/slick/Image;"))
    private static Image fixtureRaw(ImageData data) {
        if (data.getImageBufferData().remaining() != 1024) throw new AssertionError("Expected 16x16 raw data");
        return new TextureProbeImage();
    }
}
