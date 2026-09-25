/* RuleSaveRegression.java — 隔离原生二进制目录存档：预检查、显式接纳、迁移、并发失效与输出保护。 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.IODirectory;
import net.fabricacs.api.rules.*;
import net.fabricacs.api.rules.CampaignRuleSaves.*;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class RuleSaveRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++; System.out.println("PASS rule save: " + label);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String label) throws Exception {
        try { action.run(); } catch (IOException | IllegalArgumentException | IllegalStateException expected) { check(true, label); return; }
        throw new AssertionError(label);
    }
    private static Path fixture(Path path, RuleSet rules) throws Exception {
        IODirectory disk = new IODirectory(path.toFile(), "test-world");
        JSONObject map = new JSONObject().put("worldID", "test-world");
        if (rules != null) {
            CampaignDataStore store = new CampaignDataStore();
            store.write("absent_data", 9, new JSONObject().put("number", Long.MAX_VALUE).put("untouched", true));
            store.write("acbric_api", 1, new JSONObject().put("otherFrameworkField", "keep").put("sharedRules", rules.text()));
            CampaignDataHooks.write(store, map, disk);
        }
        disk.registerWithoutVersion(id -> new JSONObject().put("map", map).put("version", 10620).put("fraction", 1.5), "world");
        disk.registerWithoutVersion(id -> new JSONObject().put("unknown", "preserved exactly"), "foreign");
        disk.write(); Files.write(path.resolve("thumbnail.png"), new byte[]{1,2,3});
        return path;
    }
    private static Map<String, JSONObject> adoption() {
        Map<String, JSONObject> result = new TreeMap<>();
        SharedRulesRegistry.declarations().forEach((id, h) -> result.put(id, h.current().values()));
        return result;
    }
    private static byte[] index(Path path) throws IOException { return Files.readAllBytes(path.resolve("index.gug")); }
    public static int run(Path root) throws Exception {
        checks = 0; Files.createDirectories(root);
        int[] migrations = {0};
        SharedRules handle = SharedRulesRegistry.register("upgrade_test", 2, new JSONObject().put("percent", 100), v -> {
            if (!(v.get("percent") instanceof Integer n) || n < 1 || n > 1000) throw new IllegalArgumentException();
        });
        handle.migration(1, (from, data) -> { migrations[0]++; return new JSONObject().put("percent", data.getInt("oldPercent")); });
        rejects(() -> handle.migration(1, (v,d) -> d), "duplicate migration rejected");
        rejects(() -> handle.migration(2, (v,d) -> d), "same-version migration rejected");
        rejects(() -> handle.migration(-1, (v,d) -> d), "negative migration version rejected");
        rejects(() -> new SharedRules("unregistered", 2, new JSONObject(), v -> {}).migration(1, (v,d) -> d), "unregistered handle cannot install global migration");

        Path old = fixture(root.resolve("legacy.json"), null); byte[] originalHeader = index(old);
        Inspection inspection = CampaignRuleSaves.inspect(old);
        check(!inspection.report().compatible(), "legacy save reports missing declarations");
        check(inspection.report().issues().stream().allMatch(i -> i.status() == Status.MISSING), "missing reports carry exact MOD identities");
        check(migrations[0] == 0, "inspection does not execute migration");
        check(inspection.report().summary().contains("upgrade_test") && inspection.report().summary().contains("-1 -> 2"), "diagnostic names MOD and both versions");
        rejects(() -> inspection.prepare(Map.of()), "adoption never substitutes local defaults");
        var extra = adoption(); extra.put("unregistered", new JSONObject());
        rejects(() -> inspection.prepare(extra), "extraneous adoption cannot overwrite undeclared rules");
        var badValues = adoption(); badValues.put("upgrade_test", new JSONObject().put("percent", -1));
        rejects(() -> inspection.prepare(badValues), "invalid explicit adoption rejected");
        Plan plan = inspection.prepare(adoption());
        check(plan.report().compatible() && !plan.changes().isEmpty(), "validated adoption creates reviewable plan");
        check(Arrays.equals(originalHeader, index(old)) && !Files.exists(root.resolve("adopted.json")), "preview does not write source or destination");
        Path adopted = plan.writeNew(root.resolve("adopted.json"));
        check(CampaignRuleSaves.inspect(adopted).report().compatible(), "new directory can be inspected with current declarations");
        check(Arrays.equals(originalHeader, index(old)), "source index unchanged after publish");
        check(Arrays.equals(Files.readAllBytes(old.resolve("test-world/1_foreign.gug")), Files.readAllBytes(adopted.resolve("test-world/1_foreign.gug"))), "foreign binary block kept byte for byte");
        check(Arrays.equals(Files.readAllBytes(old.resolve("thumbnail.png")), Files.readAllBytes(adopted.resolve("thumbnail.png"))), "ancillary files preserved");
        IODirectory loaded = new IODirectory(adopted.toFile(), null);
        check(loaded.read("world").getDouble("fraction") == 1.5 && loaded.read("world").getJSONObject("map").getInt(CampaignDataHooks.KEY) == 1, "native binary reader sees marker and unchanged game number");
        rejects(() -> plan.writeNew(adopted), "existing destination never overwritten");
        rejects(() -> plan.writeNew(old), "cannot replace source");
        rejects(() -> plan.writeNew(old.resolve("nested.json")), "cannot output inside source");
        rejects(() -> plan.writeNew(root.resolve("missing-parent/out.json")), "missing output parent fails without creating hierarchy");
        var inspectedCopy = CampaignRuleSaves.inspect(adopted);
        rejects(() -> inspectedCopy.prepare(adoption()), "cannot use adoption to override matching saved values");

        Map<String, SharedRuleSnapshot> entries = new TreeMap<>(SharedRulesRegistry.newCampaign().entries);
        entries.put("upgrade_test", new SharedRuleSnapshot(1, new JSONObject().put("oldPercent", 125)));
        entries.put("absent_mod", new SharedRuleSnapshot(7, new JSONObject().put("kept", true)));
        Path v1 = fixture(root.resolve("v1.json"), new RuleSet("NEW", entries, ""));
        Inspection inspectV1 = CampaignRuleSaves.inspect(v1);
        check(inspectV1.report().issues().stream().anyMatch(i -> i.modId().equals("upgrade_test") && i.status() == Status.MIGRATABLE), "registered direct upgrade reported");
        check(migrations[0] == 0, "inspect of migratable save remains read-only");
        Plan upgrade = inspectV1.prepare(Map.of());
        check(migrations[0] == 1 && upgrade.changes().size() == 1, "only explicit preview runs migration once");
        check(upgrade.changes().get(0).before().version() == 1 && upgrade.changes().get(0).after().values().getInt("percent") == 125, "before and after exposed as immutable snapshots");
        upgrade.changes().get(0).after().values().put("percent", 999);
        Path upgraded = upgrade.writeNew(root.resolve("v2.json"));
        check(migrations[0] == 1, "publishing never reruns MOD callbacks");
        var store = SavedRuleFiles.read(upgraded).store();
        check(SharedRulesRegistry.stored(store).entries.get("upgrade_test").values().getInt("percent") == 125, "changing preview copy cannot change published data");
        check(SharedRulesRegistry.stored(store).entries.get("absent_mod").version() == 7, "absent MOD rules retained");
        check(store.read("absent_data").orElseThrow().data().getLong("number") == Long.MAX_VALUE, "uninstalled MOD namespace retained");
        check(store.read("acbric_api").orElseThrow().data().getString("otherFrameworkField").equals("keep"), "other framework fields retained");
        var sourcePipe = new IODirectory(v1.toFile(), null);
        rejects(() -> SharedRulesRegistry.preflight(sourcePipe.read("world"), sourcePipe), "ordinary load preflight blocks migration instead of executing it");
        check(migrations[0] == 1, "load preflight runs no migration");
        SharedRulesRegistry.preflight(loaded.read("world"), loaded);
        check(true, "adopted save passes preflight before world construction");

        entries.put("upgrade_test", new SharedRuleSnapshot(3, new JSONObject().put("percent", 100)));
        Inspection newer = CampaignRuleSaves.inspect(fixture(root.resolve("newer.json"), new RuleSet("SAVED", entries, "")));
        check(newer.report().issues().stream().anyMatch(i -> i.status() == Status.UNSUPPORTED_VERSION), "newer schema is not silently downgraded");
        rejects(() -> newer.prepare(Map.of()), "newer schema conversion blocked");
        entries.put("upgrade_test", new SharedRuleSnapshot(0, new JSONObject()));
        Inspection unsupported = CampaignRuleSaves.inspect(fixture(root.resolve("unsupported.json"), new RuleSet("SAVED", entries, "")));
        rejects(() -> unsupported.prepare(Map.of()), "missing migration path blocks conversion");
        entries.put("upgrade_test", new SharedRuleSnapshot(2, new JSONObject().put("percent", -1)));
        Inspection invalid = CampaignRuleSaves.inspect(fixture(root.resolve("invalid.json"), new RuleSet("SAVED", entries, "")));
        check(invalid.report().issues().stream().anyMatch(i -> i.status() == Status.INVALID), "invalid current schema reports distinct status");
        rejects(() -> invalid.prepare(Map.of()), "invalid current values cannot be overwritten with local defaults");
        entries.put("upgrade_test", new SharedRuleSnapshot(1, new JSONObject().put("oldPercent", -1)));
        Path failed = fixture(root.resolve("failed.json"), new RuleSet("SAVED", entries, ""));
        byte[] failedWorld = Files.readAllBytes(failed.resolve("test-world/1_world.gug"));
        rejects(() -> CampaignRuleSaves.inspect(failed).prepare(Map.of()), "invalid migration output cannot produce plan");
        check(Arrays.equals(failedWorld, Files.readAllBytes(failed.resolve("test-world/1_world.gug"))), "failed migration leaves source bytes untouched");
        handle.migration(0, (v,d) -> { d.put("changed", true); throw new Exception("expected failure"); });
        Inspection throwing = CampaignRuleSaves.inspect(root.resolve("unsupported.json"));
        rejects(() -> throwing.prepare(Map.of()), "throwing callback cannot produce plan");

        Path changing = fixture(root.resolve("changing.json"), null);
        Plan staleSource = CampaignRuleSaves.inspect(changing).prepare(adoption());
        Files.write(changing.resolve("thumbnail.png"), new byte[]{4,5,6});
        rejects(() -> staleSource.writeNew(root.resolve("stale.json")), "source changed after preview blocks publish");
        check(!Files.exists(root.resolve("stale.json")), "source conflict creates no final directory");
        Plan staleRegistry = CampaignRuleSaves.inspect(old).prepare(adoption());
        handle.update(new JSONObject().put("percent", 101));
        rejects(() -> staleRegistry.writeNew(root.resolve("stale-registry.json")), "candidate changes invalidate old preview");
        rejects(() -> inspection.prepare(adoption()), "stale inspection cannot invoke migrations");

        SavedRuleFiles stagingSource = SavedRuleFiles.read(old);
        rejects(() -> stagingSource.publish(stagingSource, root.resolve("guard-failed.json"), () -> { throw new IOException("guard"); }), "publish guard failure does not expose partial output");
        check(!Files.exists(root.resolve("guard-failed.json")), "failed publication target absent");
        try (var stream = Files.list(root)) { check(stream.noneMatch(f -> f.getFileName().toString().startsWith(".acbric-rules-")), "failed publication removes only its staging directory"); }
        Path packed = root.resolve("packed.json"); Files.writeString(packed, "{}");
        rejects(() -> CampaignRuleSaves.inspect(packed), "single-file formats explicitly unsupported");
        Path corrupt = fixture(root.resolve("corrupt.json"), null); Files.write(corrupt.resolve("index.gug"), new byte[]{1,2,3});
        rejects(() -> CampaignRuleSaves.inspect(corrupt), "corrupt primary index does not silently recover or modify source");
        Path missingChunk = fixture(root.resolve("missing-chunk.json"), null); Files.delete(missingChunk.resolve("test-world/1_foreign.gug"));
        rejects(() -> CampaignRuleSaves.inspect(missingChunk), "missing unrelated chunk blocks conversion of incomplete save");
        Path escape = fixture(root.resolve("escape.json"), null);
        try (var out = Files.newOutputStream(escape.resolve("index.gug"))) { new JSONObject().put("version",1).put("id","../outside").toStream(out); }
        rejects(() -> CampaignRuleSaves.inspect(escape), "unsafe native index path rejected");
        Path corruptRules = fixture(root.resolve("corrupt-rules.json"), new RuleSet("SAVED", entries, ""));
        IODirectory corruptPipe = new IODirectory(corruptRules.toFile(), null);
        JSONObject badBlock = corruptPipe.read(CampaignDataHooks.CHUNK).put("format", 99);
        try (var out = Files.newOutputStream(corruptRules.resolve("test-world/1_" + CampaignDataHooks.CHUNK + ".gug"))) { badBlock.toStream(out); }
        rejects(() -> CampaignRuleSaves.inspect(corruptRules), "unsupported extension fails without default replacement");
        Path wrongMap = fixture(root.resolve("wrong-map.json"), null);
        try (var out = Files.newOutputStream(wrongMap.resolve("test-world/1_world.gug"))) { new JSONObject().put("map", "wrong type").toStream(out); }
        rejects(() -> CampaignRuleSaves.inspect(wrongMap), "malformed map shape reports checked inspection failure");
        System.out.println("RULE SAVE REGRESSION PASS: " + checks + " checks");
        return checks;
    }
}
