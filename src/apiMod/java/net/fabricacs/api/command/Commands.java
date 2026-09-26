/* Commands.java — 控制台与 MOD 共用命令查询/执行入口；执行限当前游戏线程，查询不执行回调。 */
package net.fabricacs.api.command;
import java.util.List;
import net.fabricacs.api.impl.CommandRegistry;
public final class Commands {
    private Commands(){}
    public record Descriptor(String name,String modId,CommandSpec spec){}
    public static List<Descriptor> list(){return CommandRegistry.GLOBAL.list();}
    public static List<String> complete(String line){return CommandRegistry.GLOBAL.complete(line);}
    public static String help(String name,boolean chinese){return CommandRegistry.GLOBAL.help(name,chinese);}
    public static CommandResult execute(String line){return CommandRegistry.GLOBAL.execute(line);}
}
