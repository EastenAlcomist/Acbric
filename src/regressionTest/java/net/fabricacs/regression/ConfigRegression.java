/* ConfigRegression.java — 隔离磁盘上的配置校验、迁移、备份和写入冲突回归。 */
package net.fabricacs.regression;

import net.fabricacs.api.config.*;
import org.json.JSONObject;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.util.Arrays;

public final class ConfigRegression {
    private static int checks;
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); checks++; }
    @FunctionalInterface private interface Attempt { void run() throws Exception; }
    private static void rejects(Class<? extends Throwable> type, Attempt task) throws Exception {
        try { task.run(); } catch (Exception e) { check(type.isInstance(e), "Expected " + type + ", got " + e); return; }
        throw new AssertionError("Expected " + type);
    }
    private static void code(ConfigException.Code code, Attempt task) throws Exception {
        try { task.run(); } catch (ConfigException e) { check(e.code() == code, "Expected " + code + ", got " + e.code()); return; }
        throw new AssertionError("Expected " + code);
    }
    private static JSONObject defaults() { return new JSONObject("{\"enabled\":true,\"nested\":{\"step\":1}}"); }
    private static ModConfig config(Path root, String name) throws Exception {
        return new ModConfig(root, "test_mod", name, 2, defaults(), data -> {
            if (!(data.get("enabled") instanceof Boolean) || !(data.getJSONObject("nested").get("step") instanceof Integer)
                    || data.getJSONObject("nested").getInt("step") < 1) throw new IllegalArgumentException("Invalid settings");
            data.put("validatorMutation", true);
        });
    }
    private static String json(int version, String data) { return "{\"format\":1,\"version\":" + version + ",\"data\":" + data + ",\"extra\":42}"; }
    public static int run(Path root) throws Exception {
        checks = 0;
        ModConfig a = config(root, "settings");
        rejects(IllegalStateException.class, a::read);
        check(a.load().data().getBoolean("enabled") && !Files.exists(root), "Missing defaults do not write");
        JSONObject copy = a.read().data(); copy.put("enabled", false);
        check(a.read().data().getBoolean("enabled") && !a.read().data().has("validatorMutation"), "Snapshots and validator isolated");
        a.save(); byte[] first = Files.readAllBytes(a.path());
        check(!Files.exists(a.backupPath()), "First save has no invented backup");
        ModConfig stale = config(root, "settings"); stale.load();
        a.update(new JSONObject("{\"enabled\":false,\"custom\":42}"));
        check(!a.load().data().getBoolean("enabled") && a.read().data().getJSONObject("nested").getInt("step") == 1, "Load retains pending data and merges defaults");
        a.save(); check(Arrays.equals(first, Files.readAllBytes(a.backupPath())), "Backup exact previous bytes");
        code(ConfigException.Code.CONFLICT, stale::save);
        a.save(); check(Arrays.equals(first, Files.readAllBytes(a.backupPath())), "Unchanged save preserves backup");
        code(ConfigException.Code.VALIDATION_FAILED, () -> a.update(new JSONObject("{\"enabled\":null}")));
        check(!a.read().data().getBoolean("enabled"), "Failed update retains current");
        Files.writeString(a.path(), json(2, "{\"nested\":{\"custom\":7},\"unknown\":8}"));
        a.reload(); a.save();
        check(a.read().data().getJSONObject("nested").getInt("step") == 1 && a.read().data().getInt("unknown") == 8
                && new JSONObject(Files.readString(a.path())).getInt("extra") == 42, "Recursive missing defaults and unknown fields preserved");
        String[] invalid = {"{} trailing", "{'format':1}", "{\"format\":1,}", json(2,"{\"enabled\":true,\"enabled\":false}"),
                json(2,"{\"enabled\":true,\"\\u0065nabled\":false}"), json(2,"{\"x\":01}"), json(2,"{\"x\":1e999}"), "[]", json(2,"[]"), json(-1,"{}"),
                json(2, "{\"x\":" + "[".repeat(80) + "0" + "]".repeat(80) + "}")};
        for (String bad : invalid) {
            Files.writeString(a.path(), bad); code(ConfigException.Code.INVALID_FORMAT, a::reload);
            check(a.read().data().getInt("unknown") == 8 && Files.readString(a.path()).equals(bad), "Invalid reload preserves memory and disk");
        }
        Files.write(a.path(), new byte[]{(byte) 0xc3, (byte) 0x28}); code(ConfigException.Code.INVALID_FORMAT, a::reload);
        Files.writeString(a.path(), json(2,"{\"enabled\":\"true\"}")); code(ConfigException.Code.VALIDATION_FAILED, a::reload);
        Files.writeString(a.path(), json(3,"{}")); code(ConfigException.Code.NEWER_VERSION, a::reload);
        code(ConfigException.Code.CONFLICT, a::save);
        Files.writeString(a.path(), json(1,"{\"old\":9}")); a.reload();
        check(!a.read().data().has("enabled"), "Old schema not default-merged");
        rejects(IllegalStateException.class, a::save);
        code(ConfigException.Code.MIGRATION_FAILED, () -> a.migrate((v,d) -> { d.put("old", 0); throw new IOException("expected"); }));
        code(ConfigException.Code.MIGRATION_FAILED, () -> a.migrate((v,d) -> { a.reload(); return d; }));
        code(ConfigException.Code.VALIDATION_FAILED, () -> a.migrate((v,d) -> d.put("enabled", "bad")));
        check(a.read().dataVersion() == 1 && a.read().data().getInt("old") == 9, "Failed migrations preserve old version and data");
        byte[] old = Files.readAllBytes(a.path());
        check(a.migrate((v,d) -> d.put("migrated", v)) && Arrays.equals(old, Files.readAllBytes(a.path())), "Migration is explicit and memory-only");
        a.save(); check(Arrays.equals(old, Files.readAllBytes(a.backupPath())), "Migration backup preserves old schema");
        check(!a.migrate((v,d) -> { throw new AssertionError(); }), "Current schema migration is no-op");
        a.update(new JSONObject().put("enabled", false)); a.reload(); check(a.read().data().getBoolean("enabled"), "Reload explicitly discards pending changes");
        Files.delete(a.path()); code(ConfigException.Code.CONFLICT, a::save); a.reload(); a.save();
        try (var channel = FileChannel.open(a.path().resolveSibling("settings.json.lock"), StandardOpenOption.WRITE); var lock = channel.lock()) {
            code(ConfigException.Code.CONFLICT, a::save);
        }
        byte[] intact = Files.readAllBytes(a.path()); Files.delete(a.backupPath()); Files.createDirectory(a.backupPath());
        a.update(new JSONObject().put("enabled", false)); rejects(IOException.class, a::save);
        check(Arrays.equals(intact, Files.readAllBytes(a.path())), "Backup failure preserves original file");
        Files.write(a.path(), new byte[1024*1024+1]); rejects(IOException.class, a::reload);
        for (String name : new String[]{"../escape", "con", "nul", "com1", "A", "a/b", "x.json"}) rejects(IllegalArgumentException.class, () -> config(root, name));
        rejects(IllegalArgumentException.class, () -> new ModConfig(root,"../escape","x",2,defaults(),d -> {}));
        ModConfig b = config(root,"bom"); Files.writeString(b.path(), "\ufeff" + json(2,"{\"text\":\"中文\",\"array\":[null,true,4]}"));
        check(b.load().data().getString("text").equals("中文"), "UTF8 BOM and JSON array accepted");
        ModConfig[] guarded = {null};
        guarded[0] = new ModConfig(root,"test_mod","guarded",2,defaults(),d -> { if (guarded[0] != null) guarded[0].save(); });
        code(ConfigException.Code.VALIDATION_FAILED, guarded[0]::load);
        System.out.println("CONFIG REGRESSION PASS: " + checks + " checks");
        return checks;
    }
}
