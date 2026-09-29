/*
 * CampaignDataSnapshot.java — 战役 MOD 数据的独立快照，保存格式版本及经过验证的 JSON。
 * data() 每次返回副本；修改副本不会绕过显式写入接口改变游戏状态。
 */
package net.fabricacs.api.save;

import net.fabricacs.api.impl.CampaignJson;
import org.json.JSONObject;

public final class CampaignDataSnapshot {
    private final int dataVersion;
    private final String json;

    public CampaignDataSnapshot(int dataVersion, JSONObject data) {
        if (dataVersion < 0) throw new IllegalArgumentException("Negative campaign data version");
        this.dataVersion = dataVersion;
        this.json = CampaignJson.copy(data, 64).toString();
    }

    public int dataVersion() { return dataVersion; }

    public JSONObject data() { return new JSONObject(json); }
}
