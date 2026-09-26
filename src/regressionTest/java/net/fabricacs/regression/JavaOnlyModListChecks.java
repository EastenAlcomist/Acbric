/* JavaOnlyModListChecks.java — 无原版 MOD 的真实界面回归，覆盖刷新、空列表与禁用后重启。 */
package net.fabricacs.regression;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.Hooks;
import com.zarkonnen.catengine.util.*;
import net.fabricacs.api.impl.*;
import java.nio.file.*;
import java.util.*;

public final class JavaOnlyModListChecks {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError("JAVA_ONLY_LIST: " + label); checks++; }
    private static void draw(ModsScreen screen, MyDraw d, ScreenMode sm, Hooks hooks, Pt cursor, boolean visible) {
        int before = Integer.getInteger("acbric.test.listDraws", 0);
        screen.render(d, sm, hooks, cursor);
        check(Integer.getInteger("acbric.test.listDraws", 0) == before + (visible ? 1 : 0), "actual native list drawing, expected=" + visible);
    }
    public static void run(Path instance, MyDraw d, ScreenMode sm, Hooks hooks, Pt cursor) throws Exception {
        checks = 0;
        var field = AirshipGame.class.getDeclaredField("instance"); field.setAccessible(true);
        AirshipGame game = (AirshipGame) field.get(null);
        check(Mod.mods.stream().allMatch(FabricModListBridge::isSyntheticFabricMod), "fixture has zero native mods");
        check(Mod.getAvailableMods().isEmpty() && Mod.getEnabledMods().isEmpty(), "Java rows excluded from native resource loading");
        ModsScreen screen = new ModsScreen(game);
        check(Mod.getById("acbric_fabric:acbric_api") != null && Mod.getById("acbric_fabric:java_only_example") != null, "API and Java-only example rows exist");
        draw(screen, d, sm, hooks, cursor, true);
        int count = Mod.mods.size(); Mod.refreshMods();
        screen = new ModsScreen(game);
        check(Mod.mods.size() == count, "refresh/reopen neither duplicates nor drops Java rows");
        draw(screen, d, sm, hooks, cursor, true);
        var saved = new ArrayList<>(Mod.mods);
        try {
            Mod.mods.clear(); screen.selected = null;
            draw(screen, d, sm, hooks, cursor, false);
            var constructor = Mod.class.getDeclaredConstructor(java.io.File.class, boolean.class); constructor.setAccessible(true);
            Mod nativeMod = constructor.newInstance(instance.resolve("native-fixture").toFile(), true);
            nativeMod.id = "native_fixture"; nativeMod.name.put(Locale.ENGLISH, "Native fixture"); nativeMod.loadInfoFailed = true;
            Mod.mods.add(nativeMod);
            draw(screen, d, sm, hooks, cursor, false);
            Mod.mods.addAll(saved); screen.selected = saved.getFirst();
            draw(screen, d, sm, hooks, cursor, true);
            nativeMod.loadInfoFailed = false;
            draw(screen, d, sm, hooks, cursor, true);
        } finally { Mod.mods.clear(); Mod.mods.addAll(saved); }
        JavaModManager.refresh(); var manager = JavaModManager.current();
        boolean second = Files.exists(instance.resolve("java-only-first-pass"));
        check(manager != null && manager.entry("java_only_example").loaded() != second, "example loaded first run, disabled after restart");
        if (!second) {
            manager.toggle("java_only_example");
            check(!manager.enabledNext("java_only_example"), "disable persisted for next launch");
            Files.writeString(instance.resolve("java-only-first-pass"), "first pass");
        } else {
            check(Mod.getById("acbric_fabric:java_only_example") != null, "disabled Java MOD remains listed without native mods");
        }
        check(Mod.getAvailableMods().isEmpty() && Mod.getEnabledMods().isEmpty(), "rendering did not change native availability");
        Files.writeString(instance.resolve("java-only-result-" + (second ? "restart" : "first") + ".txt"), "PASS: " + checks + " checks; actual ModsScreen render, zero native mods\n");
        System.out.println("JAVA ONLY MOD LIST PASS: " + checks + " checks");
    }
}
