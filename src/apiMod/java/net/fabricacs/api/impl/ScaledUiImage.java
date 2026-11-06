/* ScaledUiImage.java — 将 UI 逻辑尺寸与纹理裁切分开；原始素材和引擎纹理缓存保持不变。 */
package net.fabricacs.api.impl;

import com.zarkonnen.catengine.Img;

public final class ScaledUiImage extends Img {
    public final Img original;
    public final double factor;
    private transient Object sourceCache;

    public ScaledUiImage(Img original, double factor) {
        super(original.src, original.srcX, original.srcY,
                dimension(original.srcWidth, factor), dimension(original.srcHeight, factor), original.flipped);
        this.original = original;
        this.factor = factor;
    }

    static int dimension(int value, double factor) {
        return value > 0 ? Math.max(1, (int) Math.round(value * factor)) : (int) Math.round(value * factor);
    }

    @Override public Img flip() { return new ScaledUiImage(original.flip(), factor); }

    /** 原版面板的重复边框会直接使用 Slick 缓存绘制；给它共享纹理的缩放视图，而非改写源缓存。 */
    void synchronizeCache() {
        if (original.machineImgCache == sourceCache) return;
        sourceCache = original.machineImgCache;
        if (sourceCache instanceof org.newdawn.slick.Image image) {
            machineImgCache = image.getScaledCopy((int) Math.round(image.getWidth() * factor),
                    (int) Math.round(image.getHeight() * factor));
            machineWCache = ((org.newdawn.slick.Image) machineImgCache).getWidth();
            machineHCache = ((org.newdawn.slick.Image) machineImgCache).getHeight();
        } else {
            machineImgCache = null;
            machineWCache = machineHCache = 0;
        }
    }
}
