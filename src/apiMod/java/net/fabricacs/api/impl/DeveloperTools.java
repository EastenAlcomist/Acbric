/* DeveloperTools.java — 诊断状态、内置命令与界面共享服务；背景导出不读取活游戏对象。 */
package net.fabricacs.api.impl;
import com.zarkonnen.airships.*;
import net.fabricacs.api.command.*;
import net.fabricacs.api.diagnostics.*;
import net.fabricacs.api.ui.*;
import net.fabricacs.api.util.AcbricLanguage;
import net.fabricmc.loader.api.FabricLoader;
import org.json.*;
import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeveloperTools {
    private static volatile List<DiagnosticSnapshot.ModStatus> startup=List.of();
    private static volatile String phase="NOT_REACHED",sessionId="unavailable";
    private static volatile Path sessionDirectory;
    private static AirshipGame game;
    private static boolean initialized;
    private static final AtomicBoolean exporting=new AtomicBoolean();
    private static final ExecutorService exportWorker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Acbric diagnostics export");t.setDaemon(true);return t;});
    private DeveloperTools(){}
    public static void startup(JSONObject report,Path directory){
        List<DiagnosticSnapshot.ModStatus> rows=new ArrayList<>();JSONArray mods=report.optJSONArray("mods");
        if(mods!=null)for(int i=0;i<Math.min(mods.length(),1024);i++){
            JSONObject mod=mods.getJSONObject(i);StringBuilder failure=new StringBuilder();JSONArray entries=mod.getJSONArray("entrypoints");
            for(int n=0;n<entries.length()&&failure.length()<4096;n++){JSONObject detail=entries.getJSONObject(n).optJSONObject("failure");if(detail!=null)failure.append(detail.optString("type")).append(": ").append(detail.optString("message")).append('\n');}
            rows.add(new DiagnosticSnapshot.ModStatus(mod.getString("id"),DiagnosticHub.clip(mod.optString("name"),256),mod.optString("version"),true,null,mod.optString("acbricStatus","NOT_REACHED"),"","",DiagnosticHub.clip(failure.toString(),4096)));
        }
        startup=List.copyOf(rows);phase=report.optString("phase","NOT_REACHED");sessionId=report.optString("session","unavailable");sessionDirectory=directory;
    }
    public static synchronized void initialize(){
        if(initialized)return;initialized=true;ModCommands commands=new ModCommands("acbric_api");
        commands.register(spec("help","List commands or inspect one command","列出命令或查看指定命令",List.of(CommandArgument.text("command",true)),CommandSpec.Effect.READ_ONLY),c->CommandResult.success(Commands.help(c.text("command"),false),Commands.help(c.text("command"),true)));
        commands.register(spec("status","Show framework and game status","查看框架和游戏状态",List.of(),CommandSpec.Effect.READ_ONLY),c->{var s=snapshot();return CommandResult.success(describe(s,false),describe(s,true));});
        commands.register(spec("mods","List loaded and discovered MODs","列出已加载和已发现的 MOD",List.of(),CommandSpec.Effect.READ_ONLY),c->{var s=snapshot();return CommandResult.success(modList(s,false),modList(s,true));});
        commands.register(spec("mod","Inspect one MOD","查看一个 MOD 的详情",List.of(CommandArgument.text("mod_id",false)),CommandSpec.Effect.READ_ONLY),c->{var mod=snapshot().mods().stream().filter(m->m.id().equals(c.text("mod_id"))).findFirst();return mod.isEmpty()?new CommandResult(CommandResult.Status.INVALID_ARGUMENTS,new CommandText("Unknown MOD","未知 MOD")):CommandResult.success(modDetail(mod.get(),false),modDetail(mod.get(),true));});
        commands.register(spec("diagnostics","Export a local diagnostic ZIP","导出本地诊断 ZIP",List.of(CommandArgument.choice("action",List.of("export"),false)),CommandSpec.Effect.LOCAL),c->{var task=export();if(task.isCompletedExceptionally())return new CommandResult(CommandResult.Status.UNAVAILABLE,new CommandText("Export unavailable; see diagnostics.","暂不能导出，详情见诊断。"));return CommandResult.success("Export requested; completion or failure will appear in the console.","已请求导出，完成或失败信息将显示在控制台。");});
        ModUi ui=new ModUi("acbric_api");ui.register("developer-tools",()->AcbricLanguage.text("Developer tools","开发者工具"),DeveloperConsoleUi::statusWindow);
        ui.register("console",()->AcbricLanguage.text("Console","控制台"),DeveloperConsoleUi::consoleWindow);
    }
    private static CommandSpec spec(String id,String en,String zh,List<CommandArgument> args,CommandSpec.Effect effect){return new CommandSpec(id,new CommandText(en,zh),args,effect,CommandSpec.Requirement.ACTIVE_SCREEN);}
    public static void bind(AirshipGame current){game=current;CommandRegistry.GLOBAL.bind(DeveloperTools::environment);}
    public static void exit(AirshipGame current){if(game==current)game=null;}
    private static CommandRegistry.Environment environment(){
        if(game==null||game.s==null||game.error!=null||game.helpText!=null||game.mpChatOverlayActive)return new CommandRegistry.Environment(null,null,CommandContext.Session.UNKNOWN);
        CampaignWorld world=CampaignLifecycleHooks.world(game.s);
        CommandContext.Session mode=world==null?CommandContext.Session.UNKNOWN:world.isMultiplayer()?CommandContext.Session.MULTIPLAYER:CommandContext.Session.SINGLEPLAYER;
        return new CommandRegistry.Environment(game.s,world,mode);
    }
    public static DiagnosticSnapshot snapshot(){
        CommandRegistry.GLOBAL.checkThread();Map<String,DiagnosticSnapshot.ModStatus> rows=new TreeMap<>();startup.forEach(m->rows.put(m.id(),m));
        JavaModManager manager=JavaModManager.current();if(manager!=null)for(var entry:manager.entries()){
            var prior=rows.get(entry.id());String init=prior==null?(entry.loaded()?"NOT_REACHED":"NOT_LOADED"):prior.initialization();
            rows.put(entry.id(),new DiagnosticSnapshot.ModStatus(entry.id(),DiagnosticHub.clip(entry.metadata().getName(),256),entry.metadata().getVersion().getFriendlyString(),entry.loaded(),manager.enabledNext(entry.id()),init,entry.archive()==null?"":entry.archive().toString(),manager.displayReason(entry.id()),prior==null?"":prior.failure()));
        }
        String version=FabricLoader.getInstance().getModContainer("acbric_api").map(c->c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        return new DiagnosticSnapshot(version,System.getProperty("acbric.internal.game.version","unknown"),System.getProperty("acbric.internal.game.fingerprint","unavailable"),sessionId,phase,List.copyOf(rows.values()),DiagnosticHub.dropped());
    }
    public static String describe(DiagnosticSnapshot s,boolean zh){return (zh?"API：":"API: ")+s.apiVersion()+"\n"+(zh?"游戏：":"Game: ")+s.gameVersion()+"\n"+(zh?"构建指纹：":"Fingerprint: ")+s.gameFingerprint()+"\n"+(zh?"会话：":"Session: ")+s.sessionId()+"\n"+(zh?"启动阶段：":"Startup phase: ")+s.phase()+"\n"+(zh?"已发现 MOD：":"Discovered MODs: ")+s.mods().size()+"\n"+(zh?"已淘汰消息：":"Evicted messages: ")+s.droppedMessages();}
    private static String init(String value,boolean zh){if(!zh)return value;return switch(value){case "SUCCEEDED"->"入口成功";case "FAILED"->"入口失败";case "NO_ACBRIC_ENTRYPOINT"->"无 acbric 入口";case "PENDING"->"入口等待中";case "RUNNING"->"入口执行中";case "NOT_LOADED"->"未加载";default->"未到达入口";};}
    public static String modList(DiagnosticSnapshot snapshot,boolean zh){return DiagnosticHub.clip(String.join("\n",snapshot.mods().stream().map(m->m.id()+" "+m.version()+" · "+init(m.initialization(),zh)).toList()),8192);}
    public static String modDetail(DiagnosticSnapshot.ModStatus m,boolean zh){return DiagnosticHub.clip(m.name()+" ("+m.id()+")\n"+m.version()+"\n"+(zh?"当前加载：":"Currently loaded: ")+m.loaded()+"\n"+(zh?"下次启用：":"Enabled next: ")+(m.enabledNext()==null?(zh?"未知":"unknown"):m.enabledNext())+"\n"+init(m.initialization(),zh)+"\n"+m.managementNote()+"\n"+m.source()+"\n"+m.failure(),8192);}
    public static CompletableFuture<Path> export(){
        CommandRegistry.GLOBAL.checkThread();if(!exporting.compareAndSet(false,true)){DiagnosticHub.publish("acbric_api",DiagnosticMessage.Level.WARN,"Export already running / 已有导出任务",null);return CompletableFuture.failedFuture(new IllegalStateException("Export already running / 已有导出任务"));}
        try{
            var s=snapshot();JSONObject json=new JSONObject().put("schema",1).put("apiVersion",s.apiVersion()).put("gameVersion",s.gameVersion()).put("fingerprint",s.gameFingerprint()).put("session",s.sessionId()).put("phase",s.phase()).put("droppedMessages",s.droppedMessages());
            JSONArray mods=new JSONArray();for(var m:s.mods())mods.put(new JSONObject().put("id",m.id()).put("name",m.name()).put("version",m.version()).put("loaded",m.loaded()).put("enabledNext",m.enabledNext()==null?JSONObject.NULL:m.enabledNext()).put("initialization",m.initialization()).put("source",m.source()).put("managementNote",m.managementNote()).put("failure",m.failure()));json.put("mods",mods);
            JSONArray messages=new JSONArray();for(var m:DiagnosticHub.messages(null,null))messages.put(new JSONObject().put("sequence",m.sequence()).put("time",m.time().toString()).put("modId",m.modId()).put("level",m.level().name()).put("message",m.message()).put("detail",m.detail()));
            String snapshot=json.toString(2),log=messages.toString(2);Path source=sessionDirectory,target=FabricLoader.getInstance().getGameDir().resolve("logs/acbric/exports");
            return CompletableFuture.supplyAsync(()->{try{Path path=DiagnosticExport.write(target,source,snapshot,log);DiagnosticHub.publish("acbric_api",DiagnosticMessage.Level.INFO,"Export complete / 导出完成: "+path,null);return path;}
                catch(Exception ex){DiagnosticHub.publish("acbric_api",DiagnosticMessage.Level.ERROR,"Export failed / 导出失败",ex);throw new CompletionException(ex);}finally{exporting.set(false);}},exportWorker);
        }catch(RuntimeException ex){exporting.set(false);DiagnosticHub.publish("acbric_api",DiagnosticMessage.Level.ERROR,"Export failed / 导出失败",ex);return CompletableFuture.failedFuture(ex);}
    }
}
