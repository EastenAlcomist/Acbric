/*
 * CampaignSession.java — 每个客户端独立的活动战役引用，按对象身份去重并释放旧引用。
 * 先更新状态再通知，避免回调重入造成重复退出；不持有全局游戏引用。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.event.AirshipsCampaignEvents;
import java.util.Objects;

public final class CampaignSession {
    private Object active;

    public void observe(Object world) {
        Objects.requireNonNull(world);
        if (active == world) return;
        Object previous = active;
        active = world;
        if (previous != null) AirshipsCampaignEvents.EXITED.invoker().onExited(previous);
    }

    public void restored(Object previous, Object current) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(current);
        if (previous == current || active == current) return;
        active = current;
        AirshipsCampaignEvents.RESTORED.invoker().onRestored(previous, current);
    }

    public void exit() {
        Object previous = active;
        active = null;
        if (previous != null) AirshipsCampaignEvents.EXITED.invoker().onExited(previous);
    }
}
