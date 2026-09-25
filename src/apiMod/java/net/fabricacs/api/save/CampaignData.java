/*
 * CampaignData.java — 按 MOD ID 访问单个 WorldMap 的共享战役数据；不发送网络消息。
 * 在游戏模拟线程显式写入/迁移，框架序列化只读取已提交快照，不执行 MOD 回调。
 */
package net.fabricacs.api.save;

import net.fabricacs.api.impl.CampaignDataAccess;
import net.fabricacs.api.impl.CampaignDataStore;
import org.json.JSONObject;

import java.util.Optional;

public final class CampaignData {
    private final CampaignDataStore store;
    private final String modId;

    public CampaignData(Object worldMap, String modId) {
        CampaignDataStore.validateModId(modId);
        if (!(worldMap instanceof CampaignDataAccess access)) {
            throw new IllegalArgumentException("Expected an Acbric-enabled WorldMap; pass campaignWorld.map after game initialization");
        }
        this.store = access.acbric$campaignDataStore();
        this.modId = modId;
    }

    public String modId() { return modId; }

    public Optional<CampaignDataSnapshot> read() { return store.read(modId); }

    /** 替换本 MOD 的数据；不允许无意降级到比现有数据更旧的格式。 */
    public void write(int dataVersion, JSONObject data) { store.write(modId, dataVersion, data); }

    public boolean remove() { return store.remove(modId); }

    /** 缺少数据或已是目标版本时不执行；失败不提交新值，亦不写磁盘。 */
    public boolean migrate(int targetVersion, Migration migration) { return store.migrate(modId, targetVersion, migration); }

    @FunctionalInterface
    public interface Migration {
        JSONObject migrate(int previousVersion, JSONObject data) throws Exception;
    }
}
