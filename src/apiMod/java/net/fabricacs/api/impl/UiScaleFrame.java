/* UiScaleFrame.java — 仅在提交缩放 UI 素材时还原纹理裁切并传入显示尺寸；不变换世界或鼠标坐标。 */
package net.fabricacs.api.impl;

import com.zarkonnen.catengine.Frame;
import com.zarkonnen.catengine.Img;
import com.zarkonnen.catengine.util.Clr;
import com.zarkonnen.catengine.util.Pt;
import com.zarkonnen.catengine.util.ScreenMode;

public final class UiScaleFrame implements Frame {
    private final Frame original;
    private UiScaleFrame(Frame original) { this.original = original; }
    public static Frame wrap(Frame frame) {
        return frame == null || frame instanceof UiScaleFrame ? frame : new UiScaleFrame(frame);
    }
    @Override public ScreenMode mode() { return original.mode(); }
    @Override public int fps() { return original.fps(); }
    @Override public Object nativeRenderer() { return original.nativeRenderer(); }
    @Override public Pt cursor() { return original.cursor(); }
    @Override public void rect(Clr c, double x, double y, double w, double h, double angle) {
        original.rect(c, x, y, w, h, angle);
    }
    @Override public void blit(Img img, Clr c, double alpha, double x, double y, double w, double h, double angle) {
        ScaledUiImage logical = img instanceof ScaledUiImage scaled ? scaled : null;
        if (img instanceof ScaledUiImage scaled) {
            if (w == 0) w = getWidth(img);
            if (h == 0) h = getHeight(img);
            img = scaled.original;
        }
        original.blit(img, c, alpha, x, y, w, h, angle);
        if (logical != null) logical.synchronizeCache();
    }
    @Override public double getWidth(Img img) {
        if (img instanceof ScaledUiImage scaled)
            return img.srcWidth > 0 ? img.srcWidth : original.getWidth(scaled.original) * scaled.factor;
        return original.getWidth(img);
    }
    @Override public double getHeight(Img img) {
        if (img instanceof ScaledUiImage scaled)
            return img.srcHeight > 0 ? img.srcHeight : original.getHeight(scaled.original) * scaled.factor;
        return original.getHeight(img);
    }
    @Override public void shift(double x, double y) { original.shift(x, y); }
    @Override public void scale(double x, double y) { original.scale(x, y); }
    @Override public void rotate(double angle) { original.rotate(angle); }
    @Override public void resetTransforms() { original.resetTransforms(); }
}
