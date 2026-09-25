/* CampaignLifecycleRegression.java — 验证会话对象身份、退出去重、恢复分流及异常重入边界。 */
package net.fabricacs.regression;

import net.fabricacs.api.event.AirshipsCampaignEvents;
import net.fabricacs.api.impl.CampaignSession;
import java.util.ArrayList;
import java.util.List;

public final class CampaignLifecycleRegression {
    private static int checks;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
        System.out.println("PASS campaign lifecycle: " + name);
    }
    public static int run() {
        checks = 0;
        CampaignSession session = new CampaignSession();
        Object first = new Object(), next = new Object();
        List<Object> exits = new ArrayList<>();
        List<Object> restores = new ArrayList<>();
        var exit = AirshipsCampaignEvents.EXITED.registerWithHandle(exits::add);
        var restore = AirshipsCampaignEvents.RESTORED.registerWithHandle((old, current) -> {
            restores.add(old); restores.add(current);
        });
        try {
            session.exit();
            check(exits.isEmpty(), "no phantom exit before an active campaign");
            session.observe(first); session.observe(first);
            check(exits.isEmpty(), "same world and sub-screen observations do not exit");
            session.restored(first, next); session.restored(first, next);
            check(restores.equals(List.of(first, next)), "restoration carries identities exactly once");
            check(exits.isEmpty(), "restore does not emit exit for replaced world");
            session.observe(next); session.exit(); session.exit();
            check(exits.equals(List.of(next)), "only current restored world exits once");
            session.observe(first); session.observe(next);
            check(exits.equals(List.of(next, first)), "unrelated active campaign switch exits old world");
            CampaignSession other = new CampaignSession();
            other.exit();
            check(exits.size() == 2, "clients have isolated sessions");
            var failure = AirshipsCampaignEvents.EXITED.registerWithHandle(world -> {
                session.exit(); throw new IllegalStateException("listener failure");
            });
            try {
                try { session.exit(); throw new AssertionError("swallowed callback"); }
                catch (IllegalStateException expected) { check(exits.size() == 3, "callback errors propagate without recursive duplicate exit"); }
                session.exit();
                check(exits.size() == 3, "failed exit callback does not retain active reference");
            } finally { failure.unregister(); }
            check(restores.size() == 2, "exit and active observation do not fabricate restores");
            return checks;
        } finally { exit.unregister(); restore.unregister(); }
    }
}
