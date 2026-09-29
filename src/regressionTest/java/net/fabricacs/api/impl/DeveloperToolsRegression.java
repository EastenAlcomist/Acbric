/* DeveloperToolsRegression.java — 命令边界、生命周期、并发消息上限和白名单导出的无界面回归。 */
package net.fabricacs.api.impl;
import net.fabricacs.api.command.*;
import net.fabricacs.api.diagnostics.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipFile;
public final class DeveloperToolsRegression {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS developer tools: "+name);}
    private static CommandSpec spec(String id,List<CommandArgument> args,CommandSpec.Effect effect,CommandSpec.Requirement requirement){return new CommandSpec(id,new CommandText("Description","说明"),args,effect,requirement);}
    private static CommandSpec spec(String id,List<CommandArgument> args){return spec(id,args,CommandSpec.Effect.READ_ONLY,CommandSpec.Requirement.ACTIVE_SCREEN);}
    private static void rejected(Runnable r,String name){try{r.run();throw new AssertionError(name);}catch(IllegalArgumentException|IllegalStateException expected){check(true,name);}}
    public static int run(Path root)throws Exception{
        checks=0;var registry=new CommandRegistry();Object screen=new Object(),world=new Object();var environment=new AtomicReference<>(new CommandRegistry.Environment(screen,null,CommandContext.Session.UNKNOWN));registry.bind(environment::get);
        var args=List.of(CommandArgument.text("text",false),CommandArgument.integer("count",-2,3,false),CommandArgument.decimal("ratio",0,1,false),CommandArgument.bool("flag",false),CommandArgument.choice("mode",List.of("a","two words","quote\"value"),false),CommandArgument.text("optional",true));
        AtomicReference<CommandContext> seen=new AtomicReference<>();AtomicInteger called=new AtomicInteger();
        var handle=registry.register("test_mod",spec("echo",args),c->{seen.set(c);called.incrementAndGet();return CommandResult.success("OK","成功");});
        check(registry.execute("test_mod:echo \"hello 世界\" 3 0.5 true a").status()==CommandResult.Status.SUCCESS,"typed quoted command succeeds");
        check(seen.get().text("text").equals("hello 世界")&&seen.get().integer("count")==3&&seen.get().decimal("ratio")==0.5&&seen.get().bool("flag")&&!seen.get().arguments().containsKey("optional"),"typed context and absent optional argument");
        check(seen.get().screen()==screen&&seen.get().campaignWorld()==null&&seen.get().modId().equals("test_mod"),"fresh game context and owner");
        try{seen.get().arguments().put("x",1);throw new AssertionError();}catch(UnsupportedOperationException expected){check(true,"argument map immutable");}
        for(String line:List.of("test_mod:echo", "test_mod:echo x 4 .5 true a", "test_mod:echo x 2147483648 .5 true a", "test_mod:echo x 1 NaN true a", "test_mod:echo x 1 1e-999 true a", "test_mod:echo x 1 1.000000000000001 true a", "test_mod:echo x 1 .5 TRUE a", "test_mod:echo x 1 .5 true unknown", "test_mod:echo \"unterminated", "test_mod:echo x 1 .5 true a x extra", "test_mod:echo\n", "x".repeat(2049), "", "unknown:cmd"))check(registry.execute(line).status()==CommandResult.Status.INVALID_ARGUMENTS,"invalid input rejected: "+line.substring(0,Math.min(line.length(),70)));
        check(called.get()==1,"invalid input never calls handler");
        check(CommandRegistry.tokens("cmd \"\" a\"b c\" \"quote\\\"slash\\\\\"",false).equals(List.of("cmd","","ab c","quote\"slash\\")),"quoted empty, concatenated and escaped tokens");
        check(CommandRegistry.tokens("cmd a;b|c$()",false).equals(List.of("cmd","a;b|c$()")),"shell punctuation stays plain text");
        check(registry.complete("test_").equals(List.of("test_mod:echo")),"command completion namespaced");
        check(registry.complete("test_mod:echo x 1 .5 t").equals(List.of("test_mod:echo x 1 .5 true")),"boolean completion");
        check(registry.complete("test_mod:echo x 1 .5 true \"two ").equals(List.of("test_mod:echo x 1 .5 true \"two words\"")),"space inside unfinished quote is not a new argument");
        check(registry.complete("test_mod:echo x 1 .5 true q").equals(List.of("test_mod:echo x 1 .5 true \"quote\\\"value\"")),"completion escapes candidate quotes");
        check(registry.complete("test_mod:echo x 1 .5 true a optional ").isEmpty()&&called.get()==1,"completion bounded by declaration and never executes");
        check(registry.help(handle.name(),true).contains("说明")&&registry.help(handle.name(),false).contains("Description"),"metadata powers both help languages");
        rejected(()->registry.register("test_mod",spec("echo",List.of()),c->null),"duplicate registration rejected");
        rejected(()->spec("bad",List.of(CommandArgument.text("a",true),CommandArgument.text("b",false))),"required argument cannot follow optional");
        rejected(()->spec("bad",List.of(CommandArgument.text("a",false),CommandArgument.text("a",false))),"duplicate argument rejected");
        AtomicReference<Throwable> offThread=new AtomicReference<>();Thread worker=new Thread(()->{try{registry.execute("test_mod:echo");}catch(Throwable e){offThread.set(e);}});worker.start();worker.join();check(offThread.get() instanceof IllegalStateException,"off-thread execution refused");
        registry.register("test_mod",spec("recursive",List.of()),c->registry.execute("test_mod:recursive"));check(registry.execute("test_mod:recursive").status()==CommandResult.Status.UNAVAILABLE,"recursive execution refused");
        registry.register("test_mod",spec("fail",List.of()),c->{throw new IllegalStateException("fixture failure");});check(registry.execute("test_mod:fail").status()==CommandResult.Status.FAILED,"handler failure isolated at explicit command boundary");
        check(DiagnosticHub.messages("test_mod",DiagnosticMessage.Level.ERROR).stream().anyMatch(m->m.detail().contains("fixture failure")),"command failure includes bounded traceback");
        registry.register("test_mod",spec("campaign",List.of(),CommandSpec.Effect.READ_ONLY,CommandSpec.Requirement.CAMPAIGN),c->CommandResult.success(c.session().name(),c.session().name()));
        check(registry.execute("test_mod:campaign").status()==CommandResult.Status.UNAVAILABLE,"campaign required outside campaign");
        environment.set(new CommandRegistry.Environment(screen,world,CommandContext.Session.MULTIPLAYER));check(registry.execute("test_mod:campaign").message().english().equals("MULTIPLAYER"),"campaign and session obtained at execution");
        registry.register("test_mod",spec("shared",List.of(),CommandSpec.Effect.SHARED,CommandSpec.Requirement.ACTIVE_SCREEN),c->{throw new AssertionError("Must not run");});
        for(var mode:CommandContext.Session.values()){environment.set(new CommandRegistry.Environment(screen,world,mode));check(registry.execute("test_mod:shared").status()==CommandResult.Status.UNAVAILABLE,"shared execution reserved in "+mode);}
        environment.set(new CommandRegistry.Environment(null,null,CommandContext.Session.UNKNOWN));check(registry.execute("test_mod:fail").status()==CommandResult.Status.UNAVAILABLE,"inactive screen refuses handler");
        handle.close();handle.close();check(!handle.isRegistered()&&registry.complete("test_mod:e").isEmpty(),"close unregisters idempotently");var replacement=registry.register("test_mod",spec("echo",args),c->CommandResult.success("new","新"));handle.close();check(replacement.isRegistered(),"old close cannot remove replacement");
        var limit=new CommandRegistry();for(int i=0;i<64;i++)limit.register("limit_mod",spec("cmd"+i,List.of()),c->null);rejected(()->limit.register("limit_mod",spec("more",List.of()),c->null),"per owner registration limit");check(limit.complete("").size()==32,"completion capped at 32");
        var buffer=new DiagnosticHub.Buffer();for(int i=0;i<520;i++)buffer.add("test_mod",DiagnosticMessage.Level.INFO,"message"+i,"");check(buffer.read(null,null).size()==512&&buffer.dropped()==8&&buffer.read(null,null).getFirst().sequence()==9,"count bound evicts oldest");
        buffer.add("other",DiagnosticMessage.Level.ERROR,"x".repeat(8000),"y".repeat(8000));var error=buffer.read("other",DiagnosticMessage.Level.ERROR).getFirst();check(error.message().length()==4096&&error.detail().length()==4096,"per message and detail bounds");check(buffer.read("other",DiagnosticMessage.Level.INFO).isEmpty(),"owner and level filters intersect");
        var concurrent=new DiagnosticHub.Buffer();List<Thread> writers=new ArrayList<>();for(int i=0;i<4;i++){Thread t=new Thread(()->{for(int n=0;n<100;n++)concurrent.add("worker",DiagnosticMessage.Level.INFO,"w".repeat(4096),"d".repeat(4096));});writers.add(t);t.start();}for(Thread t:writers)t.join();var rows=concurrent.read(null,null);check(rows.size()<100&&concurrent.dropped()+rows.size()==400,"concurrent writers honor total character budget");check(rows.stream().map(DiagnosticMessage::sequence).distinct().count()==rows.size(),"concurrent message sequence unique");
        Files.createDirectories(root);Path session=Files.createDirectory(root.resolve("session")),out=root.resolve("exports");Files.writeString(session.resolve("startup.json"),"{}");Files.writeString(session.resolve("private-config.json"),"secret");Files.writeString(session.resolve("runtime-events.jsonl"),"x".repeat(1024*1024+1));Path zip=DiagnosticExport.write(out,session,"{}","[]");
        try(ZipFile z=new ZipFile(zip.toFile())){check(z.getEntry("snapshot.json")!=null&&z.getEntry("messages.json")!=null&&z.getEntry("session/startup.json")!=null,"export contains snapshot and allowed current reports");check(z.getEntry("session/private-config.json")==null&&z.getEntry("session/runtime-events.jsonl")==null,"unlisted and oversized files excluded");check(new String(z.getInputStream(z.getEntry("README.txt")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("unavailable"),"missing and oversized reports listed");}
        try{DiagnosticExport.write(out,session,"x".repeat(4*1024*1024+1),"[]");throw new AssertionError();}catch(java.io.IOException expected){check(true,"oversized snapshot fails");}try(var files=Files.list(out)){check(files.count()==1,"failed export removes its partial archive");}
        Path blocker=Files.writeString(root.resolve("blocked"),"keep");try{DiagnosticExport.write(blocker,null,"{}","[]");throw new AssertionError();}catch(java.io.IOException expected){check(Files.readString(blocker).equals("keep"),"unwritable destination preserves existing file");}
        Path noSession=DiagnosticExport.write(out,null,"{}","[]");check(Files.isRegularFile(noSession),"export works without startup files");
        return checks;
    }
}
