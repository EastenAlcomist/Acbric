/*
 * CodeHandshakeRegression.java — 用可控时钟和乱序/重放报文验证握手边界。
 * 这里检验产品状态机；真实 Server/Client 的传输另由工作区隔离探针验证。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.util.*;

public final class CodeHandshakeRegression {
    private static int checks;
    private static final List<Integer> MEMBERS = List.of(1, 2);
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++; System.out.println("PASS handshake: " + label);
    }
    private static void invalid(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label); }
        catch (IllegalArgumentException | org.json.JSONException expected) { check(true, label); }
    }
    private static CodeManifest manifest(String digest, String init) {
        return new CodeManifest("1.2.15.2", "a".repeat(64), "21", List.of(
                new CodeManifest.Entry("acbric_api", "1", digest.repeat(64), "OK", init),
                new CodeManifest.Entry("fabricloader", "1", "a".repeat(64), "OK", "NO_ACBRIC_ENTRYPOINT")));
    }
    private static CodeHandshakeSession session(int self, CodeManifest manifest) {
        var value = new CodeHandshakeSession(manifest); value.context(10, self, MEMBERS, 0); return value;
    }
    private static String first(CodeHandshakeSession session, long now) { return session.tick(now).getFirst(); }
    private static String reply(CodeHandshakeSession session, String request, long now) { return session.receive(request, 10, false, now).orElseThrow(); }
    private static CodeHandshakeSession.State state(CodeHandshakeSession session, long now) { return session.snapshot(now).state(); }
    private static void deliver(CodeHandshakeSession source, CodeHandshakeSession target, long now) {
        for (String request : source.tick(now)) target.receive(request, 10, false, now).ifPresent(response -> source.receive(response, 10, false, now));
    }
    public static int run() {
        checks = 0;
        var good = manifest("a", "SUCCEEDED");
        var a = session(1, good); var b = session(2, good);
        check(state(a, 0) == CodeHandshakeSession.State.CHECKING, "new context requires confirmation");
        String request = first(a, 0), response = reply(b, request, 0);
        check(state(b, 0) == CodeHandshakeSession.State.CHECKING, "inbound request alone cannot confirm peer");
        a.receive(response, 10, false, 0);
        check(state(a, 0) == CodeHandshakeSession.State.CODE_MATCH, "current challenge response confirms equal code");
        deliver(b, a, 0);
        check(state(b, 0) == CodeHandshakeSession.State.CODE_MATCH, "both peers perform their own confirmation");
        check(a.tick(1).isEmpty(), "confirmed peer stops requests");
        var snapshot = a.snapshot(1);
        try { snapshot.peers().clear(); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { check(true, "snapshot cannot mutate live state"); }
        a.receive(response, 10, false, 29_999);
        check(state(a, 30_000) == CodeHandshakeSession.State.CHECKING, "duplicate response does not extend confirmation lease");
        a.receive(response, 10, false, 30_000);
        check(state(a, 30_000) == CodeHandshakeSession.State.CHECKING, "expired challenge cannot be resurrected");
        deliver(a, b, 30_000);
        check(state(a, 30_000) == CodeHandshakeSession.State.CODE_MATCH, "expired lease can be revalidated");
        check(snapshot.state() == CodeHandshakeSession.State.CODE_MATCH && snapshot.peers().get(2).attempts() == 1, "old snapshot remains stable");

        var timeout = session(1, good); var silent = session(2, good);
        String lost = first(timeout, 0), late = reply(silent, lost, 0);
        check(timeout.tick(1_999).isEmpty(), "retry interval enforced");
        for (int i = 1; i < 5; i++) check(first(timeout, i * 2_000).equals(lost), "retry retains same application challenge " + i);
        check(timeout.tick(9_999).isEmpty(), "at most five attempts");
        check(state(timeout, 10_000) == CodeHandshakeSession.State.TIMED_OUT, "silent or old framework times out");
        timeout.receive(late, 10, false, 10_000);
        check(state(timeout, 10_000) == CodeHandshakeSession.State.TIMED_OUT, "late response does not revive timeout");
        timeout.receive(first(silent, 0), 10, false, 10_001);
        check(state(timeout, 10_001) == CodeHandshakeSession.State.TIMED_OUT, "inbound request does not silently restart timeout");
        String prior = timeout.snapshot(10_001).session(); timeout.restart(10_002);
        check(!prior.equals(timeout.snapshot(10_002).session()), "explicit retry gets a new session");
        timeout.receive(late, 10, false, 10_002);
        check(state(timeout, 10_002) == CodeHandshakeSession.State.CHECKING, "old session reply ignored after restart");
        deliver(timeout, silent, 10_002);
        check(state(timeout, 10_002) == CodeHandshakeSession.State.CODE_MATCH, "explicit retry can recover");

        var reset = session(1, good); var partner = session(2, good);
        String oldRequest = first(reset, 0), oldResponse = reply(partner, oldRequest, 0);
        String oldSession = reset.snapshot(0).session();
        reset.context(10, 1, List.of(2, 1), 1);
        check(oldSession.equals(reset.snapshot(1).session()), "roster order does not restart timeout");
        reset.context(10, 1, List.of(1, 2, 3), 2);
        reset.receive(oldResponse, 10, false, 2);
        check(state(reset, 2) == CodeHandshakeSession.State.CHECKING && reset.snapshot(2).peers().size() == 2, "join invalidates prior confirmations");
        reset.context(10, 1, MEMBERS, 3); reset.receive(oldResponse, 10, false, 3);
        check(state(reset, 3) == CodeHandshakeSession.State.CHECKING && !oldSession.equals(reset.snapshot(3).session()), "leave and identical roster return cannot restore old session");
        reset.context(11, 1, MEMBERS, 4); reset.receive(oldResponse, 10, false, 4);
        check(state(reset, 4) == CodeHandshakeSession.State.CHECKING, "room change rejects previous room messages");
        reset.disconnect(5);
        check(state(reset, 5) == CodeHandshakeSession.State.IDLE && reset.tick(5).isEmpty(), "disconnect clears peers and outgoing work");
        reset.context(10, 1, MEMBERS, 6); first(reset, 6); reset.receive(oldResponse, 10, false, 6);
        check(state(reset, 6) == CodeHandshakeSession.State.CHECKING, "same ID reconnect requires fresh challenge");
        invalid(() -> reset.snapshot(5), "clock rollback rejected");
        reset.close(); reset.close();
        check(state(reset, 7) == CodeHandshakeSession.State.CLOSED && reset.snapshot(7).peers().isEmpty(), "close is idempotent and releases peers");
        try { reset.restart(7); throw new AssertionError("reopened"); }
        catch (IllegalStateException expected) { check(true, "closed session cannot restart"); }

        var alone = new CodeHandshakeSession(good);
        check(state(alone, 0) == CodeHandshakeSession.State.IDLE, "unbound session cannot match");
        alone.context(10, 1, List.of(1), 0);
        check(state(alone, 0) == CodeHandshakeSession.State.ALONE, "single member is explicit ALONE, not peer confirmation");
        invalid(() -> alone.context(-1, 1, MEMBERS, 0), "negative room is invalid");
        invalid(() -> alone.context(10, 3, MEMBERS, 0), "self must be in authoritative roster");
        invalid(() -> alone.context(10, 1, List.of(1, 1), 0), "duplicate members rejected");
        invalid(() -> alone.context(10, 1, List.of(-1, 1), 0), "negative IDs rejected");
        invalid(() -> alone.context(10, 1, java.util.stream.IntStream.range(0, 33).boxed().toList(), 0), "roster capped before allocation");

        for (var other : List.of(manifest("b", "SUCCEEDED"), manifest("a", "FAILED"))) {
            var left = session(1, good); var right = session(2, other);
            deliver(left, right, 0); deliver(right, left, 0);
            var expected = other.verified() ? CodeHandshakeSession.State.DIFFERENT : CodeHandshakeSession.State.UNVERIFIABLE;
            check(state(left, 0) == expected && state(right, 0) == expected, "both peers report " + expected);
            check(!left.snapshot(0).peers().get(2).comparison().differences().isEmpty(), "comparison retains actionable differences for " + expected);
        }
        var entries = new ArrayList<>(good.mods());
        for (int i = 0; i < 300; i++) entries.add(new CodeManifest.Entry("test_" + i, "1", "a".repeat(64), "OK", "SUCCEEDED"));
        var large = new CodeManifest(good.gameVersion(), good.gameFingerprint(), good.javaVersion(), entries);
        var limited = session(1, large); var ordinary = session(2, good);
        String smallRequest = first(limited, 0);
        check(CodeHandshakeProtocol.bytes(smallRequest) < 1000 && new JSONObject(smallRequest).isNull("manifest"), "oversized local manifest sends bounded explicit null");
        limited.receive(reply(ordinary, smallRequest, 0), 10, false, 0); deliver(ordinary, limited, 0);
        check(state(limited, 0) == CodeHandshakeSession.State.UNVERIFIABLE && state(ordinary, 0) == CodeHandshakeSession.State.UNVERIFIABLE
                && limited.snapshot(0).localProblem().equals("MANIFEST_TOO_LARGE"), "oversized manifest never degrades to success or empty MOD list");

        var guard = session(1, good); var remote = session(2, good);
        String pending = first(guard, 0), correct = reply(remote, pending, 0);
        guard.receive(correct, 10, true, 0); guard.receive(correct, 9, false, 0);
        check(state(guard, 0) == CodeHandshakeSession.State.CHECKING, "history and wrong outer frame ignored");
        for (String field : List.of("session", "requestSession", "challenge")) {
            JSONObject malformed = new JSONObject(correct).put(field, "invalid");
            invalid(() -> CodeHandshakeProtocol.decode(malformed.toString()), "malformed " + field + " rejected");
        }
        for (JSONObject malformed : List.of(new JSONObject(correct).put("protocol", 2), new JSONObject(correct).put("protocol", "1"),
                new JSONObject(correct).put("kind", "hello"), new JSONObject(correct).put("storeAsHistory", true),
                new JSONObject(correct).put("extra", true), new JSONObject(correct).put("from", "2"),
                new JSONObject(correct).put("###", -1),
                new JSONObject(correct).put("manifest", JSONObject.NULL), new JSONObject(correct).put("members", new org.json.JSONArray().put(1).put(1)))) {
            invalid(() -> CodeHandshakeProtocol.decode(malformed.toString()), "strict wire rejects " + malformed.toString().substring(0, Math.min(60, malformed.toString().length())));
            guard.receive(malformed.toString(), 10, false, 0);
        }
        invalid(() -> CodeHandshakeProtocol.decode("{\"protocol\":1," + correct.substring(1)), "duplicate JSON keys rejected");
        invalid(() -> CodeHandshakeProtocol.decode("{\"###\":1.5," + correct.substring(1)), "fractional sequence rejected without game floating serializer");
        invalid(() -> CodeHandshakeProtocol.decode(correct.replace("\"protocol\":1", "\"protocol\":1.0")), "floating integer is not coerced into protocol version");
        invalid(() -> CodeHandshakeProtocol.decode(correct.replace("\"schema\":1", "\"schema\":1.5")), "nested invalid numeric field rejected before serialization");
        invalid(() -> CodeHandshakeProtocol.decode(correct + "x"), "trailing JSON rejected");
        invalid(() -> CodeHandshakeProtocol.decode("中".repeat(14_000)), "UTF8 byte budget checked before parse");
        invalid(() -> CodeHandshakeProtocol.decode(new JSONObject(correct).put("manifest", large.json()).toString()), "oversized incoming manifest rejected");
        for (JSONObject stale : List.of(new JSONObject(correct).put("requestSession", UUID.randomUUID().toString()),
                new JSONObject(correct).put("challenge", UUID.randomUUID().toString()), new JSONObject(correct).put("channel", 11),
                new JSONObject(correct).put("from", 3).put("members", new org.json.JSONArray().put(1).put(2).put(3)),
                new JSONObject(correct).put("to", 2).put("from", 1))) {
            guard.receive(stale.toString(), 10, false, 0);
            check(state(guard, 0) == CodeHandshakeSession.State.CHECKING, "valid but unrelated packet cannot confirm");
        }
        check(CodeHandshakeProtocol.decode(new JSONObject(correct).put("###", Long.MAX_VALUE).toString()).manifest().equals(good), "native transport sequence allowed without changing application payload");
        guard.receive(new JSONObject(correct).put("status", "DIFFERENT").toString(), 10, false, 0);
        check(state(guard, 0) == CodeHandshakeSession.State.UNVERIFIABLE, "contradictory remote comparison cannot match");

        var flood = session(2, good);
        check(flood.receive(pending, 10, false, 0).isPresent() && flood.receive(pending, 10, false, 249).isEmpty(), "duplicate requests are rate limited per peer");
        check(flood.receive(pending, 10, false, 250).isPresent(), "lost response can be retransmitted without extending requester deadline");
        var fresh = session(1, good); var peer = session(2, good);
        deliver(fresh, peer, 0); deliver(peer, fresh, 0);
        peer.restart(300);
        fresh.receive(first(peer, 300), 10, false, 300);
        check(state(fresh, 300) == CodeHandshakeSession.State.CHECKING, "changed peer session conservatively invalidates confirmation");
        deliver(fresh, peer, 300);
        check(state(fresh, 300) == CodeHandshakeSession.State.CODE_MATCH, "fresh challenge validates restarted peer");

        var group = new HashMap<Integer, CodeHandshakeSession>();
        for (int id : List.of(1, 2, 3)) { var s = new CodeHandshakeSession(good); s.context(10, id, List.of(1, 2, 3), 0); group.put(id, s); }
        List<String> two = group.get(1).tick(0);
        String toTwo = two.stream().filter(s -> CodeHandshakeProtocol.decode(s).to() == 2).findFirst().orElseThrow();
        group.get(1).receive(reply(group.get(2), toTwo, 0), 10, false, 0);
        check(state(group.get(1), 0) == CodeHandshakeSession.State.CHECKING, "one matching peer cannot stand in for whole roster");
        String toThree = two.stream().filter(s -> CodeHandshakeProtocol.decode(s).to() == 3).findFirst().orElseThrow();
        group.get(1).receive(reply(group.get(3), toThree, 0), 10, false, 0);
        check(state(group.get(1), 0) == CodeHandshakeSession.State.CODE_MATCH, "all current peer responses required");
        return checks;
    }
}
