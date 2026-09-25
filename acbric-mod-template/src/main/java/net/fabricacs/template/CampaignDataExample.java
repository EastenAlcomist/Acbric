/*
 * CampaignDataExample.java — 战役数据初始化/迁移示例，不由模板入口自动执行。
 * 在取得实际 WorldMap 后由 MOD 的适当玩法钩子调用；联机要求各端同一步骤执行。
 */
package net.fabricacs.template;

import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.save.CampaignData;
import org.json.JSONObject;

public final class CampaignDataExample {
    private CampaignDataExample() {}

    public static CampaignData prepare(AcbricModContext context, Object worldMap) {
        CampaignData data = context.campaignData(worldMap);
        if (data.read().isEmpty()) {
            data.write(2, new JSONObject().put("visits", 0));
        } else {
            data.migrate(2, (previousVersion, json) -> {
                if (previousVersion != 1) throw new IllegalStateException("Unsupported campaign schema: " + previousVersion);
                json.put("visits", json.getInt("counter"));
                json.remove("counter");
                return json;
            });
        }
        return data;
    }
}
