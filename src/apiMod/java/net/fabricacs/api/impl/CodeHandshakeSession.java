/*
 * CodeHandshakeSession.java — 与游戏 UI/网络解耦的内部代码握手状态机。
 * 调用者在同一线程提供单调毫秒时钟、真实房间成员和连接边界；本类不发网络、不修改准备状态。
 */
package net.fabricacs.api.impl;

import java.util.*;

final class CodeHandshakeSession implements AutoCloseable {
    static final long RETRY_MS = 2_000, TIMEOUT_MS = 10_000, LEASE_MS = 30_000, RESPONSE_INTERVAL_MS = 250;
    static final int MAX_ATTEMPTS = 5;
    enum State { IDLE, ALONE, CHECKING, CODE_MATCH, DIFFERENT, UNVERIFIABLE, TIMED_OUT, CLOSED }
    record PeerSnapshot(State state, int attempts, String remoteSession, CodeManifest.Comparison comparison) {}
    record Snapshot(State state, String session, String localProblem, Map<Integer, PeerSnapshot> peers) {
        Snapshot { peers = Collections.unmodifiableMap(new TreeMap<>(peers)); }
    }
    private static final class Peer {
        String challenge = token(), remoteSession = "";
        State state = State.CHECKING;
        CodeManifest.Comparison comparison;
        long started, sentAt, confirmedAt, repliedAt;
        int attempts;
        boolean replied;
        Peer(long now) { started = now; }
        void reset(long now) {
            challenge = token(); state = State.CHECKING; comparison = null; started = now; attempts = 0;
            // 保留上一次确认的对端会话，用于发现对端换房或主动重新检查；回复限流也不能被重置绕过。
        }
    }
    private final CodeManifest local, wireManifest;
    private final String localProblem;
    private final TreeMap<Integer, Peer> peers = new TreeMap<>();
    private List<Integer> members = List.of();
    private String session = "";
    private int channel, self;
    private long clock;
    private boolean active, closed;

    CodeHandshakeSession(CodeManifest local) {
        this.local = Objects.requireNonNull(local);
        wireManifest = CodeHandshakeProtocol.bytes(local.json().toString()) <= CodeHandshakeProtocol.MAX_MANIFEST_BYTES ? local : null;
        localProblem = wireManifest == null ? "MANIFEST_TOO_LARGE" : local.verified() ? "" : "LOCAL_UNVERIFIABLE";
    }

    /** 相同上下文不重置计时；连接恢复必须先 disconnect，或由适配层显式 restart。 */
    void context(int room, int player, Collection<Integer> roster, long now) {
        ensureOpen(); time(now);
        List<Integer> sorted = CodeHandshakeProtocol.roster(roster);
        if (room <= 0 || !sorted.contains(player)) throw new IllegalArgumentException("Invalid handshake context");
        if (active && channel == room && self == player && members.equals(sorted)) return;
        channel = room; self = player; members = sorted; active = true;
        reset(now);
    }

    void disconnect(long now) {
        ensureOpen(); time(now); active = false; session = ""; members = List.of(); peers.clear();
    }
    void restart(long now) {
        ensureOpen(); time(now);
        if (active) reset(now);
    }
    private void reset(long now) {
        session = token(); peers.clear();
        for (int member : members) if (member != self) peers.put(member, new Peer(now));
    }

    /** 最多每个对端一个请求；不维护可无限堆积的发件队列。发送失败由有限重试覆盖。 */
    List<String> tick(long now) {
        ensureOpen(); advance(now);
        List<String> outgoing = new ArrayList<>();
        for (var entry : peers.entrySet()) {
            Peer peer = entry.getValue();
            if (peer.state != State.CHECKING || peer.attempts >= MAX_ATTEMPTS
                    || peer.attempts > 0 && now - peer.sentAt < RETRY_MS) continue;
            outgoing.add(CodeHandshakeProtocol.encode(new CodeHandshakeProtocol.Packet(true, channel, self, entry.getKey(), members,
                    session, session, peer.challenge, "REQUEST", wireManifest)));
            peer.attempts++; peer.sentAt = now;
        }
        return List.copyOf(outgoing);
    }

