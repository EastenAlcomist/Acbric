/*
 * LobbyHandshakeBridge.java — 战役大厅适配：沿用原生单次消费，过滤框架尾包，绑定准备与房主更新。
 * 实例归属于大厅，不持有全局游戏引用；未适配的模式只过滤框架保留消息，不改变原生命令。
 *
 * 准备意愿不再因为例行续证、规则值更新或房主改动地图设置而整批作废：每名成员的凭据绑定自己的
 * 会话，只有本人换了会话才需要重新准备。规则值采用房主权威分发，其他玩家不必手动对齐本地配置。
 */
package net.fabricacs.api.impl;

import com.zarkonnen.airships.*;
import com.zarkonnen.catengine.Fount;
import com.zarkonnen.catengine.Draw;
import net.fabricacs.api.ui.*;
import net.fabricacs.api.util.AcbricLanguage;
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
    private long generation = -1, lastFrame = -1, lastPublish, invalidation, selfInvalidation;
    /** 玩家在大厅里点过准备：原生重同步会清零 ready 位且不会自动重发，这里记住意愿并自动恢复。 */
    private boolean readyIntent;
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
        syncHostReadyView();
        restoreReadyIntent();
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
        if (invalidation != gate.invalidation()) invalidation = gate.invalidation();
        // 只有本机自己的准备需要重做时才取消本机准备；其他成员的准备显示由房主更新重写。
        if (selfInvalidation != gate.selfInvalidation()) { selfInvalidation = gate.selfInvalidation(); clearOwnReady(); }
    }
    /** 房主把自己的界面显示对齐到真正通过校验的准备凭据；原生裸 ready 标记不参与显示。 */
    private void syncHostReadyView() {
        if (!gate.isHost()) return;
        int self = screen.g.playerID();
        syncHostReadyView(screen.channelPlayers, self);
        if (screen.hoster != null) syncHostReadyView(screen.hoster.channelPlayers, self);
    }
    private void syncHostReadyView(Map<Integer, StrategicPlayerInfo> players, int self) {
        if (players == null) return;
        for (var entry : players.entrySet())
            if (entry.getKey() != self && entry.getValue() != null) entry.getValue().ready = gate.readyAccepted(entry.getKey());
    }
    /** 取消本机准备意愿；不会清掉其他玩家的准备显示。 */
    private void clearOwnReady() { screen.readySent = false; }
    /**
     * 恢复玩家已经表达过的准备意愿。
     *
     * <p>原生在重同步（ResumeScreen 重建大厅）时把所有玩家的 ready 位清零，并且不拷贝 readySent，
     * 玩家只能自己再点一次；Acbric 还会在会话变化时作废本机凭据。这里只在「玩家此前确实点过准备、
     * 本机还没有重新准备好、并且当前门禁允许准备」时自动重发原生准备消息，凭据仍由本类按新会话签发。</p>
     */
    private void restoreReadyIntent() {
        if (!readyIntent || closed || screen.readySent) return;
        if (!canReady()) return;
        if (screen instanceof LobbyHandshakeAccess access) access.acbric$resendReady();
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
            // 规则候选值变化只重新开始规则交换；已经确认的会话与准备意愿保持有效。
            rules=next;rulesExchange.reset();startingRules=null;syncInvalidation();
        }
    }
    private void rulesContext(){
        rulesExchange.context(room,screen.g.playerID(),host,gate.codeView(now()),rules,now());
        RuleSet effective=rulesExchange.effective();
        if(effective!=null && effective.valid())gate.rulesDigest(effective.digest(),now());
    }
    public boolean canReady() {
        if(!connectionReady())return false;refreshRules();rulesContext();
        return rulesExchange.matched() && (gate.canReady(now()) || gate.canStart(now()));
    }
    public boolean canStart() {
        if(!connectionReady())return false;refreshRules();rulesContext();
        return rulesExchange.matched() && gate.canStart(now());
    }
    public boolean beginStart(){
        if(!canStart())return false;
        // 生成前固化的是本机真正采用的那一套：房主采用本机候选，其他玩家采用房主广播并校验过的快照。
        RuleSet effective=rulesExchange.effective();
        startingRules=effective!=null?effective:rules;
        return true;
    }
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
                    if(!rulesExchange.matched())clearOwnReady();
                }
                continue;
            }
            // 房主改动地图设置、帝国选择等原生内容不再撤销准备：玩法值由房主权威分发，
            // 每位玩家的准备凭据只绑定自己的会话，原生消息照旧按顺序传给原生处理器。
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
            // 重同步后的新大厅按原生语义清空 ready；把玩家已经表达过的准备意愿交给新实例。
            replacement.readyIntent = old.readyIntent;
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
            readyIntent = true;
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
        boolean zh = AcbricLanguage.isChinese();
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
            boolean adopted=rulesExchange.adopted();
            label=switch(ruleState){
                case "MATCH" -> adopted ? (zh?"已采用房主设置":"Host settings applied") : label+(zh?" / 规则一致":" / Rules match");
                case "UNADOPTABLE" -> zh?"无法采用房主设置":"Cannot apply host settings";
                case "DIFFERENT" -> zh?"玩法规则不同":"Rules differ";
                case "UNVERIFIABLE" -> zh?"规则无法验证":"Rules unverifiable";
                case "TIMED_OUT" -> zh?"规则检查中（自动重试）":"Checking rules (retrying)";
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
        if (rulesExchange.adopted()) text.append('\n').append(AcbricLanguage.isChinese()
                ? "玩法设置采用房主的快照，本机配置未被修改。" : "Gameplay settings use the host's snapshot; local config files are untouched.");
        return text.append("\n").append(AcbricLanguage.isChinese()
                ? "准备意愿只在你自己重新连接后需要重做；点击此处打开联机大厅面板。" : "Your ready state survives routine re-checks; click here to open the lobby panel.").toString();
    }
    /** 复用左上角连接信息位置，不占用准备/离开按钮；原连接文字保留在提示中。 */
    public Draw drawStatus(MyDraw draw, String original, Fount font, double x, double y) {
        String label = status(); int width = draw.bw(label);
        draw.button((int)x, MyDraw.TOP_BAR_INSET, width, label, (Runnable) this::openMenu);
        draw.tooltip(x, MyDraw.TOP_BAR_INSET, width, MyDraw.BUTTON_H, original + "\n" + details());
        return draw;
    }

    /**
     * 联机大厅面板：状态与重新检查、各 MOD 的玩法设置入口（房主决定，其他玩家自动采用）、
     * AcMod（Java MOD）启停与代码差异。原状态按钮的点击从「重新检查」改为打开这里，
     * 因为重新检查已经自动进行，而设置与 MOD 管理原本只能退出大厅后才能操作。
     */
    public void openMenu() {
        try {
            ModUi menu = new ModUi("acbric_api");
            menu.open(menuWindow());
        } catch (RuntimeException ex) {
            System.err.println("[Acbric Lobby] cannot open the lobby panel: " + ex);
        }
    }
    public UiWindow menuWindow() {
        boolean zh = AcbricLanguage.isChinese();
        List<UiNode> nodes = new ArrayList<>();
        nodes.add(Ui.label(this::status));
        nodes.add(Ui.label(this::details));
        nodes.add(Ui.row(8,
                Ui.button(() -> zh ? "重新检查" : "Re-check", h -> retry()),
                Ui.button(() -> zh ? "关闭" : "Close", UiWindowHandle::close)));
        nodes.add(Ui.label(() -> zh ? "玩法设置（城市数、资金、AI 舰队等）：房主修改后其他玩家自动采用，不需要各自改成一样的值。"
                : "Gameplay settings (cities, cash, AI fleets…): the host decides and other players apply the same values automatically."));
        for (UiBridge.Registered entry : UiBridge.registered()) nodes.add(Ui.button(entry.label(), h -> {
            UiWindow next;
            try { next = entry.factory().get(); }
            catch (RuntimeException ex) { h.message(zh ? "无法打开设置" : "Cannot open settings", String.valueOf(ex.getMessage())); return; }
            if (next == null || !next.modal()) { h.message(zh ? "无法打开设置" : "Cannot open settings", entry.owner() + ":" + entry.id()); return; }
            h.dialog(next);
        }));
        nodes.add(Ui.label(() -> zh ? "AcMod（Java MOD）启停：改动在重启游戏后生效，重启后重新进入大厅即可。"
                : "AcMod (Java MOD) enable/disable: changes apply after a restart, then rejoin the lobby."));
        JavaModManager manager = JavaModManager.current();
        if (manager == null) {
            try { JavaModManager.refresh(); manager = JavaModManager.current(); }
            catch (RuntimeException ex) { System.err.println("[Acbric Lobby] MOD manager unavailable: " + ex); }
        }
        if (manager == null) nodes.add(Ui.label(() -> zh ? "无法读取 MOD 列表。" : "The MOD list is unavailable."));
        else for (JavaModManager.Entry entry : manager.entries()) {
            JavaModManager fixed = manager;
            String id = entry.id();
            nodes.add(Ui.row(8,
                    Ui.label(() -> id + " · " + fixed.status(id, AcbricLanguage.isChinese())).width(430),
                    Ui.button(() -> fixed.entry(id) != null && fixed.enabledNext(id)
                                    ? (zh ? "下次启动停用" : "Disable on restart") : (zh ? "下次启动启用" : "Enable on restart"),
                            h -> toggleMod(fixed, id, h))
                            .enabled(() -> fixed.entry(id) != null && fixed.entry(id).manageable() && fixed.reason(id).isEmpty())));
        }
        // 原生 MOD（AI 舰队包、玩法资源包等）同样只能在重启后改变加载集合；这里只写游戏自己的设置。
        Map<Mod, Boolean> nativeNext = new IdentityHashMap<>();
        List<Mod> nativeMods = new ArrayList<>();
        try { for (Mod mod : Mod.getAvailableMods()) if (mod != null && mod.dir != null) { nativeMods.add(mod); nativeNext.put(mod, mod.isCurrentlyEnabled()); } }
        catch (RuntimeException ex) { System.err.println("[Acbric Lobby] native MOD list unavailable: " + ex); }
        if (!nativeMods.isEmpty()) {
            nodes.add(Ui.label(() -> zh ? "原生 MOD（AI 舰队包等）：启停写入游戏设置，重启后生效；同房玩家需要同一组 MOD。"
                    : "Native MODs (AI fleet packs…): toggling is saved and applies after a restart; everyone needs the same set."));
            for (Mod mod : nativeMods) nodes.add(Ui.row(8,
                    Ui.label(() -> mod.getName() + " · " + (Boolean.TRUE.equals(nativeNext.get(mod)) ? (zh ? "启用" : "Enabled") : (zh ? "停用" : "Disabled"))).width(430),
                    Ui.button(() -> Boolean.TRUE.equals(nativeNext.get(mod))
                                    ? (zh ? "下次启动停用" : "Disable on restart") : (zh ? "下次启动启用" : "Enable on restart"),
                            h -> toggleNativeMod(mod, nativeNext, h))));
        }
        return new UiWindow(zh ? "Acbric 联机大厅" : "Acbric multiplayer lobby", 660, 660, true, Ui.column(8, nodes.toArray(UiNode[]::new)));
    }
    private void toggleMod(JavaModManager manager, String id, UiWindowHandle handle) {
        boolean zh = AcbricLanguage.isChinese();
        try { manager.toggle(id); }
        catch (java.io.IOException | RuntimeException ex) { handle.message(zh ? "无法更改" : "Cannot change", String.valueOf(ex.getMessage())); }
    }
    private void toggleNativeMod(Mod mod, Map<Mod, Boolean> next, UiWindowHandle handle) {
        boolean enable = !Boolean.TRUE.equals(next.get(mod));
        try { mod.setPermanentlyEnabled(enable); next.put(mod, enable); }
        catch (RuntimeException ex) {
            System.err.println("[Acbric Lobby] cannot change native MOD " + mod.getName() + ": " + ex);
            handle.message(AcbricLanguage.isChinese() ? "无法更改" : "Cannot change", String.valueOf(ex.getMessage()));
        }
    }
    @Override public void close() { if (!closed) { closed = true; readyIntent = false; gate.close(); rulesExchange.reset();rulesMap=null;startingRules=null;members = List.of(); connection = null; } }
}
