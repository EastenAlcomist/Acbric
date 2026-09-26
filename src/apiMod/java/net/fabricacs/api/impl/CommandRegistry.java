/* CommandRegistry.java — 显式命令注册、有限解析及静态补全；执行时重新取游戏上下文，不在锁内调用 MOD。 */
package net.fabricacs.api.impl;
import net.fabricacs.api.command.*;
import net.fabricacs.api.diagnostics.DiagnosticMessage.Level;
import java.util.*;
import java.util.function.Supplier;

public final class CommandRegistry {
    public static final CommandRegistry GLOBAL=new CommandRegistry();
    public record Environment(Object screen,Object world,CommandContext.Session session){}
    private final Map<String,Entry> entries=new TreeMap<>();
    private volatile Thread thread;private Supplier<Environment> environment;private boolean executing;
    private final class Entry implements ModCommands.Registration {
        final String owner,name;final CommandSpec spec;volatile ModCommands.Handler handler;
        Entry(String owner,CommandSpec spec,ModCommands.Handler handler){this.owner=owner;this.name=owner+":"+spec.id();this.spec=spec;this.handler=handler;}
        public String name(){return name;}public boolean isRegistered(){return handler!=null;}
        public void close(){synchronized(entries){entries.remove(name,this);handler=null;}}
    }
    public void bind(Supplier<Environment> environment){this.environment=Objects.requireNonNull(environment);thread=Thread.currentThread();}
    public void checkThread(){if(thread==null||thread!=Thread.currentThread())throw new IllegalStateException("Commands require the current game thread");}
    public ModCommands.Registration register(String owner,CommandSpec spec,ModCommands.Handler handler){
        if(owner==null||!owner.matches("[a-z][a-z0-9_-]{1,63}"))throw new IllegalArgumentException("MOD ID");Objects.requireNonNull(spec);Objects.requireNonNull(handler);
        synchronized(entries){String name=owner+":"+spec.id();if(entries.size()>=256||entries.containsKey(name)||entries.values().stream().filter(e->e.owner.equals(owner)).count()>=64)throw new IllegalStateException("Duplicate/excessive commands");var entry=new Entry(owner,spec,handler);entries.put(name,entry);return entry;}
    }
    public List<Commands.Descriptor> list(){synchronized(entries){return entries.values().stream().map(e->new Commands.Descriptor(e.name,e.owner,e.spec)).toList();}}
    private Entry entry(String name){synchronized(entries){return entries.get(name);}}
    private static CommandResult result(CommandResult.Status status,String en,String zh){return new CommandResult(status,new CommandText(DiagnosticHub.clip(en,8192),DiagnosticHub.clip(zh,8192)));}
    /** 双引号及引号内的反斜杠转义；不是 shell，不解释分号、管道、变量或多条命令。 */
    static List<String> tokens(String line,boolean partial){
        if(line==null||line.length()>2048||line.codePoints().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Input");
        List<String> tokens=new ArrayList<>();StringBuilder token=new StringBuilder();boolean quoted=false,started=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);
            if(c=='"'){quoted=!quoted;started=true;}
            else if(c=='\\'&&quoted&&i+1<line.length()&&(line.charAt(i+1)=='"'||line.charAt(i+1)=='\\')){token.append(line.charAt(++i));started=true;}
            else if(c==' '&&!quoted){if(started){tokens.add(token.toString());token.setLength(0);started=false;}}
            else{token.append(c);started=true;}
        }
        if(quoted&&!partial)throw new IllegalArgumentException("Unclosed quote");if(started)tokens.add(token.toString());
        if(tokens.size()>17)throw new IllegalArgumentException("Too many arguments");return tokens;
    }
    private static String quoted(String value){return value.isEmpty()||value.contains(" ")||value.contains("\"")?"\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"":value;}
    public List<String> complete(String line){
        List<String> words;try{words=tokens(line,true);}catch(IllegalArgumentException ex){return List.of();}
        boolean inQuote=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\\'&&inQuote&&i+1<line.length()&&(line.charAt(i+1)=='"'||line.charAt(i+1)=='\\'))i++;else if(c=='"')inQuote=!inQuote;}
        boolean next=line.endsWith(" ")&&!inQuote;
        if(words.isEmpty()||words.size()==1&&!next){String prefix=words.isEmpty()?"":words.getFirst();return list().stream().map(Commands.Descriptor::name).filter(n->n.startsWith(prefix)).limit(32).toList();}
        Entry entry=entry(words.getFirst());if(entry==null)return List.of();int index=words.size()-2+(next?1:0);
        if(index>=entry.spec.arguments().size())return List.of();var argument=entry.spec.arguments().get(index);
        List<String> candidates=argument.type()==CommandArgument.Type.BOOLEAN?List.of("true","false"):argument.choices();
        String prefix=next?"":words.getLast();String base=String.join(" ",words.subList(0,index+1).stream().map(CommandRegistry::quoted).toList());
        return candidates.stream().filter(v->v.startsWith(prefix)).map(v->base+" "+quoted(v)).limit(32).toList();
    }
    public String help(String name,boolean chinese){
        if(name==null||name.isBlank())return DiagnosticHub.clip(String.join("\n",list().stream().map(Commands.Descriptor::name).toList()),8192);
        Entry entry=entry(name);if(entry==null)return chinese?"未知命令":"Unknown command";
        StringBuilder text=new StringBuilder(entry.name);for(var a:entry.spec.arguments())text.append(a.optional()?" [":" <").append(a.name()).append(a.optional()?"]":">");
        text.append('\n').append(entry.spec.description().resolve(chinese)).append('\n').append(entry.spec.effect()).append(" / ").append(entry.spec.requirement());
        for(var a:entry.spec.arguments()){text.append('\n').append(a.name()).append(": ").append(a.type());if(a.type()==CommandArgument.Type.INTEGER||a.type()==CommandArgument.Type.DECIMAL)text.append(" ").append(a.minimum()).append("..").append(a.maximum());if(!a.choices().isEmpty())text.append(" ").append(a.choices());}
        return DiagnosticHub.clip(text.toString(),8192);
    }
    public CommandResult execute(String line){
        checkThread();if(executing)return result(CommandResult.Status.UNAVAILABLE,"Recursive command execution is unavailable.","不支持重入执行命令。");
        List<String> words;try{words=tokens(line,false);}catch(IllegalArgumentException ex){return result(CommandResult.Status.INVALID_ARGUMENTS,"Invalid input: use one line, <=2048 characters and balanced double quotes.","输入无效：单行不超过 2048 字符，双引号必须闭合。");}
        if(words.isEmpty())return result(CommandResult.Status.INVALID_ARGUMENTS,"Enter a command. Use acbric_api:help.","请输入命令，可使用 acbric_api:help。");
        Entry entry=entry(words.getFirst());if(entry==null)return result(CommandResult.Status.INVALID_ARGUMENTS,"Unknown command: "+words.getFirst(),"未知命令："+words.getFirst());
        var handler=entry.handler;if(handler==null)return result(CommandResult.Status.UNAVAILABLE,"Command was unregistered.","命令已注销。");
        var args=entry.spec.arguments();int count=words.size()-1;if(count>args.size()||count<args.stream().filter(a->!a.optional()).count())return result(CommandResult.Status.INVALID_ARGUMENTS,help(entry.name,false),help(entry.name,true));
        Map<String,Object> values=new LinkedHashMap<>();
        for(int i=0;i<count;i++)try{values.put(args.get(i).name(),args.get(i).parse(words.get(i+1)));}catch(IllegalArgumentException ex){return result(CommandResult.Status.INVALID_ARGUMENTS,"Invalid argument "+args.get(i).name()+".\n"+help(entry.name,false),"参数无效："+args.get(i).name()+"。\n"+help(entry.name,true));}
        executing=true;
        try{
            Environment env=Objects.requireNonNull(environment.get());
            if(env.screen()==null||entry.spec.requirement()==CommandSpec.Requirement.CAMPAIGN&&env.world()==null)return result(CommandResult.Status.UNAVAILABLE,"Required game screen/campaign is unavailable.","当前没有命令所需的游戏界面或活动战役。");
            if(entry.spec.effect()==CommandSpec.Effect.SHARED)return result(CommandResult.Status.UNAVAILABLE,"Shared gameplay commands require a future synchronized execution path.","共享玩法命令需要后续同步执行流程，当前不可执行。");
            return Objects.requireNonNull(handler.execute(new CommandContext(entry.owner,values,env.screen(),env.world(),env.session())),"Command result");
        }catch(Exception|LinkageError ex){DiagnosticHub.publish(entry.owner,Level.ERROR,"Command failed / 命令失败: "+entry.name,ex);return result(CommandResult.Status.FAILED,"Command failed; see diagnostics. Changes already made are not rolled back.","命令执行失败，详情见诊断；已经发生的修改不会回滚。");}
        finally{executing=false;}
    }
}
