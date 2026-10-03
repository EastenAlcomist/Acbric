/*
 * LobbyRuleExchange.java — 房主权威的有界规则快照分发：房主的值经本地声明校验后被其他玩家采用。
 *
 * 房主是唯一权威：其他玩家不再比较自己的本地配置，而是采用房主广播的快照，并用本地已声明的
 * 版本与校验器确认自己确实能玩这套值。这样「开局城市数、资金」等设置只需要房主改一次，其他
 * 玩家不必手动改成相同数字；缺包、版本不符或本地校验失败时依然不能准备/开局。
 * 发送按周期性窗口重试，超时不再需要玩家手动点「重新检查」。
 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class LobbyRuleExchange {
    static final String TYPE="acbric:campaign_rules";
    static final int MAX_WIRE_BYTES=40_000;
    /** 一个重试窗口：窗口内最多发送 MAX_ATTEMPTS 次、间隔至少 COOLDOWN_MS；未确认就自动进入下一个窗口。 */
    private static final long WINDOW_MS=10_000, COOLDOWN_MS=2_000;
    private static final int MAX_ATTEMPTS=5, TIMEOUT_WINDOWS=3;
    private Map<Integer,String> sessions=Map.of();
    private final Map<Integer,RuleSet> peers=new TreeMap<>();
    private RuleSet local, adopted;
    private String adoptProblem="";
    private int room,self,host=-1,attempts,windows;
    private long began,lastSend=-1;
    void reset(){sessions=Map.of();peers.clear();local=null;adopted=null;adoptProblem="";attempts=0;windows=0;lastSend=-1;}
    void context(int room,int self,int host,Map<Integer,String> view,RuleSet local,long now){
        if(view.isEmpty())return; // 例行续证不改变上下文；适配器在真正失效时显式 reset。
        if(this.room==room && this.self==self && this.host==host && sessions.equals(view)
                && this.local!=null && this.local.text().equals(local.text()) && this.local.source.equals(local.source))return;
        this.room=room;this.self=self;this.host=host;this.sessions=Map.copyOf(view);this.local=local;
        peers.clear();attempts=0;windows=0;began=now;lastSend=-1;
    }
    /** 兼容旧签名：没有房主身份时不做采用，只保留原有的逐成员比较。 */
    void context(int room,int self,Map<Integer,String> view,RuleSet local,long now){context(room,self,-1,view,local,now);}

    /** 本机实际应当执行的一套值：房主广播且本地校验通过时用它，否则用本地候选。 */
    RuleSet effective(){return adopted!=null?adopted:local;}
    boolean adopted(){return adopted!=null;}
    String adoptProblem(){return adoptProblem;}

    Optional<String> tick(long now){
        advance(now);
        if(sessions.isEmpty() || attempts>=MAX_ATTEMPTS || lastSend>=0 && now-lastSend<COOLDOWN_MS)return Optional.empty();
        RuleSet current=effective();
        if(current==null||!current.valid())return Optional.empty();
        attempts++;lastSend=now;
        JSONObject view=new JSONObject();sessions.forEach((id,session)->view.put(id.toString(),session));
        return Optional.of(new JSONObject().put("type",TYPE).put("v",1).put("channel",room).put("from",self)
                .put("sessions",view).put("rules",current.text()).toString());
    }
    void receive(String text,int frameRoom,boolean history,long now){
        advance(now);
        if(history || sessions.isEmpty() || frameRoom!=room)return;
        try{
            if(text==null || text.length()>MAX_WIRE_BYTES || text.getBytes(StandardCharsets.UTF_8).length>MAX_WIRE_BYTES)return;
            JSONObject packet=ConfigJson.parseObject(text);
            Set<String> fields=new HashSet<>(Set.of("type","v","channel","from","sessions","rules"));
            if(packet.has("###")){
                fields.add("###");Object sequence=packet.get("###");
                if(!(sequence instanceof Integer || sequence instanceof Long) || ((Number)sequence).longValue()<0)return;
            }
            RuleSet.keys(packet,fields);
            if(!TYPE.equals(packet.get("type")) || CampaignDataStore.version(packet,"v")!=1 || CampaignDataStore.version(packet,"channel")!=room)return;
            int from=CampaignDataStore.version(packet,"from");if(from==self || !sessions.containsKey(from))return;
            JSONObject view=packet.getJSONObject("sessions");
            if(view.length()!=sessions.size())return;
            for(var entry:sessions.entrySet())if(!entry.getValue().equals(view.opt(entry.getKey().toString())))return;
            if(!(packet.get("rules") instanceof String rules))return;
            RuleSet remote=RuleSet.parse(rules);
            // 房主可以随设置更新广播新快照；其他成员只报告自己确认的那一份，反复变化只推迟确认。
            if(from==host && from!=self)adopt(remote,now);
            peers.put(from,remote);
        }catch(IllegalArgumentException | org.json.JSONException invalid){ }
    }
    /** 采用房主快照前的本地校验：声明齐全、版本一致且通过本地校验器；失败时保留失败原因。 */
    private void adopt(RuleSet remote,long now){
        String failure=SharedRulesRegistry.adoptionProblem(remote);
        if(failure!=null){
            adoptProblem=failure;
            if(adopted!=null){adopted=null;peers.clear();attempts=0;began=now;lastSend=-1;}
            return;
        }
        adoptProblem="";
        if(adopted!=null && adopted.text().equals(remote.text()))return;
        adopted=remote;
        // 换了权威快照：旧的对比结果全部作废，重新收集一轮；本机准备凭据不受影响。
        peers.clear();attempts=0;windows=0;began=now;lastSend=-1;
    }
    /** 周期性重试：没有确认就开下一个窗口，不再需要玩家手动重新检查。 */
    private void advance(long now){
        if(sessions.size()<2 || matched() || now-began<WINDOW_MS)return;
        long elapsed=now-began, rolled=elapsed/WINDOW_MS;
        windows+= (int) Math.min(rolled, Integer.MAX_VALUE-windows);
        began+=rolled*WINDOW_MS;attempts=0;lastSend=-1;
    }
    boolean matched(){
        RuleSet effective=effective();
        if(effective==null || !effective.valid() || sessions.size()<2 || peers.size()!=sessions.size()-1)return false;
        return peers.values().stream().allMatch(effective::matches);
    }
    String state(long now){
        advance(now);
        if(local==null)return "WAITING";
        if(!adoptProblem.isEmpty())return "UNADOPTABLE";
        RuleSet effective=effective();
        if(!effective.valid() || peers.values().stream().anyMatch(p->!p.valid()))return "UNVERIFIABLE";
        if(peers.values().stream().anyMatch(p->!effective.matches(p)))return "DIFFERENT";
        if(matched())return "MATCH";
        return windows>=TIMEOUT_WINDOWS?"TIMED_OUT":"CHECKING";
    }
    List<String> differences(){
        List<String> result=new ArrayList<>();
        if(!adoptProblem.isEmpty())result.add(adoptProblem);
        RuleSet effective=effective();
        if(effective==null)return List.copyOf(result);
        if(!effective.valid())result.add(effective.problem);
        peers.forEach((id,remote)->effective.differences(remote).stream().limit(4).forEach(d->result.add(id+": "+d)));
        return List.copyOf(result.subList(0,Math.min(32,result.size())));
    }
}
