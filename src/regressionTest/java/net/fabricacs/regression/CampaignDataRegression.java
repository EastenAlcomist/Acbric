/*
 * CampaignDataRegression.java — 战役扩展数据回归：隔离、副本、迁移、磁盘往返及暂停时的同步缓存。
 * 普通 run 不需要 Mixin；真实 WorldMap 构造/序列化另由隔离 Fabric 探针覆盖。
 */
package net.fabricacs.regression;

import com.zarkonnen.airships.IODirectory;
import com.zarkonnen.airships.JSONObjectInPipe;
import com.zarkonnen.airships.SavedStateOutPipe;
import net.fabricacs.api.impl.CampaignDataAccess;
import net.fabricacs.api.impl.CampaignDataHooks;
import net.fabricacs.api.impl.CampaignDataStore;
import net.fabricacs.api.save.CampaignData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public final class CampaignDataRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }
    @FunctionalInterface
    private interface Action { void run() throws Exception; }
    private static void rejects(Class<? extends Throwable> type, Action action, String label) throws Exception {
        try { action.run(); } catch (Throwable e) {
            if (!type.isInstance(e)) throw new AssertionError(label, e);
            check(true, label);
            return;
        }
        throw new AssertionError("Expected failure: " + label);
    }
    private static JSONObject data(int counter) { return new JSONObject().put("counter", counter); }
    private static CampaignData view(CampaignDataStore store, String id) {
        return new CampaignData((CampaignDataAccess) () -> store, id);
    }
    public static int run(Path root) throws Exception {
        checks = 0;
        Files.createDirectories(root);
        CampaignDataStore store = new CampaignDataStore();
        CampaignData one = view(store, "first_mod"), two = view(store, "second_mod");
        check(one.read().isEmpty() && !one.remove(), "absent campaign data is distinct from an empty saved object");
        rejects(IllegalArgumentException.class, () -> view(store, "../bad"), "reject invalid campaign namespace");
        rejects(IllegalArgumentException.class, () -> new CampaignData(new Object(), "first_mod"), "reject non-WorldMap API target");
        JSONObject original = data(7).put("nested", new JSONObject().put("value", 8));
        one.write(1, original);
        original.put("counter", 99).getJSONObject("nested").put("value", 99);
        JSONObject read = one.read().orElseThrow().data();
        check(read.getInt("counter") == 7 && read.getJSONObject("nested").getInt("value") == 8 && two.read().isEmpty(),
                "writes copy nested data and isolate MOD namespaces");
        read.put("counter", 101);
        check(one.read().orElseThrow().data().getInt("counter") == 7, "read snapshots cannot mutate live campaign data");
        check(view(new CampaignDataStore(), "first_mod").read().isEmpty(), "campaign instances do not share state");
        rejects(IllegalStateException.class, () -> one.write(0, data(1)), "older MOD cannot silently downgrade stored data");
        rejects(IllegalArgumentException.class, () -> one.write(-1, data(1)), "reject negative data version");
        JSONObject cyclic = new JSONObject(); cyclic.put("cycle", cyclic);
        rejects(IllegalArgumentException.class, () -> one.write(1, cyclic), "reject cyclic JSON before modifying data");
        rejects(IllegalArgumentException.class, () -> one.write(1, new JSONObject().put("object", new Object())), "reject arbitrary Java objects");
        JSONObject deep = new JSONObject(), cursor = deep;
        for (int i = 0; i < 66; i++) { JSONObject child = new JSONObject(); cursor.put("child", child); cursor = child; }
        rejects(IllegalArgumentException.class, () -> one.write(1, deep), "reject excessive JSON nesting");
        AtomicInteger migrations = new AtomicInteger();
        check(!one.migrate(1, (v, json) -> { migrations.incrementAndGet(); return json; })
                && !two.migrate(1, (v, json) -> { migrations.incrementAndGet(); return json; }) && migrations.get() == 0,
                "migration skips absent data and matching versions");
        rejects(IllegalStateException.class, () -> one.migrate(2, (v, json) -> { json.put("counter", -1); throw new IOException("expected"); }),
                "migration failure is reported");
        check(one.read().orElseThrow().dataVersion() == 1 && one.read().orElseThrow().data().getInt("counter") == 7,
                "failed migration preserves original version and data");
        rejects(IllegalStateException.class, () -> one.migrate(2, (v, json) -> { two.write(1, data(1)); return json; }),
                "migration callback cannot mutate another namespace in the same store");
        check(two.read().isEmpty(), "reentrant migration mutation does not partially commit");
        check(one.migrate(2, (v, json) -> { migrations.incrementAndGet(); return json.put("from", v); })
                && one.read().orElseThrow().dataVersion() == 2, "explicit migration commits validated result once");
        rejects(IllegalStateException.class, () -> one.migrate(1, (v, json) -> json), "migration rejects newer saved schema");
        two.write(9, new JSONObject().put("unknown", new JSONArray().put(JSONObject.NULL).put("保留")));
        JSONObject envelope = store.snapshot().put("futureMetadata", "keep");
        envelope.getJSONObject("mods").getJSONObject("second_mod").put("futureField", "keep");
        CampaignDataStore restored = new CampaignDataStore();
        restored.restore(envelope);
        view(restored, "first_mod").remove();
        check(restored.snapshot().getString("futureMetadata").equals("keep")
                && restored.snapshot().getJSONObject("mods").getJSONObject("second_mod").getString("futureField").equals("keep"),
                "unknown MOD data and metadata survive another MOD removing its own data");
        JSONObject before = restored.snapshot();
        rejects(RuntimeException.class, () -> restored.restore(new JSONObject().put("format", 2).put("mods", new JSONObject())),
                "unknown framework data format fails explicitly");
        rejects(RuntimeException.class, () -> restored.restore(new JSONObject().put("format", 1).put("mods", new JSONObject()
                .put("bad_mod", new JSONObject().put("version", "1").put("data", new JSONObject())))), "malformed namespace entry fails explicitly");
        check(restored.snapshot().toString().equals(before.toString()), "failed restore never partially replaces current data");
        CampaignDataHooks.read(new CampaignDataStore(), new JSONObject(), id -> { throw new AssertionError("old save must not read extension"); });
        check(true, "legacy save requires no extension file");
        rejects(IOException.class, () -> CampaignDataHooks.read(restored, new JSONObject().put(CampaignDataHooks.KEY, 1), id -> { throw new IOException("missing"); }),
                "declared but missing extension fails instead of defaulting");
        rejects(IOException.class, () -> CampaignDataHooks.read(restored, new JSONObject().put(CampaignDataHooks.KEY, 2), id -> new JSONObject()),
                "unsupported extension marker fails");

        // 原版同步缓存：map 版本相同仍须刷新独立扩展块，且序列化没有迁移回调。
        SavedStateOutPipe first = new SavedStateOutPipe();
        JSONObject map1 = new JSONObject().put("age", 0);
        CampaignDataHooks.write(store, map1, first);
        first.register(id -> map1, "map", 0);
        int hash1 = first.compileAndGetHash();
        String frozen = first.toJSON().toString();
        SavedStateOutPipe same = new SavedStateOutPipe(first);
        JSONObject map2 = new JSONObject().put("age", 0);
        CampaignDataHooks.write(store, map2, same); same.register(id -> map2, "map", 0);
        check(same.compileAndGetHash() == hash1, "unchanged data yields the same state hash despite new block revision");
        one.write(2, data(11));
        SavedStateOutPipe changed = new SavedStateOutPipe(same);
        JSONObject map3 = new JSONObject().put("age", 0);
        CampaignDataHooks.write(store, map3, changed); changed.register(id -> map3, "map", 0);
        check(changed.compileAndGetHash() != hash1 && first.toJSON().toString().equals(frozen),
                "paused map data changes affect checksum without mutating prior stored state");
        CampaignDataStore received = new CampaignDataStore();
        CampaignDataHooks.read(received, changed.toJSON().getJSONObject("map"), new JSONObjectInPipe(changed.toJSON()));
        check(view(received, "first_mod").read().orElseThrow().data().getInt("counter") == 11
                && view(received, "second_mod").read().orElseThrow().data().getJSONArray("unknown").isNull(0),
                "packed state restoration preserves custom fields and JSON null");
        check(migrations.get() == 1, "serialization and restoration never run migration callbacks");

        // 通过真实二进制存档管线验证 JSON null、数字与保存后再次读取，不使用玩家存档。
        Path file = root.resolve("campaign-save");
        IODirectory disk = new IODirectory(file.toFile(), "fixture");
        JSONObject diskMap = new JSONObject().put("age", 0);
        CampaignDataHooks.write(store, diskMap, disk);
        disk.registerWithoutVersion(id -> diskMap, "map");
        disk.write();
        IODirectory opened = new IODirectory(file.toFile(), null);
        CampaignDataStore diskStore = new CampaignDataStore();
        CampaignDataHooks.read(diskStore, opened.read("map"), opened);
        check(diskStore.snapshot().toString().equals(store.snapshot().toString()), "native IODirectory binary save round-trip preserves all namespaces");
        SavedStateOutPipe deferred = new SavedStateOutPipe();
        CampaignDataHooks.write(store, new JSONObject(), deferred);
        one.write(2, data(999));
        deferred.compileAndGetHash();
        CampaignDataStore delayed = new CampaignDataStore();
        CampaignDataHooks.read(delayed, new JSONObject().put(CampaignDataHooks.KEY, 1), new JSONObjectInPipe(deferred.toJSON()));
        check(view(delayed, "first_mod").read().orElseThrow().data().getInt("counter") == 11,
                "deferred native writer uses serialization-time snapshot");
        return checks;
    }
}
