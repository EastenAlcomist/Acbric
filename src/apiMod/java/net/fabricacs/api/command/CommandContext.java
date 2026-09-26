/* CommandContext.java — 命令开始时的游戏线程上下文；对象引用只在当前回调使用，不跨战役缓存。 */
package net.fabricacs.api.command;
import java.util.*;
public record CommandContext(String modId,Map<String,Object> arguments,Object screen,Object campaignWorld,Session session) {
    public enum Session { SINGLEPLAYER, MULTIPLAYER, UNKNOWN }
    public CommandContext {Objects.requireNonNull(modId);arguments=Map.copyOf(arguments);Objects.requireNonNull(session);}
    public String text(String name){return (String)arguments.get(name);}
    public int integer(String name){return (Integer)arguments.get(name);}
    public double decimal(String name){return (Double)arguments.get(name);}
    public boolean bool(String name){return (Boolean)arguments.get(name);}
}
