/*
 * CodeHandshakeProtocol.java — 内部代码握手报文；严格限定字段、大小和上下文。
 * 原生服务器只转发这些字段，不认证 from；本协议不是身份认证或反作弊接口。
 */
package net.fabricacs.api.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class CodeHandshakeProtocol {
    static final String TYPE = "acbric:code_handshake";
    static final int MAX_WIRE_BYTES = 40_000;
    static final int MAX_MANIFEST_BYTES = 32_000;
    static final int MAX_MEMBERS = 32;
    private static final Set<String> FIELDS = Set.of("type", "protocol", "kind", "channel", "from", "to",
            "members", "session", "requestSession", "challenge", "status", "manifest");

    /** manifest 为空只代表发送端无法在单包预算内提供清单，绝不能解释为没有 MOD。 */
    record Packet(boolean request, int channel, int from, int to, List<Integer> members,
                  String session, String requestSession, String challenge, String status, CodeManifest manifest) {
        Packet {
            members = roster(members);
            if (channel <= 0 || from == to || !members.contains(from) || !members.contains(to)) throw invalid("routing");
            token(session); token(requestSession); token(challenge);
            if (request ? !status.equals("REQUEST") || !session.equals(requestSession)
                    : !Set.of("CODE_MATCH", "DIFFERENT", "UNVERIFIABLE").contains(status)) throw invalid("status");
            if (manifest == null && !request && !status.equals("UNVERIFIABLE")) throw invalid("missing manifest");
            if (manifest != null && bytes(manifest.json().toString()) > MAX_MANIFEST_BYTES) throw invalid("manifest size");
        }
    }

    static String encode(Packet p) {
        JSONArray members = new JSONArray(); p.members.forEach(members::put);
        String result = new JSONObject().put("type", TYPE).put("protocol", 1)
                .put("kind", p.request ? "request" : "response").put("channel", p.channel)
                .put("from", p.from).put("to", p.to).put("members", members).put("session", p.session)
                .put("requestSession", p.requestSession).put("challenge", p.challenge).put("status", p.status)
                .put("manifest", p.manifest == null ? JSONObject.NULL : p.manifest.json()).toString();
        if (bytes(result) > MAX_WIRE_BYTES) throw invalid("wire size");
        return result;
    }

    static Packet decode(String text) {
        if (text == null || text.length() > MAX_WIRE_BYTES || bytes(text) > MAX_WIRE_BYTES) throw invalid("wire size");
        JSONObject value = ConfigJson.parseObject(text);
        Set<String> keys = new HashSet<>(); var iterator = value.keys();
        while (iterator.hasNext()) keys.add((String) iterator.next());
        // 原生 Client 自动添加序列号；它只负责传输去重，不参与应用层会话确认。
        if (keys.remove("###")) {
            Object sequence = value.get("###");
            if (!(sequence instanceof Integer || sequence instanceof Long) || ((Number) sequence).longValue() < 0) throw invalid("sequence");
        }
        if (!keys.equals(FIELDS) || !TYPE.equals(string(value, "type")) || integer(value, "protocol") != 1) throw invalid("fields/version");
        String kind = string(value, "kind");
        if (!kind.equals("request") && !kind.equals("response")) throw invalid("kind");
        JSONArray array = value.getJSONArray("members");
        if (array.length() > MAX_MEMBERS) throw invalid("member count");
        List<Integer> members = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            if (!(array.get(i) instanceof Integer id)) throw invalid("member type");
            members.add(id);
        }
        CodeManifest manifest = value.isNull("manifest") ? null : CodeManifest.fromJson(value.getJSONObject("manifest"));
        return new Packet(kind.equals("request"), integer(value, "channel"), integer(value, "from"), integer(value, "to"), members,
                string(value, "session"), string(value, "requestSession"), string(value, "challenge"), string(value, "status"), manifest);
    }

    static List<Integer> roster(Collection<Integer> members) {
        if (members == null || members.isEmpty() || members.size() > MAX_MEMBERS) throw invalid("member count");
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer id : members) if (id == null || id < 0 || !sorted.add(id)) throw invalid("member ID");
        return List.copyOf(sorted);
    }
    static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    private static void token(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw invalid("token");
    }
    private static int integer(JSONObject value, String key) {
        if (!(value.get(key) instanceof Integer number)) throw invalid(key);
        return number;
    }
    private static String string(JSONObject value, String key) {
        if (!(value.get(key) instanceof String text)) throw invalid(key);
        return text;
    }
    private static IllegalArgumentException invalid(String detail) { return new IllegalArgumentException("Invalid code handshake: " + detail); }
    private CodeHandshakeProtocol() {}
}
