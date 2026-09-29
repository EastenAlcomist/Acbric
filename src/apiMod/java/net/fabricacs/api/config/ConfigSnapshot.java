/* ConfigSnapshot.java — 配置的不可变版本快照；JSON 读取始终返回独立副本。 */
package net.fabricacs.api.config;

import net.fabricacs.api.impl.CampaignJson;
import org.json.JSONObject;

public final class ConfigSnapshot {
    private final int version;
    private final String json;
    public ConfigSnapshot(int dataVersion, JSONObject data) {
        if (dataVersion < 0) throw new IllegalArgumentException("Negative config version");
        version = dataVersion;
        json = CampaignJson.copy(data, 64).toString();
    }
    public int dataVersion() { return version; }
    public JSONObject data() { return new JSONObject(json); }
}
