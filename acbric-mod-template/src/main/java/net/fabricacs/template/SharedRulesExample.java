/* SharedRulesExample.java — 显式接入共享规则的可选示例；默认模板不注册，不修改任何游戏伤害。 */
package net.fabricacs.template;

import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.rules.SharedRules;
import com.zarkonnen.airships.CampaignWorld;
import org.json.JSONObject;

public final class SharedRulesExample {
    private SharedRulesExample() {}
    /** 在带上下文的 acbric 入口调用一次；参数可来自已显式 load 的本地配置。 */
    public static SharedRules declare(AcbricModContext context, int damagePercent) {
        return context.sharedRules(1, new JSONObject().put("damagePercent", damagePercent), values -> {
            if (!(values.get("damagePercent") instanceof Integer value) || value < 1 || value > 10_000)
                throw new IllegalArgumentException("damagePercent must be an integer in 1..10000");
        });
    }
    /** MOD 的玩法代码应读取当前地图的固化值，不在运行/加载时直接读取本地配置。 */
    public static double multiplier(SharedRules rules, CampaignWorld world) {
        return rules.forCampaign(world.map).values().getInt("damagePercent") / 100.0;
    }
    /** 仅改变后续新战役候选；大厅会重查，已有战役不变，不写本地配置文件。 */
    public static void changeNextCampaign(SharedRules rules, int damagePercent) {
        rules.update(new JSONObject().put("damagePercent", damagePercent));
    }
}
