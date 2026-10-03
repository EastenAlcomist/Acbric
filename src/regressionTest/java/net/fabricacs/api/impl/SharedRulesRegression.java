/* SharedRulesRegression.java — 共享规则防御性复制、旧档拒绝、冻结、会话绑定与准备失效回归。 */
package net.fabricacs.api.impl;

import net.fabricacs.api.rules.*;
import org.json.*;
import java.util.*;

public final class SharedRulesRegression {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS shared rules: "+name);}
    private static void rejects(Runnable body,String name){try{body.run();throw new AssertionError("Accepted: "+name);}catch(IllegalArgumentException|IllegalStateException expected){check(true,name);}}
    private static final class MapFixture implements CampaignDataAccess {
        final CampaignDataStore store=new CampaignDataStore();
        public CampaignDataStore acbric$campaignDataStore(){return store;}
    }
    private static RuleSet rule(String source,int version,int value){return new RuleSet(source,Map.of("rule_test",new SharedRuleSnapshot(version,new JSONObject().put("damage",value))),"");}
    public static int run() throws Exception {
        checks=0;
        JSONObject values=new JSONObject().put("damage",1.5).put("label","中文").put("nested",new JSONObject().put("x",1.0));
        var snapshot=new SharedRuleSnapshot(1,values);values.put("damage",99);
        check(snapshot.values().getDouble("damage")==1.5,"declaration copies mutable input and preserves decimals");
        snapshot.values().put("damage",5);
        check(snapshot.values().getDouble("damage")==1.5,"snapshot reads return defensive copies");
        check(new SharedRuleSnapshot(1,new JSONObject().put("x",1.0)).equals(new SharedRuleSnapshot(1,new JSONObject().put("x",1))),"numeric 1 and 1.0 have identical meaning");
        check(RulesJson.encode(new JSONObject().put("b",2).put("a",1)).equals("{\"a\":1,\"b\":2}"),"object order canonicalized");
        check(RulesJson.parse(RulesJson.encode(new JSONObject().put("n",JSONObject.NULL).put("d",0.00001))).isNull("n"),"null and small decimal survive roundtrip");
        rejects(()->new SharedRuleSnapshot(-1,new JSONObject()),"negative rule version rejected");
        JSONObject cycle=new JSONObject();cycle.put("self",cycle);rejects(()->new SharedRuleSnapshot(0,cycle),"cycles rejected before serialization");
        rejects(()->RulesJson.encode(new JSONObject().put("x",new Object())),"arbitrary objects rejected");
        rejects(()->RulesJson.encode(new JSONObject().put("x","a".repeat(12_001))),"oversized strings rejected");
        rejects(()->RulesJson.encode(new JSONObject().put("x","\ud800")),"unpaired surrogate rejected");
        JSONObject deep=new JSONObject(),cursor=deep;for(int i=0;i<20;i++){var next=new JSONObject();cursor.put("x",next);cursor=next;}
        JSONObject nested=deep;rejects(()->RulesJson.encode(nested),"deep input rejected");
        rejects(()->RulesJson.parse("{\"a\":1,\"a\":2}"),"duplicate JSON keys rejected");
        rejects(()->RulesJson.parse("{} false"),"trailing text rejected");

        RuleSet base=rule("NEW",1,1),different=rule("NEW",1,2);
        check(base.matches(RuleSet.parse(base.text())),"wire roundtrip retains exact rule identity");
        check(base.differences(different).contains("rule_test/damage: VALUE"),"difference names MOD and field path");
        check(!base.matches(rule("SAVED",1,1)),"new and resumed sources are distinct");
        check(!base.matches(RuleSet.invalid("NEW","FAILED")),"invalid snapshot cannot match");
        check(!RuleSet.invalid("NEW","FAILED").matches(RuleSet.invalid("NEW","FAILED")),"two identical failures never match");
        rejects(()->RuleSet.parse(base.json().put("unknown",true).toString()),"unknown manifest fields rejected");
        JSONObject future=base.json();future.put("format",2);rejects(()->RuleSet.parse(future.toString()),"future format rejected");
        JSONObject numeric=base.json();numeric.getJSONObject("mods").getJSONObject("rule_test").put("version",1.0);
        rejects(()->RuleSet.parse(RulesJson.encode(numeric).replace("\"version\":1","\"version\":1.5")),"fractional schema rejected");

        int[] validations={0};
        var handle=SharedRulesRegistry.register("rule_test",1,new JSONObject().put("damage",1),v->{validations[0]++;if(v.getDouble("damage")<=0)throw new IllegalArgumentException();v.put("damage",999);});
        check(handle.current().values().getInt("damage")==1,"validator mutation cannot alter accepted values");
        rejects(()->SharedRulesRegistry.register("rule_test",1,new JSONObject(),v->{}),"duplicate declaration rejected");
        long revision=SharedRulesRegistry.revision();
        rejects(()->handle.update(new JSONObject().put("damage",0)),"invalid update rejected");
        check(handle.current().values().getInt("damage")==1 && SharedRulesRegistry.revision()==revision,"failed update leaves candidate and revision intact");
        handle.update(new JSONObject().put("damage",1));check(SharedRulesRegistry.revision()==revision,"equal update does not invalidate preparation");
        MapFixture map=new MapFixture();map.store.write("other_mod",9,new JSONObject().put("kept",true));
        check(!SharedRulesRegistry.saved(map).valid(),"old save missing declared rules is blocked without defaults");
        rejects(()->handle.forCampaign(map),"public read does not fall back to local config");
        try{SharedRulesRegistry.validateLoaded(map);throw new AssertionError();}catch(java.io.IOException expected){check(true,"loaded world validation fails explicitly");}
        RuleSet selected=SharedRulesRegistry.newCampaign();SharedRulesRegistry.freeze(map,selected);
        check(handle.forCampaign(map).values().getInt("damage")==1,"selected rules freeze into campaign data");
        handle.update(new JSONObject().put("damage",3));
        check(handle.forCampaign(map).values().getInt("damage")==1 && handle.current().values().getInt("damage")==3,"future config changes cannot rewrite frozen rules");
        check(SharedRulesRegistry.saved(map).entries.get("rule_test").values().getInt("damage")==1,"resumed candidate uses save values");
        rejects(()->SharedRulesRegistry.freeze(map,SharedRulesRegistry.newCampaign()),"cannot overwrite frozen rules");
        check(map.store.read("other_mod").orElseThrow().data().getBoolean("kept"),"freeze preserves other MOD data");
        JSONObject saved=map.store.snapshot();int calls=validations[0];MapFixture restored=new MapFixture();restored.store.restore(saved);SharedRulesRegistry.validateStored(restored);
        check(validations[0]==calls,"serialization and structural recovery do not execute MOD validators");
        check(handle.forCampaign(restored).equals(handle.forCampaign(map)),"campaign store restoration retains frozen rules");
        MapFixture bad=new MapFixture();bad.store.write("acbric_api",1,new JSONObject().put("sharedRules",rule("NEW",2,1).text()));
        check(!SharedRulesRegistry.saved(bad).valid(),"saved version mismatch blocks instead of migrating");
        bad.store.write("acbric_api",1,new JSONObject().put("sharedRules","broken"));
        check(!SharedRulesRegistry.saved(bad).valid(),"malformed reserved block never becomes empty success");
        try{SharedRulesRegistry.validateStored(bad);throw new AssertionError();}catch(java.io.IOException expected){check(true,"native restore rejects malformed rules without callbacks");}
        Map<String,SharedRuleSnapshot> retained=new TreeMap<>(selected.entries);retained.put("absent_mod",new SharedRuleSnapshot(8,new JSONObject().put("old",true)));
        MapFixture absent=new MapFixture();SharedRulesRegistry.freeze(absent,new RuleSet("NEW",retained,""));
        check(SharedRulesRegistry.saved(absent).entries.containsKey("absent_mod"),"absent MOD rules retained without invoking absent code");

        Map<Integer,String> sessions=Map.of(1,UUID.randomUUID().toString(),2,UUID.randomUUID().toString());
        LobbyRuleExchange a=new LobbyRuleExchange(),b=new LobbyRuleExchange();a.context(0,1,sessions,base,0);b.context(0,2,sessions,base,0);
        String ap=a.tick(0).orElseThrow(),bp=b.tick(0).orElseThrow();
        for(String key:List.of("v","channel","from","sessions","rules")) {
            JSONObject wrong=new JSONObject(bp).put(key,"wrong");a.receive(wrong.toString(),0,false,0);
            check(!a.matched(),"wrong packet type rejected: "+key);
        }
        a.receive(new JSONObject(bp).put("unknown",true).toString(),0,false,0);check(!a.matched(),"unknown packet schema rejected");
        a.receive(new JSONObject(bp).put("###",-1).toString(),0,false,0);check(!a.matched(),"negative native sequence rejected");
        check(!a.matched() && a.state(0).equals("CHECKING"),"code context alone does not confirm rules");
        a.receive(bp,0,true,0);a.receive(bp,1,false,0);check(!a.matched(),"history and wrong outer room rejected");
        a.receive(new JSONObject(bp).put("###",Long.MAX_VALUE).toString(),0,false,0);b.receive(ap,0,false,0);check(a.matched() && b.matched(),"all-peer rules exchange accepts native long sequence in LAN zero room");
        check(a.tick(1).isEmpty(),"wire retries rate limited");
        var three=new LobbyRuleExchange();var larger=new HashMap<>(sessions);larger.put(3,UUID.randomUUID().toString());three.context(0,1,larger,base,0);three.receive(bp,0,false,0);
        check(!three.matched(),"incomplete or different full roster cannot confirm");
        // 同一成员改口只影响这一轮确认：报告回到当前有效快照后重新成立，不做永久降级。
        var d=new LobbyRuleExchange();d.context(0,2,sessions,different,0);a.receive(d.tick(0).orElseThrow(),0,false,1);
        check(a.state(1).equals("DIFFERENT"),"a changed self-report counts as a mismatch for the current round");
        a.receive(bp,0,false,2);check(a.matched(),"a peer reporting the current snapshot again is accepted without a manual re-check");
        a.reset();a.context(0,1,sessions,base,0);d.reset();d.context(0,2,sessions,different,0);a.receive(d.tick(0).orElseThrow(),0,false,0);
        check(a.state(0).equals("DIFFERENT") && a.differences().get(0).contains("damage"),"rule mismatch produces actionable field difference");
        a.reset();a.context(0,1,sessions,base,0);for(int i=0;i<5;i++)check(a.tick(i*2000).isPresent(),"bounded attempt "+(i+1));
        check(a.tick(9_000).isEmpty(),"attempts inside one window stay capped");
        check(a.tick(10_000).isPresent() && a.state(10_000).equals("CHECKING"),"a new window retries automatically");
        check(a.state(30_000).equals("TIMED_OUT") && a.tick(30_000).isPresent(),"silent windows report a timeout while still retrying");
        a.receive(bp,0,false,30_001);check(a.matched(),"a late report still confirms instead of being dropped forever");
        var changed=new HashMap<>(sessions);changed.put(2,UUID.randomUUID().toString());a.context(0,1,changed,base,10_002);a.receive(bp,0,false,10_002);
        check(!a.matched(),"old session offer cannot confirm new context");
        adoption(sessions);
        JSONObject frame=new JSONObject().put("type","frame").put("messages",new JSONArray().put(new JSONObject().put("type",LobbyRuleExchange.TYPE)).put(new JSONObject().put("type","native")));
        check(LobbyHandshakeBridge.stripReserved(frame).getJSONArray("messages").length()==1,"reserved rule tails filtered, native messages preserved");
        return checks;
    }
    /** 房主权威：其他玩家采用房主广播并由本地声明校验过的快照，不需要各自改成相同的本地配置。 */
    private static void adoption(Map<Integer,String> sessions){
        RuleSet hostRules=rule("NEW",1,7),guestLocal=rule("NEW",1,1);
        var host=new LobbyRuleExchange();var guest=new LobbyRuleExchange();
        host.context(0,1,1,sessions,hostRules,0);guest.context(0,2,1,sessions,guestLocal,0);
        String hostPacket=host.tick(0).orElseThrow();
        check(!host.adopted(),"the host is authoritative and never adopts a remote snapshot");
        check(guest.effective().text().equals(guestLocal.text()),"before any host packet the guest uses its own candidate");
        guest.receive(hostPacket,0,false,0);
        check(guest.adopted()&&guest.effective().text().equals(hostRules.text()),"the guest adopts the host snapshot instead of matching local values");
        check(guest.matched(),"the adopted snapshot counts as the guest's own report");
        check(guest.effective().digest().equals(hostRules.digest()),"the lobby digest follows the adopted snapshot");
        check(guest.differences().isEmpty(),"a successfully adopted snapshot reports no difference");
        var switched=new LobbyRuleExchange();switched.context(0,2,1,sessions,guestLocal,0);switched.receive(hostPacket,0,false,0);
        var newHost=new LobbyRuleExchange();RuleSet updated=rule("NEW",1,9);newHost.context(0,1,1,sessions,updated,0);
        switched.receive(newHost.tick(0).orElseThrow(),0,false,4_000);
        check(switched.adopted()&&switched.effective().digest().equals(updated.digest()),"a new host snapshot replaces the adopted one without a manual re-check");
        var invalid=new LobbyRuleExchange();invalid.context(0,2,1,sessions,guestLocal,0);
        var badHost=new LobbyRuleExchange();badHost.context(0,1,1,sessions,rule("NEW",1,0),0);
        invalid.receive(badHost.tick(0).orElseThrow(),0,false,0);
        check(invalid.state(0).equals("UNADOPTABLE")&&invalid.adoptProblem().equals("RULE_INVALID: rule_test")&&!invalid.matched(),
                "a snapshot the local validator rejects cannot be adopted or confirmed");
        var foreign=new LobbyRuleExchange();foreign.context(0,2,1,sessions,guestLocal,0);
        var foreignHost=new LobbyRuleExchange();
        foreignHost.context(0,1,1,sessions,new RuleSet("NEW",Map.of("other_mod",new SharedRuleSnapshot(1,new JSONObject().put("x",1))),""),0);
        foreign.receive(foreignHost.tick(0).orElseThrow(),0,false,0);
        check(foreign.state(0).equals("UNADOPTABLE")&&foreign.adoptProblem().equals("RULE_SET_MISMATCH"),"a host snapshot declaring other MODs cannot be adopted");
        var observer=new LobbyRuleExchange();observer.context(0,3,1,sessions,hostRules,0);
        var otherGuest=new LobbyRuleExchange();otherGuest.context(0,2,1,sessions,rule("NEW",1,2),0);
        observer.receive(otherGuest.tick(0).orElseThrow(),0,false,0);
        check(observer.state(0).equals("DIFFERENT")&&!observer.adopted(),"only the host snapshot is adopted; a peer still has to report the same values");
    }
}
