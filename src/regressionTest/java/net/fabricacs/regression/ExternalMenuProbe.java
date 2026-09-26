/* ExternalMenuProbe.java — 调用真实 Main 并在菜单实际绘制后记录检查点；不替换图形/资源加载。 */
package net.fabricacs.regression;

import java.nio.file.*;
import org.lwjgl.opengl.*;

public final class ExternalMenuProbe {
    private static int frames;
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("java.security.manager", "").equals(SmokeGuard.class.getName()))
            throw new IllegalStateException("Isolated smoke guard required");
        com.zarkonnen.airships.Main.main(args);
        throw new AssertionError("Game returned before menu checkpoint");
    }
    public static void rendered() {
        if (Boolean.getBoolean("acbric.test.media")) { ExternalMediaProbe.menuRendered(); return; }
        if (System.getProperty("acbric.test.campaign") != null) {
            ExternalCampaignProbe.menuRendered();
            return;
        }
        if (++frames < 30) return;
        try {
            if (!Display.isCreated()) throw new AssertionError("No native OpenGL display");
            if (!org.lwjgl.openal.AL.isCreated()) throw new AssertionError("No native OpenAL context");
            Path instance = Path.of(System.getProperty("acbric.external.instance"));
            String report = "menuFrames=" + frames + "\nrenderer=" + GL11.glGetString(GL11.GL_RENDERER)
                    + "\nversion=" + GL11.glGetString(GL11.GL_VERSION)
                    + "\naudio=" + org.lwjgl.openal.AL10.alGetString(org.lwjgl.openal.AL10.AL_RENDERER) + "\n";
            Files.writeString(instance.resolve("menu-checkpoint.txt"), report);
            System.out.println("EXTERNAL MENU PASS: " + report);
            System.exit(0);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
