/*
 * CampaignLifecycleHooks.java — 识别原版活动界面与恢复成功条件。
 * 临时界面/恢复等待不等于退出；反序列化不等于激活；恢复后的共享数据不在此修改。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.*;

public final class CampaignLifecycleHooks {
    private CampaignLifecycleHooks() {}

    private static CampaignSession session(AirshipGame game) {
        return ((CampaignSessionAccess) game).acbric$campaignSession();
    }

    public static CampaignWorld world(Screen screen) {
        if (screen instanceof StrategicScreen strategic) return strategic.w;
        if (screen instanceof TechScreen tech) return tech.ss == null ? null : tech.ss.w;
        if (screen instanceof UniScreen uni) {
            if (uni.intent instanceof HasStrategicScreen owner) {
                StrategicScreen strategic = owner.getStrategicScreen();
                return strategic == null ? null : strategic.w;
            }
            return uni.cw;
        }
        return null;
    }

    public static void observe(AirshipGame game) {
        // 原版恢复会逐步建立新对象；直到 ResumeScreen 的成功出口才交接句柄。
        if (game.s instanceof ResumeScreen) return;
        CampaignWorld current = world(game.s);
        if (current != null) session(game).observe(current);
        else if (game.s instanceof MainMenu || game.s instanceof MetaLobbyScreen || game.s instanceof ExitScreen) {
            session(game).exit();
        }
    }

    public static void afterResume(ResumeScreen screen) {
        if (screen.oldWorld != null && screen.newWorld != null && world(screen.g.s) == screen.newWorld) {
            session(screen.g).restored(screen.oldWorld, screen.newWorld);
        }
    }

    public static void exit(AirshipGame game) { session(game).exit(); }
}
