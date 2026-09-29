/* LobbyRuleExchange.java — 将有界规则快照绑定已验证的全员代码会话；缺包、旧会话与错误规则不能准备。 */
package net.fabricacs.api.impl;

import org.json.JSONObject;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class LobbyRuleExchange {
    static final String TYPE="acbric:campaign_rules";
    static final int MAX_WIRE_BYTES=40_000;
    private Map<Integer,String> sessions=Map.of();
    private final Map<Integer,RuleSet> peers=new TreeMap<>();
    private RuleSet local;
    private int room,self,attempts;
    private long began,lastSend=-1;
    void reset(){sessions=Map.of();peers.clear();local=null;attempts=0;lastSend=-1;}
    void context(int room,int self,Map<Integer,String> view,RuleSet local,long now){
        if(view.isEmpty())return; // 例行续证不改变上下文；适配器在真正失效时显式 reset。
        if(this.room==room && this.self==self && sessions.equals(view) && this.local!=null && this.local.text().equals(local.text()))return;
        this.room=room;this.self=self;this.sessions=Map.copyOf(view);this.local=local;peers.clear();attempts=0;began=now;lastSend=-1;
    }
    Optional<String> tick(long now){
        if(sessions.isEmpty() || attempts>=5 || now-began>=10_000 || lastSend>=0 && now-lastSend<2_000)return Optional.empty();
        attempts++;lastSend=now;
        JSONObject view=new JSONObject();sessions.forEach((id,session)->view.put(id.toString(),session));
        return Optional.of(new JSONObject().put("type",TYPE).put("v",1).put("channel",room).put("from",self)
                .put("sessions",view).put("rules",local.text()).toString());
    }
    void receive(String text,int frameRoom,boolean history,long now){
        if(history || sessions.isEmpty() || frameRoom!=room || now-began>=10_000 && !matched())return;
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
            // 同一已确认会话若自报不同规则，永久降为无法验证，不能靠后续旧报文恢复。
            RuleSet old=peers.get(from);
            if(old!=null && !old.text().equals(remote.text()))remote=RuleSet.invalid(remote.source,"CONTRADICTORY_RULES");
            peers.put(from,remote);
        }catch(IllegalArgumentException | org.json.JSONException invalid){ }
    }
    boolean matched(){
        if(local==null || !local.valid() || sessions.size()<2 || peers.size()!=sessions.size()-1)return false;
        return peers.values().stream().allMatch(local::matches);
    }
    String state(long now){
        if(local==null)return "WAITING";
        if(!local.valid() || peers.values().stream().anyMatch(p->!p.valid()))return "UNVERIFIABLE";
        if(peers.values().stream().anyMatch(p->!local.matches(p)))return "DIFFERENT";
        if(matched())return "MATCH";
        return now-began>=10_000?"TIMED_OUT":"CHECKING";
    }
    List<String> differences(){
        List<String> result=new ArrayList<>();if(local==null)return List.of();
        if(!local.valid())result.add(local.problem);
        peers.forEach((id,remote)->local.differences(remote).stream().limit(4).forEach(d->result.add(id+": "+d)));
        return List.copyOf(result.subList(0,Math.min(32,result.size())));
    }
}
