/*
 * CampaignLifecycleRuntimeRegression.java — 真实 Mixin 下验证创建/读取入口，及恢复成功条件和退出。
 * 地图使用真实存档构造器；无 GUI 夹具的客户端/界面使用 Unsafe，不能替代实际双机恢复。
 */
package net.fabricacs.regression;

import com.zarkonnen.airships.*;
import net.fabricacs.api.event.AirshipsCampaignEvents;
import net.fabricacs.api.impl.CampaignLifecycleHooks;
import net.fabricacs.api.impl.CampaignSession;
import net.fabricacs.api.save.CampaignData;
import org.json.JSONObject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class CampaignLifecycleRuntimeRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
        System.out.println("PASS transformed lifecycle: " + label);
    }
    private static <T> T blank(Class<T> type) throws Exception {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        Field f = u.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return type.cast(u.getMethod("allocateInstance", Class.class).invoke(f.get(null), type));
    }
    public static void run(WorldMap map) throws Exception {
        checks = 0;
        List<Object> created = new ArrayList<>(), loaded = new ArrayList<>(), restored = new ArrayList<>(), exited = new ArrayList<>();
        var c = AirshipsCampaignEvents.CREATED.registerWithHandle(world -> {
            CampaignWorld w = (CampaignWorld) world;
            created.add(w);
            new CampaignData(w.map, "lifecycle_test").write(1, new JSONObject().put("ready", 42));
        });
        var l = AirshipsCampaignEvents.LOADED.registerWithHandle((world, multiplayer) -> {
            loaded.add(world);
            check(multiplayer && new CampaignData(((CampaignWorld) world).map, "lifecycle_test").read().isPresent(), "load callback sees restored extension and load mode");
        });
        var r = AirshipsCampaignEvents.RESTORED.registerWithHandle((previous, current) -> { restored.add(previous); restored.add(current); });
        var e = AirshipsCampaignEvents.EXITED.registerWithHandle(exited::add);
        try {
            CampaignWorld original = new CampaignWorld(map, null, null);
            original.playerEmpireIndex = -1;
            check(created.isEmpty() && loaded.isEmpty(), "wrapping a map for recovery does not fire creation/load");
            original.setupPlayer();
            check(created.isEmpty(), "setupPlayer outside generation does not signal creation");
            map.campaignWorldDuringGen = original;
            original.setupPlayer(); original.setupPlayer();
            check(created.equals(List.of(original)), "generation ready hook fires once despite repeated setupPlayer");
            map.campaignWorldDuringGen = null;
            SavedStateOutPipe pipe = new SavedStateOutPipe();
            JSONObject json = original.toJSON(pipe);
            pipe.compileAndGetHash();
            JSONObject packed = pipe.toJSON();
            CampaignWorld deserialized = new CampaignWorld(json, null, true, new JSONObjectInPipe(packed));
            check(loaded.equals(List.of(deserialized)) && new CampaignData(deserialized.map, "lifecycle_test").read().orElseThrow().data().getInt("ready") == 42,
                    "ready callback data survives first subsequent save and load");
            try {
                new CampaignWorld(new JSONObject(json.toString()).put("version", -1), null, true, new JSONObjectInPipe(packed));
                throw new AssertionError("invalid version accepted");
            } catch (RuntimeException expected) { check(loaded.size() == 1, "failed constructor does not fire LOADED"); }

            AirshipGame game = blank(AirshipGame.class);
            for (Field f : AirshipGame.class.getDeclaredFields()) if (f.getType() == CampaignSession.class) {
                f.setAccessible(true); f.set(game, new CampaignSession());
            }
            StrategicScreen screen = blank(StrategicScreen.class); screen.g = game;
            Field screenWorld = StrategicScreen.class.getField("w"); screenWorld.setAccessible(true); screenWorld.set(screen, original);
            game.s = screen;
            CampaignLifecycleHooks.observe(game); CampaignLifecycleHooks.observe(game);
            check(exited.isEmpty(), "same strategic screen does not create an exit");
            game.s = null; CampaignLifecycleHooks.observe(game);
            check(exited.isEmpty(), "unrecognized temporary screen does not imply exit");

            WorldMap recovered = new WorldMap(json.getJSONObject("map"), null, new JSONObjectInPipe(packed));
            CampaignWorld replacement = new CampaignWorld(recovered, null, game);
            ResumeScreen resume = blank(ResumeScreen.class); resume.g = game; resume.oldWorld = original; resume.newWorld = replacement;
            game.s = resume;
            // 调用已注入目标类的 RETURN handler；原版网络阶段不在此探针中伪造。
            var handler = java.util.Arrays.stream(ResumeScreen.class.getDeclaredMethods()).filter(m -> m.getName().contains("acbric$restored")).findFirst().orElseThrow();
            handler.setAccessible(true);
            handler.invoke(resume, new CallbackInfo("input", false));
            CampaignLifecycleHooks.observe(game);
            check(restored.isEmpty() && exited.isEmpty(), "allocated replacement still waiting in resume emits nothing");
            StrategicScreen replacementScreen = blank(StrategicScreen.class); replacementScreen.g = game; screenWorld.set(replacementScreen, replacement);
            game.s = replacementScreen;
            handler.invoke(resume, new CallbackInfo("input", false));
            handler.invoke(resume, new CallbackInfo("input", false));
            CampaignLifecycleHooks.observe(game);
            check(restored.equals(List.of(original, replacement)) && exited.isEmpty(), "successful installed replacement fires RESTORED once without EXITED");
            check(created.size() == 1 && loaded.size() == 1, "restoration does not repeat creation or load callbacks");
            check(new CampaignData(replacement.map, "lifecycle_test").read().orElseThrow().data().getInt("ready") == 42, "rebound handle observes recovered data unchanged");
            game.startExit(); game.startExit();
            check(exited.equals(List.of(replacement)), "actual transformed startExit releases restored world once");
            demoIfPresent(replacement, packed);
            System.out.println("CAMPAIGN LIFECYCLE RUNTIME PASS: " + checks + " checks; no live network or full GUI assertion");
        } finally { c.unregister(); l.unregister(); r.unregister(); e.unregister(); }
    }

    private static void demoIfPresent(CampaignWorld world, JSONObject packed) throws Exception {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("acbric_campaign_demo")) return;
        Class<?> demo = Class.forName("net.fabricacs.demo.CampaignDemo");
        var action = demo.getMethod("action", CampaignWorld.class, String.class);
        CampaignData data = new CampaignData(world.map, "acbric_campaign_demo");
        int schema = data.read().orElseThrow().dataVersion();
        String field = schema == 1 ? "counter" : "value";
        int before = data.read().orElseThrow().data().getInt(field);
        action.invoke(null, world, "ADD");
        check(data.read().orElseThrow().data().getInt(field) == before + 1, "real demo action increments persisted data");
        action.invoke(null, world, "FAIL");
        check(data.read().orElseThrow().dataVersion() == schema && data.read().orElseThrow().data().getInt(field) == before + 1, "real demo failed migration preserves value and schema");
        world.mpClient = blank(Client.class);
        action.invoke(null, world, "ADD");
        check(data.read().orElseThrow().data().getInt(field) == before + 1, "real demo blocks local multiplayer writes");
        world.mpClient = null;
        if (schema >= 2) {
            data.remove(); data.write(1, new JSONObject().put("counter", 7));
            SavedStateOutPipe pipe = new SavedStateOutPipe(); JSONObject json = world.toJSON(pipe); pipe.compileAndGetHash();
            CampaignWorld migrated = new CampaignWorld(json, null, true, new JSONObjectInPipe(pipe.toJSON()));
            var result = new CampaignData(migrated.map, "acbric_campaign_demo").read().orElseThrow();
            check(result.dataVersion() == schema && result.data().getInt("value") == 7 && !result.data().has("counter"), "real demo migrates v1 save via LOADED exactly preserving value");
        }
        if (schema == 3) configDemo(world, action, demo);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("acbric_campaign_demo").orElseThrow().getMetadata().getVersion().getFriendlyString().equals("3.1.0")) {
            action.invoke(null, world, "SCOPE");
            check(demo.getMethod("status").invoke(null).toString().startsWith("SCOPE_PROBE_PASS"), "real demo validates scope cleanup and diagnostics without mutating save");
            check(demo.getMethod("scopeState").invoke(null).equals("application=4 campaign=1 probeListeners=1"), "repeated binding retains one campaign scope");
            AirshipsCampaignEvents.EXITED.invoker().onExited(world);
            check(demo.getMethod("scopeState").invoke(null).equals("application=4 campaign=0 probeListeners=0"), "campaign exit releases local listeners but retains application lifecycle observers");
        }

    }

    /** 真实入口生成配置，重新加载仅改变本地偏好，新建单机战役才采用新参数。 */
    private static void configDemo(CampaignWorld world, java.lang.reflect.Method action, Class<?> demo) throws Exception {
        var path = net.fabricacs.api.util.AirshipsPaths.configDir().resolve("acbric_campaign_demo/settings.json");
        JSONObject file = new JSONObject(java.nio.file.Files.readString(path));
        check(file.getInt("version") == 2 && file.getJSONObject("data").getInt("newCampaignIncrement") == 1, "real mod context initializes config defaults");
        // 前一项迁移测试已把 world 写回 schema 1，先通过显式事件恢复到 v3。
        AirshipsCampaignEvents.LOADED.invoker().onLoaded(world, true);
        CampaignData data = new CampaignData(world.map, "acbric_campaign_demo");
        file.getJSONObject("data").put("newCampaignIncrement", 4).put("showHud", false);
        java.nio.file.Files.writeString(path, file.toString()); action.invoke(null, world, "RELOAD");
        check(!(Boolean) demo.getMethod("showHud").invoke(null) && data.read().orElseThrow().data().getJSONObject("rules").getInt("increment") == 1,
                "config reload changes local HUD only and preserves campaign rules");
        int before = data.read().orElseThrow().data().getInt("value"); action.invoke(null, world, "ADD");
        check(data.read().orElseThrow().data().getInt("value") == before + 1, "existing campaign still uses frozen increment");
        java.nio.file.Files.writeString(path, "broken"); action.invoke(null, world, "RELOAD");
        check(!(Boolean) demo.getMethod("showHud").invoke(null) && java.nio.file.Files.readString(path).equals("broken"), "bad reload preserves active preferences and bad file");
        java.nio.file.Files.writeString(path, "{\"format\":1,\"version\":1,\"data\":{\"step\":3,\"showHud\":true}}");
        action.invoke(null, world, "RELOAD");
        check(new JSONObject(java.nio.file.Files.readString(path)).getJSONObject("data").getInt("newCampaignIncrement") == 3
                && new JSONObject(java.nio.file.Files.readString(path.resolveSibling("settings.json.bak"))).getInt("version") == 1, "real demo migrates config with old version backup");
        data.remove(); AirshipsCampaignEvents.CREATED.invoker().onCreated(world);
        check(data.read().orElseThrow().data().getJSONObject("rules").getInt("increment") == 3, "new singleplayer campaign freezes current config");
        data.remove(); world.mpClient = blank(Client.class); AirshipsCampaignEvents.CREATED.invoker().onCreated(world); world.mpClient = null;
        check(data.read().orElseThrow().data().getJSONObject("rules").getInt("increment") == 1, "new multiplayer campaign ignores peer-local gameplay settings");
        data.remove(); data.write(2, new JSONObject().put("value", 8)); AirshipsCampaignEvents.LOADED.invoker().onLoaded(world, true);
        check(data.read().orElseThrow().dataVersion() == 3 && data.read().orElseThrow().data().getInt("value") == 8
                && data.read().orElseThrow().data().getJSONObject("rules").getInt("increment") == 1, "v2 campaign migration uses deterministic rules instead of local config");
    }
}
