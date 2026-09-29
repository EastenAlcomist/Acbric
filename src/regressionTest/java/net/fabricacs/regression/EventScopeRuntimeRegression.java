/* EventScopeRuntimeRegression.java — 在真实 Fabric/Mixin 下验证上下文归属、异常传播和会话报告。 */
package net.fabricacs.regression;

import net.fabricacs.api.AcbricModContext;
import net.fabricacs.api.event.*;
import net.fabricacs.api.util.AirshipsPaths;
import net.fabricmc.loader.api.FabricLoader;
import com.zarkonnen.airships.RenameShipPanel;
import java.nio.file.Files;
import org.json.JSONObject;

public final class EventScopeRuntimeRegression {
    private static int checks;
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    public static void run() throws Exception {
        checks = 0;
        var context = new AcbricModContext(FabricLoader.getInstance().getModContainer("acbric_api").orElseThrow());
        var event = AirshipsCombatUiEvents.RENAME_SHIP_AFTER_TICK;
        int before = event.listenerCount(); int[] later = {0};
        RuntimeException original = new IllegalStateException("Intentional transformed event diagnostic");
        try (EventScope scope = context.eventScope("runtime-probe")) {
            scope.register(event, panel -> { throw original; }); scope.register(event, panel -> later[0]++);
            try { new RenameShipPanel().tick(null, 37, null); throw new AssertionError("Expected failure"); }
            catch (RuntimeException caught) { check(caught == original, "real transformed callback preserves original exception"); }
            check(later[0] == 0 && scope.size() == 2, "later callback blocked; subscriptions not disabled on failure");
        }
        new RenameShipPanel().tick(null, 37, null);
        check(event.listenerCount() == before && later[0] == 0, "closed scope leaves real event usable");
        var path = AirshipsPaths.gameDir().resolve("logs/acbric").resolve(System.getProperty("acbric.internal.diagnostics.session")).resolve("runtime-events.jsonl");
        JSONObject row = Files.readAllLines(path).stream().map(JSONObject::new).filter(r -> r.getString("scope").equals("runtime-probe")).findFirst().orElseThrow();
        check(row.getString("modId").equals("acbric_api") && row.getString("event").equals("AirshipsCombatUiEvents.RENAME_SHIP_AFTER_TICK"), "actual context and event attribution persisted in startup session");
        check(row.getString("stackTrace").contains("EventScopeRuntimeRegression") && row.getString("type").equals(IllegalStateException.class.getName()), "original failure stack recorded");
        System.out.println("EVENT SCOPE RUNTIME PASS: " + checks + " checks");
    }
}
