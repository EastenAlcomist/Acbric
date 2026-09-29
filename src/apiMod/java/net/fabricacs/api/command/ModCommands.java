/* ModCommands.java — 按 MOD 命名空间注册命令；注销幂等，关闭控制台不影响注册。 */
package net.fabricacs.api.command;
import net.fabricacs.api.impl.CommandRegistry;
public final class ModCommands {
    private final String modId;
    public ModCommands(String modId){if(modId==null||!modId.matches("[a-z][a-z0-9_-]{1,63}"))throw new IllegalArgumentException("MOD ID");this.modId=modId;}
    public Registration register(CommandSpec spec,Handler handler){return CommandRegistry.GLOBAL.register(modId,spec,handler);}
    @FunctionalInterface public interface Handler {CommandResult execute(CommandContext context)throws Exception;}
    public interface Registration extends AutoCloseable {String name();boolean isRegistered();@Override void close();}
}
