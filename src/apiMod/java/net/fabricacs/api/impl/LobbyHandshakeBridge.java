/*
 * LobbyHandshakeBridge.java — 战役大厅适配：沿用原生单次消费，过滤框架尾包，绑定准备与房主更新。
 * 实例归属于大厅，不持有全局游戏引用；未适配的模式只过滤框架保留消息，不改变原生命令。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.Fount;
import com.zarkonnen.catengine.Draw;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public final class LobbyHandshakeBridge implements AutoCloseable {
    private static final long CLOCK_START = System.nanoTime();
    private final StrategicLobbyScreen screen;
    private final LobbyCodeGate gate;
    private final LobbyRuleExchange rulesExchange = new LobbyRuleExchange();
    private RuleSet rules, startingRules;
    private Object rulesMap;
    private long rulesRegistryRevision = -1, rulesStoreRevision = -1;
    private boolean rulesResumed;
    private int room = -1, host = -1;
    private List<Integer> members = List.of();
    private Client connection;
    private long generation = -1, lastFrame = -1, lastPublish, invalidation;
    private boolean readyConnection, closed, welcome, publishing;
    private String lastReport = "";
    private int reports;

    public LobbyHandshakeBridge(StrategicLobbyScreen screen) { this(screen, StartupCodeManifest.current); }
    LobbyHandshakeBridge(StrategicLobbyScreen screen, CodeManifest manifest) { this.screen = screen; gate = new LobbyCodeGate(manifest); }
    private static long now() { return (System.nanoTime() - CLOCK_START) / 1_000_000; }

    public void welcome(JSONObject message) {
        if (closed) return;
        JSONObject info = message.optJSONObject("info");
        welcome = info != null && !info.optBoolean("isMainChatChannel", false) && info.has("initiatorID")
                && (!info.has("mode") || info.optString("mode").equals("conquest"));
        room = welcome ? message.optInt("channelID", -1) : -1;
        host = screen.g.lanClient == null ? screen.initiatorID : screen.hoster != null ? screen.g.playerID() : -1;
        lastFrame = -1; members = List.of(); gate.disconnect(now()); rulesExchange.reset(); clearNativeReady();
    }
    private boolean connectionReady() {
        if (closed) return false;
        Client current = screen.g.lanClient != null ? screen.g.lanClient : screen.g.client;
        long nextGeneration = current instanceof ConnectionGenerationAccess access ? access.acbric$connectionGeneration() : -1;
        boolean live = current instanceof ConnectionGenerationAccess access && access.acbric$transportReady();
        if (current != connection || nextGeneration != generation || readyConnection && !live) {
            gate.disconnect(now()); rulesExchange.reset(); members = List.of(); clearNativeReady();
        }
        connection = current; generation = nextGeneration; readyConnection = live;
        return live && welcome && room >= 0 && !closed;
    }
    public void pulse() {
        if (!connectionReady()) return;
        refreshRules();
        if (members.contains(screen.g.playerID())) gate.context(room, screen.g.playerID(), host, members, now());
        for (String text : gate.tick(now())) send(text);
        syncInvalidation();
        rulesContext(); rulesExchange.tick(now()).ifPresent(this::send);
        String status = status();
        if (!lastReport.equals(status)) {
            if (reports < 100) { System.out.println("[Acbric Lobby] " + status); reports++; }
            lastReport = status;
        }
        // 原生没有准备撤销消息；用已有的房主更新传播清理后的状态及当前批次。
        if (gate.isHost() && screen.hoster != null && !publishing && now() - lastPublish >= 1_000) {
            lastPublish = now(); publishing = true;
            try { screen.hoster.doSendUpdate(); } finally { publishing = false; }
        }
    }
    private void send(String text) {
        if (connection != null && readyConnection) connection.sendMessageRawWithSizeCheck(new JSONObject(text));
    }
    private void syncInvalidation() {
        if (invalidation != gate.invalidation()) { invalidation = gate.invalidation(); clearNativeReady(); }
    }
    private void clearNativeReady() {
        screen.readySent = false;
        if (screen.channelPlayers != null) screen.channelPlayers.values().forEach(p -> p.ready = false);
        if (screen.hoster != null) screen.hoster.channelPlayers.values().forEach(p -> p.ready = false);
    }
    private void refreshRules() {
        boolean resumed=screen.isResumeFromLoaded;
        Object map=resumed && screen.loadedGame!=null ? screen.loadedGame.map : null;
        long registryRevision=SharedRulesRegistry.revision(), storeRevision=-1;
        if(map instanceof CampaignDataAccess access && access.acbric$campaignDataStore()!=null) storeRevision=access.acbric$campaignDataStore().revision();
        if(rules!=null && rulesResumed==resumed && rulesMap==map && rulesRegistryRevision==registryRevision && rulesStoreRevision==storeRevision)return;
        RuleSet next=resumed ? map==null ? RuleSet.invalid("SAVED","WAITING_FOR_SAVE") : SharedRulesRegistry.saved(map) : SharedRulesRegistry.newCampaign();
        rulesMap=map;rulesResumed=resumed;rulesRegistryRevision=registryRevision;rulesStoreRevision=storeRevision;
        if(rules==null || !rules.text().equals(next.text())) {
            rules=next;gate.rulesDigest(next.digest(),now());rulesExchange.reset();startingRules=null;syncInvalidation();
        }
    }
    private void rulesContext(){rulesExchange.context(room,screen.g.playerID(),gate.codeView(now()),rules,now());}
    public boolean canReady() {
        if(!connectionReady())return false;refreshRules();rulesContext();
        return rulesExchange.matched() && (gate.canReady(now()) || gate.canStart(now()));
    }
    public boolean canStart() {
        if(!connectionReady())return false;refreshRules();rulesContext();
        return rulesExchange.matched() && gate.canStart(now());
    }
    public boolean beginStart(){if(!canStart())return false;startingRules=rules;return true;}
    RuleSet startingRules(){if(startingRules==null)throw new IllegalStateException("No approved campaign rules");return startingRules;}
    public void retry() { if (connectionReady()) { gate.restart(now()); rulesExchange.reset(); syncInvalidation(); } }

    private JSONObject frame(JSONObject message) {
        boolean live = connectionReady();
        if(live)refreshRules();
        int channel = message.optInt("channelID", -1);
        if (welcome && channel != room) return null;
        boolean history = message.has("historyFrame");
        if (!history && live) {
            Object serial = message.opt("frameNumber");
            if (!(serial instanceof Integer || serial instanceof Long)) return null;
            long next = ((Number) serial).longValue();
            if (next <= lastFrame) return null;
            lastFrame = next;
            try {
                JSONArray array = message.getJSONArray("members"); List<Integer> ids = new ArrayList<>();
                if (array.length() > CodeHandshakeProtocol.MAX_MEMBERS) throw new IllegalArgumentException("Too many members");
                for (int i = 0; i < array.length(); i++) {
                    if (!(array.get(i) instanceof Integer id)) throw new IllegalArgumentException("Invalid member");
                    ids.add(id);
                }
                members = CodeHandshakeProtocol.roster(ids);
                if (!members.contains(screen.g.playerID())) throw new IllegalArgumentException("Local player missing");
                gate.context(room, screen.g.playerID(), host, members, now());
            } catch (IllegalArgumentException | org.json.JSONException bad) {
                gate.disconnect(now()); members = List.of(); clearNativeReady(); live = false;
            }
        }
        JSONArray source = message.optJSONArray("messages");
        if (source == null) return message;
        JSONArray kept = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.getJSONObject(i); String type = item.optString("type");
            if (CodeHandshakeProtocol.TYPE.equals(type)) {
                if (live && !history) gate.receive(item.toString(), channel, false, now()).ifPresent(this::send);
                syncInvalidation(); continue;
            }
            if (LobbyRuleExchange.TYPE.equals(type)) {
                if(live && !history && !gate.codeView(now()).isEmpty()) {
                    rulesContext();rulesExchange.receive(item.toString(),channel,false,now());
                    if(!rulesExchange.matched())clearNativeReady();
                }
                continue;
            }
            if (live && !history && gate.isHost() && Set.of("strategicSizeAndDifficulty", "claimEmpire", "joinEmpire", "strategicEmpireAndArms").contains(type)) {
                gate.restart(now()); syncInvalidation();
            }
            if (type.equals("strategicReady") || type.equals("strategicResumeReady")) {
                if (gate.isHost() && (!live || history || !canReady() || !gate.acceptReady(item.optInt("id", -1), item.optJSONObject(LobbyCodeGate.FIELD), now()))) continue;
            }
            if (type.equals("hosterUpdate")) {
                JSONObject metadata = item.optJSONObject(LobbyCodeGate.FIELD);
                // LAN 原生 initiatorID 固定为 0；房主的框架更新补充真实玩家 ID。此字段仍非认证身份。
                if (live && !history && host < 0 && screen.g.lanClient != null && metadata != null) {
                    Object claimed = metadata.opt("host");
                    if (claimed instanceof Integer id && members.contains(id)) { host = id; gate.context(room, screen.g.playerID(), host, members, now()); }
                }
                Set<Integer> nativeReady = new HashSet<>(), players = new HashSet<>(); boolean duplicate = false;
                JSONArray rows = item.getJSONArray("players");
                for (int j = 0; j < rows.length(); j++) {
                    JSONObject row = rows.getJSONObject(j); int id = row.getInt("id");
                    if (!players.add(id)) duplicate = true;
                    if (row.optBoolean("ready", false)) nativeReady.add(id);
                }
                Set<Integer> accepted = live && !history && !duplicate ? gate.observeUpdate(metadata, nativeReady, players, now()) : Set.of();
                syncInvalidation();
                if(!rulesExchange.matched())accepted=Set.of();
                for (int j = 0; j < rows.length(); j++) { JSONObject row = rows.getJSONObject(j); row.put("ready", accepted.contains(row.getInt("id"))); }
            }
            kept.put(item);
        }
        message.put("messages", kept);
        return message;
    }

    /** 只在原生消费者取消息的位置调用一次；保留非框架消息顺序及外层帧。 */
    public static JSONObject incoming(AirshipGame game, JSONObject message) {
        if (message == null) return null;
        if (game.s instanceof StrategicLobbyScreen lobby && lobby instanceof LobbyHandshakeAccess access) {
            if (message.optString("type").equals("frame")) return access.acbric$lobbyHandshake().frame(message);
        } else if (game.s instanceof ResumeScreen resume && resume.sls instanceof LobbyHandshakeAccess access
                && message.optString("type").equals("welcome")) {
            // 原生大厅恢复可能更换房间，随后创建新大厅但不再调用 processWelcome。
            JSONObject info = message.optJSONObject("info");
            if (info != null && info.has("initiatorID") && !info.optBoolean("isMainChatChannel", false))
                access.acbric$lobbyHandshake().room = message.optInt("channelID", -1);
        }
        return stripReserved(message);
    }
    public static void afterResume(ResumeScreen resume) {
        if (!(resume.sls instanceof LobbyHandshakeAccess oldAccess) || !(resume.g.s instanceof StrategicLobbyScreen next)
                || next == resume.sls || !(next instanceof LobbyHandshakeAccess newAccess)) return;
        LobbyHandshakeBridge old = oldAccess.acbric$lobbyHandshake(), replacement = newAccess.acbric$lobbyHandshake();
        if (!replacement.welcome && old.welcome) {
            replacement.room = old.room; replacement.welcome = old.room >= 0;
            replacement.host = next.g.lanClient == null ? next.initiatorID : next.hoster != null ? next.g.playerID() : -1;
            replacement.clearNativeReady(); old.close();
        }
    }
    static JSONObject stripReserved(JSONObject message) {
        if (message == null) return null;
        if (reserved(message.optString("type"))) return null;
        if (message.optString("type").equals("frame") && message.optJSONArray("messages") != null) {
            JSONArray source = message.getJSONArray("messages"), kept = new JSONArray();
            for (int i = 0; i < source.length(); i++) {
                JSONObject item = source.getJSONObject(i);
                if (!reserved(item.optString("type"))) kept.put(item);
            }
            message.put("messages", kept);
        }
        return message;
    }
    private static boolean reserved(String type){return CodeHandshakeProtocol.TYPE.equals(type) || LobbyRuleExchange.TYPE.equals(type);}
    public static boolean outgoing(AirshipGame game, JSONObject message) {
        if (!(game.s instanceof StrategicLobbyScreen lobby) || !(lobby instanceof LobbyHandshakeAccess access)) return true;
        return access.acbric$lobbyHandshake().outgoing(message);
    }
    private boolean outgoing(JSONObject message) {
        String type = message.optString("type");
        if (type.equals("strategicReady") || type.equals("strategicResumeReady")) {
            JSONObject proof = canReady() ? gate.prepare(now()) : null;
            if (proof == null) { screen.readySent = false; return false; }
            message.put(LobbyCodeGate.FIELD, proof);
        } else if (type.equals("hosterUpdate")) {
            if(!closed)refreshRules();
            JSONObject proof = connectionReady() ? gate.hostUpdate(now()) : null;
            syncInvalidation();
            JSONArray players = message.getJSONArray("players");
            for (int i = 0; i < players.length(); i++) {
                JSONObject player = players.getJSONObject(i);
                if (proof == null || !rulesExchange.matched() || !gate.readyAccepted(player.getInt("id"))) player.put("ready", false);
            }
            message.put(LobbyCodeGate.FIELD, proof == null ? JSONObject.NULL : proof);
        }
        return true;
    }

    public String status() {
        var snapshot = gate.snapshot(now());
        boolean zh = Lang.currentLocale != null && Lang.currentLocale.getLanguage().equals("zh");
        String label = switch (snapshot.state()) {
            case CODE_MATCH -> gate.canReady(now()) ? (zh ? "代码一致" : "Code match") : (zh ? "等待房主确认" : "Waiting for host");
            case DIFFERENT -> zh ? "代码不同" : "Code differs";
            case UNVERIFIABLE -> zh ? "无法验证" : "Unverifiable";
            case TIMED_OUT -> zh ? "检查超时" : "Timed out";
            case ALONE -> zh ? "等待其他玩家" : "Waiting for peers";
            case IDLE -> zh ? "等待连接与房间" : "Waiting for room";
            default -> zh ? "代码检查中" : "Checking code";
        };
        if(snapshot.state()==CodeHandshakeSession.State.CODE_MATCH) {
            String ruleState=rulesExchange.state(now());
            label=switch(ruleState){
                case "MATCH" -> label+(zh?" / 规则一致":" / Rules match");
                case "DIFFERENT" -> zh?"玩法规则不同":"Rules differ";
                case "UNVERIFIABLE" -> zh?"规则无法验证":"Rules unverifiable";
                case "TIMED_OUT" -> zh?"规则检查超时":"Rules timed out";
                default -> zh?"检查玩法规则中":"Checking rules";
            };
        }
        return "Acbric: " + label;
    }
    public String details() {
        var snapshot = gate.snapshot(now()); StringBuilder text = new StringBuilder(status());
        if (!snapshot.localProblem().isEmpty()) text.append('\n').append(snapshot.localProblem());
        snapshot.peers().forEach((id, peer) -> {
            text.append('\n').append(id).append(": ").append(peer.state());
            if (peer.comparison() != null) peer.comparison().differences().stream().limit(2)
                    .forEach(d -> text.append(" / ").append(d.id()).append(' ').append(d.code()));
        });
        rulesExchange.differences().forEach(d->text.append('\n').append(d));
        return text.append("\n").append(Lang.currentLocale != null && Lang.currentLocale.getLanguage().equals("zh")
                ? "点击重新检查；只比较代码和显式声明的规则，不保证完整状态同步。" : "Click to retry. Code and declared rules only; not full state synchronization.").toString();
    }
    /** 复用左上角连接信息位置，不占用准备/离开按钮；原连接文字保留在提示中。 */
    public Draw drawStatus(MyDraw draw, String original, Fount font, double x, double y) {
        String label = status(); int width = draw.bw(label);
        draw.button((int)x, MyDraw.TOP_BAR_INSET, width, label, (Runnable) this::retry);
        draw.tooltip(x, MyDraw.TOP_BAR_INSET, width, MyDraw.BUTTON_H, original + "\n" + details());
        return draw;
    }
    @Override public void close() { if (!closed) { closed = true; gate.close(); rulesExchange.reset();rulesMap=null;startingRules=null;members = List.of(); connection = null; } }
}
