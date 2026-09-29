/* ExternalCampaignProbe.java — 用真实生成、渲染和存读档验证外部实例；仅驱动操作，不替换世界内容。 */
package net.fabricacs.regression;

import com.zarkonnen.airships.*;
import net.fabricacs.api.*;
import net.fabricacs.api.event.AirshipsCampaignEvents;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

public final class ExternalCampaignProbe implements AcbricInitializer {
    private static AcbricModContext context;
    private static int menuFrames, setupFrames, worldFrames, created, loaded, exited, checks;
    private static boolean started, done;
    private static final String SAVE = "external-campaign.json";
    private static Path instance() { return Path.of(System.getProperty("acbric.external.instance")); }
    private static boolean creating() { return "create".equals(System.getProperty("acbric.test.campaign")); }
    private static boolean arc() { return Boolean.getBoolean("acbric.test.arc"); }
    private static AirshipGame game() throws ReflectiveOperationException {
        var field = AirshipGame.class.getDeclaredField("instance"); field.setAccessible(true);
        return (AirshipGame)field.get(null);
    }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++; System.out.println("PASS campaign: " + label);
    }
    @Override public void onInitializeAcbric() { throw new AssertionError("Context required"); }
    @Override public void onInitializeAcbric(AcbricModContext value) {
        context = value;
        AirshipsCampaignEvents.CREATED.register(object -> {
            created++;
            context.campaignData(((CampaignWorld)object).map).write(1, new JSONObject().put("marker", "external-campaign"));
        });
        AirshipsCampaignEvents.LOADED.register((object, multiplayer) -> loaded++);
        AirshipsCampaignEvents.EXITED.register(object -> exited++);
    }
    public static void menuRendered() {
        if (started || ++menuFrames < 30) return;
        started = true;
        try {
            check(org.lwjgl.opengl.Display.isCreated() && org.lwjgl.openal.AL.isCreated(), "real graphics and audio initialized");
            AirshipGame g = game();
            if (creating()) {
                GameSetupScreen setup = new GameSetupScreen(g);
                setup.mapSize = MapSize.ofName("VERY_SMALL");
                setup.seed = 89412;
                setup.seedF.setText("89412");
                setup.heroFrequency = Loadable.all(FrequencySetting.class).stream().filter(f -> f.frequencyMultiplier == 0).findFirst().orElseThrow();
                g.s = setup;
            } else {
                StrategicScreen empty = new StrategicScreen(g, null);
                new OpenGameMission(g, empty, true, StrategicScreen.savesList()).openFile(instance().resolve("userdata/saves/" + SAVE).toFile());
            }
        } catch (Throwable ex) { fail(ex); }
    }
    public static void setupRendered(GameSetupScreen setup) {
        if (!started || !creating() || ++setupFrames != 30) return;
        try {
            setup.empireNameField.setText("External Test");
            var method = GameSetupScreen.class.getDeclaredMethod("startGame");
            method.setAccessible(true); method.invoke(setup);
            check(game().s instanceof WorldGenScreen, "native start action entered world generation");
        } catch (Throwable ex) { fail(ex); }
    }
    public static void worldRendered(StrategicScreen screen) {
        if (!started || done || screen.w == null || ++worldFrames < 30) return;
        done = true;
        try {
            CampaignWorld w = screen.w;
            check(w.map.campaignWorldDuringGen == null && w.player != null, "native world generation and player setup completed");
            check(w.map.empires.size() == MapSize.ofName("VERY_SMALL").empires, "full native empire population exists");
            check(Arrays.stream(w.map.roads).anyMatch(row -> { for (boolean b : row) if (b) return true; return false; }), "native road network generated");
            check(w.player.getFleets().stream().anyMatch(f -> !f.actives.isEmpty()), "native starting fleet exists");
            check(w.player.cities.stream().anyMatch(c -> !c.getDefences().isEmpty()), "native starting defences exist");
            check(context.campaignData(w.map).read().orElseThrow().data().getString("marker").equals("external-campaign"), "framework campaign data survived native storage");
            int vanillaTowns = MapSize.ofName("VERY_SMALL").townsPerEmpire;
            for (Empire empire : w.map.empires) {
                long cities = empire.cities.stream().filter(c -> !c.isTown).count();
                int expectedCities = arc() && empire.playerControlled ? 3 : 1;
                int expectedTowns = arc() && empire.playerControlled ? 3 : vanillaTowns;
                check(cities == expectedCities && empire.cities.size() - cities == expectedTowns,
                        "native settlement counts for empire " + empire.id + " human=" + empire.playerControlled);
            }
            if (creating()) {
                check(created == 1 && loaded == 0, "creation event fired exactly once, no load event");
                if (arc()) check(w.player.getMoney() == 12345, "ARC cash applied after initial assets");
                w.player.setMoney(w.player.getMoney() - 123);
                context.campaignData(w.map).write(1, new JSONObject().put("marker", "external-campaign").put("savedCash", w.player.getMoney()));
                new SaveGameMission(game(), screen, StrategicScreen.savesList()).fileSelected(SAVE);
                check(Files.isDirectory(instance().resolve("userdata/saves/" + SAVE)), "native Save As created instance save directory");
                check(OpenGameMission.load(instance().resolve("userdata/saves/" + SAVE).toFile()).a != null, "native save can be read and passes rules preflight");
                Files.writeString(instance().resolve("campaign-expected.txt"), state(w));
            } else {
                check(created == 0 && loaded == 1, "restart loads once without repeating creation");
                check(w.player.getMoney() == context.campaignData(w.map).read().orElseThrow().data().getInt("savedCash"), "saved cash retained without regranting starting money");
                check(Files.readString(instance().resolve("campaign-expected.txt")).equals(state(w)), "world identity, roads, settlements, assets and balances retained across process restart");
            }
            // 让原生 ExitScreen 等待写入结束并关闭进程，不用测试代码强行成功退出。
            game().startExit();
            check(game().s instanceof ExitScreen && exited == 1, "native exit entered and campaign exit event fired exactly once");
            String report = new JSONObject().put("phase", creating() ? "create" : "load").put("checks", checks)
                    .put("worldFrames", worldFrames).put("created", created).put("loaded", loaded).put("exited", exited)
                    .put("playerCash", w.player.getMoney()).put("worldId", w.map.worldID).put("arc", arc()).toString(2);
            Files.writeString(instance().resolve("campaign-" + (creating() ? "create" : "load") + ".json"), report);
            System.out.println("EXTERNAL CAMPAIGN PASS: " + report);
        } catch (Throwable ex) { fail(ex); }
    }
    private static String state(CampaignWorld world) {
        List<String> values = new ArrayList<>();
        values.add("world=" + world.map.worldID + ";roads=" + Arrays.deepHashCode(world.map.roads) + ";water=" + Arrays.deepHashCode(world.map.water));
        for (Empire e : world.map.empires) {
            values.add("empire=" + e.id + ";human=" + e.playerControlled + ";money=" + e.getMoney());
            for (City c : e.cities) {
                values.add("city=" + c.id + ";owner=" + e.id + ";pos=" + c.x + "," + c.y + ";town=" + c.isTown);
                for (Airship a : c.getDefences()) values.add("defence=" + c.id + ";name=" + a.getName());
            }
            for (Fleet f : e.getFleets()) {
                values.add("fleet=" + f.id + ";owner=" + e.id);
                for (Airship a : f.actives) values.add("active=" + f.id + ";name=" + a.getName());
                for (Airship a : f.reserve) values.add("reserve=" + f.id + ";name=" + a.getName());
            }
        }
        Collections.sort(values); return String.join("\n", values);
    }
    private static void fail(Throwable ex) {
        ex.printStackTrace(); System.err.println("EXTERNAL CAMPAIGN FAILED: " + ex); System.exit(2);
    }
}
