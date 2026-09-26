/* SettingsRegression.java — 设置草稿的原子提交、并发冲突、校验及默认值保护；只写 build 夹具。 */
package net.fabricacs.regression;

import net.fabricacs.api.config.*;
import net.fabricacs.api.impl.NumberValues;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;

public final class SettingsRegression {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private interface Attempt {void run()throws Exception;}
    private static void rejects(Class<? extends Throwable> type,Attempt task)throws Exception{try{task.run();}catch(Exception ex){check(type.isInstance(ex),"Expected "+type+" got "+ex);return;}throw new AssertionError("Expected "+type);}
    private static void conflict(Attempt task)throws Exception{try{task.run();}catch(ConfigException ex){check(ex.code()==ConfigException.Code.CONFLICT,"conflict code");return;}throw new AssertionError("Expected conflict");}
    private static ConfigField.Text text(String s){return new ConfigField.Text(s,"中文"+s);}
    private static JSONObject defaults(){return new JSONObject().put("enabled",true).put("count",2).put("ratio",0.5).put("name","A😀中").put("mode","normal").put("private",new JSONObject().put("token",17));}
    private static List<ConfigField> fields(){return List.of(
        ConfigField.bool("enabled",text("Enabled"),ConfigField.Effect.IMMEDIATE),
        ConfigField.integer("count",text("Count"),1,10,ConfigField.Effect.NEW_CAMPAIGN),
        ConfigField.decimal("ratio",text("Ratio"),0,1,ConfigField.Effect.IMMEDIATE),
        ConfigField.text("name",text("Name"),4,ConfigField.Effect.IMMEDIATE),
        ConfigField.choice("mode",text("Mode"),List.of(new ConfigField.Option("normal",text("Normal")),new ConfigField.Option("compact",text("Compact"))),ConfigField.Effect.RESTART));}
    private static ModConfig config(Path root,String name)throws Exception{return new ModConfig(root,"settings_test",name,2,defaults(),j->{if(j.getInt("count")==7)throw new IllegalArgumentException("Cross-field/business rejection");});}
    public static int run(Path root)throws Exception{
        checks=0;
        check(NumberValues.parse("-2147483648",true,Integer.MIN_VALUE,Integer.MAX_VALUE) instanceof Integer,"integer type and lower edge");
        check(NumberValues.parse("2147483647",true,Integer.MIN_VALUE,Integer.MAX_VALUE).intValue()==Integer.MAX_VALUE,"upper edge");
        check(NumberValues.parse(".25",false,0,1).doubleValue()==0.25,"decimal syntax");
        check(NumberValues.parse("1e-2",false,0,1).doubleValue()==0.01,"scientific decimal syntax");
        for(String s:List.of("","-","1.1","2147483648","NaN","Infinity","0x10"," 2 "))rejects(IllegalArgumentException.class,()->NumberValues.parse(s,true,Integer.MIN_VALUE,Integer.MAX_VALUE));
        for(String s:List.of("NaN","Infinity","1e999","1e-999","-0.01","1.000000000000000000001"))rejects(IllegalArgumentException.class,()->NumberValues.parse(s,false,0,1));
        check(NumberValues.error("-",true,1,10,true).contains("整数")&&NumberValues.error("-",true,1,10,false).contains("integer"),"bilingual numeric errors");
        rejects(IllegalArgumentException.class,()->ConfigField.integer("a",text("a"),2,1,ConfigField.Effect.IMMEDIATE));
        rejects(IllegalArgumentException.class,()->ConfigField.choice("a",text("a"),List.of(new ConfigField.Option("x",text("X")),new ConfigField.Option("x",text("Y"))),ConfigField.Effect.IMMEDIATE));
        rejects(IllegalArgumentException.class,()->new ConfigField.Option("line\nbreak",text("invalid")));
        for(String invalid:List.of("line\nbreak","x".repeat(4097))){
            ModConfig badText=new ModConfig(root,"settings_test","badtext",1,new JSONObject().put("name",invalid),j->{});
            rejects(IllegalArgumentException.class,()->new ConfigEditor(badText,List.of(ConfigField.text("name",text("Name"),10,ConfigField.Effect.IMMEDIATE))));
        }
        ModConfig c=config(root,"main");ConfigEditor editor=new ConfigEditor(c,fields());ConfigSnapshot initial=c.read();
        check(!Files.exists(root)&&!editor.isDirty()&&editor.isValid(),"opening defaults never writes");
        c.defaults().data().put("count",9);check(c.defaults().data().getInt("count")==2,"defaults isolated");
        editor.set("count","-");check(editor.isDirty()&&!editor.isValid()&&!editor.error("count",true).isEmpty(),"invalid intermediate draft retained");
        rejects(ConfigException.class,editor::apply);check(c.read()==initial&&!Files.exists(root)&&editor.value("count").equals("-"),"invalid apply changes neither memory nor disk");
        editor.set("count","7");rejects(ConfigException.class,editor::apply);check(c.read()==initial&&editor.value("count").equals("7"),"MOD validator failure preserves draft and current");
        editor.set("name","A😀中x");check(editor.isValid(),"code point length permits four");editor.set("name","A😀中xy");check(!editor.isValid(),"code point overflow rejected");
        editor.set("mode","removed");check(!editor.error("mode",false).isEmpty(),"unknown option visible as invalid");editor.cancel();check(!editor.isDirty(),"cancel restores baseline without write");
        editor.set("count","3");editor.set("mode","compact");editor.set("ratio",".75");editor.set("enabled",false);
        ConfigEditor.Applied applied=editor.apply();byte[] saved=Files.readAllBytes(c.path());
        check(applied.changedKeys().equals(Set.of("count","mode","ratio","enabled")),"changed keys precise");
        check(applied.effects().equals(EnumSet.allOf(ConfigField.Effect.class)),"all declared effects returned");
        check(c.read()==applied.snapshot()&&c.read().data().get("count") instanceof Integer&&!editor.isDirty(),"persist before publishing new memory snapshot");
        check(c.read().data().getJSONObject("private").getInt("token")==17,"unlisted data retained");
        editor.restoreDefaults();check(editor.value("count").equals("2")&&c.read().data().getInt("count")==3&&Arrays.equals(saved,Files.readAllBytes(c.path())),"defaults only change draft");
        editor.cancel();check(editor.value("count").equals("3"),"cancel default reset");
        editor.set("ratio","0.750");check(editor.apply().changedKeys().isEmpty(),"same numeric value has no effect notification");
        ConfigEditor stale=new ConfigEditor(c,fields());JSONObject other=c.read().data().put("count",4);c.update(other);ConfigSnapshot otherSnapshot=c.read();
        stale.set("count","5");conflict(stale::apply);check(c.read()==otherSnapshot&&stale.value("count").equals("5"),"memory conflict does not overwrite another editor");
        conflict(()->c.save(new ConfigSnapshot(2,other),other));
        editor.reload();editor.set("count","6");ConfigSnapshot beforeDiskConflict=c.read();
        JSONObject external=new JSONObject(Files.readString(c.path()));external.getJSONObject("data").put("count",8);external.put("unknownEnvelope",true);Files.writeString(c.path(),external.toString());
        conflict(editor::apply);check(c.read()==beforeDiskConflict&&editor.value("count").equals("6")&&new JSONObject(Files.readString(c.path())).getJSONObject("data").getInt("count")==8,"disk conflict retains both external file and draft");
        editor.reload();check(editor.value("count").equals("8")&&!editor.isDirty(),"explicit reload accepts disk");
        editor.restoreDefaults();editor.apply();check(new JSONObject(Files.readString(c.path())).getBoolean("unknownEnvelope")&&c.read().data().has("private"),"reset preserves unknown envelope and data");
        ModConfig io=config(root,"io");ConfigEditor ioEditor=new ConfigEditor(io,fields());ioEditor.apply();Files.createDirectory(io.backupPath());ioEditor.set("count","5");ConfigSnapshot beforeIo=io.read();byte[] beforeBytes=Files.readAllBytes(io.path());
        rejects(IOException.class,ioEditor::apply);check(io.read()==beforeIo&&Arrays.equals(beforeBytes,Files.readAllBytes(io.path()))&&ioEditor.value("count").equals("5"),"backup failure keeps old config and editable draft");
        Files.writeString(c.path(),"broken");editor.set("count","9");rejects(ConfigException.class,editor::reload);check(editor.value("count").equals("9"),"corrupt reload retains draft");
        ModConfig old=config(root,"old");Files.writeString(old.path(),new JSONObject().put("format",1).put("version",1).put("data",defaults()).toString());
        rejects(IllegalStateException.class,()->new ConfigEditor(old,fields()));check(new JSONObject(Files.readString(old.path())).getInt("version")==1,"settings never auto-migrate");
        rejects(IllegalArgumentException.class,()->new ConfigEditor(c,List.of(fields().getFirst(),fields().getFirst())));
        AtomicReference<Throwable> wrong=new AtomicReference<>();Thread thread=new Thread(()->{try{editor.set("count","2");}catch(Throwable t){wrong.set(t);}});thread.start();thread.join();check(wrong.get() instanceof IllegalStateException,"editor rejects wrong thread");
        System.out.println("SETTINGS REGRESSION PASS: "+checks+" checks");return checks;
    }
}
