/*
 * CodeManifest.java — 本地代码清单的不可变模型、严格解析与离线比较；暂不是公开 MOD API。
 * CODE_MATCH 只表示本模型覆盖的启动代码一致，不表示资源、配置、存档或联机行为一致。
 */
package net.fabricacs.api.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

record CodeManifest(String gameVersion, String gameFingerprint, String javaVersion, List<CodeManifest.Entry> mods) {
    static final int MAX_JSON_BYTES = 1024 * 1024;
    static final String SCOPE = "startup-loaded-code-v1";
    static final Set<String> INIT = Set.of("SUCCEEDED", "NO_ACBRIC_ENTRYPOINT", "FAILED", "NOT_REACHED", "PENDING", "RUNNING");
    static final Set<String> HASH = Set.of("OK", "UNREADABLE", "UNSUPPORTED_ORIGIN", "LIMIT_EXCEEDED", "CHANGED_DURING_READ");

    record Entry(String id, String version, String digest, String fingerprintStatus, String initialization) {
        Entry {
            if (id == null || !id.matches("[a-z][a-z0-9_-]{1,63}")) throw invalid("MOD ID");
            textValue(version);
            if (!HASH.contains(fingerprintStatus) || !INIT.contains(initialization)) throw invalid("status");
            if (fingerprintStatus.equals("OK") ? !isDigest(digest) : !"".equals(digest)) throw invalid("digest/status");
        }
        boolean verified() {
            return fingerprintStatus.equals("OK") && (initialization.equals("SUCCEEDED") || initialization.equals("NO_ACBRIC_ENTRYPOINT"));
        }
        JSONObject json() {
            return new JSONObject().put("id", id).put("version", version).put("digest", digest)
                    .put("fingerprintStatus", fingerprintStatus).put("initialization", initialization);
        }
    }

    CodeManifest {
        textValue(gameVersion); textValue(javaVersion);
        if (!"unavailable".equals(gameFingerprint) && !isDigest(gameFingerprint)) throw invalid("game fingerprint");
        if (mods == null || mods.size() > 1024) throw invalid("MOD count");
        TreeMap<String, Entry> sorted = new TreeMap<>();
        for (Entry entry : mods) if (sorted.put(entry.id(), entry) != null) throw invalid("duplicate MOD ID");
        mods = List.copyOf(sorted.values());
    }

    boolean verified() {
        return isDigest(gameFingerprint) && !gameVersion.equals("unknown") && !javaVersion.equals("unknown")
                && mods.stream().anyMatch(e -> e.id().equals("acbric_api"))
                && mods.stream().anyMatch(e -> e.id().equals("fabricloader")) && mods.stream().allMatch(Entry::verified);
    }

    JSONObject json() {
        JSONArray entries = new JSONArray();
        mods.forEach(entry -> entries.put(entry.json()));
        return new JSONObject().put("schema", 1).put("scope", SCOPE)
                .put("contentAlgorithm", ModContentFingerprint.ALGORITHM)
                .put("gameAlgorithm", "acbric-game-archives-v1")
                .put("gameVersion", gameVersion).put("gameFingerprint", gameFingerprint)
                .put("javaVersion", javaVersion).put("mods", entries);
    }

