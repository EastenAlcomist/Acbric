/* TextureProbeImage.java — 测试显存终端，不创建 OpenGL 上下文，不能进入正式 API JAR。 */
package net.fabricacs.regression;
import java.lang.reflect.Proxy;
import org.newdawn.slick.Image;
import org.newdawn.slick.opengl.Texture;
public final class TextureProbeImage extends Image {
    private final Texture fixtureTexture = (Texture) Proxy.newProxyInstance(Texture.class.getClassLoader(), new Class<?>[]{Texture.class}, (proxy, method, args) -> switch (method.getName()) {
        case "getTextureData" -> new byte[1024];
        case "getTextureID", "getImageWidth", "getImageHeight", "getTextureWidth", "getTextureHeight" -> 16;
        default -> null;
    });
    @Override public int getWidth() { return 16; }
    @Override public int getHeight() { return 16; }
    @Override public Texture getTexture() { return fixtureTexture; }
    @Override public boolean isDestroyed() { return false; }
    @Override public void destroy() { }
}
