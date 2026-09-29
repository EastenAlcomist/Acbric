/*
 * CampaignDataRuntimeRegression.java — 仅由真实 Fabric 探针调用，验证变换后的地图存档与同步路径。
 * 构造空白微型地图；Unsafe 只用于提供最小静态数据条目，地图本身走真实构造方法。
 */
package net.fabricacs.regression;

import com.zarkonnen.airships.*;
import net.fabricacs.api.impl.CampaignDataHooks;
import net.fabricacs.api.save.CampaignData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public final class CampaignDataRuntimeRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("PASS transformed campaign: " + label);
    }
    private static void entry(Class<? extends Loadable> type, String name) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object unsafe = field.get(null);
        Object value = unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        Field nameField = Loadable.class.getField("name");
        nameField.setAccessible(true); nameField.set(value, name);
        Loadable.map.computeIfAbsent(type, key -> new HashMap<>()).put(name, value);
    }
    private static JSONObject grid(String text) { return new JSONObject().put("yl", 2).put("xl", 2).put("data", text); }
    private static JSONObject legacyMap() {
        return new JSONObject().put("worldID", "campaign-probe").put("lang", "en").put("empires", new JSONArray())
                .put("terrainFeatures", new JSONArray()).put("water", grid("1111")).put("roads2", grid("0000"))
                .put("connections", grid("0000")).put("cityOwnership", grid("-1 -1 -1 -1 "))
                .put("height", new JSONArray().put(0).put(0).put(0).put(0));
    }
    private static CampaignData data(WorldMap map, String id) { return new CampaignData(map, id); }

    public static void run(Path root) throws Exception {
        checks = 0;
        Files.createDirectories(root);
        var oldMap = Loadable.map;
        var oldAll = Loadable.alls;
        Loadable.map = new HashMap<>();
        Loadable.alls = null;
        try {
            entry(DifficultyLevel.class, "NORMAL"); entry(MapSize.class, "SMALLISH");
            entry(MonsterSetting.class, "DEFAULT"); entry(SeaLevelSetting.class, "MIXED");
            entry(FrequencySetting.class, "DEFAULT"); entry(TechSpeedSetting.class, "NORMAL");
            entry(EraModifier.class, "NO_BONUS"); entry(StrategicEra.class, "INITIAL");
            WorldMap map = new WorldMap(legacyMap(), null, null);
            check(data(map, "example_mod").read().isEmpty(), "legacy constructor creates empty isolated data store");
            data(map, "example_mod").write(1, new JSONObject().put("heat", 17).put("unset", JSONObject.NULL)
                    .put("long", Long.MAX_VALUE).put("fraction", 0.125));
            data(map, "absent_mod").write(8, new JSONObject().put("untouched", true));

            IODirectory disk = new IODirectory(root.resolve("save").toFile(), "campaign-probe");
            JSONObject encoded = map.toJSON(disk);
            disk.registerWithoutVersion(id -> encoded, "map"); disk.write();
            IODirectory opened = new IODirectory(root.resolve("save").toFile(), null);
            WorldMap loaded = new WorldMap(opened.read("map"), null, opened);
            check(data(loaded, "example_mod").read().orElseThrow().data().getInt("heat") == 17
                    && data(loaded, "example_mod").read().orElseThrow().data().isNull("unset")
                    && data(loaded, "example_mod").read().orElseThrow().data().getLong("long") == Long.MAX_VALUE,
                    "actual binary disk write and WorldMap reload preserve custom JSON");
            check(data(loaded, "absent_mod").read().orElseThrow().dataVersion() == 8, "data from an uninstalled MOD survives reload");

            // 使用原版 StoredState 构造器，验证 age 缓存不会屏蔽暂停期间扩展数据的变化。
            CampaignWorld campaign = new CampaignWorld(map, null, null);
            Class<?> stateType = Class.forName("com.zarkonnen.airships.CampaignWorld$StoredState");
            var constructor = stateType.getDeclaredConstructor(CampaignWorld.class, int.class);
            constructor.setAccessible(true);
            Field hashField = stateType.getField("hash"), stateField = stateType.getField("state");
            hashField.setAccessible(true); stateField.setAccessible(true);
            Object first = constructor.newInstance(campaign, 1);
            int firstHash = hashField.getInt(first);
            JSONObject firstState = ((SavedStateOutPipe) stateField.get(first)).toJSON();
            data(map, "example_mod").write(1, new JSONObject().put("heat", 18));
            Object second = constructor.newInstance(campaign, 1);
            check(hashField.getInt(second) != firstHash && map.age == 0, "real StoredState hash changes while map age is unchanged");
            JSONObject state = ((SavedStateOutPipe) stateField.get(second)).toJSON();
            WorldMap resumed = new WorldMap(state.getJSONObject("map"), null, new JSONObjectInPipe(state));
            check(data(resumed, "example_mod").read().orElseThrow().data().getInt("heat") == 18, "resync-style map reconstruction restores latest extension block");
            WorldMap previous = new WorldMap(firstState.getJSONObject("map"), null, new JSONObjectInPipe(firstState));
            check(data(previous, "example_mod").read().orElseThrow().data().getInt("heat") == 17, "old StoredState remains an independent snapshot");
            Object unchanged = constructor.newInstance(campaign, 1);
            check(hashField.getInt(unchanged) == hashField.getInt(second), "unchanged maps produce stable state hash");

            // 普通战役序列化亦必须把同一扩展块登记到管线。
            SavedStateOutPipe campaignPipe = new SavedStateOutPipe();
            JSONObject worldJSON = campaign.toJSON(campaignPipe);
            campaignPipe.compileAndGetHash();
            CampaignWorld worldReload = new CampaignWorld(worldJSON, null, true, new JSONObjectInPipe(campaignPipe.toJSON()));
            check(data(worldReload.map, "example_mod").read().orElseThrow().data().getInt("heat") == 18, "CampaignWorld serializer and reader include extension data");

            JSONObject corrupt = new JSONObject(state.toString());
            corrupt.getJSONObject(CampaignDataHooks.CHUNK).put("format", 99);
            try {
                new WorldMap(corrupt.getJSONObject("map"), null, new JSONObjectInPipe(corrupt));
                throw new AssertionError("Unsupported extension accepted");
            } catch (IOException expected) { check(expected.getMessage().contains("Acbric campaign data"), "unsupported extension aborts actual load with diagnostic"); }
            check(data(map, "example_mod").read().orElseThrow().data().getInt("heat") == 18, "failed load does not mutate existing campaign");
            System.out.println("CAMPAIGN RUNTIME PASS: " + checks + " checks; no real player save or network connection used");
            CampaignLifecycleRuntimeRegression.run(map);
        } finally {
            Loadable.map = oldMap;
            Loadable.alls = oldAll;
        }
    }
}