    static CodeManifest parse(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) throw invalid("size");
        return fromJson(ConfigJson.parseObject(text));
    }

    // 调用方先限制原始文本大小；先校验字段类型，避免对不可信浮点字段调用游戏序列化器。
    static CodeManifest fromJson(JSONObject object) {
        fields(object, Set.of("schema", "scope", "contentAlgorithm", "gameAlgorithm", "gameVersion", "gameFingerprint", "javaVersion", "mods"));
        if (!(object.get("schema") instanceof Integer version) || version != 1
                || !SCOPE.equals(string(object, "scope"))
                || !ModContentFingerprint.ALGORITHM.equals(string(object, "contentAlgorithm"))
                || !"acbric-game-archives-v1".equals(string(object, "gameAlgorithm"))) throw invalid("schema/scope/algorithm");
        JSONArray array = object.getJSONArray("mods");
        if (array.length() > 1024) throw invalid("MOD count");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.getJSONObject(i);
            fields(row, Set.of("id", "version", "digest", "fingerprintStatus", "initialization"));
            entries.add(new Entry(string(row, "id"), string(row, "version"), string(row, "digest"),
                    string(row, "fingerprintStatus"), string(row, "initialization")));
        }
        return new CodeManifest(string(object, "gameVersion"), string(object, "gameFingerprint"), string(object, "javaVersion"), entries);
    }

    record Difference(String code, String id, String left, String right) {
        JSONObject json() { return new JSONObject().put("code", code).put("id", id).put("left", left).put("right", right); }
    }
    record Comparison(String status, List<Difference> differences) {
        Comparison { differences = List.copyOf(differences); }
        JSONObject json() {
            JSONArray list = new JSONArray(); differences.forEach(d -> list.put(d.json()));
            return new JSONObject().put("scope", SCOPE).put("status", status).put("differences", list);
        }
    }

    static Comparison compare(CodeManifest left, CodeManifest right) {
        List<Difference> differences = new ArrayList<>();
        difference(differences, "GAME_VERSION", "", left.gameVersion, right.gameVersion);
        difference(differences, "GAME_CONTENT", "", left.gameFingerprint, right.gameFingerprint);
        difference(differences, "JAVA_VERSION", "", left.javaVersion, right.javaVersion);
        Map<String, Entry> a = index(left.mods), b = index(right.mods);
        TreeSet<String> ids = new TreeSet<>(a.keySet()); ids.addAll(b.keySet());
        for (String id : ids) {
            Entry x = a.get(id), y = b.get(id);
            if (x == null || y == null) {
                differences.add(new Difference(x == null ? "ONLY_RIGHT" : "ONLY_LEFT", id,
                        x == null ? "" : x.version, y == null ? "" : y.version));
            } else {
                difference(differences, "MOD_VERSION", id, x.version, y.version);
                difference(differences, "MOD_CONTENT", id, x.digest, y.digest);
                difference(differences, "INITIALIZATION", id, x.initialization, y.initialization);
            }
            if (x != null && !x.verified()) differences.add(new Difference("UNVERIFIED_LEFT", id, x.fingerprintStatus + "/" + x.initialization, ""));
            if (y != null && !y.verified()) differences.add(new Difference("UNVERIFIED_RIGHT", id, "", y.fingerprintStatus + "/" + y.initialization));
        }
        if (!left.verified()) differences.add(new Difference("INCOMPLETE_LEFT", "", "Required code identity or initialization unavailable", ""));
        if (!right.verified()) differences.add(new Difference("INCOMPLETE_RIGHT", "", "", "Required code identity or initialization unavailable"));
        return new Comparison(!left.verified() || !right.verified() ? "UNVERIFIABLE" : differences.isEmpty() ? "CODE_MATCH" : "DIFFERENT", differences);
    }

    private static Map<String, Entry> index(List<Entry> entries) {
        Map<String, Entry> result = new TreeMap<>(); entries.forEach(e -> result.put(e.id, e)); return result;
    }
    private static void difference(List<Difference> list, String code, String id, String left, String right) {
        if (!left.equals(right)) list.add(new Difference(code, id, left, right));
    }
    private static boolean isDigest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static String string(JSONObject object, String key) {
        if (!(object.get(key) instanceof String value)) throw invalid(key);
        return value;
    }
    private static void fields(JSONObject object, Set<String> expected) {
        Set<String> keys = new HashSet<>(); var it = object.keys(); while (it.hasNext()) keys.add((String) it.next());
        if (!keys.equals(expected)) throw invalid("fields");
    }
    private static void textValue(String value) {
        if (value == null || value.isBlank() || value.length() > 256 || value.chars().anyMatch(c -> c < 32 || c == 127)) throw invalid("text");
    }
    private static IllegalArgumentException invalid(String detail) { return new IllegalArgumentException("Invalid code manifest: " + detail); }
}
