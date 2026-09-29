/*
 * LobbyCodeGate.java — 战役大厅的房主批次、准备凭据与开局确认。
 * 自报身份只用于正常玩家协作；保留原生规则，由适配器同时检查资源、玩家和连接状态。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.util.*;

final class LobbyCodeGate implements AutoCloseable {
    static final String FIELD = "acbricLobby";
    private final CodeHandshakeSession handshake;
    private int room, self, host;
    private List<Integer> members = List.of();
    private Map<Integer, String> roundView = Map.of();
    private final Map<Integer, String> ready = new TreeMap<>();
    private String ownProof = "", localSession = "", hostSession = "";
    private long revision, observedRevision, invalidation, grantedAt = -1;
    private boolean bound;
    private String rulesDigest = "";

    LobbyCodeGate(CodeManifest manifest) { handshake = new CodeHandshakeSession(manifest); }

    void context(int room, int self, int host, Collection<Integer> members, long now) {
        List<Integer> roster = CodeHandshakeProtocol.roster(members);
        if (host >= 0 && !roster.contains(host)) { disconnect(now); return; }
        boolean hostChanged = bound && this.host != host;
        handshake.context(room, self, roster, now);
        if (hostChanged) handshake.restart(now);
        String session = handshake.snapshot(now).session();
        this.room = room; this.self = self; this.host = host; this.members = roster; bound = true;
        if (!session.equals(localSession)) {
            localSession = session; hostSession = ""; observedRevision = 0; invalidate();
        }
        refresh(now);
    }
    void disconnect(long now) {
        handshake.disconnect(now); bound = false; members = List.of(); localSession = ""; hostSession = "";
        observedRevision = 0; invalidate();
    }
    void restart(long now) {
        handshake.restart(now); localSession = handshake.snapshot(now).session(); invalidate();
    }
    List<String> tick(long now) { var result = handshake.tick(now); refresh(now); return result; }
    Optional<String> receive(String packet, int frameRoom, boolean history, long now) {
        // 在远端新会话的响应完成前就撤销开局决定，不能使用两秒确认窗口跨越已知会话变化。
        if (bound && !history && frameRoom == room) try {
            var p = CodeHandshakeProtocol.decode(packet);
            var peer = handshake.snapshot(now).peers().get(p.from());
            if (p.request() && p.to() == self && p.channel() == room && p.members().equals(members)
                    && peer != null && !peer.remoteSession().isEmpty() && !p.session().equals(peer.remoteSession())) invalidate();
        } catch (IllegalArgumentException | org.json.JSONException ignored) { }
        var response = handshake.receive(packet, frameRoom, history, now); refresh(now); return response;
    }
    CodeHandshakeSession.Snapshot snapshot(long now) { refresh(now); return handshake.snapshot(now); }
    long invalidation() { return invalidation; }
    long revision() { return revision; }
    boolean isHost() { return bound && self == host; }
    Map<Integer,String> codeView(long now) { return currentView(now); }
    void rulesDigest(String digest,long now) {
        if (!rulesDigest.equals(digest)) { rulesDigest=digest; restart(now); }
    }

    private Map<Integer, String> currentView(long now) {
        var snapshot = handshake.snapshot(now);
        if (!bound || snapshot.state() != CodeHandshakeSession.State.CODE_MATCH || members.size() < 2) return Map.of();
        Map<Integer, String> view = new TreeMap<>(); view.put(self, snapshot.session());
        snapshot.peers().forEach((id, peer) -> view.put(id, peer.remoteSession()));
        return Map.copyOf(view);
    }
    private void refresh(long now) {
        Map<Integer, String> view = currentView(now);
        // 定期续证期间只暂时禁止准备/开局；身份未变时保留玩家的准备意愿。
        if (view.isEmpty() || host < 0) return;
        String newHostSession = view.get(host);
        if (!Objects.equals(hostSession, newHostSession)) {
            hostSession = newHostSession; observedRevision = 0;
            if (!roundView.isEmpty()) invalidate();
        }
        if (!roundView.isEmpty() && !roundView.equals(view)) invalidate();
        if (isHost() && roundView.isEmpty()) {
            revision = Math.incrementExact(revision); roundView = view; ready.clear(); ownProof = ""; grantedAt = -1; invalidation++;
        }
    }
    private void invalidate() {
        roundView = Map.of(); ready.clear(); ownProof = ""; grantedAt = -1; invalidation++;
    }
    boolean canReady(long now) { refresh(now); return !roundView.isEmpty() && roundView.equals(currentView(now)); }

    /** 玩家实际点击准备时生成新凭据；只查询按钮状态不产生意愿或发网络。 */
    JSONObject prepare(long now) {
        if (!canReady(now)) return null;
        ownProof = UUID.randomUUID().toString();
        return header().put("proof", ownProof);
    }
    boolean acceptReady(int player, JSONObject proof, long now) {
        if (!isHost() || !canReady(now) || !members.contains(player)) return false;
        try {
            checkHeader(proof, Set.of("v", "channel", "host", "hostSession", "round", "sessions", "rulesDigest", "proof"));
            if (number(proof, "round") != revision || !view(proof.getJSONObject("sessions")).equals(roundView)) return false;
            String token = token(proof.get("proof"));
            if (player == self && !token.equals(ownProof)) return false;
            ready.put(player, token); return true;
        } catch (IllegalArgumentException | org.json.JSONException invalid) { return false; }
    }
    boolean readyAccepted(int id) { return ready.containsKey(id); }

    /** 房主广播与原生 hosterUpdate 同批次的准备凭据，不能从裸 ready=true 推断已验证。 */
    JSONObject hostUpdate(long now) {
        if (!isHost() || !canReady(now)) return null;
        JSONObject result = header(), tokens = new JSONObject();
        ready.forEach((id, token) -> tokens.put(id.toString(), token));
        return result.put("ready", tokens);
    }
    private JSONObject header() {
        JSONObject sessions = new JSONObject(); roundView.forEach((id, session) -> sessions.put(id.toString(), session));
        return new JSONObject().put("v", 2).put("channel", room).put("host", host).put("hostSession", hostSession)
                .put("round", revision).put("sessions", sessions).put("rulesDigest",rulesDigest);
    }

    /** 返回可展示为已准备的成员；收齐全员凭据后，在短窗口内执行已经确认的开局决定。 */
    Set<Integer> observeUpdate(JSONObject update, Set<Integer> nativeReady, Set<Integer> nativePlayers, long now) {
        refresh(now);
        // 后续房主更新必须独立通过校验，失败时不能继续沿用先前的短期开局许可。
        grantedAt = -1;
        try {
            checkHeader(update, Set.of("v", "channel", "host", "hostSession", "round", "sessions", "rulesDigest", "ready"));
            Map<Integer, String> view = view(update.getJSONObject("sessions"));
            if (!view.equals(currentView(now))) return Set.of();
            long next = number(update, "round");
            if (next <= 0 || next < observedRevision || isHost() && next != revision) return Set.of();
            Map<Integer, String> proofs = tokens(update.getJSONObject("ready"));
            if (next != observedRevision || roundView.isEmpty()) {
                if (!isHost()) { invalidate(); revision = next; roundView = view; }
                observedRevision = next;
            }
            if (!roundView.equals(view)) return Set.of();
            Set<Integer> accepted = new TreeSet<>(proofs.keySet()); accepted.retainAll(nativeReady);
            // 本机未点击本轮准备，即使房主误带了旧 ready 标记也不能被动开局。
            if (ownProof.isEmpty() || !ownProof.equals(proofs.get(self))) accepted.remove(self);
            if (accepted.equals(new TreeSet<>(members)) && nativePlayers.equals(new HashSet<>(members))) grantedAt = now;
            else grantedAt = -1;
            return Set.copyOf(accepted);
        } catch (IllegalArgumentException | org.json.JSONException invalid) { return Set.of(); }
    }
    boolean canStart(long now) {
        refresh(now);
        // 确认帧到达后立即进入原生开局；短暂租约跨界不把一半玩家留在大厅。
        // 连接/成员/会话变化仍通过 invalidate 撤销此决定。
        return bound && !roundView.isEmpty() && grantedAt >= 0 && now >= grantedAt && now - grantedAt <= 2_000;
    }

    private void checkHeader(JSONObject value, Set<String> fields) {
        if (value == null) throw new IllegalArgumentException("Missing lobby proof");
        Set<String> keys = new HashSet<>(); var it = value.keys(); while (it.hasNext()) keys.add((String) it.next());
        if (!keys.equals(fields) || number(value, "v") != 2 || number(value, "channel") != room || number(value, "host") != host
                || !rulesDigest.equals(value.get("rulesDigest"))
                || !token(value.get("hostSession")).equals(hostSession)) throw new IllegalArgumentException("Wrong lobby proof context");
    }
    private Map<Integer, String> view(JSONObject object) {
        Map<Integer, String> result = tokens(object);
        if (!result.keySet().equals(new HashSet<>(members))) throw new IllegalArgumentException("Incomplete lobby view");
        return result;
    }
    private Map<Integer, String> tokens(JSONObject object) {
        if (object.length() > CodeHandshakeProtocol.MAX_MEMBERS) throw new IllegalArgumentException("Too many proofs");
        Map<Integer, String> result = new TreeMap<>(); var keys = object.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next(); int id;
            try { id = Integer.parseInt(key); } catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid member"); }
            if (!key.equals(Integer.toString(id)) || !members.contains(id)) throw new IllegalArgumentException("Unexpected member");
            result.put(id, token(object.get(key)));
        }
        return Map.copyOf(result);
    }
    private static long number(JSONObject object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Integer || value instanceof Long)) throw new IllegalArgumentException("Invalid integer");
        return ((Number) value).longValue();
    }
    private static String token(Object value) {
        if (!(value instanceof String s) || !s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid token");
        return s;
    }
    @Override public void close() { handshake.close(); bound = false; members = List.of(); invalidate(); }
}
