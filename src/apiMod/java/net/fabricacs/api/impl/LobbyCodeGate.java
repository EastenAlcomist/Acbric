/*
 * LobbyCodeGate.java — 战役大厅的房主批次、准备凭据与开局确认。
 * 自报身份只用于正常玩家协作；保留原生规则，由适配器同时检查资源、玩家和连接状态。
 *
 * 准备意愿按「成员 + 会话」保存，而不是整批作废：只有真正换了会话（重连、手动重新检查、
 * 换房）的成员需要重新准备，例行续证、规则值更新、其他人的重连以及房主改动地图设置都不会
 * 再撤销全员的准备。批次 `round` 仍标识一份完整、一致的会话视图，开局许可仍要求全员凭据。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.util.*;

final class LobbyCodeGate implements AutoCloseable {
    static final String FIELD = "acbricLobby";
    /**
     * 规则摘要变化后的宽限窗口：改动落地的瞬间，正在路上的准备凭据仍按旧摘要接受。
     * 规则值由房主权威广播，准备意愿与规则值解耦，因此摘要变化本身不再重启检查。
     */
    private static final long DIGEST_GRACE_MS = 5_000;
    private final CodeHandshakeSession handshake;
    private int room, self, host;
    private List<Integer> members = List.of();
    private Map<Integer, String> roundView = Map.of();
    /** 已接受的准备凭据：成员 ID 到准备 UUID。 */
    private final Map<Integer, String> ready = new TreeMap<>();
    /** 每条凭据绑定的成员会话；会话不同即作废该成员的凭据，不影响其他成员。 */
    private final Map<Integer, String> proofSession = new TreeMap<>();
    private String ownProof = "", localSession = "", hostSession = "";
    private long revision, observedRevision, invalidation, selfInvalidation, grantedAt = -1, digestChangedAt = -1;
    private boolean bound;
    private String rulesDigest = "", previousDigest = "";

    LobbyCodeGate(CodeManifest manifest) { handshake = new CodeHandshakeSession(manifest); }

    void context(int room, int self, int host, Collection<Integer> members, long now) {
        List<Integer> roster = CodeHandshakeProtocol.roster(members);
        if (host >= 0 && !roster.contains(host)) { disconnect(now); return; }
        boolean rosterChanged = bound && !this.members.equals(roster);
        boolean hostChanged = bound && this.host != host;
        handshake.context(room, self, roster, now);
        if (hostChanged) handshake.restart(now);
        String session = handshake.snapshot(now).session();
        this.room = room; this.self = self; this.host = host; this.members = roster; bound = true;
        if (!session.equals(localSession)) { localSession = session; hostSession = ""; observedRevision = 0; }
        dropAbsent(roster, now);
        // 成员变化要重新确认开局许可；已经准备的成员仍然保留自己的凭据。
        if (rosterChanged) { grantedAt = -1; invalidateDisplay(); }
        refresh(now);
    }
    void disconnect(long now) {
        handshake.disconnect(now); bound = false; members = List.of(); localSession = ""; hostSession = "";
        observedRevision = 0; roundView = Map.of(); dropProofs();
    }
    /** 主动重新检查：只作废发起者自己的凭据，其他玩家沿用已经确认的会话。 */
    void restart(long now) {
        handshake.restart(now); localSession = handshake.snapshot(now).session(); observedRevision = 0;
        dropStale(now);
    }
    List<String> tick(long now) { var result = handshake.tick(now); refresh(now); return result; }
    Optional<String> receive(String packet, int frameRoom, boolean history, long now) {
        // 在远端新会话的响应完成前就撤销该成员的准备决定，不能使用两秒确认窗口跨越已知会话变化。
        if (bound && !history && frameRoom == room) try {
            var p = CodeHandshakeProtocol.decode(packet);
            var peer = handshake.snapshot(now).peers().get(p.from());
            if (p.request() && p.to() == self && p.channel() == room && p.members().equals(members)
                    && peer != null && !peer.remoteSession().isEmpty() && !p.session().equals(peer.remoteSession()))
                dropMemberProof(p.from(), p.session());
        } catch (IllegalArgumentException | org.json.JSONException ignored) { }
        var response = handshake.receive(packet, frameRoom, history, now); refresh(now); return response;
    }
    CodeHandshakeSession.Snapshot snapshot(long now) { refresh(now); return handshake.snapshot(now); }
    long invalidation() { return invalidation; }
    /** 只在本机玩家自己的准备需要重做时递增；适配器据此取消本机准备，而不是清掉全员的准备显示。 */
    long selfInvalidation() { return selfInvalidation; }
    long revision() { return revision; }
    boolean isHost() { return bound && self == host; }
    Map<Integer,String> codeView(long now) { return currentView(now); }
    /** 记录当前规则摘要：不重启检查，旧摘要仅在短窗口内继续有效。 */
    void rulesDigest(String digest,long now) {
        if (rulesDigest.equals(digest)) return;
        previousDigest = rulesDigest; rulesDigest = digest; digestChangedAt = now;
    }

    private Map<Integer, String> currentView(long now) {
        var snapshot = handshake.snapshot(now);
        if (!bound || snapshot.state() != CodeHandshakeSession.State.CODE_MATCH || members.size() < 2) return Map.of();
        Map<Integer, String> view = new TreeMap<>(); view.put(self, snapshot.session());
        snapshot.peers().forEach((id, peer) -> view.put(id, peer.remoteSession()));
        return Map.copyOf(view);
    }
    private void refresh(long now) {
        if (!bound) return;
        dropStale(now);
        Map<Integer, String> view = currentView(now);
        if (view.isEmpty() || host < 0) return;
        String newHostSession = view.get(host);
        if (!Objects.equals(hostSession, newHostSession)) { hostSession = newHostSession; observedRevision = 0; }
        if (!isHost()) return;                     // 客人只采信房主广播的批次，不自行产生批次
        if (roundView.equals(view)) return;
        // 视图变化只说明某个成员的会话换了；未变化的成员保留准备凭据。
        roundView = view;
        revision = Math.incrementExact(revision);
        invalidateDisplay();
    }
    private void invalidateDisplay() { invalidation++; }

    /** 会话确实变化或成员已离开时才作废该成员凭据；会话暂时未知（重连中）先保留，等重新确认再判定。 */
    private void dropStale(long now) {
        if (!bound) return;
        var snapshot = handshake.snapshot(now);
        Map<Integer, String> observed = new TreeMap<>(); observed.put(self, snapshot.session());
        snapshot.peers().forEach((id, peer) -> observed.put(id, peer.remoteSession()));
        for (int id : new ArrayList<>(proofSession.keySet())) {
            String current = observed.get(id);
            String bound = proofSession.get(id);
            if (current == null || current.isEmpty() || bound == null || current.equals(bound)) continue;
            dropProof(id);
        }
    }
    /** 成员集合变化时先清掉已经不在房间里的凭据。 */
    private void dropAbsent(Collection<Integer> roster, long now) {
        for (int id : new ArrayList<>(proofSession.keySet())) if (!roster.contains(id)) dropProof(id);
    }
    private void dropMemberProof(int member, String session) {
        if (!proofSession.containsKey(member)) return;
        if (Objects.equals(proofSession.get(member), session)) return;
        dropProof(member);
    }
    private void dropProof(int id) {
        proofSession.remove(id); ready.remove(id);
        if (id == self) dropOwnProof();
    }
    private void dropProofs() {
        proofSession.clear(); ready.clear(); dropOwnProof();
    }
    private void dropOwnProof() {
        if (ownProof.isEmpty()) return;
        ownProof = ""; grantedAt = -1; selfInvalidation++; invalidateDisplay();
    }
    boolean canReady(long now) { refresh(now); return !roundView.isEmpty() && roundView.equals(currentView(now)); }

    /** 玩家实际点击准备时生成新凭据；只查询按钮状态不产生意愿或发网络。 */
    JSONObject prepare(long now) {
        if (!canReady(now)) return null;
        ownProof = UUID.randomUUID().toString();
        // 本机凭据同样绑定当前批次里自己的会话：会话变化后凭据立即作废，需要重新点击准备。
        proofSession.put(self, roundView.get(self));
        return header().put("proof", ownProof);
    }
    boolean acceptReady(int player, JSONObject proof, long now) {
        if (!isHost() || !canReady(now) || !members.contains(player)) return false;
        try {
            checkHeader(proof, Set.of("v", "channel", "host", "hostSession", "round", "sessions", "rulesDigest", "proof"), now);
            if (number(proof, "round") != revision || !view(proof.getJSONObject("sessions")).equals(roundView)) return false;
            String token = token(proof.get("proof"));
            if (player == self && !token.equals(ownProof)) return false;
            String session = roundView.get(player);
            if (session == null) return false;
            ready.put(player, token); proofSession.put(player, session); return true;
        } catch (IllegalArgumentException | org.json.JSONException invalid) { return false; }
    }
    boolean readyAccepted(int id) { return ready.containsKey(id); }
    /** 本机玩家的准备凭据是否仍然有效；适配器据此自动重发准备，而不是让玩家重新点一次。 */
    boolean selfProofValid() { return !ownProof.isEmpty(); }

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
            checkHeader(update, Set.of("v", "channel", "host", "hostSession", "round", "sessions", "rulesDigest", "ready"), now);
            Map<Integer, String> view = view(update.getJSONObject("sessions"));
            if (!view.equals(currentView(now))) return Set.of();
            long next = number(update, "round");
            if (next <= 0 || next < observedRevision || isHost() && next != revision) return Set.of();
            Map<Integer, String> proofs = tokens(update.getJSONObject("ready"));
            if (next != observedRevision || roundView.isEmpty()) {
                // 新批次只更新视图与批次号；本机准备凭据在新视图中仍然有效时保留。
                if (!isHost()) { revision = next; roundView = view; }
                observedRevision = next;
                dropStale(now);
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
        // 连接/成员/会话变化仍通过凭据作废撤销此决定。
        return bound && !roundView.isEmpty() && grantedAt >= 0 && now >= grantedAt && now - grantedAt <= 2_000;
    }

    private void checkHeader(JSONObject value, Set<String> fields, long now) {
        if (value == null) throw new IllegalArgumentException("Missing lobby proof");
        Set<String> keys = new HashSet<>(); var it = value.keys(); while (it.hasNext()) keys.add((String) it.next());
        if (!keys.equals(fields) || number(value, "v") != 2 || number(value, "channel") != room || number(value, "host") != host
                || !digestAccepted(value.get("rulesDigest"), now)
                || !token(value.get("hostSession")).equals(hostSession)) throw new IllegalArgumentException("Wrong lobby proof context");
    }
    /** 当前摘要优先；刚变化后的短窗口内仍接受上一份摘要，避免丢掉正在路上的准备。 */
    private boolean digestAccepted(Object value, long now) {
        if (!(value instanceof String sent)) return false;
        if (rulesDigest.equals(sent)) return true;
        return previousDigest.equals(sent) && now >= digestChangedAt && now - digestChangedAt <= DIGEST_GRACE_MS;
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
    @Override public void close() { handshake.close(); bound = false; members = List.of(); roundView = Map.of(); dropProofs(); }
}
