/*
 * CampaignDataStore.java — 随 WorldMap 存活的数据容器，保留所有 MOD 命名空间及未知数据。
 * 显式修改原子替换，迁移回调只能处理副本；存档和同步序列化均不执行回调。
 */
package net.fabricacs.api.impl;

import net.fabricacs.api.save.CampaignData;
import net.fabricacs.api.save.CampaignDataSnapshot;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Optional;

public final class CampaignDataStore {
    private JSONObject envelope = new JSONObject().put("format", 1).put("mods", new JSONObject());
    private boolean migrating;
    private long revision;
    /** 内部缓存失效代次；不是存档字段，也不是网络序列。 */
    public synchronized long revision() { return revision; }

    public static void validateModId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{1,63}")) throw new IllegalArgumentException("Invalid campaign mod ID: " + id);
    }

    private JSONObject mods() { return envelope.getJSONObject("mods"); }

    public synchronized Optional<CampaignDataSnapshot> read(String id) {
        validateModId(id);
        if (!mods().has(id)) return Optional.empty();
        JSONObject entry = mods().getJSONObject(id);
        return Optional.of(new CampaignDataSnapshot(version(entry, "version"), entry.getJSONObject("data")));
    }

    public synchronized void write(String id, int version, JSONObject data) {
        writable();
        validateModId(id);
        CampaignDataSnapshot snapshot = new CampaignDataSnapshot(version, data);
        if (mods().has(id) && version < version(mods().getJSONObject(id), "version")) {
            throw new IllegalStateException("Refusing to downgrade campaign data for " + id);
        }
        put(id, snapshot);
    }

    public synchronized boolean remove(String id) {
        writable();
        validateModId(id);
        boolean removed = mods().remove(id) != null;
        if (removed) revision++;
        return removed;
    }

    public synchronized boolean migrate(String id, int target, CampaignData.Migration migration) {
        writable();
        Objects.requireNonNull(migration, "migration");
        if (target < 0) throw new IllegalArgumentException("Negative target version");
        Optional<CampaignDataSnapshot> previous = read(id);
        if (previous.isEmpty()) return false;
        CampaignDataSnapshot old = previous.get();
        if (old.dataVersion() > target) throw new IllegalStateException("Campaign data for " + id + " is newer than supported version " + target);
        if (old.dataVersion() == target) return false;
        CampaignDataSnapshot next;
        migrating = true;
        try {
            next = new CampaignDataSnapshot(target, migration.migrate(old.dataVersion(), old.data()));
        } catch (Exception e) {
            throw new IllegalStateException("Campaign data migration failed for " + id + "; original data retained", e);
        } finally {
            migrating = false;
        }
        put(id, next);
        return true;
    }

    private void put(String id, CampaignDataSnapshot snapshot) {
        mods().put(id, new JSONObject().put("version", snapshot.dataVersion()).put("data", snapshot.data()));
        revision++;
    }

    private void writable() {
        if (migrating) throw new IllegalStateException("Migration must not modify the campaign store; return the new JSON instead");
    }

    /** 返回固定快照，不能把容器引用交给延迟写入器或调用方。 */
    public synchronized JSONObject snapshot() { return CampaignJson.copy(envelope, 72); }

    /** 完整验证后才替换；缺失 MOD 不会导致其数据被删除或执行迁移。 */
    public synchronized void restore(JSONObject input) {
        writable();
        JSONObject candidate = CampaignJson.copy(input, 72);
        if (version(candidate, "format") != 1) throw new IllegalArgumentException("Unsupported Acbric campaign data format");
        JSONObject entries = candidate.getJSONObject("mods");
        var ids = entries.keys();
        while (ids.hasNext()) {
            String id = (String) ids.next();
            validateModId(id);
            JSONObject entry = entries.getJSONObject(id);
            new CampaignDataSnapshot(version(entry, "version"), entry.getJSONObject("data"));
        }
        envelope = candidate;
        revision++;
    }

    public static int version(JSONObject object, String key) {
        Object number = object.get(key);
        if (!(number instanceof Integer || number instanceof Long)
                || ((Number) number).longValue() < 0 || ((Number) number).longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected non-negative integer " + key);
        }
        return ((Number) number).intValue();
    }
}
