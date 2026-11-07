/* UiScaleRegression.java — 验证百分比边界、纹理裁切与显示尺寸分离，以及世界和鼠标坐标不变。 */
package net.fabricacs.api.impl;

import com.zarkonnen.catengine.*;
import com.zarkonnen.catengine.util.*;
import net.fabricacs.api.ui.UiScale;

public final class UiScaleRegression {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
        System.out.println("PASS UI SCALE: " + message);
    }
    public static int run() {
        checks = 0;
        UiScale.useVanilla();
        check(UiScale.percent() == 0, "default uses vanilla");
        for (int percent : new int[]{50, 100, 125, 137, 200}) {
            UiScale.setPercent(percent);
            check(UiScale.percent() == percent, "accept exact percent " + percent);
        }
        for (int percent : new int[]{Integer.MIN_VALUE, 0, 49, 201, Integer.MAX_VALUE}) {
            boolean rejected = false;
            try { UiScale.setPercent(percent); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected && UiScale.percent() == 200, "invalid request preserves prior scale " + percent);
        }
        try {
            RecordingFrame recording = new RecordingFrame();
            Frame frame = UiScaleFrame.wrap(recording);
            check(UiScaleFrame.wrap(frame) == frame && UiScaleFrame.wrap(null) == null, "wrapping is idempotent and accepts absent frame");
            Img original = new Img("atlas", 11, 17, 23, 19, false);
            Img scaled = new ScaledUiImage(original, 1.25);
            check(scaled.srcWidth == 29 && scaled.srcHeight == 24, "fractional dimensions round to pixels");
            check(original.srcWidth == 23 && original.srcX == 11, "source texture is unchanged");
            frame.blit(scaled, null, .75, 123, 456, 0, 0, .2);
            check(recording.image == original && recording.w == 29 && recording.h == 24, "native draw receives original crop and scaled natural size");
            check(recording.x == 123 && recording.y == 456 && recording.alpha == .75 && recording.angle == .2, "position opacity and rotation preserved");
            frame.blit(scaled, null, 1, 1, 2, 77, 88, 0);
            check(recording.w == 77 && recording.h == 88, "explicit dimensions are not scaled twice");
            frame.blit(original, null, 1, 1, 2, 0, 0, 0);
            check(recording.image == original && recording.w == 0 && recording.h == 0, "world images pass through unchanged");
            check(frame.mode() == recording.mode && frame.cursor() == recording.cursor && frame.nativeRenderer() == recording, "resolution renderer and cursor identity preserved");
            check(frame.getWidth(scaled) == 29 && frame.getHeight(scaled) == 24, "measurement equals rendering");
            ScaledUiImage flipped = (ScaledUiImage) scaled.flip();
            check(flipped.srcWidth == 29 && flipped.original.flipped && !original.flipped, "flipping retains logical size without mutating source");
            check(new ScaledUiImage(new Img("tiny", 0, 0, 1, 1, false), .5).srcWidth == 1, "thin borders remain at least one pixel");
            Img full = new ScaledUiImage(new Img("whole"), 1.5);
            frame.blit(full, null, 1, 0, 0, 0, 0, 0);
            check(recording.w == 60 && recording.h == 30, "whole images use native texture dimensions");
            CacheImage sourceCache = new CacheImage(23, 19);
            original.machineImgCache = sourceCache;
            frame.blit(scaled, null, 1, 0, 0, 0, 0, 0);
            var cache = (org.newdawn.slick.Image) scaled.machineImgCache;
            check(cache.getWidth() == 29 && cache.getHeight() == 24 && original.machineImgCache == sourceCache,
                    "native repeated-border cache receives scaled view with source retained");
            frame.blit(scaled, null, 1, 0, 0, 0, 0, 0);
            check(scaled.machineImgCache == cache && sourceCache.copies == 1, "steady border draws reuse cached view");
            original.machineImgCache = new CacheImage(23, 19);
            frame.blit(scaled, null, 1, 0, 0, 0, 0, 0);
            check(scaled.machineImgCache != cache, "texture reload rebuilds repeated-border cache");
        } finally { UiScale.useVanilla(); }
        check(UiScale.percent() == 0, "reset returns to vanilla");
        return checks;
    }
    private static final class RecordingFrame implements Frame {
        final ScreenMode mode = new ScreenMode(1920, 1080, false);
        final Pt cursor = new Pt(123, 456);
        Img image; double x, y, w, h, alpha, angle;
        public ScreenMode mode() { return mode; }
        public int fps() { return 60; }
        public Object nativeRenderer() { return this; }
        public Pt cursor() { return cursor; }
        public void rect(Clr c, double x, double y, double w, double h, double angle) { }
        public void blit(Img image, Clr c, double alpha, double x, double y, double w, double h, double angle) {
            this.image=image;this.alpha=alpha;this.x=x;this.y=y;this.w=w;this.h=h;this.angle=angle;
        }
        public double getWidth(Img image) { return image.srcWidth > 0 ? image.srcWidth : 40; }
        public double getHeight(Img image) { return image.srcHeight > 0 ? image.srcHeight : 20; }
        public void shift(double x, double y) { }
        public void scale(double x, double y) { }
        public void rotate(double angle) { }
        public void resetTransforms() { }
    }
    /** 不创建 GL 纹理，只检查原版边框快速路径所用缓存的尺寸与生命周期。 */
    private static final class CacheImage extends org.newdawn.slick.Image {
        final int width, height; int copies;
        CacheImage(int width, int height) { this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public org.newdawn.slick.Image getScaledCopy(int width, int height) { copies++; return new CacheImage(width, height); }
    }
}
