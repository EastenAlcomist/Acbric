/* UiScale.java — 原生界面百分比缩放的进程级接口；只提交显示偏好，不写配置或同步玩法。 */
package net.fabricacs.api.ui;

public final class UiScale {
    public static final int MIN_PERCENT = 50;
    public static final int MAX_PERCENT = 200;
    private static volatile int requestedPercent;

    private UiScale() { }

    /** 请求下次绘制时生效；100 表示原版 SMALL，范围 50..200，支持每个整数。 */
    public static void setPercent(int percent) {
        if (percent < MIN_PERCENT || percent > MAX_PERCENT)
            throw new IllegalArgumentException("UI scale must be 50..200 percent");
        requestedPercent = percent;
    }

    /** 恢复玩家当前的原版自动/小/中/大设置；不改写原版偏好。 */
    public static void useVanilla() { requestedPercent = 0; }

    /** 最近请求的百分比；0 表示沿用原版，尚未绘制时也可读取。 */
    public static int percent() { return requestedPercent; }
}
