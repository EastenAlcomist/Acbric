/* LobbyCodeGateRegression.java — 验证房主批次、全员准备凭据、过期帧及 LAN 零号房间。 */
package net.fabricacs.api.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class LobbyCodeGateRegression {
    private static int checks;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++; System.out.println("PASS lobby gate: " + name);
    }
    private static CodeManifest manifest(String hash) {
        return new CodeManifest("1", "a".repeat(64), "21", List.of(
                new CodeManifest.Entry("acbric_api", "1", hash.repeat(64), "OK", "NO_ACBRIC_ENTRYPOINT"),
                new CodeManifest.Entry("fabricloader", "1", "a".repeat(64), "OK", "NO_ACBRIC_ENTRYPOINT")));
    }
    private static Map<Integer, LobbyCodeGate> group(int room, int count) {
        var all = new TreeMap<Integer, LobbyCodeGate>(); var roster = java.util.stream.IntStream.rangeClosed(1, count).boxed().toList();
        for (int id : roster) { var gate = new LobbyCodeGate(manifest("a")); gate.context(room, id, 1, roster, 0); all.put(id, gate); }
        exchange(all, room, 0);
        JSONObject update = all.get(1).hostUpdate(0);
        for (var gate : all.values()) gate.observeUpdate(update, Set.of(), new HashSet<>(roster), 0);
        return all;
    }
    private static void exchange(Map<Integer, LobbyCodeGate> all, int room, long now) {
        for (var source : all.values()) for (String request : source.tick(now)) {
            int target = CodeHandshakeProtocol.decode(request).to();
            if (all.containsKey(target)) all.get(target).receive(request, room, false, now).ifPresent(reply -> source.receive(reply, room, false, now));
        }
    }
    public static int run() {
        checks = 0;
        var all = group(0, 3); var host = all.get(1); var b = all.get(2); var c = all.get(3);
        check(all.values().stream().allMatch(g -> g.canReady(0)), "LAN room zero negotiates a full three-member view");
        check(all.values().stream().noneMatch(g -> g.canStart(0)), "code match alone never authorizes start");
        var hp = host.prepare(0); var bp = b.prepare(0); var cp = c.prepare(0);
        check(host.acceptReady(1, hp, 0), "host also validates its own ready echo");
        check(host.acceptReady(2, bp, 0), "guest ready binds current shared view");
        check(!host.acceptReady(3, null, 0), "bare native ready cannot count as verified");
        check(host.observeUpdate(host.hostUpdate(0), Set.of(1,2), Set.of(1,2,3), 0).size() == 2 && !host.canStart(0), "partial ready set does not start");
        check(host.acceptReady(3, cp, 0), "last guest proof accepted");
        JSONObject committed = host.hostUpdate(0);
        for (var gate : all.values()) gate.observeUpdate(committed, Set.of(1,2,3), Set.of(1,2,3), 0);
        check(all.values().stream().allMatch(g -> g.canStart(0)), "all participants accept same complete ready batch");
        b.observeUpdate(null, Set.of(1,2,3), Set.of(1,2,3), 0);
        check(!b.canStart(0), "invalid subsequent host update revokes prior start grant");
        b.observeUpdate(committed, Set.of(1,2,3), Set.of(1,2,3), 0);
        check(b.canStart(0), "fresh complete update can restore a still-current decision");
        check(!host.canStart(2_001), "start confirmation has a bounded action window");
        check(!host.acceptReady(4, bp, 2_001), "non-member ready rejected");
        check(!b.acceptReady(1, hp, 0), "guest does not become ready coordinator");

        var fresh = group(5, 2); var h = fresh.get(1); var guest = fresh.get(2);
        JSONObject oldHost = h.hostUpdate(0); JSONObject oldProof = guest.prepare(0);
        h.restart(300); exchange(fresh, 5, 300); exchange(fresh, 5, 2_300);
        JSONObject replacement = h.hostUpdate(2_300);
        check(replacement != null && !replacement.getString("hostSession").equals(oldHost.getString("hostSession")), "host retry establishes a new context batch");
        check(!h.acceptReady(2, oldProof, 2_300), "prior ready cannot survive host retry");
        guest.observeUpdate(replacement, Set.of(), Set.of(1,2), 2_300);
        check(guest.canReady(2_300), "guest can prepare again after new round");
        check(guest.observeUpdate(oldHost, Set.of(1,2), Set.of(1,2), 2_300).isEmpty(), "old host update cannot roll back current context");
        check(!guest.canStart(2_300), "old ready flags do not start after reset");
        check(h.hostUpdate(2_300).getJSONObject("ready").length() == 0, "old host approvals cleared");

        for (String key : List.of("v", "channel", "host", "hostSession", "round", "sessions", "proof")) {
            JSONObject bad = guest.prepare(2_300); bad.put(key, "wrong");
            check(!h.acceptReady(2, bad, 2_300), "reject wrong ready field " + key);
        }
        JSONObject extra = guest.prepare(2_300).put("extra", true);
        check(!h.acceptReady(2, extra, 2_300), "unknown ready schema field rejected");
        JSONObject incomplete = guest.prepare(2_300); incomplete.getJSONObject("sessions").remove("1");
        check(!h.acceptReady(2, incomplete, 2_300), "missing peer view rejected");
        JSONObject canonical = guest.prepare(2_300); var sessions = canonical.getJSONObject("sessions"); sessions.put("02", sessions.remove("2"));
        check(!h.acceptReady(2, canonical, 2_300), "noncanonical member keys rejected");
        JSONObject future = h.hostUpdate(2_300).put("round", 99L);
        check(h.observeUpdate(future, Set.of(), Set.of(1,2), 2_300).isEmpty(), "host cannot adopt an unsolicited future revision");

        var missing = new LobbyCodeGate(manifest("a")); missing.context(5, 2, 1, List.of(1,2), 0);
        missing.tick(0);
        check(!missing.canReady(10_000) && !missing.canStart(10_000), "silent or old framework cannot ready or start");
        var mismatch = new LobbyCodeGate(manifest("b")); mismatch.context(5, 2, 1, List.of(1,2), 0);
        var same = new LobbyCodeGate(manifest("a")); same.context(5, 1, 1, List.of(1,2), 0);
        exchange(Map.of(1,same,2,mismatch),5,0);
        check(!same.canReady(0) && same.hostUpdate(0) == null, "different code cannot issue a room ready batch");

        var incompleteReady = group(10, 2); var ih = incompleteReady.get(1); var ig = incompleteReady.get(2);
        ih.acceptReady(1, ih.prepare(0), 0); ih.acceptReady(2, ig.prepare(0), 0);
        JSONObject complete = ih.hostUpdate(0);
        check(ig.observeUpdate(complete, Set.of(1,2), Set.of(1), 0).size() == 2 && !ig.canStart(0), "native player list must cover entire authoritative roster");
        ig.observeUpdate(complete, Set.of(1,2), Set.of(1,2), 0);
        check(ig.canStart(0), "native ready set and current proofs jointly authorize start");
        ig.context(10, 2, 1, List.of(1,2,3), 1);
        check(!ig.canStart(1) && !ig.canReady(1), "join invalidates an already granted start");
        ig.context(10, 2, 1, List.of(1,2), 2);
        check(ig.observeUpdate(complete, Set.of(1,2), Set.of(1,2), 2).isEmpty(), "same roster after leave does not reuse old grant");
        ih.disconnect(1); check(!ih.canStart(1), "disconnect revokes grant");

        var renewed = group(10,2); var rh = renewed.get(1); var rg = renewed.get(2);
        var intent = rg.prepare(0); check(rh.acceptReady(2,intent,0), "ready before renewal");
        long revision = rh.revision(); exchange(renewed,10,30_000);
        check(rh.revision() == revision && rh.readyAccepted(2), "routine lease renewal retains intent for unchanged identities");
        rg.restart(30_300);
        for (String packet : rg.tick(30_300)) rh.receive(packet,10,false,30_300);
        check(!rh.readyAccepted(2) && !rh.canStart(30_300), "known remote context change immediately revokes approvals");

        var stripped = new JSONObject().put("type","frame").put("channelID",0).put("members",new JSONArray().put(1).put(2))
                .put("messages",new JSONArray().put(new JSONObject().put("type","before"))
                        .put(new JSONObject().put("type",CodeHandshakeProtocol.TYPE)).put(new JSONObject().put("type","after")));
        var filtered = LobbyHandshakeBridge.stripReserved(stripped);
        check(filtered.getJSONArray("messages").length() == 2 && filtered.getJSONArray("members").length() == 2
                && filtered.getJSONArray("messages").getJSONObject(1).getString("type").equals("after"), "late protocol packets removed without dropping native order or members");
        check(LobbyHandshakeBridge.stripReserved(new JSONObject().put("type","other_mod:message")).getString("type").equals("other_mod:message"), "unrelated extension messages preserved");
        check(LobbyHandshakeBridge.stripReserved(null) == null, "empty native queue preserved");
        for (var gate : all.values()) gate.close();
        check(!b.canStart(30_001), "closed gate cannot grant start");
        return checks;
    }
}
