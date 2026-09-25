/*
 * CampaignDataHooks.java — 将扩展块接入游戏原有 InPipe/OutPipe 管线，覆盖磁盘及状态恢复。
 * 独立 registerWithoutVersion 避免地图 age 不变时复用旧哈希；不代表网络广播。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.InPipe;
import com.zarkonnen.airships.OutPipe;
import org.json.JSONObject;

import java.io.IOException;

public final class CampaignDataHooks {
    public static final String KEY = "acbricCampaignData";
    public static final String CHUNK = "acbric_campaign_data";

    private CampaignDataHooks() {}

    public static void write(CampaignDataStore store, JSONObject map, OutPipe output) {
        if (map.has(KEY)) throw new IllegalStateException("Reserved map key already exists: " + KEY);
        // 原版二进制写入/哈希不支持 JSONObject.NULL，用规范 JSON 文本封装完整数据。
        String snapshot = store.snapshot().toString();
        // 快照属于本次序列化，之后修改数据不会改变已登记的写入内容。
        output.registerWithoutVersion(id -> new JSONObject().put("format", 1).put("payload", snapshot), CHUNK);
        map.put(KEY, 1);
    }

    public static void read(CampaignDataStore store, JSONObject map, InPipe input) throws IOException {
        if (!map.has(KEY)) return; // 旧存档没有扩展块，保留空容器，不读不存在的文件。
        try {
            if (CampaignDataStore.version(map, KEY) != 1) throw new IllegalArgumentException("Unsupported campaign marker version");
            if (input == null) throw new IllegalArgumentException("Campaign extension requires an InPipe");
            JSONObject block = input.read(CHUNK);
            if (CampaignDataStore.version(block, "format") != 1) throw new IllegalArgumentException("Unsupported campaign block version");
            store.restore(new JSONObject(block.getString("payload")));
        } catch (IOException | RuntimeException e) {
            throw new IOException("Cannot restore Acbric campaign data; refusing to replace it with defaults", e);
        }
    }
}