    /** frameRoom/history 必须来自原生外层帧；历史记录及其他房间的报文不能参与当前握手。 */
    Optional<String> receive(String text, int frameRoom, boolean history, long now) {
        ensureOpen(); advance(now);
        if (!active || history || frameRoom != channel) return Optional.empty();
        CodeHandshakeProtocol.Packet packet;
        try { packet = CodeHandshakeProtocol.decode(text); }
        catch (IllegalArgumentException | org.json.JSONException malformed) { return Optional.empty(); }
        if (packet.channel() != channel || packet.to() != self || !packet.members().equals(members)) return Optional.empty();
        Peer peer = peers.get(packet.from());
        if (peer == null) return Optional.empty();
        if (packet.request()) {
            if (peer.replied && now - peer.repliedAt < RESPONSE_INTERVAL_MS) return Optional.empty();
            peer.replied = true; peer.repliedAt = now;
            // 旧请求最多触发保守重查，不能直接产生一致结论；超时必须显式重试，迟到请求不自动复活。
            if (!peer.remoteSession.isEmpty() && !peer.remoteSession.equals(packet.session())
                    && peer.state != State.CHECKING && peer.state != State.TIMED_OUT) peer.reset(now);
            var compared = compare(packet.manifest());
            return Optional.of(CodeHandshakeProtocol.encode(new CodeHandshakeProtocol.Packet(false, channel, self, packet.from(), members,
                    session, packet.requestSession(), packet.challenge(), compared.status(), wireManifest)));
        }
        if (peer.state != State.CHECKING || peer.attempts == 0 || !packet.requestSession().equals(session)
                || !packet.challenge().equals(peer.challenge)) return Optional.empty();
        var compared = compare(packet.manifest());
        if (!compared.status().equals(packet.status())) {
            compared = new CodeManifest.Comparison("UNVERIFIABLE", List.of(new CodeManifest.Difference(
                    "RESULT_DISAGREEMENT", "", compared.status(), packet.status())));
        }
        peer.comparison = compared; peer.state = State.valueOf(compared.status());
        peer.remoteSession = packet.session(); peer.confirmedAt = now;
        return Optional.empty();
    }

    private CodeManifest.Comparison compare(CodeManifest remote) {
        if (wireManifest == null || remote == null) return new CodeManifest.Comparison("UNVERIFIABLE",
                List.of(new CodeManifest.Difference("MANIFEST_TOO_LARGE", "", wireManifest == null ? "local" : "", remote == null ? "remote" : "")));
        return CodeManifest.compare(local, remote);
    }
    private void advance(long now) {
        time(now);
        for (Peer peer : peers.values()) {
            if (peer.state == State.CHECKING && now - peer.started >= TIMEOUT_MS) peer.state = State.TIMED_OUT;
            else if (peer.state == State.CODE_MATCH && now - peer.confirmedAt >= LEASE_MS) peer.reset(now);
        }
    }
    Snapshot snapshot(long now) {
        advance(now);
        Map<Integer, PeerSnapshot> result = new TreeMap<>();
        peers.forEach((id, peer) -> result.put(id, new PeerSnapshot(peer.state, peer.attempts, peer.remoteSession, peer.comparison)));
        State state = closed ? State.CLOSED : !active ? State.IDLE : !localProblem.isEmpty() ? State.UNVERIFIABLE
                : peers.isEmpty() ? State.ALONE : State.CODE_MATCH;
        if (active && localProblem.isEmpty() && !peers.isEmpty()) {
            for (State candidate : List.of(State.UNVERIFIABLE, State.DIFFERENT, State.TIMED_OUT, State.CHECKING)) {
                if (peers.values().stream().anyMatch(p -> p.state == candidate)) { state = candidate; break; }
            }
        }
        return new Snapshot(state, session, localProblem, result);
    }
    private void time(long now) {
        if (now < clock) throw new IllegalArgumentException("Handshake clock must be monotonic and non-negative");
        clock = now;
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("Handshake session closed"); }
    private static String token() { return UUID.randomUUID().toString(); }
    @Override public void close() {
        closed = true; active = false; session = ""; members = List.of(); peers.clear();
    }
}
